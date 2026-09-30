# HistoriaMed — Plan y lógica de negocio

> Documento vivo. Define **qué** hace el sistema y **por qué**. La estructura técnica del código está en `CLAUDE.md`.

> ⏸️ **Decisión (27/09/2026): la IA queda pospuesta.** Por ahora no se usará ninguna API de IA ni modelos abiertos. Las secciones 5.5 (extracción) y 5.6 (chatbot) describen el diseño objetivo, pero se implementarán más adelante. Mientras tanto, los documentos se suben, almacenan y visualizan **sin procesamiento automático**, y el sistema se diseña para que la IA pueda conectarse después sin rehacer nada.

## 1. Alcance

Sistema web de **historia clínica electrónica para consulta externa** de un establecimiento de salud público (primer nivel de atención). Cubre el flujo completo de una atención ambulatoria:

```
Paciente llega → ADMISIÓN → TRIAJE → MÉDICO → Atención cerrada
```

**Fuera de alcance (por ahora):** hospitalización, emergencia, farmacia/inventario, facturación, laboratorio como módulo propio, portal del paciente.

---

## 2. Principios que guían todas las decisiones

| # | Principio | Consecuencia en el sistema |
|---|---|---|
| P1 | **Mínimo privilegio** | Cada rol ve y hace solo lo que su función requiere. El ADMIN **no** lee historias clínicas. |
| P2 | **La IA sugiere, el humano decide** | Nada que genere la IA entra a la historia clínica sin que un médico lo confirme. |
| P3 | **La historia clínica no se borra ni se reescribe** | Una atención cerrada es inmutable. Las correcciones se hacen con una **adenda** (nota aclaratoria con fecha y autor). Borrado solo lógico. |
| P4 | **Todo acceso queda registrado** | Quién vio o modificó qué historia, cuándo y desde dónde (auditoría). |
| P5 | **Datos sintéticos** | En desarrollo y sustentación **nunca** se usan datos reales de pacientes. |
| P6 | **Los datos clínicos no salen del servidor** | La IA corre en local. No se envían historias clínicas a APIs de terceros. |
| P7 | **Todo gratuito** | Solo software libre o capas gratuitas sin riesgo de cobro. |
| P8 | **Las reglas críticas no dependen de la IA** | Alertas de alergias y validaciones se hacen con reglas deterministas, no con el LLM. |

---

## 3. Roles

Cuatro roles, cada uno corresponde a un proceso real del establecimiento. Un usuario tiene **un** rol.

### ADMIN — administra el sistema, no la atención
- Crear, editar, desactivar y reactivar usuarios (ADMIN, ADMISION, TRIAJE, MEDICO).
- Resetear contraseñas.
- Registrar datos de los médicos: CMP, especialidad, consultorio.
- Gestionar catálogos: especialidades, consultorios, horarios de atención.
- Consultar la **auditoría** de accesos.
- **No puede** ver el contenido clínico (diagnósticos, recetas, notas, documentos).

### ADMISION — la puerta de entrada del paciente
- **Registrar y actualizar pacientes** (filiación): tipo y número de documento, nombres, fecha de nacimiento, sexo, dirección, teléfono y contacto de emergencia.
- **Registrar el financiamiento** (SIS, EsSalud, privado o particular); ver la sección 5.2.
- **Crear el número de historia clínica** al registrar un paciente nuevo.
- **Gestionar citas**: programar, reprogramar, cancelar y marcar "no se presentó".
- **Registrar la llegada** del paciente, lo que lo pone en la **cola de atención**.
- **Subir documentos** del paciente (escaneados o PDF) que se envían al procesamiento con IA.
- Ver la cola del día (solo nombres, estado y consultorio, sin datos clínicos).

