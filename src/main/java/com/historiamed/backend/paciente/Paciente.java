package com.historiamed.backend.paciente;

import java.time.Instant;
import java.time.LocalDate;

import com.historiamed.backend.common.entity.EntidadBase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "pacientes")
public class Paciente extends EntidadBase {

	@Column(name = "numero_hc", nullable = false, unique = true, updatable = false)
	private String numeroHc;

	@Enumerated(EnumType.STRING)
	@Column(name = "tipo_documento", nullable = false)
	private TipoDocumento tipoDocumento;

	@Column(name = "numero_documento")
	private String numeroDocumento;

	@Column(nullable = false)
	private String nombres;

	@Column(name = "apellido_paterno", nullable = false)
	private String apellidoPaterno;

	@Column(name = "apellido_materno")
	private String apellidoMaterno;

	@Column(name = "fecha_nacimiento", nullable = false)
	private LocalDate fechaNacimiento;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Sexo sexo;

	private String telefono;

	private String email;

	private String direccion;

	@Column(name = "contacto_emergencia_nombre")
	private String contactoEmergenciaNombre;

	@Column(name = "contacto_emergencia_telefono")
	private String contactoEmergenciaTelefono;

	@Column(name = "contacto_emergencia_parentesco")
	private String contactoEmergenciaParentesco;

	@Enumerated(EnumType.STRING)
	@Column(name = "tipo_financiamiento", nullable = false)
	private TipoFinanciamiento tipoFinanciamiento = TipoFinanciamiento.PARTICULAR;

	@Column(name = "seguro_numero_afiliacion")
	private String seguroNumeroAfiliacion;

	@Column(name = "seguro_plan")
	private String seguroPlan;

	@Enumerated(EnumType.STRING)
	@Column(name = "seguro_estado")
	private EstadoSeguro seguroEstado;

	@Column(name = "seguro_verificado_en")
	private Instant seguroVerificadoEn;

	@Column(name = "orientado_afiliacion_sis", nullable = false)
	private boolean orientadoAfiliacionSis;

	@Column(nullable = false)
	private boolean activo = true;

	/** Usuario (ADMISION) que registró al paciente. Solo el id, para no acoplar módulos. */
	@Column(name = "creado_por", updatable = false)
	private Long creadoPor;

	public String nombreCompleto() {
		String apellidos = apellidoMaterno == null ? apellidoPaterno : apellidoPaterno + " " + apellidoMaterno;
		return apellidos + ", " + nombres;
	}

}
