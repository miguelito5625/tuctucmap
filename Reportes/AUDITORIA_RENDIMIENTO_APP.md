# Auditoría Exhaustiva de Rendimiento Móvil — TucTuc Map (Android)

**Fecha de auditoría:** Octubre 2026  
**Rol del auditor:** Ingeniero Senior de Desarrollo Móvil y Optimización de Rendimiento Android  
**Proyecto auditado:** `tuctucMap` (`com.tuctucmap.gt`, `versionCode = 11`, `versionName = "2.0"`, `minSdk = 24`, `targetSdk = 36`)  
**Alcance:** 100% del código fuente Kotlin (`33` archivos `.kt`, `~14,850` líneas de código), configuración de compilación Gradle/R8, manifiesto, recursos gráficos (`69` archivos en `src/main`) y análisis de artefacto de producción (`app-release.aab`).  
**Restricción aplicada:** Auditoría estrictamente de lectura y diagnóstico técnico comprobado; no se modificó ningún archivo de código fuente.

---

## 1. Resumen del Estado General de Optimización

### 1.1 Diagnóstico Ejecutivo
La aplicación **TucTuc Map** presenta una base arquitectónica funcional bien pensada para el negocio local (telemetría adaptativa GPS por distancia Haversine `>= 8m` o *heartbeat* de `45s`, caché de tiles aislada para cumplir políticas de OSM, y reducción de *Egress* en Firebase RTDB mediante limpieza inmediata de nodos efímeros y migración a Supabase PostgreSQL). Además, el binario de producción (`app-release.aab`) cuenta con `isMinifyEnabled = true` e `isShrinkResources = true`, logrando un peso de descarga real al usuario muy compacto (~**3.54 MB** de módulo `base/` descargable tras descontar los `4.40 MB` de `proguard.map` embebidos en los metadatos del bundle).

Sin embargo, al evaluar el comportamiento en **tiempo de ejecución sobre dispositivos Android de gama baja** (procesadores Cortex-A53 de 4 u 8 núcleos a 1.4–1.8 GHz, GPU Mali-T820 / Adreno 505, **1.5 GB a 2 GB de RAM**, almacenamiento eMMC 5.1 lento y versiones de Android 7.0 a 10), la aplicación sufre de **cuellos de botella críticos en gestión de memoria gráfica (Bitmaps), fugas de ciclo de vida del motor de mapas (`OSMDroid`), recomposiciones masivas en Jetpack Compose y duplicidad de servicios pesados en primer/segundo plano**.

### 1.2 Calificación por Dimensión Técnica

| Dimensión Evaluada | Calificación (0–10) | Estado para Gama Baja | Diagnóstico Principal |
| :--- | :---: | :---: | :--- |
| **Imágenes, Iconos y Memoria Gráfica** | **3.5 / 10** | 🔴 Crítico | `ic_tuc_tuc.png` (`1024x1024` RGB opaco en `drawable/` base) causa picos de **37.7 MB a 67.1 MB de RAM** al decodificarse en pantallas `xxhdpi`/`xxxhdpi` y ejecuta limpieza de píxeles por CPU en el hilo principal (`UI Thread`). |
| **Mapas (OSMDroid) y GPS** | **4.5 / 10** | 🔴 Crítico | Ninguno de los 3 `MapView` invoca `onResume()`, `onPause()`, `disableMyLocation()` ni `onDetach()`. El GPS de `GpsMyLocationProvider` y el bucle de invalidación (`125 ms`) quedan encendidos en segundo plano; `ValueAnimator` se acumulan sin cancelarse en `ActiveDriversMapView`. |
| **Consumo de RAM y CPU** | **5.0 / 10** | 🟠 Deficiente | Doble instancia simultánea del motor nativo `TextToSpeech` (+20–30 MB RAM), doble listener de viajes pendientes en Firebase (`LocationTrackingService` + `DriverHomeScreen`) y miles de asignaciones de `SimpleDateFormat` al abrir reportes. |
| **Velocidad de Inicio (Cold Start) y UI** | **5.5 / 10** | 🟠 Mejorable | I/O de disco síncrono en `MainActivity.onCreate()`, hasta **3 llamadas de red secuenciales** antes de resolver `initialScreen` (hasta `7.0s` en red lenta) y retención forzosa de `2,350 ms` en `AnimatedSplashScreen` con `11` gradientes radiales por frame. |
| **Servicios Externos (Supabase / Firebase)** | **6.5 / 10** | 🟡 Aceptable con fallas | Buena estrategia de *Zero-Idle Egress* en Firebase, pero en Supabase se hace `SELECT *` descargando arreglos JSONB pesados (`route_points`) en reportes donde no se usan, y canales Realtime de `profiles` sin filtro `eq("id", uid)` en el socket. |
| **Tamaño de Binario y Empaquetado (R8)** | **8.0 / 10** | 🟢 Bueno | Bundle `app-release.aab` de `7.94 MB` (`base/dex/classes.dex` = `2.39 MB` comprimido). Penalizado por reglas `-keep` excesivas en `proguard-rules.pro` sobre `SupabaseRepository` y por incluir `material-icons-extended`. |

---

## 2. Problemas Encontrados, Archivos Afectados y Evidencias

A continuación se documentan los hallazgos reales verificados en el código fuente, separando estrictamente los **Problemas Comprobados** (defectos activos en el código actual con impacto medible) de las **Mejoras Potenciales** (optimizaciones preventivas o arquitectónicas).

---

### 2.1 Problemas Comprobados (Evidencia en Código)

#### [HALLAZGO-01] Decodificación de Bitmap gigante (`37–67 MB` de pico en RAM), bucle de píxeles en el Hilo Principal y colisión de estado en `Drawable` compartido
- **Severidad:** 🔴 **CRÍTICA**
- **Archivos afectados:**
  - `tuctucMap/app/src/main/java/com/example/tuctucmap/ui/util/MarkerIconUtils.kt` (líneas `12–106`)
  - `tuctucMap/app/src/main/res/drawable/ic_tuc_tuc.png` (`1024x1024` px, `412.67 KB`, modo `RGB`)
  - `tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/passenger/PassengerMapScreen.kt` (líneas `112–113`, `637`, `698`, `737`)
  - `tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/driver/ActiveDriversMapView.kt` (líneas `87–88`, `281`, `299`)
- **Evidencia técnica:**
  1. **Explosión de RAM por carpeta `drawable/` sin calificador de densidad:** El archivo `res/drawable/ic_tuc_tuc.png` tiene dimensiones reales de **`1024x1024` píxeles** y está ubicado en la carpeta base `res/drawable/`, la cual Android interpreta como densidad base `mdpi` (`160 dpi`, factor `1.0x`). Cuando `ContextCompat.getDrawable(context, R.drawable.ic_tuc_tuc)` se ejecuta en la línea `25` de `MarkerIconUtils.kt`:
     - En un teléfono de gama baja/media con pantalla `xhdpi` (`2.0x`), `BitmapFactory` escala la imagen a `2048x2048` px en `ARGB_8888` = **`16.77 MB` de RAM**.
     - En una pantalla `xxhdpi` (`3.0x`, `1080p`), la escala a `3072x3072` px = **`37.74 MB` de RAM**.
     - En una pantalla `xxxhdpi` (`4.0x`), la escala a `4096x4096` px = **`67.10 MB` de RAM** solo para reducirla inmediatamente después a `144x144` px en la línea `29`. En dispositivos con `128 MB` o `192 MB` de `largeHeap` / heap límite de Dalvik/ART, este pico provoca pausas agresivas del *Garbage Collector* (`GC_FOR_ALLOC` de 80–200 ms) o `OutOfMemoryError`.
  2. **Procesamiento de `41,472` píxeles por CPU en el Hilo Principal (`Main Thread`):** Tanto `getTucTucIcon(context)` (líneas `39–55`) como `getPausedTucTucIcon(context)` (líneas `80–99`) se llaman síncronamente dentro de `remember(context)` durante la primera composición del mapa (`PassengerMapScreen.kt:112–113`). Ambos métodos iteran píxel por píxel (`144 * 144 = 20,736` iteraciones cada uno) en el hilo UI usando `IntArray` para borrar manualmente el fondo blanco (`r > 230 && g > 230 && b > 230`) porque el PNG original fue exportado como `RGB` sin transparencia.
  3. **Tamaño fijo en píxeles (`144x144` px) sin escalar por densidad (`density`):** En `MarkerIconUtils.kt:28`, `val width = 144; val height = 144` usa píxeles físicos fijos en lugar de `dp`. En un dispositivo de gama baja con pantalla `mdpi` (`1.0x`) o `hdpi` (`1.5x`, `720x1280`), `144 px` equivalen a **`144 dp` o `96 dp`**, haciendo que los iconos de los tuctucs se vean gigantescos y se superpongan entre sí.
  4. **Mutación cruzada de `alpha` y `bounds` al reutilizar la misma instancia de `Drawable`:** `cachedTucTucIcon` guarda un único objeto `BitmapDrawable` y se lo asigna a **todos** los marcadores del mapa (`marker.icon = tucTucIcon`). En `PassengerMapScreen.kt:637, 698, 737`, cuando un conductor entra en la ventana de degradación (minutos 3 a 5), se llama a `marker.setAlpha(fraction)`. Internamente en OSMDroid, `Marker.draw()` aplica `mIcon.setAlpha((int)(mAlpha * 255))` sobre la instancia compartida de `Drawable`, alterando la opacidad y el `Callback` de otros marcadores o entre pantallas (`PassengerMapScreen` y `DriverRouteToPassengerScreen`).

