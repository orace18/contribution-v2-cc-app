-- Identity verification pipeline (server-side OCR + face match + scoring)

CREATE TABLE identity_verifications (
    id                UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    account_holder_id UUID        NOT NULL,
    reference_id      VARCHAR(128),
    status            VARCHAR(16) NOT NULL,
    current_step      VARCHAR(32),
    document_type     VARCHAR(32) NOT NULL,
    first_name        VARCHAR(128),
    last_name         VARCHAR(128),
    phone_number      VARCHAR(32),
    reliability_score INTEGER,
    decision_reason   VARCHAR(64),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_identity_verif_account_holder ON identity_verifications (account_holder_id);
CREATE INDEX idx_identity_verif_status         ON identity_verifications (status);

-- ------------------------------------------------------------

CREATE TABLE verification_documents (
    id                 UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    verification_id    UUID        NOT NULL REFERENCES identity_verifications (id),
    document_front_key TEXT        NOT NULL,
    document_back_key  TEXT,
    selfie_key         TEXT        NOT NULL,
    bucket_name        VARCHAR(128) NOT NULL,
    capture_device     VARCHAR(64),
    app_version        VARCHAR(32),
    platform           VARCHAR(32),
    uploaded_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_verif_documents_verification ON verification_documents (verification_id);

-- ------------------------------------------------------------

CREATE TABLE verification_analysis (
    id                        UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    verification_id           UUID        NOT NULL UNIQUE REFERENCES identity_verifications (id),
    document_readable         BOOLEAN,
    ocr_confidence            INTEGER,
    extracted_full_name       VARCHAR(256),
    extracted_document_number VARCHAR(128),
    extracted_birth_date      DATE,
    extracted_expiry_date     DATE,
    extracted_nationality     VARCHAR(64),
    document_expired          BOOLEAN,
    face_match_score          INTEGER,
    face_matched              BOOLEAN,
    score_details_json        TEXT,
    analyzed_at               TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
