# Graph Report - tuctucmap  (2026-11-06)

## Corpus Check
- 137 files · ~154,378 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 1318 nodes · 2249 edges · 101 communities (77 shown, 24 thin omitted)
- Extraction: 99% EXTRACTED · 1% INFERRED · 0% AMBIGUOUS · INFERRED: 21 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `ca7d1910`
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
- SupabaseRepository.kt
- SubscriptionsComponent
- login.component.ts
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
- supabase.service.ts
- AppNavigation
- TucTucMap Dashboard — Tasks
- TuctucMapDashboard
- AGENTS.md
- Skill: Base de Datos & Backend (Supabase PostgreSQL)
- App
- AdminCrashLogsScreen.kt
- 2. Protocolo de Ejecución en 6 Pasos
- CrashLogsComponent
- Skill: Angular Dashboard (TucTuc Map)
- Intent
- AdminRepository
- Contexto del Proyecto: TucTuc Map (Monorepo)
- gradlew
- ExampleInstrumentedTest
- ExampleUnitTest
- PassengerMapScreen.kt
- BusinessConfigComponent
- environment.example.ts
- Result
- Guía Regulatoria, Fiscal (SAT) y Ruta de Formalización — TucTuc Map
- workflows/graphify.md
- Color.kt
- scripts
- AdminDriversTab.kt
- tuctucMapDashboard/package.json
- PassengersComponent
- 2. Recomendaciones Estratégicas y Técnicas (Contextualizadas para TucTuc Map)
- .saveTripRecord
- shell.component.ts
- 2. Errores Funcionales y Bugs Detectados
- rxjs
- tslib
- DriverReportsScreen.kt
- 2. Vulnerabilidades Identificadas y Remediación
- AppDrawer.kt
- 2. El Flujo de Trabajo en 3 Fases (El Ciclo Consultivo)
- PassengerDestinationPreferences
- @angular/cdk
- AdminTripsScreen.kt
- DriverSubscriptionScreen.kt
- MainActivity.kt
- public.app_crash_logs
- .loginDriverWithGoogle
- AdminPassengersScreen.kt
- @angular/platform-browser
- AdminTripPeriodFilter
- AdminModels.kt
- DriverTripHistoryScreen.kt
- public.subscription_payment_requests
- public.app_config
- public.app_config
- public.subscription_payment_requests
- public.trips
- AcceptTripResult
- public.app_crash_logs
- SelectableDates
- @angular/core
- @angular/common

## God Nodes (most connected - your core abstractions)
1. `SupabaseRepository` - 120 edges
2. `SupabaseService` - 55 edges
3. `AdminRepository` - 43 edges
4. `LocationTrackingService` - 29 edges
5. `AppNavigation()` - 29 edges
6. `Screen` - 25 edges
7. `DriversComponent` - 24 edges
8. `SecurePrefs` - 24 edges
9. `PassengerMapScreen()` - 16 edges
10. `Driver` - 14 edges

## Surprising Connections (you probably didn't know these)
- `AppNavigation()` --calls--> `DriverHomeScreen()`  [INFERRED]
  tuctucMap/app/src/main/java/com/example/tuctucmap/ui/Navigation.kt → tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/driver/DriverHomeScreen.kt
- `AppNavigation()` --calls--> `DriverPendingScreen()`  [INFERRED]
  tuctucMap/app/src/main/java/com/example/tuctucmap/ui/Navigation.kt → tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/driver/DriverPendingScreen.kt
- `AppNavigation()` --calls--> `DriverProfileScreen()`  [INFERRED]
  tuctucMap/app/src/main/java/com/example/tuctucmap/ui/Navigation.kt → tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/driver/DriverProfileScreen.kt
- `AppNavigation()` --calls--> `DriverRegisterScreen()`  [INFERRED]
  tuctucMap/app/src/main/java/com/example/tuctucmap/ui/Navigation.kt → tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/driver/DriverRegisterScreen.kt
