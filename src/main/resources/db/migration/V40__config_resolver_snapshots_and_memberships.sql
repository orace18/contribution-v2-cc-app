ALTER TABLE loan_applications
    ADD COLUMN IF NOT EXISTS config_snapshot TEXT;

ALTER TABLE loan_applications
    ADD COLUMN IF NOT EXISTS config_version INTEGER;

ALTER TABLE circles
    ADD COLUMN IF NOT EXISTS config_snapshot TEXT;

ALTER TABLE circles
    ADD COLUMN IF NOT EXISTS config_version INTEGER;

CREATE TABLE IF NOT EXISTS memberships (
    id UUID PRIMARY KEY,
    operation_id UUID NOT NULL,
    account_holder_id UUID NOT NULL,
    plan_code VARCHAR(64) NOT NULL,
    country_code VARCHAR(3) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    status VARCHAR(16) NOT NULL,
    activated_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    config_snapshot TEXT,
    config_version INTEGER,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_memberships_operation_id UNIQUE (operation_id),
    CONSTRAINT fk_memberships_operation
        FOREIGN KEY (operation_id) REFERENCES operations(id),
    CONSTRAINT fk_memberships_account_holder
        FOREIGN KEY (account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT fk_memberships_country
        FOREIGN KEY (country_code) REFERENCES countries(code),
    CONSTRAINT ck_memberships_status
        CHECK (status IN ('PENDING', 'ACTIVE', 'EXPIRED', 'CANCELED'))
);

CREATE INDEX IF NOT EXISTS idx_memberships_account_holder_status
    ON memberships (account_holder_id, status);

CREATE INDEX IF NOT EXISTS idx_memberships_country_status
    ON memberships (country_code, status);
