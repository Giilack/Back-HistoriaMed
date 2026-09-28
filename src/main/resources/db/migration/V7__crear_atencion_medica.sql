-- Atención médica: catálogos (CIE-10 y medicamentos), atención, diagnósticos, receta y adendas.
-- Ver plan.md, sección 5.4.

-- ---------------------------------------------------------------------------------------------
-- Catálogo CIE-10: subconjunto de diagnósticos frecuentes en el primer nivel de atención.
-- No es el catálogo completo (más de 14 000 códigos): se puede ampliar con otra migración.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE cie10 (
    codigo       VARCHAR(8)   PRIMARY KEY,
    descripcion  VARCHAR(200) NOT NULL
);

INSERT INTO cie10 (codigo, descripcion) VALUES
    ('A09',   'Diarrea y gastroenteritis de presunto origen infeccioso'),
    ('A06.9', 'Amebiasis, no especificada'),
    ('A16.2', 'Tuberculosis de pulmón, sin mención de confirmación bacteriológica o histológica'),
    ('B01.9', 'Varicela sin complicaciones'),
    ('B35.4', 'Tiña del cuerpo [tinea corporis]'),
    ('B37.3', 'Candidiasis de la vulva y de la vagina'),
    ('B82.9', 'Parasitosis intestinal, sin otra especificación'),
    ('B86',   'Escabiosis'),
    ('D50.9', 'Anemia por deficiencia de hierro sin otra especificación'),
    ('D64.9', 'Anemia de tipo no especificado'),
    ('E03.9', 'Hipotiroidismo, no especificado'),
    ('E11.9', 'Diabetes mellitus no insulinodependiente, sin mención de complicación'),
    ('E14.9', 'Diabetes mellitus, no especificada, sin mención de complicación'),
    ('E66.9', 'Obesidad, no especificada'),
    ('E78.5', 'Hiperlipidemia no especificada'),
    ('E86',   'Depleción del volumen'),
    ('F32.9', 'Episodio depresivo, no especificado'),
    ('F41.1', 'Trastorno de ansiedad generalizada'),
    ('F41.9', 'Trastorno de ansiedad, no especificado'),
    ('G43.9', 'Migraña, no especificada'),
    ('G44.2', 'Cefalea debida a tensión'),
    ('H10.9', 'Conjuntivitis, no especificada'),
    ('H66.9', 'Otitis media, no especificada'),
    ('I10',   'Hipertensión esencial (primaria)'),
    ('I83.9', 'Venas varicosas de los miembros inferiores sin úlcera ni inflamación'),
    ('J00',   'Rinofaringitis aguda [resfriado común]'),
    ('J01.9', 'Sinusitis aguda, no especificada'),
    ('J02.9', 'Faringitis aguda, no especificada'),
    ('J03.9', 'Amigdalitis aguda, no especificada'),
    ('J06.9', 'Infección aguda de las vías respiratorias superiores, no especificada'),
    ('J11.1', 'Influenza con otras manifestaciones respiratorias, virus no identificado'),
    ('J18.9', 'Neumonía, no especificada'),
    ('J20.9', 'Bronquitis aguda, no especificada'),
    ('J21.9', 'Bronquiolitis aguda, no especificada'),
    ('J30.4', 'Rinitis alérgica, no especificada'),
    ('J44.9', 'Enfermedad pulmonar obstructiva crónica, no especificada'),
    ('J45.9', 'Asma, no especificada'),
    ('K02.9', 'Caries dental, no especificada'),
    ('K21.9', 'Enfermedad del reflujo gastroesofágico sin esofagitis'),
    ('K29.7', 'Gastritis, no especificada'),
    ('K30',   'Dispepsia'),
    ('K52.9', 'Colitis y gastroenteritis no infecciosas, no especificadas'),
    ('K59.0', 'Constipación'),
    ('K80.2', 'Cálculo de la vesícula biliar sin colecistitis'),
    ('L01.0', 'Impétigo [cualquier sitio anatómico] [cualquier organismo]'),
    ('L20.9', 'Dermatitis atópica, de tipo no especificado'),
    ('L23.9', 'Dermatitis alérgica de contacto, de causa no especificada'),
    ('L30.9', 'Dermatitis, no especificada'),
    ('L50.9', 'Urticaria, no especificada'),
    ('L70.0', 'Acné vulgar'),
    ('M17.9', 'Gonartrosis, no especificada'),
    ('M25.5', 'Dolor en articulación'),
    ('M54.2', 'Cervicalgia'),
    ('M54.5', 'Lumbago no especificado'),
    ('M79.1', 'Mialgia'),
    ('N30.0', 'Cistitis aguda'),
    ('N39.0', 'Infección de vías urinarias, sitio no especificado'),
    ('N76.0', 'Vaginitis aguda'),
    ('N94.6', 'Dismenorrea, no especificada'),
    ('N95.1', 'Estados menopáusicos y climatéricos femeninos'),
    ('O21.0', 'Hiperemesis gravídica leve'),
    ('O23.4', 'Infección no especificada de las vías urinarias en el embarazo'),
    ('R05',   'Tos'),
    ('R10.4', 'Otros dolores abdominales y los no especificados'),
    ('R11',   'Náusea y vómito'),
    ('R42',   'Mareo y desvanecimiento'),
    ('R50.9', 'Fiebre, no especificada'),
    ('R51',   'Cefalea'),
    ('R53',   'Malestar y fatiga'),
    ('S93.4', 'Esguince y torcedura del tobillo'),
    ('T78.4', 'Alergia no especificada'),
    ('Z00.0', 'Examen médico general'),
    ('Z00.1', 'Control de salud de rutina del niño'),
    ('Z30.0', 'Consejo y asesoramiento general sobre la anticoncepción'),
    ('Z34.9', 'Supervisión de embarazo normal no especificado');

