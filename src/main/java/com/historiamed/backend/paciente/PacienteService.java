package com.historiamed.backend.paciente;

import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.historiamed.backend.auditoria.AccionAuditoria;
import com.historiamed.backend.auditoria.AuditoriaService;
import com.historiamed.backend.common.exception.RecursoNoEncontradoException;
import com.historiamed.backend.common.exception.ReglaNegocioException;
import com.historiamed.backend.common.security.UsuarioActual;
import com.historiamed.backend.common.util.Edad;
import com.historiamed.backend.paciente.dto.FinanciamientoRequest;
import com.historiamed.backend.paciente.dto.PacienteRequest;
import com.historiamed.backend.paciente.dto.VerificacionSeguroRequest;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import lombok.RequiredArgsConstructor;

/**
 * Registro y búsqueda de pacientes. Reglas en plan.md, secciones 5.1 (pacientes) y 5.2 (financiamiento).
 */
@Service
@RequiredArgsConstructor
public class PacienteService {

	private static final String RECURSO = "PACIENTE";

	private static final int EDAD_MAXIMA = 120;

	private static final Pattern PATRON_HC = Pattern.compile("^HC-?(\\d{1,9})$", Pattern.CASE_INSENSITIVE);

	private static final Pattern PATRON_DOCUMENTO = Pattern.compile("^(?=.*\\d)[A-Za-z0-9]{4,20}$");

	private static final Pattern PATRON_DNI = Pattern.compile("^\\d{8}$");

	private final PacienteRepository repository;

	private final AuditoriaService auditoria;

	/**
	 * Busca pacientes activos por número de HC, número de documento o nombres y apellidos (sin importar tildes ni
	 * mayúsculas; cada palabra debe coincidir con algún nombre o apellido).
	 */
	@Transactional(readOnly = true)
	public Page<Paciente> buscar(String texto, Pageable pageable) {
		Specification<Paciente> spec = (root, query, cb) -> cb.isTrue(root.get("activo"));
		String q = limpiar(texto);
		if (q == null) {
			return repository.findAll(spec, pageable);
		}

		var hc = PATRON_HC.matcher(q);
		if (hc.matches()) {
			String numeroHc = formatearHc(Long.parseLong(hc.group(1)));
			return repository.findAll(spec.and((root, query, cb) -> cb.equal(root.get("numeroHc"), numeroHc)),
					pageable);
		}
		if (PATRON_DOCUMENTO.matcher(q).matches()) {
			String documento = q.toUpperCase(Locale.ROOT);
			return repository.findAll(
					spec.and((root, query, cb) -> cb.equal(root.get("numeroDocumento"), documento)), pageable);
		}
		for (String palabra : q.split(" ")) {
			String patron = "%" + sinTildes(palabra.toLowerCase(Locale.ROOT)) + "%";
			spec = spec.and((root, query, cb) -> cb.or(cb.like(normalizar(cb, root.get("nombres")), patron),
					cb.like(normalizar(cb, root.get("apellidoPaterno")), patron),
					cb.like(normalizar(cb, root.get("apellidoMaterno")), patron)));
		}
		return repository.findAll(spec, pageable);
	}

	/** Obtiene la ficha y registra en la auditoría quién la consultó. */
	@Transactional
	public Paciente ver(Long id) {
		Paciente p = obtener(id);
		auditoria.registrarSobrePaciente(AccionAuditoria.VER, RECURSO, id, id, null);
		return p;
	}

	@Transactional(readOnly = true)
	public Paciente obtener(Long id) {
		return repository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Paciente", id));
	}

	@Transactional
	public Paciente registrar(PacienteRequest req) {
		Paciente p = new Paciente();
		aplicarDatos(p, req);
		aplicarFinanciamiento(p, req.financiamiento());
		p.setNumeroHc(formatearHc(repository.siguienteNumeroHc()));
		p.setCreadoPor(UsuarioActual.requerido().id());
		repository.save(p);
		auditoria.registrarSobrePaciente(AccionAuditoria.CREAR, RECURSO, p.getId(), p.getId(), p.getNumeroHc());
		return p;
	}

	@Transactional
	public Paciente actualizar(Long id, PacienteRequest req) {
		Paciente p = obtener(id);
		aplicarDatos(p, req);
		auditoria.registrarSobrePaciente(AccionAuditoria.EDITAR, RECURSO, id, id, "Datos de filiación");
		return p;
	}

	@Transactional
	public Paciente cambiarFinanciamiento(Long id, FinanciamientoRequest req) {
		Paciente p = obtener(id);
		aplicarFinanciamiento(p, req);
		auditoria.registrarSobrePaciente(AccionAuditoria.EDITAR, RECURSO, id, id,
				"Financiamiento: " + p.getTipoFinanciamiento());
		return p;
	}

	/**
	 * Registra el resultado de la verificación manual del seguro en su padrón (ACTIVO o INACTIVO).
	 */
	@Transactional
	public Paciente verificarSeguro(Long id, VerificacionSeguroRequest req) {
		Paciente p = obtener(id);
		if (!p.getTipoFinanciamiento().tieneSeguro()) {
			throw new ReglaNegocioException("El paciente no tiene un seguro registrado (es PARTICULAR)");
		}
		if (req.estado() == EstadoSeguro.NO_VERIFICADO) {
			throw new ReglaNegocioException("Indique el resultado de la verificación: ACTIVO o INACTIVO");
		}
		p.setSeguroEstado(req.estado());
		p.setSeguroVerificadoEn(Instant.now());
		auditoria.registrarSobrePaciente(AccionAuditoria.EDITAR, RECURSO, id, id,
				"Verificación " + p.getTipoFinanciamiento() + ": " + req.estado());
		return p;
	}

