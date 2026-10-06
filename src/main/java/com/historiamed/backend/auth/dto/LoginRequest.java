package com.historiamed.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param captchaToken token de Cloudflare Turnstile; solo se exige tras varios intentos fallidos
 */
public record LoginRequest(@NotBlank @Size(max = 30) String username, @NotBlank @Size(max = 72) String password,
		@Size(max = 2048) String captchaToken) {

	public LoginRequest(String username, String password) {
		this(username, password, null);
	}

}
