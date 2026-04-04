package com.ccapp.wallet.service;

import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.identity.wallet.repository.WalletRepository;
import com.ccapp.wallet.domain.entity.WalletLedgerEntry;
import com.ccapp.wallet.domain.enums.WalletLedgerCategory;
import com.ccapp.wallet.domain.enums.WalletLedgerEntryType;
import com.ccapp.wallet.domain.enums.WalletLedgerStatus;
import com.ccapp.wallet.domain.enums.WalletType;
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

        WalletDashboardResponse response = service.getDashboard(ACTOR_USER_ID, ACCOUNT_HOLDER_ID);

        assertThat(response.balance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.monthlyIncome()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.totalWithdrawn()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.recentOperations()).isEmpty();
        verify(walletLedgerEntryRepository, never()).sumAmountByWalletIdAndEntryTypeAndStatus(any(), any(), any());
    }

    @Test
    void getDashboard_returnsLedgerBasedStatsAndRecentOperations() {
        Wallet wallet = Wallet.builder()
                .id(WALLET_ID)
                .accountHolderId(ACCOUNT_HOLDER_ID)
                .provider("MTN")
                .mobileMoneyNumber("22501234567")
                .status("ACTIVE")
                .walletType(WalletType.USER)
                .build();
        WalletLedgerEntry pendingEntry = WalletLedgerEntry.builder()
                .walletId(WALLET_ID)
                .operationId(OPERATION_ID)
                .entryType(WalletLedgerEntryType.CREDIT)
                .category(WalletLedgerCategory.DEPOSIT)
                .status(WalletLedgerStatus.PENDING)
                .amount(new BigDecimal("15000.0000"))
                .currency("XOF")
                .build();
        pendingEntry.setCreatedAt(OffsetDateTime.parse("2026-04-04T10:15:30Z"));
        WalletLedgerEntry failedEntry = WalletLedgerEntry.builder()
                .walletId(WALLET_ID)
                .operationId(UUID.fromString("55555555-5555-5555-5555-555555555555"))
                .entryType(WalletLedgerEntryType.DEBIT)
                .category(WalletLedgerCategory.WITHDRAWAL)
                .status(WalletLedgerStatus.FAILED)
                .amount(new BigDecimal("2000.0000"))
                .currency("XOF")
                .build();
        failedEntry.setCreatedAt(OffsetDateTime.parse("2026-04-03T09:00:00Z"));

        when(accountHolderService.getById(ACCOUNT_HOLDER_ID)).thenReturn(accountHolder());
        when(walletRepository.findFirstByAccountHolderIdAndWalletTypeAndStatus(
                ACCOUNT_HOLDER_ID,
                WalletType.USER,
                "ACTIVE"
        )).thenReturn(Optional.of(wallet));
        when(walletLedgerEntryRepository.sumAmountByWalletIdAndEntryTypeAndStatusAndCategoryInAndCreatedAtGreaterThanEqual(
                eq(WALLET_ID),
                eq(WalletLedgerEntryType.CREDIT),
                eq(WalletLedgerStatus.CONFIRMED),
                any(),
                any(OffsetDateTime.class)
        )).thenReturn(new BigDecimal("80000.0000"));
        when(walletLedgerEntryRepository.sumAmountByWalletIdAndEntryTypeAndStatusAndCategoryIn(
                WALLET_ID,
                WalletLedgerEntryType.DEBIT,
                WalletLedgerStatus.CONFIRMED,
                List.of(WalletLedgerCategory.WITHDRAWAL, WalletLedgerCategory.TRANSFER_OUT)
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
        )).thenReturn(new BigDecimal("70000.0000"));
        when(walletLedgerEntryRepository.findByWalletIdOrderByCreatedAtDesc(eq(WALLET_ID), any(Pageable.class)))
                .thenReturn(List.of(pendingEntry, failedEntry));

        WalletDashboardResponse response = service.getDashboard(ACTOR_USER_ID, ACCOUNT_HOLDER_ID);

        assertThat(response.balance()).isEqualByComparingTo("80000.0000");
        assertThat(response.monthlyIncome()).isEqualByComparingTo("80000.0000");
        assertThat(response.totalWithdrawn()).isEqualByComparingTo("30000.0000");
        assertThat(response.recentOperations()).hasSize(2);
        assertThat(response.recentOperations().get(0).status()).isEqualTo(WalletLedgerStatus.PENDING);
        assertThat(response.recentOperations().get(0).category()).isEqualTo(WalletLedgerCategory.DEPOSIT);
        assertThat(response.recentOperations().get(1).status()).isEqualTo(WalletLedgerStatus.FAILED);
        assertThat(response.recentOperations().get(1).category()).isEqualTo(WalletLedgerCategory.WITHDRAWAL);
    }

    private AccountHolder accountHolder() {
        return AccountHolder.builder()
                .id(ACCOUNT_HOLDER_ID)
                .firstName("John")
                .lastName("Doe")
                .build();
    }
}
