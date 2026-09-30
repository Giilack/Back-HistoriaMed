-- Datos personales sensibles del paciente cifrados con AES-256-GCM (ver CifradoDatos). El texto cifrado es más
-- largo que el original, así que las columnas pasan a TEXT. Los datos existentes se cifran en V11 (migración Java).

ALTER TABLE pacientes
    ALTER COLUMN numero_documento TYPE TEXT,
    ALTER COLUMN telefono TYPE TEXT,
    ALTER COLUMN direccion TYPE TEXT,
    ALTER COLUMN contacto_emergencia_telefono TYPE TEXT;

-- El formato del DNI ya no se puede comprobar en la base (el valor está cifrado); lo valida PacienteService.
ALTER TABLE pacientes DROP CONSTRAINT ck_pacientes_dni;

-- La unicidad del documento pasa a la huella (HMAC del número): el texto cifrado cambia en cada guardado.
DROP INDEX uk_pacientes_documento;
ALTER TABLE pacientes ADD COLUMN numero_documento_huella VARCHAR(64);