---

#### [HALLAZGO-02] Fuga de memoria (Memory Leak), hilos de tiles y GPS perpetuo en segundo plano por ausencia de ciclo de vida en los 3 `MapView` de OSMDroid
- **Severidad:** 🔴 **CRÍTICA**
- **Archivos afectados:**
  - `tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/passenger/PassengerMapScreen.kt` (líneas `551–743`)
  - `tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/driver/ActiveDriversMapView.kt` (líneas `215–326`)
  - `tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/driver/DriverRouteToPassengerScreen.kt` (líneas `210–257`)
  - `tuctucMap/app/src/main/java/com/example/tuctucmap/ui/util/PassengerLocationUtils.kt` (líneas `39–114`)
- **Evidencia técnica:**
  1. **Omisión total de `mapView.onResume()`, `mapView.onPause()` y `mapView.onDetach()`:** Al buscar en todo el proyecto llamadas al ciclo de vida de `org.osmdroid.views.MapView`, **no existe ninguna**. Ninguno de los tres `AndroidView` registra un `DisposableEffect(lifecycleOwner)` para vincular `ON_RESUME` / `ON_PAUSE` ni invoca `mapView.onDetach()` al destruirse el Composable.
  2. **Hardware GPS y bucle de renderizado de `125 ms` activos permanentemente:**
     - En `PassengerMapScreen.kt:564` y `ActiveDriversMapView.kt:228`, se instancia `GpsMyLocationProvider(ctx)` y se llama a `enableMyLocation()` dentro de `factory`.
     - Nunca se llama a `myLocationOverlayInstance?.disableMyLocation()`, ni cuando el pasajero minimiza la aplicación (botón Home / apagar pantalla) ni cuando navega hacia `PassengerProfileScreen`, `PassengerTripHistoryScreen` o `DriverLoginScreen`.
     - Peor aún: en `PassengerLocationUtils.kt:109–113`, el método `PassengerLocationOverlay.drawMyLocation()` programa un redibujado perpetuo del `MapView` cada `125 ms` (8 FPS) o `1200 ms`:
       ```kotlin
       if (now < greetingUntilMs) {
           mapView.postInvalidateDelayed(125L)
       } else {
           mapView.postInvalidateDelayed(1200L)
       }
       ```
       Esto mantiene el hilo UI y el proveedor `GPS_PROVIDER` + `NETWORK_PROVIDER` despiertos de forma indefinida, drenando la batería severamente incluso cuando el pasajero no está usando la app.
  3. **Fuga del `MapTileProviderBase` y caché de bitmaps en RAM:** Cuando el conductor alterna entre la vista de panel y la vista de mapa (`showMapView = true/false` en `DriverHomeScreen.kt:999`) o abre y cierra `DriverRouteToPassengerScreen` en cada viaje aceptado, se crea un nuevo `MapView(ctx)` pero el anterior nunca ejecuta `onDetach()`, dejando su `MapTileProviderBasic`, sus colas de descarga y sus `Bitmap` de tiles retenidos en memoria.

---

#### [HALLAZGO-03] Acumulación descontrolada de `ValueAnimator` concurrentes y recomposiciones completas del mapa al escribir texto
- **Severidad:** 🔴 **CRÍTICA**
- **Archivos afectados:**
  - `tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/driver/ActiveDriversMapView.kt` (líneas `236–324`, especialmente `304–316`)
  - `tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/passenger/PassengerMapScreen.kt` (líneas `156–164`, `576–741`, `1620–1672`)
- **Evidencia técnica:**
  1. **Tormenta de `ValueAnimator` sin cancelar en `ActiveDriversMapView.kt:304–316`:**
     A diferencia de `PassengerMapScreen.kt` (donde se implementó el mapa `activeAnimators` para cancelar animaciones previas y un umbral de `0.00001`), en `ActiveDriversMapView.kt:304–316` se dejó la implementación sin control:
     ```kotlin
     val startPos = existingMarker.position
     val endPos = geoPoint
     if (startPos.latitude != endPos.latitude || startPos.longitude != endPos.longitude) {
         val animator = ValueAnimator.ofFloat(0f, 1f)
         animator.duration = 4000L
         animator.addUpdateListener { animation ->
             // ...
             existingMarker.position = GeoPoint(lat, lon)
             mapView.invalidate()
         }
         animator.start()
     }
     ```
     Cada vez que `ActiveDriversMapView` se recompone mientras un compañero se mueve (por ejemplo, cada vez que cambia `currentLocation` del propio conductor cada 5 segundos o llega un `pendingTrip`), `startPos != endPos` sigue siendo `true` porque el animador anterior de `4,000 ms` aún va a mitad de camino. En consecuencia, se lanza **un segundo, tercer y cuarto `ValueAnimator` sobre el mismo `existingMarker` sin cancelar el anterior**, provocando tirones severos (*jank*) e invalidaciones redundantes del `MapView` a 60 FPS en el hilo principal.
  2. **Re-ejecución del bloque `update` del `MapView` en cada tecla escrita por el pasajero (`PassengerMapScreen.kt:156–164`):**
     Las variables de estado de los formularios de texto (`var tripMessage by remember { mutableStateOf("") }`, `var guestName by remember { mutableStateOf(...) }`, `var guestPhone by remember { mutableStateOf(...) }`) están declaradas en el cuerpo raíz de `PassengerMapScreen` (líneas `156–164`).
     Cuando el pasajero abre el `ModalBottomSheet` para pedir un TucTuc y escribe `"Mercado Central"` (15 pulsaciones de teclado), **cada letra escrita invalida el ámbito de recomposición raíz de `PassengerMapScreen`**, forzando la ejecución del bloque `update = { mapView -> ... }` (líneas `576–741`), el cual itera sobre `activeDrivers`, recalcula la fórmula matemática de `exp(-5.0 * progress)` para la opacidad de los marcadores y ejecuta `mapView.invalidate()` en cada pulsación de tecla, causando retraso perceptible (*input lag*) en el teclado en teléfonos de gama baja.

---

#### [HALLAZGO-04] Duplicidad simultánea de motores `TextToSpeech` (TTS) y de suscripciones Firebase RTDB cuando el conductor está en línea
- **Severidad:** 🔴 **CRÍTICA**
- **Archivos afectados:**
  - `tuctucMap/app/src/main/java/com/example/tuctucmap/service/LocationTrackingService.kt` (líneas `49`, `73–76`, `190–213`, `252–290`)
  - `tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/driver/DriverHomeScreen.kt` (líneas `222–254`, `290–380`, `403–428`)
  - `tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/driver/ActiveDriversMapView.kt` (líneas `118–146`)
- **Evidencia técnica:**
  1. **Dos motores `TextToSpeech` instanciados en paralelo en el mismo proceso:**
     - `DriverHomeScreen.kt:223–254` inicializa una instancia de `TextToSpeech(context)` al entrar a la pantalla del conductor.
     - Cuando el conductor pasa a estado "En Línea", se inicia `LocationTrackingService` (`DriverHomeScreen.kt:129`), el cual en `LocationTrackingService.onCreate()` (línea `190`) instancia **un segundo objeto `TextToSpeech(this)`**.
     - Cada instancia de `TextToSpeech` mantiene su propio enlace IPC (`ServiceConnection`) con el motor de síntesis de voz del sistema (`com.google.android.tts`), duplicando el consumo de memoria nativa (~`15–25 MB` adicionales) y compitiendo por la cola de audio.
  2. **Doble suscripción simultánea a `observePendingTripsForDriver(driverId)`:**
     - `LocationTrackingService.kt:252` abre `repository.observePendingTripsForDriver(driverId)` mientras el conductor está en línea.
     - Simultáneamente, `DriverHomeScreen.kt:307` mantiene abierto otro colector sobre `repository.observePendingTripsForDriver(uid)`.
     - Ambos reciben cada evento de Firebase, deserializan la lista completa de `TripRequest`, ejecutan el filtro de distancia Haversine (`<= 5,000m`) y evalúan la cola de locución TTS por separado (mitigado únicamente por el `MutableSet` compartido `LocationTrackingService.announcedTripIds`).
  3. **Colección infinita en ` companion object` sin límite de tamaño (`LocationTrackingService.kt:75–76`):**
     `val announcedTripIds = mutableSetOf<String>()` y `val ignoredBroadcastTripIds = mutableSetOf<String>()` son conjuntos estáticos a nivel de proceso que **jamás se podan ni tienen límite LRU**, acumulando IDs durante días si el servicio no se mata.
  4. **Suscripción triplicada a `observeActiveDrivers()` al usar el mapa del conductor:**
     En `DriverHomeScreen.kt:276–285`, cuando `isOnline` es `true`, `DriverHomeScreen` observa `repository.observeActiveDrivers()` solo para contar `onlineCount = list.size`. Si el conductor abre la pestaña de mapa (`showMapView = true`), `ActiveDriversMapView.kt:118–145` abre **una segunda suscripción** a `repository.observeActiveDrivers()` (y además llama a `repository.getDriverProfileOnce(loc.driverId)` en un bucle `forEach` por cada conductor que no esté en su mapa local `driverProfiles` en la línea `135`, sin compartir caché con `DriverHomeScreen`).

---

