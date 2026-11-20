-- Link usage rows to a case / analysis (or other entity) for "Общее" breakdowns.
ALTER TABLE llm_usage
    ADD COLUMN IF NOT EXISTS ref_id UUID;

CREATE INDEX IF NOT EXISTS idx_llm_usage_purpose_ref_created
    ON llm_usage (purpose, ref_id, created_at DESC);
