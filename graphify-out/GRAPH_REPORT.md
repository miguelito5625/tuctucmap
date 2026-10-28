# Graph Report - tuctucmap  (2026-10-28)

## Corpus Check
- 109 files · ~96,899 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 913 nodes · 1433 edges · 61 communities (49 shown, 12 thin omitted)
- Extraction: 99% EXTRACTED · 1% INFERRED · 0% AMBIGUOUS · INFERRED: 12 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- Dashboard Routes & Auth Guard
- Android Driver & App Config
- Android Map Graphics & Overlays
- Dashboard Theme & Styles
- Firebase Realtime Sync
- Android GPS Telemetry & Service
- Play Store Release & Docs
- Android Performance Optimization
- Dashboard Fleet Management
- Android Setup & Implementation
- Angular Core & CDK Modules
- Angular Build & Compiler Tools
- Android Security & Session Storage
- Database SQL Migration Scripts
- Security & Vulnerability Analysis
- Subscriptions Tuctucmapdashboard Features
- Appdrawer Java Components
- Java Supabaserepository Data
- Java Data Repository
- Sql Databasetuctucapp Public
- Databasetuctucapp Package Json
- Drivertriphistoryscreen Java Screens
- Agents Skills Tuctuc
- Fleet Map Tuctucmapdashboard
- Configuracion Secretos Requeridos
- Java Screens Driver
- Trips Tuctucmapdashboard Features
- Tuctuc Skill Agents
- Agents Skills Tuctuc
- Monetization Plan Para
- Tuctucmapdashboard Shared Layout
- Business Config Tuctucmapdashboard
- Java Passenger Data
- Agents Skills Tuctuc
- First Implementation Plan
- Task Tuctucmapdashboard Implementationsplans
- Readme Tuctucmapdashboard Running
- Tuctucmapdashboard Config Routes
- Tuctuc Database Skill
- Java Data Repository
- Passengerdestinationpreferences Java Data
- Agents Agents Del
- Driverfarepreferences Java Data
- Java Data Repository
- Java Data Repository
- Gradlew Script Die
- Exampleinstrumentedtest Androidtest Java
- Exampleunittest Test Java
- Graphify Rules Agents
- Graphify Workflows Agents
- Environment Tuctucmapdashboard Environments
- Public Config
- Public Subscription Payment

## God Nodes (most connected - your core abstractions)
1. `SupabaseRepository` - 96 edges
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

## Communities (61 total, 12 thin omitted)

### Community 0 - "Dashboard Routes & Auth Guard"
Cohesion: 0.05
Nodes (23): Injectable, leaflet, authGuard(), AppConfig, FinancialKPIs, LiveDriverLocation, PassengerProfile, PaymentMethod (+15 more)

### Community 1 - "Android Driver & App Config"
Cohesion: 0.05
Nodes (49): Bundle, ComponentActivity, AppConfig, calculateRemainingDays(), Driver, parseIsoTimestamp(), ServerClock, SubscriptionRenewalRequest (+41 more)

### Community 2 - "Android Map Graphics & Overlays"
Cohesion: 0.08
Nodes (32): android, Bitmap, Canvas, Drawable, MapView, MyLocationNewOverlay, org, OsrmRoutingService (+24 more)

### Community 3 - "Dashboard Theme & Styles"
Cohesion: 0.05
Nodes (40): src/material-theme.scss, src/styles.css, build, serve, test, builder, configurations, defaultConfiguration (+32 more)

### Community 4 - "Firebase Realtime Sync"
Cohesion: 0.09
Nodes (13): DatabaseError, DataSnapshot, Flow, MutableData, SessionStatus, DriverLocation, Transaction, ValueEventListener (+5 more)

### Community 5 - "Android GPS Telemetry & Service"
Cohesion: 0.12
Nodes (17): FusedLocationProviderClient, IBinder, Intent, Job, Location, LocationResult, Notification, Service (+9 more)

### Community 6 - "Play Store Release & Docs"
Cohesion: 0.06
Nodes (32): 1. Resumen de Ajustes Técnicos Realizados en el Proyecto, 2. Datos del Almacén de Claves de Producción (*Keystore*), 3. Generación del Paquete de Producción (`.aab`), 4.1 Política de Privacidad, 4.2 Acceso a la Aplicación, 4.3 Anuncios, 4.4 Clasificación de Contenido, 4.5 Público Objetivo y Contenido (+24 more)

