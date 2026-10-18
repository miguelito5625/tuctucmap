# Contexto del Proyecto: TucTuc Map (Monorepo)

Esta es la guía central de arquitectura, normas de calidad y directrices operativas para la plataforma TucTuc Map. Aplica a todo agente o desarrollador que opere en este repositorio.

---

## 1. Estructura del Monorepo y Límites de Subsistemas

Este monorepo se compone de tres proyectos interconectados. **Nunca modifiques archivos fuera del entorno correspondiente sin verificar el impacto global:**

| Directorio | Propósito | Stack Tecnológico | Skill Especializado |
| :--- | :--- | :--- | :--- |
| [`/databaseTuctucApp`](../databaseTuctucApp) | Backend, esquema relacional, scripts administrativos y RLS | PostgreSQL en Supabase, Node.js (`pg`) | [`tuctuc-database`](./skills/tuctuc-database/SKILL.md) |
| [`/tuctucMap`](../tuctucMap) | Aplicación móvil nativa para pasajeros y conductores de toritos | Kotlin, Jetpack Compose (M3), OSMDroid, Firebase RTDB, Supabase | [`tuctuc-android`](./skills/tuctuc-android/SKILL.md) |
| [`/tuctucMapDashboard`](../tuctucMapDashboard) | Panel administrativo web para gestión de flota, aprobaciones y finanzas | Angular (v22), Angular Material, Leaflet, Supabase JS, Firebase RTDB | [`tuctuc-dashboard`](./skills/tuctuc-dashboard/SKILL.md) |

---

## 2. Skills de Trabajo Disponibles (`.agents/skills/`)

Para optimizar el uso de tokens y mantener procedimientos estandarizados, este proyecto cuenta con runbooks especializados en `.agents/skills/`:

1. **[`tuctuc-android`](./skills/tuctuc-android/SKILL.md):**
   - Convenciones Jetpack Compose, Edge-to-Edge (`.imePadding()`, `.verticalScroll()`).
   - Política de tiles OSMDroid (User-Agent y caché aislada `"TucTucOsmLive"` para evitar HTTP 403).
   - Animación de pasajero saludando (`PassengerLocationOverlay`).
   - Telemetría adaptativa GPS (desplazamiento >= 8m o heartbeat de 45s, Haversine, TTL 3 min).
   - Ciclo de vida y bloqueo de suscripciones del conductor.
   - Compilación y verificación con Gradle (`.\gradlew.bat assembleDebug`).

2. **[`tuctuc-dashboard`](./skills/tuctuc-dashboard/SKILL.md):**
   - Panel Angular 22 con Standalone Components y Angular Material.
   - Seguridad: requerimiento estricto de `role = 'admin'` en `profiles` para acceso y RLS.
   - Mapa de flota interactivo con Leaflet y sincronización en vivo con Firebase (`driver_locations`).
   - Módulo financiero de cobranza, KPIs y botón de contacto por WhatsApp.
   - Compilación y verificación con NPM (`npm run build`).

3. **[`tuctuc-database`](./skills/tuctuc-database/SKILL.md):**
   - Tablas maestras: `profiles`, `trips`, `driver_subscriptions`, `driver_locations`.
   - Principios mandatorios de Row Level Security (RLS) en PostgreSQL.
   - Ejecución segura de sentencias SQL mediante `node run_sql.js "<SQL>"`.
   - Sincronización obligatoria de cambios en `databaseTuctucApp/db.sql`.

4. **[`tuctuc-git-sync`](./skills/tuctuc-git-sync/SKILL.md):**
   - Protocolo de versionamiento y push automático a GitHub tras agregar o modificar funciones.
   - Mensajes semánticos, prevención de fuga de credenciales y sincronización remota con `origin/main`.

---

## 3. Reglas Obligatorias del Agente (AI Rules)

Las siguientes 7 reglas tienen carácter mandatorio e ineludible en toda tarea:

1. **Analizar el entorno correcto:**
   - Si la tarea involucra la web administrativa, trabajar en `tuctucMapDashboard/`.
   - Si involucra la app de toritos o pasajeros, trabajar en `tuctucMap/`.
   - Si involucra estados de viaje o entidades de persistencia, iniciar en `databaseTuctucApp/db.sql`.
2. **Convenciones UI Móvil:**
   - Mantener soporte Edge-to-Edge.
   - TODA pantalla Compose con campos de texto (`TextField`) debe implementar `.imePadding()` y `.verticalScroll()`.
3. **Disciplina en Librerías:**
   - No agregar dependencias innecesarias o redundantes (como Retrofit o Mapbox). El stack consolidado (Supabase SDK, OSMDroid, Firebase) cubre todos los requisitos.
4. **Respetar RLS:**
   - Cualquier nueva tabla o columna creada en Supabase debe ir acompañada de sus políticas de seguridad RLS correspondientes en `databaseTuctucApp/db.sql`.
5. **Documentación Continua:**
   - Cada funcionalidad relevante agregada o modificada debe ser documentada oportunamente en las directrices de `.agents/` para mantener el contexto del equipo al día.
6. **Compilación y Verificación Obligatoria:**
   - Tras editar código en `tuctucMap/` o `tuctucMapDashboard/`, el agente **DEBE** compilar (`.\gradlew.bat assembleDebug` o `npm run build`), verificar la salida y resolver proactivamente cualquier error antes de finalizar la respuesta.
7. **Sincronización y Push Obligatorio a GitHub:**
   - Cada vez que el usuario solicite agregar o modificar funciones en cualquiera de los proyectos, tras verificar la compilación exitosa, el agente **DEBE** preparar los cambios (`git add`), crear el commit semántico (`git commit -m "..."`) y hacer push a GitHub (`git push origin <rama>`), siguiendo el runbook [`tuctuc-git-sync`](./skills/tuctuc-git-sync/SKILL.md).


---

## 4. Documentos de Análisis y Planes Estratégicos
- [`MEJORAS_TUCTUCMAP.md`](../MEJORAS_TUCTUCMAP.md): Hoja de ruta técnica, optimizaciones GPS/batería, visualización de distancias, enlaces GPS en WhatsApp y tarifarios referenciales.
- [`MEJORAS_TUCTUCMAP_DASHBOARD.md`](../MEJORAS_TUCTUCMAP_DASHBOARD.md): Cobranza y planes de suscripción vía WhatsApp, mapa de flota en vivo y analítica de recaudación.
