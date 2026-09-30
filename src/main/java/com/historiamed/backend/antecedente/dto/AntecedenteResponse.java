package com.historiamed.backend.antecedente.dto;

import java.time.Instant;
import java.time.LocalDate;

import com.historiamed.backend.antecedente.Antecedente;
import com.historiamed.backend.antecedente.TipoAntecedente;

/**
 * @param cieCodigo código CIE-10, solo en los diagnósticos previos
 * @param documentoId documento del que salió el dato (nulo si se registró directamente)
 */
public record AntecedenteResponse(Long id, TipoAntecedente tipo, String descripcion, String detalle, LocalDate fecha,
		String cieCodigo, Long medicamentoId, Long documentoId, boolean activo, String motivoInactivacion,
		String registradoPor, Instant creadoEn) {

	public static AntecedenteResponse de(Antecedente a) {
		return new AntecedenteResponse(a.getId(), a.getTipo(), a.getDescripcion(), a.getDetalle(), a.getFecha(),
				a.getCie10() == null ? null : a.getCie10().getCodigo(),
				a.getMedicamento() == null ? null : a.getMedicamento().getId(),
				a.getDocumento() == null ? null : a.getDocumento().getId(), a.isActivo(), a.getMotivoInactivacion(),
				a.getRegistradoPor().getApellidos() + ", " + a.getRegistradoPor().getNombres(), a.getCreadoEn());
	}

}
