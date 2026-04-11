package com.ccapp.wallet.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletStatusServiceTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Mock
    private WalletLifecycleService walletLifecycleService;

    @InjectMocks
    private WalletStatusService walletStatusService;

    @Test
    void resolveStatus_shouldReturnLifecycleSnapshotWithoutAutoActivation() {
        WalletStatusSnapshot snapshot = new WalletStatusSnapshot(
                UUID.randomUUID(),
                true,
                false,
                WalletState.ACTIVE,
                "Wallet is active",
                true,
                true,
                List.of()
        );
        when(walletLifecycleService.resolveStatus(ACCOUNT_ID)).thenReturn(snapshot);

        WalletStatusSnapshot resolved = walletStatusService.resolveStatus(ACCOUNT_ID, false);

        assertSame(snapshot, resolved);
        verify(walletLifecycleService, never()).activateIfEligible(ACCOUNT_ID);
    }

    @Test
    void resolveStatus_shouldAttemptAutoActivationBeforeReadingSnapshot() {
        WalletStatusSnapshot snapshot = new WalletStatusSnapshot(
                null,
                false,
                false,
                WalletState.READY_FOR_WALLET_CREATION,
                "Wallet is ready for creation",
                true,
                true,
                List.of()
        );
        when(walletLifecycleService.activateIfEligible(ACCOUNT_ID)).thenReturn(Optional.empty());
        when(walletLifecycleService.resolveStatus(ACCOUNT_ID)).thenReturn(snapshot);

        WalletStatusSnapshot resolved = walletStatusService.resolveStatus(ACCOUNT_ID, true);

        assertSame(snapshot, resolved);
        verify(walletLifecycleService).activateIfEligible(ACCOUNT_ID);
        verify(walletLifecycleService).resolveStatus(ACCOUNT_ID);
    }
}
