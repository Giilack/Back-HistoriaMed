package com.historiamed.backend.cita;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.historiamed.backend.auditoria.AccionAuditoria;
import com.historiamed.backend.auditoria.AuditoriaService;
import com.historiamed.backend.cita.dto.CitaRequest;
import com.historiamed.backend.cita.dto.CitaResponse;
import com.historiamed.backend.cita.dto.LlegadaSinCitaRequest;
import com.historiamed.backend.common.exception.RecursoNoEncontradoException;
import com.historiamed.backend.common.exception.ReglaNegocioException;
import com.historiamed.backend.common.security.UsuarioActual;
import com.historiamed.backend.common.util.Tiempo;
import com.historiamed.backend.consultorio.Consultorio;
import com.historiamed.backend.consultorio.ConsultorioService;
import com.historiamed.backend.paciente.Paciente;
import com.historiamed.backend.paciente.PacienteService;
import com.historiamed.backend.usuario.Rol;
import com.historiamed.backend.usuario.Usuario;
import com.historiamed.backend.usuario.UsuarioService;

import lombok.RequiredArgsConstructor;

/**
 * Citas, llegada y cola de atención. Reglas en plan.md, sección 4.
 */
@Service
@RequiredArgsConstructor
public class CitaService {

	private static final String RECURSO = "CITA";

	private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	/** Valor que no coincide con ningún id: para "excluir" nada al validar una cita nueva. */
	private static final long NINGUNA = -1L;

	private final CitaRepository repository;

	private final PacienteService pacienteService;

	private final UsuarioService usuarioService;

	private final ConsultorioService consultorioService;

	private final AuditoriaService auditoria;

