package com.historiamed.backend.catalogo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/** Medicamento del catálogo. Catálogo de solo lectura. */
@Getter
@Entity
@Table(name = "medicamentos")
public class Medicamento {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String nombre;

	/** Sin tildes, en minúsculas; si hay varios, separados por " + ". Se usa para la alerta de alergias. */
	@Column(name = "principio_activo", nullable = false)
	private String principioActivo;

	@Column(nullable = false)
	private String concentracion;

	@Column(name = "forma_farmaceutica", nullable = false)
	private String formaFarmaceutica;

	/** Grupo farmacológico (PENICILINAS, AINES, SULFONAMIDAS...), para detectar alergias de grupo. */
	private String grupo;

	@Column(nullable = false)
	private boolean activo;

	/** "Amoxicilina 500 mg · Cápsula" */
	public String descripcion() {
		return nombre + " " + concentracion + " · " + formaFarmaceutica;
	}

}
