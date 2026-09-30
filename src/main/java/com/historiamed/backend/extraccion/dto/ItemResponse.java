package com.historiamed.backend.extraccion.dto;

import java.time.LocalDate;

import com.historiamed.backend.alergia.GravedadAlergia;
import com.historiamed.backend.alergia.TipoAlergia;
import com.historiamed.backend.antecedente.TipoAntecedente;
import com.historiamed.backend.extraccion.ExtraccionItem;

public record ItemResponse(Long id, ExtraccionItem.Categoria categoria, ExtraccionItem.Estado estado,
		String descripcion, String detalle, LocalDate fecha, TipoAlergia tipoAlergia, GravedadAlergia gravedad,
		String cieCodigo, Long medicamentoId, String valor, String unidad, String rangoReferencia,
		TipoAntecedente tipoAntecedente, String fragmentoOrigen, Integer pagina, boolean corregido, Long creadoPorId,
		String creadoPor) {

	public static ItemResponse de(ExtraccionItem i) {
		return new ItemResponse(i.getId(), i.getCategoria(), i.getEstado(), i.getDescripcion(), i.getDetalle(),
				i.getFecha(), i.getTipoAlergia(), i.getGravedad(),
				i.getCie10() == null ? null : i.getCie10().getCodigo(),
				i.getMedicamento() == null ? null : i.getMedicamento().getId(), i.getValor(), i.getUnidad(),
				i.getRangoReferencia(), i.getTipoAntecedente(), i.getFragmentoOrigen(), i.getPagina(),
				i.isCorregido(), i.getCreadoPor().getId(),
				i.getCreadoPor().getApellidos() + ", " + i.getCreadoPor().getNombres());
	}

}
