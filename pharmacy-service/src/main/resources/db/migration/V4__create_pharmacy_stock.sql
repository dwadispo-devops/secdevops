CREATE TABLE pharmacy_stock (
    pharmacy_id UUID NOT NULL REFERENCES pharmacies(id),
    medication_code VARCHAR(13) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('AVAILABLE', 'OUT_OF_STOCK')),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (pharmacy_id, medication_code)
);
