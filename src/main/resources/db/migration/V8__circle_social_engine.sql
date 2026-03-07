ALTER TABLE circle_activity
    ADD COLUMN IF NOT EXISTS reference_id UUID;

UPDATE circle_activity
SET activity_type = 'MEMBER_JOINED'
WHERE activity_type = 'CIRCLE_CREATED';

ALTER TABLE circle_activity
    DROP CONSTRAINT IF EXISTS ck_circle_activity_type;

ALTER TABLE circle_activity
    ADD CONSTRAINT ck_circle_activity_type CHECK (
        activity_type IN (
            'MEMBER_JOINED',
            'PROJECT_SUBMITTED',
            'TRAINING_COMPLETED',
            'CONTRIBUTION_PAID',
            'LOAN_GRANTED',
            'LOAN_REPAID'
        )
    );

CREATE TABLE circle_progress (
    circle_id UUID PRIMARY KEY,
    members_count INTEGER NOT NULL DEFAULT 0,
    members_completed_training INTEGER NOT NULL DEFAULT 0,
    members_submitted_project INTEGER NOT NULL DEFAULT 0,
    members_paid_contribution INTEGER NOT NULL DEFAULT 0,
    progress_percentage NUMERIC(5,2) NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_circle_progress_circle FOREIGN KEY (circle_id) REFERENCES circles(id),
    CONSTRAINT ck_circle_progress_members_count CHECK (members_count >= 0),
    CONSTRAINT ck_circle_progress_training CHECK (members_completed_training >= 0),
    CONSTRAINT ck_circle_progress_project CHECK (members_submitted_project >= 0),
    CONSTRAINT ck_circle_progress_contribution CHECK (members_paid_contribution >= 0),
    CONSTRAINT ck_circle_progress_percentage CHECK (progress_percentage >= 0 AND progress_percentage <= 100)
);

CREATE TABLE circle_scores (
    circle_id UUID PRIMARY KEY,
    activity_score NUMERIC(5,2) NOT NULL DEFAULT 0,
    training_score NUMERIC(5,2) NOT NULL DEFAULT 0,
    repayment_score NUMERIC(5,2) NOT NULL DEFAULT 0,
    global_score NUMERIC(5,2) NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_circle_scores_circle FOREIGN KEY (circle_id) REFERENCES circles(id),
    CONSTRAINT ck_circle_scores_activity CHECK (activity_score >= 0 AND activity_score <= 100),
    CONSTRAINT ck_circle_scores_training CHECK (training_score >= 0 AND training_score <= 100),
    CONSTRAINT ck_circle_scores_repayment CHECK (repayment_score >= 0 AND repayment_score <= 100),
    CONSTRAINT ck_circle_scores_global CHECK (global_score >= 0 AND global_score <= 100)
);
