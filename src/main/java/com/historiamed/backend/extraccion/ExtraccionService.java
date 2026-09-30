package com.historiamed.backend.extraccion;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.historiamed.backend.alergia.AlergiaService;
import com.historiamed.backend.alergia.dto.AlergiaRequest;
import com.historiamed.backend.antecedente.AntecedenteService;
import com.historiamed.backend.antecedente.TipoAntecedente;
import com.historiamed.backend.auditoria.AccionAuditoria;
import com.historiamed.backend.auditoria.AuditoriaService;
import com.historiamed.backend.catalogo.CatalogoService;
import com.historiamed.backend.catalogo.Cie10;
import com.historiamed.backend.catalogo.Medicamento;
import com.historiamed.backend.common.exception.AccesoProhibidoException;
import com.historiamed.backend.common.exception.RecursoNoEncontradoException;
import com.historiamed.backend.common.exception.ReglaNegocioException;
import com.historiamed.backend.common.security.UsuarioActual;
import com.historiamed.backend.documento.Documento;
import com.historiamed.backend.documento.DocumentoService;
import com.historiamed.backend.extraccion.ExtraccionItem.Categoria;
import com.historiamed.backend.extraccion.dto.ExtraccionResponse;
import com.historiamed.backend.extraccion.dto.ExtraccionResumenResponse;
import com.historiamed.backend.extraccion.dto.ItemRequest;
import com.historiamed.backend.laboratorio.LaboratorioService;
import com.historiamed.backend.usuario.Rol;
import com.historiamed.backend.usuario.UsuarioService;

import lombok.RequiredArgsConstructor;

/**
 * Revisión de documentos (plan.md, sección 5.5). TRIAJE o MEDICO llenan los datos del documento por categoría; el
 * MEDICO decide sobre cada uno. Solo los datos aceptados pasan a la historia clínica (principio P2), y lo hacen a
 * través del servicio de cada módulo: alergias, antecedentes y laboratorio.
 */
@Service
@RequiredArgsConstructor
public class ExtraccionService {

	private static final String RECURSO = "EXTRACCION";

	/** Los antecedentes que se pueden elegir a mano; los otros tipos salen de las categorías DIAGNOSTICO y MEDICAMENTO. */
	private static final Set<TipoAntecedente> TIPOS_ANTECEDENTE = Set.of(TipoAntecedente.PERSONAL,
			TipoAntecedente.FAMILIAR, TipoAntecedente.QUIRURGICO, TipoAntecedente.OTRO);

	// Límites de las tablas de destino
	private static final int MAX_SUSTANCIA = 100;

	private static final int MAX_REACCION = 200;

	private static final int MAX_EXAMEN = 150;

	private final ExtraccionRepository repository;

	private final DocumentoService documentoService;

	private final CatalogoService catalogoService;

	private final AlergiaService alergiaService;

	private final AntecedenteService antecedenteService;

	private final LaboratorioService laboratorioService;

	private final UsuarioService usuarioService;

	private final AuditoriaService auditoria;

	/**
	 * Abre la revisión del documento para llenarla a mano. Si el documento ya tiene una vigente (pendiente o
	 * validada), devuelve esa en lugar de crear otra.
	 */
	@Transactional
	public ExtraccionResponse iniciar(Long documentoId) {
		Documento d = documentoService.obtener(documentoId);
		Long pacienteId = d.getPaciente().getId();
		var vigente = repository.findByDocumentoIdAndEstadoNot(documentoId, Extraccion.Estado.RECHAZADA);
		if (vigente.isPresent()) {
			auditoria.registrarSobrePaciente(AccionAuditoria.VER, RECURSO, vigente.get().getId(), pacienteId, null);
			return ExtraccionResponse.de(vigente.get());
		}
		if (d.getEstado() == Documento.Estado.ANULADO) {
			throw new ReglaNegocioException("El documento fue anulado: " + d.getMotivoAnulacion());
		}
		Extraccion e = new Extraccion();
		e.setDocumento(d);
		e.setPaciente(d.getPaciente());
		e.setOrigen(Extraccion.Origen.MANUAL);
		e.setCreadoPor(usuarioService.obtener(UsuarioActual.requerido().id()));
		repository.save(e);
		auditoria.registrarSobrePaciente(AccionAuditoria.CREAR, RECURSO, e.getId(), pacienteId,
				"Revisión de " + d.getNombreOriginal());
		return ExtraccionResponse.de(e);
	}

