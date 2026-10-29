# Graph Report - tuctucmap  (2026-10-28)

## Corpus Check
- 105 files · ~102,095 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 950 nodes · 1472 edges · 70 communities (51 shown, 19 thin omitted)
- Extraction: 99% EXTRACTED · 1% INFERRED · 0% AMBIGUOUS · INFERRED: 12 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `b8161585`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- SupabaseService
- AppNavigation
- PassengerMapScreen.kt
- tuctucMapDashboard
- DatabaseError
- LocationTrackingService
- Plan Maestro de Optimización de Rendimiento y Reducción de Costos Operativos
- Política de Privacidad y Deslinde de Responsabilidad de TucTuc Map
- DriversComponent
- Vistas y Navegación (Jetpack Compose)
- dependencies
- devDependencies
- SecurePrefs
- db_config.js
- Parte 2: Errores Lógicos y Mejoras de Funcionamiento Operativo
- SubscriptionsComponent
- AppDrawer.kt
- Análisis Financiero, de Costos Cloud y Rentabilidad: TucTuc Map
- SupabaseRepository
- db.sql
- databaseTuctucApp/package.json
- 3. Credenciales Críticas e Infraestructura que Debes Configurar
- Plan de Monetización y Negocio: TucTuc Map App
- FleetMapComponent
- 3. Protocolo de Ejecución Paso a Paso
- Color.kt
- TripsComponent
- 2. Flujo de Ejecución Paso a Paso
- Skill: Android App (TucTuc Map)
- TucTucMap Dashboard — Plan de Implementación
- ShellComponent
- BusinessConfigComponent
- .observeDriverProfile
- TucTucMap Dashboard — Tasks
- TuctucMapDashboard
- AGENTS.md
- Skill: Base de Datos & Backend (Supabase PostgreSQL)
- App
- DriverTripHistoryScreen.kt
- TripInfo
- PassengerDestinationPreferences
- Skill: Angular Dashboard (TucTuc Map)
- scripts
- Transaction
- Contexto del Proyecto: TucTuc Map (Monorepo)
- gradlew
- ExampleInstrumentedTest
- ExampleUnitTest
- tuctucMapDashboard/package.json
- rules/graphify.md
- environment.example.ts
- public.app_config
- public.subscription_payment_requests
- workflows/graphify.md
- @angular/cdk
- @angular/common
- @angular/core
- @angular/platform-browser
- DataSnapshot
- tslib
- @supabase/supabase-js
- @angular/material

## God Nodes (most connected - your core abstractions)
1. `SupabaseRepository` - 97 edges
2. `SupabaseService` - 49 edges
3. `LocationTrackingService` - 26 edges
4. `AppNavigation()` - 26 edges
5. `DriversComponent` - 24 edges
6. `Screen` - 20 edges
7. `SecurePrefs` - 19 edges
8. `Driver` - 14 edges
9. `PassengerMapScreen()` - 13 edges
10. `FleetMapComponent` - 12 edges

## Surprising Connections (you probably didn't know these)
- `AppNavigation()` --calls--> `DriverMapFullscreenScreen()`  [INFERRED]
  tuctucMap/app/src/main/java/com/example/tuctucmap/ui/Navigation.kt → tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/driver/ActiveDriversMapView.kt
- `AppNavigation()` --calls--> `DriverHomeScreen()`  [INFERRED]
  tuctucMap/app/src/main/java/com/example/tuctucmap/ui/Navigation.kt → tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/driver/DriverHomeScreen.kt
- `AppNavigation()` --calls--> `DriverLoginScreen()`  [INFERRED]
  tuctucMap/app/src/main/java/com/example/tuctucmap/ui/Navigation.kt → tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/driver/DriverLoginScreen.kt
- `AppNavigation()` --calls--> `DriverPendingScreen()`  [INFERRED]
  tuctucMap/app/src/main/java/com/example/tuctucmap/ui/Navigation.kt → tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/driver/DriverPendingScreen.kt
- `AppNavigation()` --calls--> `DriverRegisterScreen()`  [INFERRED]
  tuctucMap/app/src/main/java/com/example/tuctucmap/ui/Navigation.kt → tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/driver/DriverRegisterScreen.kt

