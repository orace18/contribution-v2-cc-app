package com.ccapp.ledger.service;

import com.ccapp.country.config.domain.entity.CountryChartAccount;
import com.ccapp.country.config.domain.enums.CountryAccountGroup;
import com.ccapp.country.config.service.CountryChartOfAccountsService;
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
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.service.OperationService;
import com.ccapp.common.exception.BusinessException;
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
    private AccountHolderRepository accountHolderRepository;

    @Mock
    private CountryRepository countryRepository;

    @InjectMocks
    private AccountingPostingService accountingPostingService;

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
        when(countryRepository.findByFallbackTrue()).thenReturn(Optional.of(Country.builder()
                .id(UUID.randomUUID())
                .code("INT")
                .name("International")
                .currency("USD")
                .active(true)
                .configured(false)
                .fallback(true)
                .build()));
        when(countryChartOfAccountsService.findOrCreate(eq("INT"), any(CountryAccountGroup.class)))
                .thenAnswer(invocation -> CountryChartAccount.builder()
                        .id(UUID.randomUUID())
                        .countryCode("INT")
                        .accountCode("200000001")
                        .accountNumber("200000001")
                        .accountName("Generated")
                        .accountGroup(invocation.getArgument(1))
                        .currency("USD")
                        .build());
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
        when(snapshotRepository.findByAccountCodeAndCurrency(anyString(), anyString())).thenReturn(Optional.empty());
        when(snapshotRepository.save(any(AccountBalanceSnapshot.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccountingEvent event = accountingPostingService.postConfirmedOperation(operationId);

        assertNotNull(event.getId());
        assertEquals(new BigDecimal("100.00"), event.getTotalDebit());
        assertEquals(new BigDecimal("100.00"), event.getTotalCredit());

        ArgumentCaptor<Iterable<LedgerEntry>> entriesCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(ledgerEntryRepository).saveAll(entriesCaptor.capture());
        List<LedgerEntry> entries = StreamSupport.stream(entriesCaptor.getValue().spliterator(), false).toList();

        assertEquals(2, entries.size());
        assertEquals(LedgerEntryType.DEBIT, entries.get(0).getEntryType());
        assertEquals(LedgerEntryType.CREDIT, entries.get(1).getEntryType());
        assertEquals(new BigDecimal("100.00"), entries.get(0).getAmount());
        assertEquals(new BigDecimal("100.00"), entries.get(1).getAmount());
        assertTrue(entries.stream().allMatch(entry -> "USD".equals(entry.getCurrency())));

        verify(snapshotRepository, atLeast(2)).save(any(AccountBalanceSnapshot.class));
    }
}
