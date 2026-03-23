-- ============================================================
-- V56 - Wallet Debit Security
-- ============================================================

ALTER TABLE wallets
    ADD COLUMN IF NOT EXISTS pin_hash VARCHAR(255);

ALTER TABLE wallets
    ADD COLUMN IF NOT EXISTS pin_failed_attempts INTEGER NOT NULL DEFAULT 0;

ALTER TABLE wallets
    ADD COLUMN IF NOT EXISTS locked_at TIMESTAMPTZ;

ALTER TABLE wallets
    ADD COLUMN IF NOT EXISTS last_pin_changed_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS wallet_otp_challenges (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    wallet_id        UUID         NOT NULL,
    account_holder_id UUID        NOT NULL,
    operation_id     UUID         NOT NULL,
    operation_type   VARCHAR(32)  NOT NULL,
    fingerprint_hash VARCHAR(128) NOT NULL,
    otp_hash         VARCHAR(128) NOT NULL,
    phone_number     VARCHAR(32)  NOT NULL,
    status           VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    attempt_count    INTEGER      NOT NULL DEFAULT 0,
    max_attempts     INTEGER      NOT NULL,
    sent_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at       TIMESTAMPTZ  NOT NULL,
    used_at          TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_wallet_otp_challenges PRIMARY KEY (id),
    CONSTRAINT fk_wallet_otp_challenges_wallet
        FOREIGN KEY (wallet_id) REFERENCES wallets (id),
    CONSTRAINT fk_wallet_otp_challenges_operation
        FOREIGN KEY (operation_id) REFERENCES operations (id),
    CONSTRAINT ck_wallet_otp_challenges_status
        CHECK (status IN ('PENDING', 'VERIFIED', 'FAILED', 'EXPIRED')),
    CONSTRAINT ck_wallet_otp_challenges_operation_type
        CHECK (operation_type IN ('WALLET_PAYMENT', 'WALLET_DEPOSIT_AGENT', 'WALLET_WITHDRAWAL')),
    CONSTRAINT ck_wallet_otp_challenges_attempt_count
        CHECK (attempt_count >= 0),
    CONSTRAINT ck_wallet_otp_challenges_max_attempts
        CHECK (max_attempts > 0)
);

CREATE INDEX IF NOT EXISTS idx_wallet_otp_challenges_wallet_status
    ON wallet_otp_challenges (wallet_id, status);

CREATE INDEX IF NOT EXISTS idx_wallet_otp_challenges_operation_status
    ON wallet_otp_challenges (operation_id, status);
