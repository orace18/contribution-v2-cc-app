CREATE TABLE user_progress (
    user_id UUID PRIMARY KEY,
    current_step VARCHAR(64),
    progress_percentage NUMERIC(5,2) NOT NULL DEFAULT 0,
    next_task VARCHAR(64),
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_user_progress_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT ck_user_progress_percentage CHECK (progress_percentage >= 0 AND progress_percentage <= 100),
    CONSTRAINT ck_user_progress_current_step CHECK (
        current_step IS NULL OR current_step IN (
            'PHONE_VERIFIED',
            'KYC_COMPLETED',
            'CIRCLE_JOINED',
            'TRAINING_COMPLETED',
            'PROJECT_SUBMITTED',
            'PERSONAL_CONTRIBUTION_PAID'
        )
    ),
    CONSTRAINT ck_user_progress_next_task CHECK (
        next_task IS NULL OR next_task IN (
            'PHONE_VERIFIED',
            'KYC_COMPLETED',
            'CIRCLE_JOINED',
            'TRAINING_COMPLETED',
            'PROJECT_SUBMITTED',
            'PERSONAL_CONTRIBUTION_PAID'
        )
    )
);

CREATE TABLE user_tasks (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    task_type VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    validated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_user_tasks_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uk_user_tasks_user_task_type UNIQUE (user_id, task_type),
    CONSTRAINT ck_user_tasks_type CHECK (
        task_type IN (
            'PHONE_VERIFIED',
            'KYC_COMPLETED',
            'CIRCLE_JOINED',
            'TRAINING_COMPLETED',
            'PROJECT_SUBMITTED',
            'PERSONAL_CONTRIBUTION_PAID'
        )
    ),
    CONSTRAINT ck_user_tasks_status CHECK (status IN ('PENDING', 'COMPLETED', 'REJECTED'))
);

CREATE INDEX idx_user_tasks_user_id ON user_tasks (user_id);
CREATE INDEX idx_user_tasks_user_status ON user_tasks (user_id, status);
