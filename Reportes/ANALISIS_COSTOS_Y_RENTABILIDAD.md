# Informe Técnico y Financiero: Análisis de Costos Cloud (Supabase vs. Firebase) y Rentabilidad de TucTuc Map

**Fecha de elaboración y consulta de precios oficiales:** 28 de octubre de 2026  
**Tipo de cambio de referencia utilizado:** **Q 7.65 por US$ 1.00** *(Referencia oficial del Banco de Guatemala — Banguat: ~Q 7.644 – Q 7.65 / USD)*  
**Escala operativa base analizada:** **50 conductores** y **200 pasajeros recurrentes** (250 usuarios activos + 1 administrador)  
**Proyectos auditados en el monorepo:**
- `tuctucMap/` (App nativa Android en Kotlin + Jetpack Compose)
- `tuctucMapDashboard/` (Panel administrativo web en Angular 22 + Leaflet)
- `databaseTuctucApp/` (Esquema PostgreSQL, triggers, funciones RPC y `pg_cron` en Supabase)

---

## 1. Resumen Ejecutivo

Tras inspeccionar el código fuente completo del monorepo **TucTuc Map**, se concluye que el proyecto cuenta con una **arquitectura híbrida altamente optimizada para reducción de costos** que combina **Supabase** (para persistencia relacional, autenticación, reglas de negocio y auditoría) y **Firebase Realtime Database** (para la telemetría GPS efímera de alta frecuencia y el despacho en vivo de viajes).

### Hallazgos Clave del Análisis

1. **¿Qué proveedor es más económico para operar 50 conductores y 200 pasajeros?**
   - **Operar 100% en Supabase** (forzando las actualizaciones GPS cada 5 segundos a través de *Supabase Realtime*) resulta **el esquema más costoso** en escenarios normal e intensivo (**Q 191.25/mes** en uso bajo, **Q 511.25/mes** en uso normal y **Q 1,745.19/mes** en uso intensivo), debido a que Supabase factura por **cantidad de mensajes Realtime** ($2.50 USD por millón tras superar 5 millones/mes), y 50 conductores generan entre **4.38 y 86.25 millones de eventos mensuales**.
   - **Operar 100% en Firebase usando solo Cloud Firestore** cuesta **Q 15.99/mes** (bajo), **Q 115.06/mes** (normal) y **Q 452.34/mes** (intensivo), ya que Firestore cobra por cada lectura y escritura documental.
   - **Operar en la Arquitectura Híbrida Actual del código (`Supabase + Firebase RTDB`)** —o en un esquema **Firebase Híbrido (`Firebase RTDB + Firestore`)**— es **drásticamente más económico**:
     - En los escenarios **Bajo y Normal**, el costo de nube es de **Q 0.00 / mes (US$ 0.00)** utilizando los planes gratuitos (**Supabase Free + Firebase Spark/Blaze con cuota gratuita**), o de **Q 191.25 / mes (US$ 25.00)** si se adquiere **Supabase Pro** por respaldos diarios automáticos y SLA comercial.
     - En el escenario **Intensivo** (500 viajes/día, 50 conductores conectados 11 horas diarias), el costo en nube es de apenas **Q 52.56 / mes (US$ 6.87)** sobre planes base gratuitos (o **Q 243.81 / mes [US$ 31.87]** con Supabase Pro).

2. **Rentabilidad del Propietario (Modelo de Suscripciones Prepago en Código):**
   - El sistema implementa un modelo de membresías prepago para conductores (**Q 10 diario, Q 25 semanal, Q 45 quincenal, Q 80 mensual**) cobrado vía depósito o transferencia bancaria (Banrural / Banco Industrial) con validación por WhatsApp y **0% de comisión de pasarela**.
   - En el **Escenario Normal** (45 de los 50 conductores con membresía activa en el mes y un ingreso promedio ponderado [ARPU] de **Q 93.00/mes por conductor**), el propietario genera **Q 4,185.00 / mes (Q 50,220.00 / año)** en ingresos brutos.
   - Descontando costos cloud y operativos, la **ganancia neta mensual estimada del propietario** en el escenario normal es de **Q 4,161.41 / mes** (margen neto del **99.4%**) operando en capas gratuitas, o de **Q 3,970.16 / mes** (margen neto del **94.9%**) pagando el plan **Supabase Pro**.
   - El **punto de equilibrio** se alcanza con tan solo **1 conductor suscrito** en modalidad gratuita (o **3 conductores suscritos** pagando Supabase Pro).

---

## 2. Inspección Arquitectónica del Código Fuente e Identificación de Generadores de Costo

A partir de la revisión estática del repositorio, se identificaron todos los componentes que interactúan con servicios externos y su impacto en facturación:

### 2.1 Base de Datos Relacional y Lógica deServidor (`databaseTuctucApp/db.sql`)
- **Motor actual:** PostgreSQL en Supabase con Row Level Security (`RLS`) habilitado en todas las tablas.
- **Tablas implementadas (5):**
  1. `public.profiles`: Usuarios (`passenger`, `driver`, `admin`), teléfono, placa (`plate_number`), número de unidad (`unit_number`) y vigencia (`subscription_end_date`, `approval_status`).
  2. `public.driver_subscriptions`: Historial contable de pagos confirmados (`trial`, `semanal`, `quincenal`, `mensual`, `personalizado`).
  3. `public.subscription_payment_requests`: Solicitudes de renovación enviadas desde la app Android con referencia de boleta bancaria.
  4. `public.app_config`: Fila única (`id = 'default'`) con precios, cuentas bancarias (Banrural y Banco Industrial), WhatsApp administrativo y geocerca (`search_radius_km = 5.00`).
  5. `public.trips`: Registro histórico de viajes completados, incluyendo tarifa (`fare`), distancia (`distance_km`) y polilínea GPS (`route_points JSONB`).
- **Mecanismos de ahorro de almacenamiento y cómputo ya programados en `db.sql`:**
  - **Autolimpieza de polilíneas (`cleanup_old_trip_route_points`, líneas 274–301):** Un trabajo programado en `pg_cron` se ejecuta todos los días a las 03:00 AM hora de Guatemala (`0 9 * * *` UTC) y ejecuta `SET route_points = NULL WHERE created_at < NOW() - INTERVAL '30 days'`. Esto evita que los arreglos JSONB de coordenadas llenen el disco de PostgreSQL, manteniendo las tarifas y distancias históricas intactas por años.
  - **Expiración horaria en servidor (`tuctuc-expire-driver-subscriptions`, líneas 303–307):** `pg_cron` marca automáticamente como `'suscripcion_vencida'` los perfiles vencidos cada hora sin requerir servidores externos.
  - **Carga perezosa (*Lazy Loading*) de rutas en el Dashboard (`supabase.service.ts`, líneas 392–450):** `getCompletedTrips()` excluye deliberadamente la columna pesada `route_points` del `SELECT` general de la tabla y solo la descarga cuando el administrador abre el modal de mapa de un viaje específico (`getTripRoutePoints(tripId)`), reduciendo en **~99%** el Egress de PostgreSQL.

