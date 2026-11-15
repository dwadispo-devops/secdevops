-- Schéma initial d'auth-service.
-- Reprend exactement les tables créées auparavant par Hibernate (ddl-auto=update).
-- Sur une base existante, cette version est marquée « baseline » et n'est pas rejouée.

CREATE TABLE users (
    id            UUID                        NOT NULL,
    created_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    email         VARCHAR(255)                NOT NULL,
    enabled       BOOLEAN                     NOT NULL,
    first_name    VARCHAR(100)                NOT NULL,
    last_name     VARCHAR(100)                NOT NULL,
    password_hash VARCHAR(255)                NOT NULL,
    role          VARCHAR(20)                 NOT NULL,
    CONSTRAINT users_pkey PRIMARY KEY (id),
    CONSTRAINT users_email_key UNIQUE (email),
    CONSTRAINT users_role_check CHECK (role IN ('PATIENT', 'PHARMACIEN', 'ADMIN'))
);

CREATE TABLE refresh_tokens (
    id         UUID                        NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    revoked    BOOLEAN                     NOT NULL,
    token_hash VARCHAR(64)                 NOT NULL,
    user_id    UUID                        NOT NULL,
    CONSTRAINT refresh_tokens_pkey PRIMARY KEY (id),
    CONSTRAINT refresh_tokens_token_hash_key UNIQUE (token_hash)
);
