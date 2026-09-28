package com.historiamed.backend.auth.dto;

import com.historiamed.backend.usuario.dto.UsuarioResponse;

/**
 * Respuesta de login, refresh y cambio de contraseña. El refresh token no va aquí: viaja en una cookie httpOnly.
 * Si {@code usuario.debeCambiarPassword} es true, el frontend debe llevar al usuario a cambiar su contraseña.
 */
public record LoginResponse(String accessToken, String tipo, long expiraEnSegundos, UsuarioResponse usuario) {
}
