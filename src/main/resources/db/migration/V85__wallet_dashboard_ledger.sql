-- ============================================================
-- V85 - Wallet dashboard ledger projection
-- ============================================================

CREATE TABLE IF NOT EXISTS wallet_ledger (
    id          UUID          NOT NULL DEFAULT gen_random_uuid(),
    wallet_id   UUID          NOT NULL,
    operation_id UUID         NOT NULL,
    entry_type  VARCHAR(16)   NOT NULL,
    category    VARCHAR(32)   NOT NULL,
    status      VARCHAR(16)   NOT NULL,
    amount      NUMERIC(19,4) NOT NULL,
    currency    VARCHAR(3)    NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_wallet_ledger PRIMARY KEY (id),
    CONSTRAINT fk_wallet_ledger_wallet
        FOREIGN KEY (wallet_id) REFERENCES wallets (id),
    CONSTRAINT fk_wallet_ledger_operation
        FOREIGN KEY (operation_id) REFERENCES operations (id),
    CONSTRAINT ck_wallet_ledger_entry_type
        CHECK (entry_type IN ('CREDIT', 'DEBIT')),
    CONSTRAINT ck_wallet_ledger_category
        CHECK (category IN (
            'DEPOSIT',
            'TRANSFER_IN',
            'TRANSFER_OUT',
            'WITHDRAWAL',
            'PAYOUT',
            'BONUS',
            'REFUND',
            'PAYMENT',
            'REPAYMENT',
            'ADJUSTMENT'
        )),
    CONSTRAINT ck_wallet_ledger_status
        CHECK (status IN ('PENDING', 'CONFIRMED', 'FAILED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_wallet_ledger_wallet_operation
    ON wallet_ledger (wallet_id, operation_id);

CREATE INDEX IF NOT EXISTS idx_wallet_ledger_wallet_created
    ON wallet_ledger (wallet_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_wallet_ledger_wallet_status_created
    ON wallet_ledger (wallet_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_wallet_ledger_wallet_entry_status_category_created
    ON wallet_ledger (wallet_id, entry_type, status, category, created_at DESC);

WITH ranked_user_wallets AS (
    SELECT w.id,
           w.account_holder_id,
           ROW_NUMBER() OVER (
               PARTITION BY w.account_holder_id
               ORDER BY CASE WHEN w.status = 'ACTIVE' THEN 0 ELSE 1 END,
                        w.created_at DESC,
                        w.id DESC
           ) AS rn
    FROM wallets w
    WHERE w.wallet_type = 'USER'
),
preferred_user_wallets AS (
    SELECT id AS wallet_id,
           account_holder_id
    FROM ranked_user_wallets
    WHERE rn = 1
)
INSERT INTO wallet_ledger (
    wallet_id,
    operation_id,
    entry_type,
    category,
    status,
    amount,
    currency,
    created_at,
    updated_at
)
SELECT puw.wallet_id,
       o.id,
       'CREDIT',
       CASE
           WHEN o.type IN ('WALLET_DEPOSIT_PSP', 'WALLET_DEPOSIT_AGENT') THEN 'DEPOSIT'
           WHEN o.type = 'COMMISSION_PAYOUT' THEN 'BONUS'
           WHEN o.type IN ('LOAN_DISBURSEMENT', 'MARKETPLACE_MERCHANT_PAYOUT', 'COMMERCE_DSB_PAYOUT') THEN 'PAYOUT'
           WHEN o.type = 'ADJUSTMENT' THEN 'REFUND'
       END,
       CASE
           WHEN o.status = 'CONFIRMED' THEN 'CONFIRMED'
           WHEN o.status IN ('FAILED', 'EXPIRED', 'CANCELED') THEN 'FAILED'
           ELSE 'PENDING'
       END,
       o.amount,
       o.currency,
       o.created_at,
       o.updated_at
FROM operations o
JOIN preferred_user_wallets puw
  ON puw.account_holder_id = o.user_id
WHERE o.type IN (
    'WALLET_DEPOSIT_PSP',
    'WALLET_DEPOSIT_AGENT',
    'COMMISSION_PAYOUT',
    'LOAN_DISBURSEMENT',
    'MARKETPLACE_MERCHANT_PAYOUT',
    'COMMERCE_DSB_PAYOUT',
    'ADJUSTMENT'
)
ON CONFLICT (wallet_id, operation_id) DO UPDATE
SET entry_type = EXCLUDED.entry_type,
    category = EXCLUDED.category,
    status = EXCLUDED.status,
    amount = EXCLUDED.amount,
    currency = EXCLUDED.currency,
    created_at = EXCLUDED.created_at,
    updated_at = EXCLUDED.updated_at;

WITH ranked_user_wallets AS (
    SELECT w.id,
           w.account_holder_id,
           ROW_NUMBER() OVER (
               PARTITION BY w.account_holder_id
               ORDER BY CASE WHEN w.status = 'ACTIVE' THEN 0 ELSE 1 END,
                        w.created_at DESC,
                        w.id DESC
           ) AS rn
    FROM wallets w
    WHERE w.wallet_type = 'USER'
),
preferred_user_wallets AS (
    SELECT id AS wallet_id,
           account_holder_id
    FROM ranked_user_wallets
    WHERE rn = 1
)
INSERT INTO wallet_ledger (
    wallet_id,
    operation_id,
    entry_type,
    category,
    status,
    amount,
    currency,
    created_at,
    updated_at
)
SELECT puw.wallet_id,
       o.id,
       'DEBIT',
       CASE
           WHEN o.type = 'WALLET_PAYMENT' THEN 'TRANSFER_OUT'
           WHEN o.type = 'WALLET_WITHDRAWAL' THEN 'WITHDRAWAL'
           WHEN o.type IN ('SUBSCRIPTION', 'MARKETPLACE_PAYMENT') THEN 'PAYMENT'
           WHEN o.type = 'LOAN_REPAYMENT' THEN 'REPAYMENT'
           WHEN o.type IN ('MARKETPLACE_MERCHANT_PAYOUT_REVERSAL', 'COMMERCE_DSB_REVERSAL') THEN 'REFUND'
       END,
       CASE
           WHEN o.status = 'CONFIRMED' THEN 'CONFIRMED'
           WHEN o.status IN ('FAILED', 'EXPIRED', 'CANCELED') THEN 'FAILED'
           ELSE 'PENDING'
       END,
       o.amount,
       o.currency,
       o.created_at,
       o.updated_at
FROM operations o
JOIN preferred_user_wallets puw
  ON puw.account_holder_id = o.user_id
WHERE o.type IN (
    'WALLET_PAYMENT',
    'WALLET_WITHDRAWAL',
    'SUBSCRIPTION',
    'MARKETPLACE_PAYMENT',
    'LOAN_REPAYMENT',
    'MARKETPLACE_MERCHANT_PAYOUT_REVERSAL',
    'COMMERCE_DSB_REVERSAL'
)
ON CONFLICT (wallet_id, operation_id) DO UPDATE
SET entry_type = EXCLUDED.entry_type,
    category = EXCLUDED.category,
    status = EXCLUDED.status,
    amount = EXCLUDED.amount,
    currency = EXCLUDED.currency,
    created_at = EXCLUDED.created_at,
    updated_at = EXCLUDED.updated_at;

WITH ranked_user_wallets AS (
    SELECT w.id,
           w.account_holder_id,
           ROW_NUMBER() OVER (
               PARTITION BY w.account_holder_id
               ORDER BY CASE WHEN w.status = 'ACTIVE' THEN 0 ELSE 1 END,
                        w.created_at DESC,
                        w.id DESC
           ) AS rn
    FROM wallets w
    WHERE w.wallet_type = 'USER'
),
preferred_user_wallets AS (
    SELECT id AS wallet_id,
           account_holder_id
    FROM ranked_user_wallets
    WHERE rn = 1
)
INSERT INTO wallet_ledger (
    wallet_id,
    operation_id,
    entry_type,
    category,
    status,
    amount,
    currency,
    created_at,
    updated_at
)
SELECT COALESCE(o.destination_wallet_id, puw.wallet_id),
       o.id,
       'CREDIT',
       'TRANSFER_IN',
       'CONFIRMED',
       o.amount,
       o.currency,
       o.created_at,
       o.updated_at
FROM operations o
LEFT JOIN preferred_user_wallets puw
  ON puw.account_holder_id = o.destination_account_holder_id
WHERE o.type = 'WALLET_PAYMENT'
  AND o.status = 'CONFIRMED'
  AND COALESCE(o.destination_wallet_id, puw.wallet_id) IS NOT NULL
ON CONFLICT (wallet_id, operation_id) DO UPDATE
SET entry_type = EXCLUDED.entry_type,
    category = EXCLUDED.category,
    status = EXCLUDED.status,
    amount = EXCLUDED.amount,
    currency = EXCLUDED.currency,
    created_at = EXCLUDED.created_at,
    updated_at = EXCLUDED.updated_at;
