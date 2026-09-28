package com.historiamed.backend.paciente.dto;

import java.time.LocalDate;

import com.historiamed.backend.paciente.Sexo;
import com.historiamed.backend.paciente.TipoDocumento;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Datos de filiación del paciente.
 *
 * @param financiamiento solo se usa al registrar (si se omite, queda PARTICULAR). Al editar se ignora: el
 * financiamiento se cambia con PUT /api/pacientes/{id}/financiamiento.
 */
public record PacienteRequest(
		@NotNull TipoDocumento tipoDocumento,
		@Pattern(regexp = "^[A-Za-z0-9]{1,20}$", message = "solo letras y números, hasta 20 caracteres") String numeroDocumento,
		@NotBlank @Size(max = 100) String nombres,
		@NotBlank @Size(max = 100) String apellidoPaterno,
		@Size(max = 100) String apellidoMaterno,
		@NotNull @PastOrPresent LocalDate fechaNacimiento,
		@NotNull Sexo sexo,
		@Pattern(regexp = "^[0-9+ ]{6,15}$", message = "teléfono inválido") String telefono,
		@Email @Size(max = 150) String email,
		@Size(max = 200) String direccion,
		@Size(max = 150) String contactoEmergenciaNombre,
		@Pattern(regexp = "^[0-9+ ]{6,15}$", message = "teléfono inválido") String contactoEmergenciaTelefono,
		@Size(max = 30) String contactoEmergenciaParentesco,
		@Valid FinanciamientoRequest financiamiento) {
}