## Import Cycles
- None detected.

## Communities (70 total, 19 thin omitted)

### Community 0 - "SupabaseService"
Cohesion: 0.05
Nodes (23): Injectable, leaflet, authGuard(), AppConfig, FinancialKPIs, LiveDriverLocation, PassengerProfile, PaymentMethod (+15 more)

### Community 1 - "AppNavigation"
Cohesion: 0.05
Nodes (50): Bundle, ComponentActivity, SessionStatus, AppConfig, calculateRemainingDays(), Driver, parseIsoTimestamp(), ServerClock (+42 more)

### Community 2 - "PassengerMapScreen.kt"
Cohesion: 0.06
Nodes (35): android, Bitmap, Canvas, Drawable, MapView, MyLocationNewOverlay, org, DriverFarePreferences (+27 more)

### Community 3 - "tuctucMapDashboard"
Cohesion: 0.05
Nodes (40): src/material-theme.scss, src/styles.css, build, serve, test, builder, configurations, defaultConfiguration (+32 more)

### Community 4 - "DatabaseError"
Cohesion: 0.13
Nodes (7): DatabaseError, Flow, ValueEventListener, ValueEventListener, ValueEventListener, ValueEventListener, ValueEventListener

### Community 5 - "LocationTrackingService"
Cohesion: 0.12
Nodes (17): FusedLocationProviderClient, IBinder, Intent, Job, Location, LocationResult, Notification, Service (+9 more)

### Community 6 - "Plan Maestro de Optimización de Rendimiento y Reducción de Costos Operativos"
Cohesion: 0.05
Nodes (36): 1. Diagnóstico Ejecutivo y Visión General, 2.1. Memoria RAM y Evitación de Pausas del Garbage Collector (GC Churn), 2.2. Optimización de Renderizado en Compose y OSMDroid (`AndroidView`), 2.3. Batería y CPU en el Servicio de Rastreo en Segundo Plano (`LocationTrackingService`), 2.4. Reducción de Tamaño de APK y Huella de Instalación, 2. Optimización de la App Móvil Android (`/tuctucMap`) para Gama Media y Baja, 3.1. Renderizado de Flota en Vivo con Leaflet (`fleet-map.component.ts`), 3.2. Paginación y Filtrado en Base de Datos para Viajes e Historial (`trips.component.ts`) (+28 more)

### Community 7 - "Política de Privacidad y Deslinde de Responsabilidad de TucTuc Map"
Cohesion: 0.06
Nodes (32): 1. Resumen de Ajustes Técnicos Realizados en el Proyecto, 2. Datos del Almacén de Claves de Producción (*Keystore*), 3. Generación del Paquete de Producción (`.aab`), 4.1 Política de Privacidad, 4.2 Acceso a la Aplicación, 4.3 Anuncios, 4.4 Clasificación de Contenido, 4.5 Público Objetivo y Contenido (+24 more)

### Community 8 - "DriversComponent"
Cohesion: 0.11
Nodes (6): Driver, DriverStatus, DriversComponent, SubscriptionDialogComponent, SubscriptionDialogData, Component

