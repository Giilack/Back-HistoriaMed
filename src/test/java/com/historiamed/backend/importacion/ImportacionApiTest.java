package com.historiamed.backend.importacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.historiamed.backend.TestcontainersConfiguration;
import com.historiamed.backend.ia.LimiteIaException;
import com.historiamed.backend.ia.ProveedorIA;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Importación masiva de PDF con servidor y PostgreSQL reales. La IA es un doble: responde según el nombre del paciente. */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = { "historiamed.demo.habilitada=true", "historiamed.demo.password=" + ImportacionApiTest.CLAVE,
		"historiamed.almacenamiento.tipo=bd", "historiamed.importacion.pausa-ms=50",
		"historiamed.importacion.pausa-tras-limite-ms=100" })
class ImportacionApiTest {

	static final String CLAVE = "Demo2026prueba";

	private static final AtomicInteger DNI = new AtomicInteger(82000000);

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

	private record Respuesta(int estado, JsonNode cuerpo) {
	}

	@BeforeEach
	void preparar() throws Exception {
		admision = token("admision.demo");
		triaje = token("triaje.demo");
		medico = token("dr.diaz");
		when(ia.disponible()).thenReturn(true);
		when(ia.modelo()).thenReturn("modelo-doble");
	}

	private void iaResponde(String dni, String nombres, String apellido) {
		when(ia.extraerJson(any(), anyString(), anyString(), any())).thenReturn("""
				{"paciente": {"tipoDocumento": "DNI", "numeroDocumento": "%s", "nombres": "%s", "apellidoPaterno": "%s",
				  "fechaNacimiento": "1970-02-03", "sexo": "FEMENINO"},
				 "datos": [
				  {"categoria": "ALERGIA", "descripcion": "Penicilina", "tipoAlergia": "MEDICAMENTO", "gravedad": "SEVERA",
				   "fragmentoOrigen": "Alérgica a penicilina"},
				  {"categoria": "ANTECEDENTE", "descripcion": "Apendicectomía en 2010", "tipoAntecedente": "QUIRURGICO",
				   "fragmentoOrigen": "Apendicectomía 2010"},
				  {"categoria": "LABORATORIO", "descripcion": "Glucosa", "valor": "96", "unidad": "mg/dL", "fecha": "2026-01-10",
				   "fragmentoOrigen": "Glucosa 96"}]}""".formatted(dni, nombres, apellido));
	}

	@Test
	@DisplayName("CP-32 Paciente nuevo: Admisión aprueba y se crea con su historia")
	void pacienteNuevo() throws Exception {
		String dni = String.valueOf(DNI.incrementAndGet());
		iaResponde(dni, "Julia", "Nueva");
		long id = subirYEsperar();

		JsonNode detalle = llamar("GET", "/api/importaciones/" + id, null, admision).cuerpo();
		assertThat(detalle.path("paciente").path("numeroDocumento").asString()).isEqualTo(dni);
		assertThat(detalle.path("datos")).hasSize(3);
		assertThat(detalle.path("existente").isNull()).isTrue();

		Respuesta r = llamar("POST", "/api/importaciones/" + id + "/aprobacion", aprobacion(dni, "Julia", "Nueva", Set.of(0, 1, 2), false), admision);

		assertThat(r.estado()).isEqualTo(200);
		assertThat(r.cuerpo().path("pacienteNuevo").asBoolean()).isTrue();
		assertThat(r.cuerpo().path("datosRegistrados").asInt()).isEqualTo(3);
		long paciente = r.cuerpo().path("pacienteId").asLong();
		assertThat(llamar("GET", "/api/pacientes/" + paciente + "/alergias", null, medico).cuerpo().toString()).contains("Penicilina");
		assertThat(llamar("GET", "/api/pacientes/" + paciente + "/antecedentes", null, medico).cuerpo().toString()).contains("Apendicectomía");
		assertThat(llamar("GET", "/api/pacientes/" + paciente + "/resultados-laboratorio", null, medico).cuerpo().toString()).contains("Glucosa");
		assertThat(llamar("GET", "/api/pacientes/" + paciente + "/documentos", null, medico).cuerpo().toString()).contains("sin revisión médica");
	}

