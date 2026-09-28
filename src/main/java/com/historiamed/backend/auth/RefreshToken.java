package com.historiamed.backend.auth;

import java.time.Instant;

import com.historiamed.backend.usuario.Usuario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "usuario_id", nullable = false)
	private Usuario usuario;

	/** Hash SHA-256 (hex) del token; el token en claro solo lo tiene el navegador. */
	@Column(name = "token_hash", nullable = false, unique = true)
	private String tokenHash;

	@Column(name = "expira_en", nullable = false)
	private Instant expiraEn;

	@Column(nullable = false)
	private boolean revocado;

	@Column(name = "creado_en", nullable = false, updatable = false)
	private Instant creadoEn;

	RefreshToken(Usuario usuario, String tokenHash, Instant expiraEn) {
		this.usuario = usuario;
		this.tokenHash = tokenHash;
		this.expiraEn = expiraEn;
		this.creadoEn = Instant.now();
	}

}
