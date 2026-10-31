package com.historiamed.backend.laboratorio;

import java.time.LocalDate;

import com.historiamed.backend.common.entity.DatoClinicoInactivable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Resultado de un examen de laboratorio. No se borra (plan.md, principio P3): si fue un error, se inactiva con un
 * motivo (campos comunes en {@link DatoClinicoInactivable}).
 */
@Getter
@Setter
@Entity
@Table(name = "resultados_laboratorio")
public class ResultadoLaboratorio extends DatoClinicoInactivable {

	@Column(nullable = false)
	private String examen;

	/** Texto, no número: hay resultados como "Negativo" o "1/160". */
	@Column(nullable = false)
	private String valor;

	private String unidad;

	@Column(name = "rango_referencia")
	private String rangoReferencia;

	/** Fecha del examen (la del documento), no la de registro. */
	private LocalDate fecha;

}
