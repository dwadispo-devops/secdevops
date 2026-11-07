-- US14 / EF24 : notifications dans le site (demandes, réponses, réservations, retraits, expirations)
-- ENF05 : les messages ne contiennent jamais le nom des médicaments (donnée de santé).
CREATE TABLE notifications (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL,
    type       VARCHAR(40)  NOT NULL,
    title      VARCHAR(150) NOT NULL,
    message    VARCHAR(500) NOT NULL,
    link       VARCHAR(300),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at    TIMESTAMPTZ
);
CREATE INDEX idx_notifications_user_created ON notifications (user_id, created_at DESC);
CREATE INDEX idx_notifications_user_unread  ON notifications (user_id) WHERE read_at IS NULL;
