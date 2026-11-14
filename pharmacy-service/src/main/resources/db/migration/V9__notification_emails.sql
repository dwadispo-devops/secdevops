-- US14 / EF24 : envoi des notifications par e-mail via une boîte d'envoi (outbox)
ALTER TABLE notifications
    ADD COLUMN email_status   VARCHAR(10) NOT NULL DEFAULT 'PENDING'
               CHECK (email_status IN ('PENDING', 'SENT', 'SKIPPED', 'FAILED')),
    ADD COLUMN email_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN emailed_at     TIMESTAMPTZ;
-- Les notifications créées avant cette version ne sont pas envoyées par e-mail.
UPDATE notifications SET email_status = 'SKIPPED';
CREATE INDEX idx_notifications_email_pending ON notifications (created_at) WHERE email_status = 'PENDING';
