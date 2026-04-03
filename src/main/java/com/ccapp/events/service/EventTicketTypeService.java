package com.ccapp.events.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.events.domain.entity.Event;
import com.ccapp.events.domain.entity.EventTicketType;
import com.ccapp.events.domain.enums.EventProductSyncStatus;
import com.ccapp.events.domain.enums.EventStatus;
import com.ccapp.events.dto.CreateEventTicketTypeRequest;
import com.ccapp.events.dto.EventTicketTypeResponse;
import com.ccapp.events.repository.EventTicketTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventTicketTypeService {

    private final EventService eventService;
    private final EventTicketTypeRepository eventTicketTypeRepository;
    private final EventAccessService eventAccessService;
    private final EventDtoAssembler eventDtoAssembler;
    private final EventStoreSyncService eventStoreSyncService;

    @Transactional
    public EventTicketTypeResponse create(UUID eventId, CreateEventTicketTypeRequest request) {
        Event event = eventService.findEvent(eventId);
        eventAccessService.assertCanManage(event);
        if (event.getStatus() == EventStatus.COMPLETED || event.getStatus() == EventStatus.CANCELLED) {
            throw new BusinessException("Ticket types cannot be created for completed or cancelled events");
        }

        EventTicketType ticketType = eventTicketTypeRepository.save(EventTicketType.builder()
                .event(event)
                .name(request.name().trim().toUpperCase(Locale.ROOT))
                .price(request.price())
                .currency(request.currency().trim().toUpperCase(Locale.ROOT))
                .stock(request.stock())
                .cv(request.cv())
                .platformCommissionRate(request.platformCommissionRate())
                .dsbAmount(request.dsbAmount())
                .productSyncStatus(EventProductSyncStatus.PENDING)
                .build());
        eventStoreSyncService.enqueueUpsert(ticketType.getId());
        return eventDtoAssembler.toTicketTypeResponse(ticketType);
    }

    @Transactional(readOnly = true)
    public List<EventTicketTypeResponse> list(UUID eventId) {
        Event event = eventService.findEvent(eventId);
        eventAccessService.assertCanView(event);
        return eventTicketTypeRepository.findByEventIdOrderByCreatedAtAsc(eventId).stream()
                .map(eventDtoAssembler::toTicketTypeResponse)
                .toList();
    }
}
