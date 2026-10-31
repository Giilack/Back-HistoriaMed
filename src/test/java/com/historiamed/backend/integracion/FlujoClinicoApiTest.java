package com.historiamed.backend.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.historiamed.backend.TestcontainersConfiguration;
import com.historiamed.backend.ia.ProveedorIA;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pruebas funcionales de la API de punta a punta: servidor real, JWT real y PostgreSQL real con las migraciones de
 * Flyway y los datos ficticios de demostración. Cada prueba indica el caso del plan de pruebas que automatiza.
 */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = { "historiamed.demo.habilitada=true", "historiamed.demo.password=" + FlujoClinicoApiTest.CLAVE,
		"historiamed.almacenamiento.tipo=bd" })
class FlujoClinicoApiTest {

	static final String CLAVE = "Demo2026prueba";

	/** Cada prueba registra su propio paciente ficticio, con un DNI distinto. */
	private static final AtomicInteger DNI = new AtomicInteger(81000000);

	private final HttpClient http = HttpClient.newHttpClient();

	private final JsonMapper json = JsonMapper.builder().build();

	@LocalServerPort
	private int puerto;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoBean
	private ProveedorIA ia;

	private String admision;

	private String triaje;

	private String medico;

	private String admin;

	private long medicoId;

	private long consultorioId;

	private record Respuesta(int estado, JsonNode cuerpo) {
	}

	@BeforeEach
	void iniciarSesiones() throws Exception {
		admision = token("admision.demo");
		triaje = token("triaje.demo");
		medico = token("dr.diaz");
		admin = token("admin.prueba");
		for (JsonNode m : llamar("GET", "/api/usuarios/medicos", null, admision).cuerpo()) {
			if (m.path("nombreCompleto").asString().contains("Díaz")) {
				medicoId = m.path("id").asLong();
			}
		}
		consultorioId = llamar("GET", "/api/consultorios", null, admision).cuerpo().get(0).path("id").asLong();
	}

	// --- Autenticación y autorización -------------------------------------------------------------------------------

	@Test
	@DisplayName("CP-01 Inicio de sesión con credenciales válidas")
	void loginValido() throws Exception {
		Respuesta r = llamar("POST", "/api/auth/login", Map.of("username", "dr.diaz", "password", CLAVE), null);

		assertThat(r.estado()).isEqualTo(200);
		assertThat(r.cuerpo().path("accessToken").asString()).isNotBlank();
		assertThat(r.cuerpo().path("usuario").path("rol").asString()).isEqualTo("MEDICO");
	}

	@Test
	@DisplayName("CP-02 Contraseña incorrecta: mensaje genérico y auditado")
	void loginIncorrecto() throws Exception {
		Respuesta r = llamar("POST", "/api/auth/login", Map.of("username", "dra.ruiz", "password", "Incorrecta123"), null);

		assertThat(r.estado()).isEqualTo(401);
		assertThat(r.cuerpo().toString()).doesNotContain("dra.ruiz");
		assertThat(contarAuditoria("LOGIN_FALLIDO")).isPositive();
	}

	@Test
	@DisplayName("CP-05 Cada rol solo entra a lo que le corresponde")
	void autorizacionPorRol() throws Exception {
		long paciente = registrarPaciente("Prueba", "Rol");

		assertThat(llamar("GET", "/api/usuarios", null, medico).estado()).isEqualTo(403);
		assertThat(llamar("GET", "/api/auditoria", null, admision).estado()).isEqualTo(403);
		assertThat(llamar("GET", "/api/pacientes/" + paciente + "/atenciones", null, admin).estado()).isEqualTo(403);
		assertThat(llamar("GET", "/api/pacientes/" + paciente + "/atenciones", null, admision).estado()).isEqualTo(403);
		assertThat(llamar("GET", "/api/pacientes/" + paciente + "/atenciones", null, medico).estado()).isEqualTo(200);
		assertThat(llamar("GET", "/api/reportes/resumen", null, medico).estado()).isEqualTo(403);
		assertThat(llamar("GET", "/api/reportes/resumen", null, admin).estado()).isEqualTo(200);
	}

