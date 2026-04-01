ALTER TABLE notifications
    DROP CONSTRAINT ck_notifications_type,
    ADD CONSTRAINT ck_notifications_type CHECK (type IN (
        'SUBSCRIPTION_EXPIRING',
        'SUBSCRIPTION_EXPIRED',
        'LOAN_REPAYMENT_REMINDER',
        'LOAN_REPAID',
        'CIRCLE_ACTIVITY',
        'TASK_AVAILABLE',
        'TASK_COMPLETED',
        'AGENT_ACTION',
        'CHAT_MESSAGE'
    ));
