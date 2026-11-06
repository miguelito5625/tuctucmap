# Graph Report - tuctucmap  (2026-11-05)

## Corpus Check
- 124 files · ~137,868 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 1207 nodes · 1992 edges · 103 communities (75 shown, 28 thin omitted)
- Extraction: 99% EXTRACTED · 1% INFERRED · 0% AMBIGUOUS · INFERRED: 19 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `58242817`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- SupabaseService
- @supabase/supabase-js
- 💰 Reporte de Costos Operativos y Rentabilidad en Producción: TucTuc Map
- tuctucMapDashboard
- Screen
- LocationTrackingService
- Informe Técnico y Financiero: Análisis de Costos Cloud (Supabase vs. Firebase) y Rentabilidad de TucTuc Map
- Política de Privacidad y Deslinde de Responsabilidad de TucTuc Map
- DriversComponent
- Vistas y Navegación (Jetpack Compose)
- dependencies
- devDependencies
- 2.1 Problemas Comprobados (Evidencia en Código)
- db_config.js
- DatabaseError
- SubscriptionsComponent
- AdminTripsScreen.kt
- SecurePrefs
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
- DriverTripHistoryScreen.kt
- TucTucMap Dashboard — Tasks
- TuctucMapDashboard
- AGENTS.md
- Skill: Base de Datos & Backend (Supabase PostgreSQL)
- App
- driver.model.ts
- SupabaseRepository.kt
- PassengerDestinationPreferences
- Skill: Angular Dashboard (TucTuc Map)
- AdminSubscriptionsTab.kt
- AdminRepository
- Contexto del Proyecto: TucTuc Map (Monorepo)
- gradlew
- ExampleInstrumentedTest
- ExampleUnitTest
- GeoPoint
- rules/graphify.md
- environment.example.ts
- AdminMainScreen
- DriverLocation
- workflows/graphify.md
- AppNavigation
- scripts
- AdminDriversTab.kt
- tuctucMapDashboard/package.json
- PassengersComponent
- 2. Recomendaciones Estratégicas y Técnicas (Contextualizadas para TucTuc Map)
- @angular/core
- supabase.service.ts
- 2. Errores Funcionales y Bugs Detectados
- rxjs
- tslib
- TripInfo
- 2. Vulnerabilidades Identificadas y Remediación
- login.component.ts
- AppDrawer.kt
- Intent
- DriverReportsScreen.kt
- 2. El Flujo de Trabajo en 3 Fases (El Ciclo Consultivo)
- PassengerMapScreen.kt
- AdminBusinessConfigTab.kt
- AppConfig
- ExcelExporter
- app.routes.ts
- @angular/platform-browser
- SettingsScreen.kt
- AdminModels.kt
- Flow
- public.subscription_payment_requests
- public.app_config
- public.app_config
- public.subscription_payment_requests
- public.trips
- DataSnapshot
- DriverDrawerItem
- AdminPassengersScreen.kt
- AdminDrawerItem
- parseIsoTimestamp
- SubscriptionHistory
- @angular/common
- @angular/cdk

## God Nodes (most connected - your core abstractions)
1. `SupabaseRepository` - 115 edges
2. `SupabaseService` - 49 edges
3. `AdminRepository` - 38 edges
4. `LocationTrackingService` - 29 edges
5. `AppNavigation()` - 28 edges
6. `DriversComponent` - 24 edges
7. `Screen` - 24 edges
8. `SecurePrefs` - 23 edges
9. `PassengerMapScreen()` - 16 edges
10. `Driver` - 14 edges

## Surprising Connections (you probably didn't know these)
- `AppNavigation()` --calls--> `DriverHomeScreen()`  [INFERRED]
  tuctucMap/app/src/main/java/com/example/tuctucmap/ui/Navigation.kt → tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/driver/DriverHomeScreen.kt
- `AppNavigation()` --calls--> `DriverLoginScreen()`  [INFERRED]
  tuctucMap/app/src/main/java/com/example/tuctucmap/ui/Navigation.kt → tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/driver/DriverLoginScreen.kt
- `AppNavigation()` --calls--> `DriverProfileScreen()`  [INFERRED]
  tuctucMap/app/src/main/java/com/example/tuctucmap/ui/Navigation.kt → tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/driver/DriverProfileScreen.kt
- `AppNavigation()` --calls--> `DriverRegisterScreen()`  [INFERRED]
  tuctucMap/app/src/main/java/com/example/tuctucmap/ui/Navigation.kt → tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/driver/DriverRegisterScreen.kt
