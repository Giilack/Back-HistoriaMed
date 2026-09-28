package com.historiamed.backend.paciente.dto;

import com.historiamed.backend.paciente.TipoFinanciamiento;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param numeroAfiliacion número del seguro (SIS, EsSalud o póliza privada). Se ignora si es PARTICULAR.
 * @param plan plan o tipo de seguro (por ejemplo, "SIS Gratuito"); para PRIVADO, la aseguradora o EPS.
 * @param orientadoAfiliacionSis solo para PARTICULAR: se le orientó a tramitar su afiliación al SIS.
 */
public record FinanciamientoRequest(
		@NotNull TipoFinanciamiento tipo,
		@Size(max = 30) String numeroAfiliacion,
		@Size(max = 60) String plan,
		Boolean orientadoAfiliacionSis) {
}
