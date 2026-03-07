CREATE TABLE media_files (
    id UUID PRIMARY KEY,
    account_holder_id UUID NOT NULL,
    type VARCHAR(64) NOT NULL,
    bucket VARCHAR(64) NOT NULL,
    object_key VARCHAR(512) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    size BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_media_files_account_holder FOREIGN KEY (account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT uk_media_files_bucket_object_key UNIQUE (bucket, object_key),
    CONSTRAINT ck_media_files_size CHECK (size > 0),
    CONSTRAINT ck_media_files_status CHECK (status IN ('PENDING_UPLOAD', 'READY', 'FAILED', 'DELETED'))
);

CREATE INDEX idx_media_files_account_holder_created_at
    ON media_files (account_holder_id, created_at DESC);

CREATE INDEX idx_media_files_status
    ON media_files (status);

CREATE TABLE kyc_documents (
    id UUID PRIMARY KEY,
    account_holder_id UUID NOT NULL,
    media_file_id UUID NOT NULL,
    document_type VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_kyc_documents_account_holder FOREIGN KEY (account_holder_id) REFERENCES account_holders(id),
    CONSTRAINT fk_kyc_documents_media_file FOREIGN KEY (media_file_id) REFERENCES media_files(id),
    CONSTRAINT uk_kyc_documents_media_file UNIQUE (media_file_id),
    CONSTRAINT ck_kyc_documents_status CHECK (status IN ('SUBMITTED', 'VERIFIED', 'REJECTED'))
);

CREATE INDEX idx_kyc_documents_account_holder_type
    ON kyc_documents (account_holder_id, document_type);

ALTER TABLE user_profiles
    ADD COLUMN IF NOT EXISTS profile_photo_media_id UUID;

ALTER TABLE user_profiles
    DROP CONSTRAINT IF EXISTS fk_user_profiles_profile_photo_media;

ALTER TABLE user_profiles
    ADD CONSTRAINT fk_user_profiles_profile_photo_media
        FOREIGN KEY (profile_photo_media_id) REFERENCES media_files(id);
