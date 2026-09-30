package com.historiamed.backend.paciente;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

import com.historiamed.backend.common.security.CifradoDatos;

/**
 * Migración de datos (Flyway, versión 11): cifra el documento, los teléfonos y la dirección de los pacientes que ya
 * estaban registrados en texto plano y calcula la huella del documento. Es un bean de Spring para usar la clave
 * de {@code CIFRADO_CLAVE}; Flyway la ejecuta entre V10 (columnas) y V12 (índice único por huella).
 * <p>
 * Es idempotente: los valores que ya tienen el prefijo de cifrado no se vuelven a cifrar.
 */
@Component
public class V11__CifrarDatosPacientes extends BaseJavaMigration {

	private final CifradoDatos cifrado;

	public V11__CifrarDatosPacientes(CifradoDatos cifrado) {
		this.cifrado = cifrado;
	}

	@Override
	public void migrate(Context context) throws SQLException {
		Connection conexion = context.getConnection();
		String seleccion = """
				SELECT id, numero_documento, telefono, direccion, contacto_emergencia_telefono
				FROM pacientes ORDER BY id""";
		String actualizacion = """
				UPDATE pacientes SET numero_documento = ?, numero_documento_huella = ?, telefono = ?, direccion = ?,
				    contacto_emergencia_telefono = ?
				WHERE id = ?""";
		try (PreparedStatement select = conexion.prepareStatement(seleccion);
				ResultSet filas = select.executeQuery();
				PreparedStatement update = conexion.prepareStatement(actualizacion)) {
			while (filas.next()) {
				String documento = filas.getString("numero_documento");
				String documentoPlano = CifradoDatos.estaCifrado(documento) ? cifrado.descifrar(documento) : documento;
				update.setString(1, cifrarSiFalta(documento));
				update.setString(2, cifrado.huella(documentoPlano));
				update.setString(3, cifrarSiFalta(filas.getString("telefono")));
				update.setString(4, cifrarSiFalta(filas.getString("direccion")));
				update.setString(5, cifrarSiFalta(filas.getString("contacto_emergencia_telefono")));
				update.setLong(6, filas.getLong("id"));
				update.addBatch();
			}
			update.executeBatch();
		}
	}

	private String cifrarSiFalta(String valor) {
		return valor == null || CifradoDatos.estaCifrado(valor) ? valor : cifrado.cifrar(valor);
	}

}
