package com.historiamed.backend.consultorio;

import com.historiamed.backend.common.entity.EntidadBase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "consultorios")
public class Consultorio extends EntidadBase {

	@Column(nullable = false, unique = true)
	private String nombre;

	@Column(nullable = false)
	private String especialidad;

	@Column(nullable = false)
	private boolean activo = true;

}
