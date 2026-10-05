# Cómo trabajamos en HistoriaMed (backend)

El equipo usa **GitFlow**. Nadie sube cambios directamente a `main` ni a `develop`: todo entra por **Pull Request**
(PR), revisado por otro integrante y con la integración continua en verde.

## 1. Ramas

| Rama | Sale de | Se une a | Para qué |
|---|---|---|---|
| `main` | — | — | Versión estable. Cada versión lleva una etiqueta (`v0.9.0`, `v1.0.0`…). |
| `develop` | `main` | — | Integración: aquí se juntan las funcionalidades terminadas. |
| `feature/<nombre>` | `develop` | `develop` | Una tarea o funcionalidad. |
| `release/<versión>` | `develop` | `main` y `develop` | Preparar una versión: solo correcciones, nada nuevo. |
| `hotfix/<versión>` | `main` | `main` y `develop` | Corregir algo roto en producción sin esperar a `develop`. |

```
main     ──●──────────────────────●─────────●──
            \ v1.0.0             / v1.1.0   / v1.1.1
develop     ●──●──●──●──●──●──●─●─────────/─●──
               \     /   \      /         /
feature/...     ●──●      ●──●──        /
release/1.1.0 ···················●     /
hotfix/1.1.1  ·························●
```

**Nombres:** en minúsculas y con guiones, en español, describiendo la tarea: `feature/extraccion-ia`,
`feature/chatbot-medico`, `hotfix/1.0.1`. La versión sigue [SemVer](https://semver.org/lang/es/): `MAYOR.MENOR.PARCHE`.

## 2. Una funcionalidad (lo más común)

```bash
git checkout develop
git pull
git checkout -b feature/mi-tarea

# ...trabajar, con commits pequeños...
git add .
git commit -m "Descripción en español de lo que cambia"

git push -u origin feature/mi-tarea
```

En GitHub: **Pull Request de `feature/mi-tarea` hacia `develop`**. Otro integrante lo revisa. Cuando la integración
continua esté en verde y haya aprobación, se une ("Squash and merge" o "Merge") y se borra la rama.

Si `develop` avanzó mientras trabajabas:

```bash
git checkout develop && git pull
git checkout feature/mi-tarea
git merge develop        # resolver conflictos si los hay, probar y subir
```

## 3. Publicar una versión

```bash
git checkout develop && git pull
git checkout -b release/1.1.0
# solo correcciones y la actualización de documentación
git push -u origin release/1.1.0
```

1. PR de `release/1.1.0` hacia **`main`**. Al unirlo, se publica (despliegue automático).
2. Etiquetar la versión:
   ```bash
   git checkout main && git pull
   git tag -a v1.1.0 -m "Versión 1.1.0: resumen de lo que incluye"
   git push origin v1.1.0
   ```
3. PR de `release/1.1.0` hacia **`develop`**, para que las correcciones también queden ahí.

## 4. Corrección urgente en producción

```bash
git checkout main && git pull
git checkout -b hotfix/1.1.1
# corregir, probar
git push -u origin hotfix/1.1.1
```

PR hacia `main`, etiqueta `v1.1.1` como en el paso anterior, y PR hacia `develop`.

## 5. Antes de abrir un Pull Request

```bash
./mvnw test          # en Windows: .\mvnw.cmd test
```

Y además:

- **Nunca** subir `.env`, claves, contraseñas ni la carpeta `uploads/` (contiene documentos de pacientes).
- Solo datos ficticios de pacientes, también en capturas y pruebas.
- Código y mensajes en español; sufijos técnicos en inglés (`PacienteService`).
- La lógica de negocio y las decisiones están en `plan.md`. Reglas que no se discuten:
  - **Mínimo privilegio:** el ADMIN no ve datos clínicos y ADMISION no ve contenido clínico. Los permisos los decide
    el backend (`@PreAuthorize`), no el frontend.
  - **La historia clínica no se borra ni se reescribe:** las atenciones cerradas son inmutables (se corrigen con
    adendas); alergias y documentos se inactivan o anulan con un motivo.
  - **Todo acceso a datos clínicos se audita** (`AuditoriaService`).
  - **Las reglas clínicas críticas son deterministas** (alertas de triaje, alergias al recetar), nunca dependen de la IA.
  - Un módulo no usa el repositorio de otro, sino su servicio.
- **Migraciones de base de datos:** nunca modificar una que ya está en `develop`; crear una nueva
  (`V16__...sql`). Si dos ramas crean el mismo número, la que se une después debe renumerar la suya **antes** de
  unirse. Avisar en el PR qué número se usa.

## 6. Mensajes de commit

Una línea de título en español, en presente y que diga **qué cambia** (máximo unos 70 caracteres). Si hace falta,
una línea en blanco y el porqué.

```
Revisión de documentos: validar que la fecha del examen no sea futura

El formulario aceptaba fechas futuras y el laboratorio quedaba mal ordenado.
```

## 7. Protección de ramas (lo configura el dueño del repositorio)

En GitHub → **Settings → Branches → Add branch ruleset** para `main` y para `develop`:

- Exigir Pull Request antes de unir, con al menos **1 aprobación**.
- Exigir que pase la verificación («Pruebas del backend»).
- Impedir el push directo y el push forzado.
