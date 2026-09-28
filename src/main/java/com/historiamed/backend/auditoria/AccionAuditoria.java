package com.historiamed.backend.auditoria;

public enum AccionAuditoria {

	// Sesión
	LOGIN_EXITOSO,
	LOGIN_FALLIDO,
	CUENTA_BLOQUEADA,
	LOGOUT,
	CAMBIO_PASSWORD,
	/** Se presentó un refresh token ya usado: posible robo de sesión. */
	REFRESH_REUTILIZADO,

	// Operaciones sobre recursos
	VER,
	CREAR,
	EDITAR,
	ACTIVAR,
	DESACTIVAR,
	RESETEAR_PASSWORD

}
