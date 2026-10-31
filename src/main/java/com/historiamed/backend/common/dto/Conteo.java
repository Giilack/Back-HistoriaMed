package com.historiamed.backend.common.dto;

/**
 * Cantidad agrupada de un indicador.
 *
 * @param codigo valor técnico (por ejemplo, el código CIE-10)
 * @param etiqueta nombre legible
 */
public record Conteo(String codigo, String etiqueta, long total) {
}
