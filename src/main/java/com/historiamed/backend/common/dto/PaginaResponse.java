package com.historiamed.backend.common.dto;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * Respuesta paginada estable (no se expone {@link Page} directamente en la API).
 */
public record PaginaResponse<T>(List<T> contenido, int pagina, int tamanio, long totalElementos, int totalPaginas) {

	public static <E, T> PaginaResponse<T> de(Page<E> page, Function<E, T> mapper) {
		return new PaginaResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(),
				page.getTotalElements(), page.getTotalPages());
	}

}
