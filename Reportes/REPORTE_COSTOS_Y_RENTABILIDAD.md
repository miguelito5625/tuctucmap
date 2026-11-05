# 💰 Reporte de Costos Operativos y Rentabilidad en Producción: TucTuc Map

**Proyecto:** TucTuc Map (`com.tuctucmap.app` — `versionCode = 13`, `versionName = "1.1.2"`)  
**Fecha de Estimación:** 4 de noviembre de 2026  
**Escenario Objetivo:** **50 Conductores activos** y **200 Pasajeros recurrentes**  
**Tasa de Cambio Referencial:** `Q 7.65 = US$ 1.00` *(Banco de Guatemala / promedio interbancario)*

---

## 1. Resumen Ejecutivo

Gracias a las decisiones arquitectónicas del proyecto —uso de cartografía abierta (`OSMDroid` con caché local `"TucTucOsmLive"`), enrutamiento gratuito (`OSRM` con *fallback* Haversine), alertas por voz nativas (`TextToSpeech` + `ForegroundService` sin servidores de Push), cobro de suscripciones vía transferencia bancaria/WhatsApp (0% de comisión de pasarela) y telemetría GPS adaptativa por deltas— **TucTuc Map tiene una estructura de costos en la nube extraordinariamente baja y márgenes de rentabilidad superiores al 96%**.

* **Con los planes gratuitos actuales (Supabase Free + Firebase Spark):** La plataforma puede operar con **50 conductores y 200 pasajeros a un costo de infraestructura de US\$ 0.00 / mes (Q 0.00)** en escenarios de uso bajo a normal, requiriendo únicamente corregir el `ValueEventListener` del mapa administrativo (`AdminFleetMapTab`) para no exceder la cuota mensual de 10 GB de descarga de Firebase.
* **En un entorno de Producción Comercial Robusto (Supabase Pro + Firebase Blaze con holgura):** El costo total de mantener la plataforma en producción es de **US\$ 28.08 / mes (Q 214.81 / mes)**, frente a ingresos por suscripciones de conductores de entre **Q 3,450.00 y Q 4,500.00 / mes (US\$ 450.98 – US\$ 588.24 / mes)**.
* **Punto de Equilibrio (*Break-Even*):** Basta con tener **3 conductores activos** pagando su suscripción mensual (`Q 80`) o semanal (`Q 25`) para cubrir el **100% de los gastos de servidores en producción**. Los ingresos de los otros 47 conductores representan ganancia neta.

---

## 2. Supuestos Técnicos y Operativos Utilizados

Todas las proyecciones se calculan a partir de las constantes reales programadas en el código fuente:

### 2.1. Parámetros de Usuarios y Operación
1. **Flota de Conductores:** **50 conductores** activos operando un promedio de **8 horas diarias**, **26 días al mes** (6 días por semana).
2. **Base de Pasajeros:** **200 pasajeros** recurrentes que abren el mapa un promedio de **3 minutos por sesión de búsqueda** y solicitan un total combinado de **600 viajes diarios** en toda la comunidad (**12 viajes/día por conductor** = **18,000 viajes/mes**).
3. **Administración:** **1 administrador** supervisando el panel nativo (`AdminMainScreen`) durante **1 hora diaria** (revisión de comprobantes de pago, métricas y mapa de flota).
4. **Tasa de Cambio:** `Q 7.65 = US$ 1.00`.

