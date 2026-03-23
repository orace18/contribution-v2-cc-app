package com.ccapp.payments.operation.domain.enums;

public enum OperationType {
    SUBSCRIPTION,
    ADJUSTMENT,
    LOAN_DISBURSEMENT,
    LOAN_REPAYMENT,
    COMMISSION_PAYOUT,
    MARKETPLACE_PAYMENT,
    MARKETPLACE_MERCHANT_PAYOUT,
    MARKETPLACE_COMMISSION_ALLOCATION,
    MARKETPLACE_MERCHANT_PAYOUT_REVERSAL,
    MARKETPLACE_COMMISSION_ALLOCATION_REVERSAL,

    /** Peer-to-peer wallet payment (sender debited, recipient credited). */
    WALLET_PAYMENT,
    /** Inbound deposit funded by a PSP payment (PSP_CLEARING debited, wallet credited). */
    WALLET_DEPOSIT_PSP,
    /** Agent cash-in (agent float debited, user wallet credited). */
    WALLET_DEPOSIT_AGENT,
    /** Outbound withdrawal to PSP payout (wallet debited, PSP_CLEARING credited). */
    WALLET_WITHDRAWAL
}
