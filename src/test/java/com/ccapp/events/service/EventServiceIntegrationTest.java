package com.ccapp.events.service;

import com.ccapp.common.security.AuthenticatedPrincipal;
import com.ccapp.events.domain.entity.Event;
import com.ccapp.events.domain.entity.EventTicket;
import com.ccapp.events.domain.entity.EventTicketType;
import com.ccapp.events.domain.enums.EventLocationType;
import com.ccapp.events.domain.enums.EventStatus;
import com.ccapp.events.domain.enums.EventTicketStatus;
import com.ccapp.events.repository.EventAssignmentRepository;
import com.ccapp.events.repository.EventRepository;
import com.ccapp.events.repository.EventSpeakerRepository;
import com.ccapp.events.repository.EventTicketRepository;
import com.ccapp.events.repository.EventTicketTypeRepository;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.shop.domain.entity.ShopOrder;
import com.ccapp.shop.domain.enums.ShopOrderStatus;
import com.ccapp.shop.dto.UpdateShopOrderStatusRequest;
import com.ccapp.shop.service.ShopOrderService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({EventService.class, EventAccessService.class, EventDtoAssembler.class})
class EventServiceIntegrationTest {

    @Autowired
    private EventService eventService;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private EventTicketTypeRepository eventTicketTypeRepository;
    @Autowired
    private EventTicketRepository eventTicketRepository;
    @Autowired
    private EventSpeakerRepository eventSpeakerRepository;
    @Autowired
    private EventAssignmentRepository eventAssignmentRepository;

    @MockBean
    private AccountHolderService accountHolderService;
    @MockBean
    private EventStoreSyncService eventStoreSyncService;
    @MockBean
    private ShopOrderService shopOrderService;

    private UUID organizerId;

    @BeforeEach
    void setUp() {
        organizerId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                AuthenticatedPrincipal.builder()
                        .userId(organizerId)
                        .accountHolderId(organizerId)
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
    void complete_shouldMarkEventCompletedAndDeliverPaidTicketOrders() {
        Event event = eventRepository.save(Event.builder()
                .title("Impact Summit")
                .country("BJ")
                .startDateTime(OffsetDateTime.now().minusHours(2))
                .endDateTime(OffsetDateTime.now().plusHours(2))
                .locationType(EventLocationType.HYBRID)
                .organizerAccountHolderId(organizerId)
                .status(EventStatus.PUBLISHED)
                .build());
        EventTicketType ticketType = eventTicketTypeRepository.save(EventTicketType.builder()
                .event(event)
                .name("STANDARD")
                .price(new BigDecimal("8000.0000"))
                .currency("XOF")
                .stock(5)
                .cv(new BigDecimal("4.0000"))
                .platformCommissionRate(new BigDecimal("10.00"))
                .dsbAmount(new BigDecimal("500.0000"))
                .build());
        UUID orderId = UUID.randomUUID();
        eventTicketRepository.save(EventTicket.builder()
                .event(event)
                .orderId(orderId)
                .buyerAccountHolderId(UUID.randomUUID())
                .ticketType(ticketType)
                .qrCode("pending:" + UUID.randomUUID())
                .status(EventTicketStatus.VALID)
                .build());

        when(shopOrderService.findOrder(orderId)).thenReturn(ShopOrder.builder()
                .id(orderId)
                .buyerId(UUID.randomUUID())
                .merchantId(organizerId)
                .countryCode("BJ")
                .status(ShopOrderStatus.PAID)
                .totalAmount(new BigDecimal("8000.0000"))
                .currency("XOF")
                .paymentMethod("WALLET")
                .deliveryAddressId(UUID.randomUUID())
                .build());
        when(shopOrderService.updateStatus(
                ArgumentMatchers.eq(orderId),
                ArgumentMatchers.eq(organizerId),
                ArgumentMatchers.any(UpdateShopOrderStatusRequest.class)
        )).thenReturn(null);

        eventService.complete(event.getId());

        assertEquals(EventStatus.COMPLETED, eventRepository.findById(event.getId()).orElseThrow().getStatus());
        verify(shopOrderService).updateStatus(
                ArgumentMatchers.eq(orderId),
                ArgumentMatchers.eq(organizerId),
                ArgumentMatchers.argThat(request -> request.status() == ShopOrderStatus.DELIVERED)
        );
        verify(eventStoreSyncService).enqueueRefresh(ticketType.getId());
    }
}
