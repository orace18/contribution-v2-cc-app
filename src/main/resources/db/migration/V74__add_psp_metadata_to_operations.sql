-- V74 - Add PSP metadata columns to operations table
ALTER TABLE operations
    ADD COLUMN IF NOT EXISTS payment_id         VARCHAR(128),
    ADD COLUMN IF NOT EXISTS payout_id          VARCHAR(128),
    ADD COLUMN IF NOT EXISTS provider_reference VARCHAR(128),
    ADD COLUMN IF NOT EXISTS redirect_url       VARCHAR(1024),
    ADD COLUMN IF NOT EXISTS payment_method     VARCHAR(64),
    ADD COLUMN IF NOT EXISTS psp_status         VARCHAR(64),
    ADD COLUMN IF NOT EXISTS psp_payload        TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS uk_operations_payment_id
    ON operations (payment_id)
    WHERE payment_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_operations_payout_id
    ON operations (payout_id)
    WHERE payout_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_operations_provider_reference
    ON operations (provider_reference)
    WHERE provider_reference IS NOT NULL;
