-- Revisión de documentos (plan.md, sección 5.5, fase 8): los datos de un documento se llenan a mano (o, más
-- adelante, los propone la IA), el médico los valida y recién entonces pasan a la historia clínica.

-- Destino de los datos validados -------------------------------------------------------------------------------

-- Antecedentes del paciente: personales, familiares, quirúrgicos, diagnósticos previos y medicación habitual.
-- No se borran: si fue un error, se inactivan con un motivo.
CREATE TABLE antecedentes (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    paciente_id          BIGINT        NOT NULL REFERENCES pacientes (id),
    tipo                 VARCHAR(20)   NOT NULL,
    descripcion          VARCHAR(300)  NOT NULL,
    detalle              VARCHAR(300),
    -- Fecha del hecho (por ejemplo, de la cirugía o del diagnóstico), si se conoce
    fecha                DATE,
    cie_codigo           VARCHAR(8)    REFERENCES cie10 (codigo),
    medicamento_id       BIGINT        REFERENCES medicamentos (id),
    -- Documento del que salió el dato (nulo si se registró directamente)
    documento_id         BIGINT        REFERENCES documentos (id),
    activo               BOOLEAN       NOT NULL DEFAULT TRUE,
    motivo_inactivacion  VARCHAR(200),
    registrado_por       BIGINT        NOT NULL REFERENCES usuarios (id),
    creado_en            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    actualizado_en       TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT ck_antecedentes_tipo CHECK (tipo IN ('PERSONAL', 'FAMILIAR', 'QUIRURGICO', 'DIAGNOSTICO_PREVIO',
                                                    'MEDICACION_HABITUAL', 'OTRO')),
    CONSTRAINT ck_antecedentes_inactivacion CHECK (activo OR motivo_inactivacion IS NOT NULL)
);

CREATE INDEX ix_antecedentes_paciente ON antecedentes (paciente_id);

-- Resultados de exámenes de laboratorio. El valor es texto: hay resultados no numéricos ("Negativo", "1/160").
CREATE TABLE resultados_laboratorio (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    paciente_id          BIGINT        NOT NULL REFERENCES pacientes (id),
    examen               VARCHAR(150)  NOT NULL,
    valor                VARCHAR(60)   NOT NULL,
    unidad               VARCHAR(30),
    rango_referencia     VARCHAR(60),
    -- Fecha del examen (la del documento), no la de registro
    fecha                DATE,
    documento_id         BIGINT        REFERENCES documentos (id),
    activo               BOOLEAN       NOT NULL DEFAULT TRUE,
    motivo_inactivacion  VARCHAR(200),
    registrado_por       BIGINT        NOT NULL REFERENCES usuarios (id),
    creado_en            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    actualizado_en       TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT ck_resultados_laboratorio_inactivacion CHECK (activo OR motivo_inactivacion IS NOT NULL)
);

CREATE INDEX ix_resultados_laboratorio_paciente ON resultados_laboratorio (paciente_id, fecha DESC);

-- Revisión ----------------------------------------------------------------------------------------------------

CREATE TABLE extracciones (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    documento_id     BIGINT        NOT NULL REFERENCES documentos (id),
    paciente_id      BIGINT        NOT NULL REFERENCES pacientes (id),
    -- MANUAL: los datos los escribe una persona. IA: los propone el servicio de IA (fase 11).
    origen           VARCHAR(10)   NOT NULL DEFAULT 'MANUAL',
    estado           VARCHAR(20)   NOT NULL DEFAULT 'PENDIENTE_REVISION',
    -- Texto completo leído del documento (lo llenará la IA)
    texto_completo   TEXT,
    motivo_rechazo   VARCHAR(300),
    creado_por       BIGINT        NOT NULL REFERENCES usuarios (id),
    revisado_por     BIGINT        REFERENCES usuarios (id),
    revisado_en      TIMESTAMPTZ,
    creado_en        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    actualizado_en   TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT ck_extracciones_origen CHECK (origen IN ('MANUAL', 'IA')),
    CONSTRAINT ck_extracciones_estado CHECK (estado IN ('PENDIENTE_REVISION', 'VALIDADA', 'RECHAZADA')),
    CONSTRAINT ck_extracciones_rechazo CHECK ((estado = 'RECHAZADA') = (motivo_rechazo IS NOT NULL)),
    -- Una revisión cerrada (validada o rechazada) siempre tiene quién y cuándo
    CONSTRAINT ck_extracciones_revision
        CHECK ((estado = 'PENDIENTE_REVISION') = (revisado_por IS NULL AND revisado_en IS NULL))
);