#### [HALLAZGO-05] Tiempo de arranque en frío (Cold Start) penalizado por I/O síncrono en `onCreate`, cadena de red secuencial de hasta `7.0s` y bloqueo artificial de `2.35s` en `AnimatedSplashScreen`
- **Severidad:** 🟠 **ALTA**
- **Archivos afectados:**
  - `tuctucMap/app/src/main/java/com/example/tuctucmap/MainActivity.kt` (líneas `39–182`)
  - `tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/AnimatedSplashScreen.kt` (líneas `47–62`, `180–245`)
  - `tuctucMap/app/src/main/java/com/example/tuctucmap/data/repository/SupabaseRepository.kt` (líneas `290–329`)
- **Evidencia técnica:**
  1. **Operaciones de disco síncronas antes de `setContent` (`MainActivity.kt:40–65`):**
     En `MainActivity.onCreate()`, antes de pintar el primer frame, el hilo principal ejecuta:
     - Lectura de `SharedPreferences("osmdroid")` y `osmConfig.load(applicationContext, osmPrefs)`.
     - Creación de directorios en almacenamiento interno (`File(filesDir, "osmdroid")`, `mkdirs()`).
     - Si la versión de caché (`tile_cache_version < 2`) no coincide, ejecuta **`legacyOsmDir.deleteRecursively()` y `osmBaseDir.deleteRecursively()` de forma síncrona en el hilo principal** (líneas `56–57`), lo que al actualizar la app tras haber acumulado cientos de tiles en disco puede bloquear el hilo principal durante varios segundos y causar un **ANR (`Application Not Responding`)**.
  2. **Cascada de llamadas de red secuenciales en `LaunchedEffect(Unit)` (`MainActivity.kt:79–133`):**
     Para determinar `initialScreen`, el código ejecuta en serie (no en paralelo):
     - **Paso 1 (línea `81`):** `withTimeoutOrNull(1500L) { repository.getAppConfig() }` — incluso para un pasajero invitado (`savedRole == null`) que no necesita validación de sesión, la app espera la respuesta de Supabase antes de asignar `initialScreen = Screen.PassengerMap`.
     - **Paso 2 (línea `95`):** `withTimeoutOrNull(3000L) { SupabaseClient.client.auth.sessionStatus...first() }`.
     - **Paso 3 (línea `107`):** `withTimeoutOrNull(2500L) { repository.getDriverProfileOnce(uid) }`, el cual internamente en `SupabaseRepository.kt:292–300` ejecuta primero `syncServerTimeAndExpire()` (un `POST /rest/v1/rpc/get_server_time`) y luego `select` sobre `profiles` (dos viajes de red HTTP secuenciales).
     - **Impacto en redes móviles 3G/4G débiles:** En condiciones de pérdida de paquetes o latencia alta, los tiempos de espera se suman (`1,500 ms + 3,000 ms + 2,500 ms = 7,000 ms`) antes de que `initialScreen` deje de ser `null`.
  3. **Retención obligatoria de `2,350 ms` y alto costo de GPU/CPU en `AnimatedSplashScreen.kt:58–61, 180–245`:**
     - En `MainActivity.kt:166`, la transición a la pantalla principal depende de `targetState = isSplashFinished && initialScreen != null`.
     - `isSplashFinished` tiene un `delay(2350)` fijo (`AnimatedSplashScreen.kt:59`). Es decir, aunque un pasajero invitado con caché lista resuelva su pantalla en `150 ms`, **la aplicación lo obliga a esperar `2.35 segundos` adicionales**.
     - Durante esos `2.35s`, `AnimatedSplashScreen` ejecuta `5` animaciones simultáneas (incluyendo un `infiniteTransition` a 60 FPS) y un `Canvas` (líneas `180–245`) que en **cada frame** instancia `11` objetos `Brush.radialGradient` nuevos (`1` para el resplandor de la llanta y `2` por cada una de las `5` partículas de humo en el bucle `for (i in 0 until particleCount)`), generando presión innecesaria sobre el *Garbage Collector* durante el arranque.

---

#### [HALLAZGO-06] Descarga innecesaria de columnas JSONB pesadas (`SELECT *` con `route_points`) y canales Realtime sin filtro en servidor
- **Severidad:** 🟠 **ALTA**
- **Archivos afectados:**
  - `tuctucMap/app/src/main/java/com/example/tuctucmap/data/repository/SupabaseRepository.kt` (líneas `700–710`, `760–772`, `829–845`, `1251–1315`)
  - `tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/driver/DriverReportsScreen.kt` (líneas `78–96`)
  - `tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/passenger/PassengerTripHistoryScreen.kt` (líneas `38–40`, `98`)
- **Evidencia técnica:**
  1. **Descarga masiva de `route_points` en `getDriverWeeklyReportData()` (`SupabaseRepository.kt:832`):**
     ```kotlin
     val trips = supabase.postgrest["trips"]
         .select {
             filter {
                 eq("driver_id", driverId)
                 gte("created_at", sinceIsoString)
             }
             order("created_at", Order.DESCENDING)
             limit(300)
         }.decodeList<TripHistoryItem>()
     ```
     Al usar `.select()` vacío (`SELECT *`), PostgREST envía todas las columnas de los hasta `300` viajes de la semana, incluyendo la columna `route_points` (`JSONB` con todas las coordenadas `[lat, lon]` trazadas durante cada recorrido). Sin embargo, en `DriverReportsScreen.kt` **jamás se dibuja ningún mapa ni se lee `trip.route_points`**: la pantalla solo usa `id`, `driver_id`, `status`, `fare_amount`, `distance_km`, `duration_seconds` y `created_at`. Si un conductor realiza 150 viajes a la semana con 60 puntos GPS por viaje, se descargan y deserializan en JSON **`9,000` pares de coordenadas inútiles** (~`250–400 KB` de JSON crudo por apertura de pantalla), desperdiciando datos móviles, RAM y CPU.
  2. **`getPassengerTripHistory()` sin `limit`, sin `order` en SQL y con `SELECT *` (`SupabaseRepository.kt:702–705`):**
     ```kotlin
     val list = supabase.postgrest["trips"]
         .select {
             filter { eq("passenger_id", passengerId) }
         }.decodeList<TripHistoryItem>()
     ```
     A diferencia del historial del conductor (que limita a 50), el historial del pasajero descarga **todos los viajes históricos sin límite (`limit`) ni ordenamiento en base de datos**, y luego los ordena en memoria en el hilo principal en `PassengerTripHistoryScreen.kt:39` (`result.sortedByDescending { it.created_at ?: "" }`).
  3. **Canales Supabase Realtime sobre `profiles` sin filtro `filter("id", FilterOperator.EQ, uid)` (`SupabaseRepository.kt:1256–1267, 1289–1299`):**
     Tanto `observeDriverProfile(uid)` como `observePassengerProfile(uid)` crean un canal `postgresChangeFlow<PostgresAction>("public") { table = "profiles" }` sin especificar el filtro por clave primaria `id=eq.$uid`. Además, al suscribirse (`SupabaseRepository.kt:1261–1262`), `observeDriverProfile` vuelve a invocar `syncServerTimeAndExpire()` y `getAppConfig()`, repitiendo las mismas llamadas HTTP que ya ejecutó `MainActivity` segundos antes.
  4. **Creación de `CoroutineScope(Dispatchers.IO)` huérfanos (`SupabaseRepository.kt:321, 335`):**
     En `getDriverProfileOnce` y `getPassengerProfileOnce`, para guardar el perfil en caché se hace `CoroutineScope(Dispatchers.IO).launch { SecurePrefs.saveCachedProfile(...) }`, instanciando un `Job` nuevo no supervisado ni vinculado al ciclo de vida en cada lectura de perfil.

---

#### [HALLAZGO-07] Costo extremo de parseo de fechas (`SimpleDateFormat`) y ausencia de `key` en listas `LazyColumn`
- **Severidad:** 🟠 **ALTA**
- **Archivos afectados:**
  - `tuctucMap/app/src/main/java/com/example/tuctucmap/data/model/Driver.kt` (líneas `166–199`)
  - `tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/driver/DriverReportsScreen.kt` (líneas `232`, `292–311`, `331`, `827–830`)
  - `tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/driver/DriverTripHistoryScreen.kt` (líneas `98–103`, `562–572`)
  - `tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/passenger/PassengerTripHistoryScreen.kt` (línea `98`)
- **Evidencia técnica:**
  1. **Complejidad $O(7 \times N)$ de `parseIsoTimestamp` e instanciación masiva de `SimpleDateFormat`:**
     - En `DriverReportsScreen.kt`, el campo `trip.created_at` (String ISO) se parsea repetidamente con `parseIsoTimestamp(it.created_at)`:
       - 1 vez por viaje al ordenar `allTrips` (línea `232`).
       - 1 vez por viaje al calcular `filteredTrips` (línea `293`).
       - **7 veces por cada viaje** dentro del bucle `(6 downTo 0).map` de `weeklyDayStats` (líneas `298–304`), donde `completedTrips.filter { val ms = parseIsoTimestamp(it.created_at) ... }` re-parsea todos los viajes completados para cada uno de los 7 días de la semana.
       - 1 vez por viaje al agrupar `dailyGroups` (línea `331`).
       - **En cada fila visible del `LazyColumn` en cada recomposición** (líneas `828–830`):
         ```kotlin
         items(filteredTrips) { trip ->
             val tripMs = parseIsoTimestamp(trip.created_at) ?: 0L
             val dateStr = if (tripMs > 0L) SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(tripMs)) else "--/--"
         ```
     - En dispositivos con Android 7.0 / 7.1 (`API 24–25`, incluidos en `minSdk = 24`), `parseIsoTimestamp` (`Driver.kt:180–196`) entra al bloque `else` e instancia un arreglo de **7 objetos `SimpleDateFormat` nuevos en cada llamada**. Con 150 viajes semanales, abrir `DriverReportsScreen` ejecuta **`>1,500` llamadas a `parseIsoTimestamp`** y crea hasta **`10,500` instancias de `SimpleDateFormat`**, congelando el hilo UI durante varios cientos de milisegundos.
  2. **Listas `LazyColumn` sin `key` estable (`DriverReportsScreen.kt:827`, `PassengerTripHistoryScreen.kt:98`):**
     Ambas listas usan `items(filteredTrips)` e `items(trips)` sin definir `key = { it.id }`. Cuando el conductor cambia el chip de filtro (`Hoy`, `7 Días`, `Todo`, `Completados`, `Cancelados`), Compose no puede identificar qué ítems permanecen y destruye/recrea las composiciones por posición.

