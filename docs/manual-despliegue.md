# HistoriaMed — Manual de despliegue en la nube

Despliegue gratuito del sistema con **datos ficticios**. No usar datos reales de pacientes en estos servicios.

## 1. Arquitectura

```
Navegador ──HTTPS──► Vercel (frontend React, archivos estáticos)
                        │  reenvía /api/*  (vercel.json)
                        ▼
                     Render (backend Spring Boot, contenedor Docker)
                        │  JDBC con TLS
                        ▼
                     Neon (PostgreSQL: datos y documentos)
```

| Pieza | Servicio | Por qué |
|---|---|---|
| Frontend | Vercel | Gratis; publica con cada push a `main`. Reenvía `/api` al backend, así el navegador ve un solo sitio y la cookie de sesión (`SameSite=Strict`) funciona igual que en local. |
| Backend | Render (Docker) | Ejecuta Java gratis con 512 MB de RAM. Se duerme tras 15 minutos sin uso. |
| Base de datos | Neon | PostgreSQL gratis y permanente (0,5 GB). |
| Documentos | La misma base (tabla `archivos`) | El disco de Render se borra en cada reinicio. Con `ALMACENAMIENTO_TIPO=bd` los PDF se guardan en PostgreSQL y entran en el mismo respaldo. |

Archivos del repositorio que intervienen:

| Archivo | Para qué |
|---|---|
| `backend/Dockerfile` | Construye la imagen del backend (compila con Maven, ejecuta con el JRE 21). |
| `backend/render.yaml` | Define el servicio en Render y sus variables. |
| `backend/.github/workflows/ci.yml` | Ejecuta las pruebas en cada push. |
| `frontend/vercel.json` | Reenvío de `/api` al backend y cabeceras de seguridad. |
| `frontend/.github/workflows/ci.yml` | Verifica tipos, estilo y compilación en cada push. |

## 2. Requisitos previos

- Los dos repositorios subidos a GitHub, en la rama `main`.
- Una cuenta en Neon, Render y Vercel (las tres permiten entrar con la cuenta de GitHub).
- Git Bash u otra terminal con `openssl`, para generar las claves.

## 3. Base de datos en Neon

1. En https://neon.tech crear un proyecto (por ejemplo `historiamed`). Elegir la región más cercana a la de Render.
2. En **Connection Details** elegir la conexión **directa** (sin "pooled"/"-pooler") y anotar el servidor, la base,
   el usuario y la contraseña.
3. Armar la dirección en formato JDBC:

   ```
   jdbc:postgresql://SERVIDOR/BASE?sslmode=require
   ```

No hay que crear tablas: el backend aplica las migraciones (Flyway) al arrancar.

## 4. Claves de producción

Generar valores **nuevos**, distintos de los de las computadoras de desarrollo:

```bash
openssl rand -base64 32    # CIFRADO_CLAVE
openssl rand -base64 18    # ADMIN_INICIAL_PASSWORD (contraseña inicial del administrador)
```

Guardar `CIFRADO_CLAVE` en un lugar seguro fuera de Render. **Si se pierde, el documento, los teléfonos y la
dirección de los pacientes no se pueden descifrar.** `JWT_SECRET` lo genera Render.

## 5. Backend en Render

1. En https://dashboard.render.com: **New → Blueprint** y elegir el repositorio del backend. Render lee
   `render.yaml`.
