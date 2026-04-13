-- V102: Add civil identity declaration fields to account_holders.
-- nationality: ISO 3166-1 alpha-2 country code of the document issuer (e.g. 'BJ', 'FR').
-- declared_document_type: type of identity document declared by the user (e.g. 'CNI', 'PASSPORT').
-- These fields are set when the user confirms their civil identity before uploading KYC documents.

ALTER TABLE account_holders
    ADD COLUMN IF NOT EXISTS nationality          VARCHAR(3),
    ADD COLUMN IF NOT EXISTS declared_document_type VARCHAR(32);
