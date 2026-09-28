-- Triaje (signos vitales, alertas y prioridad) y alergias del paciente. Ver plan.md, sección 5.3.

-- Prioridad asignada en triaje; ordena la cola del médico
ALTER TABLE citas ADD COLUMN prioridad VARCHAR(12);
ALTER TABLE citas ADD CONSTRAINT ck_citas_prioridad
    CHECK (prioridad IS NULL OR prioridad IN ('NORMAL', 'PREFERENTE', 'URGENTE'));

CREATE TABLE triajes (
    id                        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cita_id                   BIGINT        NOT NULL REFERENCES citas (id),
    paciente_id               BIGINT        NOT NULL REFERENCES pacientes (id),
    registrado_por            BIGINT        NOT NULL REFERENCES usuarios (id),
    fecha_hora                TIMESTAMPTZ   NOT NULL,

    motivo_consulta           VARCHAR(500)  NOT NULL,

    -- Signos vitales (los opcionales pueden no tomarse, por ejemplo la presión en lactantes)
    presion_sistolica         INTEGER,
    presion_diastolica        INTEGER,
    frecuencia_cardiaca       INTEGER       NOT NULL,
    frecuencia_respiratoria   INTEGER,
    temperatura               NUMERIC(4, 1) NOT NULL,
    saturacion                INTEGER       NOT NULL,
    peso                      NUMERIC(5, 2) NOT NULL,
    talla                     NUMERIC(4, 1),
    imc                       NUMERIC(4, 1),
    perimetro_abdominal       NUMERIC(4, 1),

    -- Condiciones que dan atención preferente
    gestante                  BOOLEAN       NOT NULL DEFAULT FALSE,
    discapacidad              BOOLEAN       NOT NULL DEFAULT FALSE,

    prioridad_sugerida        VARCHAR(12)   NOT NULL,
    prioridad                 VARCHAR(12)   NOT NULL,
    -- Obligatoria si se asigna una prioridad menor a la sugerida por el sistema
    justificacion_prioridad   VARCHAR(300),
    observaciones             VARCHAR(500),

    creado_en                 TIMESTAMPTZ   NOT NULL DEFAULT now(),
    actualizado_en            TIMESTAMPTZ   NOT NULL DEFAULT now(),

    -- Un triaje por cita
    CONSTRAINT uk_triajes_cita UNIQUE (cita_id),
    CONSTRAINT ck_triajes_presion CHECK ((presion_sistolica IS NULL) = (presion_diastolica IS NULL)),
    CONSTRAINT ck_triajes_prioridad_sugerida CHECK (prioridad_sugerida IN ('NORMAL', 'PREFERENTE', 'URGENTE')),
    CONSTRAINT ck_triajes_prioridad CHECK (prioridad IN ('NORMAL', 'PREFERENTE', 'URGENTE'))
);

CREATE INDEX ix_triajes_paciente ON triajes (paciente_id, fecha_hora DESC);

-- Alertas calculadas al registrar el triaje (se guardan tal como se mostraron, aunque las reglas cambien después)
CREATE TABLE triaje_alertas (
    triaje_id   BIGINT       NOT NULL REFERENCES triajes (id),
    codigo      VARCHAR(40)  NOT NULL,
    severidad   VARCHAR(12)  NOT NULL,
    mensaje     VARCHAR(200) NOT NULL,

    CONSTRAINT ck_triaje_alertas_severidad CHECK (severidad IN ('ADVERTENCIA', 'CRITICA'))
);

CREATE INDEX ix_triaje_alertas_triaje ON triaje_alertas (triaje_id);

-- Alergias del paciente. No se borran: si fue un error de registro, se inactivan con un motivo.
CREATE TABLE alergias (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    paciente_id          BIGINT        NOT NULL REFERENCES pacientes (id),
    tipo                 VARCHAR(12)   NOT NULL,
    sustancia            VARCHAR(100)  NOT NULL,
    reaccion             VARCHAR(200),
    gravedad             VARCHAR(10)   NOT NULL,
    activa               BOOLEAN       NOT NULL DEFAULT TRUE,
    motivo_inactivacion  VARCHAR(200),
    registrado_por       BIGINT        NOT NULL REFERENCES usuarios (id),
    creado_en            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    actualizado_en       TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT ck_alergias_tipo CHECK (tipo IN ('MEDICAMENTO', 'ALIMENTO', 'AMBIENTAL', 'OTRO')),
    CONSTRAINT ck_alergias_gravedad CHECK (gravedad IN ('LEVE', 'MODERADA', 'SEVERA')),
    CONSTRAINT ck_alergias_inactivacion CHECK (activa OR motivo_inactivacion IS NOT NULL)
);

CREATE INDEX ix_alergias_paciente ON alergias (paciente_id);
-- La misma sustancia no se registra dos veces como alergia activa del mismo paciente
CREATE UNIQUE INDEX uk_alergias_activa ON alergias (paciente_id, lower(sustancia)) WHERE activa;
