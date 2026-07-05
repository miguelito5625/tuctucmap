# Plan de Implementación: Tuc Tuc Map App

Desarrollar la estructura, el código fuente y la arquitectura de una aplicación de transporte local de Tuc Tucs ("Toritos") usando Kotlin, Jetpack Compose, OSMDroid y Firebase. La aplicación manejará dos roles ("Usuario" y "Conductor") en la misma base de código.

## User Review Required

> [!IMPORTANT]
> **Archivo de Configuración de Firebase:**
> Deberás descargar el archivo `google-services.json` desde tu consola de Firebase y colocarlo en el directorio `app/` de tu proyecto de Android Studio para habilitar la conexión con Firebase.
>
> **Configuración del Emulador / Dispositivo Real:**
> Para probar la transmisión de ubicación en segundo plano y el renderizado de OpenStreetMap (OSMDroid), es recomendable usar un dispositivo real o configurar ubicaciones simuladas en el emulador de Android.

## Open Questions
No hay preguntas críticas por el momento. La estructura se adaptará a las mejores prácticas de Jetpack Compose y Clean Architecture.

## Proposed Changes

---

### Configuración del Proyecto y Dependencias

#### [MODIFY] [libs.versions.toml](file:///c:/Users/exbinario/AndroidStudioProjects/tuctucMap/gradle/libs.versions.toml)
Agregar las dependencias para Firebase (Auth, Database), OSMDroid, Servicios de Ubicación de Google Play y el plugin de Google Services.

#### [MODIFY] [build.gradle.kts (root)](file:///c:/Users/exbinario/AndroidStudioProjects/tuctucMap/build.gradle.kts)
Agregar el plugin de Google Services en el bloque de plugins principales.

#### [MODIFY] [build.gradle.kts (app)](file:///c:/Users/exbinario/AndroidStudioProjects/tuctucMap/app/build.gradle.kts)
Aplicar el plugin `com.google.gms.google-services` y declarar las dependencias de Firebase (usando BOM), OSMDroid y Play Services Location.

---

### Seguridad e Infraestructura de Firebase

#### [NEW] [firebase_rules.json](file:///c:/Users/exbinario/AndroidStudioProjects/tuctucMap/firebase_rules.json)
Reglas de seguridad de Firebase Realtime Database que permiten lectura pública de ubicaciones y escritura restringida a conductores autenticados en su propio nodo.

---

### Manifiesto de Android y Permisos

#### [MODIFY] [AndroidManifest.xml](file:///c:/Users/exbinario/AndroidStudioProjects/tuctucMap/app/src/main/AndroidManifest.xml)
Declarar permisos para Internet, Ubicación Fina/Coarse, Foreground Service, Foreground Service de Ubicación (API 34+) y Post Notifications (API 33+). Registrar el servicio de rastreo en segundo plano.

---

### Modelos y Repositorio de Datos (Clean Architecture)

#### [NEW] [Driver.kt](file:///c:/Users/exbinario/AndroidStudioProjects/tuctucMap/app/src/main/java/com/example/tuctucmap/data/model/Driver.kt)
Modelo de datos para el Conductor (nombre, teléfono, placas, estado de aprobación).

#### [NEW] [DriverLocation.kt](file:///c:/Users/exbinario/AndroidStudioProjects/tuctucMap/app/src/main/java/com/example/tuctucmap/data/model/DriverLocation.kt)
Modelo de datos para la ubicación transmitida en tiempo real (coordenadas, datos del conductor, timestamp, estado activo).

#### [NEW] [FirebaseRepository.kt](file:///c:/Users/exbinario/AndroidStudioProjects/tuctucMap/app/src/main/java/com/example/tuctucmap/data/repository/FirebaseRepository.kt)
Repositorio para manejar la autenticación en Firebase, el registro de conductores, la lectura del estado de aprobación y la actualización de ubicaciones.

---

### Servicio de Rastreo en Segundo Plano

