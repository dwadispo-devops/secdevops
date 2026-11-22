-- US20 / loi 09-08 : boîte d'envoi (outbox) des événements de compte.
-- Enregistrée dans la même transaction que la suppression du compte, puis publiée dans Kafka
-- (topic dwadispo.comptes) par AccountEventRelay. Aucune donnée personnelle : identifiant et rôle seulement.
CREATE TABLE account_events (
    id           UUID                        NOT NULL,
    type         VARCHAR(30)                 NOT NULL,
    user_id      UUID                        NOT NULL,
    role         VARCHAR(20)                 NOT NULL,
    created_at   TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT account_events_pkey PRIMARY KEY (id)
);

-- Événements pas encore publiés, dans l'ordre d'arrivée
CREATE INDEX idx_account_events_pending ON account_events (created_at) WHERE published_at IS NULL;
