package com.historiamed.backend.extraccion.dto;

import java.time.LocalDate;

import com.historiamed.backend.alergia.GravedadAlergia;
import com.historiamed.backend.alergia.TipoAlergia;
import com.historiamed.backend.antecedente.TipoAntecedente;
import com.historiamed.backend.extraccion.ExtraccionItem;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

/**
 * Un dato del documento. Qué campos se exigen depende de la categoría:
 * <ul>
 * <li>ALERGIA: {@code descripcion} (sustancia), {@code tipoAlergia}, {@code gravedad}; {@code detalle} = reacción.</li>
 * <li>DIAGNOSTICO: {@code cieCodigo} (la descripción se toma del catálogo).</li>
 * <li>MEDICAMENTO: {@code medicamentoId} (la descripción se toma del catálogo); {@code detalle} = dosis o pauta.</li>
 * <li>LABORATORIO: {@code descripcion} (examen) y {@code valor}; opcionales {@code unidad} y
 * {@code rangoReferencia}.</li>
 * <li>ANTECEDENTE: {@code descripcion} y {@code tipoAntecedente} (PERSONAL, FAMILIAR, QUIRURGICO u OTRO).</li>
 * <li>OTRO: {@code descripcion}.</li>
 * </ul>
 *
 * @param fragmentoOrigen texto del documento de donde salió el dato (opcional)
 * @param pagina página del documento (opcional)
 */
public record ItemRequest(
		@NotNull ExtraccionItem.Categoria categoria,
		@Size(max = 300) String descripcion,
		@Size(max = 300) String detalle,
		@PastOrPresent LocalDate fecha,
		TipoAlergia tipoAlergia,
		GravedadAlergia gravedad,
		@Size(max = 8) String cieCodigo,
		Long medicamentoId,
		@Size(max = 60) String valor,
		@Size(max = 30) String unidad,
		@Size(max = 60) String rangoReferencia,
		TipoAntecedente tipoAntecedente,
		@Size(max = 500) String fragmentoOrigen,
		@Min(1) Integer pagina) {
}