	/**
	 * Agenda o cola. Hay que indicar la fecha o el paciente. Un MEDICO solo ve sus propias citas.
	 */
	@Transactional(readOnly = true)
	public List<CitaResponse> listar(LocalDate fecha, Long pacienteId, Long medicoId, Long consultorioId,
			Set<EstadoCita> estados) {
		if (fecha == null && pacienteId == null) {
			throw new ReglaNegocioException("Indique la fecha o el paciente");
		}
		UsuarioActual actual = UsuarioActual.requerido();
		Long medico = Rol.MEDICO.name().equals(actual.rol()) ? actual.id() : medicoId;

		Specification<Cita> spec = Specification.unrestricted();
		if (fecha != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("fecha"), fecha));
		}
		if (pacienteId != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("paciente").get("id"), pacienteId));
		}
		if (medico != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("medico").get("id"), medico));
		}
		if (consultorioId != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("consultorio").get("id"), consultorioId));
		}
		if (estados != null && !estados.isEmpty()) {
			spec = spec.and((root, query, cb) -> root.get("estado").in(estados));
		}
		// Orden de agenda: por fecha y hora; los que llegaron sin cita (hora NULL, que PostgreSQL ordena al final
		// en orden ascendente) quedan después, por orden de llegada
		Sort orden = Sort.by(Sort.Order.desc("fecha"), Sort.Order.asc("hora"), Sort.Order.asc("llegadaEn"));
		return repository.findAll(spec, orden).stream().map(CitaResponse::de).toList();
	}

	@Transactional(readOnly = true)
	public CitaResponse ver(Long id) {
		return CitaResponse.de(obtener(id));
	}

	/**
	 * ¿El paciente tiene una cita posterior a esa fecha, programada, en curso o ya atendida? Las canceladas y las
	 * inasistencias no cuentan. Sirve para saber si un control sugerido ya se agendó.
	 */
	@Transactional(readOnly = true)
	public boolean tieneCitaPosterior(Long pacienteId, LocalDate despuesDe) {
		Set<EstadoCita> estados = EnumSet.copyOf(EstadoCita.ACTIVOS);
		estados.add(EstadoCita.ATENDIDO);
		return repository.pacienteTieneCitaPosterior(pacienteId, despuesDe, estados);
	}

	/** Para otros módulos (triaje, consulta) que operan sobre la cita dentro de su propia transacción. */
	@Transactional(readOnly = true)
	public Cita obtenerEntidad(Long id) {
		return obtener(id);
	}

	@Transactional
	public CitaResponse programar(CitaRequest req) {
		Paciente paciente = pacienteActivo(req.pacienteId());
		Cita c = new Cita();
		c.setPaciente(paciente);
		c.setCreadoPor(UsuarioActual.requerido().id());
		c.iniciar(false, Instant.now());
		aplicarProgramacion(c, req);
		repository.save(c);
		auditar(AccionAuditoria.CREAR, c, "Cita " + descripcion(c));
		return CitaResponse.de(c);
	}

	/** Paciente que llega sin cita: se registra para hoy y entra directo a la cola de triaje. */
	@Transactional
	public CitaResponse registrarSinCita(LlegadaSinCitaRequest req) {
		Paciente paciente = pacienteActivo(req.pacienteId());
		Consultorio consultorio = consultorioService.obtenerActivo(req.consultorioId());
		LocalDate hoy = Tiempo.hoy();
		validarTurnoUnico(paciente.getId(), hoy, consultorio.getId(), NINGUNA);

		Cita c = new Cita();
		c.setPaciente(paciente);
		c.setMedico(usuarioService.obtenerMedicoActivo(req.medicoId()));
		c.setConsultorio(consultorio);
		c.setFecha(hoy);
		c.setMotivo(limpiar(req.motivo()));
		c.setCreadoPor(UsuarioActual.requerido().id());
		c.iniciar(true, Instant.now());
		c.setNumeroTurno(repository.ultimoTurno(hoy, consultorio.getId()) + 1);
		repository.save(c);
		auditar(AccionAuditoria.CREAR, c, "Llegada sin cita, turno " + c.getNumeroTurno());
		return CitaResponse.de(c);
	}

	@Transactional
	public CitaResponse reprogramar(Long id, CitaRequest req) {
		Cita c = obtener(id);
		if (c.getEstado() != EstadoCita.PROGRAMADA) {
			throw new ReglaNegocioException("Solo se puede reprogramar una cita en estado PROGRAMADA");
		}
		String antes = descripcion(c);
		aplicarProgramacion(c, req);
		auditar(AccionAuditoria.EDITAR, c, "Reprogramada: " + antes + " -> " + descripcion(c));
		return CitaResponse.de(c);
	}

	/** El paciente llegó: pasa a la cola de triaje con su número de turno. Solo el día de la cita. */
	@Transactional
	public CitaResponse registrarLlegada(Long id) {
		Cita c = obtener(id);
		LocalDate hoy = Tiempo.hoy();
		if (c.getEstado() == EstadoCita.PROGRAMADA && !c.getFecha().equals(hoy)) {
			throw new ReglaNegocioException("La cita es para el " + c.getFecha().format(FORMATO_FECHA)
					+ "; solo se registra la llegada ese día. Reprográmela si corresponde");
		}
		c.cambiarEstado(EstadoCita.EN_ESPERA_TRIAJE, Instant.now());
		c.setNumeroTurno(repository.ultimoTurno(hoy, c.getConsultorio().getId()) + 1);
		auditar(AccionAuditoria.EDITAR, c, "Llegada registrada, turno " + c.getNumeroTurno());
		return CitaResponse.de(c);
	}

	@Transactional
	public CitaResponse cancelar(Long id, String motivo) {
		Cita c = obtener(id);
		c.cambiarEstado(EstadoCita.CANCELADA, Instant.now());
		c.setMotivoCancelacion(limpiar(motivo));
		auditar(AccionAuditoria.EDITAR, c, "Cancelada: " + c.getMotivoCancelacion());
		return CitaResponse.de(c);
	}

	/**
	 * No acudió a su cita, o no respondió al llamado estando en la cola. Una cita futura se cancela, no se marca.
	 */
	@Transactional
	public CitaResponse marcarNoSePresento(Long id) {
		Cita c = obtener(id);
		if (c.getEstado() == EstadoCita.PROGRAMADA && c.getFecha().isAfter(Tiempo.hoy())) {
			throw new ReglaNegocioException("La cita aún no llega a su fecha; si no vendrá, cancélela");
		}
		EstadoCita anterior = c.getEstado();
		c.cambiarEstado(EstadoCita.NO_SE_PRESENTO, Instant.now());
		auditar(AccionAuditoria.EDITAR, c, "No se presentó (estaba " + anterior + ")");
		return CitaResponse.de(c);
	}

	private void aplicarProgramacion(Cita c, CitaRequest req) {
		if (req.fecha().isBefore(Tiempo.hoy())) {
			throw new ReglaNegocioException("No se puede programar una cita en una fecha pasada");
		}
		Usuario medico = usuarioService.obtenerMedicoActivo(req.medicoId());
		Consultorio consultorio = consultorioService.obtenerActivo(req.consultorioId());
		long excluir = c.getId() == null ? NINGUNA : c.getId();

		if (repository.medicoTieneCitaALaHora(medico.getId(), req.fecha(), req.hora(), EstadoCita.ACTIVOS, excluir)) {
			throw new ReglaNegocioException("El médico ya tiene una cita el " + req.fecha().format(FORMATO_FECHA)
					+ " a las " + req.hora());
		}
		validarTurnoUnico(c.getPaciente().getId(), req.fecha(), consultorio.getId(), excluir);

		c.setMedico(medico);
		c.setConsultorio(consultorio);
		c.setFecha(req.fecha());
		c.setHora(req.hora());
		c.setMotivo(limpiar(req.motivo()));
	}

	/** Un paciente no puede tener dos turnos activos el mismo día en el mismo consultorio. */
	private void validarTurnoUnico(Long pacienteId, LocalDate fecha, Long consultorioId, long excluir) {
		if (repository.pacienteTieneTurnoActivo(pacienteId, fecha, consultorioId, EstadoCita.ACTIVOS, excluir)) {
			throw new ReglaNegocioException("El paciente ya tiene una cita activa el "
					+ fecha.format(FORMATO_FECHA) + " en ese consultorio");
		}
	}

	private Paciente pacienteActivo(Long id) {
		Paciente p = pacienteService.obtener(id);
		if (!p.isActivo()) {
			throw new ReglaNegocioException("El paciente " + p.getNumeroHc() + " está inactivo");
		}
		return p;
	}

	private Cita obtener(Long id) {
		return repository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Cita", id));
	}

	private void auditar(AccionAuditoria accion, Cita c, String detalle) {
		auditoria.registrarSobrePaciente(accion, RECURSO, c.getId(), c.getPaciente().getId(), detalle);
	}

	private static String descripcion(Cita c) {
		return c.getFecha().format(FORMATO_FECHA) + (c.getHora() == null ? "" : " " + c.getHora()) + " en "
				+ c.getConsultorio().getNombre();
	}

	private static String limpiar(String texto) {
		return texto == null || texto.isBlank() ? null : texto.trim();
	}

}