2. Completar las variables que pide:

   | Variable | Valor |
   |---|---|
   | `DB_URL` | La dirección JDBC del paso 3 |
   | `DB_USERNAME` / `DB_PASSWORD` | Usuario y contraseña de Neon (en el primer despliegue, el propietario; ver 5.1) |
   | `DB_MIGRACION_USERNAME` / `DB_MIGRACION_PASSWORD` | Propietario de Neon (`*_owner`), solo para las migraciones |
   | `CIFRADO_CLAVE` | La generada en el paso 4 |
   | `ADMIN_INICIAL_PASSWORD` | La generada en el paso 4 |
   | `CORS_ORIGENES` | Por ahora `https://pendiente.vercel.app`; se corrige en el paso 7 |

   Las demás ya vienen definidas en `render.yaml`:

   | Variable | Valor | Efecto |
   |---|---|---|
   | `JWT_SECRET` | generado por Render | Firma de los tokens de sesión |
   | `ADMIN_INICIAL_USERNAME` | `admin` | Primer administrador (se crea si no existe ninguno) |
   | `ALMACENAMIENTO_TIPO` | `bd` | Documentos en PostgreSQL |
   | `COOKIE_SECURE` | `true` | La cookie de sesión solo viaja por HTTPS |
   | `FORWARD_HEADERS` | `framework` | La auditoría registra la IP real del usuario, no la del proxy |
   | `DEMO` | `false` | Ver el paso 8 |

3. Esperar la primera construcción (varios minutos). Anotar la dirección del servicio, por ejemplo
   `https://historiamed-backend.onrender.com`.
4. Comprobar: `https://DIRECCION-DE-RENDER/actuator/health` debe responder `{"status":"UP"}` (incluye la base de datos).

## 5.1 Usuario de la aplicación con mínimo privilegio

La migración V16 crea el rol `historiamed_app`, que solo puede leer, crear y actualizar datos (sin `DROP`,
`TRUNCATE`, cambios de estructura ni desactivar triggers; `DELETE` solo en las tablas de detalle de un borrador).
Las migraciones siguen corriendo con el propietario.

1. Desplegar la versión con la V16 (el rol se crea sin poder iniciar sesión).
2. Generar una contraseña: `openssl rand -base64 24`.
3. En Neon → **SQL Editor**: `ALTER ROLE historiamed_app WITH LOGIN PASSWORD 'LA_CONTRASEÑA';`
4. En Render → **Environment**:
   - `DB_MIGRACION_USERNAME` / `DB_MIGRACION_PASSWORD`: el propietario (`historiamed_owner` y su contraseña).
   - `DB_USERNAME` = `historiamed_app` y `DB_PASSWORD` = la contraseña del paso 2.
5. Guardar con **Save, rebuild and deploy** y comprobar `/actuator/health`.

## 5.2 CAPTCHA en el inicio de sesión (Cloudflare Turnstile)

Tras 3 intentos fallidos con el mismo usuario, el login pide resolver un CAPTCHA (a los 5 se bloquea la cuenta 15
minutos). Se usa Cloudflare Turnstile: gratuito, sin tarjeta y sin acertijos de imágenes.

1. En https://dash.cloudflare.com (crear cuenta gratis) → **Turnstile → Add widget**.
2. Nombre `HistoriaMed`, dominio: la dirección de Vercel sin `https://` (y `localhost` para probar en local);
   modo **Managed**. Cloudflare entrega una **Site Key** (pública) y una **Secret Key** (secreta).
3. Render → **Environment**: `TURNSTILE_SECRET` = la Secret Key → **Save, rebuild and deploy**.
4. Vercel → **Settings → Environment Variables**: `VITE_TURNSTILE_SITE_KEY` = la Site Key → volver a desplegar.

Las dos claves van juntas: con una sola, el CAPTCHA no funciona. Sin ellas el sistema funciona igual, sin CAPTCHA.

## 6. Frontend en Vercel

1. En `frontend/vercel.json`, cambiar `https://historiamed-backend.onrender.com` por la dirección real del paso 5
   y subir el cambio a `main`.
2. En https://vercel.com: **Add New → Project**, importar el repositorio del frontend. Vercel detecta Vite; no hay
   variables de entorno que configurar.
3. Desplegar y anotar la dirección, por ejemplo `https://historiamed.vercel.app`.

## 7. Enlazar backend y frontend

En Render → servicio → **Environment**, poner en `CORS_ORIGENES` la dirección exacta de Vercel (con `https://`,
sin barra final). Guardar: Render vuelve a desplegar.