	private void aplicarDatos(Paciente p, PacienteRequest req) {
		String numeroDocumento = validarDocumento(p, req.tipoDocumento(), req.numeroDocumento());
		validarFechaNacimiento(req.fechaNacimiento());

		p.setTipoDocumento(req.tipoDocumento());
		p.setNumeroDocumento(numeroDocumento);
		p.setNombres(limpiar(req.nombres()));
		p.setApellidoPaterno(limpiar(req.apellidoPaterno()));
		p.setApellidoMaterno(limpiar(req.apellidoMaterno()));
		p.setFechaNacimiento(req.fechaNacimiento());
		p.setSexo(req.sexo());
		p.setTelefono(limpiar(req.telefono()));
		String email = limpiar(req.email());
		p.setEmail(email == null ? null : email.toLowerCase(Locale.ROOT));
		p.setDireccion(limpiar(req.direccion()));
		p.setContactoEmergenciaNombre(limpiar(req.contactoEmergenciaNombre()));
		p.setContactoEmergenciaTelefono(limpiar(req.contactoEmergenciaTelefono()));
		p.setContactoEmergenciaParentesco(limpiar(req.contactoEmergenciaParentesco()));
	}

	/**
	 * Valida el documento y devuelve el número normalizado (null si el paciente no tiene documento).
	 */
	private String validarDocumento(Paciente p, TipoDocumento tipo, String numero) {
		if (tipo == TipoDocumento.SIN_DOCUMENTO) {
			return null;
		}
		String n = limpiar(numero);
		if (n == null) {
			throw new ReglaNegocioException("Indique el número de " + tipo);
		}
		n = n.toUpperCase(Locale.ROOT);
		if (tipo == TipoDocumento.DNI && !PATRON_DNI.matcher(n).matches()) {
			throw new ReglaNegocioException("El DNI debe tener 8 dígitos");
		}
		String numeroFinal = n;
		repository.findByTipoDocumentoAndNumeroDocumento(tipo, n)
			.filter(otro -> !Objects.equals(otro.getId(), p.getId()))
			.ifPresent(otro -> {
				throw new ReglaNegocioException("Ya existe un paciente con " + tipo + " " + numeroFinal + ": "
						+ otro.getNumeroHc() + " (" + otro.nombreCompleto() + ")");
			});
		return n;
	}

	private static void validarFechaNacimiento(LocalDate fecha) {
		if (fecha.isAfter(Edad.hoy())) {
			throw new ReglaNegocioException("La fecha de nacimiento no puede ser futura");
		}
		if (Edad.anios(fecha) > EDAD_MAXIMA) {
			throw new ReglaNegocioException("La fecha de nacimiento indica más de " + EDAD_MAXIMA + " años");
		}
	}

	private static void aplicarFinanciamiento(Paciente p, FinanciamientoRequest req) {
		FinanciamientoRequest f = req != null ? req
				: new FinanciamientoRequest(TipoFinanciamiento.PARTICULAR, null, null, false);

		if (!f.tipo().tieneSeguro()) {
			p.setTipoFinanciamiento(TipoFinanciamiento.PARTICULAR);
			p.setSeguroNumeroAfiliacion(null);
			p.setSeguroPlan(null);
			p.setSeguroEstado(null);
			p.setSeguroVerificadoEn(null);
			p.setOrientadoAfiliacionSis(Boolean.TRUE.equals(f.orientadoAfiliacionSis()));
			return;
		}

		String numero = limpiar(f.numeroAfiliacion());
		String plan = limpiar(f.plan());
		if (f.tipo() == TipoFinanciamiento.PRIVADO && plan == null) {
			throw new ReglaNegocioException("Indique la aseguradora o EPS del seguro privado");
		}
		// Si cambia el seguro, la verificación anterior deja de valer
		boolean cambio = f.tipo() != p.getTipoFinanciamiento() || !Objects.equals(numero, p.getSeguroNumeroAfiliacion());
		p.setTipoFinanciamiento(f.tipo());
		p.setSeguroNumeroAfiliacion(numero);
		p.setSeguroPlan(plan);
		p.setOrientadoAfiliacionSis(false);
		if (cambio || p.getSeguroEstado() == null) {
			p.setSeguroEstado(EstadoSeguro.NO_VERIFICADO);
			p.setSeguroVerificadoEn(null);
		}
	}

	static String formatearHc(long numero) {
		return "HC-%06d".formatted(numero);
	}

	/** Quita espacios sobrantes; devuelve null si queda vacío. */
	static String limpiar(String texto) {
		if (texto == null) {
			return null;
		}
		String t = texto.trim().replaceAll("\\s+", " ");
		return t.isEmpty() ? null : t;
	}

	private static String sinTildes(String texto) {
		return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
	}

	/** lower(translate(campo, 'ÁÉÍÓÚÜáéíóúü', 'AEIOUUaeiouu')): compara sin tildes ni mayúsculas en SQL. */
	private static Expression<String> normalizar(CriteriaBuilder cb, Expression<String> campo) {
		return cb.lower(cb.function("translate", String.class, campo, cb.literal("ÁÉÍÓÚÜáéíóúü"),
				cb.literal("AEIOUUaeiouu")));
	}

}
