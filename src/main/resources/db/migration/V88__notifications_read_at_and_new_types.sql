-- Add read_at column for tracking per-user read state (NULL = unread)
ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS read_at TIMESTAMPTZ;

-- Partial index for fast unread-count queries
CREATE INDEX IF NOT EXISTS idx_notifications_account_holder_unread
    ON notifications (account_holder_id)
    WHERE read_at IS NULL;

-- Extend the type check constraint with all new types required by the deeplink mapping
ALTER TABLE notifications
    DROP CONSTRAINT IF EXISTS ck_notifications_type;

ALTER TABLE notifications
    ADD CONSTRAINT ck_notifications_type CHECK (type IN (
        'SUBSCRIPTION_EXPIRING',
        'SUBSCRIPTION_EXPIRED',
        'SUBSCRIPTION_RENEWED',
        'LOAN_REPAYMENT_REMINDER',
        'LOAN_REPAID',
        'LOAN_STATUS_CHANGED',
        'REPAYMENT_DUE',
        'CONTRIBUTION_RECEIVED',
        'PAYMENT_FAILED',
        'CIRCLE_ACTIVITY',
        'MEMBER_JOINED',
        'CIRCLE_CREATED',
        'PRAYER_REMINDER',
        'CIRCLE_PRAYED',
        'TASK_AVAILABLE',
        'TASK_COMPLETED',
        'AGENT_ACTION',
        'CHAT_MESSAGE',
        'KYC_SUBMITTED',
        'KYC_VERIFIED',
        'KYC_REJECTED',
        'KYC_RESUBMIT_REQUIRED',
        'COMPLETE_KYC',
        'SECURITY_ALERT',
        'ACCOUNT_UPDATED'
    ));
