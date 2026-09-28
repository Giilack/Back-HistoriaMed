-- Documentos clínicos del paciente (PDF o imágenes). Ver plan.md, sección 5.5.
-- El archivo se guarda en el almacenamiento (disco local o R2); aquí solo sus metadatos.
-- Los estados de procesamiento con IA (PROCESANDO, PENDIENTE_REVISION...) se agregarán cuando se active la IA.

CREATE TABLE documentos (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    paciente_id         BIGINT        NOT NULL REFERENCES pacientes (id),
    cita_id             BIGINT        REFERENCES citas (id),
    tipo                VARCHAR(20)   NOT NULL,
    descripcion         VARCHAR(200),
    fecha_documento     DATE,

    nombre_original     VARCHAR(255)  NOT NULL,
    content_type        VARCHAR(50)   NOT NULL,
    tamanio_bytes       BIGINT        NOT NULL,
    -- Clave interna en el almacenamiento (nunca el nombre que envió el usuario)
    clave_almacenamiento VARCHAR(200) NOT NULL,
    -- Huella SHA-256 del contenido: permite comprobar que el archivo no fue alterado
    sha256              VARCHAR(64)   NOT NULL,

    estado              VARCHAR(12)   NOT NULL DEFAULT 'RECIBIDO',
    motivo_anulacion    VARCHAR(200),
    subido_por          BIGINT        NOT NULL REFERENCES usuarios (id),
    creado_en           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    actualizado_en      TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT uk_documentos_clave UNIQUE (clave_almacenamiento),
    CONSTRAINT ck_documentos_tipo CHECK (tipo IN ('LABORATORIO', 'RECETA', 'INFORME_MEDICO', 'EPICRISIS',
                                                  'IMAGENOLOGIA', 'REFERENCIA', 'OTRO')),
    CONSTRAINT ck_documentos_content_type CHECK (content_type IN ('application/pdf', 'image/jpeg', 'image/png')),
    CONSTRAINT ck_documentos_tamanio CHECK (tamanio_bytes > 0 AND tamanio_bytes <= 10485760),
    CONSTRAINT ck_documentos_estado CHECK (estado IN ('RECIBIDO', 'ANULADO')),
    CONSTRAINT ck_documentos_anulacion CHECK ((estado = 'ANULADO') = (motivo_anulacion IS NOT NULL))
);

CREATE INDEX ix_documentos_paciente ON documentos (paciente_id, creado_en DESC);
