-- Usuarios del sistema (personal del establecimiento). Ver plan.md, sección 3.
CREATE TABLE usuarios (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username               VARCHAR(30)  NOT NULL,
    password_hash          VARCHAR(100) NOT NULL,
    nombres                VARCHAR(100) NOT NULL,
    apellidos              VARCHAR(100) NOT NULL,
    dni                    VARCHAR(8)   NOT NULL,
    email                  VARCHAR(150),
    rol                    VARCHAR(20)  NOT NULL,
    cmp                    VARCHAR(10),
    activo                 BOOLEAN      NOT NULL DEFAULT TRUE,
    debe_cambiar_password  BOOLEAN      NOT NULL DEFAULT TRUE,
    intentos_fallidos      INTEGER      NOT NULL DEFAULT 0,
    bloqueado_hasta        TIMESTAMPTZ,
    ultimo_acceso          TIMESTAMPTZ,
    creado_en              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    actualizado_en         TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uk_usuarios_username UNIQUE (username),
    CONSTRAINT uk_usuarios_dni      UNIQUE (dni),
    CONSTRAINT uk_usuarios_email    UNIQUE (email),
    CONSTRAINT ck_usuarios_rol      CHECK (rol IN ('ADMIN', 'ADMISION', 'TRIAJE', 'MEDICO')),
    CONSTRAINT ck_usuarios_dni      CHECK (dni ~ '^[0-9]{8}$'),
    -- Todo médico debe tener su número de colegiatura (CMP)
    CONSTRAINT ck_usuarios_cmp_medico CHECK (rol <> 'MEDICO' OR cmp IS NOT NULL)
);

CREATE INDEX ix_usuarios_rol ON usuarios (rol);
