package com.historiamed.backend.usuario.dto;

import com.historiamed.backend.usuario.Rol;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Datos editables de un usuario. El username no se puede cambiar.
 */
public record UsuarioActualizarRequest(
		@NotBlank @Size(max = 100) String nombres,
		@NotBlank @Size(max = 100) String apellidos,
		@NotBlank @Pattern(regexp = "^[0-9]{8}$", message = "debe tener 8 dígitos") String dni,
		@Email @Size(max = 150) String email,
		@NotNull Rol rol,
		@Pattern(regexp = "^[0-9]{4,10}$", message = "debe tener entre 4 y 10 dígitos") String cmp) {
}