### TRIAJE — evaluación inicial antes del médico
- Ver la **cola de triaje** (pacientes que llegaron y esperan).
- **Registrar signos vitales**: presión arterial, frecuencia cardiaca, frecuencia respiratoria, temperatura, saturación de O₂, peso, talla (el IMC se calcula solo) y perímetro abdominal.
- Registrar el **motivo de consulta** según lo relata el paciente.
- Asignar la **prioridad** de atención (ver 5.3).
- Ver el **resumen clínico** del paciente: alergias, antecedentes importantes y última atención. No ve la historia completa.
- Registrar o actualizar **alergias** reportadas por el paciente.
- **Subir documentos** que traiga el paciente (por ejemplo, resultados de laboratorio).

### MEDICO — la atención clínica
- Ver **su cola** de pacientes (ya triados, ordenados por prioridad y hora).
- Ver la **historia clínica completa** del paciente en atención.
- Registrar la **atención**: anamnesis, examen físico, diagnósticos (CIE-10), plan de trabajo.
- **Recetar** medicamentos (con alerta de alergias) e indicar tratamientos.
- Solicitar exámenes auxiliares e interconsultas.
- **Validar** los datos extraídos por la IA de los documentos.
- Usar el **asistente de IA** (chatbot) sobre la historia del paciente.
- **Cerrar (firmar)** la atención, que desde ese momento queda inmutable, y agregar adendas.

### Matriz de permisos

| Acción | ADMIN | ADMISION | TRIAJE | MEDICO |
|---|:-:|:-:|:-:|:-:|
| Gestionar usuarios y catálogos | ✅ | ❌ | ❌ | ❌ |
| Ver auditoría | ✅ | ❌ | ❌ | ❌ |
| Registrar o editar paciente y financiamiento | ❌ | ✅ | ❌ | ❌ |
| Gestionar citas y registrar llegada | ❌ | ✅ | ❌ | ❌ |
| Ver la cola | ❌ | ✅ (sin datos clínicos) | ✅ | ✅ (la suya) |
| Subir documentos | ❌ | ✅ | ✅ | ✅ |
| Registrar signos vitales, prioridad y alergias | ❌ | ❌ | ✅ | ✅ |
| Ver resumen clínico (alergias, antecedentes) | ❌ | ❌ | ✅ | ✅ |
| Ver historia clínica completa | ❌ | ❌ | ❌ | ✅ |
| Diagnosticar, recetar, cerrar atención | ❌ | ❌ | ❌ | ✅ |
| Validar extracción de IA | ❌ | ❌ | ❌ | ✅ |
| Usar el chatbot clínico | ❌ | ❌ | ❌ | ✅ |

---

## 4. Flujo de una atención

```
[ADMISION]                    [TRIAJE]                 [MEDICO]
Registra/busca paciente
Verifica financiamiento
Cita o llegada sin cita ──►  EN_ESPERA_TRIAJE
                              Signos vitales
                              Motivo + prioridad ──►  EN_ESPERA_CONSULTA
                                                      EN_CONSULTA
                                                      Diagnóstico, receta
                                                      Cierra atención ──► ATENDIDO
```

### Estados de la cita o turno

```
PROGRAMADA ──► EN_ESPERA_TRIAJE ──► EN_ESPERA_CONSULTA ──► EN_CONSULTA ──► ATENDIDO
    │                 │
    ├──► CANCELADA    └──► NO_SE_PRESENTO (si no responde al llamado)
    └──► NO_SE_PRESENTO
```

**Reglas:**
- Solo se pasa de un estado al siguiente; no se salta triaje.
- Un paciente no puede tener dos turnos activos el mismo día en el mismo consultorio.
- Se admite la **llegada sin cita** (el turno entra directamente a `EN_ESPERA_TRIAJE`).
- Cada cambio de estado guarda la hora; así se pueden mostrar tiempos de espera como indicador.

---

## 5. Reglas de negocio por módulo

### 5.1 Pacientes
- **Identificación única:** tipo de documento (DNI, carné de extranjería, pasaporte, sin documento) + número. No se permiten duplicados.
- **Paciente sin documento** (por ejemplo, un recién nacido o una persona indocumentada): se registra con un identificador temporal y se completa después.
- **Número de historia clínica:** uno por paciente, generado al registrarlo. **Decisión:** correlativo propio con formato `HC-000001`, no el DNI, porque hay pacientes sin documento, extranjeros o personas que cambian de documento.
- Antes de registrar a alguien, se **busca** primero para evitar duplicados.
- Validaciones: DNI de 8 dígitos, fecha de nacimiento no futura, edad calculada automáticamente.

