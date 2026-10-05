-- Tratamiento estructurado (plan.md, sección 5.4, fase 9): además de la receta, la atención registra como datos
-- el tratamiento no farmacológico, las órdenes de exámenes, las interconsultas, el descanso médico y el control.

-- Indicaciones del plan: tratamiento no farmacológico, exámenes auxiliares e interconsultas
CREATE TABLE atencion_plan (
    atencion_id   BIGINT        NOT NULL REFERENCES atenciones (id),
    orden         INTEGER       NOT NULL,
    tipo          VARCHAR(15)   NOT NULL,
    -- TRATAMIENTO: DIETA, REPOSO, FISIOTERAPIA, CURACION u OTRO. EXAMEN: LABORATORIO, IMAGEN u OTRO.
    -- INTERCONSULTA: sin categoría.
    categoria     VARCHAR(15),
    -- La indicación, el examen solicitado o la especialidad a la que se deriva
    descripcion   VARCHAR(200)  NOT NULL,
    -- Una nota o, en la interconsulta, el motivo
    detalle       VARCHAR(300),

    PRIMARY KEY (atencion_id, orden),
    CONSTRAINT ck_atencion_plan_tipo CHECK (tipo IN ('TRATAMIENTO', 'EXAMEN', 'INTERCONSULTA')),
    CONSTRAINT ck_atencion_plan_categoria CHECK (
        (tipo = 'TRATAMIENTO' AND categoria IN ('DIETA', 'REPOSO', 'FISIOTERAPIA', 'CURACION', 'OTRO'))
        OR (tipo = 'EXAMEN' AND categoria IN ('LABORATORIO', 'IMAGEN', 'OTRO'))
        OR (tipo = 'INTERCONSULTA' AND categoria IS NULL AND detalle IS NOT NULL))
);

-- Igual que el diagnóstico y la receta: no se puede tocar si la atención está cerrada
CREATE TRIGGER tg_atencion_plan_inmutable BEFORE INSERT OR UPDATE OR DELETE ON atencion_plan
    FOR EACH ROW EXECUTE FUNCTION fn_atencion_cerrada_inmutable();

-- Descanso médico y cita de control: como mucho uno por atención
ALTER TABLE atenciones
    ADD COLUMN descanso_dias   INTEGER,
    ADD COLUMN descanso_desde  DATE,
    ADD COLUMN control_fecha   DATE,
    ADD COLUMN control_nota    VARCHAR(200),
    ADD CONSTRAINT ck_atenciones_descanso
        CHECK ((descanso_dias IS NULL) = (descanso_desde IS NULL)),
    ADD CONSTRAINT ck_atenciones_descanso_dias CHECK (descanso_dias IS NULL OR descanso_dias BETWEEN 1 AND 30),
    ADD CONSTRAINT ck_atenciones_control CHECK (control_nota IS NULL OR control_fecha IS NOT NULL);

-- Para listar los controles pendientes de programar
CREATE INDEX ix_atenciones_control ON atenciones (control_fecha) WHERE control_fecha IS NOT NULL;
