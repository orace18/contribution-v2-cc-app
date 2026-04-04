package com.ccapp.wallet.dto;

import java.util.UUID;

public record WalletStatusResponse(
        UUID walletId,
        boolean active,
        boolean hasPin
) {
}
