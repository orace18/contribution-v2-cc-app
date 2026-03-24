-- ============================================================
-- V57 - Financial hardening
-- ============================================================

ALTER TABLE wallet_auto_debit_rules
    ADD COLUMN IF NOT EXISTS auto_debit_authorized BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE wallet_otp_challenges
    DROP CONSTRAINT IF EXISTS ck_wallet_otp_challenges_operation_type;

ALTER TABLE wallet_otp_challenges
    ADD CONSTRAINT ck_wallet_otp_challenges_operation_type
        CHECK (operation_type IN (
            'WALLET_PAYMENT',
            'WALLET_DEPOSIT_AGENT',
            'WALLET_WITHDRAWAL',
            'SUBSCRIPTION',
            'MARKETPLACE_PAYMENT',
            'LOAN_REPAYMENT'
        ));
