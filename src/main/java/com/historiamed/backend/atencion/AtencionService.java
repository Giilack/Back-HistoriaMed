package com.historiamed.backend.atencion;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.historiamed.backend.alergia.AlergiaService;
import com.historiamed.backend.atencion.dto.AtencionRequest;
import com.historiamed.backend.atencion.dto.AtencionResponse;
import com.historiamed.backend.auditoria.AccionAuditoria;
import com.historiamed.backend.auditoria.AuditoriaService;
import com.historiamed.backend.catalogo.CatalogoService;
import com.historiamed.backend.catalogo.Medicamento;
import com.historiamed.backend.cita.Cita;
import com.historiamed.backend.cita.CitaService;
import com.historiamed.backend.cita.EstadoCita;
import com.historiamed.backend.common.exception.AccesoProhibidoException;
import com.historiamed.backend.common.exception.RecursoNoEncontradoException;
import com.historiamed.backend.common.exception.ReglaNegocioException;
import com.historiamed.backend.common.security.UsuarioActual;
import com.historiamed.backend.paciente.PacienteService;
import com.historiamed.backend.triaje.TriajeService;
import com.historiamed.backend.usuario.Usuario;
import com.historiamed.backend.usuario.UsuarioService;

import lombok.RequiredArgsConstructor;

/**
 * Atención médica (plan.md, sección 5.4): iniciar desde la cola, guardar borradores, cerrar (firmar) y agregar
 * adendas. Incluye la alerta determinista de alergias al recetar (principio P8).
 */
@Service
@RequiredArgsConstructor
public class AtencionService {

	private static final String RECURSO = "ATENCION";

	private static final int HISTORIA_MAXIMO = 50;

	/** Código de error que el frontend reconoce para pedir la confirmación de la alergia. */
	public static final String ERROR_ALERGIA = "ALERGIA_MEDICAMENTO";

	private final AtencionRepository repository;

	private final AdendaRepository adendaRepository;

	private final CitaService citaService;

	private final TriajeService triajeService;

	private final AlergiaService alergiaService;

	private final PacienteService pacienteService;

	private final CatalogoService catalogoService;

	private final UsuarioService usuarioService;

	private final VerificadorAlergias verificadorAlergias;

	private final AuditoriaService auditoria;

	/**
	 * El médico llama al paciente: la cita pasa a EN_CONSULTA y se abre la atención. Si ya estaba abierta, la
	 * devuelve (permite continuar una atención interrumpida).
	 */
	@Transactional
	public AtencionResponse iniciar(Long citaId) {
		Cita cita = citaService.obtenerEntidad(citaId);
		UsuarioActual yo = UsuarioActual.requerido();
		if (!cita.getMedico().getId().equals(yo.id())) {
			throw new AccesoProhibidoException("Solo el médico asignado a la cita puede atenderla");
		}
		var existente = repository.findByCitaId(citaId);
		if (existente.isPresent()) {
			return AtencionResponse.de(existente.get());
		}
		if (cita.getEstado() != EstadoCita.EN_ESPERA_CONSULTA) {
			throw new ReglaNegocioException("El paciente no está esperando consulta (estado " + cita.getEstado() + ")");
		}
		Instant ahora = Instant.now();
		cita.cambiarEstado(EstadoCita.EN_CONSULTA, ahora);

		Atencion a = new Atencion();
		a.setCita(cita);
		a.setPaciente(cita.getPaciente());
		a.setMedico(cita.getMedico());
		a.setInicioEn(ahora);
		a.setMotivoConsulta(triajeService.motivoDeCita(citaId)
			.orElse(Objects.requireNonNullElse(cita.getMotivo(), "Consulta")));
		repository.save(a);
		auditar(AccionAuditoria.CREAR, a, "Inicio de la atención");
		return AtencionResponse.de(a);
	}

	@Transactional
	public AtencionResponse ver(Long id) {
		Atencion a = obtener(id);
		auditar(AccionAuditoria.VER, a, null);
		return AtencionResponse.de(a);
	}

