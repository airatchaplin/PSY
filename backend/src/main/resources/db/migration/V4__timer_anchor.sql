ALTER TABLE therapy_session
    ADD COLUMN IF NOT EXISTS timer_anchor_at TIMESTAMPTZ;

UPDATE therapy_session
SET timer_anchor_at = started_at
WHERE status = 'ACTIVE'
  AND timer_paused = FALSE
  AND timer_anchor_at IS NULL;
