ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS title VARCHAR(255);

UPDATE notifications
SET title = COALESCE(NULLIF(title, ''), type)
WHERE title IS NULL OR title = '';

ALTER TABLE notifications
    DROP CONSTRAINT IF EXISTS ck_notifications_type;

ALTER TABLE notifications
    ADD CONSTRAINT ck_notifications_type CHECK (type IN (
        'SUBSCRIPTION_EXPIRING',
        'SUBSCRIPTION_EXPIRED',
        'LOAN_REPAYMENT_REMINDER',
        'LOAN_REPAID',
        'CIRCLE_ACTIVITY',
        'TASK_AVAILABLE',
        'TASK_COMPLETED',
        'AGENT_ACTION',
        'CHAT_MESSAGE',
        'KYC_SUBMITTED',
        'KYC_VERIFIED',
        'KYC_REJECTED',
        'KYC_RESUBMIT_REQUIRED'
    ));
