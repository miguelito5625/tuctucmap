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
  - **Firebase Realtime Database:** Estado efímero de alta frecuencia: transmisión en vivo de coordenadas de toritos (`driver_locations`) y ciclo de vida de viaje activo (`requestTrip`).
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

---

## 3. Mapas, Tiles y Overlays (OSMDroid)
1. **Política de Tiles de OpenStreetMap (Evitar HTTP 403):**
   - Servidores de OSM bloquean paquetes por defecto como `com.example.*`.
   - Configurar siempre `userAgentValue = "TucTucMap/1.0 (Android; app.tuctucmap.gt)"` en `MainActivity.kt`.
   - En `MapTileUtils.kt`, utilizar `AppTileSource` (`XYTileSource`) con el nombre único `"TucTucOsmLive"` para aislar la caché local y prevenir mosaicos corruptos o bloqueados.
2. **Indicador de Ubicación del Pasajero (`PassengerLocationOverlay`):**
   - Implementado en `PassengerLocationUtils.kt`.
   - Figura animada de persona saludando (sin contenedor circular, chaqueta azul `#0066FF`, gorra amarilla `#F9D423`, contorno blanco grueso, brazo en saludo a ~22 FPS) y pulso de radar en la base.
3. **Gestión de Marcadores y Caché de Iconos (`MarkerIconUtils`):**
   - Implementado en `MarkerIconUtils.kt`.
   - Procesa una única vez el drawable del mototaxi con fondo transparente y lo conserva en memoria (`@Volatile cachedTucTucIcon`) para evitar bucles pesados de píxeles en el hilo principal durante recomposiciones.

---

## 4. Telemetría GPS y Optimización de Batería
1. **Modelo `DriverLocation`:**
   - Cálculo esférico Haversine (`distanceMetersTo`, `estimatedArrivalMinutes`, `formattedDistance`).
   - TTL de descarte automático: un conductor se considera inactivo si su última actualización supera los 3 minutos (`isRecentlyActive`).
2. **Servicio en Segundo Plano (`LocationTrackingService`):**
   - Ubicación: `tuctucMap/app/src/main/java/com/example/tuctucmap/service/LocationTrackingService.kt`.
   - Emisión adaptativa hacia Firebase Realtime Database:
     - Emite si el desplazamiento respecto a la última coordenada es `>= 8 metros`.
     - Si está en reposo, emite un latido (heartbeat) de refresco cada `45 segundos`.
     - Esto minimiza drásticamente el consumo de batería y datos del conductor.

---

## 5. Reglas de Negocio del Conductor
1. **Ciclo de Suscripción:**
   - El modelo `Driver.kt` evalúa `isSubscriptionExpired` y `isApprovedAndActive` parseando `subscription_end_date` (ISO-8601 UTC).
   - Si la suscripción expira, la navegación redirige forzosamente a `DriverSubscriptionExpiredScreen.kt`.
   - Banner de advertencia preventiva en `DriverHomeScreen.kt` si restan menos de 72 horas para expirar.
2. **Historial de Viajes:**
   - Al aceptar o rechazar solicitudes, se actualiza el registro en Supabase con `'accepted'` o `'rejected'`.
   - `DriverTripHistoryScreen.kt` filtra por `driver_id = auth.uid()` y provee botón para re-abrir la ruta en Google Maps.

---

## 6. Persistencia de Sesión y Autenticación Segura
1. **Gestor de Sesión Nativo (`SecurePrefs` & `SessionManager`):**
   - Implementa `io.github.jan.supabase.auth.SessionManager` y se conecta directamente a `install(Auth) { sessionManager = SecurePrefs }`.
   - Cifra el objeto completo `UserSession` (tokens, usuario y expiración) con AES-256 GCM mediante `AndroidKeyStore`.
   - Permite que Supabase restaure e intercambie tokens expirados de forma transparente al iniciar la aplicación (`autoLoadFromStorage = true`).
2. **Ciclo de Inicio en `MainActivity.kt`:**
   - Detecta `user_role` guardado en `tuctuc_prefs`.
   - Espera a que Supabase resuelva la sesión (filtra `it !is SessionStatus.Initializing`).
   - Los pasajeros van directo a `PassengerMapScreen` con su sesión activa.
   - Los conductores se dirigen a `DriverHomeScreen` (o estado correspondiente) respaldados por la caché local de aprobación, evitando que una mala red o un reinicio los expulse a `DriverLoginScreen`.

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
