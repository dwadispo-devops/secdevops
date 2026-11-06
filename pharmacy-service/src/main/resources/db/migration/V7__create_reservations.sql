-- US12 / EF21 / RG03 / RG04 : réservation et code de retrait
CREATE TABLE reservations (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id   UUID NOT NULL,
    pharmacy_id  UUID NOT NULL REFERENCES pharmacies(id),
    patient_id   UUID NOT NULL,
    pickup_code  CHAR(6) NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                 CHECK (status IN ('ACTIVE', 'RETIREE', 'ANNULEE', 'EXPIREE')),
    price        NUMERIC(12,2),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at   TIMESTAMPTZ NOT NULL,
    picked_up_at TIMESTAMPTZ,
    -- on ne peut réserver que chez une pharmacie destinataire de la demande
    FOREIGN KEY (request_id, pharmacy_id) REFERENCES availability_request_pharmacies (request_id, pharmacy_id)
);
-- une seule réservation active par demande
CREATE UNIQUE INDEX uq_reservation_active_request ON reservations (request_id) WHERE status = 'ACTIVE';
-- un code actif est unique dans une pharmacie (RG03 : code lié à la pharmacie)
CREATE UNIQUE INDEX uq_reservation_active_code ON reservations (pharmacy_id, pickup_code) WHERE status = 'ACTIVE';
CREATE INDEX idx_reservations_patient_status ON reservations (patient_id, status);
