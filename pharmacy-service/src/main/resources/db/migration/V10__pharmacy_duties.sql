-- US15 : périodes de garde des pharmacies (bornes incluses, dates à l'heure du Maroc)
CREATE TABLE pharmacy_duties (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pharmacy_id UUID NOT NULL REFERENCES pharmacies(id) ON DELETE CASCADE,
    starts_on   DATE NOT NULL,
    ends_on     DATE NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_pharmacy_duties_period CHECK (ends_on >= starts_on),
    CONSTRAINT uq_pharmacy_duties UNIQUE (pharmacy_id, starts_on)
);

CREATE INDEX idx_pharmacy_duties_period ON pharmacy_duties (starts_on, ends_on);
