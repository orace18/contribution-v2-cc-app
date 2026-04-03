package com.ccapp.events.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.events.domain.entity.Event;
import com.ccapp.events.domain.entity.EventAssignment;
import com.ccapp.events.domain.entity.EventSpeaker;
import com.ccapp.events.domain.entity.EventTicket;
import com.ccapp.events.domain.entity.EventTicketType;
import com.ccapp.events.domain.enums.EventAssignmentRole;
import com.ccapp.events.domain.enums.EventStatus;
import com.ccapp.events.domain.enums.EventTicketStatus;
import com.ccapp.events.dto.CreateEventRequest;
import com.ccapp.events.dto.EventResponse;
import com.ccapp.events.dto.EventSpeakerRequest;
import com.ccapp.events.dto.EventStatsResponse;
import com.ccapp.events.dto.UpdateEventRequest;
import com.ccapp.events.repository.EventAssignmentRepository;
import com.ccapp.events.repository.EventRepository;
import com.ccapp.events.repository.EventSpeakerRepository;
import com.ccapp.events.repository.EventTicketRepository;
import com.ccapp.events.repository.EventTicketTypeRepository;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.shop.domain.enums.ShopOrderStatus;
import com.ccapp.shop.dto.UpdateShopOrderStatusRequest;
import com.ccapp.shop.service.ShopOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

    private final EventRepository eventRepository;
    private final EventSpeakerRepository eventSpeakerRepository;
    private final EventTicketTypeRepository eventTicketTypeRepository;
    private final EventTicketRepository eventTicketRepository;
    private final EventAssignmentRepository eventAssignmentRepository;
    private final EventAccessService eventAccessService;
    private final EventDtoAssembler eventDtoAssembler;
    private final EventStoreSyncService eventStoreSyncService;
    private final AccountHolderService accountHolderService;
    private final ShopOrderService shopOrderService;

    @Transactional
    public EventResponse create(CreateEventRequest request) {
        UUID organizerAccountHolderId = eventAccessService.resolveOrganizerAccountHolderId(request.organizerAccountHolderId());
        accountHolderService.getById(organizerAccountHolderId);
        validateTimeRange(request.startDateTime(), request.endDateTime());

        Event savedEvent = eventRepository.save(Event.builder()
                .title(request.title().trim())
                .description(trimToNull(request.description()))
                .country(normalizeCountry(request.country()))
                .startDateTime(request.startDateTime())
                .endDateTime(request.endDateTime())
                .locationType(request.locationType())
                .locationAddress(trimToNull(request.locationAddress()))
                .videoLink(trimToNull(request.videoLink()))
                .organizerAccountHolderId(organizerAccountHolderId)
                .status(EventStatus.DRAFT)
                .build());

        ensureOrganizerAssignment(savedEvent.getId(), organizerAccountHolderId);
        replaceSpeakers(savedEvent, request.speakers());
        replaceControlAgents(savedEvent.getId(), organizerAccountHolderId, request.controlAgentAccountHolderIds());
        return eventDtoAssembler.toResponse(savedEvent);
    }

    @Transactional(readOnly = true)
    public List<EventResponse> listVisibleEvents() {
        List<Event> events = eventRepository.findAll(Sort.by(Sort.Direction.ASC, "startDateTime"));
        if (eventAccessService.isAdmin()) {
            return events.stream().map(eventDtoAssembler::toResponse).toList();
        }

        UUID currentAccountHolderId = eventAccessService.currentAccountHolderId();
        Set<UUID> assignedEventIds = eventAssignmentRepository.findByAccountHolderId(currentAccountHolderId).stream()
                .map(EventAssignment::getEventId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        return events.stream()
                .filter(event -> event.getStatus() == EventStatus.PUBLISHED
                        || event.getOrganizerAccountHolderId().equals(currentAccountHolderId)
                        || assignedEventIds.contains(event.getId()))
                .map(eventDtoAssembler::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public EventResponse get(UUID eventId) {
        Event event = findEvent(eventId);
        eventAccessService.assertCanView(event);
        return eventDtoAssembler.toResponse(event);
    }

    @Transactional
    public EventResponse update(UUID eventId, UpdateEventRequest request) {
        Event event = findEvent(eventId);
        eventAccessService.assertCanManage(event);
        ensureMutable(event);

        if (request.title() != null && !request.title().isBlank()) {
            event.setTitle(request.title().trim());
        }
        if (request.description() != null) {
            event.setDescription(trimToNull(request.description()));
        }
        if (request.country() != null && !request.country().isBlank()) {
            event.setCountry(normalizeCountry(request.country()));
        }
        if (request.startDateTime() != null) {
            event.setStartDateTime(request.startDateTime());
        }
        if (request.endDateTime() != null) {
            event.setEndDateTime(request.endDateTime());
        }
        validateTimeRange(event.getStartDateTime(), event.getEndDateTime());

        if (request.locationType() != null) {
            event.setLocationType(request.locationType());
        }
        if (request.locationAddress() != null) {
            event.setLocationAddress(trimToNull(request.locationAddress()));
        }
        if (request.videoLink() != null) {
            event.setVideoLink(trimToNull(request.videoLink()));
        }

        Event savedEvent = eventRepository.save(event);
        if (request.speakers() != null) {
            replaceSpeakers(savedEvent, request.speakers());
        }
        if (request.controlAgentAccountHolderIds() != null) {
            replaceControlAgents(savedEvent.getId(), savedEvent.getOrganizerAccountHolderId(), request.controlAgentAccountHolderIds());
        }

        eventTicketTypeRepository.findByEventIdOrderByCreatedAtAsc(savedEvent.getId())
                .forEach(ticketType -> eventStoreSyncService.enqueueUpsert(ticketType.getId()));
        return eventDtoAssembler.toResponse(savedEvent);
    }

    @Transactional
    public EventResponse publish(UUID eventId) {
        Event event = findEvent(eventId);
        eventAccessService.assertCanManage(event);
        if (event.getStatus() != EventStatus.DRAFT) {
            throw new BusinessException("Only draft events can be published");
        }
        validateTimeRange(event.getStartDateTime(), event.getEndDateTime());

        List<EventTicketType> ticketTypes = eventTicketTypeRepository.findByEventIdOrderByCreatedAtAsc(eventId);
        if (ticketTypes.isEmpty()) {
            throw new BusinessException("At least one ticket type is required before publishing");
        }

        event.setStatus(EventStatus.PUBLISHED);
        Event savedEvent = eventRepository.save(event);
        ticketTypes.forEach(ticketType -> eventStoreSyncService.enqueueUpsert(ticketType.getId()));
        return eventDtoAssembler.toResponse(savedEvent);
    }

    @Transactional
    public EventResponse complete(UUID eventId) {
        Event event = findEvent(eventId);
        eventAccessService.assertCanManage(event);
        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new BusinessException("Only published events can be completed");
        }

        event.setStatus(EventStatus.COMPLETED);
        Event savedEvent = eventRepository.save(event);

        UUID actorId = eventAccessService.currentActorId();
        for (UUID orderId : eventTicketRepository.findDistinctOrderIdsByEventIdAndStatusIn(
                eventId,
                List.of(EventTicketStatus.VALID, EventTicketStatus.USED)
        )) {
            ShopOrderStatus orderStatus = shopOrderService.findOrder(orderId).getStatus();
            if (orderStatus == ShopOrderStatus.PAID || orderStatus == ShopOrderStatus.SHIPPED) {
                shopOrderService.updateStatus(
                        orderId,
                        actorId,
                        new UpdateShopOrderStatusRequest(ShopOrderStatus.DELIVERED, null, null, null)
                );
            }
        }

        eventTicketTypeRepository.findByEventIdOrderByCreatedAtAsc(eventId)
                .forEach(ticketType -> eventStoreSyncService.enqueueRefresh(ticketType.getId()));
        return eventDtoAssembler.toResponse(savedEvent);
    }

    @Transactional
    public EventResponse cancel(UUID eventId) {
        Event event = findEvent(eventId);
        eventAccessService.assertCanManage(event);
        if (event.getStatus() == EventStatus.COMPLETED || event.getStatus() == EventStatus.CANCELLED) {
            throw new BusinessException("Completed or cancelled events cannot be cancelled again");
        }

        event.setStatus(EventStatus.CANCELLED);
        Event savedEvent = eventRepository.save(event);

        List<EventTicket> validTickets = eventTicketRepository.findByEventIdAndStatusIn(eventId, List.of(EventTicketStatus.VALID));
        validTickets.forEach(ticket -> ticket.setStatus(EventTicketStatus.INVALID));
        eventTicketRepository.saveAll(validTickets);

        eventTicketTypeRepository.findByEventIdOrderByCreatedAtAsc(eventId)
                .forEach(ticketType -> eventStoreSyncService.enqueueRefresh(ticketType.getId()));
        return eventDtoAssembler.toResponse(savedEvent);
    }

    @Transactional(readOnly = true)
    public EventStatsResponse stats(UUID eventId) {
        Event event = findEvent(eventId);
        eventAccessService.assertCanManage(event);

        List<EventTicket> tickets = eventTicketRepository.findByEventIdAndStatusIn(
                eventId,
                List.of(EventTicketStatus.VALID, EventTicketStatus.USED, EventTicketStatus.INVALID)
        );
        long ticketsSold = tickets.size();
        long attendance = tickets.stream().filter(ticket -> ticket.getStatus() == EventTicketStatus.USED).count();
        BigDecimal revenue = tickets.stream()
                .map(ticket -> ticket.getTicketType().getPrice())
                .reduce(ZERO, BigDecimal::add)
                .setScale(4, RoundingMode.HALF_UP);
        BigDecimal conversionRate = ticketsSold == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(attendance)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(ticketsSold), 2, RoundingMode.HALF_UP);
        return new EventStatsResponse(ticketsSold, revenue, attendance, conversionRate);
    }

    @Transactional(readOnly = true)
    public Event findEvent(UUID eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found"));
    }

    private void ensureMutable(Event event) {
        if (event.getStatus() == EventStatus.COMPLETED || event.getStatus() == EventStatus.CANCELLED) {
            throw new BusinessException("Completed or cancelled events cannot be modified");
        }
    }

    private void ensureOrganizerAssignment(UUID eventId, UUID organizerAccountHolderId) {
        if (eventAssignmentRepository.existsByEventIdAndAccountHolderIdAndRoleIn(
                eventId,
                organizerAccountHolderId,
                List.of(EventAssignmentRole.ORGANIZER)
        )) {
            return;
        }
        eventAssignmentRepository.save(EventAssignment.builder()
                .eventId(eventId)
                .accountHolderId(organizerAccountHolderId)
                .role(EventAssignmentRole.ORGANIZER)
                .build());
    }

    private void replaceSpeakers(Event event, List<EventSpeakerRequest> speakers) {
        eventSpeakerRepository.deleteByEventId(event.getId());
        if (speakers == null || speakers.isEmpty()) {
            return;
        }
        List<EventSpeaker> entities = speakers.stream()
                .map(speaker -> EventSpeaker.builder()
                        .event(event)
                        .name(speaker.name().trim())
                        .title(trimToNull(speaker.title()))
                        .photo(trimToNull(speaker.photo()))
                        .build())
                .toList();
        eventSpeakerRepository.saveAll(entities);
    }

    private void replaceControlAgents(UUID eventId,
                                      UUID organizerAccountHolderId,
                                      List<UUID> controlAgentAccountHolderIds) {
        eventAssignmentRepository.deleteByEventIdAndRole(eventId, EventAssignmentRole.CONTROL_AGENT);
        if (controlAgentAccountHolderIds == null || controlAgentAccountHolderIds.isEmpty()) {
            return;
        }
        Set<UUID> uniqueIds = controlAgentAccountHolderIds.stream()
                .filter(id -> id != null && !id.equals(organizerAccountHolderId))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        uniqueIds.forEach(accountHolderService::getById);
        List<EventAssignment> assignments = uniqueIds.stream()
                .map(accountHolderId -> EventAssignment.builder()
                        .eventId(eventId)
                        .accountHolderId(accountHolderId)
                        .role(EventAssignmentRole.CONTROL_AGENT)
                        .build())
                .toList();
        eventAssignmentRepository.saveAll(assignments);
    }

    private void validateTimeRange(OffsetDateTime startDateTime, OffsetDateTime endDateTime) {
        if (startDateTime == null || endDateTime == null || !endDateTime.isAfter(startDateTime)) {
            throw new BusinessException("Event endDateTime must be after startDateTime");
        }
    }

    private String normalizeCountry(String country) {
        return country.trim().toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
