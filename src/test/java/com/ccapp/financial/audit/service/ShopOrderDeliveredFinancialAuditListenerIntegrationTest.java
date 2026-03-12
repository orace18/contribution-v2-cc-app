package com.ccapp.financial.audit.service;

import com.ccapp.common.events.IdempotentEventListenerExecutor;
import com.ccapp.financial.audit.repository.FinancialAuditLogRepository;
import com.ccapp.shop.event.ShopOrderDeliveredEvent;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({ShopOrderDeliveredFinancialAuditListener.class, FinancialAuditService.class})
class ShopOrderDeliveredFinancialAuditListenerIntegrationTest {

    @Autowired
    private ShopOrderDeliveredFinancialAuditListener listener;
    @Autowired
    private FinancialAuditLogRepository financialAuditLogRepository;

    @MockBean
    private IdempotentEventListenerExecutor listenerExecutor;

    @BeforeEach
    void setUp() {
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(2);
            runnable.run();
            return null;
        }).when(listenerExecutor).execute(anyString(), any(), any());
    }

    @Test
    void on_shouldPersistMerchantPayoutAuditRecord() {
        UUID orderId = UUID.randomUUID();
        listener.on(new ShopOrderDeliveredEvent(
                UUID.randomUUID(),
                OffsetDateTime.now(),
                orderId,
                UUID.randomUUID(),
                new BigDecimal("10000.0000"),
                "XOF",
                new BigDecimal("8000.0000"),
                new BigDecimal("2000.0000"),
                new BigDecimal("1200.0000")
        ));

        var logs = financialAuditLogRepository.findAll();
        assertEquals(1, logs.size());
        assertEquals("MERCHANT_PAYOUT", logs.getFirst().getEventType());
        assertEquals(orderId.toString(), logs.getFirst().getReferenceId());
        assertTrue(logs.getFirst().getMetadataJson().contains("mlmVolume"));
    }
}
