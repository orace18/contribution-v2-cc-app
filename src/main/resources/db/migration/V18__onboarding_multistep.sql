ALTER TABLE user_profiles
    ALTER COLUMN first_name DROP NOT NULL;

ALTER TABLE user_profiles
    ALTER COLUMN last_name DROP NOT NULL;

ALTER TABLE user_profiles
    ALTER COLUMN country DROP NOT NULL;

ALTER TABLE user_profiles
    ALTER COLUMN phone DROP NOT NULL;

ALTER TABLE user_profiles
    ADD COLUMN IF NOT EXISTS activity_sector_code VARCHAR(64),
    ADD COLUMN IF NOT EXISTS business_experience VARCHAR(32),
    ADD COLUMN IF NOT EXISTS monthly_income_bracket_code VARCHAR(64),
    ADD COLUMN IF NOT EXISTS project_description VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS available_capital NUMERIC(19,4),
    ADD COLUMN IF NOT EXISTS solidarity_guarantee_accepted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS terms_accepted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS terms_accepted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS onboarding_step VARCHAR(16) NOT NULL DEFAULT 'STEP_1',
    ADD COLUMN IF NOT EXISTS onboarding_completed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS onboarding_completed_at TIMESTAMPTZ;

ALTER TABLE user_profiles
    DROP CONSTRAINT IF EXISTS ck_user_profiles_available_capital;

ALTER TABLE user_profiles
    ADD CONSTRAINT ck_user_profiles_available_capital
        CHECK (available_capital IS NULL OR available_capital >= 0);

ALTER TABLE user_profiles
    DROP CONSTRAINT IF EXISTS ck_user_profiles_onboarding_step;

ALTER TABLE user_profiles
    ADD CONSTRAINT ck_user_profiles_onboarding_step
        CHECK (onboarding_step IN ('STEP_1', 'STEP_2', 'STEP_3', 'COMPLETED'));
