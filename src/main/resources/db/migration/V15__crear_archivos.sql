-- Almacenamiento de documentos dentro de la base de datos (plan.md, sección 6.3). Se usa en la nube, donde el
-- disco del servidor se borra en cada reinicio; en local se sigue usando la carpeta uploads/.
-- Los metadatos del documento (tipo, paciente, huella SHA-256) siguen en la tabla documentos, que guarda la clave.

CREATE TABLE archivos (
    -- Clave generada por el sistema (la misma que documentos.clave_almacenamiento), por ejemplo 2026/09/uuid.pdf
    clave      VARCHAR(200)  PRIMARY KEY,
    contenido  BYTEA         NOT NULL,
    creado_en  TIMESTAMPTZ   NOT NULL DEFAULT now(),

    -- El mismo tope que la subida: 10 MB
    CONSTRAINT ck_archivos_tamanio CHECK (octet_length(contenido) BETWEEN 1 AND 10485760)
);

-- Un archivo guardado no se modifica ni se borra: los documentos se anulan, no se eliminan (principio P3)
CREATE FUNCTION fn_archivo_inmutable() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'El archivo % no se puede modificar ni borrar', OLD.clave;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_archivos_inmutable BEFORE UPDATE OR DELETE ON archivos
    FOR EACH ROW EXECUTE FUNCTION fn_archivo_inmutable();