- `AppNavigation()` --calls--> `DriverReportsScreen()`  [INFERRED]
  tuctucMap/app/src/main/java/com/example/tuctucmap/ui/Navigation.kt → tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/driver/DriverReportsScreen.kt

## Import Cycles
- None detected.

## Communities (103 total, 28 thin omitted)

### Community 2 - "💰 Reporte de Costos Operativos y Rentabilidad en Producción: TucTuc Map"
Cohesion: 0.10
Nodes (19): 1. Resumen Ejecutivo, 2.1. Parámetros de Usuarios y Operación, 2.2. Parámetros Reales de Código (`LocationTrackingService.kt` y `SupabaseRepository.kt`), 2. Supuestos Técnicos y Operativos Utilizados, 3.1. Consumo Mensual en Firebase Realtime Database (Telemetría + Despacho `/trips`), 3.2. Consumo Mensual en Supabase (PostgreSQL + Auth + Realtime), 3. Estimación Técnica de Consumo de Infraestructura (50 Conductores / 200 Pasajeros), 4. Desglose Completo de Costos de Producción (USD y Quetzales) (+11 more)

### Community 3 - "tuctucMapDashboard"
Cohesion: 0.05
Nodes (39): src/material-theme.scss, src/styles.css, build, serve, test, builder, configurations, defaultConfiguration (+31 more)

### Community 4 - "Screen"
Cohesion: 0.08
Nodes (29): Bundle, ComponentActivity, SessionStatus, MainActivity, AdminHome, AdminPassengers, AdminTrips, DriverHome (+21 more)

### Community 5 - "LocationTrackingService"
Cohesion: 0.11
Nodes (11): FusedLocationProviderClient, IBinder, Job, Location, LocationResult, Notification, Service, TextToSpeech (+3 more)

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
Nodes (15): @angular/animations, @angular/compiler, @angular/forms, @angular/material, @angular/router, leaflet, dependencies, @angular/animations (+7 more)

### Community 11 - "devDependencies"
Cohesion: 0.13
Nodes (15): @angular/build, @angular/cli, @angular/compiler-cli, jsdom, prettier, devDependencies, @angular/build, @angular/cli (+7 more)

### Community 12 - "2.1 Problemas Comprobados (Evidencia en Código)"
Cohesion: 0.05
Nodes (36): 1.1 Diagnóstico Ejecutivo, 1.2 Calificación por Dimensión Técnica, 1. Resumen del Estado General de Optimización, 2.1 Problemas Comprobados (Evidencia en Código), 2.2 Mejoras Potenciales (Preventivas y Arquitectónicas), 2. Problemas Encontrados, Archivos Afectados y Evidencias, 3.1 Inventario Completo de Recursos Gráficos, 3.2 Resumen Cuantitativo del Impacto en Recursos Gráficos (+28 more)

### Community 13 - "db_config.js"
Cohesion: 0.10
Nodes (13): { Client }, { dbConfig }, dbConfig, fs, path, { Client }, { dbConfig }, fs (+5 more)

### Community 14 - "DatabaseError"
Cohesion: 0.14
Nodes (6): DatabaseError, ValueEventListener, ValueEventListener, ValueEventListener, ValueEventListener, ValueEventListener

### Community 15 - "SubscriptionsComponent"
Cohesion: 0.15
Nodes (5): SubscriptionPaymentRequest, ApproveRenewalDialogComponent, RejectRenewalDialogComponent, SubscriptionsComponent, Component

### Community 16 - "AdminTripsScreen.kt"
Cohesion: 0.12
Nodes (22): AdminTrip, AdminTripCard(), AdminTripPeriodFilter, CUSTOM, LAST_3_DAYS, LAST_5_DAYS, LAST_7_DAYS, TODAY (+14 more)

### Community 17 - "SecurePrefs"
Cohesion: 0.17
Nodes (8): Application, SecretKey, SessionManager, Context, SecurePrefs, Context, TucTucApplication, UserSession

### Community 18 - "SupabaseRepository"
Cohesion: 0.08
Nodes (13): kotlinx, CompletedTripResponseDto, DriverTodaySummary, Result, PaymentRequestInsertDto, SupabaseRepository, TripSummaryDto, UpdateDriverProfileDto (+5 more)

### Community 19 - "db.sql"
Cohesion: 0.12
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
Cohesion: 0.20
Nodes (9): 1. Misión y Cuándo Activar Este Skill, 3. Plantillas de Comunicación para el Agente, 4. Ejemplos de Aplicación en TucTuc Map, 5. Reglas Mandatorias de Salida, Ejemplo 1: Solicitud imprecisa en la App Móvil, Ejemplo 2: Solicitud en el Dashboard, Plantilla A: Cuando la solicitud es ambigua o admite varias alternativas, Plantilla B: Cuando la solicitud es clara pero se detecta una mejora técnica o de UX (+1 more)

