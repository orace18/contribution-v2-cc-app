ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS target_id VARCHAR(128);

ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS deep_link_route_key VARCHAR(64);

ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS deep_link_label VARCHAR(160);

ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS deep_link_params_json TEXT NOT NULL DEFAULT '{}';

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
        'PRAYER_SUPPORT_RECEIVED',
        'PRAYER_COMMENT_RECEIVED',
        'PRAYER_STREAK_AT_RISK',
        'PRAYER_OFFICIAL_CONTENT_PUBLISHED',
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

INSERT INTO notification_templates (id, code, channel, subject, body_template, active)
VALUES
    (
        gen_random_uuid(),
        'PRAYER_SUPPORT_RECEIVED',
        'IN_APP',
        'Soutien recu',
        '{{actorDisplayName}} a prie pour ta demande.',
        TRUE
    ),
    (
        gen_random_uuid(),
        'PRAYER_COMMENT_RECEIVED',
        'IN_APP',
        'Nouveau commentaire',
        '{{actorDisplayName}} t a laisse un commentaire d encouragement.',
        TRUE
    ),
    (
        gen_random_uuid(),
        'PRAYER_DAILY_AVAILABLE',
        'IN_APP',
        'Priere du jour disponible',
        'Ta priere du jour "{{title}}" est disponible.',
        TRUE
    ),
    (
        gen_random_uuid(),
        'PRAYER_STREAK_AT_RISK',
        'IN_APP',
        'Ta serie est en danger',
        'Ta serie de {{streakCount}} jour(s) peut continuer aujourd hui avec "{{title}}".',
        TRUE
    ),
    (
        gen_random_uuid(),
        'PRAYER_OFFICIAL_CONTENT_PUBLISHED',
        'IN_APP',
        'Nouveau contenu officiel',
        'Un nouveau contenu officiel est disponible : "{{title}}".',
        TRUE
    )
ON CONFLICT ON CONSTRAINT uk_notification_templates_code_channel DO NOTHING;
