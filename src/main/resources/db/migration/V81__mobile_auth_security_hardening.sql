ALTER TABLE user_devices
    ADD COLUMN IF NOT EXISTS first_seen_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_ip_address VARCHAR(64),
    ADD COLUMN IF NOT EXISTS trusted_device BOOLEAN;

UPDATE user_devices
SET first_seen_at = COALESCE(first_seen_at, created_at),
    last_login_at = COALESCE(last_login_at, last_seen_at, updated_at, created_at),
    trusted_device = COALESCE(trusted_device, TRUE);

ALTER TABLE user_devices
    ALTER COLUMN trusted_device SET NOT NULL;

ALTER TABLE user_devices
    ALTER COLUMN trusted_device SET DEFAULT FALSE;

ALTER TABLE user_sessions
    ADD COLUMN IF NOT EXISTS ip_address VARCHAR(64),
    ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS risk_level VARCHAR(16),
    ADD COLUMN IF NOT EXISTS country_code VARCHAR(8);

UPDATE user_sessions
SET last_seen_at = COALESCE(last_seen_at, created_at),
    risk_level = COALESCE(risk_level, 'LOW');

ALTER TABLE user_sessions
    DROP CONSTRAINT IF EXISTS ck_user_sessions_risk_level;

ALTER TABLE user_sessions
    ADD CONSTRAINT ck_user_sessions_risk_level CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH'));

ALTER TABLE user_sessions
    ALTER COLUMN risk_level SET NOT NULL;

ALTER TABLE user_sessions
    ALTER COLUMN risk_level SET DEFAULT 'LOW';

CREATE TABLE IF NOT EXISTS auth_token_usage (
    id UUID PRIMARY KEY,
    token_hash VARCHAR(128) NOT NULL,
    user_id UUID,
    used_at TIMESTAMPTZ NOT NULL,
    device_identifier VARCHAR(128)
);

CREATE INDEX IF NOT EXISTS idx_auth_token_usage_hash_used_at
    ON auth_token_usage (token_hash, used_at DESC);
CREATE INDEX IF NOT EXISTS idx_auth_token_usage_user_id
    ON auth_token_usage (user_id);

CREATE TABLE IF NOT EXISTS security_events (
    id UUID PRIMARY KEY,
    user_id UUID,
    event_type VARCHAR(32) NOT NULL,
    device_identifier VARCHAR(128),
    ip_address VARCHAR(64),
    risk_level VARCHAR(16) NOT NULL,
    country_code VARCHAR(8),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_security_events_type CHECK (
        event_type IN ('NEW_DEVICE_LOGIN', 'SUSPICIOUS_LOCATION', 'TOKEN_REPLAY', 'EXCESSIVE_LOGIN_ATTEMPTS')
    ),
    CONSTRAINT ck_security_events_risk CHECK (
        risk_level IN ('LOW', 'MEDIUM', 'HIGH')
    )
);

CREATE INDEX IF NOT EXISTS idx_security_events_user_id_created_at
    ON security_events (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_security_events_event_type_created_at
    ON security_events (event_type, created_at DESC);

CREATE TABLE IF NOT EXISTS login_attempts (
    id UUID PRIMARY KEY,
    user_id UUID,
    device_identifier VARCHAR(128) NOT NULL,
    ip_address VARCHAR(64) NOT NULL,
    succeeded BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_login_attempts_ip_created_at
    ON login_attempts (ip_address, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_login_attempts_device_created_at
    ON login_attempts (device_identifier, created_at DESC);

ALTER TABLE account_holders
    ADD COLUMN IF NOT EXISTS phone_verified_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS phone_verification_source VARCHAR(32),
    ADD COLUMN IF NOT EXISTS phone_verification_ip VARCHAR(64),
    ADD COLUMN IF NOT EXISTS phone_verification_device VARCHAR(128);

ALTER TABLE account_holders
    DROP CONSTRAINT IF EXISTS ck_account_holders_phone_verification_source;

ALTER TABLE account_holders
    ADD CONSTRAINT ck_account_holders_phone_verification_source CHECK (
        phone_verification_source IS NULL
        OR phone_verification_source IN ('FIREBASE', 'INTERNAL_OTP')
    );
