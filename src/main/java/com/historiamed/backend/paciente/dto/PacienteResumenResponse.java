package com.historiamed.backend.paciente.dto;

import com.historiamed.backend.common.util.Edad;
import com.historiamed.backend.paciente.EstadoSeguro;
import com.historiamed.backend.paciente.Paciente;
import com.historiamed.backend.paciente.Sexo;
import com.historiamed.backend.paciente.TipoDocumento;
import com.historiamed.backend.paciente.TipoFinanciamiento;

/**
 * Fila del resultado de búsqueda: lo justo para identificar al paciente.
 */
public record PacienteResumenResponse(Long id, String numeroHc, TipoDocumento tipoDocumento, String numeroDocumento,
		String nombreCompleto, String edad, Sexo sexo, TipoFinanciamiento tipoFinanciamiento,
		EstadoSeguro estadoSeguro) {

	public static PacienteResumenResponse de(Paciente p) {
		return new PacienteResumenResponse(p.getId(), p.getNumeroHc(), p.getTipoDocumento(), p.getNumeroDocumento(),
				p.nombreCompleto(), Edad.texto(p.getFechaNacimiento()), p.getSexo(), p.getTipoFinanciamiento(),
				p.getSeguroEstado());
	}

}