	@Test
	@DisplayName("CP-06 Token ausente o alterado")
	void tokenInvalido() throws Exception {
		assertThat(llamar("GET", "/api/pacientes", null, null).estado()).isEqualTo(401);
		assertThat(llamar("GET", "/api/pacientes", null, medico.substring(0, medico.length() - 3) + "abc").estado())
			.isEqualTo(401);
	}

	// --- Pacientes --------------------------------------------------------------------------------------------------

	@Test
	@DisplayName("CP-07 y CP-08 Registro de paciente y DNI duplicado")
	void registroYDuplicado() throws Exception {
		Map<String, Object> datos = paciente(String.valueOf(DNI.incrementAndGet()), "Ana", "Duplicada");

		Respuesta primero = llamar("POST", "/api/pacientes", datos, admision);
		Respuesta segundo = llamar("POST", "/api/pacientes", datos, admision);

		assertThat(primero.estado()).isEqualTo(201);
		assertThat(primero.cuerpo().path("numeroHc").asString()).startsWith("HC-");
		assertThat(segundo.estado()).isBetween(400, 499);
	}

	@Test
	@DisplayName("CP-09 y CP-10 Búsqueda por DNI cifrado y sin tildes")
	void busquedas() throws Exception {
		assertThat(llamar("GET", "/api/pacientes?q=45678912", null, admision).cuerpo().path("contenido")).hasSize(1);
		assertThat(llamar("GET", "/api/pacientes?q=quispe", null, admision).cuerpo().path("contenido")).isNotEmpty();
		assertThat(llamar("GET", "/api/pacientes?q=huaman", null, admision).cuerpo().path("contenido")).isNotEmpty();
	}

	@Test
	@DisplayName("CP-11 Inyección SQL en el buscador")
	void inyeccionSql() throws Exception {
		Respuesta r = llamar("GET", "/api/pacientes?q=" + URLEncoder.encode("' OR '1'='1", StandardCharsets.UTF_8), null,
				admision);

		assertThat(r.estado()).isEqualTo(200);
		assertThat(r.cuerpo().path("contenido")).isEmpty();
	}

	@Test
	@DisplayName("CP-19 El DNI y el teléfono quedan cifrados en la base")
	void datosSensiblesCifrados() throws Exception {
		String dni = String.valueOf(DNI.incrementAndGet());
		long id = llamar("POST", "/api/pacientes", paciente(dni, "Luz", "Cifrada"), admision).cuerpo().path("id").asLong();

		Map<String, Object> fila = jdbc.queryForMap("SELECT numero_documento, telefono FROM pacientes WHERE id = ?", id);

		assertThat(fila.get("numero_documento").toString()).doesNotContain(dni);
		assertThat(fila.get("telefono").toString()).doesNotContain("987654321");
	}

	// --- Flujo clínico ----------------------------------------------------------------------------------------------

	@Test
	@DisplayName("CP-12, CP-15 y CP-16 Flujo completo: llegada, triaje, atención y cierre inmutable")
	void flujoCompleto() throws Exception {
		long paciente = registrarPaciente("Elena", "Flujo");
		Respuesta cita = llamar("POST", "/api/citas/sin-cita",
				Map.of("pacienteId", paciente, "medicoId", medicoId, "consultorioId", consultorioId, "motivo", "Fiebre"),
				admision);
		assertThat(cita.estado()).isEqualTo(201);
		assertThat(cita.cuerpo().path("estado").asString()).isEqualTo("EN_ESPERA_TRIAJE");
		long citaId = cita.cuerpo().path("id").asLong();

		assertThat(llamar("POST", "/api/citas/" + citaId + "/triaje", triaje(), triaje).estado()).isEqualTo(201);
		Respuesta atencion = llamar("POST", "/api/citas/" + citaId + "/atencion", null, medico);
		assertThat(atencion.estado()).isBetween(200, 201);
		long atencionId = atencion.cuerpo().path("id").asLong();

		Respuesta incompleta = llamar("POST", "/api/atenciones/" + atencionId + "/cierre", atencion(List.of()), medico);
		assertThat(incompleta.estado()).isBetween(400, 499);

		Map<String, Object> completa = atencion(List.of(Map.of("codigo", "J02.9", "tipo", "PRESUNTIVO", "principal", true)));
		assertThat(llamar("POST", "/api/atenciones/" + atencionId + "/cierre", completa, medico).estado()).isEqualTo(200);

		assertThat(llamar("PUT", "/api/atenciones/" + atencionId, completa, medico).estado()).isBetween(400, 499);
		assertThat(llamar("GET", "/api/atenciones/" + atencionId, null, medico).cuerpo().path("estado").asString())
			.isEqualTo("CERRADA");
	}

