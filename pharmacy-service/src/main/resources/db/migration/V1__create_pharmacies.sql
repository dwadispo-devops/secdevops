CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE pharmacies (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    osm_id        BIGINT UNIQUE,
    name          VARCHAR(200) NOT NULL,
    address       VARCHAR(300),
    phone         VARCHAR(30),
    opening_hours VARCHAR(200),
    latitude      DOUBLE PRECISION NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude     DOUBLE PRECISION NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    location      geography(Point, 4326)
                  GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography) STORED,
    status        VARCHAR(20) NOT NULL DEFAULT 'APPROVED',
    source        VARCHAR(20) NOT NULL DEFAULT 'REGISTERED',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_pharmacies_location ON pharmacies USING GIST (location);
