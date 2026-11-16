-- US22 / EF25 : alerte du patient quand un médicament introuvable revient en stock.
-- Une alerte est déclenchée une seule fois, par la première pharmacie validée du rayon qui le remet en stock.
CREATE TABLE stock_alerts (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id            UUID             NOT NULL,
    medication_code       VARCHAR(13)      NOT NULL,
    medication_name       VARCHAR(200)     NOT NULL,
    latitude              DOUBLE PRECISION NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude             DOUBLE PRECISION NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    radius_m              INTEGER          NOT NULL CHECK (radius_m BETWEEN 100 AND 20000),
    location              geography(Point, 4326)
                          GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography) STORED,
    status                VARCHAR(20)      NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DECLENCHEE')),
    created_at            TIMESTAMPTZ      NOT NULL DEFAULT now(),
    triggered_at          TIMESTAMPTZ,
    triggered_pharmacy_id UUID REFERENCES pharmacies (id) ON DELETE SET NULL
);

-- Pas deux alertes actives pour le même médicament chez le même patient
CREATE UNIQUE INDEX uq_stock_alerts_active ON stock_alerts (patient_id, medication_code) WHERE status = 'ACTIVE';
-- Recherche rapide des alertes actives d'un médicament quand il revient en stock
CREATE INDEX idx_stock_alerts_code_active ON stock_alerts (medication_code) WHERE status = 'ACTIVE';
CREATE INDEX idx_stock_alerts_location ON stock_alerts USING gist (location);