	@Test
	@DisplayName("CP-14 Recetar con alergia registrada exige confirmación y justificación")
	void alergiaAlRecetar() throws Exception {
		long paciente = registrarPaciente("Marta", "Alergica");
		assertThat(llamar("POST", "/api/pacientes/" + paciente + "/alergias", Map.of("tipo", "MEDICAMENTO", "sustancia",
				"Penicilina", "reaccion", "Urticaria", "gravedad", "SEVERA"), triaje).estado()).isEqualTo(201);
		long citaId = llamar("POST", "/api/citas/sin-cita",
				Map.of("pacienteId", paciente, "medicoId", medicoId, "consultorioId", consultorioId, "motivo", "Faringitis"),
				admision).cuerpo().path("id").asLong();
		llamar("POST", "/api/citas/" + citaId + "/triaje", triaje(), triaje);
		long atencionId = llamar("POST", "/api/citas/" + citaId + "/atencion", null, medico).cuerpo().path("id").asLong();
		long amoxicilina = llamar("GET", "/api/medicamentos?q=amoxicilina", null, medico).cuerpo().get(0).path("id").asLong();
		Map<String, Object> item = Map.of("medicamentoId", amoxicilina, "dosis", "1 cápsula", "via", "ORAL", "frecuencia",
				"cada 8 horas", "duracion", "7 días", "cantidad", 21);
		Map<String, Object> cuerpo = new java.util.HashMap<>(atencion(List.of(Map.of("codigo", "J02.9", "tipo", "PRESUNTIVO", "principal", true))));
		cuerpo.put("receta", List.of(item));

		Respuesta rechazada = llamar("POST", "/api/atenciones/" + atencionId + "/cierre", cuerpo, medico);

		assertThat(rechazada.estado()).isBetween(400, 499);
		assertThat(rechazada.cuerpo().toString().toLowerCase()).contains("alergia");
	}

	@Test
	@DisplayName("CP-17 La auditoría no se puede modificar")
	void auditoriaInmutable() throws Exception {
		llamar("GET", "/api/pacientes?q=quispe", null, medico);

		assertThatThrownBy(() -> jdbc.update("UPDATE registros_auditoria SET detalle = 'alterado'"))
			.isInstanceOf(DataAccessException.class);
	}

	@Test
	@DisplayName("CP-23 Consultar la historia queda en la auditoría")
	void accesoClinicoAuditado() throws Exception {
		long paciente = registrarPaciente("Ivan", "Auditado");
		long antes = contarAuditoria("VER");

		llamar("GET", "/api/pacientes/" + paciente + "/atenciones", null, medico);

		assertThat(contarAuditoria("VER")).isGreaterThan(antes);
	}

	@Test
	@DisplayName("CP-24 Reportes: el administrador solo ve cifras, nunca datos de pacientes")
	void reportesSinDatosPersonales() throws Exception {
		Respuesta r = llamar("GET", "/api/reportes/resumen", null, admin);

		assertThat(r.cuerpo().path("totales").path("atendidas").asLong()).isPositive();
		assertThat(r.cuerpo().toString()).doesNotContain("Mamani").doesNotContain("45678912");
	}

