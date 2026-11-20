-- History of admin balance top-ups for app users.
CREATE TABLE balance_topup (
    id                      UUID PRIMARY KEY,
    user_id                 UUID NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    username                VARCHAR(64) NOT NULL,
    display_name            VARCHAR(128),
    amount_rub              NUMERIC(14, 4) NOT NULL,
    balance_after_rub       NUMERIC(14, 4) NOT NULL,
    created_by_user_id      UUID REFERENCES app_user (id) ON DELETE SET NULL,
    created_by_username     VARCHAR(64),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_balance_topup_created ON balance_topup (created_at DESC);
CREATE INDEX idx_balance_topup_user_created ON balance_topup (user_id, created_at DESC);
