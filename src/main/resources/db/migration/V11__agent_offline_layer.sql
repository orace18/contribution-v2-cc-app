CREATE TABLE agent_profiles (
    id UUID PRIMARY KEY,
    app_user_id UUID NOT NULL,
    agent_type VARCHAR(32) NOT NULL,
    circle_id UUID,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_agent_profiles_app_user FOREIGN KEY (app_user_id) REFERENCES app_users(id),
    CONSTRAINT fk_agent_profiles_circle FOREIGN KEY (circle_id) REFERENCES circles(id),
    CONSTRAINT ck_agent_profiles_type CHECK (agent_type IN ('CIRCLE_LEADER', 'COACH', 'FIELD_AGENT', 'IMF_AGENT')),
    CONSTRAINT ck_agent_profiles_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED'))
);

CREATE INDEX idx_agent_profiles_app_user_id ON agent_profiles (app_user_id);
CREATE INDEX idx_agent_profiles_circle_id ON agent_profiles (circle_id);

CREATE TABLE assisted_sessions (
    id UUID PRIMARY KEY,
    agent_user_id UUID NOT NULL,
    account_holder_id UUID NOT NULL,
    session_type VARCHAR(16) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    CONSTRAINT fk_assisted_sessions_agent_user FOREIGN KEY (agent_user_id) REFERENCES app_users(id),
    CONSTRAINT fk_assisted_sessions_account_holder FOREIGN KEY (account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT ck_assisted_sessions_type CHECK (session_type IN ('ASSISTED', 'DELEGATED', 'OFFLINE'))
);

CREATE INDEX idx_assisted_sessions_agent_user_id ON assisted_sessions (agent_user_id);
CREATE INDEX idx_assisted_sessions_account_holder_id ON assisted_sessions (account_holder_id);

CREATE TABLE offline_transactions (
    id UUID PRIMARY KEY,
    agent_user_id UUID NOT NULL,
    account_holder_id UUID NOT NULL,
    transaction_type VARCHAR(32) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(16) NOT NULL,
    synced_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_offline_transactions_agent_user FOREIGN KEY (agent_user_id) REFERENCES app_users(id),
    CONSTRAINT fk_offline_transactions_account_holder FOREIGN KEY (account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT ck_offline_transactions_type CHECK (transaction_type IN ('CONTRIBUTION', 'LOAN_REPAYMENT', 'LOAN_DISBURSEMENT')),
    CONSTRAINT ck_offline_transactions_status CHECK (status IN ('PENDING_SYNC', 'SYNCED', 'FAILED')),
    CONSTRAINT ck_offline_transactions_amount CHECK (amount > 0)
);

CREATE INDEX idx_offline_transactions_agent_user_status ON offline_transactions (agent_user_id, status);
CREATE INDEX idx_offline_transactions_account_holder_id ON offline_transactions (account_holder_id);

CREATE TABLE agent_permissions (
    id UUID PRIMARY KEY,
    agent_profile_id UUID NOT NULL,
    permission VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_agent_permissions_profile FOREIGN KEY (agent_profile_id) REFERENCES agent_profiles(id),
    CONSTRAINT uk_agent_permissions_profile_permission UNIQUE (agent_profile_id, permission)
);

CREATE INDEX idx_agent_permissions_profile_id ON agent_permissions (agent_profile_id);
