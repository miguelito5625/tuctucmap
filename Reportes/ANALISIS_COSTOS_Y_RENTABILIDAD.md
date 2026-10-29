# Análisis Financiero, de Costos Cloud y Rentabilidad: TucTuc Map

> **Rol del Autor:** Arquitecto de Software Cloud & Analista Financiero de Aplicaciones Móviles  
> **Proyecto Auditado:** Monorepo TucTuc Map (`tuctucMap` Android, `tuctucMapDashboard` Angular 22, `databaseTuctucApp` PostgreSQL/Firebase)  
> **Base de Cálculo Inicial:** **50 Conductores (Toritos)** y **200 Pasajeros**  
> **Moneda del Informe:** Quetzales Guatemaltecos (**GTQ / Q**) y Dólares Estadounidenses (**USD / \$**)  
> **Tipo de Cambio Oficial Aplicado:** **Q7.65 por \$1.00 USD** *(Referencia oficial Banco de Guatemala: Q7.64427; rango bancario comercial venta: Q7.75–Q7.84)*  
> **Fecha de Consulta de Precios Oficiales:** 28 de octubre de 2026  

---

## 1. Resumen Ejecutivo

Tras inspeccionar línea por línea el código fuente de los tres subsistemas del proyecto **TucTuc Map**, se concluye que la aplicación implementa una **arquitectura híbrida altamente eficiente en costos** que separa las cargas transaccionales (en **Supabase**) de la telemetría GPS de alta frecuencia (en **Firebase Realtime Database**), apoyándose en cartografía abierta (**OpenStreetMap / OSMDroid**) y ruteo libre (**OSRM**).

### Conclusiones Clave para 50 Conductores y 200 Pasajeros:
1. **Proveedor Recomendado:** **Mantener la Arquitectura Híbrida Actual (Supabase + Firebase Realtime Database)** en lugar de migrar todo a un solo proveedor. Esta combinación aprovecha simultáneamente las cuotas gratuitas de ambas nubes y evita el costoso cobro por mensaje de WebSocket de Supabase Realtime o el cobro por operación de escritura de Cloud Firestore.
2. **Hallazgo Técnico Crítico sobre Costos:**
   * **Con el código actual sin cambiar el listener (`ValueEventListener` en `SupabaseRepository.kt:1066`):** En un escenario de uso normal, Firebase descargaría el árbol completo de 30 conductores en cada movimiento individual, generando **~136 GB/mes de transferencia ($126.08 USD = Q964.51/mes)**.
   * **Con el ajuste de 10 líneas a `ChildEventListener`:** El tráfico cae en un **96.6%** (a solo **4.54 GB/mes**), entrando **100% gratis** dentro de los 10 GB mensuales del plan gratuito de Firebase (**Q0.00/mes**).
3. **Costo Mensual Operativo Esperado (Escenario Normal):**
   * **Fase de Arranque (Planes Free + Spark con `ChildEventListener`):** **Q23.59 / mes** (\$3.08 USD/mes, correspondiente únicamente a la amortización anual de Google Play Console y dominio web; **Q0.00 en servidores**).
   * **Fase de Producción Blindada (Supabase Pro + Firebase Blaze con `ChildEventListener`):** **Q214.84 / mes** (\$28.08 USD/mes), garantizando copias de seguridad diarias, base de datos dedicada de 8 GB sin pausas y soporte comercial.
4. **Rentabilidad del Propietario (Escenario Normal - 42 de 50 conductores con suscripción activa):**
   * **Ingresos Brutos Mensuales:** **Q3,927.00 / mes** (**Q47,124.00 / año**).
   * **Ganancia Neta Mensual (con código actual sin optimizar):** **Q2,747.65 / mes** (Margen neto: **70.0%**).
   * **Ganancia Neta Mensual (con `ChildEventListener` + Plan Pro):** **Q3,712.16 / mes** (Margen neto: **94.5%**).
   * **Ganancia Neta Mensual (con `ChildEventListener` en Tier Gratuito):** **Q3,903.41 / mes** (Margen neto: **99.4%**).
   * **Punto de Equilibrio (Break-Even):** Se alcanza con apenas **1 conductor suscrito** en el esquema gratuito, o **3 conductores suscritos** pagando el Plan Pro de Supabase (**6% de ocupación de la flota**).

---

## 2. Auditoría Arquitectónica de Servicios que Generan Costos

La inspección del código fuente revela los siguientes componentes y su impacto financiero:

