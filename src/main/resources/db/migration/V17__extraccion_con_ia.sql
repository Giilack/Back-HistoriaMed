-- Extracción con IA (plan.md, sección 5.5, fase 11): la IA lee el documento y propone los datos en la misma
-- revisión que se llena a mano. Nada pasa a la historia clínica sin la validación del médico (principio P2).

ALTER TABLE extracciones
    -- NULL: no se usó la IA. PROCESANDO: la IA está leyendo el documento. COMPLETADA o ERROR: terminó.
    ADD COLUMN ia_estado        VARCHAR(12),
    -- Modelo que se usó (por ejemplo gemini-3.8-flash), para saber con qué se generó la propuesta
    ADD COLUMN ia_modelo        VARCHAR(60),
    ADD COLUMN ia_error         VARCHAR(500),
    ADD COLUMN ia_solicitada_en TIMESTAMPTZ,
    ADD COLUMN ia_terminada_en  TIMESTAMPTZ,
    ADD CONSTRAINT ck_extracciones_ia_estado
        CHECK (ia_estado IS NULL OR ia_estado IN ('PROCESANDO', 'COMPLETADA', 'ERROR')),
    ADD CONSTRAINT ck_extracciones_ia_error CHECK ((ia_estado = 'ERROR') = (ia_error IS NOT NULL));

-- Distingue en la revisión lo que propuso la IA de lo que escribió una persona
ALTER TABLE extraccion_items ADD COLUMN propuesto_por_ia BOOLEAN NOT NULL DEFAULT FALSE;
