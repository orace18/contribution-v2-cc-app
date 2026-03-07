CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    account_holder_id UUID NOT NULL,
    actor_user_id UUID,
    type VARCHAR(64) NOT NULL,
    channel VARCHAR(16) NOT NULL,
    template_code VARCHAR(64),
    message VARCHAR(2000) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ,
    CONSTRAINT fk_notifications_account_holder FOREIGN KEY (account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT fk_notifications_actor_user FOREIGN KEY (actor_user_id) REFERENCES app_users(id),
    CONSTRAINT ck_notifications_type CHECK (type IN (
        'SUBSCRIPTION_EXPIRING',
        'SUBSCRIPTION_EXPIRED',
        'LOAN_REPAYMENT_REMINDER',
        'LOAN_REPAID',
        'CIRCLE_ACTIVITY',
        'TASK_COMPLETED',
        'AGENT_ACTION'
    )),
    CONSTRAINT ck_notifications_channel CHECK (channel IN ('SMS', 'EMAIL', 'PUSH', 'IN_APP')),
    CONSTRAINT ck_notifications_status CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
);

CREATE INDEX idx_notifications_account_holder_created ON notifications (account_holder_id, created_at DESC);
CREATE INDEX idx_notifications_status ON notifications (status);

CREATE TABLE notification_attempts (
    id UUID PRIMARY KEY,
    notification_id UUID NOT NULL,
    channel VARCHAR(16) NOT NULL,
    attempt_number INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL,
    provider_response VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_notification_attempts_notification FOREIGN KEY (notification_id) REFERENCES notifications(id),
    CONSTRAINT uk_notification_attempts_notification_attempt UNIQUE (notification_id, attempt_number),
    CONSTRAINT ck_notification_attempts_channel CHECK (channel IN ('SMS', 'EMAIL', 'PUSH', 'IN_APP')),
    CONSTRAINT ck_notification_attempts_status CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
);

CREATE INDEX idx_notification_attempts_notification ON notification_attempts (notification_id);

CREATE TABLE notification_templates (
    id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    channel VARCHAR(16) NOT NULL,
    subject VARCHAR(255),
    body_template VARCHAR(4000) NOT NULL,
    active BOOLEAN NOT NULL,
    CONSTRAINT uk_notification_templates_code_channel UNIQUE (code, channel),
    CONSTRAINT ck_notification_templates_channel CHECK (channel IN ('SMS', 'EMAIL', 'PUSH', 'IN_APP'))
);

INSERT INTO notification_templates (id, code, channel, subject, body_template, active)
VALUES
    ('50313741-f2e8-4ae9-8124-e019c7f68f9b', 'SUBSCRIPTION_EXPIRING_D7', 'IN_APP', 'Subscription expiring', 'Your subscription expires in 7 days.', TRUE),
    ('4f7f4099-cbc0-47c0-a5c4-56ea1ff98f00', 'SUBSCRIPTION_EXPIRING_D3', 'IN_APP', 'Subscription expiring', 'Your subscription expires in 3 days.', TRUE),
    ('d6e66125-24f1-4ff5-9f26-0df72d589b00', 'SUBSCRIPTION_EXPIRING_D1', 'IN_APP', 'Subscription expiring', 'Your subscription expires in 24 hours.', TRUE),
    ('fba5bf1f-d004-454f-bd80-e22c3f67816c', 'SUBSCRIPTION_EXPIRED_D0', 'IN_APP', 'Subscription expired', 'Your subscription expires today.', TRUE),
    ('ae2261a6-e4ed-4f2f-93c7-0798fbe88311', 'SUBSCRIPTION_EXPIRED_D3', 'IN_APP', 'Subscription expired', 'Your subscription expired 3 days ago.', TRUE)
ON CONFLICT ON CONSTRAINT uk_notification_templates_code_channel DO NOTHING;
