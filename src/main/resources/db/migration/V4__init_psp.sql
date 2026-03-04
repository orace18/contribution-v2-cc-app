CREATE TABLE psp_callback_events (
    id UUID PRIMARY KEY,
    nonce VARCHAR(128) NOT NULL,
    signature VARCHAR(256) NOT NULL,
    payload_hash VARCHAR(256) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_psp_callback_nonce UNIQUE (nonce)
);

CREATE INDEX idx_psp_callback_events_received_at ON psp_callback_events (received_at);