### 5.2 Financiamiento (SIS)
**Corrección a la idea inicial:** el SIS es un **seguro público y la afiliación es de la persona**. Un establecimiento no puede asignar "su SIS" a un paciente que no lo tiene.

Modelo propuesto:
- `tipoFinanciamiento`: `SIS`, `ESSALUD`, `PRIVADO` (EPS o seguro privado), `PARTICULAR` (paga).
- Si es SIS: número de afiliación, tipo de plan y **estado** (`ACTIVO`, `INACTIVO`), más la fecha de la última verificación.
- **Si el paciente no tiene seguro:** se registra como `PARTICULAR`, y ADMISION puede marcarlo como **"orientado a afiliación SIS"**, para que el establecimiento lo derive al trámite de afiliación.
- **Verificación:** en el sistema real se consulta el padrón del SIS. En el proyecto académico, la verificación es **manual** (ADMISION marca "verificado" con la fecha) o **simulada** con un servicio falso. Se deja una interfaz `VerificadorSeguro` para poder conectarla en el futuro.
- *(Opcional)* Para atenciones SIS, generar un resumen tipo **FUA** (Formato Único de Atención).

### 5.3 Triaje
- Rangos válidos de signos vitales (por ejemplo, temperatura entre 30 y 45 °C, saturación entre 50 y 100 %). Fuera de esos rangos no se guarda.
- **Alertas automáticas** por valores anormales (por ejemplo, saturación menor a 92 % o presión mayor o igual a 180/110). Son reglas fijas, no IA.
- **Prioridad:** `NORMAL`, `PREFERENTE` (gestantes, adultos mayores, niños, personas con discapacidad) o `URGENTE` (valores de alerta; se sugiere derivar a emergencia).
- La cola del médico se ordena por prioridad y luego por hora de llegada.

**Implementación (fase 4):**
- **Umbrales de alerta** (crítica → sugiere URGENTE; advertencia → solo informa):

  | Signo | Advertencia | Crítica |
  |---|---|---|
  | Saturación O₂ | 90–91 % | < 90 % |
  | Temperatura | ≥ 38 °C | ≥ 40 °C o < 35 °C |
  | Presión arterial* | ≥ 140/90 o sistólica < 90 | ≥ 180/110 o sistólica < 80 |
  | Frecuencia cardiaca* | > 100 o < 50 lpm | > 130 o < 40 lpm |
  | Frecuencia respiratoria* | > 24 o < 10 rpm | > 30 o < 8 rpm |
  | IMC (≥ 18 años) | ≥ 30 o < 18,5 | — |

  \* Solo desde los 12 años: en niños los valores normales dependen de la edad. **Limitación conocida:** en menores de 12 años solo se evalúan temperatura y saturación.
- **Edades:** "niño" = menor de 12 años; adulto mayor = 60 años o más (Ley 30490).
- **Prioridad sugerida:** una alerta crítica → URGENTE; si no, gestante, discapacidad, niño o adulto mayor → PREFERENTE; si no, NORMAL.
- **Asignar una prioridad menor a la sugerida exige una justificación escrita** (por ejemplo, la saturación habitual de un paciente con EPOC).
- Las alertas se **guardan con el triaje** tal como se mostraron, aunque las reglas cambien después.
- **Alergias:** las registran TRIAJE y MÉDICO; no se borran, se inactivan con un motivo. "Sin alergias registradas" no equivale a "no tiene alergias".

