-- Personal case copies keep a link to the original for idempotent sharing.
ALTER TABLE therapy_case
    ADD COLUMN IF NOT EXISTS origin_case_id UUID REFERENCES therapy_case(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_therapy_case_created_by ON therapy_case(created_by);
CREATE INDEX IF NOT EXISTS idx_therapy_case_origin_owner
    ON therapy_case(origin_case_id, created_by);

-- Ensure builtins stay global (no owner).
UPDATE therapy_case
SET created_by = NULL
WHERE source = 'BUILTIN';
