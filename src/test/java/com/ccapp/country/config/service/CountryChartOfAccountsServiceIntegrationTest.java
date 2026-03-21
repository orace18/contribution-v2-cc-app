package com.ccapp.country.config.service;

import com.ccapp.config.TestJacksonAndCachingConfig;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.country.config.domain.entity.CountryChartAccount;
import com.ccapp.country.config.domain.enums.CountryAccountGroup;
import com.ccapp.country.config.domain.enums.CountryChartAccountStatus;
import com.ccapp.country.config.domain.enums.CountryChartAccountType;
import com.ccapp.country.config.dto.CreateCountryChartAccountRequest;
import com.ccapp.country.config.dto.CountryChartAccountHistoryResponse;
import com.ccapp.country.config.dto.CountryChartAccountSummaryResponse;
import com.ccapp.country.config.repository.CountryChartAccountRepository;
import com.ccapp.country.domain.entity.Country;
import com.ccapp.country.repository.CountryRepository;
import com.ccapp.ledger.domain.entity.AccountBalanceSnapshot;
import com.ccapp.ledger.domain.entity.AccountingEvent;
import com.ccapp.ledger.domain.entity.LedgerEntry;
import com.ccapp.ledger.domain.enums.AccountingEventType;
import com.ccapp.ledger.domain.enums.LedgerEntryType;
import com.ccapp.ledger.repository.AccountBalanceSnapshotRepository;
import com.ccapp.ledger.repository.AccountingEventRepository;
import com.ccapp.ledger.repository.LedgerEntryRepository;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.repository.OperationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({CountryChartOfAccountsService.class, TestJacksonAndCachingConfig.class})
class CountryChartOfAccountsServiceIntegrationTest {

    @Autowired
    private CountryChartOfAccountsService countryChartOfAccountsService;
    @Autowired
    private CountryRepository countryRepository;
    @Autowired
    private CountryChartAccountRepository countryChartAccountRepository;
    @Autowired
    private AccountBalanceSnapshotRepository accountBalanceSnapshotRepository;
    @Autowired
    private OperationRepository operationRepository;
    @Autowired
    private AccountingEventRepository accountingEventRepository;
    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Test
    void listAndHistory_shouldExposeBusinessReadableAccountDataAndMovements() {
        countryRepository.save(Country.builder()
                .id(UUID.randomUUID())
                .code("BJ")
                .name("Benin")
                .currency("XOF")
                .active(true)
                .configured(false)
                .fallback(false)
                .build());

        CountryChartAccount account = countryChartAccountRepository.save(CountryChartAccount.builder()
                .countryCode("BJ")
                .accountCode("ACC-COMMISSION")
                .accountName("Commission Pool")
                .accountType(CountryChartAccountType.REVENUE)
                .currency("XOF")
                .openingBalance(new BigDecimal("1000.0000"))
                .status(CountryChartAccountStatus.ACTIVE)
                .build());

        accountBalanceSnapshotRepository.save(AccountBalanceSnapshot.builder()
                .accountCode("ACC-COMMISSION")
                .currency("XOF")
                .balance(new BigDecimal("2500.0000"))
                .build());

        Operation operation = operationRepository.save(Operation.builder()
                .accountHolderId(UUID.randomUUID())
                .actorUserId(UUID.randomUUID())
                .type(OperationType.SUBSCRIPTION)
                .status(OperationStatus.CONFIRMED)
                .amount(new BigDecimal("1500.0000"))
                .currency("XOF")
                .idempotencyKey("chart-history-" + UUID.randomUUID())
                .description("Commission movement")
                .build());
        AccountingEvent event = accountingEventRepository.save(AccountingEvent.builder()
                .operation(operation)
                .eventType(AccountingEventType.OPERATION_CONFIRMED)
                .currency("XOF")
                .totalDebit(new BigDecimal("1500.0000"))
                .totalCredit(new BigDecimal("1500.0000"))
                .postedAt(OffsetDateTime.now())
                .build());
        ledgerEntryRepository.save(LedgerEntry.builder()
                .event(event)
                .lineNo(1)
                .accountCode("ACC-COMMISSION")
                .entryType(LedgerEntryType.CREDIT)
                .amount(new BigDecimal("1500.0000"))
                .currency("XOF")
                .description("Commission collected")
                .build());

        List<CountryChartAccountSummaryResponse> summaries = countryChartOfAccountsService.listCountryAccounts("BJ");
        assertTrue(summaries.size() >= CountryAccountGroup.requiredGroups().size());
        CountryChartAccountSummaryResponse commission = summaries.stream()
                .filter(item -> "ACC-COMMISSION".equals(item.accountCode()))
                .findFirst()
                .orElseThrow();
        assertEquals("Commission Pool", commission.accountName());
        assertEquals(new BigDecimal("2500.0000"), commission.currentBalance());

        CountryChartAccountHistoryResponse history = countryChartOfAccountsService.getCountryAccountHistory("BJ", account.getId());
        assertNotNull(history.account());
        assertEquals(new BigDecimal("1000.0000"), history.account().openingBalance());
        assertEquals(new BigDecimal("2500.0000"), history.account().currentBalance());
        assertEquals(1, history.movements().size());
        assertEquals(LedgerEntryType.CREDIT, history.movements().get(0).entryType());
    }

    @Test
    void findOrCreate_shouldCreateNumericAccountNumberByGroupAndBeIdempotent() {
        countryRepository.save(Country.builder()
                .id(UUID.randomUUID())
                .code("CI")
                .name("Cote d'Ivoire")
                .currency("XOF")
                .active(true)
                .configured(false)
                .fallback(false)
                .build());

        CountryChartAccount first = countryChartOfAccountsService.findOrCreate("ci", CountryAccountGroup.MEMBERSHIP_FEES);
        CountryChartAccount second = countryChartOfAccountsService.findOrCreate("CI", CountryAccountGroup.MEMBERSHIP_FEES);

        assertEquals(first.getId(), second.getId());
        assertEquals("SALES", first.getAccountGroup().name());
        assertTrue(first.getAccountNumber().matches("^70\\d{6}$"));
        assertEquals(first.getAccountNumber(), first.getAccountCode());
    }

    @Test
    void createAndAutoGenerate_shouldBlockManualCreationAndGenerateRequiredTemplate() {
        countryRepository.save(Country.builder()
                .id(UUID.randomUUID())
                .code("GH")
                .name("Ghana")
                .currency("GHS")
                .active(true)
                .configured(false)
                .fallback(false)
                .build());

        assertThrows(BusinessException.class, () -> countryChartOfAccountsService.createCountryAccount(
                "GH",
                new CreateCountryChartAccountRequest(CountryAccountGroup.PLATFORM_REVENUE, "Platform Revenue GH")
        ));

        List<CountryChartAccountSummaryResponse> generated = countryChartOfAccountsService.autoGenerateRequiredAccounts("GH");
        assertTrue(generated.size() >= CountryAccountGroup.requiredGroups().size());
        assertTrue(generated.stream().anyMatch(item -> item.groupId() == CountryAccountGroup.FUND_STATUTAIRE));
        assertTrue(generated.stream().anyMatch(item -> item.groupId() == CountryAccountGroup.FINTECH_REVENUE));
        assertTrue(generated.stream().allMatch(item -> item.accountNumber() != null && !item.accountNumber().isBlank()));
    }
}