Si esta dirección no coincide, el inicio de sesión responde **403**.

## 8. Datos de demostración

Solo con la base vacía (sin pacientes):

1. En Render poner `DEMO=true` y guardar.
2. Cuando termine de arrancar, en los registros aparece "Datos de demostración cargados".
3. **Volver a poner `DEMO=false`.**

Usuarios ficticios (contraseña `Demo2026`): `admision.demo`, `triaje.demo`, `dr.diaz`, `dra.torres`, `dra.ruiz`.
El administrador es `admin`, con la contraseña inicial del paso 4; el sistema obliga a cambiarla al entrar.

> La contraseña de demostración está escrita en el código, que es público: cualquiera que conozca la dirección
> puede entrar con esos usuarios. Es aceptable solo porque los datos son ficticios.

## 9. Validación después de desplegar

| # | Comprobación | Resultado esperado |
|---|---|---|
| 1 | `GET /actuator/health` y `/actuator/health/liveness` en Render | `{"status":"UP"}` en los dos |
| 2 | Abrir la dirección de Vercel | Pantalla de inicio de sesión, con candado HTTPS |
| 3 | Entrar con `admision.demo` | Panel de inicio de admisión |
| 4 | Recargar la página (F5) | La sesión se mantiene |
| 5 | Buscar un paciente por DNI | Aparece el paciente |
| 6 | Como `triaje.demo`, subir un PDF y abrir "Registrar datos" | El PDF se ve junto al formulario |
| 7 | Reiniciar el servicio en Render y abrir el mismo PDF | Sigue disponible |
| 8 | Como `dr.diaz`, atender, firmar e imprimir la receta | Atención firmada; vista de impresión correcta |
| 9 | Como `admin`, abrir Auditoría | Se ven los accesos, con la IP de cada usuario |
| 10 | Consola del navegador (F12) | Sin errores ni avisos de la política de seguridad |

Guardar capturas de cada paso en `docs/despliegue/evidencias/`.

## 10. Actualizar el sistema

Cada push a `main` despliega de nuevo: Vercel el frontend y Render el backend. Las migraciones nuevas se aplican
solas al arrancar. Antes del despliegue, GitHub Actions ejecuta las pruebas; si fallan, conviene corregir antes de
confiar en esa versión.

## 11. Respaldo

```bash
pg_dump "postgresql://USUARIO:CONTRASEÑA@SERVIDOR/BASE?sslmode=require" -Fc -f historiamed.dump
```

El volcado incluye los documentos (tabla `archivos`). Para restaurar: `pg_restore -d "DIRECCION" historiamed.dump`.
El respaldo solo sirve junto con la `CIFRADO_CLAVE` con la que se cifraron los datos.

### 11.1 Respaldo automático diario

El flujo `.github/workflows/respaldo-diario.yml` respalda la base de Neon todos los días a las 2:00 a. m. (hora de Lima),
cifra el archivo con GPG (AES-256) y lo guarda 30 días como artefacto en GitHub → **Actions → Respaldo diario de la
base de datos**. Se activa agregando dos secretos en GitHub → **Settings → Secrets and variables → Actions**:

| Secreto | Valor |
|---|---|
| `RESPALDO_DB_URL` | `postgresql://historiamed_owner:CONTRASEÑA@SERVIDOR/historiamed?sslmode=require` (el propietario) |
| `RESPALDO_CLAVE` | Una frase larga (`openssl rand -base64 32`). Guardar una copia: sin ella el respaldo no se puede abrir |

Sin los secretos el flujo termina sin hacer nada. Para probarlo: **Run workflow**.

Restaurar un respaldo descargado (en Git Bash, con PostgreSQL 17 o superior):

```bash
gpg -d historiamed_AAAA-MM-DD.dump.gpg > historiamed.dump      # pide RESPALDO_CLAVE
pg_restore --no-owner -d "postgresql://USUARIO:CONTRASEÑA@SERVIDOR/BASE_NUEVA?sslmode=require" historiamed.dump
```