### Community 26 - "TripsComponent"
Cohesion: 0.24
Nodes (3): TripsComponent, Component, ViewChild

### Community 27 - "2. Flujo de Ejecución Paso a Paso"
Cohesion: 0.22
Nodes (9): 2. Flujo de Ejecución Paso a Paso, A. Nombre de la versión, B. Notas de la versión (Formato Play Store), Paso 1: Determinar la Marca de Tiempo del Último `.aab`, Paso 2: Extraer los Commits Realizados desde esa Fecha, Paso 3: Incrementar la Versión en `tuctucMap/app/build.gradle.kts`, Paso 4: Compilar el Paquete Firmado Oficial (`.aab`), Paso 5: Redactar las Notas de Lanzamiento para Google Play Console (+1 more)

### Community 28 - "Skill: Android App (TucTuc Map)"
Cohesion: 0.22
Nodes (9): 1. Stack Tecnológico & Dependencias, 2. Convenciones Críticas de UI & Jetpack Compose, 3. Mapas, Tiles y Overlays (OSMDroid), 4. Telemetría GPS, Geocerca y Alertas en Segundo Plano, 5. Reglas de Negocio del Conductor y Suscripciones Anti-Fraude, 6. Módulo Administrativo Móvil Nativo (Admin Dashboard Integrado), 7. Secretos (`BuildConfig`), Persistencia de Sesión y Ofuscación R8, 7. Verificación y Compilación Obligatoria (+1 more)

### Community 29 - "TucTucMap Dashboard — Plan de Implementación"
Cohesion: 0.25
Nodes (7): Decisiones Técnicas, Estructura de Archivos a Crear/Modificar, Flujo de Datos, Módulos de Angular Material a Importar, Objetivo, TucTucMap Dashboard — Plan de Implementación, Verificación

### Community 32 - "DriverTripHistoryScreen.kt"
Cohesion: 0.19
Nodes (10): DriverHistoryPeriodFilter, CUSTOM_RANGE, TODAY, YESTERDAY, DriverTripCard(), DriverTripHistoryScreen(), SelectableDates, Context (+2 more)

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

### Community 38 - "driver.model.ts"
Cohesion: 0.19
Nodes (7): FinancialKPIs, LiveDriverLocation, PaymentMethod, RenewalRequestStatus, SubscriptionPlan, DriverHistoryDialogComponent, Component

### Community 39 - "SupabaseRepository.kt"
Cohesion: 0.24
Nodes (6): MutableData, AcceptTripResult, AlreadyTaken, Error, Success, Transaction

### Community 41 - "Skill: Angular Dashboard (TucTuc Map)"
Cohesion: 0.33
Nodes (5): 1. Stack Tecnológico & Dependencias, 2. Autenticación, Secretos y Seguridad Administrativa (RLS & XSS), 3. Módulos y Funcionalidades Clave, 4. Verificación y Compilación Obligatoria, Skill: Angular Dashboard (TucTuc Map)

### Community 42 - "AdminSubscriptionsTab.kt"
Cohesion: 0.20
Nodes (17): AdminPaymentRequest, AdminSubscriptionHistory, AdminSubscriptionsTab(), exportAndShareCsv(), filterChipColors(), formatEpochDate(), getSubscriptionPeriodRange(), Context (+9 more)

### Community 43 - "AdminRepository"
Cohesion: 0.14
Nodes (8): StateFlow, AdminRepository, AppConfig, Flow, Result, PendingCountIdDto, TripDateBounds, UpdateAppConfigDto

### Community 44 - "Contexto del Proyecto: TucTuc Map (Monorepo)"
Cohesion: 0.40
Nodes (5): 1. Estructura del Monorepo y Límites de Subsistemas, 2. Skills de Trabajo Disponibles (`.agents/skills/`), 3. Reglas Obligatorias del Agente (AI Rules), 4. Documentos de Análisis y Planes Estratégicos, Contexto del Proyecto: TucTuc Map (Monorepo)

### Community 45 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 49 - "GeoPoint"
Cohesion: 0.06
Nodes (40): Activity, Animator, AnimatorListenerAdapter, Canvas, GeoPoint, IMyLocationProvider, MapView, MyLocationNewOverlay (+32 more)

### Community 62 - "AppNavigation"
Cohesion: 0.17
Nodes (8): AppNavigation(), DriverRouteToPassenger, Driver, resolveDriverScreen(), DriverPendingScreen(), DriverSubscriptionExpiredScreen(), PassengerProfileScreen(), PassengerRegisterScreen()

