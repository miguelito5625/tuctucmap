# Contexto del Proyecto: TucTuc Map (Monorepo)

Esta es una guía detallada de la arquitectura, stack tecnológico y contexto general para la plataforma TucTuc Map. Debes leer esto antes de planificar o realizar cambios.

## Estructura del Repositorio
Este proyecto es un monorepo que contiene tres sistemas principales. Nunca modifiques archivos fuera del entorno correspondiente sin verificar el impacto global.

### 1. `/databaseTuctucApp` (Backend & Base de Datos)
- **Propósito:** Definición de esquemas, políticas de seguridad y scripts administrativos.
- **Base de Datos:** PostgreSQL en Supabase.
- **Archivos Clave:**
  - `db.sql`: Define las tablas principales (`profiles`, `driver_subscriptions`, `driver_locations`, `trips`) y las reglas de seguridad (RLS - Row Level Security).
  - Script en Node.js (`run_sql.js`): Sirve para ejecutar sentencias SQL directamente en Supabase desde la terminal. Uso: `node run_sql.js "TU_SENTENCIA_SQL"` (ej. `node run_sql.js "SELECT * FROM public.profiles;"`).
- **Estructura de Datos Importante:**
  - `profiles`: Contiene la columna `role` (passenger, driver, admin) y estado de aprobación para conductores.
  - `trips`: Estados del viaje (`requested`, `accepted`, `completed`, `cancelled`).

### 2. `/tuctucMap` (App Móvil para Pasajeros y Conductores)
- **Propósito:** Aplicación nativa Android usada por los clientes y los conductores de "toritos".
- **Lenguaje & UI:** Kotlin, Jetpack Compose (Material 3).
- **Dependencias Clave (build.gradle.kts):**
  - **Arquitectura Híbrida de Backend (Supabase + Firebase):**
    - **Supabase (PostgreSQL & Auth):** Encargado de la Autenticación, perfiles de usuario, estado de aprobación de conductores, historial de viajes completados y suscripciones. Se utiliza para datos relacionales, persistentes y seguros.
    - **Firebase Realtime Database:** Encargado estrictamente del **estado efímero y en tiempo real**. Se usa para la transmisión en vivo de las coordenadas de los conductores activos (`driver_locations`) y el ciclo de vida temporal de un viaje activo (solicitud `requestTrip`, observación y cambios de estado rápidos).
  - **Mapas y Geolocalización:** OSMDroid para renderizado de mapas y Play Services Location para rastreo.
- **Arquitectura Interna (`app/src/main/java/com/example/tuctucmap/`):**
  - `data/`: Repositorios y conexión a Supabase (`SupabaseRepository.kt`).
  - `service/`: Servicios en background. Vital: `LocationTrackingService` mantiene actualizadas las coordenadas del conductor.
  - `ui/`: Navegación y pantallas divididas lógicamente (`screens/passenger/`, `screens/driver/`).
- **Convenciones de Interfaz (UI):**
  - **Edge-to-Edge:** El `MainActivity` hace uso de `enableEdgeToEdge()`. TODAS las pantallas que incluyan `TextFields` deben implementar el modificador `.imePadding()` y un scroll (`.verticalScroll()`) para evitar que el teclado las oculte.

### 3. `/tuctucMapDashboard` (Panel Administrativo Web)
- **Propósito:** Interfaz de escritorio para que los administradores aprueben conductores y monitoreen la plataforma.
- **Stack Tecnológico:** Angular (v22), Angular Material, RxJS.
- **Gestión de Dependencias:** NPM (`package.json`, `angular.json`).
- **Autenticación:** Utiliza `@supabase/supabase-js` para conectarse a la misma base de datos.
- **Arquitectura Interna (`src/app/`):**
  - Estructura modular estándar de Angular: `core/`, `features/`, `services/`, `shared/`.

## Reglas de Agente (AI Rules)
1. **Analiza el entorno correcto:** Si se pide cambiar la interfaz web, ve a `/tuctucMapDashboard`. Si es sobre el flujo de viaje, ve a `/tuctucMap`. Si se pide agregar un nuevo estado de viaje, inicia modificando `/databaseTuctucApp/db.sql`.
2. **Convenciones UI Móvil:** Siempre asume diseño responsive y protección contra el teclado (IME Padding) en pantallas Compose.
3. **Manejo de Librerías:** No sugieras librerías nuevas (como Retrofit o Mapbox) si el stack actual (Supabase SDK, OSMDroid, Firebase) ya resuelve el problema.
4. **Respetar RLS:** Cualquier nueva tabla en Supabase debe ir acompañada de su correspondiente política de Row Level Security (RLS) en `db.sql`.
5. **Documentación Continua:** Cualquier funcionalidad importante que sea modificada o agregada al proyecto, debe ser documentada inmediatamente en este archivo (`AGENTS.md`) para mantener el contexto general de la plataforma siempre actualizado.
6. **Compilación y Verificación Obligatoria:** Después de realizar cambios en la app de Android (`/tuctucMap`) o en el panel de Angular (`/tuctucMapDashboard`), el agente DEBE compilar el código respectivo (`./gradlew assembleDebug` o `npm run build`), verificar la salida en la terminal y solucionar proactivamente cualquier error de sintaxis o compilación **antes** de informar al usuario que la tarea ha terminado.
