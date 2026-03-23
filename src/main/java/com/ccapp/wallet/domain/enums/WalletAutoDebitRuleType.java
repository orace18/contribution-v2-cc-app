package com.ccapp.wallet.domain.enums;

public enum WalletAutoDebitRuleType {
    /** Automatically repay an outstanding loan installment. */
    LOAN_REPAYMENT,
    /** Collect a commission percentage on credit. */
    COMMISSION_COLLECTION,
    /** Settle an outstanding debt balance. */
    DEBT_SETTLEMENT
}