---

#### [HALLAZGO-08] Acceso repetitivo sin caché al `AndroidKeyStore` y triple cifrado en `SecurePrefs`
- **Severidad:** 🟠 **ALTA**
- **Archivos afectados:**
  - `tuctucMap/app/src/main/java/com/example/tuctucmap/data/SecurePrefs.kt` (líneas `44–94`, `123–161`)
- **Evidencia técnica:**
  1. **Recarga del `AndroidKeyStore` por IPC en cada lectura/escritura:**
     En `SecurePrefs.kt:44–45`, el método `getOrCreateSecretKey()` ejecuta `KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }` y `keyStore.getEntry(KEY_ALIAS, null)` **en cada llamada a `encrypt()` o `decrypt()`**, sin guardar la referencia `SecretKey` en una variable `@Volatile private var cachedSecretKey: SecretKey? = null`. En Android, consultar el `AndroidKeyStore` implica comunicación IPC con el demonio `keystore`/`credstore` respaldado por hardware (TEE/StrongBox), lo cual en dispositivos de gama baja toma entre `5 ms` y `25 ms` por operación.
  2. **Triple cifrado redundante en `saveUserSession` (`SecurePrefs.kt:123–135`):**
     ```kotlin
     fun saveUserSession(context: Context, sessionJson: String, accessToken: String, refreshToken: String) {
         getPrefs(context).edit()
             .putString("supabase_session", encrypt(sessionJson))
             .putString("supabase_access", encrypt(accessToken))
             .putString("supabase_refresh", encrypt(refreshToken))
             .apply()
     }
     ```
     El objeto `sessionJson` (`Json.encodeToString(session)`) **ya contiene `accessToken` y `refreshToken`**, y de hecho `getAccessToken()` y `getRefreshToken()` nunca son llamados en ningún lugar del proyecto. Sin embargo, cada vez que se guarda o refresca la sesión (`MainActivity.kt:99`, `SupabaseRepository.kt:112, 165, 224, 267`), se realizan **3 llamadas seguidas al KeyStore + cifrado AES-GCM** en lugar de 1.

---

#### [HALLAZGO-09] Icono de notificación inválido (`1024x1024` RGB opaco) y recreación de `NotificationChannel` en cada actualización
- **Severidad:** 🟡 **MEDIA**
- **Archivos afectados:**
  - `tuctucMap/app/src/main/java/com/example/tuctucmap/service/LocationTrackingService.kt` (líneas `292–316`, `424–458`)
- **Evidencia técnica:**
  1. **`.setSmallIcon(R.drawable.ic_tuc_tuc)` usa un PNG RGB opaco de `1024x1024` px (`412.67 KB`):**
     En `LocationTrackingService.kt:300` (notificación de alerta de viaje) y línea `447` (notificación permanente del Foreground Service), se pasa `R.drawable.ic_tuc_tuc` como `smallIcon`.
     Desde Android 5.0 (API 21+, es decir, el **100% de los dispositivos soportados** por `minSdk = 24`), el sistema operativo ignora los canales de color RGB del `smallIcon` y utiliza exclusivamente el **canal alfa (transparencia)** para dibujar una silueta monocromática de `24x24 dp` en la barra de estado. Como `ic_tuc_tuc.png` es modo `RGB` sin canal alfa (100% opaco), Android dibuja un **cuadrado blanco sólido** en la barra de estado y obliga al proceso del sistema `SystemUI` a transferir y decodificar un recurso de `1024x1024` píxeles.
  2. **Llamada redundante a `createNotificationChannel` en `buildNotification()` (`LocationTrackingService.kt:426–434`):**
     Aunque el canal `location_tracking_channel` ya se crea en `onCreate()` (línea `170`), el método `buildNotification(contentText)` vuelve a instanciar `NotificationChannel(...)` y a llamar por IPC a `notificationManager.createNotificationChannel(channel)` cada vez que se actualiza el texto de la notificación (`updateNotification()`).

---

#### [HALLAZGO-10] Reglas de ProGuard / R8 excesivamente permisivas, dependencia pesada `material-icons-extended` y código muerto
- **Severidad:** 🟡 **MEDIA**
- **Archivos afectados:**
  - `tuctucMap/app/proguard-rules.pro` (líneas `12–21`)
  - `tuctucMap/app/build.gradle.kts` (línea `105`)
  - `tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/passenger/PassengerHomeScreen.kt` (317 líneas, `@Deprecated`)
  - `tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/OnboardingScreen.kt` (167 líneas, sin referencias en `Navigation.kt`)
- **Evidencia técnica:**
  1. **Desactivación de optimización R8 sobre el repositorio completo (`proguard-rules.pro:16–17, 21`):**
     ```proguard
     -keep class com.example.tuctucmap.data.repository.** { *; }
     -keepclassmembers class com.example.tuctucmap.** {
         *** Companion;
     }
     ```
     La clase `SupabaseRepository` (`1,317` líneas, `50 KB` de código fuente) no es una clase serializable por reflexión: las clases serializadas por `kotlinx.serialization` ya están en `data.model.**` o son clases privadas internas anotadas con `@Serializable` (que la regla de la línea `20` ya protege con `-keepclassmembers class * { @kotlinx.serialization.Serializable *; }`). Forzar `-keep class ...data.repository.** { *; }` impide que R8 haga *inlining*, elimine funciones muertas (como `getPassengerRequests`, `acceptRequest`, `completeTrip` heredadas de prototipos previos en `SupabaseRepository.kt:1165–1213`) u ofusque nombres internos.
  2. **Inclusión de `androidx.compose.material:material-icons-extended` (`build.gradle.kts:105`):**
     Esta librería contiene miles de iconos vectoriales que penalizan el tiempo de compilación, el análisis de R8 y el tamaño del APK `debug` (~`15–20 MB` extra en debug). En todo el código fuente solo se utilizan **24 iconos** fuera de `material-icons-core` (como `ElectricRickshaw`, `CardMembership`, `Assessment`, `NearMe`, `ZoomOutMap`, `ReceiptLong`, etc.), los cuales pueden importarse como Vector Drawables XML (`<1 KB` cada uno) eliminando por completo la dependencia.
  3. **Pantallas huérfanas y métodos sin uso:**
     - `PassengerHomeScreen.kt` (`317` líneas, marcada `@Deprecated`) y `OnboardingScreen.kt` (`167` líneas) no están conectadas en `AppNavigation` (`Navigation.kt`).
     - En `SupabaseRepository.kt:1165–1213`, las funciones `getPassengerRequests()`, `acceptRequest()` y `completeTrip()` consultan una tabla inexistente `"ride_requests"` que no forma parte de la arquitectura actual (`trips`) y nunca son invocadas.

---

#### [HALLAZGO-11] Caché de tiles OSMDroid fija en `150 MB` sin verificar almacenamiento libre ni RAM del dispositivo
- **Severidad:** 🟡 **MEDIA**
- **Archivos afectados:**
  - `tuctucMap/app/src/main/java/com/example/tuctucmap/MainActivity.kt` (líneas `50–51`)
  - `tuctucMap/app/src/main/java/com/example/tuctucmap/data/service/OsrmRoutingService.kt` (líneas `75–86`)
- **Evidencia técnica:**
  - En `MainActivity.kt:50–51`, se configura de forma estática:
    ```kotlin
    osmConfig.tileFileSystemCacheMaxBytes = 150L * 1024L * 1024L // 150 MB
    osmConfig.tileFileSystemCacheTrimBytes = 120L * 1024L * 1024L // 120 MB
    ```
    En teléfonos Android de gama baja con `8 GB` o `16 GB` de almacenamiento interno (donde el usuario suele tener `< 500 MB` libres), reservar hasta `150 MB` en `filesDir` (almacenamiento interno privado no purgable automáticamente por el sistema a diferencia de `cacheDir`) puede disparar la alerta del sistema *"Espacio de almacenamiento insuficiente"*.
  - Asimismo, no se ajusta `osmConfig.cacheMapTileCount` ni `osmConfig.tileDownloadThreads` cuando `ActivityManager.isLowRamDevice` es `true`.

---

### 2.2 Mejoras Potenciales (Preventivas y Arquitectónicas)

1. **Perfiles Base (`Baseline Profiles`) personalizados para el código de la app:**
   - En la inspección de `app-release.aab`, existe `BUNDLE-METADATA/com.android.tools.build.profiles/baseline.prof` (`10.23 KB`) aportado por las librerías de Jetpack Compose, pero el proyecto no cuenta con un módulo `:baselineprofile` propio que precompile (AOT) las rutas críticas de `com.example.tuctucmap` (`MainActivity`, `PassengerMapScreen`, `DriverHomeScreen`). Agregar un `BaselineProfile` propio reduce el tiempo de inicio y el *jank* inicial de Compose entre un **15% y un 30%** en dispositivos con CPUs lentas.
