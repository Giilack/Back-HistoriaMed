package com.historiamed.backend.common.exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import com.historiamed.backend.auth.CredencialesInvalidasException;
import com.historiamed.backend.common.dto.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Traduce las excepciones a respuestas JSON con el formato {@link ErrorResponse}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(RecursoNoEncontradoException.class)
	ResponseEntity<ErrorResponse> noEncontrado(RecursoNoEncontradoException ex, HttpServletRequest req) {
		return respuesta(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", ex.getMessage(), req);
	}

	@ExceptionHandler(ReglaNegocioException.class)
	ResponseEntity<ErrorResponse> reglaNegocio(ReglaNegocioException ex, HttpServletRequest req) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
			.body(new ErrorResponse(Instant.now(), HttpStatus.CONFLICT.value(), ex.getCodigo(), ex.getMessage(),
					req.getRequestURI(), ex.getDetalles()));
	}

	@ExceptionHandler(AccesoProhibidoException.class)
	ResponseEntity<ErrorResponse> accesoProhibido(AccesoProhibidoException ex, HttpServletRequest req) {
		return respuesta(HttpStatus.FORBIDDEN, "ACCESO_DENEGADO", ex.getMessage(), req);
	}

	@ExceptionHandler(CredencialesInvalidasException.class)
	ResponseEntity<ErrorResponse> credenciales(CredencialesInvalidasException ex, HttpServletRequest req) {
		return respuesta(HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO", ex.getMessage(), req);
	}

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<ErrorResponse> accesoDenegado(AccessDeniedException ex, HttpServletRequest req) {
		return respuesta(HttpStatus.FORBIDDEN, "ACCESO_DENEGADO", "No tiene permisos para esta operación", req);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ErrorResponse> validacion(MethodArgumentNotValidException ex, HttpServletRequest req) {
		Map<String, String> errores = new LinkedHashMap<>();
		ex.getBindingResult().getFieldErrors().forEach(e -> errores.putIfAbsent(e.getField(), e.getDefaultMessage()));
		ErrorResponse body = new ErrorResponse(Instant.now(), 400, "VALIDACION", "Hay datos inválidos",
				req.getRequestURI(), errores);
		return ResponseEntity.badRequest().body(body);
	}

	@ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
			PropertyReferenceException.class })
	ResponseEntity<ErrorResponse> peticionInvalida(Exception ex, HttpServletRequest req) {
		return respuesta(HttpStatus.BAD_REQUEST, "PETICION_INVALIDA", "La petición tiene un formato inválido", req);
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ResponseEntity<ErrorResponse> archivoMuyGrande(MaxUploadSizeExceededException ex, HttpServletRequest req) {
		return respuesta(HttpStatus.CONTENT_TOO_LARGE, "ARCHIVO_MUY_GRANDE", "El archivo supera el máximo de 10 MB",
				req);
	}

	@ExceptionHandler(MissingServletRequestPartException.class)
	ResponseEntity<ErrorResponse> archivoFaltante(MissingServletRequestPartException ex, HttpServletRequest req) {
		return respuesta(HttpStatus.BAD_REQUEST, "PETICION_INVALIDA", "Adjunte el archivo", req);
	}

	/** Respaldo para duplicados que llegan a la BD (por ejemplo, dos altas simultáneas con el mismo DNI). */
	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<ErrorResponse> integridad(DataIntegrityViolationException ex, HttpServletRequest req) {
		log.warn("Violación de integridad en {}: {}", req.getRequestURI(), ex.getMostSpecificCause().getMessage());
		return respuesta(HttpStatus.CONFLICT, "CONFLICTO", "Los datos entran en conflicto con un registro existente",
				req);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ErrorResponse> general(Exception ex, HttpServletRequest req) {
		// Excepciones propias de Spring MVC (404 de ruta, 405, 415...) conservan su código HTTP
		if (ex instanceof org.springframework.web.ErrorResponse springError) {
			HttpStatus status = HttpStatus.valueOf(springError.getStatusCode().value());
			return respuesta(status, status.name(), status.getReasonPhrase(), req);
		}
		log.error("Error no controlado en {}", req.getRequestURI(), ex);
		return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO", "Ocurrió un error inesperado", req);
	}

	private static ResponseEntity<ErrorResponse> respuesta(HttpStatus status, String error, String mensaje,
			HttpServletRequest req) {
		return ResponseEntity.status(status).body(ErrorResponse.de(status.value(), error, mensaje, req.getRequestURI()));
	}

}