### 5.4 Atención médica
- Cada atención incluye: motivo (viene de triaje), anamnesis, examen físico, **diagnósticos CIE-10** (al menos uno; uno se marca como principal; tipo presuntivo o definitivo), plan, receta e indicaciones.
- **Receta:** medicamento (de un catálogo), dosis, vía, frecuencia y duración.
- **Alerta de alergias (determinista):** si el medicamento recetado coincide con una alergia registrada, el sistema **bloquea o exige confirmación explícita** con justificación. No depende de la IA.
- **Cierre:** al firmar, la atención queda inmutable (P3). Después solo se permiten adendas.
- Solo el médico asignado puede editar su atención mientras está abierta.

**Implementación (fase 5):**
- **Flujo:** el médico pulsa "Atender" (cita → EN_CONSULTA) → guarda borradores → "Firmar y cerrar" (cita → ATENDIDO).
- **Para cerrar** se exige anamnesis, examen físico y al menos un diagnóstico, con uno marcado como principal.
- **Alerta de alergias** (reglas fijas): coincide el principio activo; o la alergia nombra el grupo (penicilinas, sulfas, AINE, macrólidos, quinolonas…); o hay reacción cruzada conocida (penicilina → cefalosporinas). Compara palabras completas: "sulfa" no coincide con "sulfato ferroso". Para recetar igual hay que confirmar y justificar; queda registrado en la receta.
- **Inmutabilidad:** además de la aplicación, triggers de PostgreSQL impiden modificar una atención cerrada, su diagnóstico o su receta.
- **Adendas:** cualquier médico puede agregarlas a una atención cerrada; quedan con su autor y fecha.
- **Historia clínica** (lista de atenciones): solo el MEDICO. TRIAJE ve alergias y triajes, no la historia completa.
- **Receta imprimible** desde la atención firmada.

### 5.5 Documentos e IA de extracción
> ⏸️ **Pospuesto (solo la parte de IA).** Mientras tanto, el documento se sube, se clasifica **manualmente** (quien lo sube elige el tipo) y queda en estado `RECIBIDO`, visible en la historia. Cuando se active la IA, los documentos ya subidos podrán procesarse retroactivamente.

**Implementación actual (fase 6, sin IA):**
- Suben ADMISION, TRIAJE y MÉDICO; **el contenido solo lo abren TRIAJE y MÉDICO**. ADMISION ve la lista para no duplicar.
- **PDF, JPG o PNG, máximo 10 MB.** El formato se detecta por los primeros bytes del archivo, no por la extensión: un ejecutable renombrado a .pdf se rechaza.
- Se guarda con un nombre generado por el sistema (`uploads/2026/09/uuid.pdf`), junto con su **huella SHA-256** para comprobar que no fue alterado. La carpeta `uploads/` está en .gitignore.
- **No se borran:** se anulan con un motivo (lo puede hacer quien lo subió o un médico) y su contenido deja de mostrarse.
- Cada vez que se abre un documento queda registrado en la auditoría (DESCARGAR).

Flujo objetivo (con IA):

```
Subida (ADMISION, TRIAJE o MEDICO)
   └─► RECIBIDO ─► PROCESANDO ─► PENDIENTE_REVISION ─► VALIDADO (lo confirma un médico)
                        │                          └─► RECHAZADO
                        └─► ERROR (se puede reintentar; el archivo nunca se pierde)
```

1. Se sube el archivo (PDF, JPG o PNG; máximo 10 MB) y se asocia al paciente.
2. **Clasificación automática** del tipo de documento: `LABORATORIO`, `RECETA`, `INFORME_MEDICO`, `EPICRISIS`, `IMAGENOLOGIA`, `REFERENCIA`, `OTRO`.
3. **Extracción** de datos estructurados según el tipo (por ejemplo, en laboratorio: examen, valor, unidad, rango y fecha).
4. El resultado queda como **sugerencia** en `PENDIENTE_REVISION`. El médico ve el documento original al lado de los datos extraídos, los corrige y los confirma. Solo entonces pasan a la historia clínica (P2).
5. El procesamiento es **asíncrono**: la subida responde al instante y el estado se actualiza cuando termina.

