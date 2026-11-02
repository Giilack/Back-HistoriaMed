package com.historiamed.backend.importacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.historiamed.backend.TestcontainersConfiguration;
import com.historiamed.backend.ia.ErrorIaException;
import com.historiamed.backend.ia.LimiteIaException;
import com.historiamed.backend.ia.ProveedorIA;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Importación masiva de PDF con servidor y PostgreSQL reales. La IA es un doble: responde según el nombre del paciente. */
@Testcontainers(disabledWithoutDocker = true)
@Import({ TestcontainersConfiguration.class, ImportacionApiTest.ConfiguracionIa.class })
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

	@Autowired
	private IaDoble ia;

	@Autowired
	private ImportacionService importaciones;

	private String admision;

	private String triaje;

	private String medico;

	private record Respuesta(int estado, JsonNode cuerpo) {
	}

	/** La cola llama a la IA desde otro hilo: este doble se puede cambiar mientras tanto, a diferencia de un mock. */
	static class IaDoble implements ProveedorIA {

		private volatile String respuesta = "{}";

		private final AtomicInteger limitesPendientes = new AtomicInteger();

		private final AtomicInteger erroresPendientes = new AtomicInteger();

		@Override
		public boolean disponible() {
			return true;
		}

		@Override
		public String modelo() {
			return "modelo-doble";
		}

		@Override
		public String extraerJson(byte[] documento, String mimeType, String instrucciones, Map<String, Object> esquema) {
			if (limitesPendientes.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
				throw new LimiteIaException("Límite de uso");
			}
			if (erroresPendientes.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
				throw new ErrorIaException("La IA rechazó el contenido");
			}
			return respuesta;
		}

		@Override
		public String responderJson(String instrucciones, String contenido, Map<String, Object> esquema) {
			return respuesta;
		}

	}

	@TestConfiguration(proxyBeanMethods = false)
	static class ConfiguracionIa {

		@Bean
		@Primary
		IaDoble iaDoble() {
			return new IaDoble();
		}

	}

	@BeforeEach
	void preparar() throws Exception {
		admision = token("admision.demo");
		triaje = token("triaje.demo");
		medico = token("dr.diaz");
	}

	private void iaResponde(String dni, String nombres, String apellido) {
		ia.respuesta = """
				{"paciente": {"tipoDocumento": "DNI", "numeroDocumento": "%s", "nombres": "%s", "apellidoPaterno": "%s",
				  "fechaNacimiento": "1970-02-03", "sexo": "FEMENINO"},
				 "datos": [
				  {"categoria": "ALERGIA", "descripcion": "Penicilina", "tipoAlergia": "MEDICAMENTO", "gravedad": "SEVERA",
				   "fragmentoOrigen": "Alérgica a penicilina"},
				  {"categoria": "ANTECEDENTE", "descripcion": "Apendicectomía en 2010", "tipoAntecedente": "QUIRURGICO",
				   "fragmentoOrigen": "Apendicectomía 2010"},
				  {"categoria": "LABORATORIO", "descripcion": "Glucosa", "valor": "96", "unidad": "mg/dL", "fecha": "2026-01-10",
				   "fragmentoOrigen": "Glucosa 96"}]}""".formatted(dni, nombres, apellido);
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

		Respuesta r = llamar("POST", "/api/importaciones/" + id + "/aprobacion", aprobacion(id, dni, "Julia", "Nueva", Set.of(0, 1, 2), false), admision);

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

		Respuesta r = llamar("POST", "/api/importaciones/" + id + "/aprobacion", aprobacion(id, dni, "MARTA", "ANTIGUA", Set.of(1), false), admision);

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

		Respuesta sinConfirmar = llamar("POST", "/api/importaciones/" + id + "/aprobacion", aprobacion(id, dni, "Otra", "Persona", Set.of(0), false), admision);
		assertThat(sinConfirmar.estado()).isBetween(400, 499);
		assertThat(sinConfirmar.cuerpo().toString()).contains("COINCIDENCIA_DUDOSA");

		Respuesta confirmada = llamar("POST", "/api/importaciones/" + id + "/aprobacion", aprobacion(id, dni, "Otra", "Persona", Set.of(0), true), admision);
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
		ia.limitesPendientes.set(1);

		long id = subirYEsperar();

		assertThat(llamar("GET", "/api/importaciones/" + id, null, admision).estado()).isEqualTo(200);
		assertThat(jdbc.queryForObject("SELECT intentos FROM importaciones WHERE id = ?", Integer.class, id)).isEqualTo(2);
	}

	@Test
	@DisplayName("CP-39 Alergia sin gravedad en el documento: se registra como severa, con el motivo")
	void alergiaSinGravedad() throws Exception {
		String dni = String.valueOf(DNI.incrementAndGet());
		ia.respuesta = """
				{"paciente": {"tipoDocumento": "DNI", "numeroDocumento": "%s", "nombres": "Luz", "apellidoPaterno": "Prudente",
				  "fechaNacimiento": "1970-02-03", "sexo": "FEMENINO"},
				 "datos": [{"categoria": "ALERGIA", "descripcion": "Sulfas", "tipoAlergia": "MEDICAMENTO",
				   "fragmentoOrigen": "Alergias: Sulfas"}]}""".formatted(dni);
		long id = subirYEsperar();

		long paciente = llamar("POST", "/api/importaciones/" + id + "/aprobacion", aprobacion(id, dni, "Luz", "Prudente", Set.of(0), false), admision)
			.cuerpo().path("pacienteId").asLong();

		String alergias = llamar("GET", "/api/pacientes/" + paciente + "/alergias", null, medico).cuerpo().toString();
		assertThat(alergias).contains("SEVERA").contains("por precaución");
	}

	@Test
	@DisplayName("CP-40 Si la IA falla, Admisión ve el error y puede reintentar")
	void errorYReintento() throws Exception {
		iaResponde(String.valueOf(DNI.incrementAndGet()), "Sara", "Reintento");
		ia.erroresPendientes.set(1);
		long id = json.readTree(subir(pdfUnico()).body()).path("id").asLong();
		esperarEstado(id, "ERROR");

		assertThat(llamar("GET", "/api/importaciones", null, admision).cuerpo().toString()).contains("La IA rechazó el contenido");
		assertThat(llamar("GET", "/api/importaciones/" + id, null, admision).estado()).isBetween(400, 499);

		assertThat(llamar("POST", "/api/importaciones/" + id + "/reintento", null, admision).estado()).isEqualTo(200);
		esperar(id);
		assertThat(llamar("POST", "/api/importaciones/" + id + "/reintento", null, admision).estado()).isBetween(400, 499);
	}

	@Test
	@DisplayName("CP-41 Admisión ve el PDF mientras revisa; al cerrar, ya no")
	void archivoSoloMientrasRevisa() throws Exception {
		iaResponde(String.valueOf(DNI.incrementAndGet()), "Vera", "Archivo");
		long id = subirYEsperar();
		String ruta = "http://localhost:" + puerto + "/api/importaciones/" + id + "/archivo";
		HttpRequest pedido = HttpRequest.newBuilder(URI.create(ruta)).header("Authorization", "Bearer " + admision).build();

		HttpResponse<byte[]> abierto = http.send(pedido, HttpResponse.BodyHandlers.ofByteArray());
		assertThat(abierto.statusCode()).isEqualTo(200);
		assertThat(new String(abierto.body(), StandardCharsets.US_ASCII)).startsWith("%PDF");

		llamar("POST", "/api/importaciones/" + id + "/rechazo", Map.of("motivo", "No corresponde"), admision);

		assertThat(http.send(pedido, HttpResponse.BodyHandlers.ofByteArray()).statusCode()).isBetween(400, 499);
		assertThat(llamar("POST", "/api/importaciones/" + id + "/rechazo", Map.of("motivo", "Otra vez"), admision).estado()).isBetween(400, 499);
	}

	@Test
	@DisplayName("CP-42 Admisión corrige, quita y agrega datos antes de aprobar")
	void edicionDeDatos() throws Exception {
		String dni = String.valueOf(DNI.incrementAndGet());
		iaResponde(dni, "Nora", "Edita");
		long id = subirYEsperar();
		JsonNode propuestos = llamar("GET", "/api/importaciones/" + id, null, admision).cuerpo().path("datos");
		Map<String, Object> laboratorio = new java.util.HashMap<>(json.convertValue(propuestos.get(2), Map.class));
		laboratorio.put("valor", "101");
		Map<String, Object> nuevo = Map.of("categoria", "ANTECEDENTE", "descripcion", "Hipertensión desde 2015", "tipoAntecedente", "PERSONAL");
		Map<String, Object> cuerpo = Map.of("paciente", datos(dni, "Nora", "Edita"), "datos",
				List.of(Map.of("origen", 0, "dato", propuestos.get(0)), Map.of("origen", 2, "dato", laboratorio), Map.of("dato", nuevo)));

		Respuesta r = llamar("POST", "/api/importaciones/" + id + "/aprobacion", cuerpo, admision);

		assertThat(r.estado()).isEqualTo(200);
		assertThat(r.cuerpo().path("datosRegistrados").asInt()).isEqualTo(3);
		assertThat(r.cuerpo().path("datosCorregidos").asInt()).isEqualTo(2);
		long paciente = r.cuerpo().path("pacienteId").asLong();
		assertThat(llamar("GET", "/api/pacientes/" + paciente + "/resultados-laboratorio", null, medico).cuerpo().toString()).contains("101");
		assertThat(llamar("GET", "/api/pacientes/" + paciente + "/antecedentes", null, medico).cuerpo().toString())
			.contains("Hipertensión").doesNotContain("Apendicectomía");
	}

	@Test
	@DisplayName("CP-43 Un dato editado inválido se rechaza con mensaje y no se registra nada")
	void datoInvalido() throws Exception {
		String dni = String.valueOf(DNI.incrementAndGet());
		iaResponde(dni, "Ines", "Invalida");
		long id = subirYEsperar();
		Map<String, Object> sinValor = Map.of("categoria", "LABORATORIO", "descripcion", "Colesterol");
		Map<String, Object> cuerpo = Map.of("paciente", datos(dni, "Ines", "Invalida"), "datos", List.of(Map.of("dato", sinValor)));

		Respuesta r = llamar("POST", "/api/importaciones/" + id + "/aprobacion", cuerpo, admision);

		assertThat(r.estado()).isBetween(400, 499);
		assertThat(r.cuerpo().toString()).contains("Colesterol");
		assertThat(llamar("GET", "/api/pacientes?q=" + dni, null, admision).cuerpo().path("contenido")).isEmpty();
		assertThat(llamar("GET", "/api/importaciones/" + id, null, admision).estado()).isEqualTo(200);
	}

	@Test
	@DisplayName("CP-44 Eliminar antes de aprobar borra el archivo y permite volver a subirlo")
	void eliminarAntesDeAprobar() throws Exception {
		iaResponde(String.valueOf(DNI.incrementAndGet()), "Luz", "Elimina");
		byte[] pdf = pdfUnico();
		long id = json.readTree(subir(pdf).body()).path("id").asLong();
		esperar(id);

		assertThat(llamar("DELETE", "/api/importaciones/" + id, null, admision).estado()).isEqualTo(200);

		assertThat(jdbc.queryForObject("SELECT count(*) FROM importacion_contenidos WHERE importacion_id = ?", Long.class, id)).isZero();
		assertThat(jdbc.queryForObject("SELECT datos_cifrados FROM importaciones WHERE id = ?", String.class, id)).isNull();
		assertThat(subir(pdf).statusCode()).isEqualTo(201);
		assertThat(llamar("DELETE", "/api/importaciones/" + id, null, admision).estado()).isBetween(400, 499);
	}

	@Test
	@DisplayName("CP-45 Anular una aprobación inactiva lo registrado sin borrarlo; el paciente puede volver")
	void anularAprobacion() throws Exception {
		String dni = String.valueOf(DNI.incrementAndGet());
		iaResponde(dni, "Ada", "Anulada");
		byte[] pdf = pdfUnico();
		long id = json.readTree(subir(pdf).body()).path("id").asLong();
		esperar(id);
		long paciente = llamar("POST", "/api/importaciones/" + id + "/aprobacion", aprobacion(id, dni, "Ada", "Anulada", Set.of(0, 1, 2), false), admision)
			.cuerpo().path("pacienteId").asLong();

		Respuesta r = llamar("POST", "/api/importaciones/" + id + "/anulacion", Map.of("motivo", "Era de otro paciente"), admision);

		assertThat(r.estado()).isEqualTo(200);
		assertThat(llamar("GET", "/api/pacientes/" + paciente + "/alergias", null, medico).cuerpo().get(0).path("activa").asBoolean()).isFalse();
		assertThat(llamar("GET", "/api/pacientes/" + paciente + "/antecedentes", null, medico).cuerpo().get(0).path("activo").asBoolean()).isFalse();
		assertThat(llamar("GET", "/api/pacientes/" + paciente + "/resultados-laboratorio", null, medico).cuerpo().get(0).path("activo").asBoolean()).isFalse();
		assertThat(llamar("GET", "/api/pacientes/" + paciente + "/documentos", null, medico).cuerpo().get(0).path("estado").asString()).isEqualTo("ANULADO");
		assertThat(llamar("GET", "/api/pacientes?q=" + dni, null, admision).cuerpo().path("contenido")).isEmpty();
		assertThat(llamar("POST", "/api/importaciones/" + id + "/anulacion", Map.of("motivo", "Otra vez"), admision).estado()).isBetween(400, 499);

		long otra = json.readTree(subir(pdf).body()).path("id").asLong();
		esperar(otra);
		Respuesta segunda = llamar("POST", "/api/importaciones/" + otra + "/aprobacion", aprobacion(otra, dni, "Ada", "Anulada", Set.of(1), false), admision);
		assertThat(segunda.cuerpo().path("pacienteId").asLong()).isEqualTo(paciente);
		assertThat(llamar("GET", "/api/pacientes?q=" + dni, null, admision).cuerpo().path("contenido")).hasSize(1);
	}

	@Test
	@DisplayName("CP-46 Anular no inactiva a un paciente que ya existía")
	void anularEnPacienteExistente() throws Exception {
		String dni = String.valueOf(DNI.incrementAndGet());
		long paciente = registrarPaciente(dni, "Vera", "Previa");
		iaResponde(dni, "Vera", "Previa");
		long id = subirYEsperar();
		llamar("POST", "/api/importaciones/" + id + "/aprobacion", aprobacion(id, dni, "Vera", "Previa", Set.of(1), false), admision);

		llamar("POST", "/api/importaciones/" + id + "/anulacion", Map.of("motivo", "Documento equivocado"), admision);

		assertThat(llamar("GET", "/api/pacientes?q=" + dni, null, admision).cuerpo().path("contenido").get(0).path("id").asLong()).isEqualTo(paciente);
	}

	@Test
	@DisplayName("CP-47 Documento con varias personas: se detecta y exige confirmar")
	void variosPacientes() throws Exception {
		String dni = String.valueOf(DNI.incrementAndGet());
		ia.respuesta = """
				{"paciente": {"numeroDocumento": "%s", "nombres": "Una", "apellidoPaterno": "Dos", "fechaNacimiento": "1970-02-03"},
				 "datos": [], "variosPacientes": true}""".formatted(dni);
		long id = subirYEsperar();
		assertThat(llamar("GET", "/api/importaciones/" + id, null, admision).cuerpo().path("variosPacientes").asBoolean()).isTrue();

		Map<String, Object> cuerpo = new java.util.HashMap<>(aprobacion(id, dni, "Una", "Dos", Set.of(), false));
		Respuesta r = llamar("POST", "/api/importaciones/" + id + "/aprobacion", cuerpo, admision);
		assertThat(r.cuerpo().toString()).contains("VARIOS_PACIENTES");

		cuerpo.put("confirmarUnPaciente", true);
		assertThat(llamar("POST", "/api/importaciones/" + id + "/aprobacion", cuerpo, admision).estado()).isEqualTo(200);
	}

	@Test
	@DisplayName("CP-48 Sin DNI, un paciente parecido se avisa y se puede elegir")
	void posibleDuplicado() throws Exception {
		long paciente = registrarPaciente(String.valueOf(DNI.incrementAndGet()), "Rosa", "Parecida");
		ia.respuesta = """
				{"paciente": {"tipoDocumento": "DNI", "nombres": "Rosa", "apellidoPaterno": "PARECIDA", "fechaNacimiento": "1970-02-03", "sexo": "FEMENINO"},
				 "datos": []}""";
		long id = subirYEsperar();
		JsonNode detalle = llamar("GET", "/api/importaciones/" + id, null, admision).cuerpo();
		assertThat(detalle.path("parecidos").get(0).path("id").asLong()).isEqualTo(paciente);

		Map<String, Object> sinDocumento = new java.util.HashMap<>(datos(null, "Rosa", "PARECIDA"));
		sinDocumento.put("tipoDocumento", "SIN_DOCUMENTO");
		Map<String, Object> cuerpo = new java.util.HashMap<>(Map.of("paciente", sinDocumento, "datos", List.of()));
		assertThat(llamar("POST", "/api/importaciones/" + id + "/aprobacion", cuerpo, admision).cuerpo().toString()).contains("POSIBLE_DUPLICADO");

		cuerpo.put("pacienteId", paciente);
		cuerpo.put("confirmarCoincidencia", true);
		assertThat(llamar("POST", "/api/importaciones/" + id + "/aprobacion", cuerpo, admision).cuerpo().path("pacienteId").asLong()).isEqualTo(paciente);
	}

	@Test
	@DisplayName("CP-49 Dos aprobaciones a la vez: solo una registra")
	void aprobacionSimultanea() throws Exception {
		String dni = String.valueOf(DNI.incrementAndGet());
		iaResponde(dni, "Eva", "Doble");
		long id = subirYEsperar();
		Map<String, Object> cuerpo = aprobacion(id, dni, "Eva", "Doble", Set.of(0, 1, 2), false);
		var hilos = java.util.concurrent.Executors.newFixedThreadPool(2);
		java.util.concurrent.Callable<Integer> tarea = () -> llamar("POST", "/api/importaciones/" + id + "/aprobacion", cuerpo, admision).estado();

		var resultados = hilos.invokeAll(List.of(tarea, tarea));
		hilos.shutdown();

		long aprobadas = 0;
		for (var r : resultados) {
			aprobadas += r.get() == 200 ? 1 : 0;
		}
		assertThat(aprobadas).isEqualTo(1);
		assertThat(llamar("GET", "/api/pacientes?q=" + dni, null, admision).cuerpo().path("contenido")).hasSize(1);
		long paciente = llamar("GET", "/api/pacientes?q=" + dni, null, admision).cuerpo().path("contenido").get(0).path("id").asLong();
		assertThat(llamar("GET", "/api/pacientes/" + paciente + "/alergias", null, medico).cuerpo()).hasSize(1);
	}

	@Test
	@DisplayName("CP-50 Al llegar al tope de espacio no se aceptan más archivos")
	void topeDeEspacio() throws Exception {
		Object servicio = AopTestUtils.getTargetObject(importaciones);
		Object cuota = ReflectionTestUtils.getField(servicio, "cuotaMb");
		ReflectionTestUtils.setField(servicio, "cuotaMb", 1L);
		try {
			HttpResponse<String> r = subir(pdfUnico());

			assertThat(r.statusCode()).isBetween(400, 499);
			assertThat(r.body()).contains("ESPACIO_AGOTADO");
		}
		finally {
			ReflectionTestUtils.setField(servicio, "cuotaMb", cuota);
		}
		JsonNode espacio = llamar("GET", "/api/importaciones/espacio", null, admision).cuerpo();
		assertThat(espacio.path("usadoMb").asLong()).isPositive();
		assertThat(espacio.path("cuotaMb").asLong()).isEqualTo(450);
	}

	@Test
	@DisplayName("CP-51 Quien atiende ve el aviso de documentos importados, sin aprobar nada")
	void avisoAlMedico() throws Exception {
		String dni = String.valueOf(DNI.incrementAndGet());
		iaResponde(dni, "Ana", "Aviso");
		long id = subirYEsperar();
		long paciente = llamar("POST", "/api/importaciones/" + id + "/aprobacion", aprobacion(id, dni, "Ana", "Aviso", Set.of(1), false), admision)
			.cuerpo().path("pacienteId").asLong();

		assertThat(llamar("GET", "/api/importaciones/del-paciente/" + paciente, null, medico).cuerpo().asLong()).isEqualTo(1);
		assertThat(llamar("GET", "/api/importaciones/del-paciente/" + paciente, null, admision).estado()).isEqualTo(403);
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

	private void esperar(long id) {
		esperarEstado(id, "LISTA");
	}

	private void esperarEstado(long id, String estado) {
		await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(50)).until(
				() -> estado.equals(jdbc.queryForObject("SELECT estado FROM importaciones WHERE id = ?", String.class, id)));
	}

	/** Aprueba sin cambios los datos propuestos en esas posiciones. */
	private Map<String, Object> aprobacion(long id, String dni, String nombres, String apellido, Set<Integer> aceptados, boolean confirmar)
			throws Exception {
		JsonNode propuestos = llamar("GET", "/api/importaciones/" + id, null, admision).cuerpo().path("datos");
		List<Map<String, Object>> elegidos = new java.util.ArrayList<>();
		for (int i : aceptados) {
			elegidos.add(Map.of("origen", i, "dato", propuestos.get(i)));
		}
		return Map.of("paciente", datos(dni, nombres, apellido), "datos", elegidos, "confirmarCoincidencia", confirmar);
	}

	private static Map<String, Object> datos(String dni, String nombres, String apellido) {
		Map<String, Object> d = new java.util.HashMap<>(Map.of("tipoDocumento", "DNI", "nombres", nombres, "apellidoPaterno", apellido,
				"fechaNacimiento", "1970-02-03", "sexo", "FEMENINO"));
		d.put("numeroDocumento", dni);
		return d;
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
