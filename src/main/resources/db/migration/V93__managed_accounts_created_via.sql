ALTER TABLE account_holders
    DROP CONSTRAINT IF EXISTS ck_account_holders_created_via;

ALTER TABLE account_holders
    ADD CONSTRAINT ck_account_holders_created_via
        CHECK (created_via IN ('SELF', 'CIRCLE_MEMBER', 'AGENT', 'MANAGED_ACCOUNT'));
