-- US24: profiles proches minimalistes, sans date de naissance ni autre attribut personnel.
CREATE TABLE family_profile (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL,
    label VARCHAR(60) NOT NULL CHECK (length(btrim(label)) BETWEEN 1 AND 60),
    created_at TIMESTAMPTZ NOT NULL,
    deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_family_profile_patient_label_active
    ON family_profile (patient_id, lower(btrim(label)))
    WHERE deleted_at IS NULL;

CREATE INDEX idx_family_profile_patient_active
    ON family_profile (patient_id, created_at)
    WHERE deleted_at IS NULL;

ALTER TABLE prescription
    ADD COLUMN family_profile_id UUID REFERENCES family_profile (id);

CREATE INDEX idx_prescription_family_profile
    ON prescription (family_profile_id)
    WHERE family_profile_id IS NOT NULL;
