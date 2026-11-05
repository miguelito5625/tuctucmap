# ⚙️ Reporte de Funcionamiento, Errores y Rendimiento: TucTuc Map

**Proyecto:** TucTuc Map (`com.tuctucmap.app` — `versionCode = 13`, `versionName = "1.1.2"`)  
**Fecha de Análisis:** 4 de noviembre de 2026  
**Alcance:** Ciclo de vida Android, telemetría GPS (`LocationTrackingService`), despacho en tiempo real (Firebase RTDB), persistencia y analítica (Supabase PostgreSQL + Realtime), mapas (`OSMDroid` + `OSRM`) y módulo nativo de Administración (`Admin*`).  
**Estado General:** 🟢 **ESTABLE EN FLUJO PRINCIPAL / 🟡 REQUIERE OPTIMIZACIÓN DE ESCALABILIDAD EN CONSULTAS Y LISTENERS**

---

## 1. Resumen Ejecutivo

La versión `1.1.2` (`versionCode = 13`) muestra una base técnica madura en su flujo operativo principal:
* El servicio de telemetría [`LocationTrackingService.kt`](../tuctucMap/app/src/main/java/com/example/tuctucmap/service/LocationTrackingService.kt) implementa correctamente GPS adaptativo (umbral de `8 metros` o *heartbeat* de `45s` activo / `15s` en pausa), filtro anti-rebote GPS (`150 km/h`), envíos delta a Firebase y una única instancia de `TextToSpeech` liberada en `onDestroy`.
* Los mapas `OSMDroid` en [`PassengerMapScreen.kt`](../tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/passenger/PassengerMapScreen.kt), [`DriverHomeScreen.kt`](../tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/driver/DriverHomeScreen.kt), [`DriverRouteToPassengerScreen.kt`](../tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/driver/DriverRouteToPassengerScreen.kt) y [`AdminFleetMapTab.kt`](../tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/admin/AdminFleetMapTab.kt) cuentan con manejo de ciclo de vida (`onResume`/`onPause`/`onDetach`), caché aislada `"TucTucOsmLive"` y bitmaps cacheados en memoria (`MarkerIconUtils` desde `drawable-nodpi`).

No obstante, el análisis exhaustivo línea por línea reveló **8 defectos funcionales y cuellos de botella de rendimiento** (incluyendo tormentas de consultas WebSocket `O(N)`, fugas de nodos huérfanos en Firebase para pasajeros invitados y lecturas completas de tablas sin paginación en el módulo de Administración) que impactarán la fluidez y el consumo de datos al escalar a 50 conductores y 200 pasajeros.

---

## 2. Errores Funcionales y Bugs Detectados

### 🔴 [BUG-01] Tormenta de Consultas `O(N)` por Canales Realtime sin Filtro en `public.profiles`

