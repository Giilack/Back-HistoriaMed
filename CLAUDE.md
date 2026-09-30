# Idea del Proyecto
HistoriaMed es un sistema web inteligente para la gestión, organización y consulta de historias clínicas, orientado como proyecto académico para el sector salud público.

El objetivo es desarrollar una aplicación web profesional que permita centralizar la información clínica de los pacientes, facilitar la atención médica, gestionar documentos clínicos y utilizar inteligencia artificial para extraer información de documentos y facilitar la consulta de la historia clínica.

El sistema debe ser desarrollado con una arquitectura profesional, segura, modular y escalable, pero adecuada al nivel de un proyecto universitario de 9.º ciclo y utilizando **herramientas gratuitas** durante el desarrollo.

La lógica de negocio, las decisiones tomadas y el estado de cada fase están en **`plan.md`**. Léelo antes de cambiar reglas de negocio.

# Tecnologías

FRONTEND (`frontend/`, repositorio propio):

- React 19 + TypeScript + Vite
- Tailwind CSS + shadcn/ui
- react-hook-form + zod (formularios y validación)
- pnpm

BACKEND (`backend/`, repositorio propio):

- Java 21 (LTS)
- Spring Boot 4, Spring Security (JWT con OAuth2 Resource Server), contraseñas con Argon2id
- Datos sensibles del paciente (documento, teléfonos, dirección) cifrados con AES-256-GCM; la clave `CIFRADO_CLAVE` vive solo en `.env`
- Spring Data JPA / Hibernate, API REST
- Flyway (migraciones), Maven (wrapper `mvnw`)

BASE DE DATOS:

- PostgreSQL (base local `Db-Med`)

ALMACENAMIENTO DE DOCUMENTOS:

- Disco local (`backend/uploads/`, ignorado por Git) detrás de la interfaz `AlmacenamientoService`.
- Cloudflare R2 (capa gratuita, compatible con S3) si se despliega en la nube. **No se usa Amazon S3** (es de pago).

INTELIGENCIA ARTIFICIAL: planificada para las fases 11 y 12 (ver `plan.md`, secciones 8 y 9). Antes van la revisión de documentos con llenado manual (fase 8), el tratamiento estructurado (fase 9) y el despliegue (fase 10). Cuando llegue: servicio en Python + FastAPI con modelos locales (Ollama, Qwen3 4B), sin enviar datos clínicos a APIs externas.

# Cómo ejecutar

- Backend: en `backend/`, `.\mvnw.cmd spring-boot:run` (lee `backend/.env`; plantilla en `.env.example`). Pruebas: `.\mvnw.cmd test`.
- Frontend: en `frontend/`, `pnpm dev` → http://localhost:5173 (proxy de `/api` al puerto 8080).
- Datos de demostración ficticios: `DEMO=true` sobre una base sin pacientes (ver `backend/README.md`).

# Reglas del proyecto

- **Nunca usar datos reales de pacientes**: solo datos sintéticos.
- **Nunca modificar una migración ya aplicada**: crear una nueva (`V9__...sql`).
- **No subir secretos ni datos**: `.env` y `uploads/` están en `.gitignore`.
- **Mínimo privilegio**: el ADMIN no ve datos clínicos; ADMISION no ve contenido clínico. Los permisos los decide el backend (`@PreAuthorize`), no el frontend.
- **La historia clínica no se borra ni se reescribe**: atenciones cerradas inmutables (adendas para corregir); alergias y documentos se inactivan/anulan con motivo.
- **Todo acceso a datos clínicos se audita** (`AuditoriaService`).
- **Las reglas clínicas críticas son deterministas** (alertas de triaje, alergias al recetar), nunca dependen de la IA.
- Backend organizado por módulos de negocio (`paciente`, `cita`, `triaje`, `atencion`…): un módulo no usa el repositorio de otro, sino su servicio.
- Código y mensajes en español; sufijos técnicos en inglés (`PacienteService`, `CitaController`).
