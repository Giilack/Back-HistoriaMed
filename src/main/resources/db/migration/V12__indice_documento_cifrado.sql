-- Con los datos ya cifrados (V11): un mismo documento no puede pertenecer a dos pacientes, ahora por su huella.
CREATE UNIQUE INDEX uk_pacientes_documento_huella
    ON pacientes (tipo_documento, numero_documento_huella)
    WHERE numero_documento_huella IS NOT NULL;

-- Todo número de documento tiene su huella, y viceversa
ALTER TABLE pacientes ADD CONSTRAINT ck_pacientes_documento_huella
    CHECK ((numero_documento IS NULL) = (numero_documento_huella IS NULL));
