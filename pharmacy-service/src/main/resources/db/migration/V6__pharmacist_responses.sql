ALTER TABLE availability_requests ADD COLUMN prescription_id UUID;
ALTER TABLE availability_request_pharmacies
    ADD COLUMN price NUMERIC(12,2),
    ADD COLUMN comment VARCHAR(1000),
    ADD COLUMN responded_at TIMESTAMPTZ,
    ADD COLUMN responded_by UUID,
    ADD CONSTRAINT valid_pharmacy_response CHECK (
        (status = 'PENDING' AND price IS NULL AND responded_at IS NULL AND responded_by IS NULL)
        OR (status = 'AVAILABLE' AND price IS NOT NULL AND price >= 0 AND responded_at IS NOT NULL AND responded_by IS NOT NULL)
        OR (status = 'UNAVAILABLE' AND price IS NULL AND responded_at IS NOT NULL AND responded_by IS NOT NULL)
    );
