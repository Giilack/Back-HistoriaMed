package com.historiamed.backend.atencion;

import com.historiamed.backend.catalogo.Medicamento;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemReceta {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "medicamento_id", nullable = false)
	private Medicamento medicamento;

	/** "1 tableta", "5 mL" */
	@Column(nullable = false)
	private String dosis;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ViaAdministracion via;

	/** "cada 8 horas" */
	@Column(nullable = false)
	private String frecuencia;

	/** "7 días" */
	@Column(nullable = false)
	private String duracion;

	/** Unidades a dispensar (tabletas, frascos...). */
	@Column(nullable = false)
	private Integer cantidad;

	private String indicaciones;

	/** El medicamento coincidía con una alergia y el médico lo confirmó con una justificación. */
	@Column(name = "alergia_confirmada", nullable = false)
	private boolean alergiaConfirmada;

	@Column(name = "justificacion_alergia")
	private String justificacionAlergia;

}
