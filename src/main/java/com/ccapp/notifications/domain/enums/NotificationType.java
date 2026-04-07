package com.ccapp.notifications.domain.enums;

public enum NotificationType {
    // Subscription / payment
    SUBSCRIPTION_EXPIRING,
    SUBSCRIPTION_EXPIRED,
    SUBSCRIPTION_RENEWED,
    PAYMENT_FAILED,
    CONTRIBUTION_RECEIVED,

    // Loan / financing
    LOAN_REPAYMENT_REMINDER,
    LOAN_REPAID,
    LOAN_STATUS_CHANGED,
    REPAYMENT_DUE,

    // Circle / community
    CIRCLE_ACTIVITY,
    MEMBER_JOINED,
    CIRCLE_CREATED,

    // Prayer
    PRAYER_REMINDER,
    PRAYER_SUPPORT_RECEIVED,
    PRAYER_COMMENT_RECEIVED,
    PRAYER_STREAK_AT_RISK,
    PRAYER_OFFICIAL_CONTENT_PUBLISHED,
    /**
     * @deprecated Legacy circle/community prayer notification. Replace with Business Prayer support notifications.
     */
    @Deprecated(since = "2026-04", forRemoval = false)
    CIRCLE_PRAYED,

    // Tasks / engagement
    TASK_AVAILABLE,
    TASK_COMPLETED,
    AGENT_ACTION,

    // Chat
    CHAT_MESSAGE,

    // KYC
    KYC_SUBMITTED,
    KYC_VERIFIED,
    KYC_REJECTED,
    KYC_RESUBMIT_REQUIRED,
    COMPLETE_KYC,

    // System / account
    SECURITY_ALERT,
    ACCOUNT_UPDATED
}