**Limitación a considerar:** los PDF digitales y los documentos impresos escaneados se extraen bien. **La letra manuscrita**, sobre todo la médica, tiene una precisión baja con herramientas gratuitas. Se procesa como "mejor esfuerzo" y siempre requiere revisión. Conviene mencionarlo como limitación en la sustentación.

### 5.6 Asistente clínico (chatbot del médico)
> ⏸️ **Pospuesto.** Diseño de referencia para cuando se active la IA.

- **Alcance limitado a un paciente:** el chatbot solo responde sobre la historia del paciente que el médico tiene abierta. No puede consultar otros pacientes.
- **Técnica RAG:** busca en la historia del paciente (atenciones, diagnósticos, recetas, alergias, documentos validados) y responde **citando la fuente**, por ejemplo: "Atención del 12/03/2026, Dr. X".
- **Preguntas de consulta** (el uso principal): "¿qué medicamentos se le recetaron antes?", "¿tiene alergias?", "¿qué diagnósticos previos tiene?", "resume sus últimas 3 atenciones".
- **Preguntas de recomendación** ("¿qué otro medicamento puedo darle?"): se permiten, pero la respuesta:
  - se marca como **"sugerencia de apoyo, no reemplaza el criterio médico"**;
  - pasa por el mismo **filtro determinista de alergias** (P8), de modo que nunca sugiere algo a lo que el paciente es alérgico sin advertirlo;
  - no se guarda en la historia clínica automáticamente.
- Si la información no está en la historia, el asistente debe responder **"no hay registro"** en lugar de inventar.
- Cada pregunta y respuesta se guarda en la **auditoría**.

### 5.7 Auditoría
- Se registra: usuario, rol, acción (VER, CREAR, EDITAR, CERRAR, DESCARGAR, CONSULTA_IA), recurso, paciente, fecha y hora, e IP.
- Los registros no se pueden editar ni borrar desde el sistema.
- Solo el ADMIN los consulta, con filtros por usuario, paciente y fechas.

### 5.8 Usuarios y seguridad
- Contraseñas con BCrypt. Al primer ingreso, el usuario debe cambiar la contraseña temporal.
- Bloqueo temporal tras 5 intentos fallidos.
- JWT de acceso de corta duración (15 min) + refresh token.
- Los usuarios se **desactivan**, no se borran (sus atenciones siguen firmadas por ellos).
- Un ADMIN no puede desactivarse a sí mismo ni quitar el último ADMIN.

---

## 6. Tecnologías (todo gratuito)

| Necesidad | Decisión | Costo |
|---|---|---|
| Frontend | React + TypeScript + Vite + Tailwind + shadcn/ui | Gratis |
| Backend | Java 21 + Spring Boot 4 + Spring Security (JWT) + JPA + Flyway | Gratis |
| Base de datos | PostgreSQL 18 (+ pgvector para el chatbot) | Gratis |
| Servicio de IA ⏸️ | Python + FastAPI | Gratis (pospuesto) |
| **LLM** ⏸️ | Por definir cuando se retome la IA (ver 6.1) | Pospuesto |
| OCR ⏸️ | Tesseract / PaddleOCR (local) | Gratis (pospuesto) |
| Embeddings ⏸️ | Modelo multilingüe de Hugging Face (local) | Gratis (pospuesto) |
| **Archivos** | **Disco local en desarrollo; Cloudflare R2 en la nube** (ver 6.2) | Gratis |

### 6.1 Sobre DeepSeek
> ⏸️ Decisión pospuesta junto con la IA. Se conserva este análisis para cuando se retome.

- **API de DeepSeek:** ❌ **no recomendada**.
  - Es de pago (barata, pero no gratis), lo que contradice P7.
  - Envía los datos clínicos a servidores externos (en China), lo que contradice P6 y complica el cumplimiento de la Ley 29733 de protección de datos personales en cuanto a transferencias internacionales.