- `AppNavigation()` --calls--> `DriverReportsScreen()`  [INFERRED]
  tuctucMap/app/src/main/java/com/example/tuctucmap/ui/Navigation.kt → tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/driver/DriverReportsScreen.kt

## Import Cycles
- None detected.

## Communities (101 total, 24 thin omitted)

### Community 0 - "SupabaseService"
Cohesion: 0.08
Nodes (3): Injectable, AppConfig, SupabaseService

### Community 2 - "💰 Reporte de Costos Operativos y Rentabilidad en Producción: TucTuc Map"
Cohesion: 0.10
Nodes (19): 1. Resumen Ejecutivo, 2.1. Parámetros de Usuarios y Operación, 2.2. Parámetros Reales de Código (`LocationTrackingService.kt` y `SupabaseRepository.kt`), 2. Supuestos Técnicos y Operativos Utilizados, 3.1. Consumo Mensual en Firebase Realtime Database (Telemetría + Despacho `/trips`), 3.2. Consumo Mensual en Supabase (PostgreSQL + Auth + Realtime), 3. Estimación Técnica de Consumo de Infraestructura (50 Conductores / 200 Pasajeros), 4. Desglose Completo de Costos de Producción (USD y Quetzales) (+11 more)

### Community 3 - "tuctucMapDashboard"
Cohesion: 0.05
Nodes (39): src/material-theme.scss, src/styles.css, build, serve, test, builder, configurations, defaultConfiguration (+31 more)

### Community 4 - "Screen"
Cohesion: 0.10
Nodes (21): AdminCrashLogs, AdminHome, AdminPassengers, AdminTrips, DriverHome, DriverLogin, DriverPending, DriverProfile (+13 more)

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

### Community 14 - "SupabaseRepository.kt"
Cohesion: 0.10
Nodes (12): DatabaseError, DataSnapshot, MutableData, SessionStatus, Flow, Transaction, ValueEventListener, ValueEventListener (+4 more)

### Community 15 - "SubscriptionsComponent"
Cohesion: 0.15
Nodes (5): SubscriptionPaymentRequest, ApproveRenewalDialogComponent, RejectRenewalDialogComponent, SubscriptionsComponent, Component

### Community 17 - "SecurePrefs"
Cohesion: 0.11
Nodes (13): Application, SecretKey, SessionManager, Thread, CrashReportDto, CrashReporterManager, Context, TucTucCrashHandler (+5 more)

### Community 18 - "SupabaseRepository"
Cohesion: 0.13
Nodes (7): kotlinx, AppConfig, Driver, SupabaseRepository, TripSummaryDto, UpdateDriverProfileDto, PassengerTripHistoryScreen()

### Community 19 - "db.sql"
Cohesion: 0.12
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
Cohesion: 0.20
Nodes (9): 1. Misión y Cuándo Activar Este Skill, 3. Plantillas de Comunicación para el Agente, 4. Ejemplos de Aplicación en TucTuc Map, 5. Reglas Mandatorias de Salida, Ejemplo 1: Solicitud imprecisa en la App Móvil, Ejemplo 2: Solicitud en el Dashboard, Plantilla A: Cuando la solicitud es ambigua o admite varias alternativas, Plantilla B: Cuando la solicitud es clara pero se detecta una mejora técnica o de UX (+1 more)

### Community 26 - "TripsComponent"
Cohesion: 0.24
Nodes (3): TripsComponent, Component, ViewChild

### Community 27 - "2. Flujo de Ejecución Paso a Paso"
Cohesion: 0.17
Nodes (12): 1. Cuándo Activar este Skill, 2. Flujo de Ejecución Paso a Paso, 3. Entregable al Usuario, A. Nombre de la versión, B. Notas de la versión (Formato Play Store), Paso 1: Determinar la Marca de Tiempo del Último `.aab`, Paso 2: Extraer los Commits Realizados desde esa Fecha, Paso 3: Incrementar la Versión en `tuctucMap/app/build.gradle.kts` (+4 more)

