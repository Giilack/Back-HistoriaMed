package com.historiamed.backend.usuario.dto;

/**
 * Respuesta al crear un usuario o resetear su contraseña. La contraseña temporal se muestra una sola vez;
 * el usuario deberá cambiarla en su primer ingreso.
 */
public record PasswordTemporalResponse(UsuarioResponse usuario, String passwordTemporal) {
}
