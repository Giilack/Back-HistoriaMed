-- Usuario de la aplicación con mínimo privilegio (plan.md, sección 5.8).
-- El backend se conecta con historiamed_app, que solo puede leer y escribir datos. Las migraciones (Flyway) siguen
-- usando el usuario propietario. Así, aunque alguien tomara el control de la aplicación, no podría borrar tablas,
-- cambiar su estructura ni desactivar los triggers de inmutabilidad (solo el propietario puede hacerlo).
--
-- La contraseña NO va en esta migración: se define fuera de Git con
--     ALTER ROLE historiamed_app WITH LOGIN PASSWORD '...';
-- (ver docs/manual-despliegue.md). Mientras no se defina, el rol existe pero no puede iniciar sesión.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'historiamed_app') THEN
        CREATE ROLE historiamed_app NOLOGIN;
    END IF;
END
$$;

-- Los permisos se dan sobre el esquema donde corren las migraciones (public en producción)
DO $$
DECLARE
    esquema TEXT := current_schema();
BEGIN
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO historiamed_app', current_database());
    EXECUTE format('GRANT USAGE ON SCHEMA %I TO historiamed_app', esquema);

    -- Leer, crear y actualizar en todas las tablas; sin DELETE, TRUNCATE ni cambios de estructura
    EXECUTE format('GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA %I TO historiamed_app', esquema);
    EXECUTE format('GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA %I TO historiamed_app', esquema);

    -- DELETE solo donde la aplicación reemplaza filas de detalle mientras el registro está abierto: diagnósticos,
    -- receta y plan de una atención en borrador (al firmarla, los triggers impiden cualquier cambio), alertas de un
    -- triaje e ítems de una revisión de documento que aún no se valida.
    EXECUTE format('GRANT DELETE ON %I.atencion_diagnosticos, %I.atencion_receta, %I.atencion_plan, '
                || '%I.triaje_alertas, %I.extraccion_items TO historiamed_app',
                esquema, esquema, esquema, esquema, esquema);

    -- Nada sobre el historial de migraciones
    EXECUTE format('REVOKE ALL ON %I.flyway_schema_history FROM historiamed_app', esquema);

    -- Las tablas y secuencias que creen migraciones futuras reciben los mismos permisos básicos
    EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA %I GRANT SELECT, INSERT, UPDATE ON TABLES TO historiamed_app',
                esquema);
    EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA %I GRANT USAGE, SELECT ON SEQUENCES TO historiamed_app',
                esquema);
END
$$;
