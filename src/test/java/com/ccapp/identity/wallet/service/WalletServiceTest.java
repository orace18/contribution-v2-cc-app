package com.ccapp.identity.wallet.service;

import com.ccapp.country.config.domain.entity.CountryChartAccount;
import com.ccapp.country.config.service.CountryChartOfAccountsService;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.identity.wallet.repository.WalletRepository;
import com.ccapp.wallet.repository.WalletLedgerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;
    @Mock
    private AccountHolderService accountHolderService;
    @Mock
    private CountryChartOfAccountsService countryChartOfAccountsService;
    @Mock
    private WalletLedgerRepository walletLedgerRepository;

    @InjectMocks
    private WalletService walletService;

    @Test
    void createWallet_initializesZeroLedgerBalanceSnapshot() {
        UUID accountHolderId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        AccountHolder accountHolder = AccountHolder.builder()
                .id(accountHolderId)
                .firstName("Jane")
                .lastName("Doe")
                .serviceCountryCode("BJ")
                .build();
        CountryChartAccount walletLedgerAccount = CountryChartAccount.builder()
                .accountCode("71100002")
                .currency("XOF")
                .build();
        Wallet savedWallet = Wallet.builder()
                .id(UUID.fromString("44444444-4444-4444-4444-444444444444"))
                .accountHolderId(accountHolderId)
                .provider("MOBILE")
                .mobileMoneyNumber("22961000000")
                .status("ACTIVE")
                .build();

        when(accountHolderService.getById(accountHolderId)).thenReturn(accountHolder);
        when(walletRepository.save(any(Wallet.class))).thenReturn(savedWallet);
        when(countryChartOfAccountsService.findOrCreateWallet(accountHolder)).thenReturn(walletLedgerAccount);

        Wallet wallet = walletService.createWallet(accountHolderId, "MOBILE", "22961000000", "ACTIVE");

        assertEquals(savedWallet.getId(), wallet.getId());
        verify(walletLedgerRepository).createBalanceIfMissing("71100002", "XOF");
    }
}
