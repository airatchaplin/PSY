ALTER TABLE therapy_case
    ADD COLUMN IF NOT EXISTS share_token VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS uk_therapy_case_share_token
    ON therapy_case(share_token);
