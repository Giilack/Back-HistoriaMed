-- Contraseñas con Argon2id: el hash con su prefijo ({argon2}$argon2id$v=19$m=...) ocupa unos 105 caracteres,
-- más que los 60 de BCrypt para los que se dimensionó la columna.
ALTER TABLE usuarios ALTER COLUMN password_hash TYPE VARCHAR(255);