### Community 63 - "scripts"
Cohesion: 0.33
Nodes (6): scripts, build, ng, start, test, watch

### Community 64 - "AdminDriversTab.kt"
Cohesion: 0.35
Nodes (12): AdminDriver, AdminFinancialStats, AdminDriverCard(), AdminDriversTab(), filterChipColors(), KpiCard(), AppConfig, Color (+4 more)

### Community 65 - "tuctucMapDashboard/package.json"
Cohesion: 0.40
Nodes (4): name, packageManager, private, version

### Community 66 - "PassengersComponent"
Cohesion: 0.24
Nodes (4): PassengerProfile, PassengersComponent, Component, ViewChild

### Community 67 - "2. Recomendaciones Estratégicas y Técnicas (Contextualizadas para TucTuc Map)"
Cohesion: 0.12
Nodes (16): 1.1 Diagnóstico de Estabilidad Técnica y Rendimiento (Findings), 1.2 Oportunidades de Mejora Identificadas por los Evaluadores, 1. Resumen Ejecutivo del Reporte de Pruebas, 2.1 Implementación de Onboarding / Walkthrough Dinámico por Rol, 2.2 Botón "Compartir App" (Viralidad Hiperlocal vía WhatsApp), 2.3 Botón "Calificar la Aplicación" e In-App Review, 2.4 Renovación Gráfica de la Ficha de Google Play (ASO & Capturas), 2.5 Canal de Soporte y Sugerencias In-App (+8 more)

### Community 69 - "supabase.service.ts"
Cohesion: 0.25
Nodes (4): ChangeNameDialogComponent, Component, ChangePasswordDialogComponent, Component

### Community 70 - "2. Errores Funcionales y Bugs Detectados"
Cohesion: 0.14
Nodes (13): 1. Resumen Ejecutivo, 2. Errores Funcionales y Bugs Detectados, 3. Evaluación de Rendimiento por Subsistema, 4. Hoja de Ruta de Optimización Recomendada, 🔴 [BUG-01] Tormenta de Consultas `O(N)` por Canales Realtime sin Filtro en `public.profiles`, 🔴 [BUG-02] Viajes Broadcast de Pasajeros Invitados Expirados Nunca se Eliminan de Firebase RTDB, 🟠 [BUG-03] Descarga Innecesaria de Polilíneas `route_points` (JSONB) en `getDriverTripHistory()`, 🟠 [BUG-04] Escaneo Completo de Tablas y Truncamiento Silencioso a 1,000 Filas en `AdminRepository` (+5 more)

### Community 73 - "TripInfo"
Cohesion: 0.39
Nodes (3): CompletedTripDto, RoutePoint, TripInfo

### Community 74 - "2. Vulnerabilidades Identificadas y Remediación"
Cohesion: 0.14
Nodes (13): 1. Resumen Ejecutivo, 2. Vulnerabilidades Identificadas y Remediación, 3. Controles de Seguridad Positivos Ya Implementados ✅, 4. Plan Priorizado de Remediación de Seguridad, 🛡️ Reporte de Auditoría de Seguridad: TucTuc Map (Android + Backend), 🔴 [SEC-01] CRÍTICA: Falta de Autenticación en Firebase Realtime Database (`driver_locations` y `trips`), 🟠 [SEC-02] ALTA: Exposición Masiva de Datos Personales (PII) en `public.profiles` por Política RLS Permisiva, 🟠 [SEC-03] ALTA: Falsificación de Historial de Viajes en `public.trips` y Manipulación de Montos en Solicitudes de Pago (+5 more)

### Community 76 - "AppDrawer.kt"
Cohesion: 0.18
Nodes (16): ImageVector, Passenger, AdminDrawerSheet(), DrawerNavigationItem(), DriverDrawerSheet(), formatDate(), Driver, Modifier (+8 more)

### Community 77 - "Intent"
Cohesion: 0.24
Nodes (9): Intent, DriverHomeScreen(), Context, Driver, toggleService(), AppShareUtils, Context, ExternalNavigationUtils (+1 more)

### Community 78 - "DriverReportsScreen.kt"
Cohesion: 0.20
Nodes (11): DailyReportGroup, DriverReportsPeriodFilter, CUSTOM_RANGE, TODAY, YESTERDAY, DriverReportsScreen(), SelectableDates, DriverWeeklyBarChart() (+3 more)

