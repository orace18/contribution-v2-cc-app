-- V76 - Store JawuPay webhook events for replay protection
CREATE TABLE IF NOT EXISTS jawupay_webhook_events (
    id UUID PRIMARY KEY,
    event_id VARCHAR(128),
    event_hash VARCHAR(64) NOT NULL,
    operation_id UUID NOT NULL,
    provider VARCHAR(32) NOT NULL,
    status VARCHAR(64) NOT NULL,
    payment_id VARCHAR(128),
    payout_id VARCHAR(128),
    provider_reference VARCHAR(128),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_jawupay_webhook_events_event_hash
    ON jawupay_webhook_events (event_hash);

CREATE UNIQUE INDEX IF NOT EXISTS uk_jawupay_webhook_events_event_id
    ON jawupay_webhook_events (event_id)
    WHERE event_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_jawupay_webhook_events_operation_id
    ON jawupay_webhook_events (operation_id);

CREATE INDEX IF NOT EXISTS idx_jawupay_webhook_events_payment_id
    ON jawupay_webhook_events (payment_id);

CREATE INDEX IF NOT EXISTS idx_jawupay_webhook_events_payout_id
    ON jawupay_webhook_events (payout_id);

CREATE INDEX IF NOT EXISTS idx_jawupay_webhook_events_provider_reference
    ON jawupay_webhook_events (provider_reference);
