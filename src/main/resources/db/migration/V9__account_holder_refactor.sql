CREATE TABLE account_holders (
    id UUID PRIMARY KEY,
    first_name VARCHAR(120) NOT NULL,
    last_name VARCHAR(120) NOT NULL,
    phone_number VARCHAR(32),
    kyc_status VARCHAR(16) NOT NULL,
    kyc_verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_account_holders_kyc_status CHECK (kyc_status IN ('NOT_STARTED', 'PENDING', 'VERIFIED', 'REJECTED'))
);

CREATE TABLE wallets (
    id UUID PRIMARY KEY,
    account_holder_id UUID NOT NULL,
    provider VARCHAR(64) NOT NULL,
    mobile_money_number VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_wallets_account_holder FOREIGN KEY (account_holder_id) REFERENCES account_holders(id)
);

CREATE INDEX idx_wallets_account_holder_id ON wallets (account_holder_id);

CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    firebase_uid VARCHAR(128) NOT NULL,
    account_holder_id UUID NOT NULL,
    role VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_app_users_firebase_uid UNIQUE (firebase_uid),
    CONSTRAINT fk_app_users_account_holder FOREIGN KEY (account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT ck_app_users_role CHECK (role IN ('USER', 'ADMIN'))
);

CREATE INDEX idx_app_users_account_holder_id ON app_users (account_holder_id);

CREATE TABLE account_delegations (
    id UUID PRIMARY KEY,
    account_holder_id UUID NOT NULL,
    delegate_user_id UUID NOT NULL,
    permission_level VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ,
    CONSTRAINT fk_account_delegations_account_holder FOREIGN KEY (account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT fk_account_delegations_delegate_user FOREIGN KEY (delegate_user_id) REFERENCES app_users(id),
    CONSTRAINT ck_account_delegations_permission CHECK (permission_level IN ('VIEW', 'TRANSACT', 'FULL'))
);

CREATE INDEX idx_account_delegations_account_holder_id ON account_delegations (account_holder_id);
CREATE INDEX idx_account_delegations_delegate_user_id ON account_delegations (delegate_user_id);

-- Backfill from legacy model
INSERT INTO account_holders (id, first_name, last_name, phone_number, kyc_status, kyc_verified_at, created_at)
SELECT u.id,
       COALESCE(up.first_name, 'Unknown'),
       COALESCE(up.last_name, 'Unknown'),
       up.phone,
       u.kyc_status,
       CASE WHEN u.kyc_status = 'VERIFIED' THEN NOW() ELSE NULL END,
       u.created_at
FROM users u
LEFT JOIN user_profiles up ON up.user_id = u.id
ON CONFLICT (id) DO NOTHING;

INSERT INTO app_users (id, firebase_uid, account_holder_id, role, created_at)
SELECT u.id, u.firebase_uid, u.id, u.role, u.created_at
FROM users u
ON CONFLICT (id) DO NOTHING;

-- Trace actor app user in operations (backward compatible)
ALTER TABLE operations ADD COLUMN IF NOT EXISTS actor_user_id UUID;
UPDATE operations SET actor_user_id = user_id WHERE actor_user_id IS NULL;
ALTER TABLE operations ALTER COLUMN actor_user_id SET NOT NULL;
ALTER TABLE operations DROP CONSTRAINT IF EXISTS fk_operations_actor_user;
ALTER TABLE operations
    ADD CONSTRAINT fk_operations_actor_user FOREIGN KEY (actor_user_id) REFERENCES app_users(id);
CREATE INDEX IF NOT EXISTS idx_operations_actor_user_id ON operations (actor_user_id);