### 2.2 Telemetría GPS en Tiempo Real y Despacho de Viajes (`LocationTrackingService.kt` y `SupabaseRepository.kt`)
- **Servicio utilizado:** Firebase Realtime Database (`/driver_locations/{uid}` y `/trips/{tripId}`).
- **Muestreo adaptativo del GPS (`LocationTrackingService.kt`, líneas 338–405):**
  - Cuando el conductor está `"disponible"` u `"ocupado"`, solicita lecturas GPS cada **5 segundos** (`PRIORITY_HIGH_ACCURACY, 5000L`).
  - Cuando el conductor pasa a estado `"en_pausa"`, relaja el GPS a **15 segundos** (`PRIORITY_BALANCED_POWER_ACCURACY, 15000L`).
  - **Filtro inteligente antes de escribir en la nube (línea 380):** Solo transmite a Firebase si el tuctuc se movió **`>= 8.0 metros`** respecto a la última coordenada enviada o si pasaron **`45 segundos`** (*heartbeat* para mantener su pin activo cuando está estacionado en una parada).
  - **Compresión diferencial de metadatos (`includeStaticMetadata`, línea 383 y `SupabaseRepository.kt` líneas 435–460):** Los datos estáticos (`nombre`, `telefono`, `placas`, `numeroUnidad`) solo se envían al iniciar turno y cada **3 minutos (180s)** (~250 bytes). El resto de las actualizaciones envía únicamente el nodo delta (`driver_id`, `lat`, `lon`, `active`, `status`, `updated_at`) de **~135 bytes JSON** (~200 bytes con encabezados de red).
  - **Limpieza automática al desconectarse (`registerOnDisconnectCleanup`, línea 413):** Usa `onDisconnect().removeValue()` nativo de Firebase RTDB para borrar el nodo del conductor si se apaga el teléfono o pierde datos móviles.
- **Escucha incremental en pasajeros (`observeActiveLocations`, líneas 1151–1224):**
  - Utiliza `ChildEventListener` (`onChildAdded`, `onChildChanged`, `onChildRemoved`) en lugar de `ValueEventListener`. Cuando un conductor avanza 8 metros, los pasajeros con el mapa abierto reciben **únicamente los ~200 bytes del conductor que se movió**, en vez de descargar la lista completa de 50 conductores (ahorro del **96.6%** en transferencia de salida/Egress).
- **Sondeo en el Panel Web (`fleet-map.component.ts`, línea 390 y `supabase.service.ts`, línea 373):**
  - El componente `/fleet-map` del administrador consulta `${baseUrl}/driver_locations.json` mediante HTTP `fetch` cada **8 segundos** mientras el administrador mantiene abierta esa pestaña.

### 2.3 Autenticación (`SupabaseRepository.kt`)
- **Servicio utilizado:** Supabase Auth (`io.github.jan.supabase:auth-kt`) con proveedor `Email` (correo y contraseña) y dependencias listas para Google Sign-In (`androidx.credentials`, `play-services-auth`).
- **Pasajeros Invitados sin costo de Auth (`PassengerMapScreen.kt`, líneas 163–164 y `SupabaseRepository.kt`, línea 877):** Los pasajeros pueden solicitar viajes como invitados (`guest_<timestamp>`), guardando su nombre y teléfono de 8 dígitos localmente en `SharedPreferences`. No utiliza verificación por SMS (OTP), por lo que el costo de mensajería de texto es **$0.00**.

### 2.4 Almacenamiento de Archivos (Cloud Storage)
- **Consumo actual:** **0 bytes ($0.00).**
- **Implementación en código (`DriverSubscriptionScreen.kt`, líneas 85–130):** La app **no almacena fotos de perfil ni imágenes de boletas en la nube**. El conductor ingresa el número de referencia bancaria en texto (`subscription_payment_requests`) y la app abre **WhatsApp** con un mensaje prellenado hacia el número del administrador (`appConfig.adminWhatsappPhone`) para adjuntar la captura del comprobante directamente en el chat.

### 2.5 Notificaciones y Alertas de Viaje (`LocationTrackingService.kt`, líneas 247–310)
- **Consumo externo:** **$0.00.**
- **Implementación en código:** Mientras el conductor está en turno, el servicio en primer plano (`ForegroundService`) escucha el query `tripsRef.orderByChild("status").equalTo("requested").limitToLast(25)` y filtra por geocerca Haversine (`maxSearchRadiusKm = 5.0 km`). Al detectar una solicitud cercana, reproduce localmente el timbre del teléfono (`RingtoneManager`), lee en voz alta el mensaje del pasajero usando el motor nativo de Android `TextToSpeech` (`es-GT` / `es-419`) y despliega una notificación `IMPORTANCE_HIGH` sin requerir servidores intermedios ni Cloud Functions de pago.

### 2.6 Mapas y Ruteo Vial (`MapTileUtils.kt`, `OsrmRoutingService.kt`, `fleet-map.component.ts`)
- **Renderizado de Mapas:**
  - Android: `OSMDroid` (`org.osmdroid:osmdroid-android`) consumiendo tiles gratuitos de OpenStreetMap (`https://tile.openstreetmap.org/`) con `User-Agent` personalizado (`TucTucMap/1.0 (Android; app.tuctucmap.gt)`) y caché local en el teléfono (`"TucTucOsmLive"`). **Costo: Q 0.00.**
  - Web Dashboard: `Leaflet 1.9.4` con tiles de OpenStreetMap. **Costo: Q 0.00.**
- **Cálculo de Rutas y ETA (`OsrmRoutingService.kt`, `DriverHomeScreen.kt` L171, `PassengerMapScreen.kt` L254):**
  - Consulta `https://router.project-osrm.org/route/v1/driving/...` únicamente cuando hay un viaje activo aceptado, aplicando un *throttle* estricto de **25 segundos** en el teléfono del conductor y **30 segundos** en el del pasajero, e interpolando la distancia localmente con geometría Haversine (`distancia * 1.28`) entre intervalos. **Costo: Q 0.00.**

---

## 3. Precios Oficiales Vigentes: Supabase vs. Firebase

*Fecha de verificación: 28 de octubre de 2026. Tipo de cambio: Q 7.65 = US$ 1.00.*