### Community 28 - "Skill: Android App (TucTuc Map)"
Cohesion: 0.20
Nodes (10): 1. Stack Tecnológico & Dependencias, 2. Convenciones Críticas de UI & Jetpack Compose, 3. Mapas, Tiles y Overlays (OSMDroid), 4. Telemetría GPS, Geocerca y Alertas en Segundo Plano, 5. Reglas de Negocio del Conductor y Suscripciones Anti-Fraude, 6. Módulo Administrativo Móvil Nativo (Admin Dashboard Integrado), 7. Secretos (`BuildConfig`), Persistencia de Sesión y Ofuscación R8, 8. Telemetría y Reporte Automático de Errores (Crash Reporting) (+2 more)

### Community 29 - "TucTucMap Dashboard — Plan de Implementación"
Cohesion: 0.25
Nodes (7): Decisiones Técnicas, Estructura de Archivos a Crear/Modificar, Flujo de Datos, Módulos de Angular Material a Importar, Objetivo, TucTucMap Dashboard — Plan de Implementación, Verificación

### Community 31 - "supabase.service.ts"
Cohesion: 0.17
Nodes (13): leaflet, allowedCommonJsDependencies, authGuard(), FinancialKPIs, LiveDriverLocation, PassengerProfile, PaymentMethod, RenewalRequestStatus (+5 more)

### Community 32 - "AppNavigation"
Cohesion: 0.31
Nodes (8): AppNavigation(), DriverRouteToPassenger, Driver, resolveDriverScreen(), DriverLoginScreen(), PassengerLoginScreen(), PassengerProfileScreen(), PassengerRegisterScreen()

### Community 33 - "TucTucMap Dashboard — Tasks"
Cohesion: 0.25
Nodes (7): App Wiring, Core & Config, Drivers Feature, Layout Shell, Styles, TucTucMap Dashboard — Tasks, Verification

### Community 34 - "TuctucMapDashboard"
Cohesion: 0.25
Nodes (7): Additional Resources, Building, Code scaffolding, Development server, Running end-to-end tests, Running unit tests, TuctucMapDashboard

### Community 36 - "Skill: Base de Datos & Backend (Supabase PostgreSQL)"
Cohesion: 0.29
Nodes (6): 1. Arquitectura de Datos & Tablas Principales, 2. Row Level Security (RLS), Triggers y Funciones RPC, 3. Ejecución de Consultas y Modificaciones (`run_sql.js` + `.env`), 4. Buenas Prácticas de Migración, Procedimiento de Uso:, Skill: Base de Datos & Backend (Supabase PostgreSQL)

### Community 37 - "App"
Cohesion: 0.36
Nodes (4): App, appConfig, routes, Component

### Community 38 - "AdminCrashLogsScreen.kt"
Cohesion: 0.31
Nodes (11): AdminCrashLog, AdminCrashLogsScreen(), AnalyticsBarChartCard(), AnalyticsDashboardView(), CleanCrashLogCard(), CrashDetailContent(), formatCrashDate(), KpiMiniCard() (+3 more)

### Community 39 - "2. Protocolo de Ejecución en 6 Pasos"
Cohesion: 0.18
Nodes (11): 1. Activación del Skill, 2. Protocolo de Ejecución en 6 Pasos, 3. Ejemplo Práctico de Interacción, Clasificación Inicial:, Paso 1: Consultar Incidencias Pendientes en Supabase, Paso 2: Análisis y Diagnóstico de Causa Raíz, Paso 3: Presentar Resumen y Solicitar Confirmación (Obligatorio), Paso 4: Aplicar Cambios y Compilar (Tras Aprobación) (+3 more)

### Community 40 - "CrashLogsComponent"
Cohesion: 0.15
Nodes (6): CrashLog, CrashDetailDialogComponent, Component, CrashLogsComponent, Component, ViewChild

