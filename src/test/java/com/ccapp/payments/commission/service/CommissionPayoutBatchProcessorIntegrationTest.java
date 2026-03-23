package com.ccapp.payments.commission.service;

import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.country.config.domain.enums.CountryConfigType;
import com.ccapp.country.config.service.CountryConfigResolver;
import com.ccapp.platform.config.domain.enums.PlatformConfigType;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.domain.enums.AccountType;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.identity.wallet.repository.WalletRepository;
import com.ccapp.financial.audit.domain.entity.FinancialAuditLog;
import com.ccapp.financial.audit.repository.FinancialAuditLogRepository;
import com.ccapp.financial.audit.service.FinancialAuditService;
import com.ccapp.ledger.domain.entity.AccountingEvent;
import com.ccapp.ledger.domain.entity.LedgerEntry;
import com.ccapp.ledger.domain.enums.LedgerEntryType;
import com.ccapp.ledger.repository.AccountingEventRepository;
import com.ccapp.ledger.repository.LedgerEntryRepository;
import com.ccapp.ledger.service.AccountingPostingService;
import com.ccapp.country.config.service.CountryChartOfAccountsService;
import com.ccapp.payments.commission.domain.entity.CommissionPayoutBatch;
import com.ccapp.payments.commission.domain.entity.CommissionPayoutItem;
import com.ccapp.payments.commission.domain.entity.MlmCommission;
import com.ccapp.payments.commission.domain.enums.CommissionPayoutBatchStatus;
import com.ccapp.payments.commission.domain.enums.CommissionPayoutItemStatus;
import com.ccapp.payments.commission.domain.enums.CommissionStatus;
import com.ccapp.payments.commission.domain.enums.CommissionType;
import com.ccapp.payments.commission.repository.CommissionPayoutBatchRepository;
import com.ccapp.payments.commission.repository.CommissionPayoutItemRepository;
import com.ccapp.payments.commission.repository.MlmCommissionRepository;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.repository.OperationRepository;
import com.ccapp.payments.operation.service.OperationService;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
        CommissionPayoutBatchProcessor.class,
        OperationService.class,
        AccountingPostingService.class,
        CountryChartOfAccountsService.class,
        FinancialAuditService.class
})
class CommissionPayoutBatchProcessorIntegrationTest {

    @Autowired
    private CommissionPayoutBatchProcessor payoutBatchProcessor;
    @Autowired
    private CommissionPayoutBatchRepository payoutBatchRepository;
    @Autowired
    private CommissionPayoutItemRepository payoutItemRepository;
    @Autowired
    private MlmCommissionRepository mlmCommissionRepository;
    @Autowired
    private AccountHolderRepository accountHolderRepository;
    @Autowired
    private WalletRepository walletRepository;
    @Autowired
    private OperationRepository operationRepository;
    @Autowired
    private AccountingEventRepository accountingEventRepository;
    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;
    @Autowired
    private FinancialAuditLogRepository financialAuditLogRepository;
    @MockBean
    private CountryConfigResolver countryConfigResolver;
    @MockBean
    private DomainEventPublisher domainEventPublisher;

    @BeforeEach
    void setUp() {
        CountryConfigResolver.ResolvedConfig generalConfig = new CountryConfigResolver.ResolvedConfig(
                "BJ",
                CountryConfigType.GENERAL,
                1,
                "{\"taxRate\":10}",
                CountryConfigResolver.ConfigSource.COUNTRY
        );
        CountryConfigResolver.ResolvedPlatformConfig platformGeneralConfig = new CountryConfigResolver.ResolvedPlatformConfig(
                PlatformConfigType.GLOBAL_GENERAL,
                1,
                "{\"taxRate\":10}"
        );
        when(countryConfigResolver.resolveActiveConfig(eq("BJ"), eq(CountryConfigType.GENERAL)))
                .thenReturn(generalConfig);
        when(countryConfigResolver.resolveActivePlatformConfig(eq(PlatformConfigType.GLOBAL_GENERAL)))
                .thenReturn(platformGeneralConfig);
        when(countryConfigResolver.payloadAsObjectNode(generalConfig))
                .thenReturn(JsonNodeFactory.instance.objectNode().put("taxRate", 10));
        when(countryConfigResolver.payloadAsObjectNode(platformGeneralConfig))
                .thenReturn(JsonNodeFactory.instance.objectNode().put("taxRate", 10));
    }

