package com.ccapp.identity.wallet.service;

import com.ccapp.country.config.service.CountryChartOfAccountsService;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.identity.wallet.repository.WalletRepository;
import com.ccapp.wallet.repository.WalletLedgerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private final WalletRepository walletRepository;
    private final AccountHolderService accountHolderService;
    private final CountryChartOfAccountsService countryChartOfAccountsService;
    private final WalletLedgerRepository walletLedgerRepository;

    @Transactional
    public Wallet createWallet(UUID accountHolderId, String provider, String mobileMoneyNumber, String status) {
        log.info("Identity wallet create accountHolderId={} provider={} status={}", accountHolderId, provider, status);
        AccountHolder accountHolder = accountHolderService.getById(accountHolderId);
        Wallet saved = walletRepository.save(Wallet.builder()
                .accountHolderId(accountHolderId)
                .provider(provider)
                .mobileMoneyNumber(mobileMoneyNumber)
                .status(status)
                .build());
        var walletLedgerAccount = countryChartOfAccountsService.findOrCreateWallet(accountHolder);
        walletLedgerRepository.createBalanceIfMissing(walletLedgerAccount.getAccountCode(), walletLedgerAccount.getCurrency());
        log.info("Identity wallet created walletId={} accountHolderId={}", saved.getId(), saved.getAccountHolderId());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Wallet> getWallets(UUID accountHolderId) {
        log.debug("Identity wallet list accountHolderId={}", accountHolderId);
        accountHolderService.getById(accountHolderId);
        List<Wallet> wallets = walletRepository.findByAccountHolderIdOrderByCreatedAtDesc(accountHolderId);
        log.debug("Identity wallet list result accountHolderId={} count={}", accountHolderId, wallets.size());
        return wallets;
    }
}
