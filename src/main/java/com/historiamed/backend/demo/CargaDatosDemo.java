package com.historiamed.backend.demo;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.historiamed.backend.auditoria.AccionAuditoria;
import com.historiamed.backend.auditoria.AuditoriaService;
import com.historiamed.backend.common.security.CifradoDatos;
import com.historiamed.backend.common.util.Edad;
import com.historiamed.backend.common.util.Tiempo;
import com.historiamed.backend.triaje.Alerta;
import com.historiamed.backend.triaje.EvaluadorTriaje;

/**
 * Carga datos de demostración <b>ficticios</b> para la sustentación (plan.md, principio P5: nunca datos reales).
 *
 * <ul>
 * <li>Solo se ejecuta con {@code historiamed.demo.habilitada=true} (variable DEMO=true).</li>
 * <li>Solo si la base no tiene pacientes: nunca mezcla datos ficticios con reales.</li>
 * <li>En una sola transacción: se carga completo o no se carga nada.</li>
 * </ul>
 *
 * La contraseña de los usuarios de demostración se toma de DEMO_PASSWORD: nunca se escribe en el código, porque el
 * repositorio es público. Sin ella no se carga nada.
 *
 * Crea usuarios de cada rol, 15 pacientes, alergias, unos 60 días de historia
 * (citas atendidas con triaje y atención cerrada, inasistencias y cancelaciones) y el día de hoy con pacientes en
 * cada etapa del flujo. Se escribe con SQL para poder fechar la historia en el pasado; los signos vitales pasan
 * por el mismo {@link EvaluadorTriaje} del sistema, así que alertas y prioridades son coherentes.
 */
