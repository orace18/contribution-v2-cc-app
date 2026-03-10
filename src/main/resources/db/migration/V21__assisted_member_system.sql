ALTER TABLE account_holders
    ADD COLUMN IF NOT EXISTS account_type VARCHAR(16),
    ADD COLUMN IF NOT EXISTS created_by_user_id UUID,
    ADD COLUMN IF NOT EXISTS created_via VARCHAR(16);

UPDATE account_holders
SET account_type = 'STANDARD'
WHERE account_type IS NULL;

UPDATE account_holders
SET created_via = 'SELF'
WHERE created_via IS NULL;

ALTER TABLE account_holders
    ALTER COLUMN account_type SET NOT NULL,
    ALTER COLUMN created_via SET NOT NULL;

ALTER TABLE account_holders
    ALTER COLUMN account_type SET DEFAULT 'STANDARD',
    ALTER COLUMN created_via SET DEFAULT 'SELF';

ALTER TABLE account_holders
    DROP CONSTRAINT IF EXISTS ck_account_holders_account_type;
ALTER TABLE account_holders
    ADD CONSTRAINT ck_account_holders_account_type
        CHECK (account_type IN ('STANDARD', 'ASSISTED'));

ALTER TABLE account_holders
    DROP CONSTRAINT IF EXISTS ck_account_holders_created_via;
ALTER TABLE account_holders
    ADD CONSTRAINT ck_account_holders_created_via
        CHECK (created_via IN ('SELF', 'CIRCLE_MEMBER', 'AGENT'));

ALTER TABLE account_holders
    DROP CONSTRAINT IF EXISTS fk_account_holders_created_by_user;
ALTER TABLE account_holders
    ADD CONSTRAINT fk_account_holders_created_by_user
        FOREIGN KEY (created_by_user_id) REFERENCES app_users(id);

CREATE INDEX IF NOT EXISTS idx_account_holders_account_type ON account_holders (account_type);
CREATE INDEX IF NOT EXISTS idx_account_holders_created_by_user_id ON account_holders (created_by_user_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_account_holders_phone_number
    ON account_holders (phone_number)
    WHERE phone_number IS NOT NULL;

ALTER TABLE account_delegations
    ADD COLUMN IF NOT EXISTS role VARCHAR(16),
    ADD COLUMN IF NOT EXISTS status VARCHAR(16);

UPDATE account_delegations
SET role = 'MANAGER'
WHERE role IS NULL;

UPDATE account_delegations
SET status = 'ACTIVE'
WHERE status IS NULL;

ALTER TABLE account_delegations
    ALTER COLUMN role SET NOT NULL,
    ALTER COLUMN status SET NOT NULL;

ALTER TABLE account_delegations
    ALTER COLUMN role SET DEFAULT 'MANAGER',
    ALTER COLUMN status SET DEFAULT 'ACTIVE';

ALTER TABLE account_delegations
    DROP CONSTRAINT IF EXISTS ck_account_delegations_role;
ALTER TABLE account_delegations
    ADD CONSTRAINT ck_account_delegations_role
        CHECK (role IN ('MANAGER'));

ALTER TABLE account_delegations
    DROP CONSTRAINT IF EXISTS ck_account_delegations_status;
ALTER TABLE account_delegations
    ADD CONSTRAINT ck_account_delegations_status
        CHECK (status IN ('ACTIVE', 'REVOKED'));

CREATE INDEX IF NOT EXISTS idx_account_delegations_account_holder_status
    ON account_delegations (account_holder_id, status);

CREATE TABLE IF NOT EXISTS delegated_actions (
    id UUID PRIMARY KEY,
    account_holder_id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    action_type VARCHAR(32) NOT NULL,
    payload TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_delegated_actions_account_holder FOREIGN KEY (account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT fk_delegated_actions_actor_user FOREIGN KEY (actor_user_id) REFERENCES app_users(id),
    CONSTRAINT ck_delegated_actions_type CHECK (action_type IN (
        'PROFILE_UPDATED',
        'PROJECT_UPDATED',
        'REPAYMENT_RECORDED',
        'PROGRESS_UPDATED',
        'CIRCLE_ACTIVITY',
        'KYC_SUBMITTED'
    ))
);

CREATE INDEX IF NOT EXISTS idx_delegated_actions_account_holder_created_at
    ON delegated_actions (account_holder_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_delegated_actions_actor_user_created_at
    ON delegated_actions (actor_user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS assisted_member_verifications (
    id UUID PRIMARY KEY,
    account_holder_id UUID NOT NULL,
    verified_by UUID NOT NULL,
    verification_type VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_assisted_member_verifications_account_holder FOREIGN KEY (account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT ck_assisted_member_verification_type CHECK (verification_type IN ('COACH', 'AGENT', 'ADMIN'))
);

CREATE INDEX IF NOT EXISTS idx_assisted_member_verifications_account_holder_created_at
    ON assisted_member_verifications (account_holder_id, created_at DESC);