	@Test
	@DisplayName("CP-33 Paciente existente: se agrega la historia y no se cambian sus datos personales")
	void pacienteExistente() throws Exception {
		String dni = String.valueOf(DNI.incrementAndGet());
		long paciente = registrarPaciente(dni, "Marta", "Antigua");
		iaResponde(dni, "MARTA", "ANTIGUA");
		long id = subirYEsperar();

		JsonNode existente = llamar("GET", "/api/importaciones/" + id, null, admision).cuerpo().path("existente");
		assertThat(existente.path("id").asLong()).isEqualTo(paciente);
		assertThat(existente.path("coincideNombre").asBoolean()).isTrue();

		Respuesta r = llamar("POST", "/api/importaciones/" + id + "/aprobacion", aprobacion(dni, "MARTA", "ANTIGUA", Set.of(1), false), admision);

		assertThat(r.cuerpo().path("pacienteNuevo").asBoolean()).isFalse();
		assertThat(r.cuerpo().path("pacienteId").asLong()).isEqualTo(paciente);
		assertThat(llamar("GET", "/api/pacientes/" + paciente, null, admision).cuerpo().path("nombres").asString()).isEqualTo("Marta");
		assertThat(llamar("GET", "/api/pacientes/" + paciente + "/antecedentes", null, medico).cuerpo()).hasSize(1);
		assertThat(llamar("GET", "/api/pacientes/" + paciente + "/alergias", null, medico).cuerpo()).isEmpty();
	}

	@Test
	@DisplayName("CP-34 Mismo DNI con otro nombre: no se mezcla sin confirmar")
	void coincidenciaDudosa() throws Exception {
		String dni = String.valueOf(DNI.incrementAndGet());
		registrarPaciente(dni, "Rosa", "Primera");
		iaResponde(dni, "Otra", "Persona");
		long id = subirYEsperar();

		Respuesta sinConfirmar = llamar("POST", "/api/importaciones/" + id + "/aprobacion", aprobacion(dni, "Otra", "Persona", Set.of(0), false), admision);
		assertThat(sinConfirmar.estado()).isBetween(400, 499);
		assertThat(sinConfirmar.cuerpo().toString()).contains("COINCIDENCIA_DUDOSA");

		Respuesta confirmada = llamar("POST", "/api/importaciones/" + id + "/aprobacion", aprobacion(dni, "Otra", "Persona", Set.of(0), true), admision);
		assertThat(confirmada.estado()).isEqualTo(200);
	}

	@Test
	@DisplayName("CP-35 Solo Admisión importa; mientras espera, los datos están cifrados")
	void permisosYCifrado() throws Exception {
		String dni = String.valueOf(DNI.incrementAndGet());
		iaResponde(dni, "Clara", "Cifrada");
		long id = subirYEsperar();

		assertThat(llamar("GET", "/api/importaciones", null, medico).estado()).isEqualTo(403);
		assertThat(llamar("GET", "/api/importaciones/" + id, null, triaje).estado()).isEqualTo(403);
		assertThat(jdbc.queryForObject("SELECT datos_cifrados FROM importaciones WHERE id = ?", String.class, id))
			.startsWith("v1:").doesNotContain(dni).doesNotContain("Penicilina");

		llamar("POST", "/api/importaciones/" + id + "/rechazo", Map.of("motivo", "Documento ilegible"), admision);

		assertThat(jdbc.queryForObject("SELECT datos_cifrados FROM importaciones WHERE id = ?", String.class, id)).isNull();
	}

	@Test
	@DisplayName("CP-36 Un archivo repetido o que no es PDF no entra a la cola")
	void archivoInvalido() throws Exception {
		iaResponde(String.valueOf(DNI.incrementAndGet()), "Ada", "Repetida");
		byte[] pdf = pdfUnico();

		assertThat(subir(pdf).statusCode()).isEqualTo(201);
		HttpResponse<String> repetido = subir(pdf);

		assertThat(repetido.statusCode()).isBetween(400, 499);
		assertThat(repetido.body()).contains("ARCHIVO_REPETIDO");
		assertThat(subir(new byte[] { 'M', 'Z', 0, 0 }).statusCode()).isBetween(400, 499);
	}

	@Test
	@DisplayName("CP-37 Con el límite de la IA, el documento espera y se procesa después")
	void limiteDeLaIa() throws Exception {
		String dni = String.valueOf(DNI.incrementAndGet());
		iaResponde(dni, "Eva", "Espera");
		String correcta = ia.extraerJson(new byte[0], "x", "x", Map.of());
		when(ia.extraerJson(any(), anyString(), anyString(), any()))
			.thenThrow(new LimiteIaException("Límite de uso"))
			.thenReturn(correcta);

		long id = subirYEsperar();

		assertThat(llamar("GET", "/api/importaciones/" + id, null, admision).estado()).isEqualTo(200);
		assertThat(jdbc.queryForObject("SELECT intentos FROM importaciones WHERE id = ?", Integer.class, id)).isEqualTo(2);
	}