    @Test
    void processWeek_shouldCreateBatchItemCreditWalletPostLedgerAndMarkCommissionPaid() {
        int weekId = 202611;
        UUID accountHolderId = createAccountHolder("Payout", "User");
        createActiveWallet(accountHolderId);
        createPendingGroupBonusCommission(accountHolderId, weekId, new BigDecimal("200.0000"));

        CommissionPayoutProcessingResult result = payoutBatchProcessor.processWeek(weekId);

        assertEquals(CommissionPayoutBatchStatus.COMPLETED, result.status());
        assertEquals(1, result.paidItems());
        assertEquals(0, result.failedItems());

        CommissionPayoutBatch batch = payoutBatchRepository.findByWeekId(weekId).orElseThrow();
        List<CommissionPayoutItem> items = payoutItemRepository.findByBatchIdOrderByCreatedAtAsc(batch.getId());
        assertEquals(1, items.size());
        CommissionPayoutItem item = items.getFirst();

        assertEquals(CommissionPayoutItemStatus.PAID, item.getStatus());
        assertEquals(0, item.getGrossAmount().compareTo(new BigDecimal("200.0000")));
        assertEquals(0, item.getTaxAmount().compareTo(new BigDecimal("20.0000")));
        assertEquals(0, item.getNetAmount().compareTo(new BigDecimal("180.0000")));
        assertNotNull(item.getOperationId());
        assertNotNull(item.getProcessedAt());

        MlmCommission commission = mlmCommissionRepository.findAll().getFirst();
        assertEquals(commission.getId(), item.getCommissionId());
        assertEquals(CommissionStatus.PAID, commission.getStatus());
        assertNotNull(commission.getPaidAt());
        assertEquals(batch.getId(), commission.getPayoutBatchId());
        assertEquals(item.getId(), commission.getPayoutItemId());

        Operation operation = operationRepository.findById(item.getOperationId()).orElseThrow();
        assertEquals(OperationType.COMMISSION_PAYOUT, operation.getType());
        assertEquals(OperationStatus.CONFIRMED, operation.getStatus());

        AccountingEvent event = accountingEventRepository.findByOperationId(operation.getId()).orElseThrow();
        List<LedgerEntry> entries = ledgerEntryRepository.findAll().stream()
                .filter(entry -> entry.getEvent().getId().equals(event.getId()))
                .toList();
        assertEquals(3, entries.size());
        assertTrue(entries.stream().anyMatch(entry ->
                entry.getEntryType() == LedgerEntryType.DEBIT
                        && entry.getAmount().compareTo(new BigDecimal("200.0000")) == 0));
        assertTrue(entries.stream().anyMatch(entry ->
                entry.getEntryType() == LedgerEntryType.CREDIT
                        && entry.getAmount().compareTo(new BigDecimal("180.0000")) == 0));
        assertTrue(entries.stream().anyMatch(entry ->
                entry.getEntryType() == LedgerEntryType.CREDIT
                        && entry.getAmount().compareTo(new BigDecimal("20.0000")) == 0));

        List<FinancialAuditLog> auditLogs = financialAuditLogRepository.findAll();
        assertTrue(auditLogs.stream().anyMatch(log ->
                "COMMISSION_PAYOUT".equals(log.getEventType())
                        && accountHolderId.equals(log.getAccountHolderId())
                        && log.getAmount().compareTo(new BigDecimal("200.0000")) == 0));
        assertTrue(auditLogs.stream().anyMatch(log ->
                "WALLET_CREDIT".equals(log.getEventType())
                        && accountHolderId.equals(log.getAccountHolderId())
                        && log.getAmount().compareTo(new BigDecimal("180.0000")) == 0));
    }

