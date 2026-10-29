# Graph Report - tuctucmap  (2026-10-29)

## Corpus Check
- 105 files · ~107,493 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 963 nodes · 1507 edges · 69 communities (51 shown, 18 thin omitted)
- Extraction: 99% EXTRACTED · 1% INFERRED · 0% AMBIGUOUS · INFERRED: 12 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `0248faec`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- SupabaseService
- DriverReportsScreen.kt
- PassengerMapScreen.kt
- tuctucMapDashboard
- SupabaseRepository.kt
- LocationTrackingService
- Informe Técnico y Financiero: Análisis de Costos Cloud (Supabase vs. Firebase) y Rentabilidad de TucTuc Map
- Política de Privacidad y Deslinde de Responsabilidad de TucTuc Map
- DriversComponent
- Vistas y Navegación (Jetpack Compose)
- dependencies
- devDependencies
- SecurePrefs
- db_config.js
- 2.1 Problemas Comprobados (Evidencia en Código)
- SubscriptionsComponent
- AppDrawer.kt
- Screen
- SupabaseRepository
- db.sql
- databaseTuctucApp/package.json
- 3. Credenciales Críticas e Infraestructura que Debes Configurar
- Plan de Monetización y Negocio: TucTuc Map App
- FleetMapComponent
- 3. Protocolo de Ejecución Paso a Paso
- Skill: Prompt Engineer & Asesor Técnico (TucTuc Map)
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
- AppNavigation
- Contexto del Proyecto: TucTuc Map (Monorepo)
- gradlew
- ExampleInstrumentedTest
- ExampleUnitTest
- rules/graphify.md
- environment.example.ts
- public.app_config
- public.subscription_payment_requests
- workflows/graphify.md
- scripts
- tuctucMapDashboard/package.json
- @angular/cdk
- @angular/core
- @angular/platform-browser
- rxjs
- tslib
- @supabase/supabase-js
- @angular/material

## God Nodes (most connected - your core abstractions)
1. `SupabaseRepository` - 99 edges
2. `SupabaseService` - 49 edges
3. `LocationTrackingService` - 29 edges
4. `AppNavigation()` - 26 edges
5. `DriversComponent` - 24 edges
6. `SecurePrefs` - 20 edges
7. `Screen` - 20 edges
8. `Driver` - 14 edges
9. `FleetMapComponent` - 14 edges
10. `PassengerMapScreen()` - 13 edges

## Surprising Connections (you probably didn't know these)
- `AppNavigation()` --calls--> `DriverMapFullscreenScreen()`  [INFERRED]
  tuctucMap/app/src/main/java/com/example/tuctucmap/ui/Navigation.kt → tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/driver/ActiveDriversMapView.kt
- `AppNavigation()` --calls--> `DriverHomeScreen()`  [INFERRED]
  tuctucMap/app/src/main/java/com/example/tuctucmap/ui/Navigation.kt → tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/driver/DriverHomeScreen.kt
- `AppNavigation()` --calls--> `DriverProfileScreen()`  [INFERRED]
  tuctucMap/app/src/main/java/com/example/tuctucmap/ui/Navigation.kt → tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/driver/DriverProfileScreen.kt
- `AppNavigation()` --calls--> `DriverReportsScreen()`  [INFERRED]
  tuctucMap/app/src/main/java/com/example/tuctucmap/ui/Navigation.kt → tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/driver/DriverReportsScreen.kt
- `AppNavigation()` --calls--> `DriverRouteToPassengerScreen()`  [INFERRED]
  tuctucMap/app/src/main/java/com/example/tuctucmap/ui/Navigation.kt → tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/driver/DriverRouteToPassengerScreen.kt

## Import Cycles
- None detected.

## Communities (69 total, 18 thin omitted)

### Community 0 - "SupabaseService"
Cohesion: 0.05
Nodes (23): Injectable, leaflet, authGuard(), AppConfig, FinancialKPIs, LiveDriverLocation, PassengerProfile, PaymentMethod (+15 more)

### Community 1 - "DriverReportsScreen.kt"
Cohesion: 0.20
Nodes (11): DailyReportGroup, DriverReportsPeriodFilter, CUSTOM_RANGE, TODAY, YESTERDAY, DriverReportsScreen(), SelectableDates, DriverWeeklyBarChart() (+3 more)

### Community 2 - "PassengerMapScreen.kt"
Cohesion: 0.08
Nodes (29): android, Canvas, Drawable, MapView, MyLocationNewOverlay, org, OsrmRoutingService, RouteResult (+21 more)