- **Modelos abiertos de DeepSeek en local vía Ollama:** ✅ opción válida y gratuita. Por ejemplo, las versiones destiladas de DeepSeek-R1. Los datos no salen de la máquina.
- **Recomendación:** que el modelo sea **configurable** (una variable de entorno) y comparar en pruebas DeepSeek frente a Qwen o Llama para español clínico. Los modelos de razonamiento como R1 son más lentos; para extraer datos y responder preguntas puntuales puede rendir mejor un modelo "instruct".
- **Requisito:** al menos 16 GB de RAM para modelos de 7–8B parámetros. Con GPU es más rápido. *(Por confirmar: RAM y GPU del equipo.)*

### 6.2 Sobre Cloudflare R2
- ✅ **Buena elección para la nube.** Tiene una capa gratuita (10 GB de almacenamiento al mes y **sin costo por descarga**), y es compatible con la API de S3, así que se usa con el SDK de AWS.
- ⚠️ Para activarlo, Cloudflare pide registrar un método de pago. Mientras no se superen los límites no cobra, pero conviene configurar alertas. Verifica las condiciones vigentes al activarlo.
- **Diseño:** una interfaz `AlmacenamientoService` con dos implementaciones:
  - `AlmacenamientoLocal`: carpeta `backend/uploads/`, para desarrollo y sustentación, sin cuentas ni tarjeta.
  - `AlmacenamientoR2`: para la nube, activada por configuración.
- El bucket es **privado**. Los archivos se descargan solo con **URLs prefirmadas** de corta duración (5 min), y cada descarga se audita.

---

## 7. Decisiones pendientes

- [x] ¿Número de HC = DNI, o correlativo propio? → Correlativo `HC-000001` (ver 5.1).
- [ ] ⏸️ RAM y GPU del equipo, para definir el modelo de IA (cuando se retome la IA).
- [x] ¿Un médico atiende en uno o en varios consultorios o especialidades? → No está atado a un consultorio: cada cita indica médico y consultorio (catálogo de consultorios gestionado por el ADMIN).
- [ ] ¿Se incluye el resumen FUA para pacientes SIS?
- [x] Catálogo de medicamentos: ¿lista reducida propia o un petitorio oficial? → Lista reducida (~50 medicamentos esenciales frecuentes) con principio activo y grupo farmacológico. Se puede reemplazar por el petitorio oficial con otra migración.
- [x] Catálogo CIE-10: importar el listado completo o un subconjunto frecuente. → Subconjunto (~75 diagnósticos frecuentes del primer nivel). Ampliable con otra migración; si se importa completo, la búsqueda debe pasar a la BD.

---

## 8. Fases de desarrollo

| Fase | Contenido | Entregable |
|---|---|---|
| 0 | Base del proyecto (hecho: backend, frontend, BD conectada) | ✅ |
| 1 | Seguridad: usuarios, roles, login JWT, auditoría básica | Login funcional con 4 roles — ✅ |
| 2 | Pacientes + financiamiento + búsqueda | ADMISION registra pacientes — ✅ (pendiente la prueba en navegador) |
| 3 | Citas, llegada y cola de atención | Flujo de estados funcionando — ✅ (pendiente la prueba en navegador) |
| 4 | Triaje: signos vitales, alertas, prioridad y alergias | TRIAJE deriva al médico — ✅ (pendiente la prueba en navegador) |
| 5 | Atención médica: CIE-10, receta, alerta de alergias, cierre | Atención completa de principio a fin — ✅ (pendiente la prueba en navegador) |
| 6 | Documentos: subida, clasificación manual, almacenamiento y visor | Documentos asociados al paciente — ✅ (pendiente la prueba en navegador) |
| 7 | Pulido: reportes, datos sintéticos de demo, documentación | Versión funcional sin IA — ✅ (despliegue en la nube: opcional, pendiente) |
| 8 ⏸️ | IA de extracción: OCR, clasificación, extracción y validación | Documentos a datos sugeridos |
| 9 ⏸️ | Chatbot clínico con RAG | Médico consulta la historia en lenguaje natural |

> Las fases 1 a 5 son el **núcleo**: sin ellas no hay sistema clínico. La IA (fases 8 y 9, **pospuestas**) es el diferenciador y se conectará sobre ese núcleo cuando se retome.
