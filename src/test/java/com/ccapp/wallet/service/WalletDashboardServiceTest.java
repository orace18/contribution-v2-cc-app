package com.ccapp.wallet.service;

import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.identity.wallet.repository.WalletRepository;
import com.ccapp.payments.operation.domain.entity.Operation;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.repository.OperationRepository;
import com.ccapp.wallet.domain.entity.WalletLedgerEntry;
import com.ccapp.wallet.domain.enums.WalletLedgerCategory;
import com.ccapp.wallet.domain.enums.WalletLedgerEntryType;
import com.ccapp.wallet.domain.enums.WalletLedgerStatus;
import com.ccapp.wallet.domain.enums.WalletType;
import com.ccapp.wallet.dto.WalletDashboardOperationResponse;
import com.ccapp.wallet.dto.WalletDashboardResponse;
import com.ccapp.wallet.repository.WalletLedgerEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletDashboardServiceTest {

    private static final UUID ACCOUNT_HOLDER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACTOR_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID WALLET_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID OPERATION_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Mock
    private AccessControlService accessControlService;
    @Mock
    private AccountHolderService accountHolderService;
    @Mock
    private WalletRepository walletRepository;
    @Mock
    private WalletLedgerEntryRepository walletLedgerEntryRepository;
    @Mock
    private OperationRepository operationRepository;

    @InjectMocks
    private WalletDashboardService service;

    @Test
    void getDashboard_returnsZeroStatsWhenNoActiveUserWalletExists() {
        when(accountHolderService.getById(ACCOUNT_HOLDER_ID)).thenReturn(accountHolder());
        when(walletRepository.findFirstByAccountHolderIdAndWalletTypeAndStatus(
                ACCOUNT_HOLDER_ID,
                WalletType.USER,
                "ACTIVE"
        )).thenReturn(Optional.empty());
        stubRecentWalletOperations(List.of());

        WalletDashboardResponse response = service.getDashboard(ACTOR_USER_ID, ACCOUNT_HOLDER_ID);

        assertThat(response.balance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.monthlyIncome()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.totalWithdrawn()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.recentOperations()).isEmpty();
        verify(walletLedgerEntryRepository, never()).sumAmountByWalletIdAndEntryTypeAndStatus(any(), any(), any());
    }

    @Test
    void getRecentOperations_returnsLedgerHistoryIncludingDeposits() {
        WalletLedgerEntry confirmedDeposit = WalletLedgerEntry.builder()
                .walletId(WALLET_ID)
                .operationId(OPERATION_ID)
                .entryType(WalletLedgerEntryType.CREDIT)
                .category(WalletLedgerCategory.DEPOSIT)
                .status(WalletLedgerStatus.CONFIRMED)
                .amount(new BigDecimal("15000.0000"))
                .currency("XOF")
                .build();
        confirmedDeposit.setCreatedAt(OffsetDateTime.parse("2026-04-04T10:15:30Z"));

        stubAuthorizedWallet();
        when(walletLedgerEntryRepository.findByWalletIdOrderByCreatedAtDesc(eq(WALLET_ID), any(Pageable.class)))
                .thenReturn(List.of(confirmedDeposit));
        stubRecentWalletOperations(List.of());

        List<WalletDashboardOperationResponse> response = service.getRecentOperations(ACTOR_USER_ID, ACCOUNT_HOLDER_ID);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).operationId()).isEqualTo(OPERATION_ID);
        assertThat(response.get(0).type()).isEqualTo(WalletLedgerCategory.DEPOSIT);
        assertThat(response.get(0).status()).isEqualTo(WalletLedgerStatus.CONFIRMED);
        assertThat(response.get(0).currency()).isEqualTo("XOF");
    }

    @Test
    void getRecentOperations_includesPendingDepositWhenLedgerEntryIsMissing() {
        Operation pendingDeposit = Operation.builder()
                .id(OPERATION_ID)
                .accountHolderId(ACCOUNT_HOLDER_ID)
                .actorUserId(ACTOR_USER_ID)
                .type(OperationType.WALLET_DEPOSIT_PSP)
                .status(OperationStatus.PENDING_PSP)
                .amount(new BigDecimal("5000.0000"))
                .currency("XOF")
                .idempotencyKey("wallet-deposit-pending")
                .build();
        pendingDeposit.setCreatedAt(OffsetDateTime.parse("2026-04-04T18:20:00Z"));

        when(accountHolderService.getById(ACCOUNT_HOLDER_ID)).thenReturn(accountHolder());
        when(walletRepository.findFirstByAccountHolderIdAndWalletTypeAndStatus(
                ACCOUNT_HOLDER_ID,
                WalletType.USER,
                "ACTIVE"
        )).thenReturn(Optional.empty());
        stubRecentWalletOperations(List.of(pendingDeposit));

        List<WalletDashboardOperationResponse> response = service.getRecentOperations(ACTOR_USER_ID, ACCOUNT_HOLDER_ID);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).operationId()).isEqualTo(OPERATION_ID);
        assertThat(response.get(0).type()).isEqualTo(WalletLedgerCategory.DEPOSIT);
        assertThat(response.get(0).status()).isEqualTo(WalletLedgerStatus.PENDING);
        assertThat(response.get(0).amount()).isEqualByComparingTo("5000.0000");
    }

    @Test
    void getDashboard_computesMonthlyIncomeFromConfirmedCredits() {
        stubAuthorizedWallet();
        when(walletLedgerEntryRepository.sumAmountByWalletIdAndEntryTypeAndStatusAndCreatedAtGreaterThanEqual(
                eq(WALLET_ID),
                eq(WalletLedgerEntryType.CREDIT),
                eq(WalletLedgerStatus.CONFIRMED),
                any(OffsetDateTime.class)
        )).thenReturn(new BigDecimal("80000.0000"));
        when(walletLedgerEntryRepository.sumAmountByWalletIdAndCategoryAndStatus(
                WALLET_ID,
                WalletLedgerCategory.WITHDRAWAL,
                WalletLedgerStatus.CONFIRMED
        )).thenReturn(BigDecimal.ZERO);
        when(walletLedgerEntryRepository.sumAmountByWalletIdAndEntryTypeAndStatus(
                WALLET_ID,
                WalletLedgerEntryType.CREDIT,
                WalletLedgerStatus.CONFIRMED
        )).thenReturn(new BigDecimal("150000.0000"));
        when(walletLedgerEntryRepository.sumAmountByWalletIdAndEntryTypeAndStatus(
                WALLET_ID,
                WalletLedgerEntryType.DEBIT,
                WalletLedgerStatus.CONFIRMED
        )).thenReturn(new BigDecimal("70000.0000"));
        when(walletLedgerEntryRepository.findByWalletIdOrderByCreatedAtDesc(eq(WALLET_ID), any(Pageable.class)))
                .thenReturn(List.of());
        stubRecentWalletOperations(List.of());

        WalletDashboardResponse response = service.getDashboard(ACTOR_USER_ID, ACCOUNT_HOLDER_ID);

        assertThat(response.monthlyIncome()).isEqualByComparingTo("80000.0000");
        assertThat(response.balance()).isEqualByComparingTo("80000.0000");
    }

    @Test
    void getDashboard_countsOnlyConfirmedWithdrawalsInTotalWithdrawn() {
        stubAuthorizedWallet();
        when(walletLedgerEntryRepository.sumAmountByWalletIdAndEntryTypeAndStatusAndCreatedAtGreaterThanEqual(
                eq(WALLET_ID),
                eq(WalletLedgerEntryType.CREDIT),
                eq(WalletLedgerStatus.CONFIRMED),
                any(OffsetDateTime.class)
        )).thenReturn(BigDecimal.ZERO);
        when(walletLedgerEntryRepository.sumAmountByWalletIdAndCategoryAndStatus(
                WALLET_ID,
                WalletLedgerCategory.WITHDRAWAL,
                WalletLedgerStatus.CONFIRMED
        )).thenReturn(new BigDecimal("30000.0000"));
        when(walletLedgerEntryRepository.sumAmountByWalletIdAndEntryTypeAndStatus(
                WALLET_ID,
                WalletLedgerEntryType.CREDIT,
                WalletLedgerStatus.CONFIRMED
        )).thenReturn(new BigDecimal("150000.0000"));
        when(walletLedgerEntryRepository.sumAmountByWalletIdAndEntryTypeAndStatus(
                WALLET_ID,
                WalletLedgerEntryType.DEBIT,
                WalletLedgerStatus.CONFIRMED
        )).thenReturn(new BigDecimal("30000.0000"));
        when(walletLedgerEntryRepository.findByWalletIdOrderByCreatedAtDesc(eq(WALLET_ID), any(Pageable.class)))
                .thenReturn(List.of());
        stubRecentWalletOperations(List.of());

        WalletDashboardResponse response = service.getDashboard(ACTOR_USER_ID, ACCOUNT_HOLDER_ID);

        assertThat(response.totalWithdrawn()).isEqualByComparingTo("30000.0000");
    }

    private AccountHolder accountHolder() {
        return AccountHolder.builder()
                .id(ACCOUNT_HOLDER_ID)
                .firstName("John")
                .lastName("Doe")
                .build();
    }

    private void stubAuthorizedWallet() {
        when(accountHolderService.getById(ACCOUNT_HOLDER_ID)).thenReturn(accountHolder());
        when(walletRepository.findFirstByAccountHolderIdAndWalletTypeAndStatus(
                ACCOUNT_HOLDER_ID,
                WalletType.USER,
                "ACTIVE"
        )).thenReturn(Optional.of(Wallet.builder()
                .id(WALLET_ID)
                .accountHolderId(ACCOUNT_HOLDER_ID)
                .provider("MTN")
                .mobileMoneyNumber("22501234567")
                .status("ACTIVE")
                .walletType(WalletType.USER)
                .build()));
    }

    private void stubRecentWalletOperations(List<Operation> operations) {
        when(operationRepository.findRecentWalletOperationsForAccountHolder(
                eq(ACCOUNT_HOLDER_ID),
                any(),
                any(Pageable.class)
        )).thenReturn(operations);
    }
}