	/** La revisión vigente del documento. */
	@Transactional
	public ExtraccionResponse delDocumento(Long documentoId) {
		Extraccion e = repository.findByDocumentoIdAndEstadoNot(documentoId, Extraccion.Estado.RECHAZADA)
			.orElseThrow(() -> new RecursoNoEncontradoException("Revisión del documento", documentoId));
		auditoria.registrarSobrePaciente(AccionAuditoria.VER, RECURSO, e.getId(), e.getPaciente().getId(), null);
		return ExtraccionResponse.de(e);
	}

	/** Estado de las revisiones de los documentos del paciente (sin los datos). */
	@Transactional(readOnly = true)
	public List<ExtraccionResumenResponse> delPaciente(Long pacienteId) {
		return repository.findByPacienteIdOrderByCreadoEnDesc(pacienteId)
			.stream()
			.map(ExtraccionResumenResponse::de)
			.toList();
	}

	@Transactional
	public ExtraccionResponse agregarItem(Long id, ItemRequest req) {
		Extraccion e = pendiente(id);
		ExtraccionItem item = new ExtraccionItem();
		item.setExtraccion(e);
		item.setCreadoPor(usuarioService.obtener(UsuarioActual.requerido().id()));
		aplicarDatos(item, req);
		e.getItems().add(item);
		// Se vuelca ahora para que la respuesta ya lleve el identificador del dato
		repository.saveAndFlush(e);
		auditoria.registrarSobrePaciente(AccionAuditoria.EDITAR, RECURSO, id, e.getPaciente().getId(),
				"Dato agregado: " + resumen(item));
		return ExtraccionResponse.de(e);
	}

	@Transactional
	public ExtraccionResponse editarItem(Long id, Long itemId, ItemRequest req) {
		Extraccion e = pendiente(id);
		ExtraccionItem item = item(e, itemId);
		UsuarioActual yo = UsuarioActual.requerido();
		aplicarDatos(item, req);
		// Un médico que cambia lo que propuso otra persona (o la IA) lo está corrigiendo
		boolean ajeno = e.getOrigen() == Extraccion.Origen.IA || !item.getCreadoPor().getId().equals(yo.id());
		if (ajeno && esMedico(yo)) {
			item.setCorregido(true);
		}
		auditoria.registrarSobrePaciente(AccionAuditoria.EDITAR, RECURSO, id, e.getPaciente().getId(),
				"Dato modificado: " + resumen(item));
		return ExtraccionResponse.de(e);
	}

	/**
	 * Quita un dato del borrador (por ejemplo, uno escrito por error). Solo quien lo agregó o un médico. No es un
	 * dato de la historia clínica: todavía no fue validado.
	 */
	@Transactional
	public ExtraccionResponse eliminarItem(Long id, Long itemId) {
		Extraccion e = pendiente(id);
		ExtraccionItem item = item(e, itemId);
		UsuarioActual yo = UsuarioActual.requerido();
		if (!item.getCreadoPor().getId().equals(yo.id()) && !esMedico(yo)) {
			throw new AccesoProhibidoException("Solo quien agregó el dato o un médico pueden quitarlo");
		}
		e.getItems().remove(item);
		auditoria.registrarSobrePaciente(AccionAuditoria.EDITAR, RECURSO, id, e.getPaciente().getId(),
				"Dato quitado del borrador: " + resumen(item));
		return ExtraccionResponse.de(e);
	}

	/**
	 * El médico cierra la revisión: los datos de {@code aceptados} pasan a la historia clínica y el resto queda
	 * descartado. Todo en una transacción: o se aplican todos o ninguno.
	 */
	@Transactional
	public ExtraccionResponse validar(Long id, Set<Long> aceptados) {
		Extraccion e = pendiente(id);
		if (e.getItems().isEmpty()) {
			throw new ReglaNegocioException(
					"La revisión no tiene datos. Agregue al menos uno o rechace la revisión indicando el motivo");
		}
		Set<Long> existentes = e.getItems().stream().map(ExtraccionItem::getId).collect(Collectors.toSet());
		if (!existentes.containsAll(aceptados)) {
			throw new ReglaNegocioException("Hay datos aceptados que no pertenecen a esta revisión");
		}
		int aplicados = 0;
		for (ExtraccionItem item : e.getItems()) {
			if (aceptados.contains(item.getId())) {
				pasarALaHistoria(e, item);
				item.setEstado(item.isCorregido() ? ExtraccionItem.Estado.CORREGIDO : ExtraccionItem.Estado.ACEPTADO);
				aplicados++;
			}
			else {
				item.setEstado(ExtraccionItem.Estado.DESCARTADO);
			}
		}
		cerrar(e, Extraccion.Estado.VALIDADA);
		auditoria.registrarSobrePaciente(AccionAuditoria.VALIDAR, RECURSO, id, e.getPaciente().getId(),
				aplicados + " datos aceptados, " + (e.getItems().size() - aplicados) + " descartados");
		return ExtraccionResponse.de(e);
	}