### Community 79 - "2. El Flujo de Trabajo en 3 Fases (El Ciclo Consultivo)"
Cohesion: 0.33
Nodes (6): 2. El Flujo de Trabajo en 3 Fases (El Ciclo Consultivo), Fase 1: Reformulación y Enriquecimiento Técnico (Prompt Engineering), Fase 2: Detección de Vacíos y Consulta de Dudas Críticas, Fase 3: Recomendación Proactiva de la Mejor Opción, ¿Qué aspectos consultar activamente?, Regla de Interrogación:

### Community 80 - "PassengerMapScreen.kt"
Cohesion: 0.27
Nodes (7): calculateRemainingDays(), Driver, ServerClock, SubscriptionRenewalRequest, DriverSubscriptionScreen(), Quadruple, RenewalRequestItem()

### Community 81 - "AdminBusinessConfigTab.kt"
Cohesion: 0.46
Nodes (7): AppConfig, AdminBusinessConfigTab(), configFieldColors(), ConfigSectionTitle(), Context, Modifier, testWhatsAppNumber()

### Community 84 - "app.routes.ts"
Cohesion: 0.33
Nodes (4): leaflet, allowedCommonJsDependencies, authGuard(), sanitizeCsvCell()

### Community 86 - "SettingsScreen.kt"
Cohesion: 0.70
Nodes (4): Context, Modifier, openWhatsAppSupport(), SettingsScreen()

### Community 87 - "AdminModels.kt"
Cohesion: 0.33
Nodes (5): AdminFinancialSummaryDto, ApprovePaymentRequestRpcParams, DeleteUserByAdminRpcParams, RejectPaymentRequestRpcParams, UserRoleDto

### Community 89 - "Flow"
Cohesion: 0.29
Nodes (3): AppConfig, Driver, Flow

### Community 96 - "DriverDrawerItem"
Cohesion: 0.25
Nodes (8): DriverDrawerItem, HISTORY, HOME, INFORMATION, MAP, PROFILE, REPORTS, SUBSCRIPTIONS

### Community 97 - "AdminPassengersScreen.kt"
Cohesion: 0.52
Nodes (6): AdminPassenger, AdminPassengersScreen(), formatDateShort(), Context, openWhatsApp(), PassengerCard()

### Community 98 - "AdminDrawerItem"
Cohesion: 0.29
Nodes (7): AdminDrawerItem, CONFIG, DRIVERS, FLEET_MAP, PASSENGERS, SUBSCRIPTIONS, TRIPS

### Community 99 - "parseIsoTimestamp"
Cohesion: 0.83
Nodes (3): parseIsoTimestamp(), DriverProfileScreen(), formatExpiryDate()

## Knowledge Gaps
- **370 isolated node(s):** `{ Client }`, `{ dbConfig }`, `public.app_config`, `fs`, `path` (+365 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **28 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `SupabaseRepository` connect `SupabaseRepository` to `Screen`, `LocationTrackingService`, `DatabaseError`, `AdminTripsScreen.kt`, `DriverTripHistoryScreen.kt`, `SupabaseRepository.kt`, `AdminSubscriptionsTab.kt`, `AdminRepository`, `GeoPoint`, `AdminMainScreen`, `AppNavigation`, `AdminDriversTab.kt`, `TripInfo`, `AppDrawer.kt`, `Intent`, `DriverReportsScreen.kt`, `PassengerMapScreen.kt`, `AdminBusinessConfigTab.kt`, `SettingsScreen.kt`, `Flow`, `DataSnapshot`, `parseIsoTimestamp`?**
  _High betweenness centrality (0.102) - this node is a cross-community bridge._
- **Why does `AdminRepository` connect `AdminRepository` to `AdminDriversTab.kt`, `AdminPassengersScreen.kt`, `AdminSubscriptionsTab.kt`, `AdminTripsScreen.kt`, `AdminBusinessConfigTab.kt`, `SupabaseRepository`, `AdminMainScreen`?**
  _High betweenness centrality (0.032) - this node is a cross-community bridge._
- **Why does `LocationTrackingService` connect `LocationTrackingService` to `GeoPoint`, `SupabaseRepository`, `Intent`?**
  _High betweenness centrality (0.013) - this node is a cross-community bridge._
- **What connects `{ Client }`, `{ dbConfig }`, `public.app_config` to the rest of the system?**
  _370 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `SupabaseService` be split into smaller, more focused modules?**
  _Cohesion score 0.12666666666666668 - nodes in this community are weakly interconnected._
- **Should `💰 Reporte de Costos Operativos y Rentabilidad en Producción: TucTuc Map` be split into smaller, more focused modules?**
  _Cohesion score 0.1 - nodes in this community are weakly interconnected._
- **Should `tuctucMapDashboard` be split into smaller, more focused modules?**
  _Cohesion score 0.052564102564102565 - nodes in this community are weakly interconnected._