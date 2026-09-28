-- Pacientes: datos de filiación y financiamiento. Ver plan.md, secciones 5.1 y 5.2.

-- Correlativo del número de historia clínica (HC-000001, HC-000002, ...)
CREATE SEQUENCE seq_numero_hc START 1;

CREATE TABLE pacientes (
    id                              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    numero_hc                       VARCHAR(20)  NOT NULL,

    -- Identificación
    tipo_documento                  VARCHAR(20)  NOT NULL,
    numero_documento                VARCHAR(20),
    nombres                         VARCHAR(100) NOT NULL,
    apellido_paterno                VARCHAR(100) NOT NULL,
    apellido_materno                VARCHAR(100),
    fecha_nacimiento                DATE         NOT NULL,
    sexo                            VARCHAR(10)  NOT NULL,

    -- Contacto
    telefono                        VARCHAR(15),
    email                           VARCHAR(150),
    direccion                       VARCHAR(200),
    contacto_emergencia_nombre      VARCHAR(150),
    contacto_emergencia_telefono    VARCHAR(15),
    contacto_emergencia_parentesco  VARCHAR(30),

    -- Financiamiento (uno vigente por paciente)
    tipo_financiamiento             VARCHAR(20)  NOT NULL DEFAULT 'PARTICULAR',
    seguro_numero_afiliacion        VARCHAR(30),
    seguro_plan                     VARCHAR(60),
    seguro_estado                   VARCHAR(20),
    seguro_verificado_en            TIMESTAMPTZ,
    orientado_afiliacion_sis        BOOLEAN      NOT NULL DEFAULT FALSE,

    activo                          BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_por                      BIGINT       REFERENCES usuarios (id),
    creado_en                       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    actualizado_en                  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uk_pacientes_numero_hc UNIQUE (numero_hc),
    CONSTRAINT ck_pacientes_tipo_documento
        CHECK (tipo_documento IN ('DNI', 'CARNET_EXTRANJERIA', 'PASAPORTE', 'SIN_DOCUMENTO')),
    -- Sin documento => sin número; con documento => número obligatorio
    CONSTRAINT ck_pacientes_documento
        CHECK ((tipo_documento = 'SIN_DOCUMENTO') = (numero_documento IS NULL)),
    CONSTRAINT ck_pacientes_dni
        CHECK (tipo_documento <> 'DNI' OR numero_documento ~ '^[0-9]{8}$'),
    CONSTRAINT ck_pacientes_sexo CHECK (sexo IN ('MASCULINO', 'FEMENINO')),
    CONSTRAINT ck_pacientes_financiamiento
        CHECK (tipo_financiamiento IN ('SIS', 'ESSALUD', 'PRIVADO', 'PARTICULAR')),
    CONSTRAINT ck_pacientes_seguro_estado
        CHECK (seguro_estado IS NULL OR seguro_estado IN ('NO_VERIFICADO', 'ACTIVO', 'INACTIVO')),
    -- Solo quien no tiene seguro puede estar "orientado a afiliarse al SIS"
    CONSTRAINT ck_pacientes_orientado_sis
        CHECK (NOT orientado_afiliacion_sis OR tipo_financiamiento = 'PARTICULAR')
);

-- Un mismo documento no puede pertenecer a dos pacientes
CREATE UNIQUE INDEX uk_pacientes_documento
    ON pacientes (tipo_documento, numero_documento)
    WHERE numero_documento IS NOT NULL;

-- Búsqueda por apellidos
CREATE INDEX ix_pacientes_apellidos ON pacientes (lower(apellido_paterno), lower(apellido_materno));
