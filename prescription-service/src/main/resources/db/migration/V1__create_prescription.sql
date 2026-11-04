-- US06 : ordonnance envoyée par un patient + image chiffrée (RG06, loi 09-08)

CREATE TABLE prescription (
    id               UUID         PRIMARY KEY,
    patient_id       UUID         NOT NULL,
    status           VARCHAR(30)  NOT NULL,
    file_type        VARCHAR(10)  NOT NULL,
    size_bytes       BIGINT       NOT NULL,
    consent_given_at TIMESTAMPTZ  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    image_deleted_at TIMESTAMPTZ
);

-- Pour retrouver vite les ordonnances d'un patient (historique US18)
CREATE INDEX idx_prescription_patient ON prescription (patient_id);

-- Table séparée : l'image (jusqu'à 5 Mo) n'est chargée que lorsqu'on en a besoin
CREATE TABLE prescription_image (
    prescription_id UUID  PRIMARY KEY REFERENCES prescription (id) ON DELETE CASCADE,
    iv              BYTEA NOT NULL,
    encrypted_data  BYTEA NOT NULL
);
