package com.historiamed.backend.paciente.dto;

import java.time.Instant;
import java.time.LocalDate;

import com.historiamed.backend.common.util.Edad;
import com.historiamed.backend.paciente.Paciente;
import com.historiamed.backend.paciente.Sexo;
import com.historiamed.backend.paciente.TipoDocumento;

/**
 * Ficha completa de filiación del paciente (sin datos clínicos).
 */
public record PacienteResponse(Long id, String numeroHc, TipoDocumento tipoDocumento, String numeroDocumento,
		String nombres, String apellidoPaterno, String apellidoMaterno, String nombreCompleto,
		LocalDate fechaNacimiento, String edad, Sexo sexo, String telefono, String email, String direccion,
		String contactoEmergenciaNombre, String contactoEmergenciaTelefono, String contactoEmergenciaParentesco,
		FinanciamientoResponse financiamiento, Instant creadoEn, Instant actualizadoEn) {

	public static PacienteResponse de(Paciente p) {
		return new PacienteResponse(p.getId(), p.getNumeroHc(), p.getTipoDocumento(), p.getNumeroDocumento(),
				p.getNombres(), p.getApellidoPaterno(), p.getApellidoMaterno(), p.nombreCompleto(),
				p.getFechaNacimiento(), Edad.texto(p.getFechaNacimiento()), p.getSexo(), p.getTelefono(), p.getEmail(),
				p.getDireccion(), p.getContactoEmergenciaNombre(), p.getContactoEmergenciaTelefono(),
				p.getContactoEmergenciaParentesco(), FinanciamientoResponse.de(p), p.getCreadoEn(),
				p.getActualizadoEn());
	}

}
