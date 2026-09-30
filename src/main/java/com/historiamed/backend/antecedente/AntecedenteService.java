package com.historiamed.backend.antecedente;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.historiamed.backend.antecedente.dto.AntecedenteResponse;
import com.historiamed.backend.auditoria.AccionAuditoria;
import com.historiamed.backend.auditoria.AuditoriaService;
import com.historiamed.backend.catalogo.Cie10;
import com.historiamed.backend.catalogo.Medicamento;
import com.historiamed.backend.common.exception.RecursoNoEncontradoException;
import com.historiamed.backend.common.exception.ReglaNegocioException;
import com.historiamed.backend.common.security.UsuarioActual;
import com.historiamed.backend.documento.Documento;
import com.historiamed.backend.paciente.PacienteService;
import com.historiamed.backend.usuario.UsuarioService;

import lombok.RequiredArgsConstructor;

/**
 * Antecedentes del paciente. Por ahora se registran al validar los datos de un documento (plan.md, sección 5.5).
 */
@Service
@RequiredArgsConstructor
public class AntecedenteService {

	private static final String RECURSO = "ANTECEDENTE";

	private final AntecedenteRepository repository;

	private final PacienteService pacienteService;

	private final UsuarioService usuarioService;

	private final AuditoriaService auditoria;

	/**
	 * Datos de un antecedente nuevo.
	 *
	 * @param cie10 solo en los diagnósticos previos
	 * @param medicamento solo en la medicación habitual
	 * @param documento documento del que salió el dato (puede ser nulo)
	 */
	public record Nuevo(TipoAntecedente tipo, String descripcion, String detalle, LocalDate fecha, Cie10 cie10,
			Medicamento medicamento, Documento documento) {
	}

	@Transactional
	public List<AntecedenteResponse> listar(Long pacienteId) {
		pacienteService.obtener(pacienteId);
		auditoria.registrarSobrePaciente(AccionAuditoria.VER, RECURSO, null, pacienteId, "Antecedentes");
		return repository.findByPacienteIdOrderByActivoDescCreadoEnDesc(pacienteId)
			.stream()
			.map(AntecedenteResponse::de)
			.toList();
	}

	@Transactional
	public Antecedente registrar(Long pacienteId, Nuevo nuevo) {
		Antecedente a = new Antecedente();
		a.setPaciente(pacienteService.obtener(pacienteId));
		a.setTipo(nuevo.tipo());
		a.setDescripcion(nuevo.descripcion());
		a.setDetalle(nuevo.detalle());
		a.setFecha(nuevo.fecha());
		a.setCie10(nuevo.cie10());
		a.setMedicamento(nuevo.medicamento());
		a.setDocumento(nuevo.documento());
		a.setRegistradoPor(usuarioService.obtener(UsuarioActual.requerido().id()));
		repository.save(a);
		auditoria.registrarSobrePaciente(AccionAuditoria.CREAR, RECURSO, a.getId(), pacienteId,
				a.getTipo() + ": " + a.getDescripcion());
		return a;
	}

	/** Marca como inactivo un antecedente registrado por error. No se borra. */
	@Transactional
	public AntecedenteResponse inactivar(Long id, String motivo) {
		Antecedente a = repository.findById(id)
			.orElseThrow(() -> new RecursoNoEncontradoException("Antecedente", id));
		if (!a.isActivo()) {
			throw new ReglaNegocioException("El antecedente ya está inactivo");
		}
		a.setActivo(false);
		a.setMotivoInactivacion(motivo.trim());
		auditoria.registrarSobrePaciente(AccionAuditoria.DESACTIVAR, RECURSO, id, a.getPaciente().getId(),
				a.getDescripcion() + ": " + a.getMotivoInactivacion());
		return AntecedenteResponse.de(a);
	}

}