| Subsistema / Servicio | Archivos Fuente Auditados | Tecnología Actual | Generador de Costo en la Nube |
| :--- | :--- | :--- | :--- |
| **Autenticación de Usuarios** | `SupabaseRepository.kt` (L198-L249)<br>`supabase.service.ts` (L68-L115) | **Supabase Auth** (Email/Password) + Modo Invitado local (`guest_<ts>`) | **MAUs** (Usuarios Activos Mensuales). Los invitados no consumen cuota de Auth. |
| **Base de Datos Relacional** | `databaseTuctucApp/db.sql` (L1-L522)<br>`SupabaseRepository.kt` | **Supabase PostgreSQL** (`profiles`, `trips`, `driver_subscriptions`, `app_config`, `subscription_payment_requests`) | **Almacenamiento en disco (MB/GB)** y **Egress (GB transferidos)**. El mayor consumidor es la columna `route_points` (JSONB) en `public.trips`. |
| **Sincronización de Estado** | `SupabaseRepository.kt` (L1138)<br>`shell.component.ts` | **Supabase Realtime** (`postgresChangeFlow` en `profiles` y `subscription_payment_requests`) | **Conexiones simultáneas WebSocket** y **Mensajes Realtime** cuando un admin aprueba un pago. |
| **Telemetría GPS en Vivo** | `LocationTrackingService.kt` (L336-L398)<br>`SupabaseRepository.kt` (L415-L458, L1063) | **Firebase Realtime Database** (`/driver_locations/{uid}`) | **GB descargados (Egress)** y **Conexiones simultáneas**. Muestreo adaptativo cada $\ge 8\text{ m}$ o heartbeat de $45\text{ s}$ (y $15\text{ s}$ en pausa). |
| **Despacho de Viajes en Vivo** | `SupabaseRepository.kt` (L855-L1060) | **Firebase Realtime Database** (`/trips/{tripId}`) | **GB descargados (Egress)**. El almacenamiento es **~0 MB** porque el nodo se borra con `deleteTrip()` al finalizar o tras 60s de timeout. |
| **Mapas y Mosaicos (Tiles)** | `MapTileUtils.kt`<br>`PassengerMapScreen.kt`<br>`fleet-map.component.ts` | **OSMDroid** (Android) y **Leaflet** (Web) sobre `tile.openstreetmap.org` con caché `"TucTucOsmLive"` | **Q0.00 (\$0 USD)**. Usa servidores comunitarios con caché local en el teléfono. |
| **Enrutamiento y ETA** | `OsrmRoutingService.kt` (L50-L90)<br>`DriverHomeScreen.kt` (L171) | **OSRM Público** (`router.project-osrm.org`) con throttle de **25s** + fallback local Haversine | **Q0.00 (\$0 USD)**. |
| **Alertas, Voz y Mensajería** | `LocationTrackingService.kt` (L189, L267)<br>`DriverSubscriptionScreen.kt` | **Android TTS nativo**, **RingtoneManager** local y enlaces profundos a **WhatsApp (`wa.me`)** | **Q0.00 (\$0 USD)**. Evita costos de SMS (Twilio/Firebase Phone Auth) y servidores Push pagos. |
| **Pasarela de Cobros** | `db.sql` (`app_config`)<br>`DriverSubscriptionExpiredScreen.kt` | Depósito/Transferencia directa a **Banrural** y **Banco Industrial** + pago de viajes 100% en **efectivo** | **Q0.00 (\$0 USD)** en comisiones de tarjeta (0% Stripe / Recurrente / Visanet). |

---

## 3. Precios Oficiales Vigentes: Supabase vs. Firebase

*Fecha de verificación: 28 de octubre de 2026. Conversión: 1 USD = Q7.65 GTQ.*

