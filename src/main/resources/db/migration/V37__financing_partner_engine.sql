CREATE TABLE IF NOT EXISTS financial_partners (
    id UUID PRIMARY KEY,
    name VARCHAR(180) NOT NULL,
    type VARCHAR(16) NOT NULL,
    country_code VARCHAR(3) NOT NULL,
    status VARCHAR(16) NOT NULL,
    api_endpoint VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_financial_partners_country
        FOREIGN KEY (country_code) REFERENCES countries(code),
    CONSTRAINT ck_financial_partners_type
        CHECK (type IN ('IMF', 'BANK', 'FUND')),
    CONSTRAINT ck_financial_partners_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX IF NOT EXISTS idx_financial_partners_country_status
    ON financial_partners (country_code, status);

CREATE TABLE IF NOT EXISTS financing_offers (
    id UUID PRIMARY KEY,
    partner_id UUID NOT NULL,
    name VARCHAR(180) NOT NULL,
    description VARCHAR(2000),
    loan_type VARCHAR(64) NOT NULL,
    min_amount NUMERIC(19,4) NOT NULL,
    max_amount NUMERIC(19,4) NOT NULL,
    interest_rate NUMERIC(5,2) NOT NULL,
    duration_months INTEGER NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_financing_offers_partner
        FOREIGN KEY (partner_id) REFERENCES financial_partners(id),
    CONSTRAINT ck_financing_offers_amounts
        CHECK (min_amount > 0 AND max_amount > 0 AND max_amount >= min_amount),
    CONSTRAINT ck_financing_offers_interest
        CHECK (interest_rate >= 0),
    CONSTRAINT ck_financing_offers_duration
        CHECK (duration_months > 0),
    CONSTRAINT ck_financing_offers_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX IF NOT EXISTS idx_financing_offers_partner_status
    ON financing_offers (partner_id, status);

CREATE TABLE IF NOT EXISTS offer_country_config (
    id UUID PRIMARY KEY,
    offer_id UUID NOT NULL,
    country_code VARCHAR(3) NOT NULL,
    enabled BOOLEAN NOT NULL,
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_offer_country_config_offer
        FOREIGN KEY (offer_id) REFERENCES financing_offers(id) ON DELETE CASCADE,
    CONSTRAINT fk_offer_country_config_country
        FOREIGN KEY (country_code) REFERENCES countries(code),
    CONSTRAINT uk_offer_country_config_offer_country
        UNIQUE (offer_id, country_code)
);

CREATE INDEX IF NOT EXISTS idx_offer_country_config_country_enabled
    ON offer_country_config (country_code, enabled);

CREATE TABLE IF NOT EXISTS offer_requirements (
    id UUID PRIMARY KEY,
    offer_id UUID NOT NULL,
    requirement_type VARCHAR(32) NOT NULL,
    required BOOLEAN NOT NULL,
    verification_type VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_offer_requirements_offer
        FOREIGN KEY (offer_id) REFERENCES financing_offers(id) ON DELETE CASCADE,
    CONSTRAINT uk_offer_requirements_offer_type
        UNIQUE (offer_id, requirement_type),
    CONSTRAINT ck_offer_requirements_type
        CHECK (requirement_type IN ('KYC_DOCUMENT', 'BUSINESS_PROOF', 'BANK_STATEMENT', 'RMS_SCORE', 'CIRCLE_HISTORY')),
    CONSTRAINT ck_offer_requirements_verification_type
        CHECK (verification_type IN ('MANUAL', 'DOCUMENT_UPLOAD', 'SCORE_CHECK', 'HISTORY_CHECK'))
);

CREATE INDEX IF NOT EXISTS idx_offer_requirements_offer_required
    ON offer_requirements (offer_id, required);

CREATE TABLE IF NOT EXISTS loan_applications (
    id UUID PRIMARY KEY,
    offer_id UUID NOT NULL,
    account_holder_id UUID NOT NULL,
    circle_id UUID,
    requested_amount NUMERIC(19,4) NOT NULL,
    approved_amount NUMERIC(19,4),
    status VARCHAR(16) NOT NULL,
    partner_reference VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_loan_applications_offer
        FOREIGN KEY (offer_id) REFERENCES financing_offers(id),
    CONSTRAINT fk_loan_applications_account_holder
        FOREIGN KEY (account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT fk_loan_applications_circle
        FOREIGN KEY (circle_id) REFERENCES circles(id),
    CONSTRAINT ck_loan_applications_amount
        CHECK (requested_amount > 0 AND (approved_amount IS NULL OR approved_amount > 0)),
    CONSTRAINT ck_loan_applications_status
        CHECK (status IN ('DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'APPROVED', 'REJECTED', 'DISBURSED', 'REPAID'))
);

CREATE INDEX IF NOT EXISTS idx_loan_applications_account_holder_status
    ON loan_applications (account_holder_id, status);

CREATE INDEX IF NOT EXISTS idx_loan_applications_offer_status
    ON loan_applications (offer_id, status);

CREATE TABLE IF NOT EXISTS partner_ledger_accounts (
    id UUID PRIMARY KEY,
    partner_id UUID NOT NULL,
    balance NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_partner_ledger_accounts_partner
        FOREIGN KEY (partner_id) REFERENCES financial_partners(id),
    CONSTRAINT uk_partner_ledger_accounts_partner_currency
        UNIQUE (partner_id, currency)
);

CREATE TABLE IF NOT EXISTS partner_ledger_entries (
    id UUID PRIMARY KEY,
    partner_id UUID NOT NULL,
    partner_ledger_account_id UUID NOT NULL,
    application_id UUID,
    entry_type VARCHAR(32) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_partner_ledger_entries_partner
        FOREIGN KEY (partner_id) REFERENCES financial_partners(id),
    CONSTRAINT fk_partner_ledger_entries_account
        FOREIGN KEY (partner_ledger_account_id) REFERENCES partner_ledger_accounts(id),
    CONSTRAINT fk_partner_ledger_entries_application
        FOREIGN KEY (application_id) REFERENCES loan_applications(id),
    CONSTRAINT ck_partner_ledger_entries_type
        CHECK (entry_type IN ('APPLICATION_SUBMITTED', 'APPLICATION_APPROVED', 'DISBURSEMENT', 'REPAYMENT', 'SETTLEMENT')),
    CONSTRAINT ck_partner_ledger_entries_amount
        CHECK (amount > 0)
);

CREATE INDEX IF NOT EXISTS idx_partner_ledger_entries_partner_created
    ON partner_ledger_entries (partner_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_partner_ledger_entries_application
    ON partner_ledger_entries (application_id);

CREATE TABLE IF NOT EXISTS partner_settlement_batches (
    id UUID PRIMARY KEY,
    partner_id UUID NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_partner_settlement_batches_partner
        FOREIGN KEY (partner_id) REFERENCES financial_partners(id),
    CONSTRAINT ck_partner_settlement_batches_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'SETTLED', 'FAILED')),
    CONSTRAINT ck_partner_settlement_batches_amount
        CHECK (amount > 0)
);

CREATE INDEX IF NOT EXISTS idx_partner_settlement_batches_partner_status
    ON partner_settlement_batches (partner_id, status);

CREATE TABLE IF NOT EXISTS partner_settlement_items (
    id UUID PRIMARY KEY,
    batch_id UUID NOT NULL,
    application_id UUID NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_partner_settlement_items_batch
        FOREIGN KEY (batch_id) REFERENCES partner_settlement_batches(id) ON DELETE CASCADE,
    CONSTRAINT fk_partner_settlement_items_application
        FOREIGN KEY (application_id) REFERENCES loan_applications(id),
    CONSTRAINT uk_partner_settlement_items_batch_application
        UNIQUE (batch_id, application_id),
    CONSTRAINT ck_partner_settlement_items_status
        CHECK (status IN ('PENDING', 'SETTLED', 'FAILED')),
    CONSTRAINT ck_partner_settlement_items_amount
        CHECK (amount > 0)
);

CREATE INDEX IF NOT EXISTS idx_partner_settlement_items_batch_status
    ON partner_settlement_items (batch_id, status);

CREATE TABLE IF NOT EXISTS partner_users (
    id UUID PRIMARY KEY,
    partner_id UUID NOT NULL,
    account_holder_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_partner_users_partner
        FOREIGN KEY (partner_id) REFERENCES financial_partners(id),
    CONSTRAINT fk_partner_users_account_holder
        FOREIGN KEY (account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT uk_partner_users_partner_account_holder
        UNIQUE (partner_id, account_holder_id),
    CONSTRAINT ck_partner_users_status
        CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE INDEX IF NOT EXISTS idx_partner_users_account_holder
    ON partner_users (account_holder_id);

CREATE TABLE IF NOT EXISTS partner_roles (
    id UUID PRIMARY KEY,
    partner_user_id UUID NOT NULL,
    role VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_partner_roles_user
        FOREIGN KEY (partner_user_id) REFERENCES partner_users(id) ON DELETE CASCADE,
    CONSTRAINT uk_partner_roles_user_role
        UNIQUE (partner_user_id, role),
    CONSTRAINT ck_partner_roles_role
        CHECK (role IN ('PARTNER_ADMIN', 'PARTNER_ANALYST', 'PARTNER_VIEWER'))
);

CREATE TABLE IF NOT EXISTS partner_reports (
    id UUID PRIMARY KEY,
    partner_id UUID NOT NULL,
    report_type VARCHAR(32) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    loan_volume NUMERIC(19,4) NOT NULL,
    repayment_rate NUMERIC(5,2) NOT NULL,
    defaults BIGINT NOT NULL,
    portfolio_value NUMERIC(19,4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_partner_reports_partner
        FOREIGN KEY (partner_id) REFERENCES financial_partners(id),
    CONSTRAINT ck_partner_reports_period
        CHECK (period_end >= period_start),
    CONSTRAINT ck_partner_reports_values
        CHECK (loan_volume >= 0 AND repayment_rate >= 0 AND portfolio_value >= 0 AND defaults >= 0)
);

CREATE INDEX IF NOT EXISTS idx_partner_reports_partner_created
    ON partner_reports (partner_id, created_at DESC);

CREATE TABLE IF NOT EXISTS loan_portfolio_snapshots (
    id UUID PRIMARY KEY,
    partner_id UUID NOT NULL,
    snapshot_date DATE NOT NULL,
    loan_volume NUMERIC(19,4) NOT NULL,
    repayment_rate NUMERIC(5,2) NOT NULL,
    defaults BIGINT NOT NULL,
    portfolio_value NUMERIC(19,4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_loan_portfolio_snapshots_partner
        FOREIGN KEY (partner_id) REFERENCES financial_partners(id),
    CONSTRAINT uk_loan_portfolio_snapshots_partner_date
        UNIQUE (partner_id, snapshot_date),
    CONSTRAINT ck_loan_portfolio_snapshots_values
        CHECK (loan_volume >= 0 AND repayment_rate >= 0 AND portfolio_value >= 0 AND defaults >= 0)
);

CREATE INDEX IF NOT EXISTS idx_chat_messages_conversation_created
    ON chat_messages (conversation_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_circle_members_circle_id
    ON circle_members (circle_id);

CREATE INDEX IF NOT EXISTS idx_circle_members_account_holder_id
    ON circle_members (account_holder_id);
