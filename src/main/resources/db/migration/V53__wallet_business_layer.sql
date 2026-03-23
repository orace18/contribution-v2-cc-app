-- ============================================================
-- V53 – Wallet Business Layer
-- ============================================================

-- 1. wallet_type column on existing wallets
ALTER TABLE wallets
    ADD COLUMN IF NOT EXISTS wallet_type VARCHAR(16) NOT NULL DEFAULT 'USER';

ALTER TABLE wallets
    DROP CONSTRAINT IF EXISTS ck_wallets_type;

ALTER TABLE wallets
    ADD CONSTRAINT ck_wallets_type
        CHECK (wallet_type IN ('USER', 'AGENT'));

-- 2. Enforce 1 phone = 1 wallet
ALTER TABLE wallets
    DROP CONSTRAINT IF EXISTS uk_wallets_mobile_money_number;

ALTER TABLE wallets
    ADD CONSTRAINT uk_wallets_mobile_money_number
        UNIQUE (mobile_money_number);

-- 3. Wallet reservations (fund-hold / escrow layer)
CREATE TABLE IF NOT EXISTS wallet_reservations (
    id          UUID          NOT NULL DEFAULT gen_random_uuid(),
    wallet_id   UUID          NOT NULL,
    amount      NUMERIC(19,4) NOT NULL,
    reason      VARCHAR(255)  NOT NULL,
    expiry_date TIMESTAMPTZ,
    status      VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_wallet_reservations       PRIMARY KEY (id),
    CONSTRAINT fk_wallet_reservations_wallet
        FOREIGN KEY (wallet_id) REFERENCES wallets (id),
    CONSTRAINT ck_wallet_reservations_amount_positive
        CHECK (amount > 0),
    CONSTRAINT ck_wallet_reservations_status
        CHECK (status IN ('ACTIVE','RELEASED','EXPIRED','CONSUMED'))
);

CREATE INDEX IF NOT EXISTS idx_wallet_reservations_wallet_id
    ON wallet_reservations (wallet_id);

CREATE INDEX IF NOT EXISTS idx_wallet_reservations_wallet_status
    ON wallet_reservations (wallet_id, status);

CREATE INDEX IF NOT EXISTS idx_wallet_reservations_active_expiry
    ON wallet_reservations (expiry_date)
    WHERE status = 'ACTIVE';

-- 4. Wallet auto-debit rules (rule engine: credit → auto-debit)
CREATE TABLE IF NOT EXISTS wallet_auto_debit_rules (
    id                       UUID          NOT NULL DEFAULT gen_random_uuid(),
    wallet_id                UUID          NOT NULL,
    rule_type                VARCHAR(32)   NOT NULL,
    destination_account_code VARCHAR(64)   NOT NULL,
    amount_or_percent        NUMERIC(19,4) NOT NULL,
    is_percent               BOOLEAN       NOT NULL DEFAULT FALSE,
    priority                 INTEGER       NOT NULL DEFAULT 0,
    enabled                  BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_wallet_auto_debit_rules PRIMARY KEY (id),
    CONSTRAINT fk_wallet_auto_debit_rules_wallet
        FOREIGN KEY (wallet_id) REFERENCES wallets (id),
    CONSTRAINT ck_wallet_auto_debit_rules_amount_positive
        CHECK (amount_or_percent > 0),
    CONSTRAINT ck_wallet_auto_debit_rules_type
        CHECK (rule_type IN ('LOAN_REPAYMENT','COMMISSION_COLLECTION','DEBT_SETTLEMENT'))
);

CREATE INDEX IF NOT EXISTS idx_wallet_auto_debit_rules_wallet_enabled_priority
    ON wallet_auto_debit_rules (wallet_id, enabled, priority);

-- 5. Wallet limit configs (AML – per country + KYC level)
CREATE TABLE IF NOT EXISTS wallet_limit_configs (
    id           UUID          NOT NULL DEFAULT gen_random_uuid(),
    country_code VARCHAR(3)    NOT NULL,
    kyc_level    VARCHAR(16)   NOT NULL,
    max_balance  NUMERIC(19,4) NOT NULL,
    currency     VARCHAR(3)    NOT NULL,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_wallet_limit_configs PRIMARY KEY (id),
    CONSTRAINT uk_wallet_limit_configs_country_kyc_currency
        UNIQUE (country_code, kyc_level, currency),
    CONSTRAINT ck_wallet_limit_configs_max_balance_positive
        CHECK (max_balance > 0),
    CONSTRAINT ck_wallet_limit_configs_kyc_level
        CHECK (kyc_level IN ('NOT_STARTED','PENDING','VERIFIED','REJECTED'))
);

CREATE INDEX IF NOT EXISTS idx_wallet_limit_configs_country_kyc
    ON wallet_limit_configs (country_code, kyc_level);

-- 6. Wallet AML alerts
CREATE TABLE IF NOT EXISTS wallet_aml_alerts (
    id                UUID          NOT NULL DEFAULT gen_random_uuid(),
    wallet_id         UUID          NOT NULL,
    account_holder_id UUID          NOT NULL,
    alert_type        VARCHAR(32)   NOT NULL,
    amount            NUMERIC(19,4) NOT NULL,
    currency          VARCHAR(3)    NOT NULL,
    resolved          BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_wallet_aml_alerts PRIMARY KEY (id),
    CONSTRAINT fk_wallet_aml_alerts_wallet
        FOREIGN KEY (wallet_id) REFERENCES wallets (id),
    CONSTRAINT ck_wallet_aml_alerts_type
        CHECK (alert_type IN ('MAX_BALANCE_EXCEEDED','SUSPICIOUS_PATTERN'))
);

CREATE INDEX IF NOT EXISTS idx_wallet_aml_alerts_wallet_id
    ON wallet_aml_alerts (wallet_id);

CREATE INDEX IF NOT EXISTS idx_wallet_aml_alerts_account_holder
    ON wallet_aml_alerts (account_holder_id);

CREATE INDEX IF NOT EXISTS idx_wallet_aml_alerts_unresolved
    ON wallet_aml_alerts (resolved, created_at)
    WHERE resolved = FALSE;
