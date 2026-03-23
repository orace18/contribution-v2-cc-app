package com.ccapp.wallet.domain.enums;

public enum WalletReservationStatus {
    /** Funds are held – not spendable. */
    ACTIVE,
    /** Explicitly released by the business layer. */
    RELEASED,
    /** Expired past expiry_date without being consumed. */
    EXPIRED,
    /** Consumed by the operation it was created for. */
    CONSUMED
}
