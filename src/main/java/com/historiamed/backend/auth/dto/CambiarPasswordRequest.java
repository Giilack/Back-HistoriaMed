package com.historiamed.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CambiarPasswordRequest(@NotBlank String passwordActual,
		@NotBlank @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$",
				message = "debe tener entre 8 y 72 caracteres, con al menos una letra y un número") String passwordNueva) {
}
