package com.historiamed.backend.triaje;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.historiamed.backend.auditoria.AccionAuditoria;
import com.historiamed.backend.auditoria.AuditoriaService;
import com.historiamed.backend.cita.Cita;
import com.historiamed.backend.cita.CitaService;
import com.historiamed.backend.cita.EstadoCita;
import com.historiamed.backend.cita.Prioridad;
import com.historiamed.backend.common.exception.RecursoNoEncontradoException;
import com.historiamed.backend.common.exception.ReglaNegocioException;
import com.historiamed.backend.common.security.UsuarioActual;
import com.historiamed.backend.common.util.Edad;
import com.historiamed.backend.paciente.Paciente;
import com.historiamed.backend.paciente.PacienteService;
import com.historiamed.backend.paciente.Sexo;
import com.historiamed.backend.triaje.dto.EvaluacionResponse;
import com.historiamed.backend.triaje.dto.TriajeRequest;
import com.historiamed.backend.triaje.dto.TriajeResponse;
import com.historiamed.backend.usuario.UsuarioService;

import lombok.RequiredArgsConstructor;

/**
 * Triaje: signos vitales, alertas y prioridad (plan.md, sección 5.3). Al registrarlo, el paciente pasa a la cola
 * del médico.
 */
@Service
@RequiredArgsConstructor
public class TriajeService {

	private static final String RECURSO = "TRIAJE";

	private static final int HISTORIAL_MAXIMO = 10;

	private final TriajeRepository repository;

	private final CitaService citaService;

	private final PacienteService pacienteService;

	private final UsuarioService usuarioService;

	private final EvaluadorTriaje evaluador;

	private final AuditoriaService auditoria;

	/** Vista previa: calcula alertas, IMC y prioridad sugerida sin guardar nada. */
	@Transactional(readOnly = true)
	public EvaluacionResponse evaluar(Long citaId, TriajeRequest req) {
		Paciente p = citaService.obtenerEntidad(citaId).getPaciente();
		return EvaluacionResponse.de(evaluar(p, req));
	}

	@Transactional
	public TriajeResponse registrar(Long citaId, TriajeRequest req) {
		Cita cita = citaService.obtenerEntidad(citaId);
		if (repository.existsByCitaId(citaId)) {
			throw new ReglaNegocioException("Esta cita ya tiene un triaje registrado");
		}
		if (cita.getEstado() != EstadoCita.EN_ESPERA_TRIAJE) {
			throw new ReglaNegocioException("El paciente no está en la cola de triaje (estado " + cita.getEstado()
					+ ")");
		}
		Paciente p = cita.getPaciente();
		EvaluadorTriaje.Evaluacion ev = evaluar(p, req);

		Prioridad prioridad = req.prioridad() != null ? req.prioridad() : ev.prioridadSugerida();
		String justificacion = limpiar(req.justificacionPrioridad());
		if (prioridad.esMenorQue(ev.prioridadSugerida()) && justificacion == null) {
			throw new ReglaNegocioException("El sistema sugiere prioridad " + ev.prioridadSugerida()
					+ ". Para asignar " + prioridad + " escriba una justificación");
		}

		Instant ahora = Instant.now();
		Triaje t = new Triaje();
		t.setCita(cita);
		t.setPaciente(p);
		t.setRegistradoPor(usuarioService.obtener(UsuarioActual.requerido().id()));
		t.setFechaHora(ahora);
		t.setMotivoConsulta(limpiar(req.motivoConsulta()));
		t.setPresionSistolica(req.presionSistolica());
		t.setPresionDiastolica(req.presionDiastolica());
		t.setFrecuenciaCardiaca(req.frecuenciaCardiaca());
		t.setFrecuenciaRespiratoria(req.frecuenciaRespiratoria());
		t.setTemperatura(req.temperatura());
		t.setSaturacion(req.saturacion());
		t.setPeso(req.peso());
		t.setTalla(req.talla());
		t.setImc(ev.imc());
		t.setPerimetroAbdominal(req.perimetroAbdominal());
		t.setGestante(Boolean.TRUE.equals(req.gestante()));
		t.setDiscapacidad(Boolean.TRUE.equals(req.discapacidad()));
		t.setPrioridadSugerida(ev.prioridadSugerida());
		t.setPrioridad(prioridad);
		t.setJustificacionPrioridad(prioridad.esMenorQue(ev.prioridadSugerida()) ? justificacion : null);
		t.setObservaciones(limpiar(req.observaciones()));
		t.getAlertas().addAll(ev.alertas());
		repository.save(t);

		cita.completarTriaje(prioridad, ahora);
		auditoria.registrarSobrePaciente(AccionAuditoria.CREAR, RECURSO, t.getId(), p.getId(),
				"Prioridad " + prioridad + ", " + ev.alertas().size() + " alerta(s)");
		return TriajeResponse.de(t);
	}

	@Transactional
	public TriajeResponse verPorCita(Long citaId) {
		Triaje t = repository.findByCitaId(citaId)
			.orElseThrow(() -> new RecursoNoEncontradoException("Triaje de la cita", citaId));
		auditoria.registrarSobrePaciente(AccionAuditoria.VER, RECURSO, t.getId(), t.getPaciente().getId(), null);
		return TriajeResponse.de(t);
	}

	/** Motivo de consulta registrado en el triaje de la cita (uso interno, sin auditar como consulta). */
	@Transactional(readOnly = true)
	public Optional<String> motivoDeCita(Long citaId) {
		return repository.findByCitaId(citaId).map(Triaje::getMotivoConsulta);
	}

	/** Últimos triajes del paciente (más recientes primero), para ver la evolución de sus signos vitales. */
	@Transactional
	public List<TriajeResponse> historial(Long pacienteId) {
		pacienteService.obtener(pacienteId);
		List<Triaje> triajes = repository.findByPacienteIdOrderByFechaHoraDesc(pacienteId,
				PageRequest.of(0, HISTORIAL_MAXIMO));
		auditoria.registrarSobrePaciente(AccionAuditoria.VER, RECURSO, null, pacienteId, "Historial de triajes");
		return triajes.stream().map(TriajeResponse::de).toList();
	}

	private EvaluadorTriaje.Evaluacion evaluar(Paciente p, TriajeRequest req) {
		validar(p, req);
		var signos = new EvaluadorTriaje.Signos(req.presionSistolica(), req.presionDiastolica(),
				req.frecuenciaCardiaca(), req.frecuenciaRespiratoria(), req.temperatura(), req.saturacion(),
				req.peso(), req.talla());
		var condiciones = new EvaluadorTriaje.Condiciones(Edad.anios(p.getFechaNacimiento()),
				Boolean.TRUE.equals(req.gestante()), Boolean.TRUE.equals(req.discapacidad()));
		return evaluador.evaluar(signos, condiciones);
	}

	private static void validar(Paciente p, TriajeRequest req) {
		if ((req.presionSistolica() == null) != (req.presionDiastolica() == null)) {
			throw new ReglaNegocioException("Registre la presión sistólica y la diastólica, o ninguna");
		}
		if (req.presionSistolica() != null && req.presionSistolica() <= req.presionDiastolica()) {
			throw new ReglaNegocioException("La presión sistólica debe ser mayor que la diastólica");
		}
		if (Boolean.TRUE.equals(req.gestante()) && p.getSexo() == Sexo.MASCULINO) {
			throw new ReglaNegocioException("No se puede marcar como gestante a un paciente de sexo masculino");
		}
	}

	private static String limpiar(String texto) {
		return texto == null || texto.isBlank() ? null : texto.trim();
	}

}