-- ---------------------------------------------------------------------------------------------
-- Catálogo de medicamentos: lista reducida de medicamentos esenciales de uso frecuente.
-- principio_activo (sin tildes, en minúsculas; varios separados por " + ") y grupo farmacológico
-- se usan para la alerta de alergias (por ejemplo, amoxicilina pertenece a PENICILINAS).
-- ---------------------------------------------------------------------------------------------
CREATE TABLE medicamentos (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre               VARCHAR(120) NOT NULL,
    principio_activo     VARCHAR(120) NOT NULL,
    concentracion        VARCHAR(40)  NOT NULL,
    forma_farmaceutica   VARCHAR(40)  NOT NULL,
    grupo                VARCHAR(30),
    activo               BOOLEAN      NOT NULL DEFAULT TRUE,

    CONSTRAINT uk_medicamentos UNIQUE (principio_activo, concentracion, forma_farmaceutica)
);

INSERT INTO medicamentos (nombre, principio_activo, concentracion, forma_farmaceutica, grupo) VALUES
    ('Paracetamol',                         'paracetamol',                          '500 mg',            'Tableta',            NULL),
    ('Paracetamol',                         'paracetamol',                          '120 mg/5 mL',       'Jarabe',             NULL),
    ('Ibuprofeno',                          'ibuprofeno',                           '400 mg',            'Tableta',            'AINES'),
    ('Ibuprofeno',                          'ibuprofeno',                           '100 mg/5 mL',       'Suspensión',         'AINES'),
    ('Naproxeno',                           'naproxeno',                            '550 mg',            'Tableta',            'AINES'),
    ('Diclofenaco',                         'diclofenaco',                          '50 mg',             'Tableta',            'AINES'),
    ('Ácido acetilsalicílico',              'acido acetilsalicilico',               '100 mg',            'Tableta',            'AINES'),
    ('Tramadol',                            'tramadol',                             '50 mg',             'Cápsula',            'OPIOIDES'),
    ('Amoxicilina',                         'amoxicilina',                          '500 mg',            'Cápsula',            'PENICILINAS'),
    ('Amoxicilina',                         'amoxicilina',                          '250 mg/5 mL',       'Suspensión',         'PENICILINAS'),
    ('Amoxicilina + ácido clavulánico',     'amoxicilina + acido clavulanico',      '500 mg/125 mg',     'Tableta',            'PENICILINAS'),
    ('Dicloxacilina',                       'dicloxacilina',                        '500 mg',            'Cápsula',            'PENICILINAS'),
    ('Penicilina G benzatínica',            'penicilina g benzatinica',             '2 400 000 UI',      'Inyectable',         'PENICILINAS'),
    ('Cefalexina',                          'cefalexina',                           '500 mg',            'Cápsula',            'CEFALOSPORINAS'),
    ('Azitromicina',                        'azitromicina',                         '500 mg',            'Tableta',            'MACROLIDOS'),
    ('Azitromicina',                        'azitromicina',                         '200 mg/5 mL',       'Suspensión',         'MACROLIDOS'),
    ('Claritromicina',                      'claritromicina',                       '500 mg',            'Tableta',            'MACROLIDOS'),
    ('Ciprofloxacino',                      'ciprofloxacino',                       '500 mg',            'Tableta',            'QUINOLONAS'),
    ('Sulfametoxazol + trimetoprima',       'sulfametoxazol + trimetoprima',        '800 mg/160 mg',     'Tableta',            'SULFONAMIDAS'),
    ('Nitrofurantoína',                     'nitrofurantoina',                      '100 mg',            'Cápsula',            NULL),
    ('Metronidazol',                        'metronidazol',                         '500 mg',            'Tableta',            NULL),
    ('Albendazol',                          'albendazol',                           '400 mg',            'Tableta',            NULL),
    ('Mebendazol',                          'mebendazol',                           '100 mg',            'Tableta',            NULL),
    ('Fluconazol',                          'fluconazol',                           '150 mg',            'Cápsula',            NULL),
    ('Clotrimazol',                         'clotrimazol',                          '1 %',               'Crema',              NULL),
    ('Clotrimazol',                         'clotrimazol',                          '500 mg',            'Óvulo',              NULL),
    ('Aciclovir',                           'aciclovir',                            '400 mg',            'Tableta',            NULL),
    ('Permetrina',                          'permetrina',                           '5 %',               'Crema',              NULL),
    ('Clorfenamina',                        'clorfenamina',                         '4 mg',              'Tableta',            NULL),
    ('Loratadina',                          'loratadina',                           '10 mg',             'Tableta',            NULL),
    ('Cetirizina',                          'cetirizina',                           '10 mg',             'Tableta',            NULL),
    ('Salbutamol',                          'salbutamol',                           '100 mcg/dosis',     'Inhalador',          NULL),
    ('Beclometasona',                       'beclometasona',                        '250 mcg/dosis',     'Inhalador',          'CORTICOIDES'),
    ('Prednisona',                          'prednisona',                           '20 mg',             'Tableta',            'CORTICOIDES'),
    ('Dexametasona',                        'dexametasona',                         '4 mg/mL',           'Inyectable',         'CORTICOIDES'),
    ('Omeprazol',                           'omeprazol',                            '20 mg',             'Cápsula',            NULL),
    ('Hioscina butilbromuro',               'hioscina butilbromuro',                '10 mg',             'Tableta',            NULL),
    ('Metoclopramida',                      'metoclopramida',                       '10 mg',             'Tableta',            NULL),
    ('Sales de rehidratación oral',         'sales de rehidratacion oral',          '20.5 g',            'Sobre',              NULL),
    ('Enalapril',                           'enalapril',                            '10 mg',             'Tableta',            NULL),
    ('Losartán',                            'losartan',                             '50 mg',             'Tableta',            NULL),
    ('Amlodipino',                          'amlodipino',                           '5 mg',              'Tableta',            NULL),
    ('Hidroclorotiazida',                   'hidroclorotiazida',                    '25 mg',             'Tableta',            NULL),
    ('Metformina',                          'metformina',                           '850 mg',            'Tableta',            NULL),
    ('Glibenclamida',                       'glibenclamida',                        '5 mg',              'Tableta',            NULL),
    ('Atorvastatina',                       'atorvastatina',                        '20 mg',             'Tableta',            NULL),
    ('Levotiroxina',                        'levotiroxina',                         '100 mcg',           'Tableta',            NULL),
    ('Sulfato ferroso',                     'sulfato ferroso',                      '300 mg (60 mg Fe)', 'Tableta',            NULL),
    ('Ácido fólico',                        'acido folico',                         '500 mcg',           'Tableta',            NULL),
    ('Sertralina',                          'sertralina',                           '50 mg',             'Tableta',            NULL),
    ('Fluoxetina',                          'fluoxetina',                           '20 mg',             'Cápsula',            NULL);