	/** El médico descarta la revisión completa (documento ilegible, de otro paciente...). Nada pasa a la historia. */
	@Transactional
	public ExtraccionResponse rechazar(Long id, String motivo) {
		Extraccion e = pendiente(id);
		e.getItems().forEach(item -> item.setEstado(ExtraccionItem.Estado.DESCARTADO));
		e.setMotivoRechazo(motivo.trim());
		cerrar(e, Extraccion.Estado.RECHAZADA);
		auditoria.registrarSobrePaciente(AccionAuditoria.VALIDAR, RECURSO, id, e.getPaciente().getId(),
				"Rechazada: " + e.getMotivoRechazo());
		return ExtraccionResponse.de(e);
	}

	// ------------------------------------------------------------------------------------------------------------

	private Extraccion pendiente(Long id) {
		Extraccion e = repository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Revisión", id));
		if (!e.estaPendiente()) {
			throw new ReglaNegocioException("La revisión ya fue cerrada y no se puede modificar");
		}
		return e;
	}

	private static ExtraccionItem item(Extraccion e, Long itemId) {
		return e.getItems()
			.stream()
			.filter(i -> i.getId().equals(itemId))
			.findFirst()
			.orElseThrow(() -> new RecursoNoEncontradoException("Dato de la revisión", itemId));
	}

	private void cerrar(Extraccion e, Extraccion.Estado estado) {
		e.setEstado(estado);
		e.setRevisadoPor(usuarioService.obtener(UsuarioActual.requerido().id()));
		e.setRevisadoEn(Instant.now());
	}

	private static boolean esMedico(UsuarioActual usuario) {
		return Rol.MEDICO.name().equals(usuario.rol());
	}

	/**
	 * Copia los datos al ítem según su categoría: exige los campos que esa categoría necesita y deja en blanco los
	 * que no le corresponden.
	 */
	private void aplicarDatos(ExtraccionItem item, ItemRequest req) {
		Categoria categoria = req.categoria();
		String descripcion = limpiar(req.descripcion());
		String detalle = limpiar(req.detalle());
		Cie10 cie10 = null;
		Medicamento medicamento = null;

		switch (categoria) {
			case ALERGIA -> {
				exigir(descripcion, "descripcion", "Indique la sustancia");
				exigir(req.tipoAlergia(), "tipoAlergia", "Indique el tipo de alergia");
				exigir(req.gravedad(), "gravedad", "Indique la gravedad");
				maximo(descripcion, MAX_SUSTANCIA, "descripcion");
				maximo(detalle, MAX_REACCION, "detalle");
			}
			case DIAGNOSTICO -> {
				exigir(limpiar(req.cieCodigo()), "cieCodigo", "Elija el diagnóstico del catálogo CIE-10");
				cie10 = catalogoService.obtenerCie10(req.cieCodigo());
				descripcion = cie10.getDescripcion();
			}
			case MEDICAMENTO -> {
				exigir(req.medicamentoId(), "medicamentoId", "Elija el medicamento del catálogo");
				medicamento = catalogoService.obtenerMedicamento(req.medicamentoId());
				descripcion = medicamento.descripcion();
			}
			case LABORATORIO -> {
				exigir(descripcion, "descripcion", "Indique el examen");
				exigir(limpiar(req.valor()), "valor", "Indique el resultado");
				maximo(descripcion, MAX_EXAMEN, "descripcion");
			}
			case ANTECEDENTE -> {
				exigir(descripcion, "descripcion", "Describa el antecedente");
				exigir(req.tipoAntecedente(), "tipoAntecedente", "Indique el tipo de antecedente");
				if (!TIPOS_ANTECEDENTE.contains(req.tipoAntecedente())) {
					throw invalido("tipoAntecedente", "Elija personal, familiar, quirúrgico u otro");
				}
			}
			case OTRO -> exigir(descripcion, "descripcion", "Escriba la información");
		}

		item.setCategoria(categoria);
		item.setDescripcion(descripcion);
		item.setDetalle(detalle);
		item.setFecha(req.fecha());
		item.setFragmentoOrigen(limpiar(req.fragmentoOrigen()));
		item.setPagina(req.pagina());
		item.setTipoAlergia(categoria == Categoria.ALERGIA ? req.tipoAlergia() : null);
		item.setGravedad(categoria == Categoria.ALERGIA ? req.gravedad() : null);
		item.setCie10(cie10);
		item.setMedicamento(medicamento);
		boolean laboratorio = categoria == Categoria.LABORATORIO;
		item.setValor(laboratorio ? limpiar(req.valor()) : null);
		item.setUnidad(laboratorio ? limpiar(req.unidad()) : null);
		item.setRangoReferencia(laboratorio ? limpiar(req.rangoReferencia()) : null);
		item.setTipoAntecedente(categoria == Categoria.ANTECEDENTE ? req.tipoAntecedente() : null);
	}

