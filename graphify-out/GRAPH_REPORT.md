# Graph Report - tuctucmap  (2026-11-09)

## Corpus Check
- 142 files · ~155,451 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 1270 nodes · 2246 edges · 94 communities (70 shown, 24 thin omitted)
- Extraction: 99% EXTRACTED · 1% INFERRED · 0% AMBIGUOUS · INFERRED: 22 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `46c4f7f2`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- SupabaseService
- @supabase/supabase-js
- AdminTripsScreen.kt
- tuctucMapDashboard
- Screen
- LocationTrackingService
- parseIsoTimestamp
- Política de Privacidad y Deslinde de Responsabilidad de TucTuc Map
- DriversComponent
- Vistas y Navegación (Jetpack Compose)
- dependencies
- devDependencies
- Skill: Buenas Prácticas y Calidad Visual de Interfaces Android (TucTuc Map)
- db_config.js
- DatabaseError
- subscriptions.component.ts
- .loginDriverWithGoogle
- SecurePrefs
- MainActivity.kt
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
- DataSnapshot
- GyroscopeGuideManager
- TucTucMap Dashboard — Tasks
- TuctucMapDashboard
- AGENTS.md
- Skill: Base de Datos & Backend (Supabase PostgreSQL)
- app.routes.ts
- supabase.service.ts
- 2. Protocolo de Ejecución en 6 Pasos
- CrashLogsComponent
- AnimatedSplashScreen.kt
- Dp
- @angular/platform-browser
- Contexto del Proyecto: TucTuc Map (Monorepo)
- gradlew
- ExampleInstrumentedTest
- ExampleUnitTest
- PassengerMapScreen
- BusinessConfigComponent
- environment.example.ts
- Result
- Guía Regulatoria, Fiscal (SAT) y Ruta de Formalización — TucTuc Map
- workflows/graphify.md
- AppNavigation
- scripts
- Auditoría Funcional, Arquitectónica y de Mejoras: Módulo Administrador Android (`TucTuc Map`)
- tuctucMapDashboard/package.json
- PassengersComponent
- 2. Recomendaciones Estratégicas y Técnicas (Contextualizadas para TucTuc Map)
- AdminSubscriptionsTab.kt
- shell.component.ts
- AppDrawer.kt
- rxjs
- tslib
- DriverReportsScreen.kt
- @angular/material
- 4. Tipografía, Dimensionamiento y Accesibilidad Visual
- @angular/core
- DriverHomeScreen.kt
- .saveTripRecord
- @angular/common
- public.app_crash_logs
- SupabaseRepository.kt
- SettingsScreen.kt
- public.subscription_payment_requests
- public.app_config
- public.app_config
- public.subscription_payment_requests
- public.trips
- public.app_crash_logs
- SupabaseRepository
- DriverLocation
- PassengerDestinationPreferences

## God Nodes (most connected - your core abstractions)
1. `SupabaseRepository` - 123 edges
2. `SupabaseService` - 55 edges
3. `LocationTrackingService` - 36 edges
4. `AppNavigation()` - 31 edges
5. `Screen` - 27 edges
6. `SecurePrefs` - 25 edges
7. `DriversComponent` - 24 edges
8. `AdminRepository` - 20 edges
9. `PassengerMapScreen()` - 17 edges
10. `Driver` - 14 edges

## Surprising Connections (you probably didn't know these)
- `AppNavigation()` --calls--> `DriverHomeScreen()`  [INFERRED]
  tuctucMap/app/src/main/java/com/example/tuctucmap/ui/Navigation.kt → tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/driver/DriverHomeScreen.kt
- `AppNavigation()` --calls--> `DriverLoginScreen()`  [INFERRED]
  tuctucMap/app/src/main/java/com/example/tuctucmap/ui/Navigation.kt → tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/driver/DriverLoginScreen.kt