-- Un documento tiene como máximo una revisión vigente; si se rechazó, se puede empezar otra
CREATE UNIQUE INDEX uk_extracciones_documento_vigente ON extracciones (documento_id) WHERE estado <> 'RECHAZADA';
CREATE INDEX ix_extracciones_paciente ON extracciones (paciente_id);

-- Cada dato del documento, clasificado por categoría. Según la categoría se usan unas columnas u otras
-- (lo valida ExtraccionService).
CREATE TABLE extraccion_items (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    extraccion_id     BIGINT        NOT NULL REFERENCES extracciones (id),
    categoria         VARCHAR(15)   NOT NULL,
    estado            VARCHAR(12)   NOT NULL DEFAULT 'PROPUESTO',
    -- Sustancia (alergia), diagnóstico, medicamento, examen (laboratorio) o el antecedente
    descripcion       VARCHAR(300)  NOT NULL,
    -- Reacción (alergia), dosis o pauta (medicamento) o una nota
    detalle           VARCHAR(300),
    fecha             DATE,
    tipo_alergia      VARCHAR(12),
    gravedad          VARCHAR(10),
    cie_codigo        VARCHAR(8)    REFERENCES cie10 (codigo),
    medicamento_id    BIGINT        REFERENCES medicamentos (id),
    valor             VARCHAR(60),
    unidad            VARCHAR(30),
    rango_referencia  VARCHAR(60),
    tipo_antecedente  VARCHAR(20),
    -- De dónde salió el dato en el documento, para que el médico lo compare
    fragmento_origen  VARCHAR(500),
    pagina            INTEGER,
    -- El médico cambió un dato propuesto por otra persona o por la IA
    corregido         BOOLEAN       NOT NULL DEFAULT FALSE,
    creado_por        BIGINT        NOT NULL REFERENCES usuarios (id),
    creado_en         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    actualizado_en    TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT ck_extraccion_items_categoria CHECK (categoria IN ('ALERGIA', 'DIAGNOSTICO', 'MEDICAMENTO',
                                                                  'LABORATORIO', 'ANTECEDENTE', 'OTRO')),
    CONSTRAINT ck_extraccion_items_estado CHECK (estado IN ('PROPUESTO', 'ACEPTADO', 'CORREGIDO', 'DESCARTADO')),
    CONSTRAINT ck_extraccion_items_tipo_alergia
        CHECK (tipo_alergia IS NULL OR tipo_alergia IN ('MEDICAMENTO', 'ALIMENTO', 'AMBIENTAL', 'OTRO')),
    CONSTRAINT ck_extraccion_items_gravedad CHECK (gravedad IS NULL OR gravedad IN ('LEVE', 'MODERADA', 'SEVERA')),
    CONSTRAINT ck_extraccion_items_tipo_antecedente
        CHECK (tipo_antecedente IS NULL OR tipo_antecedente IN ('PERSONAL', 'FAMILIAR', 'QUIRURGICO', 'OTRO')),
    CONSTRAINT ck_extraccion_items_pagina CHECK (pagina IS NULL OR pagina >= 1)
);

CREATE INDEX ix_extraccion_items_extraccion ON extraccion_items (extraccion_id);
