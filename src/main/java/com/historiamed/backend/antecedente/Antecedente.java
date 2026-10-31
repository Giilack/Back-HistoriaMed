package com.historiamed.backend.antecedente;

import java.time.LocalDate;

import com.historiamed.backend.catalogo.Cie10;
import com.historiamed.backend.catalogo.Medicamento;
import com.historiamed.backend.common.entity.DatoClinicoInactivable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Antecedente del paciente. No se borra (plan.md, principio P3): si fue un error, se inactiva con un motivo
 * (campos comunes en {@link DatoClinicoInactivable}).
 */
@Getter
@Setter
@Entity
@Table(name = "antecedentes")
public class Antecedente extends DatoClinicoInactivable {

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TipoAntecedente tipo;

	@Column(nullable = false)
	private String descripcion;

	private String detalle;

	/** Fecha del hecho (cirugía, diagnóstico...), si se conoce. */
	private LocalDate fecha;

	/** Solo en {@link TipoAntecedente#DIAGNOSTICO_PREVIO}. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "cie_codigo", updatable = false)
	private Cie10 cie10;

	/** Solo en {@link TipoAntecedente#MEDICACION_HABITUAL}. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "medicamento_id", updatable = false)
	private Medicamento medicamento;

}
