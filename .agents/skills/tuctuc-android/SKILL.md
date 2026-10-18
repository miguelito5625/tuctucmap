---
name: tuctuc-android
description: >-
  Guía y runbook especializado para desarrollo, arquitectura, UI Jetpack Compose, telemetría y compilación de la app móvil Android (/tuctucMap). Usar cuando se realicen modificaciones o análisis en tuctucMap.
---

# Skill: Android App (TucTuc Map)

Esta guía define las convenciones de arquitectura, patrones de interfaz de usuario, telemetría y comandos de verificación para la aplicación nativa Android ubicada en `tuctucMap/`.

---

## 1. Stack Tecnológico & Dependencias
- **Lenguaje:** Kotlin
- **UI Toolkit:** Jetpack Compose (Material 3)
- **Backend Híbrido:**
  - **Supabase (PostgreSQL & Auth):** Autenticación de usuarios, perfiles (`profiles`), roles, estado de aprobación de conductores, historial persistente de viajes y suscripciones.
  - **Firebase Realtime Database:** Estado efímero de alta frecuencia: transmisión en vivo de coordenadas de tuctucs (`driver_locations`) y ciclo de vida de viaje activo (`requestTrip`).
- **Mapas y Ubicación:**
  - **OSMDroid:** Renderizado de mapas sin cuotas de facturación.
  - **Google Play Services Location:** Rastreo GPS del dispositivo.

---

## 2. Convenciones Críticas de UI & Jetpack Compose
1. **Edge-to-Edge y Teclado:**
   - La actividad principal utiliza `enableEdgeToEdge()`.
   - **Regla estricta:** TODA pantalla que contenga campos de texto (`TextField`, `OutlinedTextField`) debe incluir:
     - `.imePadding()` en su modificador raíz o contenedor.
     - `.verticalScroll(rememberScrollState())` para garantizar que el teclado virtual nunca oculte los campos de entrada ni los botones de acción.
2. **Navegación y Menú Lateral (Drawer):**
   - Utilizar componentes de `tuctucMap/app/src/main/java/com/example/tuctucmap/ui/components/AppDrawer.kt` (`PassengerDrawerSheet` y `DriverDrawerSheet`).
   - El estado del drawer se controla mediante `rememberDrawerState()` y `CoroutineScope`.
3. **Orientación Fija Vertical (Anti-Reinicio):**
   - Para evitar reinicios de ciclo de vida y pérdida de estado al girar el dispositivo físico o si el usuario tiene activado el giro automático, `MainActivity` está bloqueada permanentemente en vertical mediante `android:screenOrientation="portrait"` y `requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT`, además de gestionar `android:configChanges="orientation|screenSize|screenLayout|keyboardHidden|smallestScreenSize"`.
4. **Formulario de Solicitud de TucTuc y Sugerencias de Destino (`PassengerDestinationPreferences`):**
   - En `PassengerMapScreen`, el campo de destino está unificado en el mismo formulario/tarjeta de datos de contacto (nombre y teléfono) con label `"Destino"`.
   - `PassengerDestinationPreferences` persiste en `SharedPreferences` (`tuctuc_prefs`) los últimos 5 destinos ingresados por el pasajero, mostrándolos como chips interactivos. Si es la primera vez que se usa la app, no se muestran datos de ejemplo.

---

## 3. Mapas, Tiles y Overlays (OSMDroid)
1. **Política de Tiles de OpenStreetMap (Evitar HTTP 403):**
   - Servidores de OSM bloquean paquetes por defecto como `com.example.*`.
   - Configurar siempre `userAgentValue = "TucTucMap/1.0 (Android; app.tuctucmap.gt)"` en `MainActivity.kt`.
   - En `MapTileUtils.kt`, utilizar `AppTileSource` (`XYTileSource`) con el nombre único `"TucTucOsmLive"` para aislar la caché local y prevenir mosaicos corruptos o bloqueados.
2. **Indicador de Ubicación del Pasajero (`PassengerLocationOverlay`):**
   - Implementado en `PassengerLocationUtils.kt`.
   - **Avatar Activo por Defecto:** Quetzal guatemalteco animado (`ic_quetzal_passenger.png`, ~72dp) saludando continuamente al usuario con alita ondeando rítmicamente, ondas de saludo cian (`👋`), rebote de respiración y pulso de radar GPS en las patitas (~22 FPS).
   - **Reversión y Conmutación Fácil:** Enum `PassengerAvatarType` (`QUETZAL` y `CLASSIC_HUMAN`). La figura anterior (persona con chaqueta azul `#0066FF` y gorra amarilla) se conserva 100% intacta y funcional en `PassengerCharacterRenderer`, conmutable al instante mediante la variable `ACTIVE_PASSENGER_AVATAR`.
