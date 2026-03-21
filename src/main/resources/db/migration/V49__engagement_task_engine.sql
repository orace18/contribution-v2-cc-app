ALTER TABLE user_progress RENAME COLUMN user_id TO account_holder_id;

ALTER TABLE user_progress
    DROP CONSTRAINT IF EXISTS fk_user_progress_user;

ALTER TABLE user_progress
    ADD CONSTRAINT fk_user_progress_account_holder
        FOREIGN KEY (account_holder_id) REFERENCES account_holders(id);

ALTER TABLE user_progress
    DROP CONSTRAINT IF EXISTS ck_user_progress_current_step;

ALTER TABLE user_progress
    DROP CONSTRAINT IF EXISTS ck_user_progress_next_task;

ALTER TABLE user_progress
    ADD CONSTRAINT ck_user_progress_current_step CHECK (
        current_step IS NULL OR current_step IN (
            'COMPLETE_KYC',
            'JOIN_CIRCLE',
            'MAKE_FIRST_CONTRIBUTION',
            'INVITE_MEMBER',
            'APPLY_LOAN',
            'REPAY_INSTALLMENT',
            'COMPLETE_PROFILE',
            'VERIFY_PHONE',
            'PHONE_VERIFIED',
            'KYC_COMPLETED',
            'CIRCLE_JOINED',
            'TRAINING_COMPLETED',
            'PROJECT_SUBMITTED',
            'PERSONAL_CONTRIBUTION_PAID'
        )
    );

ALTER TABLE user_progress
    ADD CONSTRAINT ck_user_progress_next_task CHECK (
        next_task IS NULL OR next_task IN (
            'COMPLETE_KYC',
            'JOIN_CIRCLE',
            'MAKE_FIRST_CONTRIBUTION',
            'INVITE_MEMBER',
            'APPLY_LOAN',
            'REPAY_INSTALLMENT',
            'COMPLETE_PROFILE',
            'VERIFY_PHONE',
            'PHONE_VERIFIED',
            'KYC_COMPLETED',
            'CIRCLE_JOINED',
            'TRAINING_COMPLETED',
            'PROJECT_SUBMITTED',
            'PERSONAL_CONTRIBUTION_PAID'
        )
    );

ALTER TABLE user_tasks RENAME COLUMN user_id TO account_holder_id;
ALTER TABLE user_tasks RENAME COLUMN validated_at TO completed_at;

ALTER TABLE user_tasks
    DROP CONSTRAINT IF EXISTS fk_user_tasks_user;

ALTER TABLE user_tasks
    ADD CONSTRAINT fk_user_tasks_account_holder
        FOREIGN KEY (account_holder_id) REFERENCES account_holders(id);

ALTER TABLE user_tasks
    DROP CONSTRAINT IF EXISTS uk_user_tasks_user_task_type;

DROP INDEX IF EXISTS idx_user_tasks_user_id;
DROP INDEX IF EXISTS idx_user_tasks_user_status;

ALTER TABLE user_tasks
    ADD COLUMN IF NOT EXISTS title VARCHAR(255);

ALTER TABLE user_tasks
    ADD COLUMN IF NOT EXISTS description VARCHAR(2000);

ALTER TABLE user_tasks
    ADD COLUMN IF NOT EXISTS priority_score INTEGER NOT NULL DEFAULT 0;

ALTER TABLE user_tasks
    ADD COLUMN IF NOT EXISTS due_date TIMESTAMPTZ;

ALTER TABLE user_tasks
    ADD COLUMN IF NOT EXISTS payload_json TEXT;

ALTER TABLE user_tasks
    ADD COLUMN IF NOT EXISTS action_json TEXT NOT NULL DEFAULT '{}';

ALTER TABLE user_tasks
    ADD COLUMN IF NOT EXISTS active_key VARCHAR(64);

UPDATE user_tasks
SET title = COALESCE(title, task_type),
    description = COALESCE(description, task_type),
    payload_json = COALESCE(payload_json, '{}'),
    action_json = COALESCE(action_json, '{}'),
    active_key = CASE WHEN status IN ('PENDING', 'IN_PROGRESS') THEN task_type ELSE NULL END
WHERE title IS NULL
   OR description IS NULL
   OR payload_json IS NULL
   OR action_json IS NULL
   OR active_key IS NULL;

ALTER TABLE user_tasks
    ALTER COLUMN title SET NOT NULL;

ALTER TABLE user_tasks
    DROP CONSTRAINT IF EXISTS ck_user_tasks_type;

ALTER TABLE user_tasks
    DROP CONSTRAINT IF EXISTS ck_user_tasks_status;

ALTER TABLE user_tasks
    ADD CONSTRAINT ck_user_tasks_type CHECK (
        task_type IN (
            'COMPLETE_KYC',
            'JOIN_CIRCLE',
            'MAKE_FIRST_CONTRIBUTION',
            'INVITE_MEMBER',
            'APPLY_LOAN',
            'REPAY_INSTALLMENT',
            'COMPLETE_PROFILE',
            'VERIFY_PHONE',
            'PHONE_VERIFIED',
            'KYC_COMPLETED',
            'CIRCLE_JOINED',
            'TRAINING_COMPLETED',
            'PROJECT_SUBMITTED',
            'PERSONAL_CONTRIBUTION_PAID'
        )
    );

