ALTER TABLE prayer_daily_contents
    ADD COLUMN IF NOT EXISTS code VARCHAR(96) NULL,
    ADD COLUMN IF NOT EXISTS short_content VARCHAR(280) NULL,
    ADD COLUMN IF NOT EXISTS declaration TEXT NULL,
    ADD COLUMN IF NOT EXISTS category VARCHAR(64) NULL,
    ADD COLUMN IF NOT EXISTS theme VARCHAR(120) NULL,
    ADD COLUMN IF NOT EXISTS tone VARCHAR(80) NULL,
    ADD COLUMN IF NOT EXISTS audience VARCHAR(160) NULL,
    ADD COLUMN IF NOT EXISTS tags_csv VARCHAR(512) NULL,
    ADD COLUMN IF NOT EXISTS scripture_text VARCHAR(512) NULL,
    ADD COLUMN IF NOT EXISTS language VARCHAR(8) NOT NULL DEFAULT 'fr',
    ADD COLUMN IF NOT EXISTS country_scope VARCHAR(64) NOT NULL DEFAULT 'AFRICA_FR',
    ADD COLUMN IF NOT EXISTS featured BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS priority INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS estimated_read_seconds INTEGER NOT NULL DEFAULT 45,
    ADD COLUMN IF NOT EXISTS audio_status VARCHAR(32) NOT NULL DEFAULT 'NONE';

ALTER TABLE prayer_daily_contents
    ALTER COLUMN created_by_admin_user_id DROP NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_prayer_daily_contents_code
    ON prayer_daily_contents (code)
    WHERE code IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_prayer_daily_contents_status_featured_priority
    ON prayer_daily_contents (status, featured, priority, prayer_date);

ALTER TABLE prayer_daily_contents
    ADD CONSTRAINT ck_prayer_daily_contents_priority_non_negative
        CHECK (priority >= 0),
    ADD CONSTRAINT ck_prayer_daily_contents_estimated_read_seconds_positive
        CHECK (estimated_read_seconds > 0);
