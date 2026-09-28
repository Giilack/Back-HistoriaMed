-- Consultorios (catálogo del ADMIN) y citas/turnos con su flujo de estados. Ver plan.md, sección 4.

CREATE TABLE consultorios (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre          VARCHAR(60)  NOT NULL,
    especialidad    VARCHAR(60)  NOT NULL,
    activo          BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    actualizado_en  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uk_consultorios_nombre UNIQUE (nombre)
);

-- Consultorios iniciales de un establecimiento de primer nivel (se pueden editar o desactivar)
INSERT INTO consultorios (nombre, especialidad) VALUES
    ('Consultorio 1', 'Medicina General'),
    ('Consultorio 2', 'Medicina General'),
    ('Consultorio 3', 'Pediatría'),
    ('Consultorio 4', 'Ginecología y Obstetricia');

CREATE TABLE citas (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    paciente_id           BIGINT       NOT NULL REFERENCES pacientes (id),
    medico_id             BIGINT       NOT NULL REFERENCES usuarios (id),
    consultorio_id        BIGINT       NOT NULL REFERENCES consultorios (id),
    fecha                 DATE         NOT NULL,
    -- NULL cuando el paciente llegó sin cita
    hora                  TIME,
    sin_cita              BOOLEAN      NOT NULL DEFAULT FALSE,
    motivo                VARCHAR(200),
    estado                VARCHAR(20)  NOT NULL,
    -- Orden de llegada del día en el consultorio; se asigna al registrar la llegada
    numero_turno          INTEGER,

    -- Hora de cada cambio de estado (permite medir tiempos de espera)
    llegada_en            TIMESTAMPTZ,
    triaje_en             TIMESTAMPTZ,
    consulta_inicio_en    TIMESTAMPTZ,
    atendido_en           TIMESTAMPTZ,
    cancelada_en          TIMESTAMPTZ,
    motivo_cancelacion    VARCHAR(200),

    creado_por            BIGINT       REFERENCES usuarios (id),
    creado_en             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    actualizado_en        TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_citas_estado CHECK (estado IN ('PROGRAMADA', 'EN_ESPERA_TRIAJE', 'EN_ESPERA_CONSULTA',
                                                 'EN_CONSULTA', 'ATENDIDO', 'CANCELADA', 'NO_SE_PRESENTO')),
    -- Una cita programada tiene hora; una llegada sin cita no
    CONSTRAINT ck_citas_hora CHECK (sin_cita = (hora IS NULL))
);

-- Dos pacientes no pueden tener el mismo número de turno el mismo día en el mismo consultorio
CREATE UNIQUE INDEX uk_citas_turno ON citas (fecha, consultorio_id, numero_turno) WHERE numero_turno IS NOT NULL;

CREATE INDEX ix_citas_fecha_consultorio ON citas (fecha, consultorio_id);
CREATE INDEX ix_citas_medico_fecha ON citas (medico_id, fecha);
CREATE INDEX ix_citas_paciente ON citas (paciente_id);
