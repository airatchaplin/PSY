CREATE TABLE app_setting (
    key         VARCHAR(128) PRIMARY KEY,
    value       TEXT NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE llm_usage (
    id                  UUID PRIMARY KEY,
    model               VARCHAR(255) NOT NULL,
    purpose             VARCHAR(64) NOT NULL DEFAULT 'chat',
    prompt_tokens       INT,
    completion_tokens   INT,
    total_tokens        INT,
    cost_rub            NUMERIC(14, 6),
    generation_id       VARCHAR(128),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_llm_usage_created ON llm_usage(created_at DESC);
CREATE INDEX idx_llm_usage_model_created ON llm_usage(model, created_at DESC);