### Community 41 - "Skill: Angular Dashboard (TucTuc Map)"
Cohesion: 0.33
Nodes (5): 1. Stack Tecnológico & Dependencias, 2. Autenticación, Secretos y Seguridad Administrativa (RLS & XSS), 3. Módulos y Funcionalidades Clave, 4. Verificación y Compilación Obligatoria, Skill: Angular Dashboard (TucTuc Map)

### Community 42 - "Intent"
Cohesion: 0.06
Nodes (46): Intent, DriverFarePreferences, Context, AdminPaymentRequest, AdminSubscriptionHistory, AppConfig, AdminBusinessConfigTab(), configFieldColors() (+38 more)

### Community 43 - "AdminRepository"
Cohesion: 0.15
Nodes (8): StateFlow, AdminRepository, AppConfig, Flow, Result, PendingCountIdDto, TripDateBounds, UpdateAppConfigDto

### Community 44 - "Contexto del Proyecto: TucTuc Map (Monorepo)"
Cohesion: 0.40
Nodes (5): 1. Estructura del Monorepo y Límites de Subsistemas, 2. Skills de Trabajo Disponibles (`.agents/skills/`), 3. Reglas Obligatorias del Agente (AI Rules), 4. Documentos de Análisis y Planes Estratégicos, Contexto del Proyecto: TucTuc Map (Monorepo)

### Community 45 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 49 - "PassengerMapScreen.kt"
Cohesion: 0.05
Nodes (43): Activity, Animator, AnimatorListenerAdapter, Canvas, GeoPoint, IMyLocationProvider, MapView, MyLocationNewOverlay (+35 more)

### Community 54 - "Result"
Cohesion: 0.16
Nodes (7): getOperationalCalendar(), CompletedTripResponseDto, DriverTodaySummary, Exception, Result, PaymentRequestInsertDto, UpdatePassengerProfileDto

### Community 55 - "Guía Regulatoria, Fiscal (SAT) y Ruta de Formalización — TucTuc Map"
Cohesion: 0.22
Nodes (8): 1. La Gran Ventaja del Modelo de Negocio de TucTuc Map, 2. El Semáforo de Crecimiento: Métricas y Puntos de Inflexión, 3. Análisis de Frentes Regulatorios, 4. Plan de Acción Recomendado, A. Superintendencia de Administración Tributaria (SAT) e Impuestos, B. Municipalidades y Policía Municipal de Tránsito (PMT), C. Términos y Condiciones Contractuales (Desde el Lanzamiento Público), Guía Regulatoria, Fiscal (SAT) y Ruta de Formalización — TucTuc Map

### Community 62 - "Color.kt"
Cohesion: 0.18
Nodes (11): Brush, GoogleAuthCancellationException, GoogleAuthHelper, GoogleUserData, Context, Exception, Result, CompleteDriverGoogleRegistrationDialog() (+3 more)

### Community 63 - "scripts"
Cohesion: 0.33
Nodes (6): scripts, build, ng, start, test, watch

### Community 64 - "AdminDriversTab.kt"
Cohesion: 0.28
Nodes (12): AdminDriver, AdminFinancialStats, AdminDriverCard(), AdminDriversTab(), filterChipColors(), KpiCard(), AppConfig, Color (+4 more)

### Community 65 - "tuctucMapDashboard/package.json"
Cohesion: 0.40
Nodes (4): name, packageManager, private, version

### Community 66 - "PassengersComponent"
Cohesion: 0.33
Nodes (3): PassengersComponent, Component, ViewChild

### Community 67 - "2. Recomendaciones Estratégicas y Técnicas (Contextualizadas para TucTuc Map)"
Cohesion: 0.12
Nodes (16): 1.1 Diagnóstico de Estabilidad Técnica y Rendimiento (Findings), 1.2 Oportunidades de Mejora Identificadas por los Evaluadores, 1. Resumen Ejecutivo del Reporte de Pruebas, 2.1 Implementación de Onboarding / Walkthrough Dinámico por Rol, 2.2 Botón "Compartir App" (Viralidad Hiperlocal vía WhatsApp), 2.3 Botón "Calificar la Aplicación" e In-App Review, 2.4 Renovación Gráfica de la Ficha de Google Play (ASO & Capturas), 2.5 Canal de Soporte y Sugerencias In-App (+8 more)

