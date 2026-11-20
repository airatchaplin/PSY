-- Per-user internal balance (₽). Admin tops up; LLM blocked when balance < 0.
ALTER TABLE app_user
    ADD COLUMN balance_rub NUMERIC(14, 4) NOT NULL DEFAULT 0;
