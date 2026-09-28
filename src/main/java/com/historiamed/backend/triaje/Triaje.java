package com.historiamed.backend.triaje;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.historiamed.backend.cita.Cita;
import com.historiamed.backend.cita.Prioridad;
import com.historiamed.backend.common.entity.EntidadBase;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "triajes")
public class Triaje extends EntidadBase {

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "cita_id", nullable = false, unique = true, updatable = false)
	private Cita cita;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "paciente_id", nullable = false, updatable = false)
	private Paciente paciente;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "registrado_por", nullable = false, updatable = false)
	private Usuario registradoPor;

	@Column(name = "fecha_hora", nullable = false, updatable = false)
	private Instant fechaHora;

	@Column(name = "motivo_consulta", nullable = false)
	private String motivoConsulta;

	@Column(name = "presion_sistolica")
	private Integer presionSistolica;

	@Column(name = "presion_diastolica")
	private Integer presionDiastolica;

	@Column(name = "frecuencia_cardiaca", nullable = false)
	private Integer frecuenciaCardiaca;

	@Column(name = "frecuencia_respiratoria")
	private Integer frecuenciaRespiratoria;

	@Column(nullable = false, precision = 4, scale = 1)
	private BigDecimal temperatura;

	@Column(nullable = false)
	private Integer saturacion;

	@Column(nullable = false, precision = 5, scale = 2)
	private BigDecimal peso;

	@Column(precision = 4, scale = 1)
	private BigDecimal talla;

	@Column(precision = 4, scale = 1)
	private BigDecimal imc;

	@Column(name = "perimetro_abdominal", precision = 4, scale = 1)
	private BigDecimal perimetroAbdominal;

	@Column(nullable = false)
	private boolean gestante;

	@Column(nullable = false)
	private boolean discapacidad;

	@Enumerated(EnumType.STRING)
	@Column(name = "prioridad_sugerida", nullable = false)
	private Prioridad prioridadSugerida;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Prioridad prioridad;

	@Column(name = "justificacion_prioridad")
	private String justificacionPrioridad;

	private String observaciones;

	@ElementCollection
	@CollectionTable(name = "triaje_alertas", joinColumns = @JoinColumn(name = "triaje_id"))
	private List<Alerta> alertas = new ArrayList<>();

}
