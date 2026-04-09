package com.ccapp.ledger.repository;

import com.ccapp.ledger.domain.entity.AccountingEvent;
import com.ccapp.ledger.domain.entity.LedgerEntry;
import com.ccapp.ledger.domain.enums.AccountingEventType;
import com.ccapp.ledger.domain.enums.LedgerEntryType;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.repository.OperationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class LedgerEntryRepositoryIntegrationTest {

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;
    @Autowired
    private AccountingEventRepository accountingEventRepository;
    @Autowired
    private OperationRepository operationRepository;

    @Test
    void searchExplorer_shouldReturnResultsWhenAccountCodeFilterIsNull() {
        seedLedgerEntry("acc-subscription", OperationType.SUBSCRIPTION, OffsetDateTime.parse("2026-04-09T10:00:00Z"));
        seedLedgerEntry("acc-wallet", OperationType.WALLET_PAYMENT, OffsetDateTime.parse("2026-04-09T09:00:00Z"));

        Page<LedgerEntry> result = ledgerEntryRepository.searchExplorer(
                null,
                null,
                null,
                null,
                PageRequest.of(0, 10)
        );

        assertEquals(2, result.getTotalElements());
        assertEquals("acc-subscription", result.getContent().get(0).getAccountCode());
        assertEquals("acc-wallet", result.getContent().get(1).getAccountCode());
    }

    @Test
    void searchExplorer_shouldApplyAccountCodeFilterCaseInsensitively() {
        seedLedgerEntry("ACC-COMMISSION", OperationType.SUBSCRIPTION, OffsetDateTime.parse("2026-04-09T08:00:00Z"));
        seedLedgerEntry("ACC-WALLET", OperationType.WALLET_PAYMENT, OffsetDateTime.parse("2026-04-09T07:00:00Z"));

        Page<LedgerEntry> result = ledgerEntryRepository.searchExplorer(
                "acc-commission",
                null,
                null,
                null,
                PageRequest.of(0, 10)
        );

        assertEquals(1, result.getTotalElements());
        assertEquals("ACC-COMMISSION", result.getContent().get(0).getAccountCode());
    }

    private LedgerEntry seedLedgerEntry(String accountCode,
                                        OperationType operationType,
                                        OffsetDateTime postedAt) {
        Operation operation = operationRepository.save(Operation.builder()
                .accountHolderId(UUID.randomUUID())
                .actorUserId(UUID.randomUUID())
                .type(operationType)
                .status(OperationStatus.CONFIRMED)
                .amount(new BigDecimal("1000.0000"))
                .currency("XOF")
                .idempotencyKey("ledger-entry-test-" + UUID.randomUUID())
                .description("Ledger explorer test")
                .build());

        AccountingEvent event = accountingEventRepository.save(AccountingEvent.builder()
                .operation(operation)
                .eventType(AccountingEventType.OPERATION_CONFIRMED)
                .currency("XOF")
                .totalDebit(new BigDecimal("1000.0000"))
                .totalCredit(new BigDecimal("1000.0000"))
                .postedAt(postedAt)
                .build());

        return ledgerEntryRepository.save(LedgerEntry.builder()
                .event(event)
                .lineNo(1)
                .accountCode(accountCode)
                .entryType(LedgerEntryType.CREDIT)
                .amount(new BigDecimal("1000.0000"))
                .currency("XOF")
                .description("Ledger explorer test")
                .build());
    }
}