ALTER TABLE user_tasks
    ADD CONSTRAINT ck_user_tasks_status CHECK (
        status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'EXPIRED', 'REJECTED')
    );

ALTER TABLE user_tasks
    ADD CONSTRAINT uk_user_tasks_active_key UNIQUE (account_holder_id, active_key);

CREATE INDEX idx_user_tasks_account_holder_id ON user_tasks (account_holder_id);
CREATE INDEX idx_user_tasks_account_holder_status ON user_tasks (account_holder_id, status);

CREATE TABLE IF NOT EXISTS task_definitions (
    id UUID PRIMARY KEY,
    task_type VARCHAR(64) NOT NULL,
    title_template VARCHAR(255) NOT NULL,
    description_template VARCHAR(2000),
    trigger_event VARCHAR(64) NOT NULL,
    action_json TEXT NOT NULL,
    reward_points INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    metadata_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_task_definitions_task_type UNIQUE (task_type)
);

CREATE INDEX IF NOT EXISTS idx_task_definitions_trigger_event ON task_definitions (trigger_event);

INSERT INTO task_definitions (id, task_type, title_template, description_template, trigger_event, action_json, reward_points, is_active, metadata_json)
VALUES
    ('d0ce2c3b-6f01-4ce9-919f-1bfdb8df5391', 'COMPLETE_KYC', 'Complete ton KYC', 'Valide ton identite pour debloquer les services financiers.', 'ACCOUNT_HOLDER_CREATED', '{"type":"FLOW","flow":"KYC_FLOW"}', 50, TRUE, '{"basePriority":700}'),
    ('ab8b6e40-0dc1-4dbf-804d-c69fd08f70f8', 'JOIN_CIRCLE', 'Rejoins un cercle', 'Entre dans un cercle pour avancer avec ta communaute.', 'KYC_COMPLETED', '{"type":"FLOW","flow":"CIRCLE_JOIN_FLOW"}', 40, TRUE, '{"basePriority":600}'),
    ('c5d95bde-e2e4-4767-a2dc-48243eb67283', 'MAKE_FIRST_CONTRIBUTION', 'Fais ta premiere contribution', 'Realise ta premiere contribution pour renforcer ta discipline financiere.', 'CONTRIBUTION_RECEIVED', '{"type":"FLOW","flow":"CIRCLE_CONTRIBUTION_FLOW"}', 60, TRUE, '{"basePriority":650}'),
    ('75e2b6f1-5115-4c87-84f1-744cc1b9c278', 'INVITE_MEMBER', 'Invite un membre', 'Fais grandir ton cercle en invitant un proche.', 'CIRCLE_JOINED', '{"type":"FLOW","flow":"INVITE_FLOW"}', 25, TRUE, '{"basePriority":350}'),
    ('f798bf5e-1419-49ca-bcef-7846030e9ac5', 'APPLY_LOAN', 'Demande un credit', 'Soumets une demande pour financer ton projet.', 'LOAN_APPLIED', '{"type":"FLOW","flow":"LOAN_APPLICATION_FLOW"}', 80, TRUE, '{"basePriority":800}'),
    ('cb268321-c4b1-4a93-b5ce-76bd3edec3e5', 'REPAY_INSTALLMENT', 'Remboursement attendu', 'Regle ton echeance de credit pour rester en bonne posture.', 'REPAYMENT_DUE', '{"type":"FLOW","flow":"LOAN_REPAYMENT_FLOW","params":{"loanId":"{{loanId}}"}}', 100, TRUE, '{"basePriority":950}'),
    ('d31c6e82-197d-40b5-baf7-0ea4e4fc7d1a', 'COMPLETE_PROFILE', 'Complete ton profil', 'Ajoute les informations manquantes pour finaliser ton compte.', 'PROFILE_COMPLETED', '{"type":"FLOW","flow":"PROFILE_COMPLETION_FLOW"}', 30, TRUE, '{"basePriority":500}'),
    ('fa870379-f2d7-4957-a0ae-769278ade41d', 'VERIFY_PHONE', 'Verifie ton telephone', 'Confirme ton numero pour securiser ton compte.', 'PHONE_VERIFIED', '{"type":"FLOW","flow":"PHONE_VERIFICATION_FLOW"}', 35, TRUE, '{"basePriority":550}')
ON CONFLICT (task_type) DO NOTHING;

CREATE TABLE IF NOT EXISTS task_audit_logs (
    id UUID PRIMARY KEY,
    user_task_id UUID,
    account_holder_id UUID NOT NULL,
    task_type VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    details_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_task_audit_logs_user_task FOREIGN KEY (user_task_id) REFERENCES user_tasks(id),
    CONSTRAINT fk_task_audit_logs_account_holder FOREIGN KEY (account_holder_id) REFERENCES account_holders(id)
);

CREATE INDEX IF NOT EXISTS idx_task_audit_logs_account_holder_created ON task_audit_logs (account_holder_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_task_audit_logs_user_task ON task_audit_logs (user_task_id);

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
        'AGENT_ACTION'
    ));