* **Ubicación:** [`SupabaseRepository.kt` (Líneas 1313–1332 y 1362–1379)](../tuctucMap/app/src/main/java/com/example/tuctucmap/data/repository/SupabaseRepository.kt#L1313-L1379)
* **Descripción del Defecto:**
  Tanto `observeDriverProfile(uid)` como `observePassengerProfile(uid)` se suscriben a los cambios de PostgreSQL en la tabla `profiles` sin especificar un filtro de fila (`filter`):
  ```kotlin
  val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
      table = "profiles"
      // FALTA: filter("id", FilterOperator.EQ, uid)
  }
  ```
  Debido a que la política RLS actual en `public.profiles` permite `SELECT` a cualquier usuario autenticado (`USING (auth.uid() IS NOT NULL)`), **Supabase Realtime transmite cada evento `UPDATE` o `INSERT` de cualquier usuario a TODOS los clientes conectados**. Peor aún, al recibir el evento en `changeFlow`, cada cliente ejecuta inmediatamente un `SELECT` por REST (`supabase.postgrest["profiles"].select { filter { eq("id", uid) } }`).
* **Impacto en Producción (50 conductores + 200 pasajeros):**
  * Cada vez que un nuevo pasajero se registra, un conductor edita su perfil o el job horario `pg_cron` vence 5 suscripciones, los **250 dispositivos conectados reciben el evento por WebSocket y disparan 250 peticiones HTTP REST simultáneas** (¡`5 × 250 = 1,250` consultas en un segundo!).
  * Genera consumo innecesario de batería, datos móviles y cuota de mensajes de Supabase Realtime.
* **Solución Recomendada:**
  Agregar el filtro explícito por clave primaria en ambos métodos:
  ```kotlin
  val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
      table = "profiles"
      filter("id", FilterOperator.EQ, uid)
  }
  ```

---

### 🔴 [BUG-02] Viajes Broadcast de Pasajeros Invitados Expirados Nunca se Eliminan de Firebase RTDB

* **Ubicación:**
  * [`SupabaseRepository.kt` (Líneas 1094–1100)](../tuctucMap/app/src/main/java/com/example/tuctucmap/data/repository/SupabaseRepository.kt#L1094-L1100)
  * [`PassengerMapScreen.kt` (Líneas 1562–1570)](../tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/passenger/PassengerMapScreen.kt#L1562-L1570)
* **Descripción del Defecto:**
  1. Cuando un pasajero sin cuenta (**Invitado**) solicita un tuctuc en modo Broadcast ("Pedir a Cualquiera Cercano"), `requestTrip()` asigna `passengerId = "guest_<timestamp>"` y `driverId = ""`.
  2. El temporizador de expiración de 60 segundos en [`PassengerMapScreen.kt`](../tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/passenger/PassengerMapScreen.kt#L1562-L1570) se lanza dentro del `rememberCoroutineScope()` de la UI (`coroutineScope.launch { delay(60000) ... }`). Si el pasajero invitado cierra la app o apaga la pantalla antes de los 60 segundos, esa corrutina muere y no borra el viaje.
  3. En [`SupabaseRepository.kt`](../tuctucMap/app/src/main/java/com/example/tuctucmap/data/repository/SupabaseRepository.kt#L1096-L1098), la limpieza pasiva ejecutada por los conductores al detectar un viaje con más de 60 segundos tiene la condición:
     ```kotlin
     if (trip.passengerId == currentUserUid || trip.driverId == driverId) {
         child.ref.removeValue()
     }
     ```
     Para un viaje broadcast de un invitado, `trip.passengerId` es `"guest_..."` (nunca coincide con el `currentUserUid` de ningún conductor) y `trip.driverId` es `""` (nunca coincide con `driverId`).
* **Impacto en Producción:**
  Los viajes broadcast de invitados que abandonan la app antes de los 60 segundos **quedan huérfanos para siempre con `status = "requested"` en Firebase RTDB**. Como `observePendingTripsForDriver` usa `.equalTo("requested").limitToLast(25)`, cada viaje huérfano se sigue descargando en el lote de 25 viajes de todos los conductores cada vez que cambia el nodo `/trips`.
* **Solución Recomendada:**
  Permitir en `observePendingTripsForDriver` que cualquier conductor limpie viajes expirados sin conductor asignado (`trip.driverId.isBlank() && now - trip.timestamp > 120_000L`), y además verificar al iniciar `PassengerMapScreen` si el `activeTrip` recuperado ya superó los 60 segundos en estado `"requested"` para limpiarlo de inmediato.

---

### 🟠 [BUG-03] Descarga Innecesaria de Polilíneas `route_points` (JSONB) en `getDriverTripHistory()`

* **Ubicación:**
  * [`SupabaseRepository.kt` (Líneas 819–839)](../tuctucMap/app/src/main/java/com/example/tuctucmap/data/repository/SupabaseRepository.kt#L819-L839)
  * [`DriverTripHistoryScreen.kt` (Líneas 732–735)](../tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/driver/DriverTripHistoryScreen.kt#L732-L735)
* **Descripción del Defecto:**
  En `SupabaseRepository.kt` se definió acertadamente la constante `tripColumnsWithoutRoute` (líneas 745–748) para excluir la columna pesada `route_points` (`JSONB` con hasta ~180 coordenadas GPS por viaje, ~12–15 KB por registro). Se aplicó correctamente en `getPassengerTripHistory()` y en `getDriverWeeklyReportData()`, pero **se omitió en `getDriverTripHistory()`**:
  ```kotlin
  // Línea 821: Ejecuta SELECT * incluyendo route_points y sin limit()
  val result = supabase.postgrest["trips"].select {
      filter {
          eq("driver_id", uid)
          ...
      }
  }.decodeList<CompletedTripResponseDto>()
  ```
  En `DriverTripHistoryScreen.kt`, `route_points` ni siquiera se dibuja en un mapa; solo se usa para mostrar el texto `"Puntos GPS de telemetría: ${trip.route_points.size}"` en el diálogo de detalles.
* **Impacto en Rendimiento y Datos:**
  Un conductor que realizó 100 viajes en la semana descarga **~1.5 MB de JSON crudo** y deserializa ~18,000 objetos `TripRoutePoint` en el hilo de red/CPU cada vez que abre "Historial de Viajes" o cambia el chip de filtro ("Hoy", "Ayer", "Rango").
* **Solución Recomendada:**
  Usar `select(columns = tripColumnsWithoutRoute)` en `getDriverTripHistory()`.

---

### 🟠 [BUG-04] Escaneo Completo de Tablas y Truncamiento Silencioso a 1,000 Filas en `AdminRepository`

* **Ubicación:**
  * [`AdminRepository.kt` — `getFinancialStats()` (Líneas 187–215)](../tuctucMap/app/src/main/java/com/example/tuctucmap/data/repository/AdminRepository.kt#L187-L215)
  * [`AdminRepository.kt` — `getCompletedTrips()` (Líneas 398–401)](../tuctucMap/app/src/main/java/com/example/tuctucmap/data/repository/AdminRepository.kt#L398-L401)
  * [`AdminRepository.kt` — `getPassengers()` (Líneas 524–531)](../tuctucMap/app/src/main/java/com/example/tuctucmap/data/repository/AdminRepository.kt#L524-L531)
* **Descripción del Defecto:**
  El módulo nativo de administración realiza agregaciones en memoria descargando tablas completas desde Supabase:
  1. **En `getPassengers()` (línea 525):** Ejecuta `supabase.postgrest["trips"].select(columns = Columns.list("passenger_id"))` sin filtros ni límites para contar cuántos viajes ha hecho cada pasajero. **PostgREST trunca por defecto las respuestas a 1,000 filas (`max-rows = 1000`)**. Con 50 conductores haciendo ~15 viajes diarios (750 viajes/día), **al segundo día de operación la tabla `trips` superará las 1,000 filas** y el conteo de viajes por pasajero en el panel de administración será silenciosamente incorrecto.
  2. **En `getFinancialStats()` (línea 188):** Descarga todo el historial de `driver_subscriptions` sin filtro de fecha para sumar `totalRevenue` y `monthRevenue` en el teléfono. Cuando existan más de 1,000 renovaciones históricas (~4–5 meses con 50 conductores semanales), `totalRevenue` quedará congelado en las primeras 1,000 filas.
  3. **En `getCompletedTrips()` (línea 398):** Cada vez que el administrador filtra viajes (Hoy, Ayer, Completados, etc.), antes de filtrar los viajes descarga **absolutamente todos los perfiles de la tabla `profiles`** (`supabase.postgrest["profiles"].select(columns = Columns.list("id", "name", "phone"))`) en vez de consultar únicamente los IDs presentes en la página de viajes (`isIn("id", userIds)`).
* **Solución Recomendada:**
  Crear una función RPC en PostgreSQL (`get_admin_financial_summary`) que calcule `SUM(amount)` directamente en el motor SQL, y en `getCompletedTrips()` consultar únicamente los perfiles correspondientes a los viajes cargados (igual que ya se hace bien en `getTripRouteDetails`).

---

### 🟡 [BUG-05] `AdminFleetMapTab` Usa `ValueEventListener` de Árbol Completo en Lugar de `ChildEventListener`

* **Ubicación:** [`AdminFleetMapTab.kt` (Líneas 70–85)](../tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/admin/AdminFleetMapTab.kt#L70-L85)
* **Descripción del Defecto:**
  Mientras que `SupabaseRepository.observeActiveLocations()` fue optimizado con `ChildEventListener` para recibir únicamente el nodo del conductor que se movió, [`AdminFleetMapTab.kt`](../tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/admin/AdminFleetMapTab.kt#L70-L85) conecta un `ValueEventListener` directo a la raíz `driver_locations`:
  ```kotlin
  val listener = object : ValueEventListener {
      override fun onDataChange(snapshot: DataSnapshot) {
          val list = mutableListOf<DriverLocation>()
          for (child in snapshot.children) { ... }
          locations = list
      }
  }
  ```
* **Impacto en Rendimiento y Costos:**
  Con 50 conductores moviéndose cada 5 segundos, el nodo `driver_locations` cambia ~10 veces por segundo. Cada cambio hace que el teléfono del administrador descargue **el árbol completo de los 50 conductores (~12.5 KB × 10/seg = ~125 KB/seg = ~450 MB/hora)** y reconstruya todos los marcadores del `MapView` en el hilo principal.
* **Solución Recomendada:**
  Reutilizar `repository.observeActiveLocations()` (o un `ChildEventListener` incremental con mapa mutable por `driverId`) en `AdminFleetMapTab`.

---

### 🟡 [BUG-06] Radio de Búsqueda (`maxRadiusMeters`) No Reactivo a `AppConfig` en `PassengerMapScreen`

* **Ubicación:** [`PassengerMapScreen.kt` (Línea 416)](../tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/passenger/PassengerMapScreen.kt#L416)
* **Descripción del Defecto:**
  ```kotlin
  val maxRadiusMeters = remember { SupabaseRepository.cachedAppConfig.maxSearchRadiusKm * 1000.0 }
  ```
  En `MainActivity.kt` (línea 106), `repository.getAppConfig()` se lanza de forma asíncrona en `Dispatchers.IO`. Si un pasajero (o invitado) abre `PassengerMapScreen` antes de que finalice `getAppConfig()`, `maxRadiusMeters` memoriza el valor por defecto (`5.0 km = 5000.0 m`) sin llaves en `remember` y nunca se actualiza aunque el administrador haya configurado `10.0 km` o `2.0 km` en `app_config`.
* **Solución Recomendada:**
  Convertir `cachedAppConfig` en un `StateFlow<AppConfig>` dentro de `SupabaseRepository` o cargarlo en un `LaunchedEffect(Unit)` en `PassengerMapScreen`.

---

### 🟡 [BUG-07] Condición de Carrera en `LocationTrackingService.onDestroy()` al Reiniciar Rápido el Servicio

* **Ubicación:** [`LocationTrackingService.kt` (Líneas 518–524)](../tuctucMap/app/src/main/java/com/example/tuctucmap/service/LocationTrackingService.kt#L518-L524)
* **Descripción del Defecto:**
  Cuando el servicio se detiene, `onDestroy()` lanza una corrutina huérfana fuera del `serviceScope`:
  ```kotlin
  if (driverUid.isNotEmpty()) {
      CoroutineScope(Dispatchers.IO).launch {
          repository.removeLocation(driverUid)
      }
  }
  ```
  Si el conductor apaga y vuelve a encender el interruptor de servicio rápidamente (o si el sistema recrea el servicio), `onStartCommand` publica la ubicación inicial mientras la corrutina de `onDestroy()` de la instancia previa termina unos milisegundos después y ejecuta `removeLocation(driverUid)`, borrando al conductor del mapa hasta que se mueva 8 metros o pasen 45 segundos.
* **Solución Recomendada:**
  Verificar `!LocationTrackingService.isServiceRunning` justo antes de ejecutar `locationsRef.child(uid).removeValue()` o sincronizar las operaciones de presencia con una cola secuencial (`Mutex`).

---

### 🔵 [BUG-08] Dependencia del Servidor Público de Demostración de OSRM (`router.project-osrm.org`)

* **Ubicación:** [`OsrmRoutingService.kt` (Línea 20)](../tuctucMap/app/src/main/java/com/example/tuctucmap/data/service/OsrmRoutingService.kt#L20)
* **Descripción del Riesgo Operativo:**
  El servicio de trazado de rutas utiliza `https://router.project-osrm.org/route/v1/driving/`. Aunque el código está muy bien protegido con *throttling* de 25–30 segundos, umbral de desviación de 30 metros, `connectTimeout = 5000ms` y *fallback* automático a línea recta Haversine (por lo que la app **nunca falla ni se cierra** si OSRM cae), la política de uso de `router.project-osrm.org` establece que es un servidor de demostración con límite de ~1 petición/segundo por IP y sin acuerdo de nivel de servicio (SLA).
* **Solución Recomendada:**
  Mantener el *fallback* Haversine actual (que garantiza resiliencia total) y parametrizar la URL base de OSRM en `app_config` por si en el futuro se desea apuntar a una instancia propia en Docker o a un proveedor comercial gratuito (ej. OpenRouteService / Mapbox Free Tier).

---

## 3. Evaluación de Rendimiento por Subsistema

| Subsistema | Calificación | Estado Actual en `v1.1.2` (`versionCode 13`) |
| :--- | :---: | :--- |
| **Memoria y Bitmaps en Mapas (`OSMDroid`)** | 🟢 **9.5 / 10** | Excelente. `MarkerIconUtils` carga WebP desde `drawable-nodpi` y cachea por `densityDpi`. `PassengerLocationOverlay` reutiliza 4 frames de 48×48dp (~36 KB total) y detiene su hilo al salir de pantalla. |
| **Batería y Telemetría GPS (`LocationTrackingService`)** | 🟢 **9.2 / 10** | Muy optimizado. Prioridad `PRIORITY_HIGH_ACCURACY` en activo (5s/3m) y `PRIORITY_BALANCED_POWER_ACCURACY` en pausa (15s), envíos delta a Firebase cada ≥8m o 45s, y `TextToSpeech` único reutilizable. |
| **Tiempos de Arranque (`Cold Start`)** | 🟢 **9.0 / 10** | Rápido. `MainActivity.onCreate` delega la lectura de `SecurePrefs`, `OSMDroid Configuration` y validación de sesión a `Dispatchers.IO` mientras muestra un *splash* ligero. |
| **Consumo de Red en Conductores y Pasajeros** | 🟡 **7.5 / 10** | Bueno en Firebase (`ChildEventListener`), pero penalizado en Supabase por la falta de filtro en los canales Realtime de `profiles` (**BUG-01**) y por la descarga de `route_points` en `getDriverTripHistory` (**BUG-03**). |
| **Escalabilidad del Módulo Admin (`Admin*`)** | 🟠 **6.5 / 10** | Funcional para pocos datos, pero usa `ValueEventListener` de árbol completo en `AdminFleetMapTab` (**BUG-05**), *polling* cada 10s en `AdminMainScreen` y descargas de tabla completa en `AdminRepository` (**BUG-04**). |

---

## 4. Hoja de Ruta de Optimización Recomendada

1. **Filtrar Canales Realtime por ID (`SupabaseRepository.kt`):**
   * Añadir `filter("id", FilterOperator.EQ, uid)` en `observeDriverProfile` y `observePassengerProfile`. **Impacto:** Reduce en **99.6%** el tráfico WebSocket y REST de perfiles con 250 usuarios activos.
2. **Excluir `route_points` en `getDriverTripHistory` (`SupabaseRepository.kt`):**
   * Cambiar `.select { ... }` por `.select(columns = tripColumnsWithoutRoute) { ... }`. **Impacto:** Reduce el peso de carga del historial del conductor de ~1.5 MB a ~25 KB (**98% menos datos**).
3. **Cambiar `ValueEventListener` por `observeActiveLocations()` en `AdminFleetMapTab.kt`:**
   * Reutilizar el flujo incremental de `SupabaseRepository`. **Impacto:** Reduce el tráfico del mapa administrativo de ~450 MB/hora a ~9 MB/hora (**98% de ahorro en Firebase Download**).
4. **Limpieza de Viajes Broadcast de Invitados (`SupabaseRepository.kt`):**
   * Permitir que los conductores eliminen de `/trips` cualquier solicitud con `status == "requested"` y antigüedad `> 120 segundos`. **Impacto:** Mantiene la cola de `/trips` limpia y rápida permanentemente.
5. **Reemplazar Escaneos Completos en `AdminRepository.kt`:**
   * Usar consultas filtradas (`isIn("id", userIds)`) en `getCompletedTrips()` y una función RPC SQL para las sumatorias financieras históricas.