2. **Unificación del cliente HTTP en `OsrmRoutingService`:**
   - `OsrmRoutingService.kt:75` abre conexiones con `java.net.HttpURLConnection` mientras que el proyecto ya carga en memoria el motor `io.ktor:ktor-client-cio` (`build.gradle.kts:88`). Además, no guarda en caché en memoria la última ruta calculada si las coordenadas de origen/destino variaron menos de `15 metros`.
3. **Extracción de estado a `ViewModel` con `StateFlow`:**
   - Actualmente `PassengerMapScreen` (`1,894` líneas) y `DriverHomeScreen` (`2,469` líneas) concentran decenas de `remember { mutableStateOf(...) }` y lógica de negocio directamente dentro del `@Composable`. Si ocurre un cambio de configuración no bloqueado (p. ej., cambio de tamaño de fuente, idioma o recreación de actividad por falta de memoria del sistema cuando el usuario sale a WhatsApp y regresa), todo el estado en `remember` se pierde y se re-ejecutan todas las consultas a Supabase.

---

## 3. Análisis de Imágenes e Iconos (Dimensiones, Formatos y Pesos Reales)

Se analizaron los **19 archivos de imagen rasterizada** presentes en `tuctucMap/app/src/main` y su impacto tanto en el disco/APK como en la memoria RAM al decodificarse como `Bitmap` (`ARGB_8888`, 4 bytes por píxel):

### 3.1 Inventario Completo de Recursos Gráficos

| Archivo | Ubicación en Proyecto | Formato | Dimensiones (px) | Modo Color | Peso en Fuente | Peso en `.aab` (Crunched) | RAM en `xxxhdpi` (`4x`) | Diagnóstico y Nivel de Optimización |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :--- |
| [`ic_tuc_tuc.png`](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/tuctucMap/app/src/main/res/drawable/ic_tuc_tuc.png) | `res/drawable/` (base `mdpi`) | **PNG** | **`1024 x 1024`** | **`RGB`** (Sin transp.) | **412.67 KB** | **388.22 KB** | **67.11 MB** (`4096x4096`) | 🔴 **Crítico:** Representa el **42%** de todos los recursos del bundle. Al estar en `drawable/` sin `-nodpi`, Android multiplica sus dimensiones por la densidad de pantalla antes de reducirlo a `144x144` px. Carece de canal alfa y se usa erróneamente como `smallIcon` de notificación. |
| [`ic_launcher-playstore.png`](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/tuctucMap/app/src/main/ic_launcher-playstore.png) | `app/src/main/` (raíz) | **PNG** | `512 x 512` | `RGBA` | **304.02 KB** | *0 KB (Excluido)* | N/A | 🟡 **Medio:** Artefacto de tienda ubicado dentro de `src/main/`. Aunque AAPT2 no lo empaqueta en el `.aab`, debe moverse fuera del árbol de compilación o eliminarse si ya está en Play Console. |
| [`ic_launcher_foreground.webp`](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/tuctucMap/app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.webp) | `res/mipmap-xxxhdpi/` | **WebP** | `432 x 432` | `RGB` (Opaco) | **101.17 KB** | **101.20 KB** | 0.75 MB | 🟠 **Mejorar:** Es un WebP sin pérdida/alta calidad opaco (`RGB`). Al ser un `foreground` de Adaptive Icon que no usa transparencia (contiene su propio fondo), puede comprimirse en WebP calidad 85% reduciendo su peso de `101 KB` a `~18 KB`. |
| [`splash_tuc_tuc.png`](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/tuctucMap/app/src/main/res/drawable/splash_tuc_tuc.png) | `res/drawable/` (base `mdpi`) | **PNG** | `354 x 276` | `RGBA` | **95.97 KB** | **91.15 KB** | **6.25 MB** (`1416x1104`) | 🟠 **Alto:** En formato PNG sin convertir a WebP y alojado en `drawable/` base (`mdpi`). En pantallas `xxxhdpi` se escala x4 en RAM (`6.25 MB`) para mostrarse en un `Box` de `230.dp`. Mover a `drawable-xxhdpi` o `drawable-nodpi` y convertir a WebP (`~16 KB`). |
| [`ic_quetzal_passenger.png`](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/tuctucMap/app/src/main/res/drawable/ic_quetzal_passenger.png) | `res/drawable/` (base `mdpi`) | **PNG** | `280 x 280` | `RGBA` | **68.59 KB** | **62.61 KB** | **5.02 MB** (`1120x1120`) | 🟠 **Alto:** Alojado en `drawable/` base (`mdpi`). En `PassengerLocationUtils.kt:55` se escala manualmente a `68 dp`, pero `BitmapFactory.decodeResource` primero lo infla según la densidad de pantalla (hasta `5.02 MB` en RAM). Convertir a WebP (`~12 KB`) y mover a `drawable-nodpi`. |
| `ic_launcher_foreground.webp` | `res/mipmap-xxhdpi/` | WebP | `324 x 324` | `RGB` | **56.51 KB** | **56.53 KB** | 0.42 MB | 🟡 **Medio:** Reducible a `~12 KB` con compresión WebP estándar (Q=85). |
| `ic_launcher_round.webp` | `res/mipmap-xxxhdpi/` | WebP | `192 x 192` | `RGBA` | **25.51 KB** | **25.52 KB** | 0.15 MB | 🟢 **Óptimo:** Dimensiones y formato adecuados. |
| `ic_launcher_foreground.webp` | `res/mipmap-xhdpi/` | WebP | `216 x 216` | `RGB` | **24.93 KB** | **24.94 KB** | 0.19 MB | 🟢 **Aceptable:** Reducible a `~7 KB`. |
| `ic_launcher.webp` | `res/mipmap-xxxhdpi/` | WebP | `192 x 192` | `RGBA` | **18.52 KB** | **18.53 KB** | 0.15 MB | 🟢 **Óptimo:** Dimensiones y formato adecuados. |
| `ic_launcher_round.webp` | `res/mipmap-xxhdpi/` | WebP | `144 x 144` | `RGBA` | **16.42 KB** | **16.43 KB** | 0.08 MB | 🟢 **Óptimo.** |
| `ic_launcher_foreground.webp` | `res/mipmap-hdpi/` | WebP | `162 x 162` | `RGB` | **14.26 KB** | **14.26 KB** | 0.10 MB | 🟢 **Óptimo.** |
| `ic_launcher.webp` | `res/mipmap-xxhdpi/` | WebP | `144 x 144` | `RGBA` | **11.11 KB** | **11.11 KB** | 0.08 MB | 🟢 **Óptimo.** |
| `ic_launcher_round.webp` | `res/mipmap-xhdpi/` | WebP | `96 x 96` | `RGBA` | **8.86 KB** | **8.86 KB** | 0.04 MB | 🟢 **Óptimo.** |
| `ic_launcher_foreground.webp` | `res/mipmap-mdpi/` | WebP | `108 x 108` | `RGB` | **6.59 KB** | **6.60 KB** | 0.05 MB | 🟢 **Óptimo.** |
| `ic_launcher.webp` | `res/mipmap-xhdpi/` | WebP | `96 x 96` | `RGBA` | **6.27 KB** | **6.28 KB** | 0.04 MB | 🟢 **Óptimo.** |
| `ic_launcher_round.webp` | `res/mipmap-hdpi/` | WebP | `72 x 72` | `RGBA` | **5.55 KB** | **5.56 KB** | 0.02 MB | 🟢 **Óptimo.** |
| `ic_launcher.webp` | `res/mipmap-hdpi/` | WebP | `72 x 72` | `RGBA` | **4.02 KB** | **4.02 KB** | 0.02 MB | 🟢 **Óptimo.** |
| `ic_launcher_round.webp` | `res/mipmap-mdpi/` | WebP | `48 x 48` | `RGBA` | **2.75 KB** | **2.75 KB** | 0.01 MB | 🟢 **Óptimo.** |
| `ic_launcher.webp` | `res/mipmap-mdpi/` | WebP | `48 x 48` | `RGBA` | **2.05 KB** | **2.05 KB** | 0.01 MB | 🟢 **Óptimo.** |

### 3.2 Resumen Cuantitativo del Impacto en Recursos Gráficos
- **Peso actual de las 3 imágenes PNG en `res/drawable/`:** `577.23 KB` en código fuente (`541.98 KB` dentro del `.aab`).
- **Peso tras optimización (WebP con transparencia preprocesada en `drawable-nodpi` y dimensiones exactas):**
  - `ic_tuc_tuc.webp` recortado a `192x192` px en `RGBA` (con el fondo blanco ya eliminado en tiempo de diseño): **`~9.5 KB`** (reducción del **97.7%**, de `412.67 KB` a `9.5 KB`).
  - `splash_tuc_tuc.webp` (`354x276` px, `RGBA`, WebP Q=85): **`~15.0 KB`** (reducción del **84.3%**).
  - `ic_quetzal_passenger.webp` (`204x204` px — `68dp` @ `xxxhdpi`, `RGBA`, WebP Q=85): **`~11.0 KB`** (reducción del **84.0%**).
- **Ahorro de RAM pico en tiempo de ejecución:** Se eliminan hasta **`78.38 MB` de asignaciones temporales de Bitmaps** (`67.11 MB + 6.25 MB + 5.02 MB`) y se reduce a **0 ms** el procesamiento de píxeles en CPU durante la carga del mapa.

---

## 4. Recomendaciones Técnicas Concretas para Mejorar el Rendimiento

A continuación se detallan las soluciones técnicas exactas sobre el código real del proyecto:

