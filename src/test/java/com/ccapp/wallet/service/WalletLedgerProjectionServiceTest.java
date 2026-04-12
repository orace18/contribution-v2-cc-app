package com.ccapp.wallet.service;

import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.identity.wallet.repository.WalletRepository;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.wallet.domain.entity.WalletLedgerEntry;
import com.ccapp.wallet.domain.enums.WalletLedgerCategory;
import com.ccapp.wallet.domain.enums.WalletLedgerEntryType;
import com.ccapp.wallet.domain.enums.WalletLedgerStatus;
import com.ccapp.wallet.domain.enums.WalletType;
import com.ccapp.wallet.repository.WalletLedgerEntryRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletLedgerProjectionServiceTest {

    private static final UUID ACCOUNT_HOLDER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DESTINATION_ACCOUNT_HOLDER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID WALLET_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID DESTINATION_WALLET_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID OPERATION_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @Mock
    private WalletLedgerEntryRepository walletLedgerEntryRepository;
    @Mock
    private WalletRepository walletRepository;

    @InjectMocks
    private WalletLedgerProjectionService service;

    @Test
    void syncOperation_createsPendingDepositProjectionForActiveUserWallet() {
        Wallet wallet = userWallet(WALLET_ID, ACCOUNT_HOLDER_ID, "ACTIVE");
        Operation operation = operation(OperationType.WALLET_DEPOSIT_PSP, OperationStatus.INITIATED);

        when(walletRepository.findFirstByAccountHolderIdAndWalletTypeAndStatus(
                ACCOUNT_HOLDER_ID,
                WalletType.USER,
                "ACTIVE"
        )).thenReturn(Optional.of(wallet));
        when(walletLedgerEntryRepository.findByWalletIdAndOperationId(WALLET_ID, OPERATION_ID))
                .thenReturn(Optional.empty());
        when(walletLedgerEntryRepository.save(any(WalletLedgerEntry.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.syncOperation(operation);

        ArgumentCaptor<WalletLedgerEntry> captor = ArgumentCaptor.forClass(WalletLedgerEntry.class);
        verify(walletLedgerEntryRepository).save(captor.capture());

        WalletLedgerEntry entry = captor.getValue();
        assertThat(entry.getWalletId()).isEqualTo(WALLET_ID);
        assertThat(entry.getOperationId()).isEqualTo(OPERATION_ID);
        assertThat(entry.getEntryType()).isEqualTo(WalletLedgerEntryType.CREDIT);
        assertThat(entry.getCategory()).isEqualTo(WalletLedgerCategory.DEPOSIT);
        assertThat(entry.getStatus()).isEqualTo(WalletLedgerStatus.PENDING);
        assertThat(entry.getAmount()).isEqualByComparingTo("2500.0000");
        assertThat(entry.getCurrency()).isEqualTo("XOF");
    }

    @Test
    void syncOperation_createsDebitAndCreditEntriesForConfirmedWalletTransfer() {
        Wallet sourceWallet = userWallet(WALLET_ID, ACCOUNT_HOLDER_ID, "ACTIVE");
        Wallet destinationWallet = userWallet(DESTINATION_WALLET_ID, DESTINATION_ACCOUNT_HOLDER_ID, "ACTIVE");
        Operation operation = operation(OperationType.WALLET_PAYMENT, OperationStatus.CONFIRMED);
        operation.setDestinationAccountHolderId(DESTINATION_ACCOUNT_HOLDER_ID);
        operation.setDestinationWalletId(DESTINATION_WALLET_ID);

        when(walletRepository.findFirstByAccountHolderIdAndWalletTypeAndStatus(
                ACCOUNT_HOLDER_ID,
                WalletType.USER,
                "ACTIVE"
        )).thenReturn(Optional.of(sourceWallet));
        when(walletRepository.findById(DESTINATION_WALLET_ID)).thenReturn(Optional.of(destinationWallet));
        when(walletLedgerEntryRepository.findByWalletIdAndOperationId(WALLET_ID, OPERATION_ID))
                .thenReturn(Optional.empty());
        when(walletLedgerEntryRepository.findByWalletIdAndOperationId(DESTINATION_WALLET_ID, OPERATION_ID))
                .thenReturn(Optional.empty());
        when(walletLedgerEntryRepository.save(any(WalletLedgerEntry.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.syncOperation(operation);

        ArgumentCaptor<WalletLedgerEntry> captor = ArgumentCaptor.forClass(WalletLedgerEntry.class);
        verify(walletLedgerEntryRepository, times(2)).save(captor.capture());

        List<WalletLedgerEntry> entries = captor.getAllValues();
        assertThat(entries)
                .extracting(WalletLedgerEntry::getWalletId, WalletLedgerEntry::getEntryType, WalletLedgerEntry::getCategory, WalletLedgerEntry::getStatus)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(WALLET_ID, WalletLedgerEntryType.DEBIT, WalletLedgerCategory.TRANSFER_OUT, WalletLedgerStatus.CONFIRMED),
                        org.assertj.core.groups.Tuple.tuple(DESTINATION_WALLET_ID, WalletLedgerEntryType.CREDIT, WalletLedgerCategory.TRANSFER_IN, WalletLedgerStatus.CONFIRMED)
                );
    }

    @Test
    void syncOperation_updatesExistingProjectionToFailedWhenWithdrawalFails() {
        Wallet wallet = userWallet(WALLET_ID, ACCOUNT_HOLDER_ID, "ACTIVE");
        Operation operation = operation(OperationType.WALLET_WITHDRAWAL, OperationStatus.FAILED);
        WalletLedgerEntry existingEntry = WalletLedgerEntry.builder()
                .walletId(WALLET_ID)
                .operationId(OPERATION_ID)
                .entryType(WalletLedgerEntryType.DEBIT)
                .category(WalletLedgerCategory.WITHDRAWAL)
                .status(WalletLedgerStatus.PENDING)
                .amount(new BigDecimal("2500.0000"))
                .currency("XOF")
                .build();

        when(walletRepository.findFirstByAccountHolderIdAndWalletTypeAndStatus(
                ACCOUNT_HOLDER_ID,
                WalletType.USER,
                "ACTIVE"
        )).thenReturn(Optional.of(wallet));
        when(walletLedgerEntryRepository.findByWalletIdAndOperationId(eq(WALLET_ID), eq(OPERATION_ID)))
                .thenReturn(Optional.of(existingEntry));
        when(walletLedgerEntryRepository.save(any(WalletLedgerEntry.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.syncOperation(operation);

        verify(walletLedgerEntryRepository).save(existingEntry);
        assertThat(existingEntry.getStatus()).isEqualTo(WalletLedgerStatus.FAILED);
        assertThat(existingEntry.getCategory()).isEqualTo(WalletLedgerCategory.WITHDRAWAL);
        assertThat(existingEntry.getEntryType()).isEqualTo(WalletLedgerEntryType.DEBIT);
    }

    @Test
    void syncOperation_pspFundedSubscription_doesNotDebitUserWallet() {
        // PSP-funded subscription (e.g. MoMo payment): pspReference is set.
        // The user already paid via the external PSP — debiting the wallet would double-charge them.
        Wallet wallet = userWallet(WALLET_ID, ACCOUNT_HOLDER_ID, "ACTIVE");
        Operation operation = operation(OperationType.SUBSCRIPTION, OperationStatus.CONFIRMED);
        operation.setPspReference("jawupay-ref-abc123");

        when(walletRepository.findFirstByAccountHolderIdAndWalletTypeAndStatus(
                ACCOUNT_HOLDER_ID,
                WalletType.USER,
                "ACTIVE"
        )).thenReturn(Optional.of(wallet));

        service.syncOperation(operation);

        verify(walletLedgerEntryRepository, times(0)).save(any());
    }

    @Test
    void syncOperation_walletFundedSubscription_debitsUserWallet() {
        // Wallet-funded subscription: pspReference is null.
        // The subscription fee must be deducted from the user's internal wallet.
        Wallet wallet = userWallet(WALLET_ID, ACCOUNT_HOLDER_ID, "ACTIVE");
        Operation operation = operation(OperationType.SUBSCRIPTION, OperationStatus.CONFIRMED);
        // pspReference stays null (default builder)

        when(walletRepository.findFirstByAccountHolderIdAndWalletTypeAndStatus(
                ACCOUNT_HOLDER_ID,
                WalletType.USER,
                "ACTIVE"
        )).thenReturn(Optional.of(wallet));
        when(walletLedgerEntryRepository.findByWalletIdAndOperationId(WALLET_ID, OPERATION_ID))
                .thenReturn(Optional.empty());
        when(walletLedgerEntryRepository.save(any(WalletLedgerEntry.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.syncOperation(operation);

        ArgumentCaptor<WalletLedgerEntry> captor = ArgumentCaptor.forClass(WalletLedgerEntry.class);
        verify(walletLedgerEntryRepository).save(captor.capture());

        WalletLedgerEntry entry = captor.getValue();
        assertThat(entry.getWalletId()).isEqualTo(WALLET_ID);
        assertThat(entry.getEntryType()).isEqualTo(WalletLedgerEntryType.DEBIT);
        assertThat(entry.getCategory()).isEqualTo(WalletLedgerCategory.PAYMENT);
        assertThat(entry.getStatus()).isEqualTo(WalletLedgerStatus.CONFIRMED);
    }

    private Wallet userWallet(UUID walletId, UUID accountHolderId, String status) {
        return Wallet.builder()
                .id(walletId)
                .accountHolderId(accountHolderId)
                .provider("MTN")
                .mobileMoneyNumber("22501234567")
                .status(status)
                .walletType(WalletType.USER)
                .build();
    }

    private Operation operation(OperationType type, OperationStatus status) {
        return Operation.builder()
                .id(OPERATION_ID)
                .accountHolderId(ACCOUNT_HOLDER_ID)
                .actorUserId(ACCOUNT_HOLDER_ID)
                .type(type)
                .status(status)
                .amount(new BigDecimal("2500.0000"))
                .currency("XOF")
                .idempotencyKey("idem-wallet-dashboard")
                .build();
    }
}
