package com.historiamed.backend.cita;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import com.historiamed.backend.common.entity.EntidadBase;
import com.historiamed.backend.common.exception.ReglaNegocioException;
import com.historiamed.backend.consultorio.Consultorio;
import com.historiamed.backend.paciente.Paciente;
import com.historiamed.backend.usuario.Usuario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "citas")
public class Cita extends EntidadBase {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "paciente_id", nullable = false, updatable = false)
	private Paciente paciente;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "medico_id", nullable = false)
	private Usuario medico;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "consultorio_id", nullable = false)
	private Consultorio consultorio;

	@Column(nullable = false)
	private LocalDate fecha;

	/** null si el paciente llegó sin cita. */
	private LocalTime hora;

	@Column(name = "sin_cita", nullable = false, updatable = false)
	private boolean sinCita;

	private String motivo;

	/** Solo cambia con {@link #cambiarEstado}, que valida el flujo. */
	@Setter(AccessLevel.NONE)
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private EstadoCita estado;

	@Column(name = "numero_turno")
	private Integer numeroTurno;

	@Setter(AccessLevel.NONE)
	@Column(name = "llegada_en")
	private Instant llegadaEn;

	@Setter(AccessLevel.NONE)
	@Column(name = "triaje_en")
	private Instant triajeEn;

	@Setter(AccessLevel.NONE)
	@Column(name = "consulta_inicio_en")
	private Instant consultaInicioEn;

	@Setter(AccessLevel.NONE)
	@Column(name = "atendido_en")
	private Instant atendidoEn;

	@Setter(AccessLevel.NONE)
	@Column(name = "cancelada_en")
	private Instant canceladaEn;

	@Column(name = "motivo_cancelacion")
	private String motivoCancelacion;

	@Column(name = "creado_por", updatable = false)
	private Long creadoPor;

	/** Estado inicial: PROGRAMADA (con cita) o EN_ESPERA_TRIAJE (llegó sin cita). */
	public void iniciar(boolean llegoSinCita, Instant ahora) {
		this.sinCita = llegoSinCita;
		this.estado = llegoSinCita ? EstadoCita.EN_ESPERA_TRIAJE : EstadoCita.PROGRAMADA;
		if (llegoSinCita) {
			this.llegadaEn = ahora;
		}
	}

	/**
	 * Avanza en el flujo de estados y guarda la hora del cambio. Rechaza saltos no permitidos.
	 */
	public void cambiarEstado(EstadoCita nuevo, Instant ahora) {
		if (!estado.puedePasarA(nuevo)) {
			throw new ReglaNegocioException("No se puede pasar una cita de " + estado + " a " + nuevo);
		}
		switch (nuevo) {
			case EN_ESPERA_TRIAJE -> llegadaEn = ahora;
			case EN_ESPERA_CONSULTA -> triajeEn = ahora;
			case EN_CONSULTA -> consultaInicioEn = ahora;
			case ATENDIDO -> atendidoEn = ahora;
			case CANCELADA -> canceladaEn = ahora;
			default -> {
			}
		}
		estado = nuevo;
	}

}