### Community 68 - ".saveTripRecord"
Cohesion: 0.39
Nodes (3): CompletedTripDto, RoutePoint, TripInfo

### Community 69 - "shell.component.ts"
Cohesion: 0.24
Nodes (4): ChangeNameDialogComponent, Component, ChangePasswordDialogComponent, Component

### Community 70 - "2. Errores Funcionales y Bugs Detectados"
Cohesion: 0.14
Nodes (13): 1. Resumen Ejecutivo, 2. Errores Funcionales y Bugs Detectados, 3. Evaluación de Rendimiento por Subsistema, 4. Hoja de Ruta de Optimización Recomendada, 🔴 [BUG-01] Tormenta de Consultas `O(N)` por Canales Realtime sin Filtro en `public.profiles`, 🔴 [BUG-02] Viajes Broadcast de Pasajeros Invitados Expirados Nunca se Eliminan de Firebase RTDB, 🟠 [BUG-03] Descarga Innecesaria de Polilíneas `route_points` (JSONB) en `getDriverTripHistory()`, 🟠 [BUG-04] Escaneo Completo de Tablas y Truncamiento Silencioso a 1,000 Filas en `AdminRepository` (+5 more)

### Community 73 - "DriverReportsScreen.kt"
Cohesion: 0.20
Nodes (11): DailyReportGroup, DriverReportsPeriodFilter, CUSTOM_RANGE, TODAY, YESTERDAY, DriverReportsScreen(), SelectableDates, DriverWeeklyBarChart() (+3 more)

### Community 74 - "2. Vulnerabilidades Identificadas y Remediación"
Cohesion: 0.14
Nodes (13): 1. Resumen Ejecutivo, 2. Vulnerabilidades Identificadas y Remediación, 3. Controles de Seguridad Positivos Ya Implementados ✅, 4. Plan Priorizado de Remediación de Seguridad, 🛡️ Reporte de Auditoría de Seguridad: TucTuc Map (Android + Backend), 🔴 [SEC-01] CRÍTICA: Falta de Autenticación en Firebase Realtime Database (`driver_locations` y `trips`), 🟠 [SEC-02] ALTA: Exposición Masiva de Datos Personales (PII) en `public.profiles` por Política RLS Permisiva, 🟠 [SEC-03] ALTA: Falsificación de Historial de Viajes en `public.trips` y Manipulación de Montos en Solicitudes de Pago (+5 more)

### Community 75 - "AppDrawer.kt"
Cohesion: 0.08
Nodes (35): Passenger, AdminDrawerItem, CONFIG, CRASH_LOGS, DRIVERS, FLEET_MAP, PASSENGERS, SUBSCRIPTIONS (+27 more)

### Community 76 - "2. El Flujo de Trabajo en 3 Fases (El Ciclo Consultivo)"
Cohesion: 0.33
Nodes (6): 2. El Flujo de Trabajo en 3 Fases (El Ciclo Consultivo), Fase 1: Reformulación y Enriquecimiento Técnico (Prompt Engineering), Fase 2: Detección de Vacíos y Consulta de Dudas Críticas, Fase 3: Recomendación Proactiva de la Mejor Opción, ¿Qué aspectos consultar activamente?, Regla de Interrogación:

### Community 79 - "AdminTripsScreen.kt"
Cohesion: 0.33
Nodes (12): AdminTrip, AdminTripCard(), AdminTripsScreen(), createCircleMarkerDrawable(), DriverFilterDialog(), exportTripsCsv(), formatTripDate(), Color (+4 more)

