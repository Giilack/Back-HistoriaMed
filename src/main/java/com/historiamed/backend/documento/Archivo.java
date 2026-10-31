package com.historiamed.backend.documento;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Contenido de un documento guardado en PostgreSQL. No se modifica ni se borra (principio P3). */
@Getter
@Entity
@Immutable
@Table(name = "archivos")
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Archivo {

	@Id
	private String clave;

	@Column(nullable = false)
	private byte[] contenido;

}
