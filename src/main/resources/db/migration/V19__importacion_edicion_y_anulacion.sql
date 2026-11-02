-- Mientras no se aprueban, los documentos importados viven en su propia tabla: se pueden borrar y liberan espacio.
-- Los archivos de la tabla archivos son inmutables, porque ya forman parte de la historia clínica.
CREATE TABLE importacion_contenidos (
    importacion_id  BIGINT       PRIMARY KEY REFERENCES importaciones (id),
    contenido       BYTEA        NOT NULL,
    creado_en       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'historiamed_app') THEN
        GRANT DELETE ON importacion_contenidos TO historiamed_app;
    END IF;
END
$$;

ALTER TABLE importaciones ALTER COLUMN clave_almacenamiento DROP NOT NULL;
ALTER TABLE importaciones ADD COLUMN paciente_nuevo BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE importaciones DROP CONSTRAINT ck_importaciones_estado;
ALTER TABLE importaciones ADD CONSTRAINT ck_importaciones_estado
    CHECK (estado IN ('EN_COLA', 'PROCESANDO', 'LISTA', 'ERROR', 'APROBADA', 'RECHAZADA', 'ELIMINADA', 'ANULADA'));

-- Un archivo descartado se puede volver a subir
DROP INDEX ux_importaciones_archivo_vigente;
CREATE UNIQUE INDEX ux_importaciones_archivo_vigente ON importaciones (sha256)
    WHERE estado NOT IN ('RECHAZADA', 'ELIMINADA', 'ANULADA');

-- Permite inactivar las alergias de una importación anulada
ALTER TABLE alergias ADD COLUMN documento_id BIGINT REFERENCES documentos (id);
CREATE INDEX ix_alergias_documento ON alergias (documento_id) WHERE documento_id IS NOT NULL;
