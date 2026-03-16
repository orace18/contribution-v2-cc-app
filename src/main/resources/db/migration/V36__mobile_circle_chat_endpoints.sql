CREATE TABLE IF NOT EXISTS circle_member_interactions (
    id UUID PRIMARY KEY,
    circle_id UUID NOT NULL,
    member_id UUID NOT NULL,
    sender_id UUID NOT NULL,
    type VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_circle_member_interactions_circle
        FOREIGN KEY (circle_id) REFERENCES circles(id) ON DELETE CASCADE,
    CONSTRAINT fk_circle_member_interactions_member
        FOREIGN KEY (member_id) REFERENCES account_holders(id),
    CONSTRAINT fk_circle_member_interactions_sender
        FOREIGN KEY (sender_id) REFERENCES account_holders(id),
    CONSTRAINT ck_circle_member_interactions_type
        CHECK (type IN ('ENCOURAGE', 'PRAY', 'ADVISE'))
);

CREATE INDEX IF NOT EXISTS idx_circle_member_interactions_circle_member_created
    ON circle_member_interactions (circle_id, member_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_circle_member_interactions_sender_created
    ON circle_member_interactions (sender_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_chat_messages_conversation_created
    ON chat_messages (conversation_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_circle_members_circle_id
    ON circle_members (circle_id);

CREATE INDEX IF NOT EXISTS idx_circle_members_account_holder_id
    ON circle_members (account_holder_id);
