package com.ccapp.events.service;

import com.ccapp.commerce.domain.entity.CommerceProduct;
import com.ccapp.commerce.repository.CommerceProductRepository;
import com.ccapp.commerce.service.CommerceMerchantService;
import com.ccapp.events.domain.entity.Event;
import com.ccapp.events.domain.entity.EventStoreSyncJob;
import com.ccapp.events.domain.entity.EventTicketType;
import com.ccapp.events.domain.enums.EventProductSyncStatus;
import com.ccapp.events.domain.enums.EventStatus;
import com.ccapp.events.domain.enums.EventStoreSyncJobStatus;
import com.ccapp.events.domain.enums.EventStoreSyncJobType;
import com.ccapp.events.repository.EventRepository;
import com.ccapp.events.repository.EventStoreSyncJobRepository;
import com.ccapp.events.repository.EventTicketTypeRepository;
import com.ccapp.shop.domain.entity.ShopProduct;
import com.ccapp.shop.repository.ShopProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventStoreSyncService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

    private final EventStoreSyncJobRepository eventStoreSyncJobRepository;
    private final EventTicketTypeRepository eventTicketTypeRepository;
    private final EventRepository eventRepository;
    private final ShopProductRepository shopProductRepository;
    private final CommerceProductRepository commerceProductRepository;
    private final CommerceMerchantService commerceMerchantService;
    private final TransactionTemplate transactionTemplate;

    @Transactional
    public void enqueueUpsert(UUID ticketTypeId) {
        enqueue(ticketTypeId, EventStoreSyncJobType.UPSERT_PRODUCT);
    }

    @Transactional
    public void enqueueRefresh(UUID ticketTypeId) {
        enqueue(ticketTypeId, EventStoreSyncJobType.REFRESH_PRODUCT_STATE);
    }

    public void processPendingJobs() {
        List<EventStoreSyncJob> pendingJobs = eventStoreSyncJobRepository.findTop25ByStatusOrderByCreatedAtAsc(EventStoreSyncJobStatus.PENDING);
        for (EventStoreSyncJob pendingJob : pendingJobs) {
            transactionTemplate.executeWithoutResult(status -> processSingleJob(pendingJob.getId()));
        }
        transactionTemplate.executeWithoutResult(status -> enqueueTimeDrivenRefreshJobs());
    }

    @Transactional
    public void processSingleJob(UUID jobId) {
        EventStoreSyncJob job = eventStoreSyncJobRepository.findById(jobId).orElse(null);
        if (job == null || job.getStatus() != EventStoreSyncJobStatus.PENDING) {
            return;
        }

        job.setStatus(EventStoreSyncJobStatus.RUNNING);
        job.setAttempts(job.getAttempts() == null ? 1 : job.getAttempts() + 1);
        eventStoreSyncJobRepository.save(job);

        try {
            switch (job.getType()) {
                case UPSERT_PRODUCT -> upsertProduct(job.getTicketTypeId());
                case REFRESH_PRODUCT_STATE -> refreshProductState(job.getTicketTypeId());
            }
            job.setStatus(EventStoreSyncJobStatus.COMPLETED);
            job.setLastError(null);
        } catch (RuntimeException ex) {
            log.warn("event_store_sync_job_failed jobId={} ticketTypeId={} type={}",
                    job.getId(), job.getTicketTypeId(), job.getType(), ex);
            job.setStatus(EventStoreSyncJobStatus.FAILED);
            job.setLastError(truncate(ex.getMessage()));
            markTicketSyncFailed(job.getTicketTypeId(), ex.getMessage());
        }
        eventStoreSyncJobRepository.save(job);
    }

    @Transactional
    public void enqueueTimeDrivenRefreshJobs() {
        OffsetDateTime now = OffsetDateTime.now();
        Set<UUID> staleEventIds = new LinkedHashSet<>();
        staleEventIds.addAll(eventRepository.findByStatusAndEndDateTimeLessThanEqual(EventStatus.PUBLISHED, now).stream()
                .map(Event::getId)
                .toList());
        staleEventIds.addAll(eventRepository.findByStatusInOrderByStartDateTimeAsc(List.of(EventStatus.COMPLETED, EventStatus.CANCELLED)).stream()
                .map(Event::getId)
                .toList());

        if (!staleEventIds.isEmpty()) {
            eventTicketTypeRepository.findByEventIdIn(staleEventIds)
                    .forEach(ticketType -> enqueue(ticketType.getId(), EventStoreSyncJobType.REFRESH_PRODUCT_STATE));
        }

        eventTicketTypeRepository.findByStockLessThanEqualAndShopProductIdIsNotNull(0)
                .forEach(ticketType -> enqueue(ticketType.getId(), EventStoreSyncJobType.REFRESH_PRODUCT_STATE));
    }

    private void enqueue(UUID ticketTypeId, EventStoreSyncJobType type) {
        if (eventStoreSyncJobRepository.existsByTicketTypeIdAndTypeAndStatusIn(
                ticketTypeId,
                type,
                List.of(EventStoreSyncJobStatus.PENDING, EventStoreSyncJobStatus.RUNNING)
        )) {
            return;
        }
        eventStoreSyncJobRepository.save(EventStoreSyncJob.builder()
                .ticketTypeId(ticketTypeId)
                .type(type)
                .status(EventStoreSyncJobStatus.PENDING)
                .attempts(0)
                .build());
    }

    private void upsertProduct(UUID ticketTypeId) {
        EventTicketType ticketType = eventTicketTypeRepository.findById(ticketTypeId).orElse(null);
        if (ticketType == null) {
            return;
        }
        Event event = ticketType.getEvent();
        commerceMerchantService.ensureMerchant(event.getOrganizerAccountHolderId());

        boolean active = shouldProductBeActive(event, ticketType);
        ShopProduct product = resolveOrCreateProduct(ticketType, event, active);

        CommerceProduct commerceProduct = commerceProductRepository.findById(product.getId())
                .orElseGet(() -> CommerceProduct.builder().productId(product.getId()).build());
        commerceProduct.setDsbAmount(scale(ticketType.getDsbAmount()));
        commerceProductRepository.save(commerceProduct);

        ticketType.setShopProductId(product.getId());
        ticketType.setProductSyncStatus(EventProductSyncStatus.SYNCED);
        ticketType.setProductSyncError(null);
        ticketType.setProductSyncedAt(OffsetDateTime.now());
        eventTicketTypeRepository.save(ticketType);
    }

    private void refreshProductState(UUID ticketTypeId) {
        EventTicketType ticketType = eventTicketTypeRepository.findById(ticketTypeId).orElse(null);
        if (ticketType == null || ticketType.getShopProductId() == null) {
            return;
        }
        ShopProduct product = shopProductRepository.findById(ticketType.getShopProductId()).orElse(null);
        if (product == null) {
            upsertProduct(ticketTypeId);
            return;
        }
        product.setActive(shouldProductBeActive(ticketType.getEvent(), ticketType));
        shopProductRepository.save(product);
        ticketType.setProductSyncStatus(EventProductSyncStatus.SYNCED);
        ticketType.setProductSyncError(null);
        ticketType.setProductSyncedAt(OffsetDateTime.now());
        eventTicketTypeRepository.save(ticketType);
    }

    private ShopProduct resolveOrCreateProduct(EventTicketType ticketType, Event event, boolean active) {
        Optional<ShopProduct> existing = ticketType.getShopProductId() == null
                ? Optional.empty()
                : shopProductRepository.findById(ticketType.getShopProductId());
        ShopProduct product = existing.orElseGet(ShopProduct::new);
        product.setMerchantId(event.getOrganizerAccountHolderId());
        product.setName(buildProductName(event, ticketType));
        product.setDescription(trimToNull(event.getDescription()));
        product.setPrice(scale(ticketType.getPrice()));
        product.setCurrency(ticketType.getCurrency().trim().toUpperCase(Locale.ROOT));
        product.setCommissionPercent(scaleRate(ticketType.getPlatformCommissionRate()));
        product.setCvValue(scale(ticketType.getCv()));
        product.setActive(active);
        return shopProductRepository.save(product);
    }

    private boolean shouldProductBeActive(Event event, EventTicketType ticketType) {
        return event.getStatus() == EventStatus.PUBLISHED
                && ticketType.getStock() != null
                && ticketType.getStock() > 0
                && event.getEndDateTime() != null
                && event.getEndDateTime().isAfter(OffsetDateTime.now());
    }

    private void markTicketSyncFailed(UUID ticketTypeId, String message) {
        EventTicketType ticketType = eventTicketTypeRepository.findById(ticketTypeId).orElse(null);
        if (ticketType == null) {
            return;
        }
        ticketType.setProductSyncStatus(EventProductSyncStatus.FAILED);
        ticketType.setProductSyncError(truncate(message));
        eventTicketTypeRepository.save(ticketType);
    }

    private String buildProductName(Event event, EventTicketType ticketType) {
        return event.getTitle().trim() + " - " + ticketType.getName().trim();
    }

    private String truncate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= 255 ? value : value.substring(0, 255);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private BigDecimal scale(BigDecimal value) {
        if (value == null) {
            return ZERO;
        }
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal scaleRate(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
