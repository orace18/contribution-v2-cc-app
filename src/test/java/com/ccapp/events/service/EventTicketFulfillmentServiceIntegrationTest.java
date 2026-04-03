package com.ccapp.events.service;

import com.ccapp.events.domain.entity.Event;
import com.ccapp.events.domain.entity.EventTicket;
import com.ccapp.events.domain.entity.EventTicketType;
import com.ccapp.events.domain.enums.EventLocationType;
import com.ccapp.events.domain.enums.EventProductSyncStatus;
import com.ccapp.events.domain.enums.EventStatus;
import com.ccapp.events.repository.EventRepository;
import com.ccapp.events.repository.EventTicketRepository;
import com.ccapp.events.repository.EventTicketTypeRepository;
import com.ccapp.shop.domain.entity.ShopOrder;
import com.ccapp.shop.domain.entity.ShopOrderItem;
import com.ccapp.shop.domain.entity.ShopProduct;
import com.ccapp.shop.domain.enums.ShopOrderStatus;
import com.ccapp.shop.repository.ShopOrderItemRepository;
import com.ccapp.shop.repository.ShopOrderRepository;
import com.ccapp.shop.repository.ShopProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({EventTicketFulfillmentService.class, EventTicketQrService.class})
class EventTicketFulfillmentServiceIntegrationTest {

    @Autowired
    private EventTicketFulfillmentService eventTicketFulfillmentService;
    @Autowired
    private EventTicketQrService eventTicketQrService;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private EventTicketTypeRepository eventTicketTypeRepository;
    @Autowired
    private EventTicketRepository eventTicketRepository;
    @Autowired
    private ShopProductRepository shopProductRepository;
    @Autowired
    private ShopOrderRepository shopOrderRepository;
    @Autowired
    private ShopOrderItemRepository shopOrderItemRepository;

    @MockBean
    private EventStoreSyncService eventStoreSyncService;

    @Test
    void fulfillPaidOrder_shouldIssueTicketsAndDecreaseStock() {
        Event event = eventRepository.save(Event.builder()
                .title("Summit")
                .country("BJ")
                .startDateTime(OffsetDateTime.now().plusDays(3))
                .endDateTime(OffsetDateTime.now().plusDays(4))
                .locationType(EventLocationType.HYBRID)
                .organizerAccountHolderId(UUID.randomUUID())
                .status(EventStatus.PUBLISHED)
                .build());

        ShopProduct product = shopProductRepository.save(ShopProduct.builder()
                .merchantId(event.getOrganizerAccountHolderId())
                .name("Summit - VIP")
                .description("VIP access")
                .price(new BigDecimal("20000.0000"))
                .currency("XOF")
                .commissionPercent(new BigDecimal("12.00"))
                .cvValue(new BigDecimal("10.0000"))
                .active(true)
                .build());

        EventTicketType ticketType = eventTicketTypeRepository.save(EventTicketType.builder()
                .event(event)
                .name("VIP")
                .price(new BigDecimal("20000.0000"))
                .currency("XOF")
                .stock(2)
                .cv(new BigDecimal("10.0000"))
                .platformCommissionRate(new BigDecimal("12.00"))
                .dsbAmount(new BigDecimal("1000.0000"))
                .shopProductId(product.getId())
                .productSyncStatus(EventProductSyncStatus.SYNCED)
                .build());

        UUID buyerId = UUID.randomUUID();
        ShopOrder order = shopOrderRepository.save(ShopOrder.builder()
                .buyerId(buyerId)
                .merchantId(event.getOrganizerAccountHolderId())
                .countryCode("BJ")
                .status(ShopOrderStatus.PAID)
                .totalAmount(new BigDecimal("40000.0000"))
                .currency("XOF")
                .paymentMethod("WALLET")
                .deliveryAddressId(UUID.randomUUID())
                .build());
        shopOrderItemRepository.save(ShopOrderItem.builder()
                .order(order)
                .productId(product.getId())
                .quantity(2)
                .unitPrice(new BigDecimal("20000.0000"))
                .commissionPercent(new BigDecimal("12.00"))
                .cvValue(new BigDecimal("10.0000"))
                .build());

        eventTicketFulfillmentService.fulfillPaidOrder(order.getId(), buyerId);

        List<EventTicket> tickets = eventTicketRepository.findByOrderId(order.getId());
        EventTicketType updatedTicketType = eventTicketTypeRepository.findById(ticketType.getId()).orElseThrow();

        assertEquals(2, tickets.size());
        assertEquals(0, updatedTicketType.getStock());
        assertTrue(tickets.stream().allMatch(ticket -> eventTicketQrService.decodeAndVerify(ticket.getQrCode()).valid()));
        verify(eventStoreSyncService, atLeastOnce()).enqueueRefresh(ticketType.getId());
    }
}