### Community 3 - "tuctucMapDashboard"
Cohesion: 0.05
Nodes (40): src/material-theme.scss, src/styles.css, build, serve, test, builder, configurations, defaultConfiguration (+32 more)

### Community 4 - "SupabaseRepository.kt"
Cohesion: 0.06
Nodes (24): DatabaseError, DataSnapshot, MutableData, SessionStatus, AppConfig, calculateRemainingDays(), Driver, ServerClock (+16 more)

### Community 5 - "LocationTrackingService"
Cohesion: 0.09
Nodes (20): FusedLocationProviderClient, IBinder, Intent, Job, Location, LocationResult, Notification, Service (+12 more)

### Community 6 - "Informe Técnico y Financiero: Análisis de Costos Cloud (Supabase vs. Firebase) y Rentabilidad de TucTuc Map"
Cohesion: 0.06
Nodes (35): 10. Fuentes Oficiales Consultadas, 1. Resumen Ejecutivo, 2.1 Base de Datos Relacional y Lógica deServidor (`databaseTuctucApp/db.sql`), 2.2 Telemetría GPS en Tiempo Real y Despacho de Viajes (`LocationTrackingService.kt` y `SupabaseRepository.kt`), 2.3 Autenticación (`SupabaseRepository.kt`), 2.4 Almacenamiento de Archivos (Cloud Storage), 2.5 Notificaciones y Alertas de Viaje (`LocationTrackingService.kt`, líneas 247–310), 2.6 Mapas y Ruteo Vial (`MapTileUtils.kt`, `OsrmRoutingService.kt`, `fleet-map.component.ts`) (+27 more)

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
Nodes (15): @angular/animations, @angular/common, @angular/compiler, @angular/forms, @angular/router, leaflet, dependencies, @angular/animations (+7 more)

### Community 11 - "devDependencies"
Cohesion: 0.13
Nodes (15): @angular/build, @angular/cli, @angular/compiler-cli, jsdom, prettier, devDependencies, @angular/build, @angular/cli (+7 more)

### Community 12 - "SecurePrefs"
Cohesion: 0.18
Nodes (8): Application, SecretKey, SessionManager, Context, SecurePrefs, Context, TucTucApplication, UserSession

### Community 13 - "db_config.js"
Cohesion: 0.10
Nodes (13): { Client }, { dbConfig }, dbConfig, fs, path, { Client }, { dbConfig }, fs (+5 more)

### Community 14 - "2.1 Problemas Comprobados (Evidencia en Código)"
Cohesion: 0.05
Nodes (36): 1.1 Diagnóstico Ejecutivo, 1.2 Calificación por Dimensión Técnica, 1. Resumen del Estado General de Optimización, 2.1 Problemas Comprobados (Evidencia en Código), 2.2 Mejoras Potenciales (Preventivas y Arquitectónicas), 2. Problemas Encontrados, Archivos Afectados y Evidencias, 3.1 Inventario Completo de Recursos Gráficos, 3.2 Resumen Cuantitativo del Impacto en Recursos Gráficos (+28 more)

### Community 15 - "SubscriptionsComponent"
Cohesion: 0.15
Nodes (5): SubscriptionPaymentRequest, ApproveRenewalDialogComponent, RejectRenewalDialogComponent, SubscriptionsComponent, Component

### Community 16 - "AppDrawer.kt"
Cohesion: 0.13
Nodes (21): ImageVector, Passenger, DrawerNavigationItem(), DriverDrawerItem, HISTORY, HOME, MAP, PROFILE (+13 more)

### Community 17 - "Screen"
Cohesion: 0.09
Nodes (25): Bundle, ComponentActivity, MainActivity, DriverHome, DriverLogin, DriverMapFullscreen, DriverPending, DriverProfile (+17 more)

### Community 18 - "SupabaseRepository"
Cohesion: 0.11
Nodes (10): AppConfig, kotlinx, Result, CompletedTripResponseDto, DriverTodaySummary, PaymentRequestInsertDto, SupabaseRepository, TripSummaryDto (+2 more)

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
Cohesion: 0.23
Nodes (3): FleetMapComponent, Component, ViewChild

