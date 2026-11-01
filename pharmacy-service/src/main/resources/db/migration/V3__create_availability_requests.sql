CREATE TABLE availability_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id UUID NOT NULL,
    latitude DOUBLE PRECISION NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude DOUBLE PRECISION NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    radius_m INTEGER NOT NULL CHECK (radius_m BETWEEN 100 AND 20000),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE availability_request_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID NOT NULL REFERENCES availability_requests(id) ON DELETE CASCADE,
    medication_name VARCHAR(200) NOT NULL,
    quantity INTEGER NOT NULL DEFAULT 1 CHECK (quantity > 0)
);

CREATE TABLE availability_request_pharmacies (
    request_id UUID NOT NULL REFERENCES availability_requests(id) ON DELETE CASCADE,
    pharmacy_id UUID NOT NULL REFERENCES pharmacies(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    PRIMARY KEY (request_id, pharmacy_id)
);

CREATE INDEX idx_availability_requests_patient ON availability_requests(patient_id);
CREATE INDEX idx_availability_request_pharmacies_pharmacy ON availability_request_pharmacies(pharmacy_id);