## 12. Problemas frecuentes

| Síntoma | Causa probable | Solución |
|---|---|---|
| El inicio de sesión responde 403 | `CORS_ORIGENES` no coincide con la dirección de Vercel | Corregirla en Render (paso 7) |
| La primera visita tarda cerca de un minuto o da error 502 | El servicio gratuito de Render estaba dormido | Esperar y reintentar; abrir el sistema unos minutos antes de una demostración |
| La sesión se pierde al recargar | Se abrió el backend directamente, o `/api` no pasa por Vercel | Revisar la dirección en `vercel.json` |
| El backend no arranca: "Falta CIFRADO_CLAVE" | Variable vacía o mal copiada | Debe ser Base64 de 32 bytes (44 caracteres) |
| El backend se reinicia solo | Falta de memoria (512 MB) | Ver los registros; bajar `MaxRAMPercentage` en el `Dockerfile` |
| Vercel falla al instalar | Versión de pnpm | El proyecto fija `pnpm@12.6.0` en `package.json`; activar Corepack en Vercel si hace falta |

## 12.1 Mantener el backend despierto (opcional)

El plan gratuito de Render duerme el servicio tras 15 minutos sin visitas. Para que el sistema responda al instante
a cualquier hora, algo debe visitarlo cada 10 minutos.

**Implementado:** el flujo de GitHub Actions `.github/workflows/mantener-despierto.yml` lo visita cada 10 minutos desde
`main` (gratis en repositorios públicos). Se ve en GitHub → **Actions → Mantener despierto el backend**, y se puede
ejecutar a mano con *Run workflow*. GitHub puede retrasar los flujos programados unos minutos y los desactiva si el
repositorio pasa 60 días sin actividad.

**Alternativa más puntual** (servicio externo gratuito):

1. Crear una cuenta en https://cron-job.org.
2. **Create cronjob**:
   - URL: `https://DIRECCION-DE-RENDER/actuator/health/liveness`
   - Ejecución: cada 10 minutos.
   - Activar el aviso por correo si falla, para enterarse si el servicio se cae.
3. Guardar y comprobar en el historial que las llamadas responden 200.

Usar siempre `/actuator/health/liveness` y **no** `/actuator/health`: el segundo consulta la base de datos, y con
una visita cada 10 minutos Neon nunca se apagaría y consumiría sus horas gratuitas de cómputo. `liveness` solo
comprueba que el proceso responde.

Un mes tiene 744 horas y la capa gratuita de Render da 750 horas al mes: alcanza para **un** servicio encendido
todo el tiempo, no para dos.

## 13. Límites de la capa gratuita

- Render duerme el servicio tras 15 minutos sin uso.
- Neon: 0,5 GB en total, contando los documentos (cada uno pesa como máximo 10 MB).
- La extracción con IA (fases 11 a 13 de `plan.md`) no corre en estos servicios: necesita más memoria.
- Las condiciones de los tres servicios cambian: confirmarlas al crear las cuentas.

## 14. Estado de verificación de este manual

Comprobado en un equipo local el 30/09/2026, simulando la nube:

- El archivo ejecutable de producción arranca con 256 MB de memoria para Java, aplica las 15 migraciones sobre una
  base vacía y carga los datos de demostración.
- Con `ALMACENAMIENTO_TIPO=bd`, un PDF subido se lee idéntico y sigue disponible tras reiniciar el servidor.
- La versión compilada del frontend, servida con las cabeceras de `vercel.json`, funciona sin ningún aviso de la
  política de seguridad, incluido el visor de PDF.

**Pendiente de comprobar en el despliegue real**, porque no se podía probar sin las cuentas ni sin Docker:

- La construcción de la imagen con el `Dockerfile`.
- Los flujos de GitHub Actions.
- El reenvío de Vercel con archivos grandes (hasta 10 MB) y con el arranque en frío de Render.
- La instalación con pnpm 12 en Vercel.
