package com.historiamed.backend.consultorio;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.historiamed.backend.auditoria.AccionAuditoria;
import com.historiamed.backend.auditoria.AuditoriaService;
import com.historiamed.backend.common.exception.RecursoNoEncontradoException;
import com.historiamed.backend.common.exception.ReglaNegocioException;
import com.historiamed.backend.consultorio.dto.ConsultorioRequest;

import lombok.RequiredArgsConstructor;

/**
 * Catálogo de consultorios (lo gestiona el ADMIN). Se desactivan, no se borran: las citas pasadas los referencian.
 */
@Service
@RequiredArgsConstructor
public class ConsultorioService {

	private static final String RECURSO = "CONSULTORIO";

	private final ConsultorioRepository repository;

	private final AuditoriaService auditoria;

	@Transactional(readOnly = true)
	public List<Consultorio> listar(boolean soloActivos) {
		return soloActivos ? repository.findByActivoTrueOrderByNombreAsc() : repository.findAllByOrderByNombreAsc();
	}

	@Transactional(readOnly = true)
	public Consultorio obtener(Long id) {
		return repository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Consultorio", id));
	}

	/** Para asignar a una cita: debe existir y estar activo. */
	@Transactional(readOnly = true)
	public Consultorio obtenerActivo(Long id) {
		Consultorio c = obtener(id);
		if (!c.isActivo()) {
			throw new ReglaNegocioException("El consultorio " + c.getNombre() + " está desactivado");
		}
		return c;
	}

	@Transactional
	public Consultorio crear(ConsultorioRequest req) {
		String nombre = req.nombre().trim();
		if (repository.existsByNombreIgnoreCase(nombre)) {
			throw new ReglaNegocioException("Ya existe un consultorio llamado " + nombre);
		}
		Consultorio c = new Consultorio();
		c.setNombre(nombre);
		c.setEspecialidad(req.especialidad().trim());
		repository.save(c);
		auditoria.registrar(AccionAuditoria.CREAR, RECURSO, c.getId(), c.getNombre());
		return c;
	}

	@Transactional
	public Consultorio actualizar(Long id, ConsultorioRequest req) {
		Consultorio c = obtener(id);
		String nombre = req.nombre().trim();
		if (repository.existsByNombreIgnoreCaseAndIdNot(nombre, id)) {
			throw new ReglaNegocioException("Ya existe un consultorio llamado " + nombre);
		}
		c.setNombre(nombre);
		c.setEspecialidad(req.especialidad().trim());
		auditoria.registrar(AccionAuditoria.EDITAR, RECURSO, id, c.getNombre());
		return c;
	}

	@Transactional
	public Consultorio cambiarEstado(Long id, boolean activo) {
		Consultorio c = obtener(id);
		c.setActivo(activo);
		auditoria.registrar(activo ? AccionAuditoria.ACTIVAR : AccionAuditoria.DESACTIVAR, RECURSO, id,
				c.getNombre());
		return c;
	}

}
