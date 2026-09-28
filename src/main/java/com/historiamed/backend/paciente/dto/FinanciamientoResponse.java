package com.historiamed.backend.paciente.dto;

import java.time.Instant;

import com.historiamed.backend.paciente.EstadoSeguro;
import com.historiamed.backend.paciente.Paciente;
import com.historiamed.backend.paciente.TipoFinanciamiento;

public record FinanciamientoResponse(TipoFinanciamiento tipo, String numeroAfiliacion, String plan,
		EstadoSeguro estado, Instant verificadoEn, boolean orientadoAfiliacionSis) {

	public static FinanciamientoResponse de(Paciente p) {
		return new FinanciamientoResponse(p.getTipoFinanciamiento(), p.getSeguroNumeroAfiliacion(), p.getSeguroPlan(),
				p.getSeguroEstado(), p.getSeguroVerificadoEn(), p.isOrientadoAfiliacionSis());
	}

}
