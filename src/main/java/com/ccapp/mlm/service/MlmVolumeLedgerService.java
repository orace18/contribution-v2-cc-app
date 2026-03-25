package com.ccapp.mlm.service;

import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.mlm.domain.entity.MlmMemberLink;
import com.ccapp.mlm.domain.entity.MlmRelationship;
import com.ccapp.mlm.domain.entity.MlmVolumeLedger;
import com.ccapp.mlm.domain.enums.MlmVolumeTreeType;
import com.ccapp.mlm.event.MlmVolumeUpdatedEvent;
import com.ccapp.mlm.repository.MlmMemberLinkRepository;
import com.ccapp.mlm.repository.MlmRelationshipRepository;
import com.ccapp.mlm.repository.MlmVolumeLedgerRepository;
import com.ccapp.observability.PlatformMonitoringHooks;
import com.ccapp.shop.event.ShopOrderDeliveredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MlmVolumeLedgerService {

    private static final int MAX_SPONSOR_GENERATIONS = 10;

    private final MlmRelationshipRepository mlmRelationshipRepository;
    private final MlmMemberLinkRepository mlmMemberLinkRepository;
    private final MlmVolumeLedgerRepository mlmVolumeLedgerRepository;
    private final DomainEventPublisher domainEventPublisher;

    @Transactional
    public int recordForDeliveredOrder(ShopOrderDeliveredEvent event) {
        if (event == null || event.mlmVolume() == null || event.mlmVolume().signum() <= 0) {
            return 0;
        }
        return recordUplineVolume(
                event.buyerId(),
                event.orderId(),
                event.mlmVolume(),
                event.currency(),
                event.occurredAt()
        );
    }

    @Transactional
    public int recordUplineVolume(UUID sourceMemberId,
                                  UUID sourceOrderId,
                                  BigDecimal businessVolume,
                                  OffsetDateTime occurredAt) {
        return recordUplineVolume(sourceMemberId, sourceOrderId, businessVolume, null, occurredAt);
    }

    @Transactional
    public int recordUplineVolume(UUID sourceMemberId,
                                  UUID sourceOrderId,
                                  BigDecimal businessVolume,
                                  String currency,
                                  OffsetDateTime occurredAt) {
        if (sourceMemberId == null || sourceOrderId == null) {
            return 0;
        }
        if (businessVolume == null || businessVolume.signum() <= 0) {
            return 0;
        }

        OffsetDateTime eventTime = occurredAt != null ? occurredAt : OffsetDateTime.now();
        int weekId = toWeekId(eventTime);
        String normalizedCurrency = normalizeCurrency(currency);

        List<MlmVolumeLedger> toInsert = new ArrayList<>();
        toInsert.addAll(buildPlacementEntries(sourceMemberId, sourceOrderId, businessVolume, normalizedCurrency, weekId));
        toInsert.addAll(buildSponsorEntries(sourceMemberId, sourceOrderId, businessVolume, normalizedCurrency, weekId));

        if (toInsert.isEmpty()) {
            return 0;
        }
        try {
            mlmVolumeLedgerRepository.saveAll(toInsert);
        } catch (DataIntegrityViolationException ex) {
            log.info("mlm_volume_ledger_idempotent_conflict sourceOrderId={} sourceMemberId={}",
                    sourceOrderId, sourceMemberId);
            return 0;
        }

        PlatformMonitoringHooks.onLedgerVolumeEntriesWritten(toInsert.size());
        domainEventPublisher.publish(MlmVolumeUpdatedEvent.create(
                sourceOrderId,
                sourceMemberId,
                scale(businessVolume),
                normalizedCurrency,
                toInsert.size()
        ));
        log.info("mlm_volume_ledger_upserted sourceOrderId={} sourceMemberId={} weekId={} entries={} businessVolume={}",
                sourceOrderId, sourceMemberId, weekId, toInsert.size(), businessVolume);
        return toInsert.size();
    }

    @Transactional
    public int rebuildForDeliveredOrder(UUID sourceMemberId,
                                        UUID sourceOrderId,
                                        BigDecimal businessVolume,
                                        OffsetDateTime occurredAt) {
        return rebuildForDeliveredOrder(sourceMemberId, sourceOrderId, businessVolume, null, occurredAt);
    }

    @Transactional
    public int rebuildForDeliveredOrder(UUID sourceMemberId,
                                        UUID sourceOrderId,
                                        BigDecimal businessVolume,
                                        String currency,
                                        OffsetDateTime occurredAt) {
        if (sourceOrderId == null) {
            return 0;
        }
        mlmVolumeLedgerRepository.deleteBySourceOrderId(sourceOrderId);
        return recordUplineVolume(sourceMemberId, sourceOrderId, businessVolume, currency, occurredAt);
    }

    private List<MlmVolumeLedger> buildPlacementEntries(UUID sourceMemberId,
                                                        UUID sourceOrderId,
                                                        BigDecimal businessVolume,
                                                        String currency,
                                                        int weekId) {
        List<MlmRelationship> ancestors = mlmRelationshipRepository.findAncestors(sourceMemberId);
        if (ancestors.isEmpty()) {
            throw new BusinessException("MLM tree not initialized for source member");
        }

        List<MlmVolumeLedger> entries = new ArrayList<>();
        for (MlmRelationship ancestor : ancestors) {
            if (ancestor.getLevel() == null || ancestor.getLevel() <= 0) {
                continue;
            }
            UUID uplineId = ancestor.getAncestorId();
            if (mlmVolumeLedgerRepository.existsBySourceOrderIdAndAccountHolderIdAndTreeType(
                    sourceOrderId,
                    uplineId,
                    MlmVolumeTreeType.PLACEMENT
            )) {
                continue;
            }
            entries.add(MlmVolumeLedger.builder()
                    .accountHolderId(uplineId)
                    .sourceMemberId(sourceMemberId)
                    .sourceOrderId(sourceOrderId)
                    .treeType(MlmVolumeTreeType.PLACEMENT)
                    .generation(ancestor.getLevel())
                    .volumeCv(scale(businessVolume))
                    .currency(currency)
                    .weekId(weekId)
                    .build());
        }
        return entries;
    }

    private List<MlmVolumeLedger> buildSponsorEntries(UUID sourceMemberId,
                                                      UUID sourceOrderId,
                                                      BigDecimal businessVolume,
                                                      String currency,
                                                      int weekId) {
        List<MlmVolumeLedger> entries = new ArrayList<>();
        Set<UUID> visitedSponsors = new HashSet<>();
        UUID cursor = sourceMemberId;
        int generation = 1;
        while (cursor != null && generation <= MAX_SPONSOR_GENERATIONS) {
            MlmMemberLink memberLink = mlmMemberLinkRepository.findById(cursor).orElse(null);
            if (memberLink == null) {
                break;
            }
            UUID sponsorId = memberLink.getSponsorAccountHolderId();
            if (sponsorId == null || sponsorId.equals(sourceMemberId) || !visitedSponsors.add(sponsorId)) {
                break;
            }
            if (!mlmVolumeLedgerRepository.existsBySourceOrderIdAndAccountHolderIdAndTreeType(
                    sourceOrderId,
                    sponsorId,
                    MlmVolumeTreeType.SPONSOR
            )) {
                entries.add(MlmVolumeLedger.builder()
                        .accountHolderId(sponsorId)
                        .sourceMemberId(sourceMemberId)
                        .sourceOrderId(sourceOrderId)
                        .treeType(MlmVolumeTreeType.SPONSOR)
                        .generation(generation)
                        .volumeCv(scale(businessVolume))
                        .currency(currency)
                        .weekId(weekId)
                        .build());
            }
            cursor = sponsorId;
            generation++;
        }
        return entries;
    }

    private int toWeekId(OffsetDateTime timestamp) {
        int weekYear = timestamp.get(IsoFields.WEEK_BASED_YEAR);
        int week = timestamp.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return (weekYear * 100) + week;
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return null;
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }

    private BigDecimal scale(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return amount.setScale(4, RoundingMode.HALF_UP);
    }
}