- `AppNavigation()` --calls--> `DriverPendingScreen()`  [INFERRED]
  tuctucMap/app/src/main/java/com/example/tuctucmap/ui/Navigation.kt → tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/driver/DriverPendingScreen.kt
- `AppNavigation()` --calls--> `DriverProfileScreen()`  [INFERRED]
  tuctucMap/app/src/main/java/com/example/tuctucmap/ui/Navigation.kt → tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/driver/DriverProfileScreen.kt
- `AppNavigation()` --calls--> `DriverRegisterScreen()`  [INFERRED]
  tuctucMap/app/src/main/java/com/example/tuctucmap/ui/Navigation.kt → tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/driver/DriverRegisterScreen.kt

## Import Cycles
- None detected.

## Communities (94 total, 24 thin omitted)

### Community 0 - "SupabaseService"
Cohesion: 0.09
Nodes (4): Injectable, AppConfig, SubscriptionHistory, SupabaseService

### Community 2 - "AdminTripsScreen.kt"
Cohesion: 0.06
Nodes (55): AdminCrashLog, AdminDriver, AdminFinancialSummaryDto, AdminPassenger, AdminProfileDto, AdminTrip, AdminUserDto, ApproveDriverRpcParams (+47 more)

### Community 3 - "tuctucMapDashboard"
Cohesion: 0.05
Nodes (41): leaflet, src/material-theme.scss, src/styles.css, build, serve, test, builder, configurations (+33 more)

### Community 4 - "Screen"
Cohesion: 0.09
Nodes (23): AdminAdmins, AdminCrashLogs, AdminHome, AdminPassengers, AdminProfile, AdminTrips, DriverHome, DriverLogin (+15 more)

### Community 5 - "LocationTrackingService"
Cohesion: 0.14
Nodes (12): FusedLocationProviderClient, IBinder, Job, Location, LocationResult, Notification, Service, TextToSpeech (+4 more)

### Community 6 - "parseIsoTimestamp"
Cohesion: 0.33
Nodes (6): parseIsoTimestamp(), DriverProfileScreen(), formatExpiryDate(), PassengerProfileScreen(), AppShareUtils, Context

### Community 7 - "Política de Privacidad y Deslinde de Responsabilidad de TucTuc Map"
Cohesion: 0.11
Nodes (17): 10. Contacto, 1. Naturaleza del Servicio (Intermediación Tecnológica), 2.1 Consentimiento Voluntario, 2.2 Veracidad y Legalidad de la Información, 2.3 Datos Compartidos Directamente entre Usuarios, 2. Responsabilidad Exclusiva del Usuario sobre los Datos Compartidos, 3. Deslinde y Exclusión Total de Responsabilidad del Propietario, 4.1 Información de Registro y Perfil (+9 more)

### Community 8 - "DriversComponent"
Cohesion: 0.11
Nodes (6): Driver, DriverStatus, DriversComponent, SubscriptionDialogComponent, SubscriptionDialogData, Component

