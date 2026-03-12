CREATE TABLE IF NOT EXISTS mlm_weekly_bonus_job_runs (
    id UUID PRIMARY KEY,
    week_id INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL,
    members_processed INTEGER NOT NULL DEFAULT 0,
    commissions_created INTEGER NOT NULL DEFAULT 0,
    carryovers_stored INTEGER NOT NULL DEFAULT 0,
    error_message VARCHAR(512),
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_mlm_weekly_bonus_job_runs_week UNIQUE (week_id),
    CONSTRAINT ck_mlm_weekly_bonus_job_runs_status
        CHECK (status IN ('SUCCESS', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_mlm_weekly_bonus_job_runs_week
    ON mlm_weekly_bonus_job_runs (week_id);

CREATE INDEX IF NOT EXISTS idx_mlm_weekly_bonus_job_runs_finished_at
    ON mlm_weekly_bonus_job_runs (finished_at DESC);
