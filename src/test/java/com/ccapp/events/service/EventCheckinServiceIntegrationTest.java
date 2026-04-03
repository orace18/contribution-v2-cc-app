package com.ccapp.events.service;

import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.common.security.AuthenticatedPrincipal;
import com.ccapp.events.domain.entity.Event;
import com.ccapp.events.domain.entity.EventAssignment;
import com.ccapp.events.domain.entity.EventTicket;
import com.ccapp.events.domain.entity.EventTicketType;
import com.ccapp.events.domain.enums.EventAssignmentRole;
import com.ccapp.events.domain.enums.EventCheckinResult;
import com.ccapp.events.domain.enums.EventLocationType;
import com.ccapp.events.domain.enums.EventStatus;
import com.ccapp.events.domain.enums.EventTicketStatus;
import com.ccapp.events.dto.EventCheckinResponse;
import com.ccapp.events.event.MemberEventAttendanceEvent;
import com.ccapp.events.repository.EventAssignmentRepository;
import com.ccapp.events.repository.EventCheckinAttemptRepository;
import com.ccapp.events.repository.EventRepository;
import com.ccapp.events.repository.EventTicketRepository;
import com.ccapp.events.repository.EventTicketTypeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({EventCheckinService.class, EventTicketQrService.class, EventAccessService.class})
class EventCheckinServiceIntegrationTest {

    @Autowired
    private EventCheckinService eventCheckinService;
    @Autowired
    private EventTicketQrService eventTicketQrService;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private EventAssignmentRepository eventAssignmentRepository;
    @Autowired
    private EventTicketTypeRepository eventTicketTypeRepository;
    @Autowired
    private EventTicketRepository eventTicketRepository;
    @Autowired
    private EventCheckinAttemptRepository eventCheckinAttemptRepository;

    @MockBean
    private DomainEventPublisher domainEventPublisher;

    private UUID scannerId;

    @BeforeEach
    void setUp() {
        scannerId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                AuthenticatedPrincipal.builder()
                        .userId(scannerId)
                        .accountHolderId(scannerId)
                        .internal(false)
                        .build(),
                null,
                List.of()
        ));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void checkin_shouldMarkTicketUsedAndRejectSecondScan() {
        Event event = eventRepository.save(Event.builder()
                .title("Conference")
                .country("BJ")
                .startDateTime(OffsetDateTime.now().minusHours(1))
                .endDateTime(OffsetDateTime.now().plusHours(4))
                .locationType(EventLocationType.PHYSICAL)
                .organizerAccountHolderId(UUID.randomUUID())
                .status(EventStatus.PUBLISHED)
                .build());
        eventAssignmentRepository.save(EventAssignment.builder()
                .eventId(event.getId())
                .accountHolderId(scannerId)
                .role(EventAssignmentRole.CONTROL_AGENT)
                .build());

        EventTicketType ticketType = eventTicketTypeRepository.save(EventTicketType.builder()
                .event(event)
                .name("STANDARD")
                .price(new BigDecimal("5000.0000"))
                .currency("XOF")
                .stock(20)
                .cv(new BigDecimal("3.0000"))
                .platformCommissionRate(new BigDecimal("10.00"))
                .dsbAmount(new BigDecimal("500.0000"))
                .build());
        EventTicket ticket = eventTicketRepository.save(EventTicket.builder()
                .event(event)
                .orderId(UUID.randomUUID())
                .buyerAccountHolderId(UUID.randomUUID())
                .ticketType(ticketType)
                .qrCode("pending:" + UUID.randomUUID())
                .status(EventTicketStatus.VALID)
                .build());
        ticket.setQrCode(eventTicketQrService.generate(ticket));
        eventTicketRepository.save(ticket);

        EventCheckinResponse firstScan = eventCheckinService.checkin(ticket.getQrCode());
        EventCheckinResponse secondScan = eventCheckinService.checkin(ticket.getQrCode());

        assertEquals(EventCheckinResult.VALID, firstScan.result());
        assertEquals(EventCheckinResult.ALREADY_USED, secondScan.result());
        assertEquals(2, eventCheckinAttemptRepository.count());
        verify(domainEventPublisher, times(1)).publish(ArgumentMatchers.any(MemberEventAttendanceEvent.class));
    }
}