	/** Lleva un dato aceptado a la tabla que le corresponde, a través del servicio de ese módulo. */
	private void pasarALaHistoria(Extraccion e, ExtraccionItem item) {
		Long pacienteId = e.getPaciente().getId();
		Documento documento = e.getDocumento();
		switch (item.getCategoria()) {
			case ALERGIA -> {
				// Si el paciente ya tiene esa alergia activa, el dato se acepta sin duplicarla
				boolean yaRegistrada = alergiaService.activas(pacienteId)
					.stream()
					.anyMatch(a -> a.getSustancia().equalsIgnoreCase(item.getDescripcion()));
				if (!yaRegistrada) {
					alergiaService.registrar(pacienteId, new AlergiaRequest(item.getTipoAlergia(),
							item.getDescripcion(), item.getDetalle(), item.getGravedad()));
				}
			}
			case DIAGNOSTICO -> antecedenteService.registrar(pacienteId,
					new AntecedenteService.Nuevo(TipoAntecedente.DIAGNOSTICO_PREVIO, item.getDescripcion(),
							item.getDetalle(), item.getFecha(), item.getCie10(), null, documento));
			case MEDICAMENTO -> antecedenteService.registrar(pacienteId,
					new AntecedenteService.Nuevo(TipoAntecedente.MEDICACION_HABITUAL, item.getDescripcion(),
							item.getDetalle(), item.getFecha(), null, item.getMedicamento(), documento));
			case ANTECEDENTE -> antecedenteService.registrar(pacienteId,
					new AntecedenteService.Nuevo(item.getTipoAntecedente(), item.getDescripcion(), item.getDetalle(),
							item.getFecha(), null, null, documento));
			case LABORATORIO -> laboratorioService.registrar(pacienteId,
					new LaboratorioService.Nuevo(item.getDescripcion(), item.getValor(), item.getUnidad(),
							item.getRangoReferencia(),
							item.getFecha() != null ? item.getFecha() : documento.getFechaDocumento(), documento));
			case OTRO -> {
				// No pasa a la historia: queda solo en la revisión
			}
		}
	}

	private static String resumen(ExtraccionItem item) {
		return item.getCategoria() + " · " + item.getDescripcion();
	}

	/** Quita espacios sobrantes; vacío se convierte en {@code null}. */
	private static String limpiar(String texto) {
		if (texto == null) {
			return null;
		}
		String limpio = texto.trim().replaceAll("\\s+", " ");
		return limpio.isEmpty() ? null : limpio;
	}

	private static void exigir(Object valor, String campo, String mensaje) {
		if (valor == null) {
			throw invalido(campo, mensaje);
		}
	}

	private static void maximo(String valor, int maximo, String campo) {
		if (valor != null && valor.length() > maximo) {
			throw invalido(campo, "Máximo " + maximo + " caracteres");
		}
	}

	private static ReglaNegocioException invalido(String campo, String mensaje) {
		return new ReglaNegocioException("DATO_INVALIDO", mensaje, Map.of(campo, mensaje));
	}

}
