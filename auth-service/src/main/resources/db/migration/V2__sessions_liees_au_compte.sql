-- US20 / loi 09-08 : les sessions (refresh tokens) disparaissent avec le compte.
-- 1. On retire d'éventuelles sessions orphelines (compte déjà supprimé).
DELETE FROM refresh_tokens rt
WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.id = rt.user_id);

-- 2. Lien vers le compte, avec suppression en cascade.
ALTER TABLE refresh_tokens
    ADD CONSTRAINT fk_refresh_tokens_user
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

-- 3. Index pour retrouver vite les sessions d'un utilisateur (révocation, déconnexion partout).
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
