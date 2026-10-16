-- Preserve the source dosage without interpreting compound expressions.
ALTER TABLE medication ADD COLUMN dosage_brut TEXT;
COMMENT ON COLUMN medication.dosage_brut IS 'Original nonblank DOSAGE1 source text; NULL when absent';
