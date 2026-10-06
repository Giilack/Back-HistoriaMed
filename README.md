# HistoriaMed — Backend

API REST del sistema de historias clínicas **HistoriaMed** (proyecto académico). Java 21 + Spring Boot 4 + PostgreSQL.

La lógica de negocio completa está en `plan.md` (en la carpeta raíz del proyecto).

## Requisitos

| Herramienta | Versión | Notas |
|---|---|---|
| Java (JDK) | 21 LTS | Eclipse Temurin recomendado. `JAVA_HOME` debe apuntar al JDK 21 |
| PostgreSQL | 16 o superior | Probado con PostgreSQL 18 |
| Maven | — | No hace falta instalarlo: se usa el wrapper `mvnw` |
| Docker | opcional | Solo para la prueba de integración con Testcontainers (sin Docker se omite) |

## Configuración

1. Crear la base de datos vacía (por ejemplo, `Db-Med`) en PostgreSQL. **Las tablas las crea Flyway** al arrancar.
2. Copiar `.env.example` como `.env` y completar los valores. `.env` **no se sube a Git**.

| Variable | Descripción |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/Db-Med` |
| `DB_USERNAME` / `DB_PASSWORD` | Credenciales de PostgreSQL con que trabaja la aplicación (en la nube, `historiamed_app`: mínimo privilegio) |
| `DB_MIGRACION_USERNAME` / `DB_MIGRACION_PASSWORD` | Opcional: usuario propietario solo para las migraciones (Flyway). Sin ellas se usa `DB_USERNAME` |
| `JWT_SECRET` | Secreto aleatorio de al menos 32 caracteres (`openssl rand -base64 48`) |
| `CIFRADO_CLAVE` | Clave de cifrado de los datos sensibles del paciente: 32 bytes en Base64 (`openssl rand -base64 32`). **Guarde una copia**: si se pierde, esos datos no se pueden descifrar |
| `ADMIN_INICIAL_USERNAME` / `ADMIN_INICIAL_PASSWORD` | Primer ADMIN; se crea solo si no existe ninguno y debe cambiar su contraseña al ingresar |
| `DEMO` | `true` para cargar datos de demostración (ver abajo). Por defecto `false` |
| `ALMACENAMIENTO_DIR` | Carpeta de los documentos subidos. Por defecto `uploads/` (ignorada por Git) |
| `COOKIE_SECURE` | `true` en producción (HTTPS) |
| `ALMACENAMIENTO_TIPO` | `local` (por defecto, carpeta `uploads/`) o `bd` (documentos en PostgreSQL; para la nube) |
| `FORWARD_HEADERS` | `framework` detrás de un proxy (nube), para registrar la IP real del usuario. Por defecto `none` |
| `PORT` | Puerto del servidor. Por defecto 8080 |
| `TURNSTILE_SECRET` | Clave secreta de Cloudflare Turnstile. Con ella, tras 3 intentos fallidos con el mismo usuario el login exige resolver un CAPTCHA. Vacía = sin CAPTCHA |

## Ejecutar

Desde la carpeta `backend` (ahí se lee el `.env`):

```powershell
.\mvnw.cmd spring-boot:run
```

La API queda en `http://localhost:8080`. Estado: `http://localhost:8080/actuator/health`.

## Pruebas

```powershell
.\mvnw.cmd test
```

Pruebas unitarias de las reglas de negocio (autenticación, pacientes, citas, triaje, alergias, atención, documentos, cifrado y revisión de documentos).
La prueba de contexto con Testcontainers se omite automáticamente si no hay Docker.

## Datos de demostración 

Datos **100 % ficticios** (principio P5: nunca datos reales). Se cargan **solo** si `DEMO=true` **y** la base no tiene
pacientes, para no mezclarlos nunca con datos reales. Se recomienda una base aparte:

```powershell
# 1. Crear en pgAdmin una base vacía, por ejemplo db_med_demo
# 2. Arrancar apuntando a ella con la carga activada:
$env:DB_URL = "jdbc:postgresql://localhost:5432/db_med_demo"; $env:DEMO = "true"; .\mvnw.cmd spring-boot:run
```

Crea 15 pacientes, alergias, ~60 días de historia (citas, triajes y atenciones cerradas) y el día de hoy con pacientes
en cada etapa del flujo. Usuarios de demostración (contraseña **`Demo2026`**):

| Usuario | Rol |
|---|---|
| `admision.demo` | Admisión |
| `triaje.demo` | Triaje |
| `dr.diaz`, `dra.torres` | Médico (Medicina General) |
| `dra.ruiz` | Médico (Pediatría) |

El ADMIN es el definido en `ADMIN_INICIAL_*`.

## Estructura

Organizado **por módulos de negocio** (`com.historiamed.backend`):

| Paquete | Contenido |
|---|---|
| `auth` | Login, JWT, refresh token rotativo (cookie httpOnly), cambio de contraseña |
| `usuario` | Usuarios y roles (ADMIN, ADMISION, TRIAJE, MEDICO) |
| `paciente` | Filiación, financiamiento (SIS/EsSalud/privado/particular), búsqueda |
| `consultorio` | Catálogo de consultorios |
| `cita` | Citas, llegada, cola y flujo de estados |
| `triaje` | Signos vitales, evaluador de alertas y prioridad |
| `alergia` | Alergias del paciente |
| `catalogo` | CIE-10 y medicamentos |
| `atencion` | Atención médica, diagnósticos, receta, alerta de alergias, plan de tratamiento (indicaciones, exámenes, interconsultas, descanso médico, control), cierre y adendas |
| `documento` | Documentos clínicos y almacenamiento (`AlmacenamientoService`) |
| `extraccion` | Revisión de los datos de un documento: llenado por categoría, validación del médico y paso a la historia |
| `antecedente` | Antecedentes del paciente (personales, familiares, quirúrgicos, diagnósticos previos, medicación habitual) |
| `laboratorio` | Resultados de laboratorio |
| `reporte` | Indicadores agregados para el ADMIN |
| `auditoria` | Registro inmutable de accesos y operaciones |
| `demo` | Carga de datos ficticios |
| `common`, `config` | Errores, seguridad, utilidades y configuración |

Las migraciones de la base de datos están en `src/main/resources/db/migration` (V1 a V15; la V11 es una migración Java en el paquete `paciente`). **Una migración ya aplicada
no se modifica nunca**: los cambios se hacen con una migración nueva.

## Despliegue en la nube

Vercel (frontend) + Render (backend, con el `Dockerfile`) + Neon (PostgreSQL). Los pasos, las variables y la lista
de validación están en [`docs/manual-despliegue.md`](docs/manual-despliegue.md).

## Seguridad (resumen)

- Contraseñas con hash Argon2id (los hashes BCrypt anteriores se actualizan al iniciar sesión); bloqueo de 15 minutos tras 5 intentos fallidos; contraseña temporal obligatoria de cambiar.
- Documento, teléfonos y dirección del paciente cifrados en la base con AES-256-GCM (`CifradoDatos`); la búsqueda por documento usa una huella HMAC.
- Access token JWT de 30 minutos + refresh token rotativo con detección de reutilización.
- Permisos por rol en cada endpoint: el ADMIN no accede a datos clínicos; ADMISION no ve contenido clínico.
- Auditoría inmutable (la base de datos rechaza modificarla) de accesos, cambios y descargas.
- Atenciones cerradas inmutables también en la base de datos (triggers).
- Documentos: formato verificado por contenido, nombres generados por el sistema y huella SHA-256.
