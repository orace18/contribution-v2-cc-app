package com.ccapp.identity.wallet.service;

import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.identity.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final AccountHolderService accountHolderService;

    @Transactional
    public Wallet createWallet(UUID accountHolderId, String provider, String mobileMoneyNumber, String status) {
        accountHolderService.getById(accountHolderId);
        return walletRepository.save(Wallet.builder()
                .accountHolderId(accountHolderId)
                .provider(provider)
                .mobileMoneyNumber(mobileMoneyNumber)
                .status(status)
                .build());
    }

    @Transactional(readOnly = true)
    public List<Wallet> getWallets(UUID accountHolderId) {
        accountHolderService.getById(accountHolderId);
        return walletRepository.findByAccountHolderIdOrderByCreatedAtDesc(accountHolderId);
    }
}