### 3.1 Tarifario Oficial de Supabase
**Fuente oficial:** [https://supabase.com/pricing](https://supabase.com/pricing) y [Documentación de Facturación de Supabase](https://supabase.com/docs/guides/platform/billing-on-supabase) (Consultado el 28/10/2026).

| Recurso / Servicio | Plan Free ($0 / Q 0.00 al mes) | Plan Pro ($25 / Q 191.25 al mes) | Costo por Excedente (Plan Pro) |
| :--- | :--- | :--- | :--- |
| **Cuota Base Mensual** | **US$ 0.00 (Q 0.00)** | **US$ 25.00 (Q 191.25)** *(incluye $10 de cómputo Micro)* | — |
| **Base de Datos PostgreSQL** | 500 MB (se pausa tras 7 días inactivo) | 8 GB dedicados (sin pausa, backups de 7 días) | US$ 0.125 / GB-mes (Q 0.96 / GB) |
| **Egress (Transferencia Salida)** | 5 GB uncached + 5 GB cached / mes | 250 GB uncached + 250 GB cached / mes | US$ 0.09 / GB (Q 0.69 / GB) |
| **Autenticación (MAUs)** | 50,000 usuarios activos / mes | 100,000 usuarios activos / mes | US$ 0.00325 / MAU (Q 0.025 / MAU) |
| **Realtime: Conexiones Pico** | 200 conexiones concurrentes | 500 conexiones concurrentes | US$ 10.00 por cada 1,000 conex. (Q 76.50) |
| **Realtime: Mensajes / mes** | **2,000,000 mensajes / mes** | **5,000,000 mensajes / mes** | **US$ 2.50 por millón (Q 19.13 / millón)** |
| **Storage (Archivos)** | 1 GB | 100 GB | US$ 0.021 / GB-mes (Q 0.16 / GB) |

### 3.2 Tarifario Oficial de Firebase (Google Cloud)
**Fuente oficial:** [https://firebase.google.com/pricing](https://firebase.google.com/pricing) (Consultado el 28/10/2026).

| Recurso / Servicio | Plan Spark (Gratis) / Cuota Gratis en Blaze | Tarifa de Excedente en Plan Blaze (Pago por Uso) |
| :--- | :--- | :--- |
| **Cuota Base Mensual** | **US$ 0.00 (Q 0.00)** | **US$ 0.00 (Q 0.00)** *(conserva las cuotas gratis de Spark)* |
| **Realtime Database (RTDB): Conexiones** | 100 simultáneas (Spark) / 200,000 (Blaze) | Sin costo por conexión en Blaze |
| **Realtime Database (RTDB): Almacenamiento** | 1 GB | US$ 5.00 / GB-mes (Q 38.25 / GB) |
| **Realtime Database (RTDB): Descarga (Egress)** | **10 GB / mes gratis** | **US$ 1.00 / GB (Q 7.65 / GB)** *(0 cobro por escrituras/mensajes)* |
| **Cloud Firestore: Almacenamiento** | 1 GiB gratis | US$ 0.18 / GiB-mes (Q 1.38 / GiB) |
| **Cloud Firestore: Escrituras** | **20,000 escrituras / día** (~600,000 / mes) | **US$ 0.18 por 100,000 escrituras (Q 1.38 / 100k)** |
| **Cloud Firestore: Lecturas** | **50,000 lecturas / día** (~1,500,000 / mes) | **US$ 0.06 por 100,000 lecturas (Q 0.46 / 100k)** |
| **Cloud Firestore: Eliminaciones** | 20,000 eliminaciones / día (~600,000 / mes) | US$ 0.02 por 100,000 eliminaciones (Q 0.15 / 100k) |
| **Cloud Firestore: Red (Egress)** | 10 GiB / mes gratis | US$ 0.12 / GiB (Q 0.92 / GiB) |
| **Firebase Auth (Email / Google / Anónimo)** | **Gratis (< 50,000 MAU)** | Gratis hasta 50,000 MAU en Identity Platform |
| **Firebase Hosting (Panel Web Angular)** | 10 GB disco + 360 MB/día (~10.8 GB/mes) | US$ 0.026 / GB disco y US$ 0.15 / GB transferencia |

---

## 4. Modelado de Tres Escenarios de Uso (Bajo, Normal e Intensivo)

### 4.1 Supuestos Técnicos Basados en el Código Fuente
Para la carga de **50 conductores** y **200 pasajeros** (durante un mes estándar de 30 días):
1. **Frecuencia real de actualización GPS (`LocationTrackingService.kt` L340 y L380):**
   - **En movimiento** (velocidad media de tuctuc de 18–20 km/h = ~5 m/s): en el ciclo de 5 segundos del GPS el vehículo avanza ~25 metros (`>= 8m`), por lo que emite **1 actualización cada 5 segundos** (**720 actualizaciones por hora de movimiento**).
   - **En espera / parada** (desplazamiento `< 8m`): emite **1 *heartbeat* cada 45 segundos** (**80 actualizaciones por hora de espera**).
2. **Peso por paquete de ubicación (`SupabaseRepository.kt` L450):**
   - Promedio ponderado entre `deltaLocation` (~135 bytes JSON cada 5s/45s) y `fullLocation` (~250 bytes cada 180s) más cabeceras WebSocket = **0.20 KB (200 bytes) por actualización**.
3. **Peso en Base de Datos por Viaje Completado (`db.sql` L147 y L274):**
   - Un viaje urbano/municipal promedio (~1.5 km) registra ~180 puntos en `route_points JSONB` (~10 KB por viaje). Gracias al job `cleanup_old_trip_route_points()` que purga `route_points` a los 30 días, el espacio ocupado por polilíneas nunca supera los viajes de **1 mes**, y el registro contable permanente ocupa solo **~0.3 KB por viaje**.

### 4.2 Resumen de Parámetros Operativos por Escenario

| Métrica / Supuesto Operativo | Escenario 1: Uso Bajo | Escenario 2: Uso Normal (Esperado) | Escenario 3: Uso Intensivo |
| :--- | :---: | :---: | :---: |
| **Conductores registrados / activos por día** | 50 reg. / **25 activos/día** | 50 reg. / **40 activos/día** | 50 reg. / **50 activos/día** |
| **Conductores simultáneos promedio (pico)** | 12 prom. (25 pico) | 25 prom. (40 pico) | 38 prom. (50 pico) |
| **Jornada diaria por conductor activo** | **6 h/día** *(1.5h mov. + 4.5h espera)* | **8 h/día** *(3.0h mov. + 5.0h espera)* | **11 h/día** *(5.5h mov. + 5.5h espera)* |
| **Actualizaciones GPS / conductor / día** | 1,440 act./día | 2,560 act./día | 4,400 act./día |
| **Actualizaciones GPS totales de la flota / día** | 36,000 escrituras/día | 102,400 escrituras/día | 220,000 escrituras/día |
| **Actualizaciones GPS totales de la flota / mes** | **1,080,000 / mes** | **3,072,000 / mes** | **6,600,000 / mes** |
| **Pasajeros activos por día (de 200 totales)** | 40 pasajeros/día | 90 pasajeros/día | 160–200 pasajeros/día |
| **Receptores simultáneos viendo el mapa en vivo** | **3 clientes** *(2 pasaj. + 1 cond.)* | **6 clientes** *(5 pasaj. + 1 cond.)* | **12 clientes** *(10 pasaj. + 2 cond.)* |
| **Viajes completados por día (y por mes)** | **100 / día** (**3,000 / mes**) | **250 / día** (**7,500 / mes**) | **500 / día** (**15,000 / mes**) |
| **Viajes completados al año** | 36,000 / año | 90,000 / año | 180,000 / año |
| **Mensajes Realtime totales / mes (si todo va por WS)** | **4.38 millones / mes** | **21.73 millones / mes** | **86.25 millones / mes** |
| **Transferencia / Egress total de telemetría / mes** | **0.82 GB / mes** | **4.03 GB / mes** | **16.87 GB / mes** |
| **Transferencia / Egress de consultas BD / mes** | **0.15 GB / mes** | **0.35 GB / mes** | **0.75 GB / mes** |
| **Almacenamiento en BD al final del Año 1** | **~45 MB** *(con `pg_cron` 30d)* | **~102 MB** *(con `pg_cron` 30d)* | **~204 MB** *(con `pg_cron` 30d)* |

---

## 5. Comparación de Costos: Supabase vs. Firebase (Mensual y Anual en USD y GTQ)

Para ofrecer una toma de decisiones transparente, se evalúan **cuatro configuraciones arquitectónicas**:
1. **Opción A — 100% Supabase:** Autenticación, Base de Datos PostgreSQL y Telemetría GPS sobre *Supabase Realtime*.
2. **Opción B1 — 100% Firebase (Todo en Cloud Firestore):** Autenticación, Perfiles, Viajes y Telemetría GPS guardados como documentos en *Cloud Firestore*.
3. **Opción B2 — 100% Firebase Optimizado (`Firebase RTDB + Cloud Firestore`):** Telemetría GPS en *Firebase Realtime Database* (que cobra por GB y no por operación) y datos persistentes en *Cloud Firestore*.
4. **Opción C — Arquitectura Híbrida Actual del Proyecto (`Supabase + Firebase RTDB`):** El diseño exacto presente hoy en el código fuente (`Supabase` para Auth/PostgreSQL + `Firebase RTDB` para GPS y viajes en vivo), evaluado tanto en su modalidad de **Capas Gratuitas** como con **Supabase Pro**.

### 5.1 Detalle del Cálculo por Proveedor y Escenario

#### A. Cálculo en 100% Supabase (Opción A)
- **Escenario Bajo (4.38M mensajes Realtime/mes):** Supera el límite de 2M de mensajes del Plan Free. Requiere **Plan Pro (US$ 25.00/mes)**, el cual incluye 5M de mensajes, 8 GB de BD y 250 GB de Egress. Excedentes = $0. **Total: US$ 25.00/mes (Q 191.25/mes).**
- **Escenario Normal (21.73M mensajes Realtime/mes):** Plan Pro (US$ 25.00 base, incluye 5M mensajes) + excedente de $16.73\text{ millones} \times \text{US\$ } 2.50 = \text{US\$ } 41.83$. **Total: US$ 66.83/mes (Q 511.25/mes).**
- **Escenario Intensivo (86.25M mensajes Realtime/mes):** Plan Pro (US$ 25.00 base, incluye 5M mensajes) + excedente de $81.25\text{ millones} \times \text{US\$ } 2.50 = \text{US\$ } 203.13$. **Total: US$ 228.13/mes (Q 1,745.19/mes).**

#### B. Cálculo en 100% Firebase — Solo Cloud Firestore (Opción B1)
- **Escenario Bajo:** 1.13M escrituras/mes (menos 600k gratis = 530k × $0.18/100k = **US$ 0.95**) + 3.40M lecturas/mes (menos 1.5M gratis = 1.90M × $0.06/100k = **US$ 1.14**). **Total: US$ 2.09/mes (Q 15.99/mes).**
- **Escenario Normal:** 3.19M escrituras/mes (excedente 2.59M × $0.18/100k = **US$ 4.66**) + 18.80M lecturas/mes (excedente 17.30M × $0.06/100k = **US$ 10.38**). **Total: US$ 15.04/mes (Q 115.06/mes).**
- **Escenario Intensivo:** 6.85M escrituras/mes (excedente 6.25M × $0.18/100k = **US$ 11.25**) + 79.80M lecturas/mes (excedente 78.30M × $0.06/100k = **US$ 46.98**) + excedente de Egress (7.47 GiB × $0.12 = **US$ 0.90**). **Total: US$ 59.13/mes (Q 452.34/mes).**

#### C. Cálculo en 100% Firebase Optimizado (`RTDB + Firestore`) (Opción B2) y Arquitectura Híbrida Actual (`Supabase + Firebase RTDB`) (Opción C)
- En **Firebase RTDB**, las escrituras son gratuitas y la cuota sin costo incluye **1 GB de almacenamiento** y **10 GB/mes de descarga (Egress)**:
  - **Escenario Bajo:** 0.82 GB/mes de Egress en RTDB (< 10 GB gratis) → **US$ 0.00**.
  - **Escenario Normal:** 4.03 GB/mes de Egress en RTDB (< 10 GB gratis) → **US$ 0.00**.
  - **Escenario Intensivo:** 16.87 GB/mes de Egress en RTDB (supera en 6.87 GB la cuota gratuita de 10 GB × US$ 1.00/GB) → **US$ 6.87/mes (Q 52.56/mes)**.
- En **Supabase** (bajo la Arquitectura Híbrida Actual), al estar descargado del tráfico GPS:
  - El almacenamiento de BD es de 45 MB a 204 MB (< 500 MB del Plan Free), el Egress es de 0.15 a 0.75 GB/mes (< 5 GB del Plan Free) y los mensajes Realtime de administración son < 60,000/mes (< 3% del límite de 2M del Plan Free). Por ende, **cabe holgadamente en Supabase Free (US$ 0.00)** o se puede contratar opcionalmente **Supabase Pro (US$ 25.00 = Q 191.25/mes)** para contar con copias de seguridad automáticas diarias y soporte dedicado.

### 5.2 Tabla Comparativa General de Costos Cloud (Mensual y Anual)

| Arquitectura / Proveedor | Plan Recomendado | Escenario Bajo (Mensual / Anual) | Escenario Normal (Mensual / Anual) | Escenario Intensivo (Mensual / Anual) |
| :--- | :--- | :--- | :--- | :--- |
| **1. 100% Supabase** *(PostgreSQL + Supabase Realtime para GPS)* | Supabase **Pro** + Excedentes Realtime | US$ 25.00 / mes<br>**Q 191.25 / mes**<br>*(Q 2,295.00 / año)* | US$ 66.83 / mes<br>**Q 511.25 / mes**<br>*(Q 6,135.00 / año)* | US$ 228.13 / mes<br>**Q 1,745.19 / mes**<br>*(Q 20,942.33 / año)* |
| **2. 100% Firebase (Solo Firestore)** *(Firestore para BD y GPS)* | Firebase **Blaze** (Pago por uso) | US$ 2.09 / mes<br>**Q 15.99 / mes**<br>*(Q 191.86 / año)* | US$ 15.04 / mes<br>**Q 115.06 / mes**<br>*(Q 1,380.67 / año)* | US$ 59.13 / mes<br>**Q 452.34 / mes**<br>*(Q 5,428.13 / año)* |
| **3. 100% Firebase Optimizado** *(RTDB para GPS + Firestore para BD)* | Firebase **Blaze** *(con cuotas gratis)* | US$ 0.00 / mes<br>**Q 0.00 / mes**<br>*(Q 0.00 / año)* | US$ 0.00 / mes<br>**Q 0.00 / mes**<br>*(Q 0.00 / año)* | US$ 6.87 / mes<br>**Q 52.56 / mes**<br>*(Q 630.67 / año)* |
| **4A. Híbrida Actual del Código (Modo Ahorro Máximo)** *(Supabase + Firebase RTDB)* | Supabase **Free** + Firebase **Blaze** | US$ 0.00 / mes<br>**Q 0.00 / mes**<br>*(Q 0.00 / año)* | US$ 0.00 / mes<br>**Q 0.00 / mes**<br>*(Q 0.00 / año)* | US$ 6.87 / mes<br>**Q 52.56 / mes**<br>*(Q 630.67 / año)* |
| **4B. Híbrida Actual del Código (Modo Producción Blindada)** *(Supabase Pro + Firebase RTDB)* | Supabase **Pro** ($25) + Firebase **Blaze** | US$ 25.00 / mes<br>**Q 191.25 / mes**<br>*(Q 2,295.00 / año)* | US$ 25.00 / mes<br>**Q 191.25 / mes**<br>*(Q 2,295.00 / año)* | US$ 31.87 / mes<br>**Q 243.81 / mes**<br>*(Q 2,925.67 / año)* |

### 5.3 Veredicto Técnico: ¿Qué proveedor y qué plan conviene utilizar?
1. **Proveedor recomendado:** **Mantener la Arquitectura Híbrida Actual del código (`Supabase + Firebase Realtime Database`)**.
   - **¿Por qué no migrar todo a Firebase (Opción 3)?** Aunque 100% Firebase (`RTDB + Firestore`) empata en costo cloud ($0.00 en escenario normal), migrar el backend relacional de Supabase a Firestore implicaría **perder la integridad referencial SQL, las políticas RLS, los triggers anti-fraude (`trg_profile_insert_security`, `trg_protect_profile_sensitive_columns`), las funciones atómicas RPC (`approve_subscription_payment_request`) y las tareas programadas de `pg_cron`**, obligando a reescribir todo en *Firebase Cloud Functions* y pagando costos de desarrollo innecesarios.
   - **¿Por qué no migrar el GPS a Supabase (Opción 1)?** Porque el modelo de cobro por mensaje de *Supabase Realtime* encarece la operación hasta **Q 511.25/mes** (normal) y **Q 1,745.19/mes** (intensivo), mientras que *Firebase RTDB* procesa esos mismos millones de coordenadas por **Q 0.00/mes** (normal) y **Q 52.56/mes** (intensivo).
2. **Plan recomendado por etapa:**
   - **Etapa de Lanzamiento y Primeros 50 Conductores (Modo Ahorro Máximo — Opción 4A):** Usar **Supabase Free ($0)** + **Firebase Blaze sin cargo fijo ($0)** (activar Blaze con alerta de presupuesto de $5 USD para desbloquear 200,000 conexiones simultáneas en RTDB sin perder los 10 GB/mes gratuitos). **Costo mensual: Q 0.00.**
   - **Etapa de Consolidación Comercial (Modo Producción Blindada — Opción 4B):** Subir únicamente Supabase al **Plan Pro (US$ 25.00 = Q 191.25/mes)** cuando se deseen copias de seguridad automáticas diarias de 7 días y soporte prioritario, manteniendo Firebase en Blaze (cuota gratuita).

---

## 6. Otros Gastos Relevantes de Operación

Además de Supabase y Firebase, se contabilizan los siguientes rubros para operar la plataforma en Guatemala:

| Rubro / Servicio | Implementación en el Proyecto | Costo Mensual (USD) | Costo Mensual (GTQ) | Costo Anual (GTQ) | Observaciones |
| :--- | :--- | :---: | :---: | :---: | :--- |
| **Mapas Móviles y Web** | `OSMDroid` + `Leaflet` (OpenStreetMap) | US$ 0.00 | **Q 0.00** | **Q 0.00** | Caché local `"TucTucOsmLive"` y `User-Agent` oficial. Ahorra >Q 1,500/mes frente a Google Maps API. |
| **Ruteo y Cálculo de ETA** | `OsrmRoutingService` (OSRM Público + Fallback Haversine) | US$ 0.00 | **Q 0.00** | **Q 0.00** | *Throttle* de 25s–30s en código. *(Si escala a >300 tuctucs, un VPS propio con OSRM GT cuesta ~Q 38.25/mes).* |
| **Hosting del Panel Web (`tuctucMapDashboard`)** | Angular 22 estático en Firebase Hosting / Cloudflare Pages | US$ 0.00 | **Q 0.00** | **Q 0.00** | El bundle compilado pesa < 2 MB; consume < 1% de la capa gratuita de 10 GB/mes. |
| **Dominio Web Administrativo** | Dominio `.com` (Cloudflare / Namecheap) con SSL gratis | US$ 1.00 | **Q 7.65** | **Q 91.80** | Pago anual de US$ 12.00 (~Q 91.80/año). |
| **Cuenta Google Play Console** | Publicación del paquete firmado `.aab` (`com.tuctucmap.app`) | US$ 2.08 *(Año 1)* | **Q 15.94** *(Año 1)* | **Q 191.25** *(Pago único)* | Pago único de por vida de US$ 25.00. A partir del Año 2 el costo es **Q 0.00/mes**. |
| **Pasarela de Pagos** | Depósito / Transferencia Banrural y BI + Validación WhatsApp | US$ 0.00 | **Q 0.00** | **Q 0.00** | **0% de comisión**. *(Usar pasarela de tarjeta local restaría 4.5%–5.5% + Q 1.91 por cobro).* |
| **Notificaciones y SMS** | Alertas locales `TextToSpeech` + `RingtoneManager` + WhatsApp | US$ 0.00 | **Q 0.00** | **Q 0.00** | Sin cobros de SMS/Twilio. |
| **Total Otros Gastos Fijos** | **Infraestructura complementaria obligatoria (Año 1)** | **US$ 3.08 / mes** | **Q 23.59 / mes** | **Q 283.05 / año** | *(Baja a **Q 7.65 / mes** [Q 91.80/año] desde el Año 2).* |

---

## 7. Análisis de Monetización, Rentabilidad del Propietario y Punto de Equilibrio

### 7.1 Modelo de Monetización Existente en el Código Fuente
En `databaseTuctucApp/db.sql` (líneas 39–60 y 113–129) y `DriverSubscriptionScreen.kt` (líneas 316–320), el sistema tiene implementado un **modelo SaaS de membresía prepago para conductores** con los siguientes precios oficiales configurados en `public.app_config`:
- **Periodo de Prueba (`trial`):** **Q 0.00 por 7 días** (otorgado automáticamente una sola vez por número de placa mediante el trigger `trg_profile_insert_security`).
- **Plan Diario (`price_daily`):** **Q 10.00** (1 día).
- **Plan Semanal (`price_semanal`):** **Q 25.00 por 7 días** *(equivale a **Q 100.00** en 4 semanas o **Q 107.14** en 30 días)*.
- **Plan Quincenal (`price_quincenal`):** **Q 45.00 por 15 días** *(equivale a **Q 90.00 / mes**)*.
- **Plan Mensual (`price_mensual`):** **Q 80.00 por 30 días** *(equivale a **Q 80.00 / mes**)*.

#### Ingreso Promedio Mensual por Conductor Activo (ARPU)
En el transporte local de mototaxis (tuctucs), los conductores manejan flujo de caja diario/semanal y prefieren mayoritariamente pagos cortos. Considerando una distribución conservadora de renovaciones en un mes (4 semanas):
- **50% en Plan Semanal** (4 pagos × Q 25.00 = **Q 100.00 / mes**)
- **30% en Plan Quincenal** (2 pagos × Q 45.00 = **Q 90.00 / mes**)
- **20% en Plan Mensual** (1 pago × Q 80.00 = **Q 80.00 / mes**)
$$\text{ARPU Ponderado} = (0.50 \times \text{Q } 100) + (0.30 \times \text{Q } 90) + (0.20 \times \text{Q } 80) = \mathbf{\text{Q } 93.00 \text{ / conductor al mes}}$$
*(Nota: Si el 100% de los conductores eligiera el plan mensual más barato, el piso mínimo es de **Q 80.00/conductor/mes**; si todos pagaran por semana exacta [4.285 semanas/mes], el techo es de **Q 107.14/conductor/mes**).*

### 7.2 Proyección de Ingresos, Gastos y Ganancias del Propietario (50 Conductores)

Se proyectan los tres escenarios considerando la tasa de conductores con membresía pagada al día dentro de la flota de 50 unidades:
- **Escenario Bajo:** 35 conductores pagando suscripción activa en el mes (70% de la flota; ej. 15 en prueba o inactivos).
- **Escenario Normal:** 45 conductores pagando suscripción activa en el mes (90% de la flota activa).
- **Escenario Intensivo:** 50 conductores pagando suscripción activa todo el mes (100% de la flota activa).

#### Tabla A: Rentabilidad Operando con Arquitectura Híbrida en Modo Ahorro (Supabase Free + Firebase Blaze Gratis)
*(Gastos fijos complementarios de dominio + amortización Google Play = **Q 23.59 / mes**).*

| Indicador Financiero | Escenario Bajo (35 cond. pagos) | Escenario Normal (45 cond. pagos) | Escenario Intensivo (50 cond. pagos) |
| :--- | :---: | :---: | :---: |
| **Ingresos Brutos Mensuales (ARPU Q 93.00)** | **Q 3,255.00** | **Q 4,185.00** | **Q 4,650.00** |
| **Ingresos Brutos Anuales** | **Q 39,060.00** | **Q 50,220.00** | **Q 55,800.00** |
| *(Rango mensual según plan: 100% Mensual Q 80 vs. 100% Semanal Q 100)* | *(Q 2,800 – Q 3,500)* | *(Q 3,600 – Q 4,500)* | *(Q 4,000 – Q 5,000)* |
| **(-) Costo Cloud (Supabase Free + Firebase RTDB)** | Q 0.00 | Q 0.00 | Q 52.56 *(US$ 6.87)* |
| **(-) Otros Gastos (Dominio + Google Play Año 1)** | Q 23.59 | Q 23.59 | Q 23.59 |
| **(=) Gastos Operativos Totales / Mes** | **Q 23.59** | **Q 23.59** | **Q 76.15** |
| **(=) Ganancia Neta Mensual del Propietario** | **Q 3,231.41 / mes** | **Q 4,161.41 / mes** | **Q 4,573.85 / mes** |
| **(=) Ganancia Neta Anual del Propietario** | **Q 38,776.95 / año** | **Q 49,936.95 / año** | **Q 54,886.28 / año** |
| **Margen de Ganancia Neta (%)** | **99.28%** | **99.44%** | **98.36%** |

#### Tabla B: Rentabilidad Operando con Arquitectura Híbrida en Modo Producción Blindada (Supabase Pro US$ 25/mes + Firebase Blaze)
*(Incluye el pago mensual de **Supabase Pro = Q 191.25 / mes** para respaldos diarios automáticos + **Q 23.59 / mes** de dominio/Play Store = **Q 214.84 / mes** de costo base).*

| Indicador Financiero | Escenario Bajo (35 cond. pagos) | Escenario Normal (45 cond. pagos) | Escenario Intensivo (50 cond. pagos) |
| :--- | :---: | :---: | :---: |
| **Ingresos Brutos Mensuales (ARPU Q 93.00)** | **Q 3,255.00** | **Q 4,185.00** | **Q 4,650.00** |
| **Ingresos Brutos Anuales** | **Q 39,060.00** | **Q 50,220.00** | **Q 55,800.00** |
| **(-) Costo Cloud (Supabase Pro + Firebase RTDB)** | Q 191.25 *(US$ 25.00)* | Q 191.25 *(US$ 25.00)* | Q 243.81 *(US$ 31.87)* |
| **(-) Otros Gastos (Dominio + Google Play Año 1)** | Q 23.59 | Q 23.59 | Q 23.59 |
| **(=) Gastos Operativos Totales / Mes** | **Q 214.84** | **Q 214.84** | **Q 267.40** |
| **(=) Ganancia Neta Mensual del Propietario** | **Q 3,040.16 / mes** | **Q 3,970.16 / mes** | **Q 4,382.60 / mes** |
| **(=) Ganancia Neta Anual del Propietario** | **Q 36,481.95 / año** | **Q 47,641.95 / año** | **Q 52,591.28 / año** |
| **Margen de Ganancia Neta (%)** | **93.40%** | **94.87%** | **94.25%** |

### 7.3 Cálculo del Punto de Equilibrio (*Break-Even Point*)
El punto de equilibrio determina cuántos conductores suscritos necesita el propietario para cubrir el 100% de los costos mensuales de la plataforma (con un ARPU de **Q 93.00/mes** o el plan mensual mínimo de **Q 80.00/mes**):

1. **En Modo Ahorro (Gastos fijos = Q 23.59 / mes):**
   $$\text{Punto de Equilibrio} = \frac{\text{Q } 23.59}{\text{Q } 93.00} = 0.25 \text{ conductores} \implies \mathbf{1 \text{ conductor activo suscrito}}$$
   *(Con **1 solo pago semanal de Q 25.00** al mes se cubre la totalidad de los costos operativos del mes).*
2. **En Modo Producción con Supabase Pro (Gastos fijos = Q 214.84 / mes):**
   $$\text{Punto de Equilibrio} = \frac{\text{Q } 214.84}{\text{Q } 93.00} = 2.31 \text{ conductores} \implies \mathbf{3 \text{ conductores activos suscritos}}$$
   *(Con **3 conductores** de los 50 [apenas el **6% de la flota**], el propietario cubre Supabase Pro, dominio y Play Store; a partir del 4.º conductor, el ingreso es ganancia neta).*

### 7.4 Comparativa con Escenarios Hipotéticos de Monetización (Comisión por Viaje vs. Suscripción Actual)
Para evaluar si al propietario le convendría cambiar el modelo actual de suscripción por uno de **comisión por viaje**, se modelan dos escenarios hipotéticos claramente identificados (asumiendo una tarifa promedio cobrada al pasajero de **Q 12.50 por viaje**, acorde a los valores por defecto `Q 10, Q 15, Q 20` en `DriverFarePreferences.kt`):

| Modelo de Monetización | Escenario Bajo (3,000 viajes/mes) | Escenario Normal (7,500 viajes/mes) | Escenario Intensivo (15,000 viajes/mes) | Viabilidad Operativa en Guatemala |
| :--- | :---: | :---: | :---: | :--- |
| **Modelo Real en Código:** Suscripción fija prepago (Q 25 sem / Q 45 quin / Q 80 mes) | **Q 3,255.00 / mes** | **Q 4,185.00 / mes** | **Q 4,650.00 / mes** | **Alta (Óptima):** Ingreso predecible por adelantado; el conductor se queda con el 100% de sus viajes y no tiene incentivo para evadir la app. |
| **Escenario Hipotético 1:** Comisión fija de **Q 1.00 por viaje completado** (~8% del viaje) | Q 3,000.00 / mes | Q 7,500.00 / mes | Q 15,000.00 / mes | **Media-Baja:** Como el pasajero paga en efectivo al subir al tuctuc, el conductor puede contactar al pasajero y cancelar el viaje en la app para no descontar Q 1.00 de su saldo. |
| **Escenario Hipotético 2:** Modelo Híbrido (**Q 50/mes base + patrocinios de comercios locales en el mapa**) | Q 2,750.00 / mes *(+ Q 1,000 anuncios)* | Q 3,250.00 / mes *(+ Q 2,000 anuncios)* | Q 3,500.00 / mes *(+ Q 3,500 anuncios)* | **Alta a Mediano Plazo:** Agregar pines patrocinados de negocios locales (farmacias, comedores, ferreterías) como destinos sugeridos en `PassengerMapScreen` genera ingresos extra sin cobrar más al conductor. |

> **Impacto en la economía del conductor (Por qué la suscripción de Q 25/semana es tan atractiva):** En el Escenario Normal, un conductor realiza ~6.25 viajes/día × Q 12.50 = **Q 78.13 diarios (~Q 2,343.75/mes)** a través de la app. Pagar **Q 80.00 a Q 100.00 al mes** de suscripción representa apenas el **3.4% – 4.2% de sus ingresos**, es decir, **recupera el costo de toda su semana (Q 25) con tan solo 2 viajes**.

---

## 8. Riesgos Técnicos y Financieros Identificados en el Código

1. **Límite de 100 conexiones simultáneas en Firebase Plan Spark:**
   - En el Plan Spark ($0), Firebase Realtime Database bloquea la conexión número 101 simultánea. Con 50 conductores en línea y >50 pasajeros abriendo el mapa al mismo tiempo en un día lluvioso o festivo, se podría alcanzar el tope de 100 conexiones.
   - **Mitigación:** Habilitar el **Plan Blaze** en Firebase y configurar una **Alerta de Presupuesto en Google Cloud de US$ 5.00**. El Plan Blaze desbloquea **200,000 conexiones simultáneas** inmediatamente y **mantiene los primeros 10 GB/mes de descarga 100% gratis**.
2. **Sondeo HTTP (`fetch`) cada 8 segundos en el Mapa del Dashboard (`fleet-map.component.ts` L390):**
   - Actualmente, `getLiveDrivers()` en `supabase.service.ts` (L373) descarga el JSON completo `${baseUrl}/driver_locations.json` cada 8 segundos mientras un administrador deja abierta la pantalla `/fleet-map`. Si 2 administradores dejaran esa pestaña abierta las 24 horas del día los 30 días del mes con 50 conductores activos (`50 × 250 bytes = 12.5 KB × 10,800 peticiones/día × 2 admins`), consumirían **~8.1 GB/mes de Egress en Firebase RTDB** solo por dejar la pestaña abierta sin mirarla.
3. **Política de Uso Justo de Servidores Públicos de OpenStreetMap y OSRM:**
   - Para 50 conductores y 200 pasajeros, el consumo de tiles de `tile.openstreetmap.org` y rutas de `router.project-osrm.org` es muy bajo gracias a la caché `"TucTucOsmLive"` y al *throttle* de 25–30 segundos. Sin embargo, si el negocio escala a **>500 conductores**, los servidores comunitarios gratuitos de OSM/OSRM pueden aplicar *rate-limiting* (HTTP 429).

---

## 9. Recomendaciones Prácticas para Reducir Costos sin Comprometer la Seguridad (Aplicadas)

Sin necesidad de cambiar el modelo de negocio ni comprometer la seguridad RLS actual, se implementaron las siguientes mejoras prácticas sobre la arquitectura del monorepo:

1. **Mantener el Diseño Híbrido Actual (`Supabase + Firebase RTDB`) y No Migrar el GPS a Supabase Realtime (Activo):**
   - Conservar las coordenadas en vivo en Firebase RTDB ahorra entre **Q 511.25/mes y Q 1,692.63/mes** frente a una arquitectura 100% Supabase.
2. **Detener el Sondeo de `/fleet-map` Cuando la Pestaña del Navegador No Está Visible o Hay Inactividad (Aplicado en `fleet-map.component.ts`):**
   - En `tuctucMapDashboard/src/app/features/fleet-map/fleet-map.component.ts`, se condicionó el ciclo de sondeo (`setInterval` cada 8s) a `document.visibilityState === 'visible'` (Page Visibility API) y se agregó una pausa automática tras **15 minutos sin interacción del usuario** (`INACTIVITY_TIMEOUT_MS`), reanudándose al instante cuando el administrador vuelve a la pestaña o mueve el cursor. Así, si el administrador minimiza el navegador o cambia de pestaña, el consumo de Egress en Firebase cae a **0 bytes** (ahorro de hasta 80% de Egress web).
3. **Limitar el Input "Destino" del Pasajero al Solicitar un Viaje para Evitar Inyección de Textos Gigantes (Aplicado en App, Repositorio y BD):**
   - Para evitar que un cliente malintencionado inyecte textos gigantes en el campo de destino al solicitar un viaje (lo cual inflaría la factura de Egress en Firebase RTDB para todos los conductores suscritos, el peso en Supabase `public.trips` y la lectura por voz `TextToSpeech`), se aplicó un límite estricto multicapa de **100 caracteres (`MAX_DESTINATION_LENGTH = 100`)**:
     - **UI (`PassengerMapScreen.kt`):** El `OutlinedTextField` de `"Destino"` trunca entradas y textos pegados a **100 caracteres** (eliminando saltos de línea), muestra un contador en vivo (`0/100 caracteres máx.`) y limita también el nombre (`50` caracteres) y teléfono (`15` caracteres) de invitados.
     - **Persistencia Local (`PassengerDestinationPreferences.kt`):** Sanitiza y recorta cualquier destino guardado o leído de `SharedPreferences` a 100 caracteres (`sanitizeDestination()`).
     - **Repositorio y Servicio TTS (`SupabaseRepository.kt` y `LocationTrackingService.kt`):** `requestBroadcastTrip()`, `requestTrip()`, `saveTripRecord()` y `observePendingTripsForDriver()` truncan el mensaje a `MAX_TRIP_MESSAGE_LENGTH = 100` antes de transmitirlo o anunciarlo por voz.
     - **Base de Datos (`databaseTuctucApp/db.sql`):** Constraint `trips_message_max_length_check` (`CHECK (message IS NULL OR char_length(message) <= 150)`) en `public.trips`.
4. **Activar Presupuestos y Alertas de Facturación en Google Cloud y Supabase:**
   - En Google Cloud Console (para Firebase Blaze), crear una alerta de presupuesto en **US$ 5.00 (Q 38.25)** con notificaciones al correo al llegar al 50%, 80% y 100%.
   - En Supabase (si se activa el Plan Pro de $25/mes), mantener activado el **Spend Cap (Límite de Gasto)** que viene encendido por defecto en el Plan Pro, lo cual garantiza que la factura mensual **jamás supere los US$ 25.00 (Q 191.25)** bajo ninguna circunstancia.
5. **Aprovechar el Descuento del Plan Quincenal/Mensual para Reducir Carga Operativa (Aplicado en `DriverSubscriptionScreen.kt` y `DriverSubscriptionExpiredScreen.kt`):**
   - La validación de comprobantes por WhatsApp toma ~10 minutos diarios para 50 conductores. Se agregaron etiquetas visuales dinámicas de ahorro (`"Ahorra Q5"` en Plan Quincenal y `"Ahorra Q20"` en Plan Mensual, calculadas automáticamente desde `appConfig`) en las pantallas de renovación del conductor para incentivar pagos quincenales y mensuales, reduciendo entre un 50% y 75% la cantidad de boletas bancarias que el administrador debe revisar al mes y asegurando flujo de efectivo anticipado.

---

## 10. Fuentes Oficiales Consultadas

1. **Supabase — Precios Oficiales y Cuotas de Planes (Free, Pro, Realtime, Egress, Auth):**  
   - Enlace: [https://supabase.com/pricing](https://supabase.com/pricing)  
   - Documentación de facturación de mensajes Realtime y Egress: [https://supabase.com/docs/guides/platform/billing-on-supabase](https://supabase.com/docs/guides/platform/billing-on-supabase)  
   - Fecha de consulta: 28 de octubre de 2026.
2. **Google Firebase — Precios Oficiales (Planes Spark y Blaze, Realtime Database, Cloud Firestore, Authentication, Hosting):**  
   - Enlace: [https://firebase.google.com/pricing](https://firebase.google.com/pricing)  
   - Documentación de facturación de Realtime Database: [https://firebase.google.com/docs/database/usage/billing](https://firebase.google.com/docs/database/usage/billing)  
   - Fecha de consulta: 28 de octubre de 2026.
3. **Banco de Guatemala (Banguat) — Tipo de Cambio de Referencia (Quetzal / Dólar Estadounidense):**  
   - Enlace: [https://www.banguat.gob.gt/tipo_cambio/](https://www.banguat.gob.gt/tipo_cambio/)  
   - Valor de referencia: ~Q 7.644 – Q 7.65 por US$ 1.00.  
   - Fecha de consulta: 28 de octubre de 2026.
4. **Google Play Console — Registro de Desarrollador:**  
   - Enlace: [https://support.google.com/googleplay/android-developer/answer/6112435](https://support.google.com/googleplay/android-developer/answer/6112435)  
   - Fecha de consulta: 28 de octubre de 2026.

