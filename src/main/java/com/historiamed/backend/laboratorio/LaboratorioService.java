package com.historiamed.backend.laboratorio;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.historiamed.backend.auditoria.AccionAuditoria;
import com.historiamed.backend.auditoria.AuditoriaService;
import com.historiamed.backend.common.exception.RecursoNoEncontradoException;
import com.historiamed.backend.common.exception.ReglaNegocioException;
import com.historiamed.backend.common.security.UsuarioActual;
import com.historiamed.backend.documento.Documento;
import com.historiamed.backend.laboratorio.dto.ResultadoLaboratorioResponse;
import com.historiamed.backend.paciente.PacienteService;
import com.historiamed.backend.usuario.UsuarioService;

import lombok.RequiredArgsConstructor;

/**
 * Resultados de laboratorio del paciente. Por ahora se registran al validar los datos de un documento (plan.md,
 * sección 5.5).
 */
@Service
@RequiredArgsConstructor
public class LaboratorioService {

	private static final String RECURSO = "RESULTADO_LABORATORIO";

	private final ResultadoLaboratorioRepository repository;

	private final PacienteService pacienteService;

	private final UsuarioService usuarioService;

	private final AuditoriaService auditoria;

	/**
	 * Datos de un resultado nuevo.
	 *
	 * @param documento documento del que salió el dato (puede ser nulo)
	 */
	public record Nuevo(String examen, String valor, String unidad, String rangoReferencia, LocalDate fecha,
			Documento documento) {
	}

	@Transactional
	public List<ResultadoLaboratorioResponse> listar(Long pacienteId) {
		pacienteService.obtener(pacienteId);
		auditoria.registrarSobrePaciente(AccionAuditoria.VER, RECURSO, null, pacienteId, "Resultados de laboratorio");
		return repository.delPaciente(pacienteId).stream().map(ResultadoLaboratorioResponse::de).toList();
	}

	@Transactional
	public ResultadoLaboratorio registrar(Long pacienteId, Nuevo nuevo) {
		ResultadoLaboratorio r = new ResultadoLaboratorio();
		r.setPaciente(pacienteService.obtener(pacienteId));
		r.setExamen(nuevo.examen());
		r.setValor(nuevo.valor());
		r.setUnidad(nuevo.unidad());
		r.setRangoReferencia(nuevo.rangoReferencia());
		r.setFecha(nuevo.fecha());
		r.setDocumento(nuevo.documento());
		r.setRegistradoPor(usuarioService.obtener(UsuarioActual.requerido().id()));
		repository.save(r);
		auditoria.registrarSobrePaciente(AccionAuditoria.CREAR, RECURSO, r.getId(), pacienteId,
				r.getExamen() + ": " + r.getValor() + (r.getUnidad() == null ? "" : " " + r.getUnidad()));
		return r;
	}

	/** Marca como inactivo un resultado registrado por error. No se borra. */
	@Transactional
	public ResultadoLaboratorioResponse inactivar(Long id, String motivo) {
		ResultadoLaboratorio r = repository.findById(id)
			.orElseThrow(() -> new RecursoNoEncontradoException("Resultado de laboratorio", id));
		if (!r.isActivo()) {
			throw new ReglaNegocioException("El resultado ya está inactivo");
		}
		r.setActivo(false);
		r.setMotivoInactivacion(motivo.trim());
		auditoria.registrarSobrePaciente(AccionAuditoria.DESACTIVAR, RECURSO, id, r.getPaciente().getId(),
				r.getExamen() + ": " + r.getMotivoInactivacion());
		return ResultadoLaboratorioResponse.de(r);
	}

}