#### [NEW] [LocationTrackingService.kt](file:///c:/Users/exbinario/AndroidStudioProjects/tuctucMap/app/src/main/java/com/example/tuctucmap/service/LocationTrackingService.kt)
Foreground Service que obtiene actualizaciones de ubicación a través del FusedLocationProviderClient cada 5-10 segundos y las sube a Firebase Realtime Database. Muestra una notificación persistente.

---

### Vistas y Navegación (Jetpack Compose)

#### [NEW] [Navigation.kt](file:///c:/Users/exbinario/AndroidStudioProjects/tuctucMap/app/src/main/java/com/example/tuctucmap/ui/Navigation.kt)
Sistema de navegación de estado para gestionar el flujo entre la selección de rol, pantallas del Pasajero y pantallas del Conductor.

#### [NEW] [OnboardingScreen.kt](file:///c:/Users/exbinario/AndroidStudioProjects/tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/OnboardingScreen.kt)
Pantalla inicial para elegir rol (Pasajero o Conductor), guardando la selección en SharedPreferences.

#### [NEW] [PassengerMapScreen.kt](file:///c:/Users/exbinario/AndroidStudioProjects/tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/passenger/PassengerMapScreen.kt)
Pantalla principal para el usuario. Contiene el mapa de OSMDroid (AndroidView), marcadores interactivos de los conductores en tiempo real y un Bottom Sheet con información detallada para contactarlos (teléfono, placas, WhatsApp).

#### [NEW] [DriverLoginScreen.kt](file:///c:/Users/exbinario/AndroidStudioProjects/tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/driver/DriverLoginScreen.kt)
Pantalla de inicio de sesión de conductores.

#### [NEW] [DriverRegisterScreen.kt](file:///c:/Users/exbinario/AndroidStudioProjects/tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/driver/DriverRegisterScreen.kt)
Pantalla de registro de conductores con campos: Nombre, Teléfono y Placas. Registra con `estadoAprobacion = "pendiente"`.

#### [NEW] [DriverPendingScreen.kt](file:///c:/Users/exbinario/AndroidStudioProjects/tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/driver/DriverPendingScreen.kt)
Pantalla de "Cuenta en revisión" que escucha cambios en tiempo real del estado de aprobación del conductor y avanza a la pantalla principal automáticamente cuando el estado cambia a `"aprobado"`.

#### [NEW] [DriverHomeScreen.kt](file:///c:/Users/exbinario/AndroidStudioProjects/tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/driver/DriverHomeScreen.kt)
Pantalla principal del conductor con el botón de "Conectarse / Desconectarse" para iniciar/detener la transmisión de ubicación.

#### [MODIFY] [MainActivity.kt](file:///c:/Users/exbinario/AndroidStudioProjects/tuctucMap/app/src/main/java/com/example/tuctucmap/MainActivity.kt)
Inicializar OSMDroid, gestionar la verificación y petición de permisos de ubicación y notificaciones en tiempo de ejecución, y renderizar el flujo principal (`AppNavigation`).

---

## Verification Plan

### Manual Verification
1. **Flujo de Onboarding:** Verificar que al abrir por primera vez la app pida el rol y lo recuerde en posteriores inicios.
2. **Rol Conductor (Registro y Login):** Registrar un conductor, comprobar que se crea en la base de datos de Firebase con estado `pendiente` y se bloquea en la pantalla de revisión.
3. **Aprobación de Conductor:** Modificar el estado a `aprobado` en la consola de Firebase y verificar que la pantalla transiciona automáticamente a la pantalla de Conductor Activo.
4. **Tracking en Background:** Iniciar el servicio desde la pantalla del conductor, confirmar que aparece la notificación persistente y las coordenadas cambian en Firebase al simular movimiento. Detener el servicio y comprobar que se elimina el nodo del conductor.
5. **Rol Pasajero (Visualización en Mapa):** Abrir la app como Pasajero, verificar que muestra el mapa, la ubicación del usuario, y los marcadores de conductores activos, y que al hacer click se abre el Bottom Sheet con la información.
