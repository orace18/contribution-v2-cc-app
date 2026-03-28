-- CPass integration: store the external OTP reference ID issued by CPass
-- when OTP delegation is enabled (cpass.enabled=true).
-- code_hash stores the sentinel value 'CPASS_DELEGATED' in this mode.
ALTER TABLE phone_verifications
    ADD COLUMN IF NOT EXISTS cpass_reference_id VARCHAR(128);
