package com.ccapp.accounting.ledger.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.country.config.domain.entity.CountryChartAccount;
import com.ccapp.country.config.domain.enums.CountryAccountGroup;
import com.ccapp.country.config.domain.enums.CountryAccountSystemTag;
import com.ccapp.country.config.domain.enums.CountryConfigType;
import com.ccapp.country.config.service.CountryChartOfAccountsService;
import com.ccapp.country.config.service.CountryConfigResolver;
import com.ccapp.country.domain.entity.Country;
import com.ccapp.country.repository.CountryRepository;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.ledger.domain.entity.AccountBalanceSnapshot;
import com.ccapp.ledger.domain.entity.AccountingEvent;
import com.ccapp.ledger.domain.entity.LedgerEntry;
import com.ccapp.ledger.domain.enums.LedgerEntryType;
import com.ccapp.ledger.repository.AccountBalanceSnapshotRepository;
import com.ccapp.ledger.repository.AccountingEventRepository;
import com.ccapp.ledger.repository.LedgerEntryRepository;
import com.ccapp.ledger.service.AccountingPostingService;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.service.OperationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountingPostingServiceTest {

    @Mock
    private OperationService operationService;

    @Mock
    private AccountingEventRepository accountingEventRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private AccountBalanceSnapshotRepository snapshotRepository;

    @Mock
    private CountryChartOfAccountsService countryChartOfAccountsService;

    @Mock
    private CountryConfigResolver countryConfigResolver;

    @Mock
    private AccountHolderRepository accountHolderRepository;

    @Mock
    private CountryRepository countryRepository;

    @InjectMocks
    private AccountingPostingService accountingPostingService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void postConfirmedOperation_shouldReturnExistingEvent_whenAlreadyPosted() {
        UUID operationId = UUID.randomUUID();
        AccountingEvent existingEvent = AccountingEvent.builder().id(UUID.randomUUID()).build();
        when(accountingEventRepository.findByOperationId(operationId)).thenReturn(Optional.of(existingEvent));

        AccountingEvent result = accountingPostingService.postConfirmedOperation(operationId);

        assertSame(existingEvent, result);
        verifyNoInteractions(operationService);
        verify(ledgerEntryRepository, never()).saveAll(any());
    }

    @Test
    void postConfirmedOperation_shouldThrowBusinessException_whenOperationIsNotConfirmed() {
        UUID operationId = UUID.randomUUID();
        Operation operation = Operation.builder()
                .id(operationId)
                .accountHolderId(UUID.randomUUID())
                .type(OperationType.SUBSCRIPTION)
                .status(OperationStatus.INITIATED)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .build();

        when(accountingEventRepository.findByOperationId(operationId)).thenReturn(Optional.empty());
        when(operationService.getById(operationId)).thenReturn(operation);

        assertThrows(BusinessException.class, () -> accountingPostingService.postConfirmedOperation(operationId));
        verify(accountingEventRepository, never()).save(any(AccountingEvent.class));
        verify(ledgerEntryRepository, never()).saveAll(any());
    }

    @Test
    void postConfirmedOperation_shouldCreateBalancedEntriesAndSnapshots_whenOperationConfirmed() {
        UUID operationId = UUID.randomUUID();
        Operation operation = Operation.builder()
                .id(operationId)
                .accountHolderId(UUID.randomUUID())
                .type(OperationType.SUBSCRIPTION)
                .status(OperationStatus.CONFIRMED)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .build();

        AccountHolder accountHolder = AccountHolder.builder()
                .id(operation.getAccountHolderId())
                .firstName("Test")
                .lastName("User")
                .serviceCountryCode("INT")
                .build();

        when(accountingEventRepository.findByOperationId(operationId)).thenReturn(Optional.empty());
        when(operationService.getById(operationId)).thenReturn(operation);
        when(accountHolderRepository.findById(operation.getAccountHolderId())).thenReturn(Optional.of(accountHolder));
        when(accountingEventRepository.save(any(AccountingEvent.class))).thenAnswer(invocation -> {
            AccountingEvent input = invocation.getArgument(0);
            return AccountingEvent.builder()
                    .id(UUID.randomUUID())
                    .operation(input.getOperation())
                    .eventType(input.getEventType())
                    .currency(input.getCurrency())
                    .totalDebit(input.getTotalDebit())
                    .totalCredit(input.getTotalCredit())
                    .postedAt(input.getPostedAt())
                    .build();
        });
        CountryConfigResolver.ResolvedConfig generalConfig = new CountryConfigResolver.ResolvedConfig(
                "INT",
                CountryConfigType.GENERAL,
                1,
                "{\"percentages\":{\"commissionPool\":60,\"guaranteeFund\":15,\"platformFee\":10,\"administration\":10,\"socialFund\":5}}",
                CountryConfigResolver.ConfigSource.COUNTRY
        );
        CountryConfigResolver.ResolvedConfig accountingConfig = new CountryConfigResolver.ResolvedConfig(
                "INT",
                CountryConfigType.ACCOUNTING,
                1,
                "{}",
                CountryConfigResolver.ConfigSource.COUNTRY
        );
        ObjectNode generalPayload = objectMapper.createObjectNode();
        ObjectNode percentages = generalPayload.putObject("percentages");
        percentages.put("commissionPool", 60);
        percentages.put("guaranteeFund", 15);
        percentages.put("platformFee", 10);
        percentages.put("administration", 10);
        percentages.put("socialFund", 5);
        ObjectNode accountingPayload = objectMapper.createObjectNode();
        when(countryConfigResolver.resolveActiveConfig("INT", CountryConfigType.GENERAL)).thenReturn(generalConfig);
        when(countryConfigResolver.resolveActiveConfig("INT", CountryConfigType.ACCOUNTING)).thenReturn(accountingConfig);
        when(countryConfigResolver.payloadAsObjectNode(generalConfig)).thenReturn(generalPayload);
        when(countryConfigResolver.payloadAsObjectNode(accountingConfig)).thenReturn(accountingPayload);
        when(countryChartOfAccountsService.resolveSystemAccount(eq("INT"), any(CountryAccountSystemTag.class)))
                .thenAnswer(invocation -> systemAccount((CountryAccountSystemTag) invocation.getArgument(1)));
        when(countryChartOfAccountsService.findOrCreateWallet(any(AccountHolder.class)))
                .thenReturn(CountryChartAccount.builder()
                        .id(UUID.randomUUID())
                        .countryCode("INT")
                        .accountCode("250000001")
                        .accountNumber("250000001")
                        .accountName("Wallet Test User")
                        .accountGroup(CountryAccountGroup.USER_WALLET)
                        .currency("USD")
                        .accountHolderId(accountHolder.getId())
                        .build());
        when(countryChartOfAccountsService.findOrCreate(eq("INT"), eq(CountryAccountGroup.MEMBERSHIP_FEES), anyString()))
                .thenReturn(sharedAccount("750001", CountryAccountGroup.MEMBERSHIP_FEES));
        when(countryChartOfAccountsService.findOrCreate(eq("INT"), eq(CountryAccountGroup.GUARANTEE_FUND), anyString()))
                .thenReturn(sharedAccount("230000001", CountryAccountGroup.GUARANTEE_FUND));
        when(countryChartOfAccountsService.findOrCreate(eq("INT"), eq(CountryAccountGroup.ADMINISTRATION), anyString()))
                .thenReturn(sharedAccount("221000001", CountryAccountGroup.ADMINISTRATION));
        when(countryChartOfAccountsService.findOrCreate(eq("INT"), eq(CountryAccountGroup.SOCIAL_FUND), anyString()))
                .thenReturn(sharedAccount("231000001", CountryAccountGroup.SOCIAL_FUND));
        when(snapshotRepository.findByAccountCodeAndCurrency(anyString(), anyString())).thenReturn(Optional.empty());
        when(snapshotRepository.save(any(AccountBalanceSnapshot.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccountingEvent event = accountingPostingService.postConfirmedOperation(operationId);

        assertNotNull(event.getId());
        assertEquals(0, new BigDecimal("200.0000").compareTo(event.getTotalDebit()));
        assertEquals(0, new BigDecimal("200.0000").compareTo(event.getTotalCredit()));

        ArgumentCaptor<Iterable<LedgerEntry>> entriesCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(ledgerEntryRepository).saveAll(entriesCaptor.capture());
        List<LedgerEntry> entries = StreamSupport.stream(entriesCaptor.getValue().spliterator(), false).toList();

        assertEquals(8, entries.size());
        assertEquals(LedgerEntryType.DEBIT, entries.get(0).getEntryType());
        assertEquals(LedgerEntryType.CREDIT, entries.get(1).getEntryType());
        assertEquals(new BigDecimal("100.00"), entries.get(0).getAmount());
        assertEquals(new BigDecimal("100.00"), entries.get(1).getAmount());
        assertEquals(LedgerEntryType.DEBIT, entries.get(2).getEntryType());
        assertEquals(new BigDecimal("60.0000"), entries.get(3).getAmount());
        assertEquals(new BigDecimal("15.0000"), entries.get(4).getAmount());
        assertEquals(new BigDecimal("10.0000"), entries.get(5).getAmount());
        assertEquals(new BigDecimal("10.0000"), entries.get(6).getAmount());
        assertEquals(new BigDecimal("5.0000"), entries.get(7).getAmount());
        assertTrue(entries.stream().allMatch(entry -> "USD".equals(entry.getCurrency())));

        verify(snapshotRepository, atLeast(8)).save(any(AccountBalanceSnapshot.class));
    }

    @Test
    void postMarketplacePayment_shouldDebitSourceAndCreditMarketplacePool() {
        UUID buyerId = UUID.randomUUID();
        Operation operation = Operation.builder()
                .id(UUID.randomUUID())
                .accountHolderId(buyerId)
                .type(OperationType.MARKETPLACE_PAYMENT)
                .status(OperationStatus.CONFIRMED)
                .amount(new BigDecimal("10000.0000"))
                .currency("XOF")
                .build();
        AccountHolder buyer = AccountHolder.builder()
                .id(buyerId)
                .firstName("Buyer")
                .lastName("One")
                .serviceCountryCode("BJ")
                .build();

        when(accountHolderRepository.findById(buyerId)).thenReturn(Optional.of(buyer));
        when(accountingEventRepository.findByOperationId(operation.getId())).thenReturn(Optional.empty());
        when(accountingEventRepository.save(any(AccountingEvent.class))).thenAnswer(invocation -> {
            AccountingEvent input = invocation.getArgument(0);
            return AccountingEvent.builder()
                    .id(UUID.randomUUID())
                    .operation(input.getOperation())
                    .eventType(input.getEventType())
                    .currency(input.getCurrency())
                    .totalDebit(input.getTotalDebit())
                    .totalCredit(input.getTotalCredit())
                    .postedAt(input.getPostedAt())
                    .build();
        });
        when(countryChartOfAccountsService.resolveSystemAccount(eq("BJ"), any(CountryAccountSystemTag.class)))
                .thenAnswer(invocation -> systemAccountForCountry((CountryAccountSystemTag) invocation.getArgument(1), "BJ", "XOF"));
        when(snapshotRepository.findByAccountCodeAndCurrency(anyString(), anyString())).thenReturn(Optional.empty());
        when(snapshotRepository.save(any(AccountBalanceSnapshot.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccountingEvent event = accountingPostingService.postMarketplacePayment(operation, buyerId, "BJ", false);

        assertNotNull(event.getId());
        assertEquals(0, event.getTotalDebit().compareTo(new BigDecimal("10000.0000")));
        assertEquals(0, event.getTotalCredit().compareTo(new BigDecimal("10000.0000")));

        ArgumentCaptor<Iterable<LedgerEntry>> entriesCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(ledgerEntryRepository).saveAll(entriesCaptor.capture());
        List<LedgerEntry> entries = StreamSupport.stream(entriesCaptor.getValue().spliterator(), false).toList();

        assertEquals(2, entries.size());
        assertEquals(LedgerEntryType.DEBIT, entries.get(0).getEntryType());
        assertEquals("470000001", entries.get(0).getAccountCode());
        assertEquals(0, entries.get(0).getAmount().compareTo(new BigDecimal("10000.0000")));
        assertEquals(LedgerEntryType.CREDIT, entries.get(1).getEntryType());
        assertEquals("710000001", entries.get(1).getAccountCode());
        assertEquals(0, entries.get(1).getAmount().compareTo(new BigDecimal("10000.0000")));
    }

    @Test
    void postMarketplaceMerchantPayout_shouldDebitMarketplacePoolAndCreditMerchantWallet() {
        UUID merchantId = UUID.randomUUID();
        Operation operation = Operation.builder()
                .id(UUID.randomUUID())
                .accountHolderId(merchantId)
                .type(OperationType.MARKETPLACE_MERCHANT_PAYOUT)
                .status(OperationStatus.CONFIRMED)
                .amount(new BigDecimal("8000.0000"))
                .currency("XOF")
                .build();
        AccountHolder merchant = AccountHolder.builder()
                .id(merchantId)
                .firstName("Merchant")
                .lastName("One")
                .serviceCountryCode("BJ")
                .build();

        when(accountHolderRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(accountingEventRepository.findByOperationId(operation.getId())).thenReturn(Optional.empty());
        when(accountingEventRepository.save(any(AccountingEvent.class))).thenAnswer(invocation -> {
            AccountingEvent input = invocation.getArgument(0);
            return AccountingEvent.builder()
                    .id(UUID.randomUUID())
                    .operation(input.getOperation())
                    .eventType(input.getEventType())
                    .currency(input.getCurrency())
                    .totalDebit(input.getTotalDebit())
                    .totalCredit(input.getTotalCredit())
                    .postedAt(input.getPostedAt())
                    .build();
        });
        when(countryChartOfAccountsService.resolveSystemAccount(eq("BJ"), any(CountryAccountSystemTag.class)))
                .thenAnswer(invocation -> systemAccountForCountry((CountryAccountSystemTag) invocation.getArgument(1), "BJ", "XOF"));
        when(countryChartOfAccountsService.findOrCreateWallet(any(AccountHolder.class)))
                .thenReturn(CountryChartAccount.builder()
                        .id(UUID.randomUUID())
                        .countryCode("BJ")
                        .accountCode("460000456")
                        .accountNumber("460000456")
                        .accountName("Wallet Merchant One")
                        .accountGroup(CountryAccountGroup.USER_WALLET)
                        .currency("XOF")
                        .accountHolderId(merchantId)
                        .build());
        when(snapshotRepository.findByAccountCodeAndCurrency(anyString(), anyString())).thenReturn(Optional.empty());
        when(snapshotRepository.save(any(AccountBalanceSnapshot.class))).thenAnswer(invocation -> invocation.getArgument(0));

        accountingPostingService.postMarketplaceMerchantPayout(operation, merchantId, "BJ");

        ArgumentCaptor<Iterable<LedgerEntry>> entriesCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(ledgerEntryRepository).saveAll(entriesCaptor.capture());
        List<LedgerEntry> entries = StreamSupport.stream(entriesCaptor.getValue().spliterator(), false).toList();

        assertEquals(2, entries.size());
        assertEquals(LedgerEntryType.DEBIT, entries.get(0).getEntryType());
        assertEquals("710000001", entries.get(0).getAccountCode());
        assertEquals(0, entries.get(0).getAmount().compareTo(new BigDecimal("8000.0000")));
        assertEquals(LedgerEntryType.CREDIT, entries.get(1).getEntryType());
        assertEquals("460000456", entries.get(1).getAccountCode());
        assertEquals(0, entries.get(1).getAmount().compareTo(new BigDecimal("8000.0000")));
    }

    @Test
    void postMarketplaceCommissionAllocation_shouldSplitCommissionAcrossFiveDestinationAccounts() {
        Operation operation = Operation.builder()
                .id(UUID.randomUUID())
                .accountHolderId(UUID.randomUUID())
                .type(OperationType.MARKETPLACE_COMMISSION_ALLOCATION)
                .status(OperationStatus.CONFIRMED)
                .amount(new BigDecimal("2000.0000"))
                .currency("XOF")
                .build();

        when(accountingEventRepository.findByOperationId(operation.getId())).thenReturn(Optional.empty());
        when(accountingEventRepository.save(any(AccountingEvent.class))).thenAnswer(invocation -> {
            AccountingEvent input = invocation.getArgument(0);
            return AccountingEvent.builder()
                    .id(UUID.randomUUID())
                    .operation(input.getOperation())
                    .eventType(input.getEventType())
                    .currency(input.getCurrency())
                    .totalDebit(input.getTotalDebit())
                    .totalCredit(input.getTotalCredit())
                    .postedAt(input.getPostedAt())
                    .build();
        });
        when(countryChartOfAccountsService.resolveSystemAccount(eq("BJ"), any(CountryAccountSystemTag.class)))
                .thenAnswer(invocation -> systemAccountForCountry((CountryAccountSystemTag) invocation.getArgument(1), "BJ", "XOF"));
        when(countryChartOfAccountsService.findOrCreate(eq("BJ"), eq(CountryAccountGroup.GUARANTEE_FUND), anyString()))
                .thenReturn(sharedAccountForCountry("230000001", CountryAccountGroup.GUARANTEE_FUND, "BJ", "XOF"));
        when(countryChartOfAccountsService.findOrCreate(eq("BJ"), eq(CountryAccountGroup.ADMINISTRATION), anyString()))
                .thenReturn(sharedAccountForCountry("221000001", CountryAccountGroup.ADMINISTRATION, "BJ", "XOF"));
        when(countryChartOfAccountsService.findOrCreate(eq("BJ"), eq(CountryAccountGroup.SOCIAL_FUND), anyString()))
                .thenReturn(sharedAccountForCountry("231000001", CountryAccountGroup.SOCIAL_FUND, "BJ", "XOF"));
        when(snapshotRepository.findByAccountCodeAndCurrency(anyString(), anyString())).thenReturn(Optional.empty());
        when(snapshotRepository.save(any(AccountBalanceSnapshot.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccountingEvent event = accountingPostingService.postMarketplaceCommissionAllocation(operation, "BJ");

        assertNotNull(event.getId());
        assertEquals(0, event.getTotalDebit().compareTo(new BigDecimal("2000.0000")));
        assertEquals(0, event.getTotalCredit().compareTo(new BigDecimal("2000.0000")));

        ArgumentCaptor<Iterable<LedgerEntry>> entriesCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(ledgerEntryRepository).saveAll(entriesCaptor.capture());
        List<LedgerEntry> entries = StreamSupport.stream(entriesCaptor.getValue().spliterator(), false).toList();

        assertEquals(6, entries.size());
        assertEquals("710000001", entries.get(0).getAccountCode());
        assertEquals(LedgerEntryType.DEBIT, entries.get(0).getEntryType());
        assertEquals(0, entries.get(0).getAmount().compareTo(new BigDecimal("2000.0000")));
        assertTrue(entries.stream().anyMatch(entry -> entry.getEntryType() == LedgerEntryType.CREDIT
                && "230000001".equals(entry.getAccountCode())
                && entry.getAmount().compareTo(new BigDecimal("300.0000")) == 0));
        assertTrue(entries.stream().anyMatch(entry -> entry.getEntryType() == LedgerEntryType.CREDIT
                && "220000001".equals(entry.getAccountCode())
                && entry.getAmount().compareTo(new BigDecimal("200.0000")) == 0));
        assertTrue(entries.stream().anyMatch(entry -> entry.getEntryType() == LedgerEntryType.CREDIT
                && "221000001".equals(entry.getAccountCode())
                && entry.getAmount().compareTo(new BigDecimal("200.0000")) == 0));
        assertTrue(entries.stream().anyMatch(entry -> entry.getEntryType() == LedgerEntryType.CREDIT
                && "231000001".equals(entry.getAccountCode())
                && entry.getAmount().compareTo(new BigDecimal("100.0000")) == 0));
        assertTrue(entries.stream().anyMatch(entry -> entry.getEntryType() == LedgerEntryType.CREDIT
                && "680000001".equals(entry.getAccountCode())
                && entry.getAmount().compareTo(new BigDecimal("1200.0000")) == 0));
    }

    private CountryChartAccount systemAccount(CountryAccountSystemTag systemTag) {
        String accountCode = switch (systemTag) {
            case MARKETPLACE_POOL -> "710000001";
            case MLM_POOL -> "680000001";
            case PLATFORM_REVENUE -> "220000001";
            case PSP_CLEARING -> "470000001";
            case USER_WALLET -> "460000001";
            case LOAN_PORTFOLIO -> "720000001";
            case INTEREST_REVENUE -> "760000001";
            case CIRCLE_POOL -> "140000001";
        };
        CountryAccountGroup accountGroup = switch (systemTag) {
            case MARKETPLACE_POOL -> CountryAccountGroup.MARKETPLACE_POOL;
            case MLM_POOL -> CountryAccountGroup.MLM_POOL;
            case PLATFORM_REVENUE -> CountryAccountGroup.PLATFORM_REVENUE;
            case PSP_CLEARING -> CountryAccountGroup.PSP_CLEARING;
            case USER_WALLET -> CountryAccountGroup.USER_WALLET;
            case LOAN_PORTFOLIO -> CountryAccountGroup.LOAN_PORTFOLIO;
            case INTEREST_REVENUE -> CountryAccountGroup.INTEREST_REVENUE;
            case CIRCLE_POOL -> CountryAccountGroup.FUND_STATUTAIRE;
        };
        return CountryChartAccount.builder()
                .id(UUID.randomUUID())
                .countryCode("INT")
                .accountCode(accountCode)
                .accountNumber(accountCode)
                .accountName(systemTag.name())
                .accountGroup(accountGroup)
                .currency("USD")
                .build();
    }

    private CountryChartAccount sharedAccount(String accountCode, CountryAccountGroup group) {
        return sharedAccountForCountry(accountCode, group, "INT", "USD");
    }

    private CountryChartAccount systemAccountForCountry(CountryAccountSystemTag systemTag,
                                                        String countryCode,
                                                        String currency) {
        CountryChartAccount base = systemAccount(systemTag);
        base.setCountryCode(countryCode);
        base.setCurrency(currency);
        return base;
    }

    private CountryChartAccount sharedAccountForCountry(String accountCode,
                                                        CountryAccountGroup group,
                                                        String countryCode,
                                                        String currency) {
        return CountryChartAccount.builder()
                .id(UUID.randomUUID())
                .countryCode(countryCode)
                .accountCode(accountCode)
                .accountNumber(accountCode)
                .accountName(group.name())
                .accountGroup(group)
                .currency(currency)
                .build();
    }
}
