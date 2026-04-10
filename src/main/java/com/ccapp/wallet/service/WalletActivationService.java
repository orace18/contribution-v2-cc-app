package com.ccapp.wallet.service;

import com.ccapp.identity.wallet.domain.entity.Wallet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletActivationService {

    private final WalletLifecycleService walletLifecycleService;

    public Wallet activate(UUID accountHolderId) {
        return walletLifecycleService.activate(accountHolderId);
    }

    public Optional<Wallet> activateIfEligible(UUID accountHolderId) {
        return walletLifecycleService.activateIfEligible(accountHolderId);
    }
}
