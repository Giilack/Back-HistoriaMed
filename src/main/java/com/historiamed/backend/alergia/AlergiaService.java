package com.historiamed.backend.alergia;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.historiamed.backend.alergia.dto.AlergiaRequest;
import com.historiamed.backend.alergia.dto.AlergiaResponse;
import com.historiamed.backend.auditoria.AccionAuditoria;
import com.historiamed.backend.auditoria.AuditoriaService;
import com.historiamed.backend.common.exception.RecursoNoEncontradoException;
import com.historiamed.backend.common.exception.ReglaNegocioException;
import com.historiamed.backend.common.security.UsuarioActual;
import com.historiamed.backend.paciente.Paciente;
import com.historiamed.backend.paciente.PacienteService;
import com.historiamed.backend.usuario.UsuarioService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AlergiaService {

	private static final String RECURSO = "ALERGIA";

	private final AlergiaRepository repository;

	private final PacienteService pacienteService;

	private final UsuarioService usuarioService;

	private final AuditoriaService auditoria;

	@Transactional
	public List<AlergiaResponse> listar(Long pacienteId) {
		pacienteService.obtener(pacienteId);
		auditoria.registrarSobrePaciente(AccionAuditoria.VER, RECURSO, null, pacienteId, "Alergias");
		return repository.findByPacienteIdOrderByActivaDescCreadoEnDesc(pacienteId)
			.stream()
			.map(AlergiaResponse::de)
			.toList();
	}

	@Transactional
	public AlergiaResponse registrar(Long pacienteId, AlergiaRequest req) {
		Paciente p = pacienteService.obtener(pacienteId);
		String sustancia = req.sustancia().trim().replaceAll("\\s+", " ");
		if (repository.existsByPacienteIdAndSustanciaIgnoreCaseAndActivaTrue(pacienteId, sustancia)) {
			throw new ReglaNegocioException("El paciente ya tiene registrada una alergia a " + sustancia);
		}
		Alergia a = new Alergia();
		a.setPaciente(p);
		a.setTipo(req.tipo());
		a.setSustancia(sustancia);
		a.setReaccion(req.reaccion() == null || req.reaccion().isBlank() ? null : req.reaccion().trim());
		a.setGravedad(req.gravedad());
		a.setRegistradoPor(usuarioService.obtener(UsuarioActual.requerido().id()));
		repository.save(a);
		auditoria.registrarSobrePaciente(AccionAuditoria.CREAR, RECURSO, a.getId(), pacienteId,
				a.getTipo() + ": " + a.getSustancia() + " (" + a.getGravedad() + ")");
		return AlergiaResponse.de(a);
	}

	/** Marca como inactiva una alergia registrada por error o descartada. No se borra. */
	@Transactional
	public AlergiaResponse inactivar(Long id, String motivo) {
		Alergia a = repository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Alergia", id));
		if (!a.isActiva()) {
			throw new ReglaNegocioException("La alergia ya está inactiva");
		}
		a.setActiva(false);
		a.setMotivoInactivacion(motivo.trim());
		auditoria.registrarSobrePaciente(AccionAuditoria.DESACTIVAR, RECURSO, id, a.getPaciente().getId(),
				a.getSustancia() + ": " + a.getMotivoInactivacion());
		return AlergiaResponse.de(a);
	}

}
