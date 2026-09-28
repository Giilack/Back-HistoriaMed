-- Registro de auditoría: quién hizo qué, sobre qué recurso, cuándo y desde dónde. Ver plan.md, sección 5.7.
-- Se guardan username y rol como texto para que el registro siga siendo legible aunque el usuario cambie.
CREATE TABLE registros_auditoria (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fecha        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    usuario_id   BIGINT,
    username     VARCHAR(30),
    rol          VARCHAR(20),
    accion       VARCHAR(40)  NOT NULL,
    recurso      VARCHAR(40)  NOT NULL,
    recurso_id   VARCHAR(40),
    paciente_id  BIGINT,
    detalle      VARCHAR(500),
    ip           VARCHAR(45)
);

CREATE INDEX ix_auditoria_fecha    ON registros_auditoria (fecha DESC);
CREATE INDEX ix_auditoria_usuario  ON registros_auditoria (usuario_id);
CREATE INDEX ix_auditoria_paciente ON registros_auditoria (paciente_id);

-- La auditoría no se puede modificar ni borrar
CREATE FUNCTION fn_auditoria_inmutable() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Los registros de auditoría no se pueden modificar ni eliminar';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_auditoria_inmutable
    BEFORE UPDATE OR DELETE ON registros_auditoria
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria_inmutable();