3. **Gestión de Marcadores y Caché de Iconos (`MarkerIconUtils`):**
   - Implementado en `MarkerIconUtils.kt`.
   - Procesa una única vez el drawable del mototaxi con fondo transparente y lo conserva en memoria (`@Volatile cachedTucTucIcon`) para evitar bucles pesados de píxeles en el hilo principal durante recomposiciones.
   - Genera dinámicamente el pin de recogida del pasajero (`getPassengerPickupIcon`) con el icono del Quetzal sobre un escudo circular esmeralda y blanco.
4. **Trazado de Ruta al Pasajero (Nivel 1 con OSRM & OSMDroid):**
   - Implementado en `OsrmRoutingService.kt` y `DriverRouteToPassengerScreen.kt`.
   - Permite la navegación visual en tiempo real del conductor hacia el punto de recogida del pasajero a costo $0 sin depender de Google Maps.
   - Dibuja la polilínea de calles sobre OSMDroid (`AppTileSource`), calculando distancia exacta (km/m) y tiempo estimado de llegada (~min) con fallback automático Haversine si no hay conexión al servidor OSRM.
   - **Desacoplamiento total del pasajero:** Las coordenadas de recogida se guardan fijas en Firebase (`trips`) y en caché local (`SharedPreferences`), de modo que la ruta funciona con total normalidad aunque el pasajero salga de la aplicación, bloquee el teléfono o se quede sin señal.

---

## 4. Telemetría GPS, Geocerca y Alertas en Segundo Plano
1. **Modelo `DriverLocation` y Geocerca Rural (`search_radius_km`):**
   - Cálculo esférico Haversine (`distanceMetersTo`, `estimatedArrivalMinutes`, `formattedDistance`).
   - TTL de descarte automático: un conductor se considera inactivo si su última actualización supera los 3 minutos (`isRecentlyActive`).
   - **Filtro de Radio Geográfico:** Tanto `PassengerMapScreen` como `SupabaseRepository.observePendingTripsForDriver` filtran tuctucs y solicitudes de viaje utilizando el radio configurado en `AppConfig.search_radius_km` (por defecto 15 km), evitando que conductores o pasajeros de otros municipios lejanos se mezclen.
2. **Servicio en Segundo Plano (`LocationTrackingService`):**
   - Ubicación: `tuctucMap/app/src/main/java/com/example/tuctucmap/service/LocationTrackingService.kt`.
   - Emisión adaptativa hacia Firebase Realtime Database (`>= 8 metros` o heartbeat cada `45 segundos`).
   - **Alerta de Viaje con Pantalla Apagada o en WhatsApp:** El servicio observa `observePendingTripsForDriver(driverId)` directamente en segundo plano. Cuando entra una solicitud dentro del radio del conductor, reproduce el timbre de llamada, anuncia por voz (`TextToSpeech` en español) el nombre del pasajero y la distancia, y lanza una notificación de alta prioridad (`IMPORTANCE_HIGH`) que abre `MainActivity`. Comparte `announcedTripIds` e `ignoredBroadcastTripIds` con `DriverHomeScreen` para no duplicar anuncios.

---

## 5. Reglas de Negocio del Conductor y Suscripciones Anti-Fraude
1. **Reloj del Servidor (`ServerClock`) y Ciclo de Suscripción:**
   - El objeto `ServerClock` en `Driver.kt` se sincroniza con `sync_server_time_and_expire()` de Supabase y `.info/serverTimeOffset` de Firebase para que `isSubscriptionExpired` sea inmune a manipulaciones de la fecha del teléfono.
   - Si la suscripción expira, la navegación redirige forzosamente a `DriverSubscriptionExpiredScreen.kt`.
   - El estado de aprobación y fecha de vencimiento se guardan cifrados en `SecurePrefs` (`EncryptedSharedPreferences`) y nunca en texto plano.
