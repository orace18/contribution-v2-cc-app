CREATE TABLE IF NOT EXISTS credit_applications (
    id UUID PRIMARY KEY,
    account_holder_id UUID NOT NULL,
    circle_id UUID NOT NULL,
    product_type VARCHAR(32) NOT NULL,
    bridge_state VARCHAR(48) NOT NULL,
    workflow_state VARCHAR(32) NOT NULL,
    requested_amount NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    requested_duration_months INTEGER,
    requested_interest_rate NUMERIC(5,2),
    readiness_state VARCHAR(32) NOT NULL,
    readiness_trust_score NUMERIC(5,2) NOT NULL DEFAULT 0,
    credit_eligibility_score NUMERIC(5,2) NOT NULL DEFAULT 0,
    max_eligible_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    blocking_reasons TEXT,
    pending_criteria TEXT,
    linked_loan_id UUID,
    decision_note VARCHAR(1000),
    submitted_at TIMESTAMPTZ NOT NULL,
    bridge_evaluated_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_credit_applications_account_holder
        FOREIGN KEY (account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT fk_credit_applications_circle
        FOREIGN KEY (circle_id) REFERENCES circles(id),
    CONSTRAINT fk_credit_applications_linked_loan
        FOREIGN KEY (linked_loan_id) REFERENCES loans(id),
    CONSTRAINT ck_credit_applications_product_type
        CHECK (product_type IN ('SEED_CAPITAL')),
    CONSTRAINT ck_credit_applications_bridge_state
        CHECK (bridge_state IN (
            'BLOCKED_BY_MEMBER_ACCESS',
            'BLOCKED_BY_CIRCLE_READINESS',
            'WAITING_FUTURE_GATES',
            'BLOCKED_BY_CREDIT_SCORING',
            'CANDIDATE_FOR_CREDIT_APPLICATION'
        )),
    CONSTRAINT ck_credit_applications_workflow_state
        CHECK (workflow_state IN (
            'SUBMITTED',
            'UNDER_REVIEW',
            'APPROVED',
            'REJECTED',
            'WAITING_FUTURE_GATES',
            'BLOCKED',
            'CONVERTED_TO_LOAN',
            'CANCELLED'
        )),
    CONSTRAINT ck_credit_applications_readiness_state
        CHECK (readiness_state IN ('BLOCKED', 'READY_FOR_FUTURE_GATES', 'ELIGIBLE')),
    CONSTRAINT ck_credit_applications_requested_amount
        CHECK (requested_amount > 0),
    CONSTRAINT ck_credit_applications_currency
        CHECK (char_length(currency) = 3),
    CONSTRAINT ck_credit_applications_requested_duration
        CHECK (requested_duration_months IS NULL OR requested_duration_months > 0),
    CONSTRAINT ck_credit_applications_requested_interest
        CHECK (requested_interest_rate IS NULL OR requested_interest_rate >= 0),
    CONSTRAINT ck_credit_applications_readiness_trust
        CHECK (readiness_trust_score >= 0 AND readiness_trust_score <= 100),
    CONSTRAINT ck_credit_applications_credit_score
        CHECK (credit_eligibility_score >= 0 AND credit_eligibility_score <= 100),
    CONSTRAINT ck_credit_applications_max_eligible_amount
        CHECK (max_eligible_amount >= 0)
);

CREATE INDEX IF NOT EXISTS idx_credit_applications_account_holder_workflow
    ON credit_applications (account_holder_id, workflow_state, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_credit_applications_circle_workflow
    ON credit_applications (circle_id, workflow_state, created_at DESC);
