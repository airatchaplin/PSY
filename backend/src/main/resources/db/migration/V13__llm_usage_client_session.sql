-- Attribute LLM usage to client / session for full per-user stats.
ALTER TABLE llm_usage
    ADD COLUMN IF NOT EXISTS client_id UUID REFERENCES client (id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS session_id UUID REFERENCES therapy_session (id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_llm_usage_client_created
    ON llm_usage (client_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_llm_usage_therapist_client_created
    ON llm_usage (therapist_id, client_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_llm_usage_purpose_created
    ON llm_usage (purpose, created_at DESC);