	// --- Documentos y asistente: la IA se reemplaza por un doble para no gastar la cuota ni depender de la red ---------

	@Test
	@DisplayName("CP-18 Un ejecutable renombrado a .pdf se rechaza y un PDF se guarda")
	void formatoDeArchivo() throws Exception {
		long paciente = registrarPaciente("Rosa", "Documento");

		HttpResponse<String> falso = subir(paciente, "informe.pdf", new byte[] { 'M', 'Z', 0, 0 });
		HttpResponse<String> pdf = subir(paciente, "informe.pdf", "%PDF-1.4 ficticio".getBytes(StandardCharsets.US_ASCII));

		assertThat(falso.statusCode()).isBetween(400, 499);
		assertThat(pdf.statusCode()).isEqualTo(201);
		assertThat(llamar("GET", "/api/pacientes/" + paciente + "/documentos", null, medico).cuerpo()).hasSize(1);
		long documento = json.readTree(pdf.body()).path("id").asLong();
		HttpResponse<byte[]> archivo = http.send(HttpRequest
			.newBuilder(URI.create("http://localhost:" + puerto + "/api/documentos/" + documento + "/archivo"))
			.header("Authorization", "Bearer " + medico)
			.build(), HttpResponse.BodyHandlers.ofByteArray());
		assertThat(new String(archivo.body(), StandardCharsets.US_ASCII)).startsWith("%PDF");
	}

	@Test
	@DisplayName("CP-25 El asistente cita la fuente, descarta citas inventadas y alerta alergias")
	void asistenteClinico() throws Exception {
		long paciente = registrarPaciente("Teresa", "Asistente");
		llamar("POST", "/api/pacientes/" + paciente + "/alergias", Map.of("tipo", "MEDICAMENTO", "sustancia", "Penicilina",
				"reaccion", "Urticaria", "gravedad", "SEVERA"), triaje);
		long citaId = llamar("POST", "/api/citas/sin-cita",
				Map.of("pacienteId", paciente, "medicoId", medicoId, "consultorioId", consultorioId, "motivo", "Fiebre"),
				admision).cuerpo().path("id").asLong();
		llamar("POST", "/api/citas/" + citaId + "/triaje", triaje(), triaje);
		long atencionId = llamar("POST", "/api/citas/" + citaId + "/atencion", null, medico).cuerpo().path("id").asLong();
		llamar("POST", "/api/atenciones/" + atencionId + "/cierre",
				atencion(List.of(Map.of("codigo", "J02.9", "tipo", "PRESUNTIVO", "principal", true))), medico);
		when(ia.disponible()).thenReturn(true);
		when(ia.modelo()).thenReturn("modelo-doble");
		when(ia.responderJson(anyString(), anyString(), any())).thenReturn("""
				{"respuesta": "Alergia a penicilina. Se podría usar amoxicilina.", "fuentes": ["AL1", "A1", "INVENTADA"],
				 "esRecomendacion": true, "medicamentosSugeridos": ["amoxicilina"]}""");

		Respuesta r = llamar("POST", "/api/pacientes/" + paciente + "/asistente",
				Map.of("pregunta", "¿Qué antibiótico le doy?", "historial", List.of()), medico);

		assertThat(r.estado()).isEqualTo(200);
		assertThat(r.cuerpo().path("fuentes")).hasSize(2);
		assertThat(r.cuerpo().path("sugerencia").asBoolean()).isTrue();
		assertThat(r.cuerpo().path("advertencias").toString()).contains("Alerta de alergia");
		assertThat(contarAuditoria("CONSULTA_IA")).isPositive();
		assertThat(llamar("POST", "/api/pacientes/" + paciente + "/asistente",
				Map.of("pregunta", "¿Tiene alergias?", "historial", List.of()), triaje).estado()).isEqualTo(403);
	}

	// ----------------------------------------------------------------------------------------------------------------

