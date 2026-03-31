-- V77 - Add JawuPay country metadata to operations table
ALTER TABLE operations
    ADD COLUMN IF NOT EXISTS psp_country VARCHAR(5);
