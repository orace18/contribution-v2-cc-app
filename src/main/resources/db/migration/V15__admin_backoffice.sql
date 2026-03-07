CREATE TABLE admin_users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL,
    country_id UUID,
    status VARCHAR(16) NOT NULL,
    two_factor_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    two_factor_secret VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL,
    activated_at TIMESTAMPTZ,
    CONSTRAINT uk_admin_users_email UNIQUE (email),
    CONSTRAINT ck_admin_users_role CHECK (role IN (
        'ROLE_SUPER_ADMIN',
        'ROLE_COUNTRY_ADMIN',
        'ROLE_IMF_ADMIN',
        'ROLE_OPERATIONS',
        'ROLE_SUPPORT'
    )),
    CONSTRAINT ck_admin_users_status CHECK (status IN ('INVITED', 'ACTIVE', 'DISABLED'))
);

CREATE INDEX idx_admin_users_role_status ON admin_users (role, status);
CREATE INDEX idx_admin_users_country ON admin_users (country_id);

CREATE TABLE admin_invitations (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    role VARCHAR(32) NOT NULL,
    country_id UUID,
    invitation_token VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_admin_invitations_token UNIQUE (invitation_token),
    CONSTRAINT ck_admin_invitations_role CHECK (role IN (
        'ROLE_SUPER_ADMIN',
        'ROLE_COUNTRY_ADMIN',
        'ROLE_IMF_ADMIN',
        'ROLE_OPERATIONS',
        'ROLE_SUPPORT'
    )),
    CONSTRAINT ck_admin_invitations_status CHECK (status IN ('PENDING', 'ACCEPTED', 'EXPIRED'))
);

CREATE INDEX idx_admin_invitations_email_status ON admin_invitations (email, status);
CREATE INDEX idx_admin_invitations_expires_at ON admin_invitations (expires_at);

CREATE TABLE admin_audit_logs (
    id UUID PRIMARY KEY,
    admin_user_id UUID NOT NULL,
    action VARCHAR(128) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_id VARCHAR(64),
    payload TEXT,
    event_timestamp TIMESTAMPTZ NOT NULL,
    ip_address VARCHAR(64),
    CONSTRAINT fk_admin_audit_logs_admin_user FOREIGN KEY (admin_user_id) REFERENCES admin_users(id)
);

CREATE INDEX idx_admin_audit_logs_user_timestamp ON admin_audit_logs (admin_user_id, event_timestamp DESC);
CREATE INDEX idx_admin_audit_logs_action ON admin_audit_logs (action);

CREATE TABLE admin_countries (
    id UUID PRIMARY KEY,
    country_code VARCHAR(2) NOT NULL,
    name VARCHAR(120) NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_admin_countries_code UNIQUE (country_code)
);

CREATE TABLE admin_policies (
    id UUID PRIMARY KEY,
    policy_type VARCHAR(64) NOT NULL,
    country_code VARCHAR(2),
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_admin_policies_type CHECK (policy_type IN ('CREDIT_POLICY', 'SEED_CAPITAL_POLICY'))
);

CREATE INDEX idx_admin_policies_type_country ON admin_policies (policy_type, country_code);