    @Test
    void processWeek_shouldBeIdempotentOnRerun() {
        int weekId = 202612;
        UUID accountHolderId = createAccountHolder("Idempotent", "User");
        createActiveWallet(accountHolderId);
        createPendingGroupBonusCommission(accountHolderId, weekId, new BigDecimal("100.0000"));

        CommissionPayoutProcessingResult firstRun = payoutBatchProcessor.processWeek(weekId);
        CommissionPayoutProcessingResult secondRun = payoutBatchProcessor.processWeek(weekId);

        assertEquals(CommissionPayoutBatchStatus.COMPLETED, firstRun.status());
        assertEquals(CommissionPayoutBatchStatus.COMPLETED, secondRun.status());
        assertEquals(firstRun.batchId(), secondRun.batchId());
        assertEquals(1, payoutBatchRepository.count());
        assertEquals(1, payoutItemRepository.count());
        assertEquals(1, operationRepository.countByType(OperationType.COMMISSION_PAYOUT));
        assertEquals(1, mlmCommissionRepository.findAll().stream()
                .filter(c -> c.getStatus() == CommissionStatus.PAID)
                .count());
    }

    @Test
    void processWeek_shouldCreateOnePayoutItemPerCommission() {
        int weekId = 202613;
        UUID firstAccountHolderId = createAccountHolder("Multi", "CommissionA");
        UUID secondAccountHolderId = createAccountHolder("Multi", "CommissionB");
        createActiveWallet(firstAccountHolderId);
        createActiveWallet(secondAccountHolderId);
        createPendingGroupBonusCommission(firstAccountHolderId, weekId, new BigDecimal("100.0000"));
        createPendingGroupBonusCommission(secondAccountHolderId, weekId, new BigDecimal("50.0000"));

        CommissionPayoutProcessingResult result = payoutBatchProcessor.processWeek(weekId);

        assertEquals(CommissionPayoutBatchStatus.COMPLETED, result.status());
        assertEquals(2, result.paidItems());
        CommissionPayoutBatch batch = payoutBatchRepository.findByWeekId(weekId).orElseThrow();
        List<CommissionPayoutItem> items = payoutItemRepository.findByBatchIdOrderByCreatedAtAsc(batch.getId());
        assertEquals(2, items.size());
        assertEquals(2, items.stream().map(CommissionPayoutItem::getCommissionId).distinct().count());
    }

    private UUID createAccountHolder(String firstName, String lastName) {
        AccountHolder saved = accountHolderRepository.save(AccountHolder.builder()
                .id(UUID.randomUUID())
                .firstName(firstName)
                .lastName(lastName)
                .phoneNumber("229" + UUID.randomUUID().toString().replace("-", "").substring(0, 10))
                .countryCode("BJ")
                .serviceCountryCode("BJ")
                .accountType(AccountType.STANDARD)
                .build());
        return saved.getId();
    }

    private void createActiveWallet(UUID accountHolderId) {
        walletRepository.save(Wallet.builder()
                .accountHolderId(accountHolderId)
                .provider("MOMO")
                .mobileMoneyNumber("+22900000000")
                .status("ACTIVE")
                .build());
    }

    private void createPendingGroupBonusCommission(UUID accountHolderId, int weekId, BigDecimal amount) {
        mlmCommissionRepository.save(MlmCommission.builder()
                .beneficiaryAccountHolderId(accountHolderId)
                .amount(amount)
                .currency("XOF")
                .source("WEEKLY_GROUP_BONUS")
                .commissionType(CommissionType.GROUP_BONUS)
                .weekId(weekId)
                .status(CommissionStatus.PENDING)
                .build());
    }
}
