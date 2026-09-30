package com.historiamed.backend.extraccion;

import java.time.LocalDate;

import com.historiamed.backend.alergia.GravedadAlergia;
import com.historiamed.backend.alergia.TipoAlergia;
import com.historiamed.backend.antecedente.TipoAntecedente;
import com.historiamed.backend.catalogo.Cie10;
import com.historiamed.backend.catalogo.Medicamento;
import com.historiamed.backend.common.entity.EntidadBase;
import com.historiamed.backend.usuario.Usuario;

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
 * Un dato del documento, clasificado por categoría. Según la categoría se usan unos campos u otros (lo valida
 * {@link ExtraccionService}).
 */
@Getter
@Setter
@Entity
@Table(name = "extraccion_items")
public class ExtraccionItem extends EntidadBase {

	public enum Categoria {
		/** Al validarse, se registra como alergia del paciente. */
		ALERGIA,
		/** Diagnóstico hecho en otro lugar (CIE-10): queda como antecedente. */
		DIAGNOSTICO,
		/** Medicamento que el paciente ya toma: queda como antecedente. */
		MEDICAMENTO,
		/** Resultado de un examen de laboratorio. */
		LABORATORIO,
		/** Antecedente personal, familiar o quirúrgico. */
		ANTECEDENTE,
		/** Información que no encaja en las demás: no pasa a la historia, queda solo en la revisión. */
		OTRO
	}

	public enum Estado {
		PROPUESTO,
		ACEPTADO,
		/** Aceptado después de que el médico lo modificó. */
		CORREGIDO,
		DESCARTADO
	}

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "extraccion_id", nullable = false, updatable = false)
	private Extraccion extraccion;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Categoria categoria;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Estado estado = Estado.PROPUESTO;

	/** Sustancia (alergia), diagnóstico, medicamento, examen (laboratorio) o el antecedente. */
	@Column(nullable = false)
	private String descripcion;

	/** Reacción (alergia), dosis o pauta (medicamento) o una nota. */
	private String detalle;

	private LocalDate fecha;

	@Enumerated(EnumType.STRING)
	@Column(name = "tipo_alergia")
	private TipoAlergia tipoAlergia;

	@Enumerated(EnumType.STRING)
	private GravedadAlergia gravedad;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "cie_codigo")
	private Cie10 cie10;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "medicamento_id")
	private Medicamento medicamento;

	private String valor;

	private String unidad;

	@Column(name = "rango_referencia")
	private String rangoReferencia;

	@Enumerated(EnumType.STRING)
	@Column(name = "tipo_antecedente")
	private TipoAntecedente tipoAntecedente;

	/** De dónde salió el dato en el documento, para que el médico lo compare. */
	@Column(name = "fragmento_origen")
	private String fragmentoOrigen;

	private Integer pagina;

	/** El médico cambió un dato propuesto por otra persona o por la IA. */
	@Column(nullable = false)
	private boolean corregido;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "creado_por", nullable = false, updatable = false)
	private Usuario creadoPor;

}
