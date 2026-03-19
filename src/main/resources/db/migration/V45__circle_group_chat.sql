-- Circle group chat messages (text, image, video, audio via MinIO)
-- Le mobile uploade d'abord via /api/media/upload-url (type=CIRCLE_CHAT),
-- confirme via /api/media/confirm, puis envoie le mediaId dans le message.
CREATE TABLE circle_chat_messages (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    circle_id           UUID        NOT NULL REFERENCES circles(id) ON DELETE CASCADE,
    sender_id           UUID        NOT NULL,
    message_type        VARCHAR(16) NOT NULL DEFAULT 'TEXT',   -- TEXT | IMAGE | VIDEO | AUDIO
    content             TEXT,                                   -- texte ou légende du media
    media_id            UUID        REFERENCES media_files(id), -- fichier uploadé sur MinIO
    media_duration_secs INTEGER,                               -- durée audio/vidéo (métadonnée client)
    client_message_id   VARCHAR(128),                          -- déduplication côté client
    sent_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_circle_chat_messages PRIMARY KEY (id),
    CONSTRAINT uq_circle_chat_client_msg UNIQUE (circle_id, sender_id, client_message_id)
);

CREATE INDEX idx_circle_chat_circle_sent ON circle_chat_messages(circle_id, sent_at DESC);
CREATE INDEX idx_circle_chat_sender ON circle_chat_messages(sender_id);
CREATE INDEX idx_circle_chat_media ON circle_chat_messages(media_id);

-- Read receipts : qui a lu quoi et quand
CREATE TABLE circle_chat_read_receipts (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    message_id  UUID        NOT NULL REFERENCES circle_chat_messages(id) ON DELETE CASCADE,
    member_id   UUID        NOT NULL,
    read_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_circle_chat_read_receipts PRIMARY KEY (id),
    CONSTRAINT uq_circle_chat_read UNIQUE (message_id, member_id)
);

CREATE INDEX idx_circle_chat_read_message ON circle_chat_read_receipts(message_id);
CREATE INDEX idx_circle_chat_read_member ON circle_chat_read_receipts(member_id);
