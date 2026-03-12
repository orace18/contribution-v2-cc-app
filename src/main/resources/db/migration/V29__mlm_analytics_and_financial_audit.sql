CREATE TABLE IF NOT EXISTS mlm_member_metrics (
    account_holder_id UUID PRIMARY KEY,
    team_size BIGINT NOT NULL DEFAULT 0,
    active_team_members BIGINT NOT NULL DEFAULT 0,
    total_volume NUMERIC(19,4) NOT NULL DEFAULT 0,
    total_commissions NUMERIC(19,4) NOT NULL DEFAULT 0,
    last_updated TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_mlm_member_metrics_account_holder
        FOREIGN KEY (account_holder_id) REFERENCES account_holders(id)
);

CREATE INDEX IF NOT EXISTS idx_mlm_member_metrics_last_updated
    ON mlm_member_metrics (last_updated);

CREATE TABLE IF NOT EXISTS mlm_weekly_snapshots (
    week_id INTEGER NOT NULL,
    account_holder_id UUID NOT NULL,
    team_volume NUMERIC(19,4) NOT NULL DEFAULT 0,
    team_members BIGINT NOT NULL DEFAULT 0,
    commissions_generated NUMERIC(19,4) NOT NULL DEFAULT 0,
    commissions_paid NUMERIC(19,4) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_mlm_weekly_snapshots PRIMARY KEY (week_id, account_holder_id),
    CONSTRAINT fk_mlm_weekly_snapshots_account_holder
        FOREIGN KEY (account_holder_id) REFERENCES account_holders(id)
);

CREATE INDEX IF NOT EXISTS idx_mlm_weekly_snapshots_week
    ON mlm_weekly_snapshots (week_id);

CREATE INDEX IF NOT EXISTS idx_mlm_weekly_snapshots_account_holder
    ON mlm_weekly_snapshots (account_holder_id);

CREATE TABLE IF NOT EXISTS financial_audit_log (
    id UUID PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    reference_id VARCHAR(128),
    account_holder_id UUID,
    amount NUMERIC(19,4),
    created_at TIMESTAMPTZ NOT NULL,
    metadata_json TEXT NOT NULL,
    CONSTRAINT fk_financial_audit_log_account_holder
        FOREIGN KEY (account_holder_id) REFERENCES account_holders(id)
);

CREATE INDEX IF NOT EXISTS idx_financial_audit_log_event_type
    ON financial_audit_log (event_type);

CREATE INDEX IF NOT EXISTS idx_financial_audit_log_created_at
    ON financial_audit_log (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_financial_audit_log_account_holder
    ON financial_audit_log (account_holder_id);
