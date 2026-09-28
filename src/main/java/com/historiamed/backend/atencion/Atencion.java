package com.historiamed.backend.atencion;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.historiamed.backend.cita.Cita;
import com.historiamed.backend.common.entity.EntidadBase;
import com.historiamed.backend.common.exception.ReglaNegocioException;
import com.historiamed.backend.paciente.Paciente;
import com.historiamed.backend.usuario.Usuario;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * Atención médica de una cita (plan.md, sección 5.4). Mientras está EN_CURSO, solo su médico la edita; al
 * cerrarla (firmarla) queda inmutable: la base de datos también lo impide con triggers (V7).
 */
@Getter
@Setter
@Entity
@Table(name = "atenciones")
public class Atencion extends EntidadBase {

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "cita_id", nullable = false, unique = true, updatable = false)
	private Cita cita;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "paciente_id", nullable = false, updatable = false)
	private Paciente paciente;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "medico_id", nullable = false, updatable = false)
	private Usuario medico;

	@Setter(AccessLevel.NONE)
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private EstadoAtencion estado = EstadoAtencion.EN_CURSO;

	@Column(name = "inicio_en", nullable = false, updatable = false)
	private Instant inicioEn;

	@Setter(AccessLevel.NONE)
	@Column(name = "cerrada_en")
	private Instant cerradaEn;

	@Column(name = "motivo_consulta", nullable = false)
	private String motivoConsulta;

	@Column(name = "tiempo_enfermedad")
	private String tiempoEnfermedad;

	private String anamnesis;

	@Column(name = "examen_fisico")
	private String examenFisico;

	@Column(name = "plan_trabajo")
	private String planTrabajo;

	private String indicaciones;

	@Setter(AccessLevel.NONE)
	@ElementCollection
	@CollectionTable(name = "atencion_diagnosticos", joinColumns = @JoinColumn(name = "atencion_id"))
	@OrderColumn(name = "orden")
	private List<Diagnostico> diagnosticos = new ArrayList<>();

	@Setter(AccessLevel.NONE)
	@ElementCollection
	@CollectionTable(name = "atencion_receta", joinColumns = @JoinColumn(name = "atencion_id"))
	@OrderColumn(name = "orden")
	private List<ItemReceta> receta = new ArrayList<>();

	@Setter(AccessLevel.NONE)
	@OneToMany(mappedBy = "atencion")
	@OrderBy("creadoEn")
	private List<Adenda> adendas = new ArrayList<>();

	public boolean estaCerrada() {
		return estado == EstadoAtencion.CERRADA;
	}

	/** Reemplaza diagnósticos y receta del borrador. */
	void reemplazarContenido(List<Diagnostico> nuevosDiagnosticos, List<ItemReceta> nuevaReceta) {
		exigirEnCurso();
		diagnosticos.clear();
		diagnosticos.addAll(nuevosDiagnosticos);
		receta.clear();
		receta.addAll(nuevaReceta);
	}

	void cerrar(Instant ahora) {
		exigirEnCurso();
		estado = EstadoAtencion.CERRADA;
		cerradaEn = ahora;
	}

	Adenda agregarAdenda(Usuario autor, String texto) {
		if (!estaCerrada()) {
			throw new ReglaNegocioException("La atención sigue en curso: edítela directamente en lugar de agregar una adenda");
		}
		Adenda a = new Adenda(this, autor, texto);
		// Carga la lista antes de agregar: si no, Hibernate podría mostrar la adenda dos veces al leerla después
		adendas.size();
		adendas.add(a);
		return a;
	}

	void exigirEnCurso() {
		if (estaCerrada()) {
			throw new ReglaNegocioException("La atención está cerrada y no se puede modificar; agregue una adenda");
		}
	}

}
