CREATE TABLE phone_verifications (
    id UUID PRIMARY KEY,
    requester_account_holder_id UUID NOT NULL,
    phone_number VARCHAR(32) NOT NULL,
    channel VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    code_hash VARCHAR(128) NOT NULL,
    attempt_count INTEGER NOT NULL,
    max_attempts INTEGER NOT NULL,
    send_count INTEGER NOT NULL,
    max_send_count INTEGER NOT NULL,
    last_sent_at TIMESTAMPTZ NOT NULL,
    resend_available_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_phone_verifications_requester FOREIGN KEY (requester_account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT ck_phone_verifications_channel CHECK (channel IN ('SMS', 'WHATSAPP')),
    CONSTRAINT ck_phone_verifications_status CHECK (status IN ('PENDING', 'VERIFIED', 'EXPIRED', 'FAILED')),
    CONSTRAINT ck_phone_verifications_attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT ck_phone_verifications_max_attempts CHECK (max_attempts > 0),
    CONSTRAINT ck_phone_verifications_send_count CHECK (send_count > 0),
    CONSTRAINT ck_phone_verifications_max_send_count CHECK (max_send_count > 0)
);

CREATE INDEX idx_phone_verifications_requester_created
    ON phone_verifications (requester_account_holder_id, created_at DESC);
CREATE INDEX idx_phone_verifications_phone_status
    ON phone_verifications (phone_number, status);
CREATE INDEX idx_phone_verifications_expires_at
    ON phone_verifications (expires_at);

CREATE TABLE circle_invites (
    id UUID PRIMARY KEY,
    circle_id UUID NOT NULL,
    created_by_account_holder_id UUID NOT NULL,
    invitation_token VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL,
    max_uses INTEGER NOT NULL,
    used_count INTEGER NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_circle_invites_circle FOREIGN KEY (circle_id) REFERENCES circles(id),
    CONSTRAINT fk_circle_invites_creator FOREIGN KEY (created_by_account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT uk_circle_invites_token UNIQUE (invitation_token),
    CONSTRAINT ck_circle_invites_status CHECK (status IN ('ACTIVE', 'REVOKED', 'EXPIRED')),
    CONSTRAINT ck_circle_invites_max_uses CHECK (max_uses > 0),
    CONSTRAINT ck_circle_invites_used_count CHECK (used_count >= 0)
);

CREATE INDEX idx_circle_invites_circle_status
    ON circle_invites (circle_id, status);
CREATE INDEX idx_circle_invites_expires_at
    ON circle_invites (expires_at);