### Community 24 - "3. Protocolo de Ejecución Paso a Paso"
Cohesion: 0.20
Nodes (10): 1. Activación Obligatoria, 2. Requisitos Previos al Commit (Garantía de Integridad), 3. Protocolo de Ejecución Paso a Paso, 4. Comunicación con el Usuario, Paso 1: Inspeccionar el Estado de Git, Paso 2: Preparar los Archivos (Staging), Paso 3: Crear el Commit Semántico, Paso 4: Validar Rama y Sincronizar Remoto (+2 more)

### Community 25 - "Skill: Prompt Engineer & Asesor Técnico (TucTuc Map)"
Cohesion: 0.12
Nodes (15): 1. Misión y Cuándo Activar Este Skill, 2. El Flujo de Trabajo en 3 Fases (El Ciclo Consultivo), 3. Plantillas de Comunicación para el Agente, 4. Ejemplos de Aplicación en TucTuc Map, 5. Reglas Mandatorias de Salida, Ejemplo 1: Solicitud imprecisa en la App Móvil, Ejemplo 2: Solicitud en el Dashboard, Fase 1: Reformulación y Enriquecimiento Técnico (Prompt Engineering) (+7 more)

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
Cohesion: 0.15
Nodes (13): parseIsoTimestamp(), DriverProfileScreen(), formatExpiryDate(), DriverHistoryPeriodFilter, CUSTOM_RANGE, TODAY, YESTERDAY, DriverTripCard() (+5 more)

### Community 39 - "TripInfo"
Cohesion: 0.21
Nodes (4): Flow, CompletedTripDto, RoutePoint, TripInfo

### Community 41 - "Skill: Angular Dashboard (TucTuc Map)"
Cohesion: 0.33
Nodes (5): 1. Stack Tecnológico & Dependencias, 2. Autenticación, Secretos y Seguridad Administrativa (RLS & XSS), 3. Módulos y Funcionalidades Clave, 4. Verificación y Compilación Obligatoria, Skill: Angular Dashboard (TucTuc Map)

### Community 42 - "AppNavigation"
Cohesion: 0.13
Nodes (11): AppNavigation(), Driver, resolveDriverScreen(), DriverLoginScreen(), DriverPendingScreen(), DriverRegisterScreen(), DriverSubscriptionExpiredScreen(), PassengerLoginScreen() (+3 more)

### Community 44 - "Contexto del Proyecto: TucTuc Map (Monorepo)"
Cohesion: 0.40
Nodes (5): 1. Estructura del Monorepo y Límites de Subsistemas, 2. Skills de Trabajo Disponibles (`.agents/skills/`), 3. Reglas Obligatorias del Agente (AI Rules), 4. Documentos de Análisis y Planes Estratégicos, Contexto del Proyecto: TucTuc Map (Monorepo)

### Community 45 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 63 - "scripts"
Cohesion: 0.33
Nodes (6): scripts, build, ng, start, test, watch

### Community 65 - "tuctucMapDashboard/package.json"
Cohesion: 0.40
Nodes (4): name, packageManager, private, version

## Knowledge Gaps
- **297 isolated node(s):** `{ Client }`, `{ dbConfig }`, `public.app_config`, `fs`, `path` (+292 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **18 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `SupabaseRepository` connect `SupabaseRepository` to `.observeDriverProfile`, `DriverReportsScreen.kt`, `PassengerMapScreen.kt`, `SupabaseRepository.kt`, `LocationTrackingService`, `DriverTripHistoryScreen.kt`, `TripInfo`, `AppNavigation`, `AppDrawer.kt`, `Screen`?**
  _High betweenness centrality (0.073) - this node is a cross-community bridge._
- **Why does `SupabaseService` connect `SupabaseService` to `SubscriptionsComponent`?**
  _High betweenness centrality (0.019) - this node is a cross-community bridge._
- **Why does `leaflet` connect `SupabaseService` to `tuctucMapDashboard`?**
  _High betweenness centrality (0.017) - this node is a cross-community bridge._
- **Are the 10 inferred relationships involving `AppNavigation()` (e.g. with `DriverMapFullscreenScreen()` and `DriverHomeScreen()`) actually correct?**
  _`AppNavigation()` has 10 INFERRED edges - model-reasoned connections that need verification._
- **What connects `{ Client }`, `{ dbConfig }`, `public.app_config` to the rest of the system?**
  _297 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `SupabaseService` be split into smaller, more focused modules?**
  _Cohesion score 0.05221518987341772 - nodes in this community are weakly interconnected._
- **Should `PassengerMapScreen.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.08313725490196078 - nodes in this community are weakly interconnected._