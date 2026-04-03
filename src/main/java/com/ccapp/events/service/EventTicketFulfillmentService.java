package com.ccapp.events.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.events.domain.entity.Event;
import com.ccapp.events.domain.entity.EventTicket;
import com.ccapp.events.domain.entity.EventTicketType;
import com.ccapp.events.domain.enums.EventProductSyncStatus;
import com.ccapp.events.domain.enums.EventStatus;
import com.ccapp.events.domain.enums.EventTicketStatus;
import com.ccapp.events.repository.EventTicketRepository;
import com.ccapp.events.repository.EventTicketTypeRepository;
import com.ccapp.shop.domain.entity.ShopOrderItem;
import com.ccapp.shop.repository.ShopOrderItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventTicketFulfillmentService {

    private final ShopOrderItemRepository shopOrderItemRepository;
    private final EventTicketTypeRepository eventTicketTypeRepository;
    private final EventTicketRepository eventTicketRepository;
    private final EventTicketQrService eventTicketQrService;
    private final EventStoreSyncService eventStoreSyncService;

    @Transactional
    public void fulfillPaidOrder(UUID orderId, UUID buyerAccountHolderId) {
        if (!eventTicketRepository.findByOrderId(orderId).isEmpty()) {
            return;
        }

        List<ShopOrderItem> orderItems = shopOrderItemRepository.findByOrderId(orderId);
        if (orderItems.isEmpty()) {
            return;
        }

        Map<UUID, EventTicketType> ticketTypesByProductId = new LinkedHashMap<>();
        eventTicketTypeRepository.findByShopProductIdIn(orderItems.stream().map(ShopOrderItem::getProductId).distinct().toList())
                .forEach(ticketType -> ticketTypesByProductId.put(ticketType.getShopProductId(), ticketType));
        if (ticketTypesByProductId.isEmpty()) {
            return;
        }
        if (ticketTypesByProductId.size() != orderItems.stream().map(ShopOrderItem::getProductId).distinct().count()) {
            throw new BusinessException("Event ticket orders cannot mix event tickets with other products");
        }

        Set<UUID> eventIds = ticketTypesByProductId.values().stream()
                .map(ticketType -> ticketType.getEvent().getId())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (eventIds.size() != 1) {
            throw new BusinessException("Event ticket orders must belong to a single event");
        }

        Event event = ticketTypesByProductId.values().iterator().next().getEvent();
        validateEventReadyForTicketing(event);

        Set<UUID> touchedTicketTypeIds = new LinkedHashSet<>();
        for (ShopOrderItem orderItem : orderItems) {
            EventTicketType lockedTicketType = eventTicketTypeRepository.lockById(
                    ticketTypesByProductId.get(orderItem.getProductId()).getId()
            ).orElseThrow(() -> new BusinessException("Event ticket type not found"));
            if (lockedTicketType.getStock() < orderItem.getQuantity()) {
                throw new BusinessException("Ticket stock exhausted for " + lockedTicketType.getName());
            }

            lockedTicketType.setStock(lockedTicketType.getStock() - orderItem.getQuantity());
            lockedTicketType.setProductSyncStatus(EventProductSyncStatus.PENDING);
            eventTicketTypeRepository.save(lockedTicketType);
            touchedTicketTypeIds.add(lockedTicketType.getId());

            for (int index = 0; index < orderItem.getQuantity(); index++) {
                EventTicket savedTicket = eventTicketRepository.save(EventTicket.builder()
                        .event(event)
                        .orderId(orderId)
                        .buyerAccountHolderId(buyerAccountHolderId)
                        .ticketType(lockedTicketType)
                        .qrCode("pending:" + UUID.randomUUID())
                        .status(EventTicketStatus.VALID)
                        .build());
                savedTicket.setQrCode(eventTicketQrService.generate(savedTicket));
                eventTicketRepository.save(savedTicket);
            }
        }

        touchedTicketTypeIds.forEach(eventStoreSyncService::enqueueRefresh);
    }

    @Transactional
    public void markRefundedOrder(UUID orderId) {
        List<EventTicket> tickets = eventTicketRepository.findByOrderId(orderId);
        if (tickets.isEmpty()) {
            return;
        }
        for (EventTicket ticket : tickets) {
            if (ticket.getStatus() != EventTicketStatus.REFUNDED) {
                ticket.setStatus(EventTicketStatus.REFUNDED);
            }
        }
        eventTicketRepository.saveAll(tickets);
    }

    private void validateEventReadyForTicketing(Event event) {
        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new BusinessException("Event is not open for ticket sales");
        }
        if (event.getEndDateTime() == null || !event.getEndDateTime().isAfter(OffsetDateTime.now())) {
            throw new BusinessException("Event ticket sales are closed");
        }
    }
}