-- ---------------------------------------------------------------------------------------------
-- Atención médica: una por cita. EN_CURSO se puede editar (solo su médico); CERRADA es inmutable
-- (plan.md, principio P3) y solo admite adendas.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE atenciones (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cita_id             BIGINT        NOT NULL REFERENCES citas (id),
    paciente_id         BIGINT        NOT NULL REFERENCES pacientes (id),
    medico_id           BIGINT        NOT NULL REFERENCES usuarios (id),
    estado              VARCHAR(10)   NOT NULL,
    inicio_en           TIMESTAMPTZ   NOT NULL,
    cerrada_en          TIMESTAMPTZ,

    motivo_consulta     VARCHAR(500)  NOT NULL,
    tiempo_enfermedad   VARCHAR(60),
    anamnesis           VARCHAR(4000),
    examen_fisico       VARCHAR(4000),
    plan_trabajo        VARCHAR(2000),
    indicaciones        VARCHAR(2000),

    creado_en           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    actualizado_en      TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT uk_atenciones_cita UNIQUE (cita_id),
    CONSTRAINT ck_atenciones_estado CHECK (estado IN ('EN_CURSO', 'CERRADA')),
    CONSTRAINT ck_atenciones_cierre CHECK ((estado = 'CERRADA') = (cerrada_en IS NOT NULL))
);

