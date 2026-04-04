package com.ccapp.wallet.service;

import com.ccapp.country.config.domain.entity.CountryChartAccount;
import com.ccapp.country.config.service.CountryChartOfAccountsService;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.identity.wallet.repository.WalletRepository;
import com.ccapp.ledger.domain.entity.AccountBalanceSnapshot;
import com.ccapp.wallet.dto.WalletBalanceResponse;
import com.ccapp.wallet.repository.WalletLedgerRepository;
import com.ccapp.wallet.repository.WalletReservationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletBalanceServiceTest {

    private static final UUID ACCOUNT_HOLDER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID WALLET_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private WalletLedgerRepository walletLedgerRepository;
    @Mock
    private CountryChartOfAccountsService countryChartOfAccountsService;
    @Mock
    private AccountHolderRepository accountHolderRepository;
    @Mock
    private WalletReservationRepository reservationRepository;
    @Mock
    private WalletRepository walletRepository;

    @InjectMocks
    private WalletBalanceService walletBalanceService;

    @Test
    void getBalance_returnsZeroAndBootstrapsSnapshotWhenLedgerBalanceIsMissing() {
        Wallet wallet = Wallet.builder()
                .id(WALLET_ID)
                .accountHolderId(ACCOUNT_HOLDER_ID)
                .status("ACTIVE")
                .build();
        AccountHolder accountHolder = AccountHolder.builder()
                .id(ACCOUNT_HOLDER_ID)
                .firstName("John")
                .lastName("Doe")
                .build();
        CountryChartAccount walletLedgerAccount = CountryChartAccount.builder()
                .accountCode("71100001")
                .currency("XOF")
                .build();

        when(walletRepository.findFirstByAccountHolderIdAndStatusOrderByCreatedAtDesc(ACCOUNT_HOLDER_ID, "ACTIVE"))
                .thenReturn(Optional.of(wallet));
        when(accountHolderRepository.findById(ACCOUNT_HOLDER_ID)).thenReturn(Optional.of(accountHolder));
        when(countryChartOfAccountsService.findOrCreateWallet(accountHolder)).thenReturn(walletLedgerAccount);
        when(walletLedgerRepository.createBalanceIfMissing("71100001", "XOF"))
                .thenReturn(AccountBalanceSnapshot.builder()
                        .accountCode("71100001")
                        .currency("XOF")
                        .balance(BigDecimal.ZERO)
                        .version(0L)
                        .build());
        when(reservationRepository.sumSpendBlockingReservationsByWalletId(eq(WALLET_ID), any(OffsetDateTime.class)))
                .thenReturn(Optional.of(BigDecimal.ZERO));

        WalletBalanceResponse response = walletBalanceService.getBalance(ACCOUNT_HOLDER_ID, "xof");

        assertEquals(WALLET_ID, response.walletId());
        assertEquals(ACCOUNT_HOLDER_ID, response.accountHolderId());
        assertEquals("XOF", response.currency());
        assertEquals(BigDecimal.ZERO, response.mainBalance());
        assertEquals(BigDecimal.ZERO, response.reservedBalance());
        assertEquals(BigDecimal.ZERO, response.availableBalance());
        verify(walletLedgerRepository).createBalanceIfMissing("71100001", "XOF");
    }

    @Test
    void getBalance_returnsZeroResponseWhenNoActiveWalletExists() {
        when(walletRepository.findFirstByAccountHolderIdAndStatusOrderByCreatedAtDesc(ACCOUNT_HOLDER_ID, "ACTIVE"))
                .thenReturn(Optional.empty());

        WalletBalanceResponse response = walletBalanceService.getBalance(ACCOUNT_HOLDER_ID, "xof");

        assertNull(response.walletId());
        assertEquals(ACCOUNT_HOLDER_ID, response.accountHolderId());
        assertEquals("XOF", response.currency());
        assertEquals(BigDecimal.ZERO, response.mainBalance());
        assertEquals(BigDecimal.ZERO, response.reservedBalance());
        assertEquals(BigDecimal.ZERO, response.availableBalance());
        verify(walletLedgerRepository, never()).createBalanceIfMissing(any(), any());
    }
}
