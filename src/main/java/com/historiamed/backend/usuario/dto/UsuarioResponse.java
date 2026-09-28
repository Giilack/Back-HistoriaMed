package com.historiamed.backend.usuario.dto;

import java.time.Instant;

import com.historiamed.backend.usuario.Rol;
import com.historiamed.backend.usuario.Usuario;

/**
 * Datos públicos de un usuario (nunca incluye el hash de la contraseña).
 */
public record UsuarioResponse(Long id, String username, String nombres, String apellidos, String dni, String email,
		Rol rol, String cmp, boolean activo, boolean debeCambiarPassword, boolean bloqueado, Instant ultimoAcceso,
		Instant creadoEn) {

	public static UsuarioResponse de(Usuario u) {
		return new UsuarioResponse(u.getId(), u.getUsername(), u.getNombres(), u.getApellidos(), u.getDni(),
				u.getEmail(), u.getRol(), u.getCmp(), u.isActivo(), u.isDebeCambiarPassword(),
				u.estaBloqueado(Instant.now()), u.getUltimoAcceso(), u.getCreadoEn());
	}

}
