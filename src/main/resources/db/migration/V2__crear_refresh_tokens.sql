-- Refresh tokens de sesión. Se guarda solo el hash SHA-256, nunca el token en claro.
CREATE TABLE refresh_tokens (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id  BIGINT      NOT NULL REFERENCES usuarios (id),
    token_hash  VARCHAR(64) NOT NULL,
    expira_en   TIMESTAMPTZ NOT NULL,
    revocado    BOOLEAN     NOT NULL DEFAULT FALSE,
    creado_en   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX ix_refresh_tokens_usuario ON refresh_tokens (usuario_id);