### 4.1 Optimización de `MarkerIconUtils.kt` e Imagen `ic_tuc_tuc`
1. **Acción sobre el recurso gráfico:**
   - Pre-eliminar el fondo blanco de `ic_tuc_tuc.png` en una herramienta de edición o script de build, redimensionarlo a **`192x192` px** con canal alfa (`RGBA`), exportarlo como **WebP sin pérdida o Q=90** (`~9 KB`) y moverlo a **`res/drawable-nodpi/ic_tuc_tuc.webp`**.
   - Crear un Vector Drawable monocromático `res/drawable/ic_stat_tuctuc.xml` (`24dp x 24dp`, blanco `#FFFFFF` con fondo transparente) exclusivamente para `.setSmallIcon(R.drawable.ic_stat_tuctuc)` en `LocationTrackingService.kt:300, 447`.
2. **Refactorización de `MarkerIconUtils.kt`:**
   - Cachear el `Bitmap` inmutable escalado según los `dp` reales de la pantalla (`48.dp`), eliminar el doble bucle de `20,736` píxeles en CPU (o usar un `ColorMatrixColorFilter` de hardware para el estado pausado en escala de grises) y retornar **una nueva instancia ligera de `BitmapDrawable(context.resources, bitmap)` por cada marcador** para que `marker.setAlpha(...)` no contamine a los demás tuctucs del mapa:

```kotlin
package com.example.tuctucmap.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.example.tuctucmap.R
import kotlin.math.roundToInt

object MarkerIconUtils {
    @Volatile private var cachedActiveBitmap: Bitmap? = null
    @Volatile private var cachedPausedBitmap: Bitmap? = null

    private fun getOrCreateActiveBitmap(context: Context): Bitmap {
        cachedActiveBitmap?.let { if (!it.isRecycled) return it }
        synchronized(this) {
            cachedActiveBitmap?.let { if (!it.isRecycled) return it }
            val targetSizePx = (48f * context.resources.displayMetrics.density).roundToInt().coerceIn(64, 192)
            val options = BitmapFactory.Options().apply { inScaled = false }
            val raw = BitmapFactory.decodeResource(context.resources, R.drawable.ic_tuc_tuc, options)
            val scaled = Bitmap.createScaledBitmap(raw, targetSizePx, targetSizePx, true)
            if (raw != scaled) raw.recycle()
            cachedActiveBitmap = scaled
            return scaled
        }
    }

    fun getTucTucIcon(context: Context): Drawable {
        // Nueva envoltura BitmapDrawable (~48 bytes) compartiendo el mismo Bitmap en RAM:
        // evita que marker.setAlpha() afecte a todos los demás marcadores del mapa.
        return BitmapDrawable(context.resources, getOrCreateActiveBitmap(context))
    }

    fun getPausedTucTucIcon(context: Context): Drawable {
        val pausedBmp = cachedPausedBitmap?.takeIf { !it.isRecycled } ?: synchronized(this) {
            val active = getOrCreateActiveBitmap(context)
            val grayBmp = Bitmap.createBitmap(active.width, active.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(grayBmp)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
                alpha = 180
            }
            canvas.drawBitmap(active, 0f, 0f, paint)
            cachedPausedBitmap = grayBmp
            grayBmp
        }
        return BitmapDrawable(context.resources, pausedBmp)
    }
}
```

---

### 4.2 Gestión Correcta del Ciclo de Vida de `MapView` y Cancelación de Animadores
En `PassengerMapScreen.kt`, `ActiveDriversMapView.kt` y `DriverRouteToPassengerScreen.kt`, vincular el `MapView` y el `MyLocationNewOverlay` al `LocalLifecycleOwner` y liberar los recursos en `onDispose`:

```kotlin
val lifecycleOwner = LocalLifecycleOwner.current
DisposableEffect(lifecycleOwner, mapViewInstance, myLocationOverlayInstance) {
    val map = mapViewInstance ?: return@DisposableEffect onDispose {}
    val observer = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_RESUME -> {
                map.onResume()
                if (hasLocationPermission) myLocationOverlayInstance?.enableMyLocation()
            }
            Lifecycle.Event.ON_PAUSE -> {
                myLocationOverlayInstance?.disableMyLocation()
                map.onPause()
            }
            else -> {}
        }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
        lifecycleOwner.lifecycle.removeObserver(observer)
        activeAnimators.values.forEach { it.cancel() }
        activeAnimators.clear()
        myLocationOverlayInstance?.disableMyLocation()
        map.onPause()
        map.onDetach()
    }
}
```
Adicionalmente, en **`ActiveDriversMapView.kt:304–316`**, aplicar exactamente el mismo mapa `val activeAnimators = remember { mutableMapOf<String, ValueAnimator>() }` y el filtro `abs(latDiff) > 0.00001 || abs(lonDiff) > 0.00001` que ya existe en `PassengerMapScreen.kt:671–688`.

---

### 4.3 Aislamiento de Estado en Formularios para Evitar Recomposiciones del Mapa
En `PassengerMapScreen.kt:156–164, 1620–1672`:
- Extraer el contenido del `ModalBottomSheet` (`showRequestSheet`) a un Composable independiente `@Composable private fun TripRequestBottomSheetContent(...)` que mantenga internamente sus propios estados locales `var tripMessage by remember { mutableStateOf("") }`, `var guestName by remember { mutableStateOf(initialGuestName) }` y `var guestPhone by remember { mutableStateOf(initialGuestPhone) }`.
- De esta forma, escribir en los `OutlinedTextField` solo recompondrá el interior del `ModalBottomSheet` y **nunca disparará el bloque `update` del `AndroidView(MapView)`**.

---

### 4.4 Consolidación de `TextToSpeech` y Listeners en Modo Conductor
1. **Unificar `TextToSpeech` y `observePendingTripsForDriver`:**
   - Dado que el conductor solo puede recibir y escuchar solicitudes cuando está **En Línea** (`isOnline == true`), y estar En Línea siempre ejecuta `LocationTrackingService`, centralizar el motor `TextToSpeech` en un singleton ligero `DriverVoiceAnnouncer` o exclusivamente dentro de `LocationTrackingService`, eliminando el segundo motor `TextToSpeech` de `DriverHomeScreen.kt:222–254`.
   - Exponer la lista de viajes pendientes filtrados desde `LocationTrackingService` mediante un `StateFlow<List<TripRequest>>` en memoria hacia `DriverHomeScreen`, eliminando la doble suscripción simultánea a Firebase RTDB (`observePendingTripsForDriver`).
   - Limitar `announcedTripIds` e `ignoredBroadcastTripIds` a los últimos `200` IDs (por ejemplo, usando ` object : LinkedHashMap<String, Boolean>(200, 0.75f, true)` o limpiando el conjunto cuando `size > 200`).

---

### 4.5 Aceleración del Inicio en Frío (`Cold Start`) en `MainActivity.kt` y `AnimatedSplashScreen.kt`
1. **Mover la configuración de disco de OSMDroid a `Dispatchers.IO`** o ejecutar `deleteRecursively()` en segundo plano para no bloquear `MainActivity.onCreate()`.
2. **Paralelizar y desacoplar la resolución de ruta inicial (`MainActivity.kt:79–133`):**
   - Si `savedRole == null` (pasajero invitado), asignar **inmediatamente** `initialScreen = Screen.PassengerMap` en `< 5 ms` y cargar `repository.getAppConfig()` en segundo plano (`launch(Dispatchers.IO)`).
   - Para usuarios con sesión guardada (`savedRole == "driver"` o `"passenger"`), leer primero el perfil cacheado en `SecurePrefs.getCachedProfile(context)` para navegar de inmediato sin bloquear por red, o ejecutar `async { repository.getAppConfig() }` en paralelo con la verificación de sesión.
3. **Reducir la duración de `AnimatedSplashScreen` y pre-asignar pinceles:**
   - Reducir el bloqueo de `delay(2350)` a `800–1000 ms` (o usar la API nativa `androidx.core:core-splashscreen`).
   - Evitar instanciar nuevos objetos `Brush.radialGradient` en cada frame cuando `smokeFade <= 0.01f`.

---

### 4.6 Optimización de Consultas Supabase (`SELECT` de Columnas Específicas) y `SecurePrefs`
1. **Excluir `route_points` de listados y reportes (`SupabaseRepository.kt:702, 762, 832`):**
   - En `getDriverWeeklyReportData()`, seleccionar únicamente las columnas necesarias:
     ```kotlin
     val columns = Columns.list(
         "id", "driver_id", "passenger_id", "passenger_name", "passenger_phone",
         "pickup_lat", "pickup_lon", "destination_lat", "destination_lon",
         "status", "trip_message", "fare_amount", "distance_km", "duration_seconds",
         "created_at", "completed_at"
     )
     val trips = supabase.postgrest["trips"].select(columns = columns) { ... }
     ```
   - En `getPassengerTripHistory()`, agregar `order("created_at", Order.DESCENDING)` y `limit(50)` igual que en `getDriverTripHistory()`.
   - En `observeDriverProfile(uid)` y `observePassengerProfile(uid)` (`SupabaseRepository.kt:1256, 1289`), añadir el filtro de servidor en el flujo Realtime:
     ```kotlin
     val changeFlow = channel.postgresChangeFlow<PostgresAction>("public") {
         table = "profiles"
         filter("id", FilterOperator.EQ, uid)
     }
     ```