### Community 9 - "Vistas y Navegación (Jetpack Compose)"
Cohesion: 0.07
Nodes (29): Configuración del Proyecto y Dependencias, Manifiesto de Android y Permisos, Manual Verification, Modelos y Repositorio de Datos (Clean Architecture), [MODIFY] [AndroidManifest.xml](file:///c:/Users/exbinario/AndroidStudioProjects/tuctucMap/app/src/main/AndroidManifest.xml), [MODIFY] [build.gradle.kts (app)](file:///c:/Users/exbinario/AndroidStudioProjects/tuctucMap/app/build.gradle.kts), [MODIFY] [build.gradle.kts (root)](file:///c:/Users/exbinario/AndroidStudioProjects/tuctucMap/build.gradle.kts), [MODIFY] [libs.versions.toml](file:///c:/Users/exbinario/AndroidStudioProjects/tuctucMap/gradle/libs.versions.toml) (+21 more)

### Community 10 - "dependencies"
Cohesion: 0.13
Nodes (15): @angular/animations, @angular/cdk, @angular/compiler, @angular/forms, @angular/router, leaflet, dependencies, @angular/animations (+7 more)

### Community 11 - "devDependencies"
Cohesion: 0.13
Nodes (15): @angular/build, @angular/cli, @angular/compiler-cli, jsdom, prettier, devDependencies, @angular/build, @angular/cli (+7 more)

### Community 12 - "Skill: Buenas Prácticas y Calidad Visual de Interfaces Android (TucTuc Map)"
Cohesion: 0.13
Nodes (15): 1. Activación Mandatoria del Skill, 2.1. Regla Inquebrantable para Formularios y Campos de Entrada, 2.2. Manejo de Scaffold e Insets de Navegación, 2. Insets del Sistema, Edge-to-Edge y Manejo del Teclado (IME), 3.1. Tokens Oficiales de Color, 3.2. Reglas de Contraste y Legibilidad, 3. Paleta Cromática y Tema Oficial (Dark Mode por Defecto), 5. Manejo Integral de los 4 Estados de UI (State Management) (+7 more)

### Community 13 - "db_config.js"
Cohesion: 0.07
Nodes (22): { Client }, { dbConfig }, { Client }, { dbConfig }, fs, path, { Client }, { dbConfig } (+14 more)

### Community 14 - "DatabaseError"
Cohesion: 0.13
Nodes (7): DatabaseError, Flow, ValueEventListener, ValueEventListener, ValueEventListener, ValueEventListener, ValueEventListener

### Community 15 - "subscriptions.component.ts"
Cohesion: 0.14
Nodes (6): SubscriptionPaymentRequest, ApproveRenewalDialogComponent, RejectRenewalDialogComponent, SubscriptionsComponent, Component, sanitizeCsvCell()

### Community 16 - ".loginDriverWithGoogle"
Cohesion: 0.27
Nodes (3): DriverGoogleLoginResult, NeedsProfile, RoleMismatchException

### Community 17 - "SecurePrefs"
Cohesion: 0.11
Nodes (13): Application, SecretKey, SessionManager, Thread, CrashReportDto, CrashReporterManager, Context, TucTucCrashHandler (+5 more)

### Community 18 - "MainActivity.kt"
Cohesion: 0.36
Nodes (5): Bundle, ComponentActivity, Intent, MainActivity, TuctucMapTheme()

### Community 19 - "db.sql"
Cohesion: 0.11
Nodes (8): public.app_config, public.driver_subscriptions, public.handle_profile_insert_security(), public.profiles, public.sync_app_config_fields(), auth.users, trg_protect_profile_sensitive_columns, public.protect_profile_sensitive_columns

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
Cohesion: 0.17
Nodes (12): 1. Cuándo Activar este Skill, 2. Flujo de Ejecución Paso a Paso, 3. Entregable al Usuario, A. Nombre de la versión, B. Notas de la versión (Formato Play Store), Paso 1: Determinar la Marca de Tiempo del Último `.aab`, Paso 2: Extraer los Commits Realizados desde esa Fecha, Paso 3: Incrementar la Versión en `tuctucMap/app/build.gradle.kts` (+4 more)

### Community 28 - "Skill: Android App (TucTuc Map)"
Cohesion: 0.18
Nodes (11): 1. Stack Tecnológico & Dependencias, 2. Arquitectura de UI, Restricciones y Delegación, 3. Mapas, Tiles y Overlays (OSMDroid), 4. Telemetría GPS, Geocerca y Alertas en Segundo Plano, 5. Reglas de Negocio del Conductor y Suscripciones Anti-Fraude, 6. Módulo Administrativo Móvil Nativo (Admin Dashboard Integrado), 7. Secretos (`BuildConfig`), Persistencia de Sesión y Ofuscación R8, 8. Telemetría y Reporte Automático de Errores (Crash Reporting) (+3 more)

### Community 29 - "TucTucMap Dashboard — Plan de Implementación"
Cohesion: 0.25
Nodes (7): Decisiones Técnicas, Estructura de Archivos a Crear/Modificar, Flujo de Datos, Módulos de Angular Material a Importar, Objetivo, TucTucMap Dashboard — Plan de Implementación, Verificación

### Community 32 - "GyroscopeGuideManager"
Cohesion: 0.24
Nodes (4): Sensor, SensorEvent, SensorEventListener, GyroscopeGuideManager

### Community 33 - "TucTucMap Dashboard — Tasks"
Cohesion: 0.25
Nodes (7): App Wiring, Core & Config, Drivers Feature, Layout Shell, Styles, TucTucMap Dashboard — Tasks, Verification

### Community 34 - "TuctucMapDashboard"
Cohesion: 0.25
Nodes (7): Additional Resources, Building, Code scaffolding, Development server, Running end-to-end tests, Running unit tests, TuctucMapDashboard

### Community 36 - "Skill: Base de Datos & Backend (Supabase PostgreSQL)"
Cohesion: 0.29
Nodes (6): 1. Arquitectura de Datos & Tablas Principales, 2. Row Level Security (RLS), Triggers y Funciones RPC, 3. Ejecución de Consultas y Modificaciones (`run_sql.js` + `.env`), 4. Buenas Prácticas de Migración, Procedimiento de Uso:, Skill: Base de Datos & Backend (Supabase PostgreSQL)

### Community 37 - "app.routes.ts"
Cohesion: 0.18
Nodes (7): App, appConfig, routes, Component, authGuard(), LoginComponent, Component

### Community 38 - "supabase.service.ts"
Cohesion: 0.15
Nodes (9): FinancialKPIs, LiveDriverLocation, PaymentMethod, RenewalRequestStatus, SubscriptionPlan, CrashDetailDialogComponent, Component, DriverHistoryDialogComponent (+1 more)

### Community 39 - "2. Protocolo de Ejecución en 6 Pasos"
Cohesion: 0.18
Nodes (11): 1. Activación del Skill, 2. Protocolo de Ejecución en 6 Pasos, 3. Ejemplo Práctico de Interacción, Clasificación Inicial:, Paso 1: Consultar Incidencias Pendientes en Supabase, Paso 2: Análisis y Diagnóstico de Causa Raíz, Paso 3: Presentar Resumen y Solicitar Confirmación (Obligatorio), Paso 4: Aplicar Cambios y Compilar (Tras Aprobación) (+3 more)

### Community 40 - "CrashLogsComponent"
Cohesion: 0.27
Nodes (4): CrashLog, CrashLogsComponent, Component, ViewChild

### Community 41 - "AnimatedSplashScreen.kt"
Cohesion: 0.80
Nodes (4): AnimatedAppName(), AnimatedSplashScreen(), Modifier, SmokePuffDef

### Community 42 - "Dp"
Cohesion: 0.08
Nodes (33): Brush, Dp, AdminFinancialStats, GoogleAuthCancellationException, GoogleAuthHelper, GoogleUserData, Context, Exception (+25 more)

### Community 44 - "Contexto del Proyecto: TucTuc Map (Monorepo)"
Cohesion: 0.40
Nodes (5): 1. Estructura del Monorepo y Límites de Subsistemas, 2. Skills de Trabajo Disponibles (`.agents/skills/`), 3. Reglas Obligatorias del Agente (AI Rules), 4. Documentos de Análisis y Planes Estratégicos, Contexto del Proyecto: TucTuc Map (Monorepo)

### Community 45 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 49 - "PassengerMapScreen"
Cohesion: 0.07
Nodes (35): Animator, AnimatorListenerAdapter, Canvas, GeoPoint, IMyLocationProvider, MapView, MyLocationNewOverlay, org (+27 more)

### Community 54 - "Result"
Cohesion: 0.18
Nodes (6): CompletedTripResponseDto, ByteArray, Exception, Result, UpdateDriverProfileDto, UpdatePassengerProfileDto

### Community 55 - "Guía Regulatoria, Fiscal (SAT) y Ruta de Formalización — TucTuc Map"
Cohesion: 0.22
Nodes (8): 1. La Gran Ventaja del Modelo de Negocio de TucTuc Map, 2. El Semáforo de Crecimiento: Métricas y Puntos de Inflexión, 3. Análisis de Frentes Regulatorios, 4. Plan de Acción Recomendado, A. Superintendencia de Administración Tributaria (SAT) e Impuestos, B. Municipalidades y Policía Municipal de Tránsito (PMT), C. Términos y Condiciones Contractuales (Desde el Lanzamiento Público), Guía Regulatoria, Fiscal (SAT) y Ruta de Formalización — TucTuc Map

### Community 62 - "AppNavigation"
Cohesion: 0.24
Nodes (10): AppNavigation(), DriverRouteToPassenger, Driver, resolveDriverScreen(), AdminAdminsScreen(), AdminProfileScreen(), DriverSubscriptionExpiredScreen(), PassengerLoginScreen() (+2 more)

### Community 63 - "scripts"
Cohesion: 0.33
Nodes (6): scripts, build, ng, start, test, watch

### Community 64 - "Auditoría Funcional, Arquitectónica y de Mejoras: Módulo Administrador Android (`TucTuc Map`)"
Cohesion: 0.07
Nodes (27): 1.1. Pestañas Principales (`BottomNavigationBar` en `AdminMainScreen`), 1.2. Pantallas Secundarias y Acciones del Menú Lateral (`AdminDrawerSheet`), 1. Inventario de Funcionalidades del Administrador en Android, 2.1. Operaciones No Ejecutadas por Restricción de Integridad de Datos, 2. Resultados de las Pruebas Realizadas en Emulador vía ADB (`emulator-5554`), 3.1. Errores Críticos y de Lógica de Negocio, 3.2. Problemas de Interfaz (UI/UX) Confirmados en Emulador, 3. Errores Funcionales, Bugs Lógicos y Comportamientos Inesperados (+19 more)

### Community 65 - "tuctucMapDashboard/package.json"
Cohesion: 0.40
Nodes (4): name, packageManager, private, version

### Community 66 - "PassengersComponent"
Cohesion: 0.24
Nodes (4): PassengerProfile, PassengersComponent, Component, ViewChild

### Community 67 - "2. Recomendaciones Estratégicas y Técnicas (Contextualizadas para TucTuc Map)"
Cohesion: 0.12
Nodes (16): 1.1 Diagnóstico de Estabilidad Técnica y Rendimiento (Findings), 1.2 Oportunidades de Mejora Identificadas por los Evaluadores, 1. Resumen Ejecutivo del Reporte de Pruebas, 2.1 Implementación de Onboarding / Walkthrough Dinámico por Rol, 2.2 Botón "Compartir App" (Viralidad Hiperlocal vía WhatsApp), 2.3 Botón "Calificar la Aplicación" e In-App Review, 2.4 Renovación Gráfica de la Ficha de Google Play (ASO & Capturas), 2.5 Canal de Soporte y Sugerencias In-App (+8 more)

### Community 68 - "AdminSubscriptionsTab.kt"
Cohesion: 0.09
Nodes (29): AdminPaymentRequest, AdminSubscriptionHistory, AppConfig, AdminBusinessConfigTab(), configFieldColors(), ConfigSectionTitle(), Context, Modifier (+21 more)

### Community 69 - "shell.component.ts"
Cohesion: 0.24
Nodes (4): ChangeNameDialogComponent, Component, ChangePasswordDialogComponent, Component

### Community 70 - "AppDrawer.kt"
Cohesion: 0.08
Nodes (37): Passenger, AdminDrawerItem, ADMINS, CONFIG, CRASH_LOGS, DRIVERS, FLEET_MAP, PASSENGERS (+29 more)

### Community 73 - "DriverReportsScreen.kt"
Cohesion: 0.20
Nodes (11): DailyReportGroup, DriverReportsPeriodFilter, CUSTOM_RANGE, TODAY, YESTERDAY, DriverReportsScreen(), SelectableDates, DriverWeeklyBarChart() (+3 more)

### Community 75 - "4. Tipografía, Dimensionamiento y Accesibilidad Visual"
Cohesion: 0.50
Nodes (4): 4.1. Unidades Estrictas: `dp` vs `sp`, 4.2. Prevención de Textos Cortados por Escalado de Fuente (Font Scaling), 4.3. Área Mínima de Toque (Touch Target Size), 4. Tipografía, Dimensionamiento y Accesibilidad Visual

### Community 79 - "DriverHomeScreen.kt"
Cohesion: 0.06
Nodes (30): Activity, DriverFarePreferences, Context, calculateRemainingDays(), Driver, getOperationalCalendar(), ServerClock, SubscriptionRenewalRequest (+22 more)

### Community 80 - ".saveTripRecord"
Cohesion: 0.39
Nodes (3): CompletedTripDto, RoutePoint, TripInfo

### Community 84 - "SupabaseRepository.kt"
Cohesion: 0.22
Nodes (7): MutableData, SessionStatus, AcceptTripResult, AlreadyTaken, Error, Success, Transaction

### Community 87 - "SettingsScreen.kt"
Cohesion: 0.70
Nodes (4): Context, Modifier, openWhatsAppSupport(), SettingsScreen()

### Community 100 - "SupabaseRepository"
Cohesion: 0.12
Nodes (7): AppConfig, kotlinx, DriverTodaySummary, Driver, PaymentRequestInsertDto, SupabaseRepository, TripSummaryDto

## Knowledge Gaps
- **333 isolated node(s):** `{ Client }`, `{ dbConfig }`, `{ Client }`, `{ dbConfig }`, `fs` (+328 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **24 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `SupabaseRepository` connect `SupabaseRepository` to `AdminTripsScreen.kt`, `AdminSubscriptionsTab.kt`, `LocationTrackingService`, `AppDrawer.kt`, `parseIsoTimestamp`, `DriverReportsScreen.kt`, `Dp`, `DatabaseError`, `DriverHomeScreen.kt`, `.saveTripRecord`, `.loginDriverWithGoogle`, `MainActivity.kt`, `PassengerMapScreen`, `SupabaseRepository.kt`, `Result`, `SettingsScreen.kt`, `AppNavigation`, `DataSnapshot`?**
  _High betweenness centrality (0.117) - this node is a cross-community bridge._
- **Why does `SecurePrefs` connect `SecurePrefs` to `MainActivity.kt`, `Dp`, `LocationTrackingService`?**
  _High betweenness centrality (0.032) - this node is a cross-community bridge._
- **Why does `AppNavigation()` connect `AppNavigation` to `AdminTripsScreen.kt`, `SupabaseRepository`, `Screen`, `AppDrawer.kt`, `parseIsoTimestamp`, `DriverReportsScreen.kt`, `Dp`, `DriverHomeScreen.kt`, `SecurePrefs`, `MainActivity.kt`, `PassengerMapScreen`, `SettingsScreen.kt`?**
  _High betweenness centrality (0.026) - this node is a cross-community bridge._
- **Are the 9 inferred relationships involving `AppNavigation()` (e.g. with `DriverHomeScreen()` and `DriverLoginScreen()`) actually correct?**
  _`AppNavigation()` has 9 INFERRED edges - model-reasoned connections that need verification._
- **What connects `{ Client }`, `{ dbConfig }`, `{ Client }` to the rest of the system?**
  _333 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `SupabaseService` be split into smaller, more focused modules?**
  _Cohesion score 0.08708708708708708 - nodes in this community are weakly interconnected._
- **Should `AdminTripsScreen.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.05605499735589635 - nodes in this community are weakly interconnected._