	private HttpResponse<String> subir(long paciente, String nombre, byte[] contenido) throws Exception {
		String limite = "limite-de-prueba";
		var cuerpo = new java.io.ByteArrayOutputStream();
		String fin = "\r\n";
		cuerpo.write(("--" + limite + fin + "Content-Disposition: form-data; name=\"archivo\"; filename=\"" + nombre + "\"" + fin
				+ "Content-Type: application/pdf" + fin + fin).getBytes(StandardCharsets.UTF_8));
		cuerpo.write(contenido);
		cuerpo.write((fin + "--" + limite + fin + "Content-Disposition: form-data; name=\"tipo\"" + fin + fin + "LABORATORIO" + fin
				+ "--" + limite + "--" + fin).getBytes(StandardCharsets.UTF_8));
		return http.send(HttpRequest
			.newBuilder(URI.create("http://localhost:" + puerto + "/api/pacientes/" + paciente + "/documentos"))
			.header("Authorization", "Bearer " + medico)
			.header("Content-Type", "multipart/form-data; boundary=" + limite)
			.POST(HttpRequest.BodyPublishers.ofByteArray(cuerpo.toByteArray()))
			.build(), HttpResponse.BodyHandlers.ofString());
	}

	private long contarAuditoria(String accion) {
		Long total = jdbc.queryForObject("SELECT count(*) FROM registros_auditoria WHERE accion = ?", Long.class, accion);
		return total == null ? 0 : total;
	}

	private long registrarPaciente(String nombres, String apellido) throws Exception {
		Respuesta r = llamar("POST", "/api/pacientes", paciente(String.valueOf(DNI.incrementAndGet()), nombres, apellido),
				admision);
		assertThat(r.estado()).isEqualTo(201);
		return r.cuerpo().path("id").asLong();
	}

	private static Map<String, Object> paciente(String dni, String nombres, String apellido) {
		return Map.of("tipoDocumento", "DNI", "numeroDocumento", dni, "nombres", nombres, "apellidoPaterno", apellido,
				"fechaNacimiento", "1990-05-20", "sexo", "FEMENINO", "telefono", "987654321", "direccion", "Calle Ficticia 123");
	}

	private static Map<String, Object> triaje() {
		return Map.of("motivoConsulta", "Fiebre y dolor de garganta", "presionSistolica", 118, "presionDiastolica", 76,
				"frecuenciaCardiaca", 80, "temperatura", "37.5", "saturacion", 98, "peso", "60", "talla", "160");
	}

	private static Map<String, Object> atencion(List<Map<String, Object>> diagnosticos) {
		return Map.of("motivoConsulta", "Fiebre", "anamnesis", "Dos días de fiebre.", "examenFisico", "Faringe congestiva.",
				"diagnosticos", diagnosticos, "receta", List.of());
	}

	private String token(String usuario) throws Exception {
		if ("admin.prueba".equals(usuario)) {
			crearAdministrador();
		}
		return llamar("POST", "/api/auth/login", Map.of("username", usuario, "password", CLAVE), null).cuerpo()
			.path("accessToken").asString();
	}

	/** Administrador de prueba con la misma contraseña que los usuarios demo, sin cambio obligatorio. */
	private void crearAdministrador() {
		jdbc.update("""
				INSERT INTO usuarios (username, password_hash, nombres, apellidos, dni, rol, debe_cambiar_password)
				SELECT 'admin.prueba', password_hash, 'Admin', 'Prueba', '90000099', 'ADMIN', FALSE
				FROM usuarios WHERE username = 'dr.diaz' ON CONFLICT DO NOTHING""");
	}

	private Respuesta llamar(String metodo, String ruta, Object cuerpo, String token) throws Exception {
		HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
			.header("Content-Type", "application/json")
			.method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody()
					: HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo)));
		if (token != null) {
			peticion.header("Authorization", "Bearer " + token);
		}
		HttpResponse<String> r = http.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
		return new Respuesta(r.statusCode(), r.body().isBlank() ? json.createObjectNode() : json.readTree(r.body()));
	}

}