2. **Cachear `SecretKey` en memoria dentro de `SecurePrefs.kt`:**
   ```kotlin
   @Volatile private var cachedKey: SecretKey? = null

   private fun getOrCreateSecretKey(): SecretKey {
       cachedKey?.let { return it }
       synchronized(this) {
           cachedKey?.let { return it }
           val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
           val existingKey = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
           val key = existingKey?.secretKey ?: run {
               val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
               keyGenerator.init(
                   KeyGenParameterSpec.Builder(
                       KEY_ALIAS,
                       KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                   )
                       .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                       .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                       .setKeySize(256)
                       .build()
               )
               keyGenerator.generateKey()
           }
           cachedKey = key
           return key
       }
   }
   ```
   Y en `saveUserSession()`, almacenar únicamente `"supabase_session"` eliminando los cifrados duplicados de `"supabase_access"` y `"supabase_refresh"`.

---

### 4.7 Optimización de Fechas y Listas en `DriverReportsScreen.kt` y `Driver.kt`
1. **Cachear los formatos `SimpleDateFormat` con `ThreadLocal` en `Driver.kt:180–196`** para API 24–25, o pre-calcular `val createdAtEpochMs: Long` una sola vez al recibir la lista de viajes de Supabase.
2. **En `DriverReportsScreen.kt:827` y `PassengerTripHistoryScreen.kt:98`**, agregar `key = { it.id ?: it.hashCode() }` al bloque `items(...)` de `LazyColumn` y mover `remember { SimpleDateFormat("dd/MM", Locale.getDefault()) }` fuera del `LazyColumn`.

---

## 5. Tabla de Prioridades: Crítico, Alto, Medio y Bajo

| ID | Prioridad | Categoría | Problema Detectado | Archivos Afectados | Impacto en Dispositivos de Gama Baja |
| :---: | :---: | :--- | :--- | :--- | :--- |
| **P-01** | 🔴 **Crítico** | Imágenes / RAM / UI Thread | `ic_tuc_tuc.png` (`1024x1024` RGB en `drawable/` base) decodifica hasta `67 MB` en RAM, ejecuta 2 bucles de `20,736` píxeles en el hilo principal y comparte la misma instancia `Drawable` entre todos los marcadores. | `MarkerIconUtils.kt:12-106`, `res/drawable/ic_tuc_tuc.png` | Pausas severas de GC (`100–250 ms`), riesgo de `OutOfMemoryError`, iconos gigantes en pantallas `mdpi`/`hdpi` y corrupción de opacidad (`alpha`) entre marcadores. |
| **P-02** | 🔴 **Crítico** | Mapas / Batería / Memoria | Ninguno de los 3 `MapView` invoca `onResume()`, `onPause()`, `disableMyLocation()` ni `onDetach()`. `PassengerLocationOverlay` ejecuta `postInvalidateDelayed(125L)` indefinidamente. | `PassengerMapScreen.kt:551-743`, `ActiveDriversMapView.kt:215-326`, `DriverRouteToPassengerScreen.kt:210-257`, `PassengerLocationUtils.kt:109-113` | El GPS y el renderizado del mapa quedan activos con la app en segundo plano o al cambiar de pantalla; fuga de memoria de `MapTileProvider` al alternar vistas. |
| **P-03** | 🔴 **Crítico** | CPU / Fluidez (60 FPS) | `ActiveDriversMapView` crea nuevos `ValueAnimator` de `4000 ms` en cada recomposición sin cancelar los anteriores; escribir texto en `PassengerMapScreen` recompone todo el mapa. | `ActiveDriversMapView.kt:304-316`, `PassengerMapScreen.kt:156-164, 576-741` | Caída de fotogramas (*jank* a `<20 FPS`) en el mapa de conductores y retraso al escribir el destino o nombre en el teclado. |
| **P-04** | 🔴 **Crítico** | RAM / Red / Batería | Doble instancia simultánea de `TextToSpeech` y doble suscripción a `observePendingTripsForDriver` en `LocationTrackingService` y `DriverHomeScreen`. | `LocationTrackingService.kt:49, 190, 252`, `DriverHomeScreen.kt:222-254, 307` | Consumo extra de `20–30 MB` de RAM nativa, doble procesamiento de paquetes de Firebase RTDB y conjuntos estáticos sin límite de memoria. |
| **P-05** | 🟠 **Alto** | Velocidad de Inicio (Cold Start) | I/O síncrono en `MainActivity.onCreate()`, hasta 3 llamadas de red en serie (`getAppConfig` -> `sessionStatus` -> `getDriverProfileOnce`) y espera fija de `2,350 ms` con `11` gradientes radiales por frame en el Splash. | `MainActivity.kt:40-133, 166`, `AnimatedSplashScreen.kt:58-61, 180-245` | Arranque en frío de `2.7s` en red óptima y hasta `7.0s–9.3s` en redes 3G/inestables antes de mostrar el mapa. |
| **P-06** | 🟠 **Alto** | Datos Móviles / Red / RAM | `SELECT *` descarga `route_points` (JSONB pesado) en `getDriverWeeklyReportData` y `getPassengerTripHistory` (sin `limit`); canales Realtime de `profiles` sin filtro `eq("id", uid)`. | `SupabaseRepository.kt:702, 762, 832, 1256, 1289` | Desperdicio de datos móviles (`250–400 KB` por consulta de reporte), mayor uso de RAM al parsear JSON y llamadas HTTP duplicadas a `syncServerTimeAndExpire()`. |
| **P-07** | 🟠 **Alto** | CPU / Listas Compose | `parseIsoTimestamp` se ejecuta `>1,000` veces al abrir `DriverReportsScreen` e instancia `SimpleDateFormat` dentro de `LazyColumn` sin `key = { it.id }`. | `Driver.kt:166-199`, `DriverReportsScreen.kt:232, 298, 827-830`, `PassengerTripHistoryScreen.kt:98` | Congelamiento de `300–600 ms` al entrar a Reportes o cambiar filtros en dispositivos con Android 7/8 (`API 24–25`). |
| **P-08** | 🟠 **Alto** | CPU / IPC (KeyStore) | `SecurePrefs` recarga `AndroidKeyStore` por IPC en cada lectura/escritura sin cachear `SecretKey` y cifra 3 veces los tokens en `saveUserSession`. | `SecurePrefs.kt:44-94, 123-135` | Bloqueos de `15–75 ms` por llamadas IPC innecesarias al demonio de hardware KeyStore durante login y arranque. |
| **P-09** | 🟠 **Alto** | Imágenes / Memoria | `splash_tuc_tuc.png` (`95.97 KB`) e `ic_quetzal_passenger.png` (`68.59 KB`) están en PNG dentro de `drawable/` base (`mdpi`) e `ic_launcher_foreground.webp` pesa `101.17 KB` en `xxxhdpi`. | `res/drawable/splash_tuc_tuc.png`, `res/drawable/ic_quetzal_passenger.png`, `res/mipmap-*/ic_launcher_foreground.webp` | Escalado x4 en RAM (`11.27 MB` combinados en `xxxhdpi`) y `~250 KB` extra de peso en el paquete de instalación. |
| **P-10** | 🟡 **Medio** | Notificaciones / SystemUI | `.setSmallIcon(R.drawable.ic_tuc_tuc)` usa un PNG opaco de `1024x1024` y `buildNotification` recrea el `NotificationChannel` en cada actualización. | `LocationTrackingService.kt:300, 426-434, 447` | Cuadro blanco sólido en la barra de estado de Android y sobrecarga de IPC hacia `SystemUI`. |
| **P-11** | 🟡 **Medio** | Empaquetado / R8 | `-keep class ...data.repository.** { *; }` impide optimizar `SupabaseRepository`; dependencia pesada `material-icons-extended`; archivos muertos (`PassengerHomeScreen.kt`, `OnboardingScreen.kt`). | `proguard-rules.pro:16-21`, `build.gradle.kts:105`, `PassengerHomeScreen.kt`, `OnboardingScreen.kt` | Mayor huella de bytecode DEX en RAM y compilaciones de desarrollo más lentas. |
| **P-12** | 🟢 **Bajo** | Almacenamiento / Red | Caché de tiles fija en `150 MB` en `filesDir` sin verificar espacio libre (`StatFs`) ni `ActivityManager.isLowRamDevice`; `ic_launcher-playstore.png` (`304 KB`) suelto en `src/main/`. | `MainActivity.kt:50-51`, `app/src/main/ic_launcher-playstore.png` | Riesgo de llenar almacenamiento en teléfonos de `8 GB / 16 GB` con poco espacio disponible. |

---

## 6. Plan de Mejoras Priorizado para Dispositivos de Gama Baja

Para garantizar que **TucTuc Map** funcione con fluidez total (`60 FPS` estables, arranque `< 1.5s`, sin cierres por falta de memoria en equipos de `1.5 GB – 2 GB de RAM` y con mínimo consumo de batería y plan de datos), se recomienda ejecutar el siguiente plan en **4 Fases iterativas**:

### Fase 1: Estabilización Crítica de RAM, Ciclo de Vida del Mapa y Batería (Impacto Inmediato)
*Objetivo: Eliminar picos de memoria de Bitmaps, fugas de `MapView` y drenaje de GPS en segundo plano.*
1. **Convertir y reubicar recursos gráficos (`P-01`, `P-09`, `P-10`):**
   - Convertir `ic_tuc_tuc.png` a `res/drawable-nodpi/ic_tuc_tuc.webp` (`192x192` px, `RGBA` con fondo transparente).
   - Convertir `splash_tuc_tuc.png` e `ic_quetzal_passenger.png` a formato **WebP** en `res/drawable-nodpi/`.
   - Crear `res/drawable/ic_stat_tuctuc.xml` (vector monocromático `24x24 dp`) y asignarlo en `.setSmallIcon(...)` dentro de `LocationTrackingService.kt:300, 447`.
   - Eliminar `app/src/main/ic_launcher-playstore.png` del árbol de fuentes o moverlo a la raíz del repositorio fuera de `src/main`.
