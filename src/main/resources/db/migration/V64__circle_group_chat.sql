-- Add type and circle_id to chat_conversations for CIRCLE group chat support
ALTER TABLE chat_conversations
    ADD COLUMN type VARCHAR(16) NOT NULL DEFAULT 'DIRECT',
    ADD COLUMN circle_id UUID REFERENCES circles(id);

-- Participant columns are nullable for CIRCLE type (no A/B participants)
ALTER TABLE chat_conversations
    ALTER COLUMN participant_a_account_holder_id DROP NOT NULL,
    ALTER COLUMN participant_b_account_holder_id DROP NOT NULL;

-- Unique constraint: one conversation per circle
CREATE UNIQUE INDEX uk_chat_conversations_circle
    ON chat_conversations(circle_id)
    WHERE circle_id IS NOT NULL;

-- recipient is null for circle messages (broadcast to all members)
ALTER TABLE chat_messages
    ALTER COLUMN recipient_account_holder_id DROP NOT NULL;
