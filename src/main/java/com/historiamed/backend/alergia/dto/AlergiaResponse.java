package com.historiamed.backend.alergia.dto;

import java.time.Instant;

import com.historiamed.backend.alergia.Alergia;
import com.historiamed.backend.alergia.GravedadAlergia;
import com.historiamed.backend.alergia.TipoAlergia;

public record AlergiaResponse(Long id, TipoAlergia tipo, String sustancia, String reaccion, GravedadAlergia gravedad,
		boolean activa, String motivoInactivacion, String registradoPor, Instant creadoEn) {

	public static AlergiaResponse de(Alergia a) {
		return new AlergiaResponse(a.getId(), a.getTipo(), a.getSustancia(), a.getReaccion(), a.getGravedad(),
				a.isActiva(), a.getMotivoInactivacion(),
				a.getRegistradoPor().getApellidos() + ", " + a.getRegistradoPor().getNombres(), a.getCreadoEn());
	}

}
