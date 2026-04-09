CREATE TABLE IF NOT EXISTS imf_savings_channels (
    id UUID PRIMARY KEY,
    account_holder_id UUID NOT NULL,
    partner_id UUID NOT NULL,
    partner_type VARCHAR(16) NOT NULL,
    country_code VARCHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    partner_member_reference VARCHAR(128),
    external_channel_reference VARCHAR(128),
    readiness_note VARCHAR(500),
    configured_at TIMESTAMPTZ NOT NULL,
    activated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_imf_savings_channels_account_holder
        FOREIGN KEY (account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT fk_imf_savings_channels_partner
        FOREIGN KEY (partner_id) REFERENCES financial_partners(id),
    CONSTRAINT uk_imf_savings_channels_account_country
        UNIQUE (account_holder_id, country_code),
    CONSTRAINT ck_imf_savings_channels_partner_type
        CHECK (partner_type IN ('IMF')),
    CONSTRAINT ck_imf_savings_channels_status
        CHECK (status IN ('PENDING_PARTNER_CONFIRMATION', 'ACTIVE', 'SUSPENDED', 'CLOSED')),
    CONSTRAINT ck_imf_savings_channels_country_code
        CHECK (char_length(country_code) BETWEEN 2 AND 3)
);

CREATE INDEX IF NOT EXISTS idx_imf_savings_channels_account_country
    ON imf_savings_channels (account_holder_id, country_code, status);

CREATE TABLE IF NOT EXISTS savings_cycles (
    id UUID PRIMARY KEY,
    circle_id UUID NOT NULL,
    created_by_account_holder_id UUID NOT NULL,
    cycle_number INTEGER,
    window_start TIMESTAMPTZ NOT NULL,
    window_end TIMESTAMPTZ NOT NULL,
    expected_amount_per_member NUMERIC(19,4) NOT NULL,
    country_code VARCHAR(3) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    participant_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL,
    note VARCHAR(500),
    closed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_savings_cycles_circle
        FOREIGN KEY (circle_id) REFERENCES circles(id) ON DELETE CASCADE,
    CONSTRAINT fk_savings_cycles_created_by
        FOREIGN KEY (created_by_account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT ck_savings_cycles_status
        CHECK (status IN ('PLANNED', 'OPEN', 'RECONCILING', 'COMPLETED', 'MISSED', 'CANCELLED')),
    CONSTRAINT ck_savings_cycles_window
        CHECK (window_end > window_start),
    CONSTRAINT ck_savings_cycles_expected_amount
        CHECK (expected_amount_per_member > 0),
    CONSTRAINT ck_savings_cycles_participant_count
        CHECK (participant_count >= 0)
);

CREATE INDEX IF NOT EXISTS idx_savings_cycles_circle_status_window
    ON savings_cycles (circle_id, status, window_start DESC);

CREATE TABLE IF NOT EXISTS member_savings_contributions (
    id UUID PRIMARY KEY,
    cycle_id UUID NOT NULL,
    account_holder_id UUID NOT NULL,
    expected_amount NUMERIC(19,4) NOT NULL,
    confirmed_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    status VARCHAR(24) NOT NULL,
    confirmed_at TIMESTAMPTZ,
    partner_reference VARCHAR(128),
    transaction_reference VARCHAR(128),
    confirmation_source VARCHAR(32),
    confirmation_note VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_member_savings_contributions_cycle
        FOREIGN KEY (cycle_id) REFERENCES savings_cycles(id) ON DELETE CASCADE,
    CONSTRAINT fk_member_savings_contributions_account_holder
        FOREIGN KEY (account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT uk_member_savings_contributions_cycle_member
        UNIQUE (cycle_id, account_holder_id),
    CONSTRAINT ck_member_savings_contributions_expected_amount
        CHECK (expected_amount > 0),
    CONSTRAINT ck_member_savings_contributions_confirmed_amount
        CHECK (confirmed_amount >= 0),
    CONSTRAINT ck_member_savings_contributions_status
        CHECK (status IN ('EXPECTED', 'CONFIRMED', 'PARTIALLY_CONFIRMED', 'MISSED')),
    CONSTRAINT ck_member_savings_contributions_confirmation_source
        CHECK (confirmation_source IS NULL OR confirmation_source IN ('MEMBER_DECLARATION', 'PARTNER_CONFIRMATION', 'BACKOFFICE_OVERRIDE'))
);

CREATE INDEX IF NOT EXISTS idx_member_savings_contributions_cycle_status
    ON member_savings_contributions (cycle_id, status);

CREATE INDEX IF NOT EXISTS idx_member_savings_contributions_member_status
    ON member_savings_contributions (account_holder_id, status);

ALTER TABLE seed_capital_eligibility
    ADD COLUMN IF NOT EXISTS imf_channel_coverage_met BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE seed_capital_eligibility
    ADD COLUMN IF NOT EXISTS savings_discipline_met BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE seed_capital_eligibility
    ADD COLUMN IF NOT EXISTS savings_gate_state VARCHAR(32);

ALTER TABLE seed_capital_eligibility
    ADD COLUMN IF NOT EXISTS configured_imf_channels_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE seed_capital_eligibility
    ADD COLUMN IF NOT EXISTS ready_imf_channels_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE seed_capital_eligibility
    ADD COLUMN IF NOT EXISTS qualifying_savings_cycles_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE seed_capital_eligibility
    ADD COLUMN IF NOT EXISTS required_savings_cycles_count INTEGER NOT NULL DEFAULT 2;

ALTER TABLE seed_capital_eligibility
    ADD CONSTRAINT ck_seed_capital_eligibility_savings_gate_state
        CHECK (savings_gate_state IS NULL OR savings_gate_state IN (
            'CHANNELS_MISSING',
            'CHANNELS_NOT_READY',
            'DISCIPLINE_INSUFFICIENT',
            'READY'
        ));

ALTER TABLE seed_capital_eligibility
    ADD CONSTRAINT ck_seed_capital_eligibility_configured_imf_channels
        CHECK (configured_imf_channels_count >= 0);

ALTER TABLE seed_capital_eligibility
    ADD CONSTRAINT ck_seed_capital_eligibility_ready_imf_channels
        CHECK (ready_imf_channels_count >= 0 AND ready_imf_channels_count <= configured_imf_channels_count);

ALTER TABLE seed_capital_eligibility
    ADD CONSTRAINT ck_seed_capital_eligibility_qualifying_cycles
        CHECK (qualifying_savings_cycles_count >= 0);

ALTER TABLE seed_capital_eligibility
    ADD CONSTRAINT ck_seed_capital_eligibility_required_cycles
        CHECK (required_savings_cycles_count > 0);

ALTER TABLE credit_applications
    ADD COLUMN IF NOT EXISTS savings_gate_state VARCHAR(32);

ALTER TABLE credit_applications
    ADD COLUMN IF NOT EXISTS configured_imf_channels_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE credit_applications
    ADD COLUMN IF NOT EXISTS ready_imf_channels_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE credit_applications
    ADD COLUMN IF NOT EXISTS qualifying_savings_cycles_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE credit_applications
    ADD COLUMN IF NOT EXISTS required_savings_cycles_count INTEGER NOT NULL DEFAULT 2;

ALTER TABLE credit_applications
    ADD CONSTRAINT ck_credit_applications_savings_gate_state
        CHECK (savings_gate_state IS NULL OR savings_gate_state IN (
            'CHANNELS_MISSING',
            'CHANNELS_NOT_READY',
            'DISCIPLINE_INSUFFICIENT',
            'READY'
        ));

ALTER TABLE credit_applications
    ADD CONSTRAINT ck_credit_applications_configured_imf_channels
        CHECK (configured_imf_channels_count >= 0);

ALTER TABLE credit_applications
    ADD CONSTRAINT ck_credit_applications_ready_imf_channels
        CHECK (ready_imf_channels_count >= 0 AND ready_imf_channels_count <= configured_imf_channels_count);

ALTER TABLE credit_applications
    ADD CONSTRAINT ck_credit_applications_qualifying_cycles
        CHECK (qualifying_savings_cycles_count >= 0);

ALTER TABLE credit_applications
    ADD CONSTRAINT ck_credit_applications_required_cycles
        CHECK (required_savings_cycles_count > 0);