### Community 7 - "Android Performance Optimization"
Cohesion: 0.06
Nodes (33): 1. Diagnóstico Ejecutivo y Visión General, 2.1. Memoria RAM y Evitación de Pausas del Garbage Collector (GC Churn), 2.2. Optimización de Renderizado en Compose y OSMDroid (`AndroidView`), 2.3. Batería y CPU en el Servicio de Rastreo en Segundo Plano (`LocationTrackingService`), 2.4. Reducción de Tamaño de APK y Huella de Instalación, 2. Optimización de la App Móvil Android (`/tuctucMap`) para Gama Media y Baja, 3.1. Renderizado de Flota en Vivo con Leaflet (`fleet-map.component.ts`), 3.2. Paginación y Filtrado en Base de Datos para Viajes e Historial (`trips.component.ts`) (+25 more)

### Community 8 - "Dashboard Fleet Management"
Cohesion: 0.11
Nodes (6): Driver, DriverStatus, DriversComponent, SubscriptionDialogComponent, SubscriptionDialogData, Component

### Community 9 - "Android Setup & Implementation"
Cohesion: 0.07
Nodes (29): Configuración del Proyecto y Dependencias, Manifiesto de Android y Permisos, Manual Verification, Modelos y Repositorio de Datos (Clean Architecture), [MODIFY] [AndroidManifest.xml](file:///c:/Users/exbinario/AndroidStudioProjects/tuctucMap/app/src/main/AndroidManifest.xml), [MODIFY] [build.gradle.kts (app)](file:///c:/Users/exbinario/AndroidStudioProjects/tuctucMap/app/build.gradle.kts), [MODIFY] [build.gradle.kts (root)](file:///c:/Users/exbinario/AndroidStudioProjects/tuctucMap/build.gradle.kts), [MODIFY] [libs.versions.toml](file:///c:/Users/exbinario/AndroidStudioProjects/tuctucMap/gradle/libs.versions.toml) (+21 more)

### Community 10 - "Angular Core & CDK Modules"
Cohesion: 0.07
Nodes (29): @angular/animations, @angular/cdk, @angular/common, @angular/compiler, @angular/core, @angular/forms, @angular/material, @angular/platform-browser (+21 more)

### Community 11 - "Angular Build & Compiler Tools"
Cohesion: 0.08
Nodes (25): @angular/build, @angular/cli, @angular/compiler-cli, jsdom, prettier, devDependencies, @angular/build, @angular/cli (+17 more)

### Community 12 - "Android Security & Session Storage"
Cohesion: 0.18
Nodes (8): Application, SecretKey, SessionManager, Context, SecurePrefs, Context, TucTucApplication, UserSession

### Community 13 - "Database SQL Migration Scripts"
Cohesion: 0.10
Nodes (13): { Client }, { dbConfig }, dbConfig, fs, path, { Client }, { dbConfig }, fs (+5 more)

### Community 14 - "Security & Vulnerability Analysis"
Cohesion: 0.09
Nodes (21): 1.1. [CRÍTICO] Escalación de Privilegios y Auto-Renovación Gratuita en Supabase (RLS), 1.2. [CRÍTICO] Evasión de Vencimiento Cambiando la Fecha del Teléfono (Clock Tampering), 1.3. [CRÍTICO] Contraseña Maestra de Base de Datos Expuesta en el Repositorio, 1.4. [ALTO] Firebase Realtime Database Abierta sin Reglas de Validación ni Autenticación, 1.5. [ALTO] Exposición Pública de Todos los Perfiles en `public.profiles`, 1.6. [MEDIO] Inyección HTML / XSS en los Popups del Mapa del Dashboard Angular, 1.7. [MEDIO] Ofuscación R8 Desactivada y Respaldo en Texto Plano en Android, 2.1. [BUG CRÍTICO] Un Solo Conductor que Presione "Ignorar (X)" Cancela el Viaje para TODOS los Demás Conductores y para el Pasajero (+13 more)

### Community 15 - "Subscriptions Tuctucmapdashboard Features"
Cohesion: 0.15
Nodes (5): SubscriptionPaymentRequest, ApproveRenewalDialogComponent, RejectRenewalDialogComponent, SubscriptionsComponent, Component

### Community 16 - "Appdrawer Java Components"
Cohesion: 0.14
Nodes (19): ImageVector, DrawerNavigationItem(), DriverDrawerItem, HISTORY, HOME, MAP, PROFILE, REPORTS (+11 more)

### Community 17 - "Java Supabaserepository Data"
Cohesion: 0.13
Nodes (8): kotlinx, PaymentRequestInsertDto, SupabaseRepository, TripSummaryDto, UpdateDriverProfileDto, UpdatePassengerProfileDto, DriverRegisterScreen(), PassengerTripHistoryScreen()

### Community 18 - "Java Data Repository"
Cohesion: 0.14
Nodes (4): com, Result, CompletedTripResponseDto, DriverTodaySummary

### Community 19 - "Sql Databasetuctucapp Public"
Cohesion: 0.15
Nodes (8): auth.users, public.app_config, public.driver_subscriptions, public.handle_profile_insert_security(), public.profiles, public.sync_app_config_fields(), trg_protect_profile_sensitive_columns, public.protect_profile_sensitive_columns

### Community 20 - "Databasetuctucapp Package Json"
Cohesion: 0.12
Nodes (15): author, dependencies, pg, @supabase/supabase-js, description, @supabase/supabase-js, keywords, license (+7 more)

### Community 21 - "Drivertriphistoryscreen Java Screens"
Cohesion: 0.19
Nodes (10): DriverHistoryPeriodFilter, CUSTOM_RANGE, TODAY, YESTERDAY, DriverTripCard(), DriverTripHistoryScreen(), SelectableDates, Context (+2 more)

### Community 22 - "Agents Skills Tuctuc"
Cohesion: 0.17
Nodes (12): 1. Cuándo Activar este Skill, 2. Flujo de Ejecución Paso a Paso, 3. Entregable al Usuario, A. Nombre de la versión, B. Notas de la versión (Formato Play Store), Paso 1: Determinar la Marca de Tiempo del Último `.aab`, Paso 2: Extraer los Commits Realizados desde esa Fecha, Paso 3: Incrementar la Versión en `tuctucMap/app/build.gradle.kts` (+4 more)

### Community 23 - "Fleet Map Tuctucmapdashboard"
Cohesion: 0.26
Nodes (3): FleetMapComponent, Component, ViewChild

### Community 24 - "Configuracion Secretos Requeridos"
Cohesion: 0.18
Nodes (10): 1. Archivos de Secretos Locales (Excluidos de GitHub en `.gitignore`), 2. Datos Comerciales que Debes Reemplazar (Sin Recompilar la App), 3. Credenciales Críticas e Infraestructura que Debes Configurar, 4. Resumen deDóndeReemplazar Cada Secreto si Creas un Nuevo Entorno, A. Rotar la Contraseña de Base de Datos en Supabase (¡Prioridad Alta!), B. Publicar las Reglas de Seguridad en Firebase Realtime Database, C. Restringir las API Keys de Google / Firebase (`google-services.json`), ¿Cómo actualizar estos datos en un segundo? (+2 more)

### Community 25 - "Java Screens Driver"
Cohesion: 0.18
Nodes (3): DriverLoginScreen(), DriverPendingScreen(), DriverSubscriptionExpiredScreen()

### Community 26 - "Trips Tuctucmapdashboard Features"
Cohesion: 0.24
Nodes (3): TripsComponent, Component, ViewChild

### Community 27 - "Tuctuc Skill Agents"
Cohesion: 0.22
Nodes (5): 1. Stack Tecnológico & Dependencias, 2. Autenticación, Secretos y Seguridad Administrativa (RLS & XSS), 3. Módulos y Funcionalidades Clave, 4. Verificación y Compilación Obligatoria, Skill: Angular Dashboard (TucTuc Map)

### Community 28 - "Agents Skills Tuctuc"
Cohesion: 0.20
Nodes (10): 1. Activación Obligatoria, 2. Requisitos Previos al Commit (Garantía de Integridad), 3. Protocolo de Ejecución Paso a Paso, 4. Comunicación con el Usuario, Paso 1: Inspeccionar el Estado de Git, Paso 2: Preparar los Archivos (Staging), Paso 3: Crear el Commit Semántico, Paso 4: Validar Rama y Sincronizar Remoto (+2 more)

### Community 29 - "Monetization Plan Para"
Cohesion: 0.22
Nodes (9): 1. Modelo SaaS: Suscripción para Conductores (Recomendado), 2. Publicidad Geolocalizada e Hiperlocal (Pasajeros), 3. Modelo B2B: Portal para Sindicatos y Asociaciones de Tuc Tucs, 4. Funciones Premium de Pasajeros (Microtransacciones), Mecánica:, Mecánica:, Mecánica:, Plan de Acción para Lanzamiento y Validación (MVP) (+1 more)

### Community 32 - "Java Passenger Data"
Cohesion: 0.25
Nodes (3): AppConfig, Passenger, PassengerHomeScreen()

### Community 33 - "Agents Skills Tuctuc"
Cohesion: 0.25
Nodes (8): 1. Stack Tecnológico & Dependencias, 2. Convenciones Críticas de UI & Jetpack Compose, 3. Mapas, Tiles y Overlays (OSMDroid), 4. Telemetría GPS, Geocerca y Alertas en Segundo Plano, 5. Reglas de Negocio del Conductor y Suscripciones Anti-Fraude, 6. Secretos (`BuildConfig`), Persistencia de Sesión y Ofuscación R8, 7. Verificación y Compilación Obligatoria, Skill: Android App (TucTuc Map)

### Community 34 - "First Implementation Plan"
Cohesion: 0.25
Nodes (7): Decisiones Técnicas, Estructura de Archivos a Crear/Modificar, Flujo de Datos, Módulos de Angular Material a Importar, Objetivo, TucTucMap Dashboard — Plan de Implementación, Verificación

### Community 35 - "Task Tuctucmapdashboard Implementationsplans"
Cohesion: 0.25
Nodes (7): App Wiring, Core & Config, Drivers Feature, Layout Shell, Styles, TucTucMap Dashboard — Tasks, Verification

### Community 36 - "Readme Tuctucmapdashboard Running"
Cohesion: 0.25
Nodes (7): Additional Resources, Building, Code scaffolding, Development server, Running end-to-end tests, Running unit tests, TuctucMapDashboard

### Community 37 - "Tuctucmapdashboard Config Routes"
Cohesion: 0.36
Nodes (4): App, appConfig, routes, Component

### Community 38 - "Tuctuc Database Skill"
Cohesion: 0.29
Nodes (6): 1. Arquitectura de Datos & Tablas Principales, 2. Row Level Security (RLS), Triggers y Funciones RPC, 3. Ejecución de Consultas y Modificaciones (`run_sql.js` + `.env`), 4. Buenas Prácticas de Migración, Procedimiento de Uso:, Skill: Base de Datos & Backend (Supabase PostgreSQL)

### Community 39 - "Java Data Repository"
Cohesion: 0.38
Nodes (3): CompletedTripDto, RoutePoint, TripInfo

### Community 41 - "Agents Agents Del"
Cohesion: 0.40
Nodes (5): 1. Estructura del Monorepo y Límites de Subsistemas, 2. Skills de Trabajo Disponibles (`.agents/skills/`), 3. Reglas Obligatorias del Agente (AI Rules), 4. Documentos de Análisis y Planes Estratégicos, Contexto del Proyecto: TucTuc Map (Monorepo)

### Community 43 - "Java Data Repository"
Cohesion: 0.40
Nodes (4): AcceptTripResult, AlreadyTaken, Error, Success

### Community 45 - "Gradlew Script Die"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

## Knowledge Gaps
- **267 isolated node(s):** `{ Client }`, `{ dbConfig }`, `public.app_config`, `fs`, `path` (+262 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **12 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `SupabaseRepository` connect `Java Supabaserepository Data` to `Java Passenger Data`, `Android Driver & App Config`, `Android Map Graphics & Overlays`, `Firebase Realtime Sync`, `Android GPS Telemetry & Service`, `Java Data Repository`, `Java Data Repository`, `Java Data Repository`, `Java Data Repository`, `Drivertriphistoryscreen Java Screens`, `Java Screens Driver`?**
  _High betweenness centrality (0.075) - this node is a cross-community bridge._
- **Why does `SupabaseService` connect `Dashboard Routes & Auth Guard` to `Subscriptions Tuctucmapdashboard Features`?**
  _High betweenness centrality (0.021) - this node is a cross-community bridge._
- **Why does `leaflet` connect `Dashboard Routes & Auth Guard` to `Dashboard Theme & Styles`?**
  _High betweenness centrality (0.018) - this node is a cross-community bridge._
- **Are the 10 inferred relationships involving `AppNavigation()` (e.g. with `DriverMapFullscreenScreen()` and `DriverHomeScreen()`) actually correct?**
  _`AppNavigation()` has 10 INFERRED edges - model-reasoned connections that need verification._
- **What connects `{ Client }`, `{ dbConfig }`, `public.app_config` to the rest of the system?**
  _267 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Dashboard Routes & Auth Guard` be split into smaller, more focused modules?**
  _Cohesion score 0.05221518987341772 - nodes in this community are weakly interconnected._
- **Should `Android Driver & App Config` be split into smaller, more focused modules?**
  _Cohesion score 0.053005464480874315 - nodes in this community are weakly interconnected._