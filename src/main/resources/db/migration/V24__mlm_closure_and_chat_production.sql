CREATE TABLE IF NOT EXISTS mlm_relationships (
    ancestor_id UUID NOT NULL,
    descendant_id UUID NOT NULL,
    level INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_mlm_relationships PRIMARY KEY (ancestor_id, descendant_id),
    CONSTRAINT fk_mlm_relationships_ancestor FOREIGN KEY (ancestor_id) REFERENCES account_holders(id),
    CONSTRAINT fk_mlm_relationships_descendant FOREIGN KEY (descendant_id) REFERENCES account_holders(id),
    CONSTRAINT ck_mlm_relationships_level_non_negative CHECK (level >= 0)
);

CREATE INDEX IF NOT EXISTS idx_mlm_relationships_ancestor_level_created
    ON mlm_relationships (ancestor_id, level, created_at);

CREATE INDEX IF NOT EXISTS idx_mlm_relationships_descendant_level
    ON mlm_relationships (descendant_id, level);

INSERT INTO mlm_relationships (ancestor_id, descendant_id, level, created_at)
SELECT ah.id, ah.id, 0, COALESCE(ah.created_at, NOW())
FROM account_holders ah
ON CONFLICT (ancestor_id, descendant_id) DO NOTHING;

CREATE TABLE IF NOT EXISTS chat_conversations (
    id UUID PRIMARY KEY,
    participant_a_account_holder_id UUID NOT NULL,
    participant_b_account_holder_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_chat_conversations_participant_a
        FOREIGN KEY (participant_a_account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT fk_chat_conversations_participant_b
        FOREIGN KEY (participant_b_account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT uk_chat_conversations_participants
        UNIQUE (participant_a_account_holder_id, participant_b_account_holder_id),
    CONSTRAINT ck_chat_conversations_distinct_participants
        CHECK (participant_a_account_holder_id <> participant_b_account_holder_id)
);

CREATE INDEX IF NOT EXISTS idx_chat_conversations_participant_a
    ON chat_conversations (participant_a_account_holder_id);

CREATE INDEX IF NOT EXISTS idx_chat_conversations_participant_b
    ON chat_conversations (participant_b_account_holder_id);

ALTER TABLE chat_messages
    ADD COLUMN IF NOT EXISTS conversation_id UUID,
    ADD COLUMN IF NOT EXISTS message_status VARCHAR(16) NOT NULL DEFAULT 'SENT',
    ADD COLUMN IF NOT EXISTS delivered_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS read_at TIMESTAMPTZ;

WITH normalized_pairs AS (
    SELECT DISTINCT
        LEAST(sender_account_holder_id, recipient_account_holder_id) AS participant_a,
        GREATEST(sender_account_holder_id, recipient_account_holder_id) AS participant_b
    FROM chat_messages
    WHERE sender_account_holder_id <> recipient_account_holder_id
)
INSERT INTO chat_conversations (id, participant_a_account_holder_id, participant_b_account_holder_id, created_at)
SELECT
    (
        SUBSTRING(MD5(participant_a::text || ':' || participant_b::text), 1, 8) || '-' ||
        SUBSTRING(MD5(participant_a::text || ':' || participant_b::text), 9, 4) || '-' ||
        SUBSTRING(MD5(participant_a::text || ':' || participant_b::text), 13, 4) || '-' ||
        SUBSTRING(MD5(participant_a::text || ':' || participant_b::text), 17, 4) || '-' ||
        SUBSTRING(MD5(participant_a::text || ':' || participant_b::text), 21, 12)
    )::UUID AS id,
    participant_a,
    participant_b,
    NOW()
FROM normalized_pairs
ON CONFLICT (participant_a_account_holder_id, participant_b_account_holder_id) DO NOTHING;

UPDATE chat_messages cm
SET conversation_id = cc.id
FROM chat_conversations cc
WHERE cm.conversation_id IS NULL
  AND LEAST(cm.sender_account_holder_id, cm.recipient_account_holder_id) = cc.participant_a_account_holder_id
  AND GREATEST(cm.sender_account_holder_id, cm.recipient_account_holder_id) = cc.participant_b_account_holder_id;

ALTER TABLE chat_messages
    DROP CONSTRAINT IF EXISTS fk_chat_messages_conversation;

ALTER TABLE chat_messages
    ADD CONSTRAINT fk_chat_messages_conversation
        FOREIGN KEY (conversation_id) REFERENCES chat_conversations(id);

ALTER TABLE chat_messages
    DROP CONSTRAINT IF EXISTS ck_chat_messages_message_status;

ALTER TABLE chat_messages
    ADD CONSTRAINT ck_chat_messages_message_status
        CHECK (message_status IN ('SENT', 'DELIVERED', 'READ'));

CREATE INDEX IF NOT EXISTS idx_chat_messages_conversation_created
    ON chat_messages (conversation_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_chat_messages_recipient_status_created
    ON chat_messages (recipient_account_holder_id, message_status, created_at DESC);