### 2.2. Parámetros Reales de Código (`LocationTrackingService.kt` y `SupabaseRepository.kt`)
1. **Telemetría GPS Adaptativa ([`LocationTrackingService.kt:61-65`](../tuctucMap/app/src/main/java/com/example/tuctucmap/service/LocationTrackingService.kt#L61-L65)):**
   * En movimiento (`>= 8.0 metros`): Envía un payload delta (`lat`, `lon`, `status`, `lastUpdated`) de **~135 bytes** cada ~12 segundos en promedio durante horas activas.
   * En espera / parada: Envía un *heartbeat* cada **45 segundos** (`45_000L` ms) o cada **15 segundos** en modo "En Pausa" (`15_000L` ms).
   * Sincronización de metadatos completos (`nombre`, `placa`, `telefono` — **~260 bytes**): Solo cada **180 segundos** (`METADATA_SYNC_INTERVAL_MS = 180_000L`).
   * **Promedio ponderado por conductor en turno de 8h:** ~1,200 escrituras GPS/día de ~165 bytes promedio.
2. **Escucha de Flota en Pasajeros ([`SupabaseRepository.kt:1237-1310`](../tuctucMap/app/src/main/java/com/example/tuctucmap/data/repository/SupabaseRepository.kt#L1237-L1310)):**
   * Utiliza `ChildEventListener` (descarga inicial de los conductores activos + deltas incrementales solo de los conductores que se mueven mientras el pasajero mira el mapa).
3. **Persistencia y Limpieza de Rutas en PostgreSQL ([`db.sql:331-361`](../databaseTuctucApp/db.sql#L331-L361)):**
   * Cada viaje completado guarda un registro en `public.trips` (~450 bytes de metadatos + ~12 KB de `route_points` JSONB).
   * El job automático `pg_cron` (`cleanup-old-trip-routes-daily`) vacía (`NULL`) la columna `route_points` de todos los viajes con más de **30 días de antigüedad** a las 03:00 AM UTC, manteniendo la base de datos compacta permanentemente.
4. **Precios Oficiales de Suscripción ([`AdminModels.kt:175-189`](../tuctucMap/app/src/main/java/com/example/tuctucmap/data/model/AdminModels.kt#L175-L189) y `db.sql`):**
   * **Plan Diario (referencial):** `Q 10.00` (US\$ 1.31)
   * **Plan Semanal (7 días):** `Q 25.00` (US\$ 3.27) — Equivalente a `Q 100.00 / mes`
   * **Plan Quincenal (15 días):** `Q 45.00` (US\$ 5.88) — Equivalente a `Q 90.00 / mes`
   * **Plan Mensual (30 días):** `Q 80.00` (US\$ 10.46) — Equivalente a `Q 80.00 / mes`

---

## 3. Estimación Técnica de Consumo de Infraestructura (50 Conductores / 200 Pasajeros)

### 3.1. Consumo Mensual en Firebase Realtime Database (Telemetría + Despacho `/trips`)

| Concepto de Tráfico Firebase RTDB | Cálculo Basado en la Arquitectura Real | Volumen Mensual |
| :--- | :--- | :---: |
| **1. Escrituras GPS de Conductores** | 50 conductores × 1,200 actualizaciones/día × 26 días × 165 B | **~257 MB / mes** *(Almacenamiento en vivo: < 25 KB)* |
| **2. Descarga de Mapa en Pasajeros (`ChildEventListener`)** | 200 pasajeros × 2 sesiones/día (3 min c/u) × 30 días × ~280 KB/sesión | **~3.36 GB / mes** |
| **3. Despacho de Viajes (`/trips`)** | 600 viajes/día × 30 días × ~15 conductores escuchando cambios de estado (~1.2 KB/viaje) | **~324 MB / mes** |
| **4. Mapa de Flota Admin (`AdminFleetMapTab`)** | *Con el código actual (`ValueEventListener`): ~150 MB por cada hora de uso = ~4.5 GB/mes.*<br>*Corrigiendo a `ChildEventListener`: ~9 MB/hora = ~270 MB/mes.* | **~4.50 GB / mes** *(actual)*<br>**~0.27 GB / mes** *(optimizado)* |
| **TOTAL DESCARGA MENSUAL (Egress)** | **Suma total de tráfico saliente desde Firebase RTDB** | **~8.18 GB / mes** *(actual)*<br>**~3.95 GB / mes** *(optimizado)* |
| **Conexiones Simultáneas Pico** | 50 conductores + ~35 pasajeros concurrentes en hora pico + 1 admin | **~86 conexiones** *(Límite Free: 100)* |

> **Conclusión sobre Firebase RTDB:** El almacenamiento en vivo es inferior a **0.1 MB** (el límite gratuito Spark es de **1 GB**). La descarga mensual es de **~8.18 GB/mes** (o **~3.95 GB/mes** corrigiendo `AdminFleetMapTab`), manteniéndose dentro de la capa gratuita de **10 GB/mes** del plan Spark. Si se activa el plan **Firebase Blaze (Pago por uso)** para eliminar el tope de 100 conexiones simultáneas (pasando a 200,000 conexiones), los primeros 10 GB siguen siendo gratuitos y cualquier GB adicional cuesta únicamente **US\$ 1.00 / GB (Q 7.65 / GB)**.

---

### 3.2. Consumo Mensual en Supabase (PostgreSQL + Auth + Realtime)

| Recurso en Supabase | Consumo Estimado (50 Conductores + 200 Pasajeros) | Límite Plan Free (US\$ 0) | Límite Plan Pro (US\$ 25/mes) |
| :--- | :---: | :---: | :---: |
| **Almacenamiento Base de Datos (`db.sql`)** | **~225 MB estables**<br>*(18,000 viajes/mes × 12 KB de ruta = 216 MB rotativos gracias a la purga de 30 días de `pg_cron` + ~9 MB de metadatos anuales)* | 500 MB (Suficiente) | 8 GB |
| **Usuarios Activos Mensuales (MAU Auth)** | **~255 MAU** *(50 conductores + 200 pasajeros + admins)* | 50,000 MAU | 100,000 MAU |
| **Ancho de Banda (Egress REST)** | **~1.8 GB / mes** *(con `getDriverTripHistory` optimizado)*<br>**~4.2 GB / mes** *(con el `SELECT *` actual en historial)* | 5 GB / mes | 250 GB / mes |
| **Mensajes Supabase Realtime** | **~1.5M / mes** *(actual sin filtro en `profiles`)*<br>**~45,000 / mes** *(con filtro `eq("id", uid)`)* | 2,000,000 / mes | 5,000,000 / mes |

---

## 4. Desglose Completo de Costos de Producción (USD y Quetzales)

A continuación se presentan los **3 escenarios de infraestructura** para mantener la aplicación en producción con 50 conductores y 200 pasajeros:

### Escenario 1: Etapa Inicial / Bootstrapping (Planes Gratuitos Optimizados)
Ideal para los primeros meses de operación mientras la concurrencia simultánea no supere 100 usuarios conectados al mismo segundo.

### Escenario 2: Producción Comercial Recomendada (Supabase Pro + Firebase Blaze) ⭐
Elimina cualquier riesgo de pausa de base de datos, incluye respaldos diarios automáticos (*Daily Backups*) de PostgreSQL, soporte para hasta 200,000 conexiones simultáneas en Firebase y holgura total de ancho de banda.

### Escenario 3: Uso Intensivo / Alta Demanda (Pico de 1,000 viajes/día + VPS OSRM opcional)
Incluye tráfico extra de Firebase (+5 GB sobre la cuota gratuita) y un servidor VPS ligero opcional para alojar un motor OSRM dedicado para Guatemala.

| Rubro / Servicio | Escenario 1: Inicial (Free Tier)<br>**USD / GTQ (Mensual)** | Escenario 2: Producción Recomendado ⭐<br>**USD / GTQ (Mensual)** | Escenario 3: Uso Intensivo<br>**USD / GTQ (Mensual)** |
| :--- | :---: | :---: | :---: |
| **1. Base de Datos y Auth (Supabase)** | US\$ 0.00 / **Q 0.00** *(Plan Free)* | US\$ 25.00 / **Q 191.25** *(Plan Pro)* | US\$ 25.00 / **Q 191.25** *(Plan Pro)* |
| **2. Telemetría en Vivo (Firebase RTDB)** | US\$ 0.00 / **Q 0.00** *(Spark < 10 GB)* | US\$ 1.00 / **Q 7.65** *(Blaze +1 GB margen)* | US\$ 5.00 / **Q 38.25** *(Blaze +5 GB extra)* |
| **3. Mapas y Mosaicos (`OSMDroid`)** | US\$ 0.00 / **Q 0.00** *(OpenStreetMap)* | US\$ 0.00 / **Q 0.00** *(Caché local)* | US\$ 0.00 / **Q 0.00** *(Caché local)* |
| **4. Ruteo Vial (`OSRM`)** | US\$ 0.00 / **Q 0.00** *(OSRM Público)* | US\$ 0.00 / **Q 0.00** *(OSRM + Haversine)* | US\$ 6.00 / **Q 45.90** *(VPS Docker OSRM)* |
| **5. Notificaciones y Alertas (`TTS`)** | US\$ 0.00 / **Q 0.00** *(Nativo Android)* | US\$ 0.00 / **Q 0.00** *(Nativo Android)* | US\$ 0.00 / **Q 0.00** *(Nativo Android)* |
| **6. Pasarela de Cobro (Bancos + WhatsApp)** | US\$ 0.00 / **Q 0.00** *(0% comisión)* | US\$ 0.00 / **Q 0.00** *(0% comisión)* | US\$ 0.00 / **Q 0.00** *(0% comisión)* |
| **7. Google Play Console (Amortizado)**<br>*(Pago único de US\$ 25.00 / Q 191.25)* | US\$ 2.08 / **Q 15.94** *(Año 1)* | US\$ 2.08 / **Q 15.94** *(Año 1)* | US\$ 2.08 / **Q 15.94** *(Año 1)* |
| **TOTAL COSTO MENSUAL** | **US\$ 2.08 / Q 15.94** | **US\$ 28.08 / Q 214.84** | **US\$ 38.08 / Q 291.34** |
| **TOTAL COSTO ANUAL (12 Meses)** | **US\$ 25.00 / Q 191.25** | **US\$ 337.00 / Q 2,578.05** | **US\$ 457.00 / Q 3,496.05** |
| **Costo Mensual por Conductor (50 cond.)** | **US\$ 0.04 / Q 0.32** | **US\$ 0.56 / Q 4.30** | **US\$ 0.76 / Q 5.83** |

---

## 5. Estimación de Ingresos y Análisis de Rentabilidad

### 5.1. Proyección de Ingresos Mensuales de la Plataforma (50 Conductores)
El modelo de negocio de TucTuc Map cobra una suscripción plana a los conductores (los pasajeros usan la aplicación de forma 100% gratuita). Evaluamos **3 modelos de adopción de planes** entre los 50 conductores según las tarifas configuradas en [`AdminModels.kt`](../tuctucMap/app/src/main/java/com/example/tuctucmap/data/model/AdminModels.kt#L180-L182):

1. **Ingresos Conservadores (100% en Plan Mensual con descuento — `Q 80.00 / mes`):**
   * 50 conductores × `Q 80.00` = **Q 4,000.00 / mes (US\$ 522.88 / mes)**.
   * *(Considerando un 10% de rotación/inactividad temporal = 45 conductores pagando: **Q 3,600.00 / mes** o **US\$ 470.59 / mes**).*
2. **Ingresos Esperados / Mix Realista (50% Plan Semanal `Q 25`, 30% Plan Quincenal `Q 45`, 20% Plan Mensual `Q 80`):**
   * 25 conductores semanales (`25 × 4 × Q 25 = Q 2,500`) + 15 conductores quincenales (`15 × 2 × Q 45 = Q 1,350`) + 10 conductores mensuales (`10 × 1 × Q 80 = Q 800`).
   * **Ingreso Bruto Mensual:** **Q 4,650.00 / mes (US\$ 607.84 / mes)**.
3. **Ingresos Óptimos (Predominio de Plan Semanal `Q 25`):**
   * 50 conductores × 4 semanas × `Q 25.00` = **Q 5,000.00 / mes (US\$ 653.59 / mes)**.

---

### 5.2. Comparativa de Costos vs. Ingresos (¿Es Rentable?)

Tomando el **Escenario 2 de Costos (Producción Comercial Robusta: Q 214.84 / mes = US\$ 28.08 / mes)** frente a los tres escenarios de ingresos:

| Indicador Financiero | Escenario Conservador<br>*(45 cond. × Q 80)* | Escenario Esperado (Mix) ⭐<br>*(50 cond. Mix Sem/Quin/Men)* | Escenario Óptimo<br>*(50 cond. × Plan Semanal)* |
| :--- | :---: | :---: | :---: |
| **Ingresos Brutos Mensuales** | **Q 3,600.00** *(US\$ 470.59)* | **Q 4,650.00** *(US\$ 607.84)* | **Q 5,000.00** *(US\$ 653.59)* |
| **Costos de Producción Mensuales** | **- Q 214.84** *(- US\$ 28.08)* | **- Q 214.84** *(- US\$ 28.08)* | **- Q 214.84** *(- US\$ 28.08)* |
| **GANANCIA NETA MENSUAL** | 🟢 **Q 3,385.16 (US\$ 442.50)** | 🟢 **Q 4,435.16 (US\$ 579.76)** | 🟢 **Q 4,785.16 (US\$ 625.51)** |
| **GANANCIA NETA ANUAL (12 Meses)** | 🟢 **Q 40,621.95 (US\$ 5,310.06)** | 🟢 **Q 53,221.95 (US\$ 6,957.12)** | 🟢 **Q 57,421.95 (US\$ 7,506.14)** |
| **Margen de Rentabilidad Neta (%)** | **94.03%** | **95.38%** | **95.70%** |
| **Multiplicador de Cobertura (Ingresos / Costos)** | Cubre los gastos **16.7 veces** | Cubre los gastos **21.6 veces** | Cubre los gastos **23.3 veces** |
| **Punto de Equilibrio (*Break-Even*)** | **2.7 conductores** (`3 conductores`) | **2.3 conductores** (`3 conductores`) | **2.2 conductores** (`3 conductores`) |

### ✅ Veredicto de Rentabilidad
**Sí, la aplicación es altamente rentable.**
1. **Para el propietario de la plataforma:** Las ganancias cubren más de **21 veces** los costos de infraestructura en producción comercial. Incluso si la flota cayera de 50 a solo **3 conductores activos**, la aplicación seguiría cubriendo el 100% de sus costos de servidor (`Supabase Pro` + `Firebase Blaze`).
2. **Para el conductor de TucTuc (Economía Unitaria):** Pagar `Q 25.00` a la semana (`Q 3.57 / día`) o `Q 80.00` al mes (`Q 2.67 / día`) equivale a **menos de 1 viaje corto al día** (tarifa mínima de un tuctuc: `Q 5.00 – Q 10.00`). Además, el ahorro de combustible por dejar de dar vueltas vacías buscando pasaje supera ampliamente el costo de la suscripción semanal.

---

## 6. Recomendaciones Prácticas para Reducir Costos y Mejorar la Rentabilidad

### 6.1. Recomendaciones Técnicas para Reducir Costos de Nube (Ahorro Directo)
1. **Reemplazar `ValueEventListener` en `AdminFleetMapTab.kt` (Ahorra ~4.2 GB/mes en Firebase):**
   * Actualmente, abrir la pestaña "Mapa" en el panel de administrador descarga los 50 conductores completos cada vez que uno solo se mueve 8 metros. Cambiarlo para que consuma `repository.observeActiveLocations()` (`ChildEventListener`) reduce el consumo de ancho de banda administrativo en un **94%** y garantiza que la plataforma pueda permanecer mucho más tiempo dentro de los **10 GB gratuitos** de Firebase.
2. **Añadir Filtro `eq("id", uid)` en Canales Realtime de `profiles` (`SupabaseRepository.kt`):**
   * Reduce los mensajes de WebSocket de Supabase Realtime de ~1.5 millones/mes a menos de 45,000/mes, evitando sobrepasar la cuota gratuita de 2 millones de mensajes incluso si la app crece a **500 conductores y 2,000 pasajeros**.
3. **Usar `tripColumnsWithoutRoute` en `getDriverTripHistory()` (`SupabaseRepository.kt:821`):**
   * Evita descargar las polilíneas GPS (`route_points`) cuando el conductor consulta su historial de viajes, ahorrando **~2.4 GB/mes** de transferencia (*Egress*) en Supabase.
4. **Activar Firebase Blaze con Alerta de Presupuesto de US\$ 5.00:**
   * No es necesario pagar `Supabase Pro` (US\$ 25/mes) desde el día 1 si se realiza al menos una actividad semanal en el proyecto, pero **sí se recomienda activar Firebase Blaze** (cuyo costo real con 50 conductores será de **US\$ 0.00 a US\$ 1.00 / mes**) únicamente para desbloquear el límite duro de 100 conexiones simultáneas del plan gratuito Spark.

### 6.2. Recomendaciones Comerciales para Incrementar los Ingresos y Retención
1. **Automatizar Recordatorios Preventivos por WhatsApp (2 días antes del vencimiento):**
   * Aprovechar el filtro `"Por vencer"` en `AdminSubscriptionsTab` para enviar con un toque el recordatorio de renovación 48 horas antes del vencimiento, evitando días muertos de recaudación entre cortes semanales.
2. **Incentivar el Plan Quincenal (`Q 45`) y Mensual (`Q 80`):**
   * Aunque el plan semanal (`Q 25 × 4 = Q 100`) genera mayor ingreso bruto nominal, en la práctica los conductores semanales suelen dejar pasar 1 o 2 días sin recargar cuando descansan, reduciendo la recaudación efectiva y aumentando la carga operativa del administrador validando boletas bancarias cada 7 días. El plan quincenal/mensual asegura flujo de caja anticipado y reduce en un **50% a 75%** el tiempo administrativo de validación.
3. **Alianzas Locales / Comercios Destacados en el Mapa (Ingreso Adicional B2B):**
   * Con 200 pasajeros activos abriendo el mapa diariamente en el municipio, se puede ofrecer a entre 5 y 10 negocios locales (farmacias, restaurantes, ferreterías) aparecer como un **Pin Comercial Destacado** en el mapa de pasajeros con un botón de *"Pedir TucTuc hacia este negocio"* por **Q 100.00 – Q 150.00 / mes por comercio**, generando entre **Q 500.00 y Q 1,500.00 adicionales al mes** sin costo extra de servidor.