	@Transactional
	public AtencionResponse verPorCita(Long citaId) {
		Atencion a = repository.findByCitaId(citaId)
			.orElseThrow(() -> new RecursoNoEncontradoException("Atención de la cita", citaId));
		auditar(AccionAuditoria.VER, a, null);
		return AtencionResponse.de(a);
	}

	/** Guarda el borrador (reemplaza todo el contenido). Solo el médico que la inició, y solo si está en curso. */
	@Transactional
	public AtencionResponse guardar(Long id, AtencionRequest req) {
		Atencion a = obtenerParaEditar(id);
		aplicar(a, req);
		auditar(AccionAuditoria.EDITAR, a, "Borrador");
		return AtencionResponse.de(a);
	}

	/**
	 * Guarda el contenido final y firma la atención: queda inmutable y la cita pasa a ATENDIDO.
	 */
	@Transactional
	public AtencionResponse cerrar(Long id, AtencionRequest req) {
		Atencion a = obtenerParaEditar(id);
		aplicar(a, req);
		validarParaCierre(a);
		// Se escriben diagnósticos y receta ANTES de marcarla cerrada: la BD rechaza cambios en atenciones cerradas
		repository.flush();

		Instant ahora = Instant.now();
		a.cerrar(ahora);
		a.getCita().cambiarEstado(EstadoCita.ATENDIDO, ahora);
		auditar(AccionAuditoria.CERRAR, a, a.getDiagnosticos().size() + " diagnóstico(s), "
				+ a.getReceta().size() + " medicamento(s)");
		return AtencionResponse.de(a);
	}

	/** Corrección o aclaración a una atención cerrada. La puede agregar cualquier médico; queda su autoría. */
	@Transactional
	public AtencionResponse agregarAdenda(Long id, String texto) {
		Atencion a = obtener(id);
		Usuario autor = usuarioService.obtener(UsuarioActual.requerido().id());
		adendaRepository.save(a.agregarAdenda(autor, texto.trim()));
		auditar(AccionAuditoria.CREAR, a, "Adenda");
		return AtencionResponse.de(a);
	}

	/** Historia clínica: atenciones del paciente, de la más reciente a la más antigua. */
	@Transactional
	public List<AtencionResponse> historia(Long pacienteId) {
		pacienteService.obtener(pacienteId);
		List<Atencion> atenciones = repository.findByPacienteIdOrderByInicioEnDesc(pacienteId,
				PageRequest.of(0, HISTORIA_MAXIMO));
		auditoria.registrarSobrePaciente(AccionAuditoria.VER, RECURSO, null, pacienteId, "Historia clínica");
		return atenciones.stream().map(AtencionResponse::de).toList();
	}

	private void aplicar(Atencion a, AtencionRequest req) {
		a.setMotivoConsulta(limpiar(req.motivoConsulta()));
		a.setTiempoEnfermedad(limpiar(req.tiempoEnfermedad()));
		a.setAnamnesis(limpiar(req.anamnesis()));
		a.setExamenFisico(limpiar(req.examenFisico()));
		a.setPlanTrabajo(limpiar(req.planTrabajo()));
		a.setIndicaciones(limpiar(req.indicaciones()));
		a.reemplazarContenido(diagnosticos(req.diagnosticos()), receta(a, req.receta()));
	}

	private List<Diagnostico> diagnosticos(List<AtencionRequest.DiagnosticoRequest> pedidos) {
		Set<String> codigos = new HashSet<>();
		long principales = pedidos.stream().filter(AtencionRequest.DiagnosticoRequest::principal).count();
		if (principales > 1) {
			throw new ReglaNegocioException("Solo un diagnóstico puede ser el principal");
		}
		List<Diagnostico> lista = new ArrayList<>();
		for (var d : pedidos) {
			var cie10 = catalogoService.obtenerCie10(d.codigo());
			if (!codigos.add(cie10.getCodigo())) {
				throw new ReglaNegocioException("El diagnóstico " + cie10.getCodigo() + " está repetido");
			}
			lista.add(new Diagnostico(cie10, d.tipo(), d.principal()));
		}
		return lista;
	}