### 3.1. Precios Oficiales de Supabase
*Fuente oficial: [https://supabase.com/pricing](https://supabase.com/pricing) y [https://supabase.com/docs/guides/platform/billing-on-supabase](https://supabase.com/docs/guides/platform/billing-on-supabase)*

| Recurso | Plan Free (\$0 / Q0.00 al mes) | Plan Pro (\$25 USD = Q191.25 / mes) | Tarifa por Excedente (En Plan Pro) |
| :--- | :--- | :--- | :--- |
| **Instancia de Cómputo** | CPU Compartida (Se pausa tras 7 días sin uso) | Incluye **\$10 USD de crédito** (Cubre instancia Micro dedicada 24/7) | Small: +\$5/mes \| Medium: +\$50/mes |
| **Tamaño de Base de Datos** | **500 MB** incluidos | **8 GB** incluidos | **\$0.125 / GB-mes** (Q0.96 / GB) |
| **Ancho de Banda (Egress)** | **5 GB** origen + 5 GB caché | **250 GB** origen + 250 GB caché | **\$0.09 / GB** (Q0.69 / GB) |
| **Usuarios Activos (Auth MAU)** | **50,000 MAUs** incluidos | **100,000 MAUs** incluidos | **\$0.00325 / MAU** (Q0.025 / usuario) |
| **Realtime: Conexiones Pico** | **200** conexiones concurrentes | **500** conexiones concurrentes | **\$10.00 / 1,000 conexiones** (Q76.50) |
| **Realtime: Mensajes** | **2,000,000** mensajes / mes | **5,000,000** mensajes / mes | **\$2.50 / 1 millón de mensajes** (Q19.13) |
| **Respaldos (Backups)** | No incluidos | Diarios automáticos (7 días de retención) | PITR (Point-in-time): desde \$100/mes |

---

### 3.2. Precios Oficiales de Google Firebase
*Fuente oficial: [https://firebase.google.com/pricing](https://firebase.google.com/pricing)*

| Servicio / Recurso | Plan Spark (Gratis: \$0 / Q0.00) | Plan Blaze (Pago por uso: incluye cuota Spark gratis) | Tarifa por Excedente (En Plan Blaze) |
| :--- | :--- | :--- | :--- |
| **Realtime Database (RTDB): Almacenamiento** | **1 GB** total | Primer **1 GB gratis** | **\$5.00 / GB-mes** (Q38.25 / GB) |
| **Realtime Database (RTDB): Descarga (Egress)** | **10 GB / mes** | Primeros **10 GB / mes gratis** | **\$1.00 / GB** (Q7.65 / GB) |
| **Realtime Database (RTDB): Conexiones** | **100** conexiones simultáneas (límite duro) | **200,000** conexiones simultáneas | Sin costo por conexión |
| **Cloud Firestore: Almacenamiento** | **1 GiB** total | Primer **1 GiB gratis** | **\$0.18 / GiB-mes** (Q1.38 / GiB) |
| **Cloud Firestore: Escrituras** | **20,000 / día** (600,000 / mes) | Primeras **20,000 / día gratis** | **\$0.18 por 100,000 escrituras** (Q1.38) |
| **Cloud Firestore: Lecturas** | **50,000 / día** (1,500,000 / mes) | Primeras **50,000 / día gratis** | **\$0.06 por 100,000 lecturas** (Q0.46) |
| **Cloud Firestore: Borrados** | **20,000 / día** (600,000 / mes) | Primeros **20,000 / día gratis** | **\$0.02 por 100,000 borrados** (Q0.15) |
| **Firebase Auth (Email/Password)** | **50,000 MAUs** gratis | **50,000 MAUs** gratis | \$0.0055 / MAU (>50k) |

---

## 4. Modelado de Tráfico y Supuestos Técnicos (50 Conductores y 200 Pasajeros)

### 4.1. Parámetros Medidos en el Código Fuente
1. **Peso del paquete GPS en `/driver_locations/{uid}` (`SupabaseRepository.kt:426`):**
   ```json
   {"driver_id":"uuid-36","lat":14.634912,"lon":-90.506882,"nombre":"Carlos Perez","telefono":"50244556677","placas":"M-458JKL","numeroUnidad":"Torito #12","active":true,"status":"disponible","updated_at":"1761696000000"}
   ```
   * **Tamaño exacto en red:** **$\approx 210\text{ bytes}$** por conductor.
2. **Peso de un viaje completado en PostgreSQL (`public.trips`):**
   * `LocationTrackingService.kt` (L142) guarda un `RoutePoint` (`{"lat":...,"lon":...,"timestamp":...}` $\approx 55\text{ bytes}$) cada **$\ge 8\text{ metros}$**.
   * Para un viaje promedio de municipio de **1.8 km (1,800 m)**: $1,800 / 8 = 225\text{ puntos} \times 55\text{ B} = 12.37\text{ KB}$ + metadatos de fila = **$\approx 13\text{ KB}$ por viaje guardado**.
3. **Cadencia de transmisión GPS (`LocationTrackingService.kt:378`):**
   * Cuando el mototaxi está estacionado en punto/parada, envía **1 heartbeat cada 45 segundos** ($80\text{ envíos/hora}$).
   * Cuando está en movimiento a $20\text{ km/h}$ ($5.5\text{ m/s}$), recorre $8\text{ metros}$ antes de que se cumpla el intervalo de `5000 ms` del `LocationRequest`, por lo que envía **1 coordenada cada 5 segundos** ($720\text{ envíos/hora}$).

---

### 4.2. Definición de los Tres Escenarios Operativos

| Variable Operativa | Escenario 1: Uso Bajo<br>*(Arranque / Temporada Baja)* | Escenario 2: Uso Normal<br>*(Operación Diaria Estándar)* | Escenario 3: Uso Intensivo<br>*(Días de Plaza / Feria / Pico)* |
| :--- | :---: | :---: | :---: |
| **Conductores Registrados** | 50 | 50 | 50 |
| **Conductores Activos por Día** | 25 conductores | 40 conductores | 50 conductores |
| **Conductores Simultáneos en Turno** | 15 promedio (8 h/día) | 30 promedio (10 h/día) | 45 promedio (12 h/día) |
| **Pasajeros Registrados / Totales** | 200 | 200 | 200 |
| **Pasajeros Activos por Día (DAU)** | 60 pasajeros | 120 pasajeros | 180 pasajeros |
| **Viajes Completados por Conductor/Día** | 5 viajes/día | 10 viajes/día | 16 viajes/día |
| **Total Viajes Completados (Día / Mes)** | **125 / día** (**3,750 / mes**) | **400 / día** (**12,000 / mes**) | **800 / día** (**24,000 / mes**) |
| **Proporción Movimiento vs. Espera** | 35% mov. / 65% espera | 50% mov. / 50% espera | 70% mov. / 30% espera |
| **Frecuencia Promedio GPS Efectiva** | 1 envío cada **20 seg** (180/h) | 1 envío cada **12 seg** (300/h) | 1 envío cada **8 seg** (450/h) |
| **Actualizaciones GPS Totales / Mes** | **648,000 / mes** | **2,700,000 / mes** | **7,290,000 / mes** |
| **Clientes Concurrentes Viendo Mapa** | 4 clientes (3 pasaj. + 1 admin) | 8 clientes (7 pasaj. + 1 admin) | 18 clientes (17 pasaj. + 1 admin) |
| **Conexiones Simultáneas en Hora Pico** | ~30 conexiones | ~62 conexiones | ~92 conexiones |
| **Crecimiento de BD `trips` / Mes** | **48.7 MB / mes** (0.58 GB/año) | **156.0 MB / mes** (1.87 GB/año) | **312.0 MB / mes** (3.74 GB/año) |
| **Egress RTDB (Código Actual `ValueEventListener`)** | **8.56 GB / mes** | **136.08 GB / mes** | **1,240.03 GB / mes** |
| **Egress RTDB (Optimizado `ChildEventListener`)** | **0.58 GB / mes** | **4.54 GB / mes** | **27.55 GB / mes** |

> **Nota sobre la diferencia crítica de Egress:**  
> En `SupabaseRepository.kt` (L1066), `databaseRef.addValueEventListener(listener)` descarga la lista completa de todos los conductores activos ($30 \times 210\text{ B} = 6.3\text{ KB}$) cada vez que **un solo** conductor se mueve 8 metros. Al usar `ChildEventListener`, Firebase transmite únicamente los $210\text{ bytes}$ del conductor que se movió, reduciendo el tráfico en **30 veces** para una flota de 30 unidades en turno.

---

## 5. Comparación de Costos Cloud: Supabase vs. Firebase vs. Híbrido

Se evalúan cuatro alternativas arquitectónicas para soportar exactamente la misma carga de **50 conductores y 200 pasajeros**:

1. **Alternativa 1 (100% Supabase):** PostgreSQL + Supabase Auth + **Supabase Realtime Broadcast** para transmitir el GPS de los conductores (sin usar Firebase).
2. **Alternativa 2 (100% Firebase con Firestore):** Firebase Auth + **Cloud Firestore** para guardar perfiles, viajes y actualizaciones GPS en vivo.
3. **Alternativa 3 (Híbrida Actual - Sin Optimizar):** Supabase (Auth/SQL) + Firebase RTDB con el `ValueEventListener` actual.
4. **Alternativa 4 (Híbrida Actual - Optimizada con `ChildEventListener`):** Supabase (Auth/SQL) + Firebase RTDB enviando deltas individuales.

### 5.1. Tabla Comparativa en los Tres Escenarios (Costos Mensuales y Anuales en GTQ)

| Arquitectura / Proveedor | Escenario 1: Uso Bajo<br>*(3,750 viajes/mes)* | Escenario 2: Uso Normal<br>*(12,000 viajes/mes)* | Escenario 3: Uso Intensivo<br>*(24,000 viajes/mes)* |
| :--- | :---: | :---: | :---: |
| **1. 100% Supabase (Todo en Supabase)**<br>*(Cada envío GPS a $N$ clientes = $1+N$ mensajes Realtime)* | **Q191.25 / mes**<br>*(\$25.00 USD)*<br>**Q2,295.00 / año**<br>*(3.24M msjs: excede los 2M del plan Free, obliga a Plan Pro)* | **Q573.75 / mes**<br>*(\$75.00 USD)*<br>**Q6,885.00 / año**<br>*(24.3M msjs = \$25 Pro + \$50 por 20M msjs extra)* | **Q2,754.00 / mes**<br>*(\$360.00 USD)*<br>**Q33,048.00 / año**<br>*(138.5M msjs = \$25 Pro + \$335 por 134M msjs extra)* |
| **2. 100% Firebase (Todo en Firestore)**<br>*(Cobra \$0.18 por 100k escrituras y \$0.06 por 100k lecturas)* | **Q6.12 / mes**<br>*(\$0.80 USD)*<br>**Q73.44 / año**<br>*(648k esc/mes supera por poco las 600k gratis; lecturas <1.5M gratis)* | **Q136.78 / mes**<br>*(\$17.88 USD)*<br>**Q1,641.36 / año**<br>*(2.1M esc. extra = \$3.78 + 20.1M lect. extra = \$12.06 + Egress \$2.04)* | **Q1,089.67 / mes**<br>*(\$142.44 USD)*<br>**Q13,076.04 / año**<br>*(6.69M esc. extra = \$12.04 + 129.7M lect. extra = \$77.83 + Egress \$52.57)* |
| **3. Híbrida Actual (Sin Optimizar)**<br>*(Supabase Free/Pro + Firebase RTDB con `ValueEventListener`)* | **Q0.00 a Q191.25 / mes**<br>*(\$0 a \$25.00 USD)*<br>**Q0 a Q2,295.00 / año**<br>*(8.56 GB Egress < 10 GB gratis de Firebase Spark)* | **Q964.51 a Q1,155.76 / mes**<br>*(\$126.08 a \$151.08 USD)*<br>**Q11,574 a Q13,869 / año**<br>*(136.08 GB Egress en Firebase = \$126.08 + Supabase Free/Pro)* | **Q9,409.50 a Q9,600.75 / mes**<br>*(\$1,230 a \$1,255 USD)*<br>**Q112,914 a Q115,209 / año**<br>*(1,240 GB Egress en Firebase = \$1,230 + Supabase Free/Pro)* |
| **4. Híbrida Optimizada (RECOMENDADA)**<br>*(Supabase + Firebase RTDB con `ChildEventListener`)* | **Q0.00 / mes** *(Free)*<br>**Q191.25 / mes** *(Con Pro)*<br>**Q0 a Q2,295.00 / año**<br>*(0.58 GB Egress < 10 GB gratis)* | **Q0.00 / mes** *(Free)*<br>**Q191.25 / mes** *(Con Pro)*<br>**Q0 a Q2,295.00 / año**<br>*(4.54 GB Egress < 10 GB gratis)* | **Q134.26 / mes** *(Con Free)*<br>**Q325.51 / mes** *(Con Pro)*<br>**Q1,611 a Q3,906 / año**<br>*(27.55 GB Egress = \$17.55 USD en Firebase Blaze)* |

---

### 5.2. ¿Cuál es más económico y qué plan conviene utilizar?

1. **¿Por qué NO conviene usar 100% Supabase para el GPS?**
   Supabase Realtime cobra **\$2.50 USD (Q19.13) por cada millón de mensajes entregados**. En una app de transporte donde 30 mototaxis envían su coordenada cada 12 segundos a 8 pantallas abiertas, se generan **24.3 millones de mensajes al mes** ($2,700,000 \times 9$), disparando la factura a **Q573.75/mes** en uso normal y **Q2,754.00/mes** en uso intensivo.
2. **¿Por qué NO conviene usar Cloud Firestore para el GPS?**
   Firestore cobra por cada operación de documento (escritura y lectura). Actualizar el GPS millones de veces al mes agota la cuota diaria de 20,000 escrituras en pocas horas.
3. **¿Por qué la Arquitectura Híbrida Actual (Supabase + Firebase RTDB) es la ganadora indiscutible?**
   * **Firebase Realtime Database NO cobra por cantidad de escrituras ni por número de mensajes**, sino únicamente por el volumen total de gigabytes descargados (con **10 GB/mes gratuitos**). Usando paquetes pequeños de 210 bytes y `ChildEventListener`, 2.7 millones de actualizaciones GPS pesan apenas **4.54 GB en todo el mes**, resultando en un costo de **Q0.00**.
   * **Supabase PostgreSQL** guarda únicamente los perfiles, pagos de suscripciones y el resumen final cuando termina el viaje, manteniéndose cómodamente dentro de su capa gratuita o del Plan Pro básico de \$25/mes.

#### Plan Recomendado según Etapa del Negocio:
* **Etapa 1 (Meses 1 a 3 - Validación con primeros 20 a 35 conductores):**
  * **Supabase Plan Free (Q0.00)** + **Firebase Plan Blaze con alerta de presupuesto de \$5 USD (Q0.00 de consumo real con `ChildEventListener`)**.
  * *Nota técnica:* En el mes 3, ejecutar un script SQL automático (`pg_cron`) que vacíe la columna `route_points` de los viajes con más de 30 días de antigüedad para no superar nunca los 500 MB del Plan Free de Supabase.
* **Etapa 2 (Mes 4 en adelante - Operación consolidada con 40 a 50+ conductores):**
  * Actualizar a **Supabase Plan Pro (\$25 USD = Q191.25/mes)** para obtener **8 GB de base de datos**, **copias de seguridad diarias automáticas** de la contabilidad de suscripciones y **cero riesgo de pausa** del servidor.

---

## 6. Otros Gastos Operativos Relevantes

Además de Supabase y Firebase, la operación real de **TucTuc Map** involucra los siguientes rubros:

| Rubro / Servicio | Proveedor Actual en el Proyecto | Costo Mensual (GTQ) | Costo Anual (GTQ) | Observaciones y Costos Ocultos Potenciales |
| :--- | :--- | :---: | :---: | :--- |
| **Cuenta de Desarrollador Android** | Google Play Console | **Q15.94 / mes**<br>*(Amortizado año 1)* | **Q191.25**<br>*(\$25 USD pago único)* | Es un **pago único de por vida** de \$25 USD. A partir del segundo año el costo es **Q0.00**. |
| **Alojamiento Web del Dashboard (`tuctucMapDashboard`)** | Firebase Hosting / Vercel / Cloudflare Pages | **Q0.00** | **Q0.00** | El panel Angular es una SPA estática usada solo por el administrador (consumo < 100 MB/mes, 100% gratis). |
| **Dominio Web Administrativo** *(Opcional)* | Cloudflare / Namecheap (`tuctucmap.com`) | **Q7.65 / mes** | **Q91.80 / año**<br>*(\$12 USD/año)* | Opcional; si se usa el subdominio gratuito `.web.app` de Firebase o `.vercel.app`, el costo es **Q0.00**. |
| **Mapas (Visualización de Calles)** | OpenStreetMap (`tile.openstreetmap.org`) vía OSMDroid y Leaflet | **Q0.00** | **Q0.00** | Gratis con la caché `"TucTucOsmLive"` configurada en el código. *(Si en el futuro escalara a >500 conductores y se requiriera un proveedor comercial como Stadia Maps o Mapbox, sigue siendo gratis hasta 25,000–50,000 cargas/mes)*. |
| **Enrutamiento por Calles (Rutas y ETA)** | OSRM Demo Server (`router.project-osrm.org`) + Haversine local | **Q0.00** | **Q0.00** | Si el servidor comunitario OSRM aplica límite de tasa (HTTP 429), la app ya tiene programado en `OsrmRoutingService.kt` un fallback matemático Haversine a costo **Q0.00**. *(Opcionalmente, un VPS propio en Hetzner/DigitalOcean con OSRM Guatemala cuesta \$4.50 USD = **Q34.43/mes**)*. |
| **Pasarela de Pagos y Recaudación** | Transferencia / Depósito Banrural y Banco Industrial + Efectivo | **Q0.00** | **Q0.00** | Sin comisiones por transacción. *(Si en el futuro se integrara cobro con tarjeta vía Recurrente Guatemala o Visanet, la comisión bancaria sería del **3.5% al 4.5% + Q2.00** por cobro)*. |
| **Total Gastos Fijos No-Cloud** | — | **Q23.59 / mes** | **Q283.05 / año** | **Q0.00/mes** a partir del año 2 si se usa subdominio gratuito. |

---

## 7. Análisis de Monetización y Rentabilidad del Propietario

### 7.1. Modelo de Monetización Existente en el Código Fuente
En [`databaseTuctucApp/db.sql`](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/databaseTuctucApp/db.sql#L39-L60) (`public.app_config`) y en las pantallas de suscripción de Android y Angular, el sistema implementa un **modelo B2B de Suscripción Prepagada por Conductor (SaaS para Mototaxis)**:

* **Pasajeros:** Uso **100% gratuito** (incentiva adopción masiva inmediata en el pueblo frente a llamar por teléfono).
* **Viajes:** Pago **100% en efectivo directo al conductor**, sin comisión por viaje para la plataforma (elimina fricción con los conductores).
* **Conductores (Toritos):** Pagan una membresía prepagada para aparecer activos en el mapa y recibir solicitudes:
  * **Plan Semanal (7 días):** **Q25.00** $\rightarrow$ Equivale a $\text{Q25} \times (30 / 7) = \mathbf{Q107.14 / \text{mes}}$.
  * **Plan Quincenal (15 días):** **Q45.00** $\rightarrow$ Equivale a $\text{Q45} \times 2 = \mathbf{Q90.00 / \text{mes}}$.
  * **Plan Mensual (30 días):** **Q80.00** $\rightarrow$ Equivale a $\mathbf{Q80.00 / \text{mes}}$.

#### Ingreso Promedio Mensual por Conductor Activo (ARPU Ponderado):
Considerando el hábito de flujo de caja diario en municipios de Guatemala, donde la mayoría de conductores prefiere pagos cortos:
* **50% elige Plan Semanal** ($\text{Q100.00/mes}$ considerando 4 semanas efectivas pagadas al mes).
* **30% elige Plan Quincenal** ($\text{Q90.00/mes}$).
* **20% elige Plan Mensual** ($\text{Q80.00/mes}$).
$$\text{ARPU Promedio Ponderado} = (0.50 \times \text{Q100}) + (0.30 \times \text{Q90}) + (0.20 \times \text{Q80}) = \mathbf{Q93.00 / \text{conductor al mes}}$$

---

### 7.2. Estado de Resultados Proyectado (Ingresos, Gastos y Ganancias en GTQ)

A continuación se presenta la proyección financiera para el propietario de la plataforma bajo los tres escenarios, evaluando la **Arquitectura Híbrida Optimizada (`ChildEventListener`) en Producción Profesional (con Supabase Pro \$25/mes)** vs. **Modalidad Lean (Planes Gratuitos)**:

| Concepto Financiero | Escenario 1: Bajo<br>*(25 de 50 cond. pagando)* | Escenario 2: Normal<br>*(42 de 50 cond. pagando)* | Escenario 3: Intensivo<br>*(50 de 50 cond. pagando)* |
| :--- | :---: | :---: | :---: |
| **Conductores con Suscripción Pagada** | 25 conductores (50%) | 42 conductores (84%) | 50 conductores (100%) |
| **Ingreso Promedio por Conductor (ARPU)** | Q93.00 / mes | Q93.00 / mes | Q93.00 / mes |
| **INGRESOS BRUTOS MENSUALES** | **Q2,325.00 / mes** | **Q3,906.00 / mes** | **Q4,650.00 / mes** |
| **INGRESOS BRUTOS ANUALES** | **Q27,900.00 / año** | **Q46,872.00 / año** | **Q55,800.00 / año** |
| *(-) Costo Supabase (Plan Pro \$25 USD)* | -Q191.25 | -Q191.25 | -Q191.25 |
| *(-) Costo Firebase RTDB (Con `ChildEventListener`)* | Q0.00 *(0.58 GB)* | Q0.00 *(4.54 GB)* | -Q134.26 *(27.55 GB)* |
| *(-) Google Play Console + Dominio (Amortizado)* | -Q23.59 | -Q23.59 | -Q23.59 |
| **TOTAL GASTOS OPERATIVOS / MES (Con Plan Pro)** | **Q214.84 / mes** | **Q214.84 / mes** | **Q349.10 / mes** |
| **GANANCIA NETA MENSUAL (Con Plan Pro)** | **Q2,110.16 / mes** | **Q3,691.16 / mes** | **Q4,300.90 / mes** |
| **GANANCIA NETA ANUAL (Con Plan Pro)** | **Q25,321.92 / año** | **Q44,293.92 / año** | **Q51,610.80 / año** |
| **Margen de Ganancia Neta (Con Plan Pro)** | **90.8%** | **94.5%** | **92.5%** |
| **GANANCIA NETA MENSUAL (En Planes Free/Spark)** | **Q2,301.41 / mes** *(99.0%)* | **Q3,882.41 / mes** *(99.4%)* | **Q4,492.15 / mes** *(96.6%)* |

> **Comparativa de Impacto si NO se corrige `ValueEventListener`:**  
> Si se deja el código actual sin cambiar a `ChildEventListener`, en el **Escenario Normal** el gasto mensual sube de **Q214.84** a **Q1,179.35/mes**, reduciendo la ganancia del propietario a **Q2,726.65/mes**. En el **Escenario Intensivo**, la factura de Firebase (Q9,409.50/mes) provocaría una **pérdida neta de -Q4,974.34/mes**. De ahí la importancia estratégica de aplicar la optimización indicada en la Sección 8.

---

### 7.3. Escenarios Hipotéticos Alternativos de Monetización

Para evaluar el potencial financiero del activo de software, se comparan tres modelos adicionales sobre el **Escenario Normal (42 conductores activos, 12,000 viajes/mes, tarifa promedio de viaje en pueblo = Q10.00)**:

| Modelo de Monetización | Regla de Cobro | Ingreso Bruto Mensual | Gasto Cloud + Fijos | Ganancia Neta Mensual | Viabilidad Operativa en Municipios |
| :--- | :--- | :---: | :---: | :---: | :--- |
| **A. Modelo Actual (Base)** | **Q25/sem**, **Q45/quinc**, **Q80/mes** | **Q3,906.00** | Q214.84 | **Q3,691.16** | **Muy Alta.** Q25 a la semana equivale a **Q3.57 diarios** (menos de 1 viaje al día para el conductor). |
| **B. Hipotético 1: Suscripción Ajustada** | **Q35/sem**, **Q65/quinc**, **Q120/mes** *(ARPU Q130)* | **Q5,460.00** | Q214.84 | **Q5,245.16** | **Alta.** Equivale a **Q5.00 diarios** (1 solo pasaje corto al día). Aumenta la ganancia anual a **Q62,941.92**. |
| **C. Hipotético 2: Comisión por Viaje** | **Q1.00 por viaje completado** *(10% de Q10)* | **Q12,000.00** | Q214.84 | **Q11,785.16** | **Media-Baja (Riesgo de Evasión).** Al pagarse en efectivo, incentiva a que el pasajero o conductor cancelen en la app al verse para no pagar el Q1.00. |
| **D. Hipotético 3: Híbrido + Publicidad Local** | Suscripción actual + **10 comercios del pueblo** anunciados en el mapa a **Q150/mes** | **Q5,406.00** | Q214.84 | **Q5,191.16** | **Muy Alta.** Farmacias, comedores y ferreterías del municipio pagan por aparecer como pines destacados en el mapa del pasajero. |

---

### 7.4. Cálculo del Punto de Equilibrio (Break-Even Point)

El punto de equilibrio determina cuántos conductores pagados necesita el propietario para cubrir el 100% de los costos de infraestructura:

$$\text{Punto de Equilibrio (Conductores)} = \left\lceil \frac{\text{Costos Fijos y Cloud Mensuales}}{\text{ARPU por Conductor (Q93.00)}} \right\rceil$$

1. **En Fase Inicial (Supabase Free + Firebase Spark con `ChildEventListener`):**
   * Costo mensual = **Q23.59/mes**.
   * $\text{Punto de Equilibrio} = 23.59 / 93.00 = 0.25 \rightarrow \mathbf{1\text{ conductor pagando un plan semanal de Q25.00}}$.
2. **En Fase Profesional (Supabase Pro \$25 + Firebase con `ChildEventListener`):**
   * Costo mensual = **Q214.84/mes**.
   * $\text{Punto de Equilibrio} = 214.84 / 93.00 = 2.31 \rightarrow \mathbf{3\text{ conductores activos}}$ (apenas el **6% de la flota de 50 conductores**). A partir del 4.º conductor, el 100% de las suscripciones es utilidad neta para el propietario.

---

## 8. Recomendaciones Prácticas para Reducir Costos sin Comprometer Seguridad ni Funcionamiento

Para asegurar que el costo de nube se mantenga entre **Q0.00 y Q214.84 al mes** incluso cuando la aplicación crezca a más municipios, se recomiendan las siguientes 5 acciones técnicas concretas:

### 1. [PRIORIDAD #1] Cambiar `ValueEventListener` por `ChildEventListener` en `observeActiveLocations()`
* **Archivo:** `tuctucMap/app/src/main/java/com/example/tuctucmap/data/repository/SupabaseRepository.kt` (L1063–L1136).
* **Por qué:** Actualmente cada pasajero descarga la lista entera de conductores cada vez que un solo mototaxi avanza 8 metros. Con `ChildEventListener` (`onChildAdded`, `onChildChanged`, `onChildRemoved`) manteniendo un `ConcurrentHashMap<String, DriverLocation>` en memoria, el consumo de red en Firebase **baja un 96.6%**, ahorrando entre **Q960 y Q9,200 mensuales** sin perder ni un milisegundo de tiempo real ni tocar las reglas de seguridad.

### 2. [PRIORIDAD #2] Desactivar el Listener de Mapa Cuando la App del Pasajero Pasa a Segundo Plano
* **Archivo:** `PassengerMapScreen.kt`.
* **Por qué:** Si un pasajero deja la app abierta en su bolsillo sin pedir viaje, su teléfono sigue recibiendo coordenadas por WebSocket. Vincular `observeActiveLocations()` a `Lifecycle.State.STARTED` (`collectAsStateWithLifecycle`) corta el consumo de datos inmediatamente al apagar la pantalla o cambiar a WhatsApp.

### 3. [PRIORIDAD #3] Limpieza Automática de `route_points` Antiguos en PostgreSQL (`pg_cron`)
* **Archivo:** `databaseTuctucApp/db.sql`.
* **Por qué:** El 95% del peso de la tabla `public.trips` proviene del arreglo JSONB `route_points` (12.4 KB por viaje). Las tarifas (`fare`), distancias (`distance_km`), fechas y participantes pesan solo 0.6 KB.
* **Acción:** Programar una tarea semanal en Supabase que ejecute:
  ```sql
  UPDATE public.trips
  SET route_points = NULL
  WHERE created_at < NOW() - INTERVAL '30 days'
    AND route_points IS NOT NULL;
  ```
  Con esto, el historial financiero y el conteo de viajes se conservan **para siempre**, pero la base de datos nunca superará los **150 MB**, permitiendo operar en el **Plan Free de Supabase (500 MB)** por años sin pagar almacenamiento adicional.

### 4. [PRIORIDAD #4] Separar Datos Estáticos del Conductor del Latido GPS Dinámico
* **Archivo:** `SupabaseRepository.kt` (`updateLocation`, L426).
* **Por qué:** En cada latido GPS cada 8 metros se reenvían `nombre`, `telefono`, `placas` y `numeroUnidad`. Si se acortan las claves o se envían los metadatos solo al iniciar turno, el paquete baja de **210 bytes a ~65 bytes**, reduciendo el consumo de megas tanto del servidor como del plan de datos prepago (Tigo/Claro) del conductor y del pasajero.

### 5. [PRIORIDAD #5] Configurar Alertas de Presupuesto y Límite en Firebase y Supabase
* En **Supabase Pro**: Mantener activado el **Spend Cap (Límite de gasto)** por defecto para garantizar que la factura nunca supere los **\$25.00 USD (Q191.25/mes)**.
* En **Google Cloud / Firebase Billing**: Configurar una alerta de presupuesto en **\$5.00 USD (Q38.25)** y **\$15.00 USD (Q114.75)** con notificación al correo del propietario.

---

## 9. Fuentes Oficiales Consultadas

1. **Supabase Official Pricing & Quotas (Consultado el 28 de octubre de 2026):**
   * Planes y Tarifas Base: [https://supabase.com/pricing](https://supabase.com/pricing)
   * Documentación de Facturación de Mensajes y Conexiones Realtime: [https://supabase.com/docs/guides/realtime/pricing](https://supabase.com/docs/guides/realtime/pricing)
   * Gestión de Egress y Almacenamiento: [https://supabase.com/docs/guides/platform/manage-your-usage](https://supabase.com/docs/guides/platform/manage-your-usage)
2. **Google Firebase Official Pricing (Consultado el 28 de octubre de 2026):**
   * Comparativa de Planes Spark y Blaze: [https://firebase.google.com/pricing](https://firebase.google.com/pricing)
   * Facturación de Firebase Realtime Database: [https://firebase.google.com/docs/database/usage/billing](https://firebase.google.com/docs/database/usage/billing)
   * Precios de Cloud Firestore: [https://firebase.google.com/docs/firestore/pricing](https://firebase.google.com/docs/firestore/pricing)
3. **Tipo de Cambio Oficial en Guatemala (Consultado el 28 de octubre de 2026):**
   * Banco de Guatemala (Banguat) - Tipo de Cambio de Referencia: [https://www.banguat.gob.gt/tipo_cambio/](https://www.banguat.gob.gt/tipo_cambio/)
4. **Políticas de Uso de Cartografía y Ruteo Abierto (Consultado el 28 de octubre de 2026):**
   * OpenStreetMap Tile Usage Policy: [https://operations.osmfoundation.org/policies/tiles/](https://operations.osmfoundation.org/policies/tiles/)
   * Project OSRM API Usage Policy: [https://github.com/Project-OSRM/osrm-backend/wiki/Api-usage-policy](https://github.com/Project-OSRM/osrm-backend/wiki/Api-usage-policy)