2. **Refactorizar `MarkerIconUtils.kt` (`P-01`):**
   - Eliminar los bucles de limpieza de píxeles en CPU, escalar el icono dinámicamente a `48.dp` según `displayMetrics.density` y devolver instancias independientes de `BitmapDrawable(context.resources, cachedBitmap)` por marcador.
3. **Blindar el ciclo de vida de los 3 `MapView` (`P-02`, `P-03`):**
   - Agregar `LifecycleEventObserver` (`onResume` / `onPause`) y `onDispose` (`disableMyLocation()` + `onDetach()`) en `PassengerMapScreen.kt`, `ActiveDriversMapView.kt` y `DriverRouteToPassengerScreen.kt`.
   - Agregar el mapa `activeAnimators` con cancelación previa y umbral de movimiento en `ActiveDriversMapView.kt:304–316`.
   - Extraer el formulario del `ModalBottomSheet` de `PassengerMapScreen` a su propio Composable con estado local para que el teclado no invalide el mapa.

### Fase 2: Desduplicación de Servicios en Segundo Plano y Arranque Rápido (Cold Start)
*Objetivo: Reducir el uso de RAM/CPU en modo conductor y bajar el tiempo de apertura a ~1.0 segundo.*
1. **Unificar `TextToSpeech` y listener de viajes pendientes (`P-04`):**
   - Eliminar la instancia duplicada de `TextToSpeech` y la suscripción duplicada de `observePendingTripsForDriver` en `DriverHomeScreen.kt`, delegando la escucha y locución al servicio activo o a un gestor único compartido en memoria, y acotar `announcedTripIds` / `ignoredBroadcastTripIds` a un máximo de `200` entradas.
2. **Optimizar `MainActivity.kt`, `SecurePrefs.kt` y `AnimatedSplashScreen.kt` (`P-05`, `P-08`):**
   - Cachear `SecretKey` en memoria dentro de `SecurePrefs.kt` y eliminar los cifrados redundantes `supabase_access` y `supabase_refresh`.
   - Mover la inicialización y limpieza de directorios de OSMDroid a `Dispatchers.IO`.
   - Navegar de inmediato a `Screen.PassengerMap` si `savedRole == null` sin esperar a `getAppConfig()`, y reducir la duración de `AnimatedSplashScreen` a `900 ms` pre-calculando los objetos `Brush`.

### Fase 3: Eficiencia de Datos Móviles (Supabase) y Fluidez en Listas/Reportes
*Objetivo: Minimizar el consumo del plan de datos móviles y eliminar tirones al consultar reportes.*
1. **Proyección de columnas y filtros en `SupabaseRepository.kt` (`P-06`):**
   - Especificar `Columns.list(...)` excluyendo `route_points` en `getDriverWeeklyReportData()`, `getDriverTripHistory()` y `getPassengerTripHistory()` (creando un método ligero `getTripRoutePoints(tripId)` solo cuando el conductor toque *"Ver Mapa del Recorrido"* en un viaje específico).
   - Agregar `order("created_at", Order.DESCENDING)` y `limit(50)` en `getPassengerTripHistory()`.
   - Añadir `filter("id", FilterOperator.EQ, uid)` en `observeDriverProfile` y `observePassengerProfile`, eliminando las llamadas repetidas a `syncServerTimeAndExpire()` dentro de cada reconexión del flujo.
2. **Optimización de `DriverReportsScreen.kt` y `Driver.kt` (`P-07`):**
   - Pre-calcular el timestamp numérico (`epochMs`) una sola vez por `TripHistoryItem` al descargar la lista.
   - Añadir `key = { it.id ?: it.hashCode() }` en todos los `LazyColumn` de historial y reportes.

### Fase 4: Adelgazamiento de Binario, R8 y Adaptación a Hardware Limitado (`Low-RAM`)
*Objetivo: Reducir la huella de memoria DEX y almacenamiento en dispositivos de 8 GB / 16 GB y 1.5 GB RAM.*
1. **Ajuste dinámico de caché OSMDroid (`P-12`):**
   - Consultar `ActivityManager.isLowRamDevice` y `StatFs(filesDir.absolutePath).availableBytes` en el arranque para limitar la caché de disco al `10%` del espacio libre (con tope de `50 MB` en dispositivos de gama baja y `120 MB` en gama media/alta) y reducir `cacheMapTileCount` en equipos `isLowRamDevice`.
2. **Limpieza de dependencias y reglas R8 (`P-11`):**
   - Reemplazar `androidx.compose.material:material-icons-extended` por los `24` iconos vectoriales XML específicos utilizados en el proyecto.
   - Eliminar `-keep class com.example.tuctucmap.data.repository.** { *; }` de `proguard-rules.pro` y retirar los archivos/métodos obsoletos (`PassengerHomeScreen.kt`, `OnboardingScreen.kt`, `getPassengerRequests`).



---

## 7. Estado de Implementación (Correcciones Aplicadas)

Todas las recomendaciones críticas, altas, medias y bajas (`P-01` a `P-12`) han sido implementadas en el código fuente manteniendo intacta la funcionalidad actual:

1. **Optimización de Imágenes e Iconos (`P-01`, `P-09`, `P-10`, `P-12`):**
   - `ic_tuc_tuc.png` (`412.67 KB`, `1024x1024` RGB) fue convertido a `res/drawable-nodpi/ic_tuc_tuc.webp` (`5.40 KB`, `192x192` RGBA con transparencia preprocesada, **98.7% de reducción**).
   - `splash_tuc_tuc.png` (`95.97 KB`) e `ic_quetzal_passenger.png` (`68.59 KB`) fueron convertidos a WebP en `res/drawable-nodpi/` (`16.93 KB` y `15.17 KB` respectivamente).
   - `mipmap-*/ic_launcher_foreground.webp` fue optimizado (`xxxhdpi` reducido de `101.17 KB` a `6.05 KB`).
   - Se creó el icono vectorial monocromático `res/drawable/ic_stat_tuctuc.xml` (`24x24 dp`) para las notificaciones de `LocationTrackingService`.
   - `MarkerIconUtils.kt` fue refactorizado para decodificar con `inScaled = false`, escalar a `48.dp` según densidad real, eliminar los bucles de píxeles en CPU y entregar instancias independientes de `BitmapDrawable` por marcador.
2. **Ciclo de Vida de `MapView`, Animadores y Aislamiento de Estado (`P-02`, `P-03`):**
   - `PassengerMapScreen.kt`, `ActiveDriversMapView.kt` y `DriverRouteToPassengerScreen.kt` ahora registran `LifecycleEventObserver` (`ON_RESUME`, `ON_PAUSE`) y ejecutan `disableMyLocation()` + `mapView.onDetach()` en `onDispose`.
   - Se corrigió la acumulación de `ValueAnimator` en `ActiveDriversMapView.kt` mediante `activeAnimators` y filtro de desplazamiento mínimo.
   - Se protegió el bloque `update` de `PassengerMapScreen.kt` con firma de estado (`lastMapRenderSignature`) para que escribir en campos de texto (`tripMessage`, `guestName`, `guestPhone`) no invalide ni re-evalúe los marcadores del mapa.
   - `PassengerLocationOverlay` detiene sus redibujados cuando el mapa o el marcador salen del área visible.
3. **Consolidación de `TextToSpeech` y Listeners Firebase (`P-04`):**
   - `LocationTrackingService` centraliza el motor `TextToSpeech` y la suscripción a `observePendingTripsForDriver`, exponiendo `pendingTripsFlow`, `stopSpeaking()` y `dismissBroadcastTrip()` hacia `DriverHomeScreen` y `ActiveDriversMapView`, con conjuntos `announcedTripIds` e `ignoredBroadcastTripIds` acotados por LRU (`<= 200`).
4. **Arranque en Frío (`Cold Start`), `SecurePrefs`, Fechas y Consultas Supabase (`P-05`, `P-06`, `P-07`, `P-08`, `P-11`):**
   - `MainActivity.kt` ejecuta la configuración/limpieza de caché de OSMDroid en `Dispatchers.IO`, adapta los límites de caché a `ActivityManager.isLowRamDevice` y `StatFs`, y paraleliza `getAppConfig()`.
   - `AnimatedSplashScreen.kt` usa `Modifier.graphicsLayer` (evitando relayout en cada frame) y capas concéntricas sin instanciar `Brush.radialGradient` por frame.
   - `SecurePrefs.kt` cachea la instancia `SecretKey` de `AndroidKeyStore` en memoria y evita cifrados triplicados en `saveUserSession`.
   - `Driver.kt` incorpora caché LRU $O(1)$ y `ThreadLocal<List<SimpleDateFormat>>` en `parseIsoTimestamp()`.
   - `SupabaseRepository.kt` proyecta columnas específicas (`tripColumnsWithoutRoute`, `tripSummaryColumns`) excluyendo el JSONB pesado `route_points` en reportes e historiales, añade `filter("id", FilterOperator.EQ, uid)` en canales Realtime e incluye debounce de 60s en `syncServerTimeAndExpire()` y `getAppConfig()`.
   - `OsrmRoutingService.kt` cachea en memoria la última ruta válida para desplazamientos `< 20m`, y `proguard-rules.pro` restringe `-keep` a los DTOs internos (`SupabaseRepository$*`).