CREATE INDEX ix_atenciones_paciente ON atenciones (paciente_id, inicio_en DESC);

CREATE TABLE atencion_diagnosticos (
    atencion_id   BIGINT       NOT NULL REFERENCES atenciones (id),
    orden         INTEGER      NOT NULL,
    cie10_codigo  VARCHAR(8)   NOT NULL REFERENCES cie10 (codigo),
    tipo          VARCHAR(12)  NOT NULL,
    principal     BOOLEAN      NOT NULL,

    PRIMARY KEY (atencion_id, orden),
    CONSTRAINT ck_atencion_diagnosticos_tipo CHECK (tipo IN ('PRESUNTIVO', 'DEFINITIVO'))
);

CREATE TABLE atencion_receta (
    atencion_id               BIGINT        NOT NULL REFERENCES atenciones (id),
    orden                     INTEGER       NOT NULL,
    medicamento_id            BIGINT        NOT NULL REFERENCES medicamentos (id),
    dosis                     VARCHAR(60)   NOT NULL,
    via                       VARCHAR(20)   NOT NULL,
    frecuencia                VARCHAR(60)   NOT NULL,
    duracion                  VARCHAR(60)   NOT NULL,
    cantidad                  INTEGER       NOT NULL,
    indicaciones              VARCHAR(200),
    -- Si el medicamento coincide con una alergia registrada, el médico debe confirmarlo y justificarlo
    alergia_confirmada        BOOLEAN       NOT NULL DEFAULT FALSE,
    justificacion_alergia     VARCHAR(300),

    PRIMARY KEY (atencion_id, orden),
    CONSTRAINT ck_atencion_receta_via CHECK (via IN ('ORAL', 'SUBLINGUAL', 'TOPICA', 'OFTALMICA', 'OTICA', 'NASAL',
                                                     'INHALATORIA', 'VAGINAL', 'RECTAL', 'INTRAMUSCULAR',
                                                     'ENDOVENOSA', 'SUBCUTANEA')),
    CONSTRAINT ck_atencion_receta_cantidad CHECK (cantidad > 0),
    CONSTRAINT ck_atencion_receta_alergia CHECK (NOT alergia_confirmada OR justificacion_alergia IS NOT NULL)
);

-- Correcciones o aclaraciones posteriores al cierre, con su autor y fecha
CREATE TABLE atencion_adendas (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    atencion_id  BIGINT         NOT NULL REFERENCES atenciones (id),
    autor_id     BIGINT         NOT NULL REFERENCES usuarios (id),
    texto        VARCHAR(2000)  NOT NULL,
    creado_en    TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX ix_atencion_adendas_atencion ON atencion_adendas (atencion_id);

-- Una atención cerrada no se puede modificar ni borrar desde ningún lado (tampoco su diagnóstico ni receta)
CREATE FUNCTION fn_atencion_cerrada_inmutable() RETURNS TRIGGER AS $$
DECLARE
    v_atencion_id BIGINT;
BEGIN
    IF TG_TABLE_NAME = 'atenciones' THEN
        IF OLD.estado = 'CERRADA' THEN
            RAISE EXCEPTION 'La atención % está cerrada y no se puede modificar', OLD.id;
        END IF;
        RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
    END IF;
    v_atencion_id := CASE WHEN TG_OP = 'INSERT' THEN NEW.atencion_id ELSE OLD.atencion_id END;
    IF EXISTS (SELECT 1 FROM atenciones WHERE id = v_atencion_id AND estado = 'CERRADA') THEN
        RAISE EXCEPTION 'La atención % está cerrada y no se puede modificar', v_atencion_id;
    END IF;
    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_atenciones_inmutable BEFORE UPDATE OR DELETE ON atenciones
    FOR EACH ROW EXECUTE FUNCTION fn_atencion_cerrada_inmutable();
CREATE TRIGGER tg_atencion_diagnosticos_inmutable BEFORE INSERT OR UPDATE OR DELETE ON atencion_diagnosticos
    FOR EACH ROW EXECUTE FUNCTION fn_atencion_cerrada_inmutable();
CREATE TRIGGER tg_atencion_receta_inmutable BEFORE INSERT OR UPDATE OR DELETE ON atencion_receta
    FOR EACH ROW EXECUTE FUNCTION fn_atencion_cerrada_inmutable();