### Community 80 - "DriverSubscriptionScreen.kt"
Cohesion: 0.31
Nodes (7): calculateRemainingDays(), Driver, ServerClock, SubscriptionRenewalRequest, DriverSubscriptionScreen(), Quadruple, RenewalRequestItem()

### Community 81 - "MainActivity.kt"
Cohesion: 0.33
Nodes (7): Bundle, ComponentActivity, parseIsoTimestamp(), MainActivity, DriverProfileScreen(), formatExpiryDate(), TuctucMapTheme()

### Community 83 - ".loginDriverWithGoogle"
Cohesion: 0.33
Nodes (3): DriverGoogleLoginResult, NeedsProfile, RoleMismatchException

### Community 84 - "AdminPassengersScreen.kt"
Cohesion: 0.43
Nodes (6): AdminPassenger, AdminPassengersScreen(), formatDateShort(), Context, openWhatsApp(), PassengerCard()

### Community 86 - "AdminTripPeriodFilter"
Cohesion: 0.29
Nodes (7): AdminTripPeriodFilter, CUSTOM, LAST_3_DAYS, LAST_5_DAYS, LAST_7_DAYS, TODAY, getPeriodRangeIso()

### Community 87 - "AdminModels.kt"
Cohesion: 0.33
Nodes (5): AdminFinancialSummaryDto, ApprovePaymentRequestRpcParams, DeleteUserByAdminRpcParams, RejectPaymentRequestRpcParams, UserRoleDto

### Community 89 - "DriverTripHistoryScreen.kt"
Cohesion: 0.19
Nodes (10): DriverHistoryPeriodFilter, CUSTOM_RANGE, TODAY, YESTERDAY, DriverTripCard(), DriverTripHistoryScreen(), SelectableDates, Context (+2 more)

### Community 95 - "AcceptTripResult"
Cohesion: 0.40
Nodes (4): AcceptTripResult, AlreadyTaken, Error, Success

## Knowledge Gaps
- **386 isolated node(s):** `{ Client }`, `{ dbConfig }`, `public.app_config`, `fs`, `path` (+381 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **24 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `SupabaseRepository` connect `SupabaseRepository` to `AppNavigation`, `AdminDriversTab.kt`, `.saveTripRecord`, `LocationTrackingService`, `DriverReportsScreen.kt`, `Intent`, `AdminRepository`, `AppDrawer.kt`, `SupabaseRepository.kt`, `AdminTripsScreen.kt`, `DriverSubscriptionScreen.kt`, `MainActivity.kt`, `PassengerMapScreen.kt`, `.loginDriverWithGoogle`, `Result`, `DriverTripHistoryScreen.kt`, `Color.kt`, `AcceptTripResult`?**
  _High betweenness centrality (0.095) - this node is a cross-community bridge._
- **Why does `SecurePrefs` connect `SecurePrefs` to `MainActivity.kt`, `Intent`, `Color.kt`?**
  _High betweenness centrality (0.032) - this node is a cross-community bridge._
- **Why does `AdminRepository` connect `AdminRepository` to `AdminDriversTab.kt`, `AdminCrashLogsScreen.kt`, `Intent`, `AppDrawer.kt`, `AdminTripsScreen.kt`, `SupabaseRepository`, `AdminPassengersScreen.kt`?**
  _High betweenness centrality (0.027) - this node is a cross-community bridge._
- **What connects `{ Client }`, `{ dbConfig }`, `public.app_config` to the rest of the system?**
  _386 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `SupabaseService` be split into smaller, more focused modules?**
  _Cohesion score 0.08408408408408409 - nodes in this community are weakly interconnected._
- **Should `💰 Reporte de Costos Operativos y Rentabilidad en Producción: TucTuc Map` be split into smaller, more focused modules?**
  _Cohesion score 0.1 - nodes in this community are weakly interconnected._
- **Should `tuctucMapDashboard` be split into smaller, more focused modules?**
  _Cohesion score 0.052564102564102565 - nodes in this community are weakly interconnected._