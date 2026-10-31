-- Importación masiva de historias en PDF (islas de datos): el documento aún no tiene paciente. La IA propone los datos
-- personales y clínicos, y Admisión aprueba: entonces se crea o se ubica al paciente y se registra su historia.
CREATE TABLE importaciones (
    id                     BIGSERIAL     PRIMARY KEY,
    nombre_original        VARCHAR(255)  NOT NULL,
    content_type           VARCHAR(100)  NOT NULL,
    tamanio_bytes          BIGINT        NOT NULL,
    sha256                 VARCHAR(64)   NOT NULL,
    clave_almacenamiento   VARCHAR(200)  NOT NULL UNIQUE,
    estado                 VARCHAR(20)   NOT NULL DEFAULT 'EN_COLA',
    intentos               INT           NOT NULL DEFAULT 0,
    error                  VARCHAR(500),
    modelo                 VARCHAR(60),
    -- Datos personales y clínicos propuestos, cifrados con AES-256-GCM. Se borran al aprobar o rechazar
    datos_cifrados         TEXT,
    paciente_id            BIGINT        REFERENCES pacientes (id),
    documento_id           BIGINT        REFERENCES documentos (id),
    subido_por             BIGINT        NOT NULL REFERENCES usuarios (id),
    revisado_por           BIGINT        REFERENCES usuarios (id),
    revisado_en            TIMESTAMPTZ,
    motivo_rechazo         VARCHAR(300),
    creado_en              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    actualizado_en         TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT ck_importaciones_estado
        CHECK (estado IN ('EN_COLA', 'PROCESANDO', 'LISTA', 'ERROR', 'APROBADA', 'RECHAZADA'))
);

CREATE INDEX ix_importaciones_estado ON importaciones (estado, id);

-- El mismo archivo no se procesa dos veces mientras siga vigente
CREATE UNIQUE INDEX ux_importaciones_archivo_vigente ON importaciones (sha256) WHERE estado <> 'RECHAZADA';