### Community 9 - "Vistas y Navegación (Jetpack Compose)"
Cohesion: 0.07
Nodes (29): Configuración del Proyecto y Dependencias, Manifiesto de Android y Permisos, Manual Verification, Modelos y Repositorio de Datos (Clean Architecture), [MODIFY] [AndroidManifest.xml](file:///c:/Users/exbinario/AndroidStudioProjects/tuctucMap/app/src/main/AndroidManifest.xml), [MODIFY] [build.gradle.kts (app)](file:///c:/Users/exbinario/AndroidStudioProjects/tuctucMap/app/build.gradle.kts), [MODIFY] [build.gradle.kts (root)](file:///c:/Users/exbinario/AndroidStudioProjects/tuctucMap/build.gradle.kts), [MODIFY] [libs.versions.toml](file:///c:/Users/exbinario/AndroidStudioProjects/tuctucMap/gradle/libs.versions.toml) (+21 more)

### Community 10 - "dependencies"
Cohesion: 0.13
Nodes (15): @angular/animations, @angular/compiler, @angular/forms, @angular/router, leaflet, rxjs, dependencies, @angular/animations (+7 more)

### Community 11 - "devDependencies"
Cohesion: 0.13
Nodes (15): @angular/build, @angular/cli, @angular/compiler-cli, jsdom, prettier, devDependencies, @angular/build, @angular/cli (+7 more)

### Community 12 - "SecurePrefs"
Cohesion: 0.18
Nodes (8): Application, SecretKey, SessionManager, Context, SecurePrefs, Context, TucTucApplication, UserSession

### Community 13 - "db_config.js"
Cohesion: 0.10
Nodes (13): { Client }, { dbConfig }, dbConfig, fs, path, { Client }, { dbConfig }, fs (+5 more)

### Community 14 - "Parte 2: Errores Lógicos y Mejoras de Funcionamiento Operativo"
Cohesion: 0.09
Nodes (21): 1.1. [CRÍTICO] Escalación de Privilegios y Auto-Renovación Gratuita en Supabase (RLS), 1.2. [CRÍTICO] Evasión de Vencimiento Cambiando la Fecha del Teléfono (Clock Tampering), 1.3. [CRÍTICO] Contraseña Maestra de Base de Datos Expuesta en el Repositorio, 1.4. [ALTO] Firebase Realtime Database Abierta sin Reglas de Validación ni Autenticación, 1.5. [ALTO] Exposición Pública de Todos los Perfiles en `public.profiles`, 1.6. [MEDIO] Inyección HTML / XSS en los Popups del Mapa del Dashboard Angular, 1.7. [MEDIO] Ofuscación R8 Desactivada y Respaldo en Texto Plano en Android, 2.1. [BUG CRÍTICO] Un Solo Conductor que Presione "Ignorar (X)" Cancela el Viaje para TODOS los Demás Conductores y para el Pasajero (+13 more)

### Community 15 - "SubscriptionsComponent"
Cohesion: 0.15
Nodes (5): SubscriptionPaymentRequest, ApproveRenewalDialogComponent, RejectRenewalDialogComponent, SubscriptionsComponent, Component

### Community 16 - "AppDrawer.kt"
Cohesion: 0.12
Nodes (21): ImageVector, Passenger, DrawerNavigationItem(), DriverDrawerItem, HISTORY, HOME, MAP, PROFILE (+13 more)

### Community 17 - "Análisis Financiero, de Costos Cloud y Rentabilidad: TucTuc Map"
Cohesion: 0.07
Nodes (28): 1. [PRIORIDAD #1] Cambiar `ValueEventListener` por `ChildEventListener` en `observeActiveLocations()`, 1. Resumen Ejecutivo, 2. Auditoría Arquitectónica de Servicios que Generan Costos, 2. [PRIORIDAD #2] Desactivar el Listener de Mapa Cuando la App del Pasajero Pasa a Segundo Plano, 3.1. Precios Oficiales de Supabase, 3.2. Precios Oficiales de Google Firebase, 3. Precios Oficiales Vigentes: Supabase vs. Firebase, 3. [PRIORIDAD #3] Limpieza Automática de `route_points` Antiguos en PostgreSQL (`pg_cron`) (+20 more)

### Community 18 - "SupabaseRepository"
Cohesion: 0.09
Nodes (11): kotlinx, Result, CompletedTripResponseDto, DriverTodaySummary, PaymentRequestInsertDto, SupabaseRepository, TripSummaryDto, UpdateDriverProfileDto (+3 more)

### Community 19 - "db.sql"
Cohesion: 0.14
Nodes (8): auth.users, public.app_config, public.driver_subscriptions, public.handle_profile_insert_security(), public.profiles, public.sync_app_config_fields(), trg_protect_profile_sensitive_columns, public.protect_profile_sensitive_columns

### Community 20 - "databaseTuctucApp/package.json"
Cohesion: 0.12
Nodes (15): author, dependencies, pg, @supabase/supabase-js, description, @supabase/supabase-js, keywords, license (+7 more)

### Community 21 - "3. Credenciales Críticas e Infraestructura que Debes Configurar"
Cohesion: 0.18
Nodes (10): 1. Archivos de Secretos Locales (Excluidos de GitHub en `.gitignore`), 2. Datos Comerciales que Debes Reemplazar (Sin Recompilar la App), 3. Credenciales Críticas e Infraestructura que Debes Configurar, 4. Resumen deDóndeReemplazar Cada Secreto si Creas un Nuevo Entorno, A. Rotar la Contraseña de Base de Datos en Supabase (¡Prioridad Alta!), B. Publicar las Reglas de Seguridad en Firebase Realtime Database, C. Restringir las API Keys de Google / Firebase (`google-services.json`), ¿Cómo actualizar estos datos en un segundo? (+2 more)

### Community 22 - "Plan de Monetización y Negocio: TucTuc Map App"
Cohesion: 0.18
Nodes (10): 1. Modelo SaaS: Suscripción para Conductores (Recomendado), 2. Publicidad Geolocalizada e Hiperlocal (Pasajeros), 3. Modelo B2B: Portal para Sindicatos y Asociaciones de Tuc Tucs, 4. Funciones Premium de Pasajeros (Microtransacciones), Mecánica:, Mecánica:, Mecánica:, Mecánica: (+2 more)

### Community 23 - "FleetMapComponent"
Cohesion: 0.26
Nodes (3): FleetMapComponent, Component, ViewChild

### Community 24 - "3. Protocolo de Ejecución Paso a Paso"
Cohesion: 0.20
Nodes (10): 1. Activación Obligatoria, 2. Requisitos Previos al Commit (Garantía de Integridad), 3. Protocolo de Ejecución Paso a Paso, 4. Comunicación con el Usuario, Paso 1: Inspeccionar el Estado de Git, Paso 2: Preparar los Archivos (Staging), Paso 3: Crear el Commit Semántico, Paso 4: Validar Rama y Sincronizar Remoto (+2 more)

### Community 25 - "Color.kt"
Cohesion: 0.18
Nodes (3): DriverPendingScreen(), DriverRegisterScreen(), DriverSubscriptionExpiredScreen()

### Community 26 - "TripsComponent"
Cohesion: 0.24
Nodes (3): TripsComponent, Component, ViewChild

### Community 27 - "2. Flujo de Ejecución Paso a Paso"
Cohesion: 0.22
Nodes (9): 2. Flujo de Ejecución Paso a Paso, A. Nombre de la versión, B. Notas de la versión (Formato Play Store), Paso 1: Determinar la Marca de Tiempo del Último `.aab`, Paso 2: Extraer los Commits Realizados desde esa Fecha, Paso 3: Incrementar la Versión en `tuctucMap/app/build.gradle.kts`, Paso 4: Compilar el Paquete Firmado Oficial (`.aab`), Paso 5: Redactar las Notas de Lanzamiento para Google Play Console (+1 more)

### Community 28 - "Skill: Android App (TucTuc Map)"
Cohesion: 0.25
Nodes (8): 1. Stack Tecnológico & Dependencias, 2. Convenciones Críticas de UI & Jetpack Compose, 3. Mapas, Tiles y Overlays (OSMDroid), 4. Telemetría GPS, Geocerca y Alertas en Segundo Plano, 5. Reglas de Negocio del Conductor y Suscripciones Anti-Fraude, 6. Secretos (`BuildConfig`), Persistencia de Sesión y Ofuscación R8, 7. Verificación y Compilación Obligatoria, Skill: Android App (TucTuc Map)

### Community 29 - "TucTucMap Dashboard — Plan de Implementación"
Cohesion: 0.25
Nodes (7): Decisiones Técnicas, Estructura de Archivos a Crear/Modificar, Flujo de Datos, Módulos de Angular Material a Importar, Objetivo, TucTucMap Dashboard — Plan de Implementación, Verificación

### Community 33 - "TucTucMap Dashboard — Tasks"
Cohesion: 0.25
Nodes (7): App Wiring, Core & Config, Drivers Feature, Layout Shell, Styles, TucTucMap Dashboard — Tasks, Verification

### Community 34 - "TuctucMapDashboard"
Cohesion: 0.25
Nodes (7): Additional Resources, Building, Code scaffolding, Development server, Running end-to-end tests, Running unit tests, TuctucMapDashboard

### Community 35 - "AGENTS.md"
Cohesion: 0.33
Nodes (3): 1. Cuándo Activar este Skill, 3. Entregable al Usuario, Skill: Publicación y Generación de Versiones para Google Play Store

### Community 36 - "Skill: Base de Datos & Backend (Supabase PostgreSQL)"
Cohesion: 0.29
Nodes (6): 1. Arquitectura de Datos & Tablas Principales, 2. Row Level Security (RLS), Triggers y Funciones RPC, 3. Ejecución de Consultas y Modificaciones (`run_sql.js` + `.env`), 4. Buenas Prácticas de Migración, Procedimiento de Uso:, Skill: Base de Datos & Backend (Supabase PostgreSQL)

### Community 37 - "App"
Cohesion: 0.36
Nodes (4): App, appConfig, routes, Component

### Community 38 - "DriverTripHistoryScreen.kt"
Cohesion: 0.19
Nodes (10): DriverHistoryPeriodFilter, CUSTOM_RANGE, TODAY, YESTERDAY, DriverTripCard(), DriverTripHistoryScreen(), SelectableDates, Context (+2 more)

### Community 39 - "TripInfo"
Cohesion: 0.38
Nodes (3): CompletedTripDto, RoutePoint, TripInfo

### Community 41 - "Skill: Angular Dashboard (TucTuc Map)"
Cohesion: 0.33
Nodes (5): 1. Stack Tecnológico & Dependencias, 2. Autenticación, Secretos y Seguridad Administrativa (RLS & XSS), 3. Módulos y Funcionalidades Clave, 4. Verificación y Compilación Obligatoria, Skill: Angular Dashboard (TucTuc Map)

### Community 42 - "scripts"
Cohesion: 0.33
Nodes (6): scripts, build, ng, start, test, watch

### Community 43 - "Transaction"
Cohesion: 0.25
Nodes (6): MutableData, AcceptTripResult, AlreadyTaken, Error, Success, Transaction

### Community 44 - "Contexto del Proyecto: TucTuc Map (Monorepo)"
Cohesion: 0.40
Nodes (5): 1. Estructura del Monorepo y Límites de Subsistemas, 2. Skills de Trabajo Disponibles (`.agents/skills/`), 3. Reglas Obligatorias del Agente (AI Rules), 4. Documentos de Análisis y Planes Estratégicos, Contexto del Proyecto: TucTuc Map (Monorepo)

### Community 45 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 49 - "tuctucMapDashboard/package.json"
Cohesion: 0.40
Nodes (4): name, packageManager, private, version

## Knowledge Gaps
- **294 isolated node(s):** `{ Client }`, `{ dbConfig }`, `public.app_config`, `fs`, `path` (+289 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **19 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `SupabaseRepository` connect `SupabaseRepository` to `.observeDriverProfile`, `AppNavigation`, `DataSnapshot`, `PassengerMapScreen.kt`, `DatabaseError`, `LocationTrackingService`, `DriverTripHistoryScreen.kt`, `TripInfo`, `Transaction`, `AppDrawer.kt`, `Color.kt`?**
  _High betweenness centrality (0.071) - this node is a cross-community bridge._
- **Why does `SupabaseService` connect `SupabaseService` to `SubscriptionsComponent`?**
  _High betweenness centrality (0.020) - this node is a cross-community bridge._
- **Why does `leaflet` connect `SupabaseService` to `tuctucMapDashboard`?**
  _High betweenness centrality (0.017) - this node is a cross-community bridge._
- **Are the 10 inferred relationships involving `AppNavigation()` (e.g. with `DriverMapFullscreenScreen()` and `DriverHomeScreen()`) actually correct?**
  _`AppNavigation()` has 10 INFERRED edges - model-reasoned connections that need verification._
- **What connects `{ Client }`, `{ dbConfig }`, `public.app_config` to the rest of the system?**
  _294 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `SupabaseService` be split into smaller, more focused modules?**
  _Cohesion score 0.05221518987341772 - nodes in this community are weakly interconnected._
- **Should `AppNavigation` be split into smaller, more focused modules?**
  _Cohesion score 0.05222734254992319 - nodes in this community are weakly interconnected._