package com.ccapp.wallet.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletStatusService {

    private final WalletLifecycleService walletLifecycleService;

    public WalletStatusSnapshot resolveStatus(UUID accountHolderId, boolean attemptAutoActivation) {
        if (attemptAutoActivation) {
            walletLifecycleService.activateIfEligible(accountHolderId);
        }
        return walletLifecycleService.resolveStatus(accountHolderId);
    }
}
