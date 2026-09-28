package com.historiamed.backend.auth;

import java.time.Duration;
import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.historiamed.backend.common.security.UsuarioActual;
import com.historiamed.backend.config.HistoriaMedProperties;
import com.historiamed.backend.config.JwtConfig;
import com.historiamed.backend.usuario.Usuario;

import lombok.RequiredArgsConstructor;

/**
 * Emite los access tokens (JWT firmados con HS256, de corta duración).
 */
@Service
@RequiredArgsConstructor
public class TokenService {

	private final JwtEncoder encoder;

	private final HistoriaMedProperties properties;

	public String generarAccessToken(Usuario usuario) {
		Instant ahora = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(JwtConfig.ISSUER)
			.subject(usuario.getUsername())
			.issuedAt(ahora)
			.expiresAt(ahora.plus(duracion()))
			.claim(UsuarioActual.CLAIM_ID, usuario.getId())
			.claim(UsuarioActual.CLAIM_ROL, usuario.getRol().name())
			.claim(UsuarioActual.CLAIM_CAMBIO_PENDIENTE, usuario.isDebeCambiarPassword())
			.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	public Duration duracion() {
		return Duration.ofMinutes(properties.jwt().accessTokenMinutos());
	}

}
