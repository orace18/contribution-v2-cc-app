-- Short 6-digit codes for circle invitations
-- Allows mobile users to join a circle by typing a code or scanning a QR

CREATE TABLE invite_codes (
    id          UUID         PRIMARY KEY,
    code        VARCHAR(6)   NOT NULL,
    invite_id   UUID         NOT NULL,
    circle_id   UUID         NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_invite_codes_invite  FOREIGN KEY (invite_id)  REFERENCES circle_invites(id),
    CONSTRAINT fk_invite_codes_circle  FOREIGN KEY (circle_id)  REFERENCES circles(id),
    CONSTRAINT uk_invite_codes_code    UNIQUE (code),
    CONSTRAINT ck_invite_codes_code    CHECK (code ~ '^[0-9]{6}$')
);

CREATE INDEX idx_invite_codes_code       ON invite_codes (code);
CREATE INDEX idx_invite_codes_expires_at ON invite_codes (expires_at);