	/**
	 * Arma la receta verificando cada medicamento contra las alergias activas del paciente. Si alguno coincide y
	 * el médico no lo confirmó con una justificación, se rechaza con el detalle por ítem (receta[i]).
	 */
	private List<ItemReceta> receta(Atencion a, List<AtencionRequest.ItemRecetaRequest> pedidos) {
		List<VerificadorAlergias.AlergiaActiva> alergias = alergiaService.activas(a.getPaciente().getId())
			.stream()
			.map(al -> new VerificadorAlergias.AlergiaActiva(al.getSustancia(), al.getGravedad().name()))
			.toList();

		Set<Long> medicamentos = new HashSet<>();
		Map<String, String> pendientes = new LinkedHashMap<>();
		List<ItemReceta> lista = new ArrayList<>();
		for (int i = 0; i < pedidos.size(); i++) {
			var p = pedidos.get(i);
			Medicamento m = catalogoService.obtenerMedicamento(p.medicamentoId());
			if (!medicamentos.add(m.getId())) {
				throw new ReglaNegocioException(m.descripcion() + " está repetido en la receta");
			}
			var coincidencias = verificadorAlergias.verificar(m, alergias);
			boolean confirmada = Boolean.TRUE.equals(p.confirmarAlergia());
			String justificacion = limpiar(p.justificacionAlergia());
			if (!coincidencias.isEmpty()) {
				String detalle = m.descripcion() + ": " + coincidencias.stream()
					.map(c -> "alergia a " + c.alergia().sustancia() + " (" + c.alergia().gravedad() + "), "
							+ c.motivo())
					.collect(Collectors.joining("; "));
				if (!confirmada) {
					pendientes.put("receta[" + i + "]", detalle);
				}
				else if (justificacion == null) {
					pendientes.put("receta[" + i + "]", detalle + ". Escriba la justificación para recetarlo");
				}
			}
			boolean conAlergia = !coincidencias.isEmpty();
			lista.add(new ItemReceta(m, limpiar(p.dosis()), p.via(), limpiar(p.frecuencia()), limpiar(p.duracion()),
					p.cantidad(), limpiar(p.indicaciones()), conAlergia, conAlergia ? justificacion : null));
		}
		if (!pendientes.isEmpty()) {
			throw new ReglaNegocioException(ERROR_ALERGIA,
					"Hay medicamentos que coinciden con alergias registradas del paciente. Si decide recetarlos, "
							+ "confírmelo y escriba la justificación",
					pendientes);
		}
		return lista;
	}

	private static void validarParaCierre(Atencion a) {
		List<String> faltantes = new ArrayList<>();
		if (a.getAnamnesis() == null) {
			faltantes.add("anamnesis");
		}
		if (a.getExamenFisico() == null) {
			faltantes.add("examen físico");
		}
		if (a.getDiagnosticos().isEmpty()) {
			faltantes.add("al menos un diagnóstico");
		}
		if (!faltantes.isEmpty()) {
			throw new ReglaNegocioException("Para cerrar la atención falta: " + String.join(", ", faltantes));
		}
		if (a.getDiagnosticos().stream().noneMatch(Diagnostico::isPrincipal)) {
			throw new ReglaNegocioException("Marque cuál es el diagnóstico principal");
		}
	}

	private Atencion obtenerParaEditar(Long id) {
		Atencion a = obtener(id);
		if (!a.getMedico().getId().equals(UsuarioActual.requerido().id())) {
			throw new AccesoProhibidoException("Solo el médico que inició la atención puede editarla");
		}
		a.exigirEnCurso();
		return a;
	}

	private Atencion obtener(Long id) {
		return repository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Atención", id));
	}

	private void auditar(AccionAuditoria accion, Atencion a, String detalle) {
		auditoria.registrarSobrePaciente(accion, RECURSO, a.getId(), a.getPaciente().getId(), detalle);
	}

	private static String limpiar(String texto) {
		return texto == null || texto.isBlank() ? null : texto.trim();
	}

}