@Component
@ConditionalOnProperty(name = "historiamed.demo.habilitada", havingValue = "true")
public class CargaDatosDemo implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(CargaDatosDemo.class);

	/** Misma política que el cambio de contraseña: 8 a 72 caracteres, con al menos una letra y un número. */
	private static final Pattern POLITICA_PASSWORD = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,72}$");

	private static final int DIAS_HISTORIA = 60;

	// Valores que se repiten en los datos ficticios

	private static final String FEMENINO = "FEMENINO";

	private static final String MASCULINO = "MASCULINO";

	private static final String ACTIVO = "ACTIVO";

	private static final String SIS_GRATUITO = "SIS Gratuito";

	private static final String ESSALUD = "ESSALUD";

	private static final String PARTICULAR = "PARTICULAR";

	private static final String PRESUNTIVO = "PRESUNTIVO";

	private static final String DEFINITIVO = "DEFINITIVO";

	private static final String MEDICO = "MEDICO";

	private static final String MEDICAMENTO = "MEDICAMENTO";

	private static final String PROGRAMADA = "PROGRAMADA";

	private static final String EN_ESPERA_TRIAJE = "EN_ESPERA_TRIAJE";

	private static final String NO_SE_PRESENTO = "NO_SE_PRESENTO";

	private static final String CANCELADA = "CANCELADA";

	private static final String PARACETAMOL = "paracetamol";

	private static final String MG_500 = "500 mg";

	private static final String UNA_TABLETA = "1 tableta";

	private static final String CADA_8_HORAS = "cada 8 horas";

	private static final String CADA_24_HORAS = "cada 24 horas";

	private static final String UN_DIA = "1 día";

	private static final String TRES_DIAS = "3 días";

	private static final String CINCO_DIAS = "5 días";

	private static final String TREINTA_DIAS = "30 días";

	private static final String SIGNOS_NORMALES = "normal";

	private final String passwordDemo;

	private final JdbcTemplate jdbc;

	private final PasswordEncoder passwordEncoder;

	private final EvaluadorTriaje evaluador;

	private final AuditoriaService auditoria;

	private final CifradoDatos cifrado;

	/** Semilla fija: la demostración sale igual cada vez que se carga. */
	private final Random azar = new Random(2026);

	/** Último número de turno por (fecha, consultorio). */
	private final Map<String, Integer> turnos = new HashMap<>();

	public CargaDatosDemo(JdbcTemplate jdbc, PasswordEncoder passwordEncoder, EvaluadorTriaje evaluador,
			AuditoriaService auditoria, CifradoDatos cifrado,
			@Value("${historiamed.demo.password:}") String passwordDemo) {
		this.passwordDemo = passwordDemo;
		this.jdbc = jdbc;
		this.passwordEncoder = passwordEncoder;
		this.evaluador = evaluador;
		this.auditoria = auditoria;
		this.cifrado = cifrado;
	}

	// ------------------------------------------------------------------------------------------------------------
	// Datos ficticios
	// ------------------------------------------------------------------------------------------------------------

	private record PacienteDemo(String tipoDoc, String doc, String nombres, String paterno, String materno,
			LocalDate nacimiento, String sexo, String financiamiento, String afiliacion, String plan, String estadoSeguro,
			String telefono, String direccion) {
	}

	private static final List<PacienteDemo> PACIENTES = List.of(
			new PacienteDemo("DNI", "45678912", "Rosa María", "Quispe", "Mamani", LocalDate.of(1985, 3, 14), FEMENINO,
					"SIS", "SIS-4567891", SIS_GRATUITO, ACTIVO, "987654321", "Av. Los Olivos 123, Comas"),
			new PacienteDemo("DNI", "40011122", "Juan Carlos", "Huamán", "Flores", LocalDate.of(1958, 6, 1), MASCULINO,
					ESSALUD, "EsS-400111", null, ACTIVO, "912345678", "Jr. Huancavelica 450, Cercado"),
			new PacienteDemo("DNI", "72233445", "Lucía Fernanda", "Torres", "Ríos", LocalDate.of(1998, 11, 20),
					FEMENINO, PARTICULAR, null, null, null, "956123789", "Calle Las Begonias 88, San Juan"),
			new PacienteDemo("DNI", "41122334", "José Luis", "Mendoza", "Castillo", LocalDate.of(1976, 2, 9),
					MASCULINO, "SIS", "SIS-4112233", SIS_GRATUITO, ACTIVO, "945678123", "Mz. B Lt. 7, Villa El Salvador"),
			new PacienteDemo("DNI", "70011223", "Carmen Rosa", "Paredes", "Soto", LocalDate.of(1990, 7, 15), FEMENINO,
					"SIS", "SIS-7001122", "SIS Para Todos", "NO_VERIFICADO", "978321654", "Av. Túpac Amaru 1500, Carabayllo"),
			new PacienteDemo("SIN_DOCUMENTO", null, "Mateo", "García", "Luna", LocalDate.now(Tiempo.ZONA).minusMonths(7),
					MASCULINO, PARTICULAR, null, null, null, "964852147", "Asoc. Los Jardines 12, Puente Piedra"),
			new PacienteDemo("DNI", "78899001", "Valentina", "Chávez", "Rojas", LocalDate.of(2019, 5, 22), FEMENINO,
					"SIS", "SIS-7889900", SIS_GRATUITO, ACTIVO, "923456781", "Jr. Ayacucho 230, Rímac"),
			new PacienteDemo("DNI", "43344556", "Miguel Ángel", "Ramos", "Vargas", LocalDate.of(1965, 9, 30),
					MASCULINO, "PRIVADO", "PAC-778812", "Pacífico EPS", "NO_VERIFICADO", "998877665", "Av. Arequipa 3010, San Isidro"),
			new PacienteDemo("DNI", "46677889", "Ana Sofía", "Gutiérrez", "Salazar", LocalDate.of(1982, 12, 4),
					FEMENINO, ESSALUD, "EsS-466778", null, ACTIVO, "934567812", "Calle Los Pinos 540, Los Olivos"),
			new PacienteDemo("CARNET_EXTRANJERIA", "001234567", "Carlos Eduardo", "Rodríguez", "Pérez",
					LocalDate.of(1988, 4, 18), MASCULINO, PARTICULAR, null, null, null, "951753852", "Av. Brasil 1200, Breña"),
			new PacienteDemo("DNI", "47788990", "Rosa Elvira", "Condori", "Apaza", LocalDate.of(1950, 1, 25), FEMENINO,
					"SIS", "SIS-4778899", SIS_GRATUITO, ACTIVO, "917263548", "Jr. Puno 77, El Agustino"),
			new PacienteDemo("DNI", "71234567", "Diego Alonso", "Vásquez", "Medina", LocalDate.of(2005, 8, 8),
					MASCULINO, "SIS", "SIS-7123456", SIS_GRATUITO, ACTIVO, "962584713", "Mz. F Lt. 3, Ate"),
			new PacienteDemo("DNI", "44455667", "Patricia Isabel", "Flores", "Cárdenas", LocalDate.of(1979, 10, 12),
					FEMENINO, PARTICULAR, null, null, null, "985214763", "Av. Universitaria 2100, San Martín de Porres"),
			new PacienteDemo("DNI", "49900112", "Luis Fernando", "Castro", "Núñez", LocalDate.of(1993, 3, 3), MASCULINO,
					ESSALUD, "EsS-499001", null, ACTIVO, "973185246", "Calle Real 340, Surco"),
			new PacienteDemo("DNI", "75566778", "Gabriela", "Morales", "Huerta", LocalDate.of(2001, 6, 27), FEMENINO,
					"SIS", "SIS-7556677", SIS_GRATUITO, ACTIVO, "941852637", "Jr. Cusco 615, Independencia"));

	private record RecetaDemo(String principioActivo, String concentracion, String dosis, String via,
			String frecuencia, String duracion, int cantidad) {
	}

	/** Tipo de consulta: diagnóstico, tratamiento y el tipo de signos vitales que suele traer. */
	private record Plantilla(String motivo, String tiempo, String anamnesis, String examen, String cie10,
			String tipoDx, String plan, String indicaciones, String signos, List<RecetaDemo> receta) {
	}

	private static final Plantilla FARINGITIS = new Plantilla("Dolor de garganta y fiebre", "2 días",
			"Odinofagia y alza térmica no cuantificada. Niega tos.", "Faringe congestiva, sin exudado. Adenopatías cervicales no dolorosas.",
			"J02.9", PRESUNTIVO, "Control si persiste la fiebre más de 72 h.", "Abundantes líquidos, reposo relativo.", "fiebre",
			List.of(new RecetaDemo(PARACETAMOL, MG_500, UNA_TABLETA, "ORAL", CADA_8_HORAS, TRES_DIAS, 9)));

	private static final Plantilla HIPERTENSION = new Plantilla("Control de presión arterial", "5 años",
			"Paciente hipertenso en tratamiento. Refiere cefalea ocasional.", "Ruidos cardiacos rítmicos, sin soplos. Sin edemas.",
			"I10", DEFINITIVO, "Control mensual. Perfil lipídico.", "Dieta hiposódica, caminar 30 minutos al día.", "hta",
			List.of(new RecetaDemo("enalapril", "10 mg", UNA_TABLETA, "ORAL", "cada 12 horas", TREINTA_DIAS, 60)));

	private static final Plantilla DIABETES = new Plantilla("Control de diabetes", "8 años",
			"Diabético tipo 2 en tratamiento. Glucosa capilar en ayunas 145 mg/dL.", "Pies sin lesiones. Sensibilidad conservada.",
			"E11.9", DEFINITIVO, "Hemoglobina glicosilada. Control en un mes.", "Dieta sin azúcares simples.", SIGNOS_NORMALES,
			List.of(new RecetaDemo("metformina", "850 mg", UNA_TABLETA, "ORAL", "cada 12 horas", TREINTA_DIAS, 60)));

	private static final Plantilla DIARREA = new Plantilla("Deposiciones líquidas", UN_DIA,
			"5 deposiciones líquidas sin moco ni sangre. Tolera vía oral.", "Abdomen blando, depresible, ruidos aumentados. Mucosas húmedas.",
			"A09", PRESUNTIVO, "Signos de alarma explicados.", "Hidratación oral, dieta blanda.", SIGNOS_NORMALES,
			List.of(new RecetaDemo("sales de rehidratacion oral", "20.5 g", "1 sobre en 1 litro de agua", "ORAL", "a libre demanda", TRES_DIAS, 6)));

	private static final Plantilla ITU = new Plantilla("Ardor al orinar", TRES_DIAS,
			"Disuria y polaquiuria. Niega fiebre.", "Puño percusión lumbar negativa. Dolor suprapúbico leve.",
			"N39.0", PRESUNTIVO, "Examen de orina completo y urocultivo.", "Abundantes líquidos.", SIGNOS_NORMALES,
			List.of(new RecetaDemo("nitrofurantoina", "100 mg", "1 cápsula", "ORAL", "cada 6 horas", "7 días", 28)));

	private static final Plantilla RESFRIADO = new Plantilla("Congestión nasal y estornudos", "2 días",
			"Rinorrea hialina y malestar general.", "Mucosa nasal congestiva. Pulmones limpios.",
			"J00", DEFINITIVO, null, "Lavados nasales con suero fisiológico.", SIGNOS_NORMALES,
			List.of(new RecetaDemo("clorfenamina", "4 mg", UNA_TABLETA, "ORAL", CADA_8_HORAS, CINCO_DIAS, 15),
					new RecetaDemo(PARACETAMOL, MG_500, UNA_TABLETA, "ORAL", "cada 8 horas si hay malestar", TRES_DIAS, 9)));

	private static final Plantilla GASTRITIS = new Plantilla("Dolor en boca del estómago", "2 semanas",
			"Epigastralgia urente postprandial.", "Dolor a la palpación en epigastrio. Sin signos peritoneales.",
			"K29.7", PRESUNTIVO, "Evaluar endoscopía si no mejora.", "Evitar irritantes, comidas a sus horas.", SIGNOS_NORMALES,
			List.of(new RecetaDemo("omeprazol", "20 mg", "1 cápsula", "ORAL", "cada 24 horas en ayunas", "28 días", 28)));

	private static final Plantilla LUMBAGO = new Plantilla("Dolor de espalda baja", "4 días",
			"Lumbalgia mecánica tras cargar peso. Sin irradiación.", "Contractura paravertebral lumbar. Lasègue negativo.",
			"M54.5", DEFINITIVO, null, "Calor local, evitar cargar peso.", SIGNOS_NORMALES,
			List.of(new RecetaDemo(PARACETAMOL, MG_500, UNA_TABLETA, "ORAL", CADA_8_HORAS, CINCO_DIAS, 15)));

	private static final Plantilla CONTROL_NINO = new Plantilla("Control de crecimiento y desarrollo", null,
			"Control de rutina. Madre sin quejas.", "Peso y talla adecuados para la edad. Desarrollo acorde.",
			"Z00.1", DEFINITIVO, "Control en 6 meses.", "Alimentación balanceada.", "nino",
			List.of(new RecetaDemo("albendazol", "400 mg", UNA_TABLETA, "ORAL", "dosis única", UN_DIA, 1)));

	/** Control del lactante: sin medicamentos (el albendazol no se indica en menores de 1 año). */
	private static final Plantilla CONTROL_LACTANTE = new Plantilla("Control de crecimiento y desarrollo", null,
			"Control de rutina. Lactancia materna exclusiva hasta los 6 meses; inicia alimentación complementaria.",
			"Peso y talla en percentil adecuado. Desarrollo psicomotor acorde a la edad.", "Z00.1", DEFINITIVO,
			"Control en 1 mes. Vacunas al día.", "Continuar lactancia materna y alimentación complementaria.", "nino",
			List.of());

	private static final Plantilla ASMA = new Plantilla("Silbido en el pecho", UN_DIA,
			"Asmático conocido. Crisis leve tras exposición a polvo.", "Sibilantes espiratorios difusos. Sin tiraje.",
			"J45.9", DEFINITIVO, "Control en 48 h.", "Evitar polvo y humo.", SIGNOS_NORMALES,
			List.of(new RecetaDemo("salbutamol", "100 mcg/dosis", "2 inhalaciones", "INHALATORIA", "cada 6 horas", CINCO_DIAS, 1)));

	private static final Plantilla ANEMIA = new Plantilla("Cansancio", "1 mes",
			"Fatiga y palidez. Hemoglobina 10.2 g/dL en control previo.", "Palidez de piel y mucosas.",
			"D50.9", DEFINITIVO, "Hemoglobina de control en 30 días.", "Dieta rica en hierro.", SIGNOS_NORMALES,
			List.of(new RecetaDemo("sulfato ferroso", "300 mg (60 mg Fe)", UNA_TABLETA, "ORAL", CADA_24_HORAS, TREINTA_DIAS, 30),
					new RecetaDemo("acido folico", "500 mcg", UNA_TABLETA, "ORAL", CADA_24_HORAS, TREINTA_DIAS, 30)));

	private static final Plantilla BRONQUITIS = new Plantilla("Tos con flema", CINCO_DIAS,
			"Tos productiva, sin fiebre en las últimas 24 h.", "Roncantes aislados. Sin crepitantes.",
			"J20.9", PRESUNTIVO, null, "Abundantes líquidos.", SIGNOS_NORMALES,
			List.of(new RecetaDemo("azitromicina", MG_500, UNA_TABLETA, "ORAL", CADA_24_HORAS, TRES_DIAS, 3)));

	/** Consultas históricas posibles por paciente (índice en PACIENTES), acordes a su edad y alergias. */
	private static final Map<Integer, List<Plantilla>> HISTORIA = Map.ofEntries(Map.entry(0, List.of(BRONQUITIS, ITU, GASTRITIS)),
			Map.entry(1, List.of(HIPERTENSION, LUMBAGO)), Map.entry(2, List.of(FARINGITIS, ITU)),
			Map.entry(3, List.of(HIPERTENSION, HIPERTENSION)), Map.entry(4, List.of(ANEMIA)),
			Map.entry(5, List.of(CONTROL_LACTANTE)), Map.entry(6, List.of(CONTROL_NINO, RESFRIADO)),
			Map.entry(7, List.of(DIABETES, DIABETES)), Map.entry(8, List.of(GASTRITIS, RESFRIADO)),
			Map.entry(9, List.of(LUMBAGO, DIARREA)), Map.entry(10, List.of(HIPERTENSION, LUMBAGO)),
			Map.entry(11, List.of(ASMA, FARINGITIS)), Map.entry(12, List.of(GASTRITIS)),
			Map.entry(13, List.of(DIARREA, RESFRIADO)), Map.entry(14, List.of(FARINGITIS, ANEMIA)));

	// ------------------------------------------------------------------------------------------------------------

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (passwordDemo == null || !POLITICA_PASSWORD.matcher(passwordDemo).matches()) {
			log.warn("Datos de demostración NO cargados: defina DEMO_PASSWORD (8 a 72 caracteres, con al menos una "
					+ "letra y un número). La contraseña de los usuarios demo no se escribe en el código.");
			return;
		}
		Long pacientes = jdbc.queryForObject("SELECT count(*) FROM pacientes", Long.class);
		if (pacientes != null && pacientes > 0) {
			log.warn("Datos de demostración NO cargados: la base ya tiene {} paciente(s). Solo se cargan en una "
					+ "base sin pacientes, para no mezclar datos ficticios con reales.", pacientes);
			return;
		}
		log.info("Cargando datos de demostración ficticios...");

		Personal personal = new Personal(usuario("admision.demo", "Lucía", "Rojas Paredes", "90000001", "ADMISION", null),
				usuario("triaje.demo", "Pedro", "Salas Vega", "90000002", "TRIAJE", null),
				usuario("dr.diaz", "Carlos", "Díaz Mendoza", "90000003", MEDICO, "045123"),
				usuario("dra.torres", "Ana", "Torres Quispe", "90000004", MEDICO, "052871"),
				usuario("dra.ruiz", "Elena", "Ruiz Chávez", "90000005", MEDICO, "061342"));
		Consultorios consultorios = consultorios();

		Instant ahora = Instant.now();
		LocalDate hoy = Tiempo.hoy();

		long[] ids = new long[PACIENTES.size()];
		for (int i = 0; i < PACIENTES.size(); i++) {
			// Registrados escalonadamente en los últimos meses (para el indicador de pacientes nuevos)
			ids[i] = paciente(PACIENTES.get(i), personal.admision(), ahora.minus(90 - i * 6L, ChronoUnit.DAYS));
		}
		long triaje = personal.triaje();
		alergia(ids[0], MEDICAMENTO, "Penicilina", "Anafilaxia en 2019", "SEVERA", triaje);
		alergia(ids[3], MEDICAMENTO, "Ibuprofeno", "Urticaria", "MODERADA", triaje);
		alergia(ids[8], "ALIMENTO", "Mariscos", "Prurito y ronchas", "LEVE", triaje);
		alergia(ids[10], MEDICAMENTO, "Sulfas", "Erupción cutánea", "MODERADA", triaje);
		alergia(ids[12], "AMBIENTAL", "Látex", "Dermatitis de contacto", "LEVE", triaje);

		int visitas = cargarHistoria(ids, personal, consultorios, hoy);
		cargarHoy(ids, personal, consultorios, ahora, hoy);

		auditoria.registrarComo(null, "sistema", null, AccionAuditoria.CREAR, "DEMO", null,
				"Carga de datos de demostración ficticios: " + PACIENTES.size() + " pacientes, " + visitas
						+ " atenciones históricas");
		log.info("Datos de demostración cargados: 5 usuarios (contraseña: la de DEMO_PASSWORD), {} pacientes y {} "
				+ "atenciones históricas. Usuarios: admision.demo, triaje.demo, dr.diaz, dra.torres, dra.ruiz",
				PACIENTES.size(), visitas);
	}

	/** Usuarios de la demostración, por rol. */
	private record Personal(long admision, long triaje, long drDiaz, long draTorres, long draRuiz) {
	}

	/** Consultorios usados: dos de medicina general (pueden ser el mismo) y pediatría (o el general si no hay). */
	private record Consultorios(long general, long general2, long pediatria) {
	}

	/** Paciente, médico, consultorio, día y hora de una cita. */
	private record CitaDemo(long paciente, long medico, long consultorio, LocalDate dia, LocalTime hora) {
	}

	private Consultorios consultorios() {
		List<Long> activos = jdbc.queryForList("SELECT id FROM consultorios WHERE activo ORDER BY id", Long.class);
		if (activos.isEmpty()) {
			throw new IllegalStateException("No hay consultorios activos para la demostración");
		}
		long general = activos.get(0);
		long general2 = activos.get(Math.min(1, activos.size() - 1));
		long pediatria = jdbc.queryForList(
				"SELECT id FROM consultorios WHERE activo AND especialidad ILIKE 'pediatr%' ORDER BY id", Long.class)
			.stream()
			.findFirst()
			.orElse(general);
		return new Consultorios(general, general2, pediatria);
	}

	/** Historia: citas atendidas en días hábiles pasados, con algunas inasistencias y cancelaciones. */
	private int cargarHistoria(long[] ids, Personal personal, Consultorios consultorios, LocalDate hoy) {
		int visitas = 0;
		for (int diasAtras = DIAS_HISTORIA; diasAtras >= 1; diasAtras--) {
			LocalDate dia = hoy.minusDays(diasAtras);
			if (dia.getDayOfWeek() != DayOfWeek.SUNDAY) {
				visitas += cargarDia(ids, personal, consultorios, dia);
			}
		}
		return visitas;
	}

	/** Entre 4 y 10 citas en el día, cada una de un paciente distinto (el sistema no limita las atenciones diarias). */
	private int cargarDia(long[] ids, Personal personal, Consultorios consultorios, LocalDate dia) {
		int visitas = 0;
		int citasDelDia = 4 + azar.nextInt(7);
		List<Integer> delDia = new ArrayList<>(IntStream.range(0, PACIENTES.size()).boxed().toList());
		Collections.shuffle(delDia, azar);
		for (int k = 0; k < citasDelDia; k++) {
			int idx = delDia.get(k);
			List<Plantilla> opciones = HISTORIA.get(idx);
			Plantilla plantilla = opciones.get(azar.nextInt(opciones.size()));
			boolean pediatrico = plantilla == CONTROL_NINO || plantilla == CONTROL_LACTANTE;
			long medico = pediatrico ? personal.draRuiz() : medicoGeneral(personal);
			long consultorio = consultorioDe(medico, pediatrico, personal, consultorios);
			LocalTime hora = LocalTime.of(8 + azar.nextInt(8), azar.nextBoolean() ? 0 : 30);
			CitaDemo cita = new CitaDemo(ids[idx], medico, consultorio, dia, hora);

			int suerte = azar.nextInt(100);
			if (suerte < 8) {
				citaSinAtencion(cita, NO_SE_PRESENTO, personal.admision(), null);
			}
			else if (suerte < 13) {
				citaSinAtencion(cita, CANCELADA, personal.admision(), "El paciente reprogramará");
			}
			else {
				atencionCompleta(cita, PACIENTES.get(idx), plantilla, personal.triaje(), personal.admision(), null);
				visitas++;
			}
		}
		return visitas;
	}

	private long medicoGeneral(Personal personal) {
		return azar.nextInt(2) == 0 ? personal.drDiaz() : personal.draTorres();
	}

	private static long consultorioDe(long medico, boolean pediatrico, Personal personal, Consultorios consultorios) {
		if (pediatrico) {
			return consultorios.pediatria();
		}
		return medico == personal.drDiaz() ? consultorios.general() : consultorios.general2();
	}

	/** Hoy: un paciente en cada etapa del flujo. */
	private void cargarHoy(long[] ids, Personal personal, Consultorios consultorios, Instant ahora, LocalDate hoy) {
		long admision = personal.admision();
		long triaje = personal.triaje();
		long drDiaz = personal.drDiaz();
		long draTorres = personal.draTorres();
		long draRuiz = personal.draRuiz();
		long consultorioGeneral = consultorios.general();
		long consultorioGeneral2 = consultorios.general2();
		long consultorioPediatria = consultorios.pediatria();
		// Programados para más tarde
		citaHoy(new CitaDemo(ids[1], drDiaz, consultorioGeneral, hoy, LocalTime.of(15, 0)), PROGRAMADA, admision, null,
				null);
		citaHoy(new CitaDemo(ids[7], draTorres, consultorioGeneral2, hoy, LocalTime.of(16, 0)), PROGRAMADA, admision, null,
				null);
		citaHoy(new CitaDemo(ids[2], draTorres, consultorioGeneral2, hoy, LocalTime.of(17, 0)), PROGRAMADA, admision, null,
				null);
		// Esperando triaje (llegaron hace 40, 22 y 6 minutos; el último sin cita)
		citaHoy(new CitaDemo(ids[11], drDiaz, consultorioGeneral, hoy, LocalTime.of(8, 30)), EN_ESPERA_TRIAJE, admision,
				ahora.minus(40, ChronoUnit.MINUTES), null);
		citaHoy(new CitaDemo(ids[4], draTorres, consultorioGeneral2, hoy, LocalTime.of(9, 0)), EN_ESPERA_TRIAJE, admision,
				ahora.minus(22, ChronoUnit.MINUTES), null);
		citaHoy(new CitaDemo(ids[9], drDiaz, consultorioGeneral, hoy, null), EN_ESPERA_TRIAJE, admision,
				ahora.minus(6, ChronoUnit.MINUTES), null);
		// Ya triados, esperando al médico: uno urgente (crisis hipertensiva), preferentes y normal
		esperandoConsulta(new CitaDemo(ids[3], drDiaz, consultorioGeneral, hoy, LocalTime.of(8, 0)), PACIENTES.get(3),
				new EvaluadorTriaje.Signos(186, 112, 96, 20, new BigDecimal("36.9"), 97, new BigDecimal("82"),
						new BigDecimal("168")),
				"Dolor de cabeza intenso y visión borrosa", ahora.minus(55, ChronoUnit.MINUTES), triaje, admision);
		esperandoConsulta(new CitaDemo(ids[10], drDiaz, consultorioGeneral, hoy, LocalTime.of(8, 15)), PACIENTES.get(10),
				new EvaluadorTriaje.Signos(135, 82, 78, 18, new BigDecimal("36.6"), 95, new BigDecimal("58"),
						new BigDecimal("150")),
				"Dolor de rodillas", ahora.minus(48, ChronoUnit.MINUTES), triaje, admision);
		esperandoConsulta(new CitaDemo(ids[13], drDiaz, consultorioGeneral, hoy, LocalTime.of(9, 30)), PACIENTES.get(13),
				new EvaluadorTriaje.Signos(118, 76, 72, 16, new BigDecimal("36.7"), 98, new BigDecimal("74"),
						new BigDecimal("172")),
				"Molestias al orinar", ahora.minus(30, ChronoUnit.MINUTES), triaje, admision);
		esperandoConsulta(new CitaDemo(ids[5], draRuiz, consultorioPediatria, hoy, LocalTime.of(9, 0)), PACIENTES.get(5),
				new EvaluadorTriaje.Signos(null, null, 138, 36, new BigDecimal("38.6"), 97, new BigDecimal("8.1"),
						new BigDecimal("68")),
				"Fiebre desde anoche", ahora.minus(25, ChronoUnit.MINUTES), triaje, admision);
		// Atendida esta mañana, no se presentó y cancelada
		atencionCompleta(new CitaDemo(ids[8], draTorres, consultorioGeneral2, hoy, LocalTime.of(8, 0)), PACIENTES.get(8),
				GASTRITIS, triaje, admision, ahora.minus(2, ChronoUnit.HOURS));
		citaHoy(new CitaDemo(ids[12], drDiaz, consultorioGeneral, hoy, LocalTime.of(7, 30)), NO_SE_PRESENTO, admision,
				null, null);
		citaHoy(new CitaDemo(ids[14], draTorres, consultorioGeneral2, hoy, LocalTime.of(10, 0)), CANCELADA, admision, null,
				"Viaje por trabajo");
	}

	// ------------------------------------------------------------------------------------------------------------
	// Inserción
	// ------------------------------------------------------------------------------------------------------------

	private long usuario(String username, String nombres, String apellidos, String dni, String rol, String cmp) {
		jdbc.update("""
				INSERT INTO usuarios (username, password_hash, nombres, apellidos, dni, rol, cmp, debe_cambiar_password)
				VALUES (?, ?, ?, ?, ?, ?, ?, FALSE) ON CONFLICT DO NOTHING""", username,
				passwordEncoder.encode(passwordDemo), nombres, apellidos, dni, rol, cmp);
		return jdbc.queryForObject("SELECT id FROM usuarios WHERE username = ?", Long.class, username);
	}

	private long paciente(PacienteDemo p, long registradoPor, Instant creado) {
		long numero = jdbc.queryForObject("SELECT nextval('seq_numero_hc')", Long.class);
		return jdbc.queryForObject("""
				INSERT INTO pacientes (numero_hc, tipo_documento, numero_documento, numero_documento_huella, nombres,
				    apellido_paterno, apellido_materno, fecha_nacimiento, sexo, telefono, direccion, tipo_financiamiento,
				    seguro_numero_afiliacion, seguro_plan, seguro_estado, seguro_verificado_en,
				    orientado_afiliacion_sis, creado_por, creado_en, actualizado_en)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""", Long.class,
				// Documento, teléfono y dirección cifrados, igual que al registrar por la aplicación
				"HC-%06d".formatted(numero), p.tipoDoc(), cifrado.cifrar(p.doc()), cifrado.huella(p.doc()),
				p.nombres(), p.paterno(), p.materno(), p.nacimiento(), p.sexo(), cifrado.cifrar(p.telefono()),
				cifrado.cifrar(p.direccion()), p.financiamiento(), p.afiliacion(), p.plan(),
				p.estadoSeguro(), ACTIVO.equals(p.estadoSeguro()) ? ts(creado) : null,
				PARTICULAR.equals(p.financiamiento()) && p.doc() != null && p.tipoDoc().equals("DNI"),
				registradoPor, ts(creado), ts(creado));
	}

	private void alergia(long pacienteId, String tipo, String sustancia, String reaccion, String gravedad,
			long registradoPor) {
		jdbc.update("""
				INSERT INTO alergias (paciente_id, tipo, sustancia, reaccion, gravedad, registrado_por)
				VALUES (?, ?, ?, ?, ?, ?)""", pacienteId, tipo, sustancia, reaccion, gravedad, registradoPor);
	}

	private int siguienteTurno(LocalDate fecha, long consultorio) {
		return turnos.merge(fecha + "/" + consultorio, 1, Integer::sum);
	}

	private void citaSinAtencion(CitaDemo c, String estado, long creadoPor, String motivoCancelacion) {
		Instant programada = instante(c.dia().minusDays(3), LocalTime.of(10, 0));
		jdbc.update("""
				INSERT INTO citas (paciente_id, medico_id, consultorio_id, fecha, hora, sin_cita, motivo, estado,
				    cancelada_en, motivo_cancelacion, creado_por, creado_en, actualizado_en)
				VALUES (?, ?, ?, ?, ?, FALSE, 'Control', ?, ?, ?, ?, ?, ?)""", c.paciente(), c.medico(), c.consultorio(),
				c.dia(), c.hora(), estado, CANCELADA.equals(estado) ? ts(programada.plus(1, ChronoUnit.DAYS)) : null,
				motivoCancelacion, creadoPor, ts(programada), ts(programada));
	}

	/** Cita de hoy que aún no pasó por triaje (PROGRAMADA, EN_ESPERA_TRIAJE, NO_SE_PRESENTO o CANCELADA). */
	private void citaHoy(CitaDemo c, String estado, long creadoPor, Instant llegada, String motivoCancelacion) {
		LocalDate hoy = c.dia();
		LocalTime hora = c.hora();
		long consultorio = c.consultorio();
		Integer turno = llegada != null ? siguienteTurno(hoy, consultorio) : null;
		Instant creada = llegada != null && hora == null ? llegada : Instant.now().minus(2, ChronoUnit.DAYS);
		jdbc.update("""
				INSERT INTO citas (paciente_id, medico_id, consultorio_id, fecha, hora, sin_cita, motivo, estado,
				    numero_turno, llegada_en, cancelada_en, motivo_cancelacion, creado_por, creado_en, actualizado_en)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", c.paciente(), c.medico(), consultorio, hoy, hora,
				hora == null, hora == null ? null : "Consulta", estado, turno, ts(llegada),
				CANCELADA.equals(estado) ? ts(Instant.now().minus(1, ChronoUnit.DAYS)) : null, motivoCancelacion,
				creadoPor, ts(creada), ts(creada));
	}

	/** Cita de hoy con triaje registrado, esperando al médico. */
	private void esperandoConsulta(CitaDemo c, PacienteDemo datos, EvaluadorTriaje.Signos signos, String motivo,
			Instant llegada, long triaje, long creadoPor) {
		LocalDate hoy = c.dia();
		Instant triado = llegada.plus(12, ChronoUnit.MINUTES);
		var ev = evaluar(signos, datos);
		long cita = jdbc.queryForObject("""
				INSERT INTO citas (paciente_id, medico_id, consultorio_id, fecha, hora, sin_cita, motivo, estado,
				    numero_turno, prioridad, llegada_en, triaje_en, creado_por, creado_en, actualizado_en)
				VALUES (?, ?, ?, ?, ?, FALSE, 'Consulta', 'EN_ESPERA_CONSULTA', ?, ?, ?, ?, ?, ?, ?) RETURNING id""",
				Long.class, c.paciente(), c.medico(), c.consultorio(), hoy, c.hora(), siguienteTurno(hoy, c.consultorio()),
				ev.prioridadSugerida().name(), ts(llegada), ts(triado), creadoPor, ts(llegada), ts(triado));
		triaje(cita, c.paciente(), triaje, triado, motivo, signos, ev);
	}

	/** Cita atendida: llegada, triaje, atención con diagnóstico y receta, y cierre. */
	private void atencionCompleta(CitaDemo c, PacienteDemo datos, Plantilla p, long triaje, long creadoPor,
			Instant llegadaHoy) {
		long paciente = c.paciente();
		long medico = c.medico();
		LocalDate dia = c.dia();
		Instant llegada = llegadaHoy != null ? llegadaHoy
				: instante(dia, c.hora()).minus(azar.nextInt(15), ChronoUnit.MINUTES);
		Instant triado = llegada.plus(8L + azar.nextInt(20), ChronoUnit.MINUTES);
		Instant inicio = triado.plus(10L + azar.nextInt(40), ChronoUnit.MINUTES);
		Instant fin = inicio.plus(12L + azar.nextInt(15), ChronoUnit.MINUTES);
		EvaluadorTriaje.Signos signos = signos(p.signos(), datos);
		var ev = evaluar(signos, datos);

		long cita = jdbc.queryForObject("""
				INSERT INTO citas (paciente_id, medico_id, consultorio_id, fecha, hora, sin_cita, motivo, estado,
				    numero_turno, prioridad, llegada_en, triaje_en, consulta_inicio_en, atendido_en, creado_por,
				    creado_en, actualizado_en)
				VALUES (?, ?, ?, ?, ?, FALSE, ?, 'ATENDIDO', ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""", Long.class,
				paciente, medico, c.consultorio(), dia, c.hora(), p.motivo(), siguienteTurno(dia, c.consultorio()),
				ev.prioridadSugerida().name(), ts(llegada), ts(triado), ts(inicio), ts(fin), creadoPor,
				ts(llegada.minus(2, ChronoUnit.DAYS)), ts(fin));
		triaje(cita, paciente, triaje, triado, p.motivo(), signos, ev);

		long atencion = jdbc.queryForObject("""
				INSERT INTO atenciones (cita_id, paciente_id, medico_id, estado, inicio_en, motivo_consulta,
				    tiempo_enfermedad, anamnesis, examen_fisico, plan_trabajo, indicaciones, creado_en, actualizado_en)
				VALUES (?, ?, ?, 'EN_CURSO', ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""", Long.class, cita, paciente,
				medico, ts(inicio), p.motivo(), p.tiempo(), p.anamnesis(), p.examen(), p.plan(), p.indicaciones(),
				ts(inicio), ts(inicio));
		jdbc.update("""
				INSERT INTO atencion_diagnosticos (atencion_id, orden, cie10_codigo, tipo, principal)
				VALUES (?, 0, ?, ?, TRUE)""", atencion, p.cie10(), p.tipoDx());
		for (int i = 0; i < p.receta().size(); i++) {
			RecetaDemo r = p.receta().get(i);
			long medicamento = jdbc.queryForObject(
					"SELECT id FROM medicamentos WHERE principio_activo = ? AND concentracion = ?", Long.class,
					r.principioActivo(), r.concentracion());
			jdbc.update("""
					INSERT INTO atencion_receta (atencion_id, orden, medicamento_id, dosis, via, frecuencia, duracion,
					    cantidad) VALUES (?, ?, ?, ?, ?, ?, ?, ?)""", atencion, i, medicamento, r.dosis(), r.via(),
					r.frecuencia(), r.duracion(), r.cantidad());
		}
		// Se cierra al final: la BD no admite cambios en diagnósticos ni receta de una atención cerrada
		jdbc.update("UPDATE atenciones SET estado = 'CERRADA', cerrada_en = ?, actualizado_en = ? WHERE id = ?",
				ts(fin), ts(fin), atencion);
	}

	private void triaje(long cita, long paciente, long registradoPor, Instant fecha, String motivo,
			EvaluadorTriaje.Signos s, EvaluadorTriaje.Evaluacion ev) {
		long id = jdbc.queryForObject("""
				INSERT INTO triajes (cita_id, paciente_id, registrado_por, fecha_hora, motivo_consulta,
				    presion_sistolica, presion_diastolica, frecuencia_cardiaca, frecuencia_respiratoria, temperatura,
				    saturacion, peso, talla, imc, prioridad_sugerida, prioridad, creado_en, actualizado_en)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""", Long.class, cita,
				paciente, registradoPor, ts(fecha), motivo, s.presionSistolica(), s.presionDiastolica(),
				s.frecuenciaCardiaca(), s.frecuenciaRespiratoria(), s.temperatura(), s.saturacion(), s.peso(),
				s.talla(), ev.imc(), ev.prioridadSugerida().name(), ev.prioridadSugerida().name(), ts(fecha),
				ts(fecha));
		for (Alerta a : ev.alertas()) {
			jdbc.update("INSERT INTO triaje_alertas (triaje_id, codigo, severidad, mensaje) VALUES (?, ?, ?, ?)", id,
					a.getCodigo(), a.getSeveridad().name(), a.getMensaje());
		}
	}

	private EvaluadorTriaje.Evaluacion evaluar(EvaluadorTriaje.Signos s, PacienteDemo p) {
		return evaluador.evaluar(s, new EvaluadorTriaje.Condiciones(Edad.anios(p.nacimiento()), false, false));
	}

	/** Signos vitales verosímiles según el tipo de consulta y la edad. */
	private EvaluadorTriaje.Signos signos(String tipo, PacienteDemo p) {
		int edad = Edad.anios(p.nacimiento());
		if ("nino".equals(tipo) || edad < 12) {
			BigDecimal peso = edad < 1 ? new BigDecimal("7.9") : new BigDecimal(String.valueOf(14 + edad * 2));
			BigDecimal talla = edad < 1 ? new BigDecimal("67") : new BigDecimal(String.valueOf(85 + edad * 6));
			return new EvaluadorTriaje.Signos(null, null, 95 + azar.nextInt(25), 22 + azar.nextInt(8),
					temperatura(36.4, 0.5), 97 + azar.nextInt(3), peso, talla);
		}
		int pas = "hta".equals(tipo) ? 138 + azar.nextInt(18) : 105 + azar.nextInt(25);
		int pad = "hta".equals(tipo) ? 86 + azar.nextInt(10) : 65 + azar.nextInt(15);
		BigDecimal temp = "fiebre".equals(tipo) ? temperatura(38.0, 1.0) : temperatura(36.4, 0.6);
		return new EvaluadorTriaje.Signos(pas, pad, 65 + azar.nextInt(25), 14 + azar.nextInt(6), temp,
				95 + azar.nextInt(4), new BigDecimal(String.valueOf(55 + azar.nextInt(35))),
				new BigDecimal(String.valueOf(150 + azar.nextInt(30))));
	}

	private BigDecimal temperatura(double base, double rango) {
		return BigDecimal.valueOf(base + azar.nextDouble() * rango).setScale(1, java.math.RoundingMode.HALF_UP);
	}

	private static Instant instante(LocalDate dia, LocalTime hora) {
		return ZonedDateTime.of(dia, hora, Tiempo.ZONA).toInstant();
	}

	private static Timestamp ts(Instant instante) {
		return instante == null ? null : Timestamp.from(instante);
	}

}
