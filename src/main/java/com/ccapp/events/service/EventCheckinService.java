package com.ccapp.events.service;

import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.events.domain.entity.Event;
import com.ccapp.events.domain.entity.EventCheckinAttempt;
import com.ccapp.events.domain.entity.EventTicket;
import com.ccapp.events.domain.enums.EventCheckinResult;
import com.ccapp.events.domain.enums.EventStatus;
import com.ccapp.events.domain.enums.EventTicketStatus;
import com.ccapp.events.dto.EventBatchCheckinItemRequest;
import com.ccapp.events.dto.EventBatchCheckinItemResponse;
import com.ccapp.events.dto.EventCheckinResponse;
import com.ccapp.events.event.MemberEventAttendanceEvent;
import com.ccapp.events.repository.EventCheckinAttemptRepository;
import com.ccapp.events.repository.EventTicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventCheckinService {

    private final EventTicketRepository eventTicketRepository;
    private final EventCheckinAttemptRepository eventCheckinAttemptRepository;
    private final EventTicketQrService eventTicketQrService;
    private final EventAccessService eventAccessService;
    private final DomainEventPublisher domainEventPublisher;

    @Transactional
    public EventCheckinResponse checkin(String qrCode) {
        ScanOutcome outcome = processScan(null, qrCode, OffsetDateTime.now(), null);
        return new EventCheckinResponse(
                outcome.result(),
                outcome.ticketId(),
                outcome.eventId(),
                outcome.checkinTimestamp()
        );
    }

    @Transactional
    public List<EventBatchCheckinItemResponse> batchCheckin(List<EventBatchCheckinItemRequest> items) {
        return items.stream()
                .map(this::processBatchItem)
                .toList();
    }

    private EventBatchCheckinItemResponse processBatchItem(EventBatchCheckinItemRequest item) {
        if (item.clientScanId() != null && !item.clientScanId().isBlank()) {
            EventCheckinAttempt existingAttempt = eventCheckinAttemptRepository.findByClientScanId(item.clientScanId().trim()).orElse(null);
            if (existingAttempt != null) {
                return new EventBatchCheckinItemResponse(
                        existingAttempt.getClientScanId(),
                        existingAttempt.getResult(),
                        existingAttempt.getTicketId(),
                        existingAttempt.getEventId(),
                        existingAttempt.getProcessedAt()
                );
            }
        }
        ScanOutcome outcome = processScan(item.clientScanId(), item.qrCode(), item.scannedAt(), item.sourceDeviceId());
        return new EventBatchCheckinItemResponse(
                item.clientScanId(),
                outcome.result(),
                outcome.ticketId(),
                outcome.eventId(),
                outcome.checkinTimestamp()
        );
    }

    private ScanOutcome processScan(String clientScanId,
                                    String qrCode,
                                    OffsetDateTime scannedAt,
                                    String sourceDeviceId) {
        String qrHash = hash(qrCode);
        EventTicketQrService.DecodedQrPayload payload = eventTicketQrService.decodeAndVerify(qrCode);
        if (!payload.valid()) {
            EventCheckinAttempt attempt = saveAttempt(clientScanId, sourceDeviceId, null, null, qrHash, scannedAt, EventCheckinResult.INVALID, OffsetDateTime.now());
            return ScanOutcome.fromAttempt(attempt);
        }

        eventAccessService.assertCanScan(payload.eventId());
        EventTicket ticket = eventTicketRepository.lockById(payload.ticketId()).orElse(null);
        if (ticket == null || !matchesPayload(ticket, payload)) {
            EventCheckinAttempt attempt = saveAttempt(clientScanId, sourceDeviceId, payload.eventId(), payload.ticketId(), qrHash,
                    scannedAt, EventCheckinResult.INVALID, OffsetDateTime.now());
            return ScanOutcome.fromAttempt(attempt);
        }

        Event event = ticket.getEvent();
        if (event.getStatus() == EventStatus.CANCELLED || ticket.getStatus() == EventTicketStatus.INVALID || ticket.getStatus() == EventTicketStatus.REFUNDED) {
            EventCheckinAttempt attempt = saveAttempt(clientScanId, sourceDeviceId, event.getId(), ticket.getId(), qrHash,
                    scannedAt, EventCheckinResult.INVALID, OffsetDateTime.now());
            return ScanOutcome.fromAttempt(attempt);
        }
        if (ticket.getStatus() == EventTicketStatus.USED) {
            EventCheckinAttempt attempt = saveAttempt(clientScanId, sourceDeviceId, event.getId(), ticket.getId(), qrHash,
                    scannedAt, EventCheckinResult.ALREADY_USED, ticket.getCheckinTimestamp());
            return ScanOutcome.fromAttempt(attempt);
        }

        OffsetDateTime checkinTimestamp = OffsetDateTime.now();
        ticket.setStatus(EventTicketStatus.USED);
        ticket.setCheckinTimestamp(checkinTimestamp);
        eventTicketRepository.save(ticket);

        domainEventPublisher.publish(MemberEventAttendanceEvent.create(
                event.getId(),
                ticket.getId(),
                ticket.getTicketType().getId(),
                ticket.getBuyerAccountHolderId(),
                checkinTimestamp
        ));

        EventCheckinAttempt attempt = saveAttempt(clientScanId, sourceDeviceId, event.getId(), ticket.getId(), qrHash,
                scannedAt, EventCheckinResult.VALID, checkinTimestamp);
        return ScanOutcome.fromAttempt(attempt);
    }

    private EventCheckinAttempt saveAttempt(String clientScanId,
                                            String sourceDeviceId,
                                            UUID eventId,
                                            UUID ticketId,
                                            String qrHash,
                                            OffsetDateTime scannedAt,
                                            EventCheckinResult result,
                                            OffsetDateTime processedAt) {
        return eventCheckinAttemptRepository.save(EventCheckinAttempt.builder()
                .clientScanId(trimToNull(clientScanId))
                .sourceDeviceId(trimToNull(sourceDeviceId))
                .eventId(eventId)
                .ticketId(ticketId)
                .qrHash(qrHash)
                .clientScannedAt(scannedAt)
                .result(result)
                .processedAt(processedAt == null ? OffsetDateTime.now() : processedAt)
                .build());
    }

    private boolean matchesPayload(EventTicket ticket, EventTicketQrService.DecodedQrPayload payload) {
        return ticket.getId().equals(payload.ticketId())
                && ticket.getEvent().getId().equals(payload.eventId())
                && ticket.getBuyerAccountHolderId().equals(payload.buyerId());
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record ScanOutcome(EventCheckinResult result,
                               UUID ticketId,
                               UUID eventId,
                               OffsetDateTime checkinTimestamp) {
        private static ScanOutcome fromAttempt(EventCheckinAttempt attempt) {
            return new ScanOutcome(
                    attempt.getResult(),
                    attempt.getTicketId(),
                    attempt.getEventId(),
                    attempt.getProcessedAt()
            );
        }
    }
}
