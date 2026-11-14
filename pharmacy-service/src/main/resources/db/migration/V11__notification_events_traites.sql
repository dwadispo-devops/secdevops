-- US14 (Kafka) : idempotence du consommateur.
-- Un événement déjà traité est ignoré, même si Kafka le livre deux fois.
CREATE TABLE notification_events_traites (
    event_id  UUID PRIMARY KEY,
    traite_le TIMESTAMPTZ NOT NULL DEFAULT now()
);
