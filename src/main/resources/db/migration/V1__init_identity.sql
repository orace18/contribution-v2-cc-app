CREATE TABLE users (
    id UUID PRIMARY KEY,
    firebase_uid VARCHAR(128) NOT NULL,
    email VARCHAR(320),
    role VARCHAR(16) NOT NULL,
    account_status VARCHAR(16) NOT NULL,
    kyc_status VARCHAR(16) NOT NULL,
    blocked_reason VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_users_firebase_uid UNIQUE (firebase_uid),
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE INDEX idx_users_firebase_uid ON users (firebase_uid);

CREATE TABLE user_profiles (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    first_name VARCHAR(120) NOT NULL,
    last_name VARCHAR(120) NOT NULL,
    country VARCHAR(2) NOT NULL,
    phone VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_user_profiles_user_id UNIQUE (user_id),
    CONSTRAINT fk_user_profiles_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE user_devices (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    device_identifier VARCHAR(128) NOT NULL,
    platform VARCHAR(16) NOT NULL,
    blocked BOOLEAN NOT NULL DEFAULT FALSE,
    blocked_at TIMESTAMPTZ,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    last_seen_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_user_device_identifier UNIQUE (user_id, device_identifier),
    CONSTRAINT fk_user_devices_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_user_devices_user_id ON user_devices (user_id);

CREATE TABLE user_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    device_id UUID NOT NULL,
    refresh_token_hash VARCHAR(128) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_user_sessions_refresh_hash UNIQUE (refresh_token_hash),
    CONSTRAINT fk_user_sessions_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_user_sessions_device FOREIGN KEY (device_id) REFERENCES user_devices(id)
);

CREATE INDEX idx_user_sessions_user_id ON user_sessions (user_id);
CREATE INDEX idx_user_sessions_device_id ON user_sessions (device_id);
