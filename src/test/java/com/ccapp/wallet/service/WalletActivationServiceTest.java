package com.ccapp.wallet.service;

import com.ccapp.identity.wallet.domain.entity.Wallet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletActivationServiceTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Mock
    private WalletLifecycleService walletLifecycleService;

    @InjectMocks
    private WalletActivationService walletActivationService;

    @Test
    void activate_shouldDelegateToLifecycleService() {
        Wallet wallet = Wallet.builder()
                .id(UUID.randomUUID())
                .accountHolderId(ACCOUNT_ID)
                .status("ACTIVE")
                .build();
        when(walletLifecycleService.activate(ACCOUNT_ID)).thenReturn(wallet);

        Wallet activated = walletActivationService.activate(ACCOUNT_ID);

        assertSame(wallet, activated);
        verify(walletLifecycleService).activate(ACCOUNT_ID);
    }

    @Test
    void activateIfEligible_shouldDelegateToLifecycleService() {
        Wallet wallet = Wallet.builder()
                .id(UUID.randomUUID())
                .accountHolderId(ACCOUNT_ID)
                .status("ACTIVE")
                .build();
        when(walletLifecycleService.activateIfEligible(ACCOUNT_ID)).thenReturn(Optional.of(wallet));

        Optional<Wallet> activated = walletActivationService.activateIfEligible(ACCOUNT_ID);

        assertSame(wallet, activated.orElseThrow());
        verify(walletLifecycleService).activateIfEligible(ACCOUNT_ID);
    }
}
