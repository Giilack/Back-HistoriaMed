package com.historiamed.backend.catalogo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/** Diagnóstico de la Clasificación Internacional de Enfermedades (CIE-10). Catálogo de solo lectura. */
@Getter
@Entity
@Table(name = "cie10")
public class Cie10 {

	@Id
	private String codigo;

	@Column(nullable = false)
	private String descripcion;

}
