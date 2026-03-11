ALTER TABLE community_posts
    ADD COLUMN IF NOT EXISTS author_account_holder_id UUID,
    ADD COLUMN IF NOT EXISTS community_id UUID,
    ADD COLUMN IF NOT EXISTS visibility VARCHAR(32);

ALTER TABLE community_posts
    ADD CONSTRAINT fk_community_posts_author_account_holder
        FOREIGN KEY (author_account_holder_id) REFERENCES account_holders(id);

CREATE INDEX IF NOT EXISTS idx_community_posts_author_account_holder
    ON community_posts (author_account_holder_id);

CREATE TABLE IF NOT EXISTS mlm_commissions (
    id UUID PRIMARY KEY,
    operation_id UUID NOT NULL,
    beneficiary_account_holder_id UUID NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    source VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_mlm_commissions_operation FOREIGN KEY (operation_id) REFERENCES operations(id),
    CONSTRAINT fk_mlm_commissions_beneficiary FOREIGN KEY (beneficiary_account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT uk_mlm_commissions_operation UNIQUE (operation_id),
    CONSTRAINT ck_mlm_commissions_amount_positive CHECK (amount >= 0)
);

CREATE INDEX IF NOT EXISTS idx_mlm_commissions_beneficiary_created
    ON mlm_commissions (beneficiary_account_holder_id, created_at DESC);

CREATE TABLE IF NOT EXISTS chat_messages (
    id UUID PRIMARY KEY,
    sender_account_holder_id UUID NOT NULL,
    recipient_account_holder_id UUID NOT NULL,
    content VARCHAR(2000) NOT NULL,
    channel VARCHAR(32) NOT NULL,
    client_message_id VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_chat_messages_sender FOREIGN KEY (sender_account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT fk_chat_messages_recipient FOREIGN KEY (recipient_account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT uk_chat_messages_sender_client_message UNIQUE (sender_account_holder_id, client_message_id)
);

CREATE INDEX IF NOT EXISTS idx_chat_messages_sender_created
    ON chat_messages (sender_account_holder_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_chat_messages_recipient_created
    ON chat_messages (recipient_account_holder_id, created_at DESC);

CREATE TABLE IF NOT EXISTS event_listener_checkpoints (
    id UUID PRIMARY KEY,
    listener_key VARCHAR(128) NOT NULL,
    event_id UUID NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_event_listener_checkpoints_listener_event UNIQUE (listener_key, event_id)
);

CREATE INDEX IF NOT EXISTS idx_event_listener_checkpoints_listener_key
    ON event_listener_checkpoints (listener_key);
