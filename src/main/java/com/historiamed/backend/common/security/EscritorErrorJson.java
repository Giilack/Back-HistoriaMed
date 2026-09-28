package com.historiamed.backend.common.security;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.historiamed.backend.common.dto.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * Escribe errores con el formato {@link ErrorResponse} desde los filtros de seguridad, que se ejecutan antes de
 * llegar a los controladores (y por eso no pasan por el GlobalExceptionHandler).
 */
@Component
@RequiredArgsConstructor
public class EscritorErrorJson {

	private final JsonMapper jsonMapper;

	public void escribir(HttpServletRequest request, HttpServletResponse response, int status, String error,
			String mensaje) throws IOException {
		response.setStatus(status);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		jsonMapper.writeValue(response.getWriter(), ErrorResponse.de(status, error, mensaje, request.getRequestURI()));
	}

}
