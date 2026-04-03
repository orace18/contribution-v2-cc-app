package com.ccapp.events.service;

import com.ccapp.commerce.domain.entity.CommerceProduct;
import com.ccapp.commerce.repository.CommerceProductRepository;
import com.ccapp.commerce.service.CommerceMerchantService;
import com.ccapp.events.domain.entity.Event;
import com.ccapp.events.domain.entity.EventStoreSyncJob;
import com.ccapp.events.domain.entity.EventTicketType;
import com.ccapp.events.domain.enums.EventLocationType;
import com.ccapp.events.domain.enums.EventProductSyncStatus;
import com.ccapp.events.domain.enums.EventStatus;
import com.ccapp.events.repository.EventRepository;
import com.ccapp.events.repository.EventStoreSyncJobRepository;
import com.ccapp.events.repository.EventTicketTypeRepository;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.domain.enums.AccountType;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.shop.domain.entity.ShopProduct;
import com.ccapp.shop.repository.ShopProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({EventStoreSyncService.class, CommerceMerchantService.class})
class EventStoreSyncServiceIntegrationTest {

    @Autowired
    private EventStoreSyncService eventStoreSyncService;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private EventTicketTypeRepository eventTicketTypeRepository;
    @Autowired
    private EventStoreSyncJobRepository eventStoreSyncJobRepository;
    @Autowired
    private ShopProductRepository shopProductRepository;
    @Autowired
    private CommerceProductRepository commerceProductRepository;
    @Autowired
    private AccountHolderRepository accountHolderRepository;

    @MockBean
    private AccountHolderService accountHolderService;

    @BeforeEach
    void setUp() {
        when(accountHolderService.getById(any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return accountHolderRepository.findById(id).orElseThrow();
        });
    }

    @Test
    void processSingleJob_shouldCreateAndActivateStoreProductForTicketType() {
        UUID organizerId = createAccountHolder("Grace", "Organizer");
        Event event = eventRepository.save(Event.builder()
                .title("Grace Conference")
                .description("Annual leadership summit")
                .country("BJ")
                .startDateTime(OffsetDateTime.now().plusDays(5))
                .endDateTime(OffsetDateTime.now().plusDays(6))
                .locationType(EventLocationType.PHYSICAL)
                .locationAddress("Cotonou")
                .organizerAccountHolderId(organizerId)
                .status(EventStatus.DRAFT)
                .build());
        EventTicketType ticketType = eventTicketTypeRepository.save(EventTicketType.builder()
                .event(event)
                .name("VIP")
                .price(new BigDecimal("25000.0000"))
                .currency("XOF")
                .stock(10)
                .cv(new BigDecimal("15.0000"))
                .platformCommissionRate(new BigDecimal("10.00"))
                .dsbAmount(new BigDecimal("3000.0000"))
                .productSyncStatus(EventProductSyncStatus.PENDING)
                .build());

        eventStoreSyncService.enqueueUpsert(ticketType.getId());
        EventStoreSyncJob upsertJob = eventStoreSyncJobRepository.findAll().getFirst();
        eventStoreSyncService.processSingleJob(upsertJob.getId());

        EventTicketType syncedTicketType = eventTicketTypeRepository.findById(ticketType.getId()).orElseThrow();
        ShopProduct draftProduct = shopProductRepository.findById(syncedTicketType.getShopProductId()).orElseThrow();
        CommerceProduct commerceProduct = commerceProductRepository.findById(draftProduct.getId()).orElseThrow();

        assertFalse(draftProduct.isActive());
        assertEquals("Grace Conference - VIP", draftProduct.getName());
        assertEquals(0, commerceProduct.getDsbAmount().compareTo(new BigDecimal("3000.0000")));

        event.setStatus(EventStatus.PUBLISHED);
        eventRepository.save(event);

        eventStoreSyncService.enqueueRefresh(ticketType.getId());
        EventStoreSyncJob refreshJob = eventStoreSyncJobRepository.findAll().stream()
                .filter(job -> !job.getId().equals(upsertJob.getId()))
                .findFirst()
                .orElseThrow();
        eventStoreSyncService.processSingleJob(refreshJob.getId());

        ShopProduct publishedProduct = shopProductRepository.findById(syncedTicketType.getShopProductId()).orElseThrow();
        assertTrue(publishedProduct.isActive());
    }

    private UUID createAccountHolder(String firstName, String lastName) {
        return accountHolderRepository.save(AccountHolder.builder()
                .id(UUID.randomUUID())
                .firstName(firstName)
                .lastName(lastName)
                .phoneNumber("229" + UUID.randomUUID().toString().replace("-", "").substring(0, 10))
                .countryCode("BJ")
                .serviceCountryCode("BJ")
                .accountType(AccountType.STANDARD)
                .build()).getId();
    }
}
