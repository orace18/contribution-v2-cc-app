package com.ccapp.events.service;

import com.ccapp.events.domain.entity.Event;
import com.ccapp.events.domain.entity.EventAssignment;
import com.ccapp.events.domain.entity.EventSpeaker;
import com.ccapp.events.domain.entity.EventTicket;
import com.ccapp.events.domain.entity.EventTicketType;
import com.ccapp.events.domain.enums.EventAssignmentRole;
import com.ccapp.events.dto.EventResponse;
import com.ccapp.events.dto.EventSpeakerResponse;
import com.ccapp.events.dto.EventTicketTypeResponse;
import com.ccapp.events.dto.MyEventTicketResponse;
import com.ccapp.events.repository.EventAssignmentRepository;
import com.ccapp.events.repository.EventSpeakerRepository;
import com.ccapp.events.repository.EventTicketTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class EventDtoAssembler {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

    private final EventSpeakerRepository eventSpeakerRepository;
    private final EventTicketTypeRepository eventTicketTypeRepository;
    private final EventAssignmentRepository eventAssignmentRepository;

    @Transactional(readOnly = true)
    public EventResponse toResponse(Event event) {
        List<EventSpeakerResponse> speakers = eventSpeakerRepository.findByEventIdOrderByCreatedAtAsc(event.getId()).stream()
                .map(this::toSpeakerResponse)
                .toList();
        List<EventTicketTypeResponse> ticketTypes = eventTicketTypeRepository.findByEventIdOrderByCreatedAtAsc(event.getId()).stream()
                .map(this::toTicketTypeResponse)
                .toList();
        List<UUID> controlAgentAccountHolderIds = eventAssignmentRepository.findByEventId(event.getId()).stream()
                .filter(assignment -> assignment.getRole() == EventAssignmentRole.CONTROL_AGENT)
                .map(EventAssignment::getAccountHolderId)
                .toList();
        return new EventResponse(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                event.getCountry(),
                event.getStartDateTime(),
                event.getEndDateTime(),
                event.getLocationType(),
                event.getLocationAddress(),
                event.getVideoLink(),
                event.getOrganizerAccountHolderId(),
                event.getStatus(),
                speakers,
                ticketTypes,
                controlAgentAccountHolderIds
        );
    }

    public EventTicketTypeResponse toTicketTypeResponse(EventTicketType ticketType) {
        return new EventTicketTypeResponse(
                ticketType.getId(),
                ticketType.getEvent().getId(),
                ticketType.getName(),
                scale(ticketType.getPrice()),
                ticketType.getCurrency(),
                ticketType.getStock(),
                scale(ticketType.getCv()),
                scaleRate(ticketType.getPlatformCommissionRate()),
                scale(ticketType.getDsbAmount()),
                ticketType.getShopProductId(),
                ticketType.getProductSyncStatus()
        );
    }

    public MyEventTicketResponse toMyTicketResponse(EventTicket ticket) {
        return new MyEventTicketResponse(
                ticket.getId(),
                ticket.getEvent().getId(),
                ticket.getEvent().getTitle(),
                ticket.getEvent().getStartDateTime(),
                ticket.getEvent().getEndDateTime(),
                ticket.getOrderId(),
                ticket.getBuyerAccountHolderId(),
                ticket.getTicketType().getId(),
                ticket.getTicketType().getName(),
                ticket.getQrCode(),
                ticket.getStatus(),
                ticket.getCheckinTimestamp()
        );
    }

    private EventSpeakerResponse toSpeakerResponse(EventSpeaker speaker) {
        return new EventSpeakerResponse(
                speaker.getId(),
                speaker.getEvent().getId(),
                speaker.getName(),
                speaker.getTitle(),
                speaker.getPhoto()
        );
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
