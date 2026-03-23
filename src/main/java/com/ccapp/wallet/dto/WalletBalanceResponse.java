package com.ccapp.wallet.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Derived (never stored directly): all values are computed from the ledger + reservations.
 *
 * <pre>
 *   available_balance = main_balance - reserved_balance
 * </pre>
 */
public record WalletBalanceResponse(
        UUID walletId,
        UUID accountHolderId,
        String currency,
        BigDecimal mainBalance,
        BigDecimal reservedBalance,
        BigDecimal availableBalance
) {
}
