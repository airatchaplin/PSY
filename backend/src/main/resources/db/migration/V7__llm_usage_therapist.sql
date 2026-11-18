ALTER TABLE llm_usage
    ADD COLUMN IF NOT EXISTS therapist_id UUID REFERENCES therapist_profile(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_llm_usage_therapist_created
    ON llm_usage(therapist_id, created_at DESC);