2. **Configuración Comercial Dinámica (`AppConfig`) y Solicitudes de Renovación (`DriverSubscriptionScreen.kt`):**
   - `DriverHomeScreen`, `DriverSubscriptionScreen` y `DriverSubscriptionExpiredScreen` cargan desde `SupabaseRepository.getAppConfig()` las cuentas bancarias, precios y el WhatsApp del administrador.
   - **Pantalla y Flujo de Membresía (`DriverSubscriptionScreen`):**
     - Muestra estado actual, días restantes, accesos rápidos para copiar números de cuenta bancaria y selector de plan.
     - Permite ingresar boleta o enviar captura de comprobante por WhatsApp, registrando la solicitud en `subscription_payment_requests` (`submitPaymentRequest`).
     - **Historial de Solicitudes en Vivo:** Lista cronológica de todas las solicitudes enviadas por el conductor con chips de estado (`Pendiente`, `Aprobado`, `Rechazado`), motivo de rechazo si aplica y botón de seguimiento directo a WhatsApp.
   - **Acceso Directo y Re-verificación:** Accesible desde el menú lateral (`AppDrawer`), desde la píldora superior de membresía en `DriverHomeScreen`, y directamente desde `DriverSubscriptionExpiredScreen` con botón para verificar activación al instante.
3. **Número de Unidad (`unit_number`) y Protección de Solicitudes:**
   - Los conductores pueden registrar su Número de TucTuc/Unidad municipal (`unit_number` / `displayIdentifier`) además de la placa.
   - En `DriverHomeScreen`, el botón `X` sobre una solicitud abierta para todos (`driver_id == ""`) solo la ignora localmente para ese conductor sin cancelarla en Firebase para los demás tuctucs.
4. **Historial de Viajes, Pasajero y Cobro:**
   - Al aceptar o rechazar solicitudes directas, se actualiza el registro en Supabase con `'accepted'` o `'rejected'`.
   - **Identidad del Pasajero (Registrado o Invitado):** `saveTripRecord` almacena directamente `passenger_name` y `passenger_phone` en la tabla `trips` (validando 8 dígitos locales para invitados).
   - **Rastreo de Trayecto en Viaje Activo:** `LocationTrackingService.startTripTracking` acumula la distancia en metros (`activeTripDistanceMeters`) y registra puntos de coordenadas `RoutePoint` (>= 8m) desde que el conductor acepta la solicitud hasta que finaliza el viaje.
   - **Sugerencias de Precios Dinámicas:** Gestionadas en `DriverFarePreferences.kt` vía SharedPreferences (`tuctuc_prefs`).
   - **Diálogo de Finalización:** En `DriverHomeScreen.kt`, el conductor selecciona o ingresa el monto cobrado (con `.imePadding()` y `.verticalScroll()`), visualiza la distancia total recorrida en km y persiste `fare`, `distance_km`, `route_points`, `passenger_name` y `passenger_phone` en Supabase `trips`.

---

## 6. Secretos (`BuildConfig`), Persistencia de Sesión y Ofuscación R8
1. **Inyección de Secretos desde `.env`:**
   - `tuctucMap/app/build.gradle.kts` lee las credenciales desde el archivo raíz `../.env` (o `local.properties`) e inyecta `BuildConfig.SUPABASE_URL`, `BuildConfig.SUPABASE_KEY` y `BuildConfig.FIREBASE_RTDB_URL`. Nunca hardcodear llaves en `SupabaseClient.kt`.
   - El build `release` tiene habilitado `isMinifyEnabled = true` y `isShrinkResources = true` con reglas para Kotlin Serialization, Supabase y OSMDroid en `proguard-rules.pro`, además de `android:allowBackup="false"` en `AndroidManifest.xml`.
2. **Gestor de Sesión Nativo (`SecurePrefs` & `SessionManager`):**
   - Cifra el objeto completo `UserSession` y la caché de suscripción con AES-256 GCM mediante `AndroidKeyStore`, sin fallbacks inseguros en texto plano.

---

## 7. Verificación y Compilación Obligatoria
Antes de dar por terminada cualquier tarea en el módulo Android, compilar y verificar:

```powershell
# Desde el directorio tuctucMap/
cd tuctucMap
.\gradlew.bat assembleDebug
```
*En Linux/macOS usar `./gradlew assembleDebug`.*
- Verificar que el resultado sea `BUILD SUCCESSFUL`.
- Si surgen errores de sintaxis o tipos, corregirlos de inmediato.