	@Test
	@DisplayName("CP-39 Alergia sin gravedad en el documento: se registra como severa, con el motivo")
	void alergiaSinGravedad() throws Exception {
		String dni = String.valueOf(DNI.incrementAndGet());
		when(ia.extraerJson(any(), anyString(), anyString(), any())).thenReturn("""
				{"paciente": {"tipoDocumento": "DNI", "numeroDocumento": "%s", "nombres": "Luz", "apellidoPaterno": "Prudente",
				  "fechaNacimiento": "1970-02-03", "sexo": "FEMENINO"},
				 "datos": [{"categoria": "ALERGIA", "descripcion": "Sulfas", "tipoAlergia": "MEDICAMENTO",
				   "fragmentoOrigen": "Alergias: Sulfas"}]}""".formatted(dni));
		long id = subirYEsperar();

		long paciente = llamar("POST", "/api/importaciones/" + id + "/aprobacion", aprobacion(dni, "Luz", "Prudente", Set.of(0), false), admision)
			.cuerpo().path("pacienteId").asLong();

		String alergias = llamar("GET", "/api/pacientes/" + paciente + "/alergias", null, medico).cuerpo().toString();
		assertThat(alergias).contains("SEVERA").contains("por precaución");
	}

	@Test
	@DisplayName("CP-38 Lote de 20 PDF: se procesan todos, de uno en uno")
	void loteDeVeinte() throws Exception {
		iaResponde(String.valueOf(DNI.incrementAndGet()), "Lote", "Prueba");
		List<Long> ids = new java.util.ArrayList<>();
		for (int i = 0; i < 20; i++) {
			ids.add(json.readTree(subir(pdfUnico()).body()).path("id").asLong());
		}

		for (long id : ids) {
			esperar(id);
		}

		assertThat(ids).hasSize(20);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM importaciones WHERE id = ANY(?) AND estado = 'LISTA'", Long.class,
				(Object) ids.toArray(new Long[0]))).isEqualTo(20);
	}

	// ----------------------------------------------------------------------------------------------------------------

	private static byte[] pdfUnico() {
		return ("%PDF-1.4 ficticio " + UUID.randomUUID()).getBytes(StandardCharsets.US_ASCII);
	}

	private long subirYEsperar() throws Exception {
		HttpResponse<String> r = subir(pdfUnico());
		assertThat(r.statusCode()).isEqualTo(201);
		long id = json.readTree(r.body()).path("id").asLong();
		esperar(id);
		return id;
	}

	private void esperar(long id) throws Exception {
		for (int i = 0; i < 300; i++) {
			String estado = jdbc.queryForObject("SELECT estado FROM importaciones WHERE id = ?", String.class, id);
			if ("LISTA".equals(estado) || "ERROR".equals(estado)) {
				assertThat(estado).isEqualTo("LISTA");
				return;
			}
			Thread.sleep(50);
		}
		throw new AssertionError("La importación " + id + " no terminó");
	}

	private static Map<String, Object> aprobacion(String dni, String nombres, String apellido, Set<Integer> aceptados, boolean confirmar) {
		return Map.of("paciente", datos(dni, nombres, apellido), "aceptados", aceptados, "confirmarCoincidencia", confirmar);
	}

	private static Map<String, Object> datos(String dni, String nombres, String apellido) {
		return Map.of("tipoDocumento", "DNI", "numeroDocumento", dni, "nombres", nombres, "apellidoPaterno", apellido,
				"fechaNacimiento", "1970-02-03", "sexo", "FEMENINO");
	}

	private long registrarPaciente(String dni, String nombres, String apellido) throws Exception {
		return llamar("POST", "/api/pacientes", datos(dni, nombres, apellido), admision).cuerpo().path("id").asLong();
	}

	private String token(String usuario) throws Exception {
		return llamar("POST", "/api/auth/login", Map.of("username", usuario, "password", CLAVE), null).cuerpo().path("accessToken").asString();
	}

	private HttpResponse<String> subir(byte[] contenido) throws Exception {
		String limite = "limite-de-prueba";
		String fin = "\r\n";
		var cuerpo = new ByteArrayOutputStream();
		cuerpo.write(("--" + limite + fin + "Content-Disposition: form-data; name=\"archivo\"; filename=\"historia.pdf\"" + fin
				+ "Content-Type: application/pdf" + fin + fin).getBytes(StandardCharsets.UTF_8));
		cuerpo.write(contenido);
		cuerpo.write((fin + "--" + limite + "--" + fin).getBytes(StandardCharsets.UTF_8));
		return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/importaciones"))
			.header("Authorization", "Bearer " + admision)
			.header("Content-Type", "multipart/form-data; boundary=" + limite)
			.POST(HttpRequest.BodyPublishers.ofByteArray(cuerpo.toByteArray()))
			.build(), HttpResponse.BodyHandlers.ofString());
	}

	private Respuesta llamar(String metodo, String ruta, Object cuerpo, String token) throws Exception {
		HttpRequest.Builder p = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
			.header("Content-Type", "application/json")
			.method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody()
					: HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo)));
		if (token != null) {
			p.header("Authorization", "Bearer " + token);
		}
		HttpResponse<String> r = http.send(p.build(), HttpResponse.BodyHandlers.ofString());
		return new Respuesta(r.statusCode(), r.body().isBlank() ? json.createObjectNode() : json.readTree(r.body()));
	}

}
