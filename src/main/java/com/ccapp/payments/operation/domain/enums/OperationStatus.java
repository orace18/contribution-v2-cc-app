package com.ccapp.payments.operation.domain.enums;

public enum OperationStatus {
    INITIATED,
    PAYOUT_INITIATED,
    PENDING_PSP,
    CONFIRMED,
    FAILED,
    EXPIRED,
    CANCELED
}
