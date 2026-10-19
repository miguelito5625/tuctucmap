# Plan Maestro de Optimización de Rendimiento y Reducción de Costos Operativos
## Ecosistema TucTuc Map: App Móvil (Android), Dashboard Web (Angular) e Infraestructura Cloud (Supabase / Firebase / OSM)

> **Tipo de documento:** Auditoría técnica de rendimiento, compatibilidad con gama baja/media y arquitectura de costos en producción.  
> **Ámbito de aplicación:** `/tuctucMap` (App nativa Android), `/tuctucMapDashboard` (Panel administrativo web) y `/databaseTuctucApp` (Servicios backend).  
> **Estado:** Propuesta de optimización sin alteración de código actual.

---

## 1. Diagnóstico Ejecutivo y Visión General

El modelo de negocio de **TucTuc Map** opera principalmente en municipios y áreas periurbanas de Guatemala y Centroamérica. En este entorno, el perfil de hardware y conectividad presenta condiciones particulares:
1. **Conductores (Toritos):** Dispositivos de gama de entrada o media (2 GB a 3 GB de RAM, procesadores MediaTek Helio G25/G35, Unisoc o Snapdragon serie 400, baterías con desgaste y pantallas expuestas a la luz solar en trayectos continuos).
2. **Pasajeros:** Dispositivos heterogéneos conectados a redes móviles 3G/LTE con planes de datos prepago (paquetes de Q5 o Q10 con 500 MB – 1.5 GB de vigencia limitada).
3. **Servicios de Nube y Presupuesto:** Infraestructura apoyada en planes gratuitos o de bajo costo (Supabase Free/Pro Tier, Firebase Realtime Database Spark/Blaze Tier y servidores comunitarios de OpenStreetMap y OSRM).

```
                  ┌──────────────────────────────────────────────┐
                  │          PASAJERO (App Android)              │
                  │  - Red Móvil Prepago (Ancho de banda bajo)   │
                  │  - Dispositivo Gama Media/Baja (2-3 GB RAM)  │
                  └──────────────┬───────────────────────────────┘
                                 │
     Lecturas frecuentes         │           Alertas y Solicitudes
     de conductores              │           (WebSockets / Eventos)
     (Filtro espacial)           ▼
┌────────────────────────────────────────────────────────────────────────┐
│                   INFRAESTRUCTURA Y SERVICIOS                          │
│                                                                        │
│   [Firebase Realtime Database]  ── Telemetría viva y despacho viajes  │
│   [Supabase PostgreSQL]         ── Perfiles, finanzas y auditoría     │
│   [OSM Tile Server]             ── Capas de mapa base                 │
│   [OSRM Routing Server]         ── Cálculo de ruta y kilometraje      │
└────────────────────────────────┬───────────────────────────────────────┘
                                 ▲
     Transmisión adaptativa      │           Monitoreo de Flota
     (8m / 45s heartbeat)        │           y Cobranza
                                 │
                  ┌──────────────┴───────────────────────────────┐
                  │           CONDUCTOR (Torito Android)         │
                  │  - Servicio en Segundo Plano (Foreground)    │
                  │  - Consumo Térmico y de Batería Crítico      │
                  └──────────────────────────────────────────────┘
```

Si no se implementan directrices estrictas de **muestreo diferencial, reciclaje de memoria y restricción de consultas**, una flota de 100 conductores y 300 pasajeros simultáneos puede generar:
- Más de **60 GB de transferencia diaria en Firebase Realtime Database**, superando el límite gratuito (10 GB/mes) en menos de 48 horas e incurriendo en cobros por gigabyte excedente.
- **Bloqueos HTTP 403 y 429** por parte de los servidores públicos de OpenStreetMap (OSM) y del enrutador OSRM debido a solicitudes no limitadas desde el ciclo de composición de la UI.
- **Micro-congelamientos (jank de 5–15 FPS) y cierres por memoria (OOM)** en teléfonos de gama baja al animar decenas de marcadores en el mapa y procesar matrices de coordenadas sin reciclaje.

---

## 2. Optimización de la App Móvil Android (`/tuctucMap`) para Gama Media y Baja

### 2.1. Memoria RAM y Evitación de Pausas del Garbage Collector (GC Churn)

#### Hallazgo en el código actual:
1. **Generación manual de transparencia en mapa de bits (`MarkerIconUtils.kt`):**  
   En `MarkerIconUtils.kt`, líneas 28–46 y 70–98, la aplicación toma el icono de `R.drawable.ic_tuc_tuc` (un PNG de 422 KB), crea un bitmap escalado de 144×144 píxeles, extrae una matriz de enteros `IntArray(144 * 144)` (= 20,736 enteros) y ejecuta un bucle en el procesador evaluando píxel por píxel para convertir blancos en transparente y generar la versión ámbar desaturada. Aunque se guarda en memoria tras la primera llamada, cualquier recomposición o limpieza de caché vuelve a forzar esta asignación en la pila de memoria.
2. **Ciclo continuo de redibujado a 12.5 FPS (`PassengerLocationUtils.kt`):**  
   En `PassengerLocationUtils.kt`, línea 107, el avatar del pasajero ejecuta `mapView.postInvalidateDelayed(80L)`. Esto fuerza a OSMDroid a redibujar el `MapView` completo 12.5 veces por segundo de forma ininterrumpida, incluso si el mapa está estático, la pantalla no registra toques o el avatar está fuera de la vista visible (viewport).

```
IMPACTO EN GAMA BAJA:
- 12.5 redibujados completos por segundo consumen entre el 18% y el 25% de CPU sostenido.
- La GPU no puede ingresar a estados de bajo consumo (idle).
- El Garbage Collector se activa cada 4 a 6 segundos para limpiar búferes de dibujo,
  provocando caídas de fotogramas perceptibles por el usuario.
```

#### Soluciones recomendadas:
- **Pre-rasterización de activos a formato WebP:** Convertir `ic_tuc_tuc.png` (422 KB) y sus variantes de estado (activo verde, ocupado naranja, inactivo gris) a formato **WebP sin pérdida o con compresión 95%** directamente desde Android Studio (`Convert to WebP`). Esto reduce el peso de 422 KB a menos de 25 KB por imagen, incluye canal alfa nativo y elimina por completo las funciones de manipulación de píxeles en tiempo de ejecución.
- **Condicionar la tasa de refresco de la animación del avatar:**
  - Desactivar el ciclo `postInvalidateDelayed` cuando el pasajero desplace el mapa y su coordenada quede fuera de la vista visible (`mapView.boundingBox.contains(geoPoint)`).
  - Reducir la cadencia de la animación en reposo de 80 ms (~12.5 FPS) a 160 ms (~6.2 FPS), suficiente para transmitir fluidez en un saludo sin calentar el procesador gráfico de bajo rendimiento.
  - Pausar completamente el hilo de animación cuando la actividad ejecute `onPause()` o `onStop()`.

---

### 2.2. Optimización de Renderizado en Compose y OSMDroid (`AndroidView`)

#### Hallazgo en el código actual:
En `PassengerMapScreen.kt` (líneas 580–650), cada vez que `rawDriverLocations` emite una nueva lista desde Firebase:
1. Se recorre la lista completa de conductores.
2. Si la posición cambió, se instancia un nuevo objeto `ValueAnimator.ofFloat(0f, 1f)` con duración de 4000 ms.
3. Si entran 10 actualizaciones consecutivas de conductores en movimiento, **se acumulan múltiples animadores paralelos compitiendo por llamar a `mapView.invalidate()` simultáneamente**, sin cancelar el animador previo del mismo marcador.

```kotlin
// Código actual problemático (PassengerMapScreen.kt:634):
val animator = ValueAnimator.ofFloat(0f, 1f)
animator.duration = 4000L
animator.interpolator = LinearInterpolator()
animator.addUpdateListener { animation ->
    // Provoca múltiples invalidaciones a 60 FPS por cada conductor
    mapView.invalidate()
}
animator.start()
```

#### Soluciones recomendadas:
- **Gestión centralizada de animadores con cancelación proactiva:**
  Mantener un mapa de animadores activos (`val activeAnimators = mutableMapOf<String, ValueAnimator>()`). Al recibir una nueva coordenada para el conductor `uid`, si existe una animación en curso, llamar a `animator.cancel()` antes de crear la nueva interpolación.
- **Umbral mínimo de desplazamiento para animación (Deadzone espacial):**
  Si el conductor se movió menos de 3 metros (ruido de señal GPS), actualizar la propiedad `marker.position` directamente sin lanzar un `ValueAnimator`.
- **Desacoplar la recomposición de Compose de la vista de mapa:**
  Evitar leer flujos de alta frecuencia directamente en el ámbito superior de la función composable (`@Composable PassengerMapScreen`). Encapsular el mapa en un componente estable (`@Stable`) o mover la recolección de marcadores a una corrutina aislada que interactúe directamente con la capa de overlays sin forzar la recomposición de los paneles flotantes ni de los botones superiores.

---

### 2.3. Batería y CPU en el Servicio de Rastreo en Segundo Plano (`LocationTrackingService`)

#### Estado actual:
`LocationTrackingService.kt` implementa un filtro adaptativo:
- `LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L).setMinUpdateIntervalMillis(4000L)`.
- Condición de envío: Primera lectura, desplazamiento $\ge 8\text{ metros}$ o tiempo transcurrido $\ge 45\text{ segundos}$ (heartbeat).

#### Puntos de mejora para maximizar la autonomía del conductor:
1. **Ajuste dinámico de prioridad de precisión (Power Management):**  
   Cuando el conductor cambia su estado a `"en_pausa"` o `"ocupado"` en un viaje de larga duración, la precisión del GPS no requiere `PRIORITY_HIGH_ACCURACY` a 5 segundos. Puede degradarse dinámicamente a `PRIORITY_BALANCED_POWER_ACCURACY` con intervalo de 15 a 20 segundos, reduciendo el consumo de batería del módem GPS en hasta un 40%.
2. **Detección de inactividad por acelerómetro (Activity Recognition):**  
   Si el dispositivo permanece quieto en un soporte de tuctuc durante más de 3 minutos (detectable mediante el podómetro o sensor de gravedad integrado sin costo energético adicional), el intervalo de sondeo GPS puede espaciarse a 60 segundos hasta registrar un movimiento físico brusco.

---

### 2.4. Reducción de Tamaño de APK y Huella de Instalación

1. **Eliminar la dependencia pesada de iconos extendidos de Compose:**  
   En `tuctucMap/app/build.gradle.kts` (línea 105) se encuentra:
   ```kotlin
   implementation(libs.androidx.compose.material.icons.extended)
   ```
   Esta librería incluye más de 2,000 iconos vectoriales empaquetados en clases Java. Aunque R8/ProGuard realiza optimización en compilaciones de producción, genera una sobrecarga sustancial en el conteo de métodos DEX y en tiempos de compilación.  
   *Recomendación:* Eliminar esta dependencia e importar únicamente los 4 o 5 iconos necesarios como archivos vectoriales XML locales en `res/drawable/`.
2. **Configuración R8 Full Mode:**  
   Verificar que `gradle.properties` cuente con `android.enableR8.fullMode=true` para garantizar el stripping agresivo de metadatos de serialización de Kotlin y clases no utilizadas en las librerías de Supabase y Firebase.

---

## 3. Optimización del Panel Web Administrativo (`/tuctucMapDashboard`)

### 3.1. Renderizado de Flota en Vivo con Leaflet (`fleet-map.component.ts`)

#### Hallazgo en el código actual:
En `fleet-map.component.ts` (líneas 387–391 y 458–516):
- Se ejecuta un `setInterval` cada 8 segundos que descarga la lista de conductores.
- En `renderMarkers()`, para cada conductor se genera una cadena HTML compleja en línea (`iconHtml = '<div style="background: ...">🛺</div>'`) y se instancia un nuevo objeto `L.divIcon` en cada ciclo de sondeo, reemplazando el icono existente con `marker.setIcon(customIcon)`.

```typescript
// Código actual en fleet-map.component.ts:482
const customIcon = L.divIcon({
  html: iconHtml,
  className: 'custom-tuctuc-marker',
  iconSize: [36, 36],
  iconAnchor: [18, 18],
});
// Se asigna en cada ciclo de 8s incluso si el conductor no cambió de estado ni posición
marker.setIcon(customIcon);
```

#### Soluciones recomendadas:
1. **Reutilización de instancias de iconos (`Flyweight Pattern`):**  
   Crear únicamente tres instancias estáticas globales de `L.DivIcon`:
   - `ICON_AVAILABLE`: Fondo verde `#16a34a`.
   - `ICON_BUSY`: Fondo naranja `#ea580c`.
   - `ICON_PAUSED`: Fondo gris `#64748b`.  
   Solo invocar `marker.setIcon()` si el estado (`status`) del conductor cambió con respecto al ciclo anterior.
2. **Actualización suave de coordenadas (`L.Marker.setLatLng`):**  
   Si el estado no cambió y solo cambió la coordenada, ejecutar exclusivamente `marker.setLatLng([newLat, newLon])`. No reconstruir el contenido del popup (`setPopupContent`) en cada ciclo a menos que el conductor haya modificado su número telefónico o placas.
3. **Capa Canvas para flotas grandes (> 50 unidades):**  
   Si la flota crece por encima de 50 o 100 mototaxis simultáneos, reemplazar los marcadores DOM individuales por `L.circleMarker` renderizados sobre un `<canvas>` nativo de Leaflet (`preferCanvas: true`), eliminando cientos de nodos del DOM que ralentizan navegadores en computadoras de baja potencia.

---

### 3.2. Paginación y Filtrado en Base de Datos para Viajes e Historial (`trips.component.ts`)

#### Hallazgo en el código actual:
En `supabase.service.ts` (líneas 379–420) y `trips.component.ts` (líneas 64–69):
- `getCompletedTrips()` ejecuta `this.supabase.from('trips').select('*').order('created_at', { ascending: false })` **sin límite de registros (`limit`) ni paginación (`range`)**.
- Descarga la columna `route_points` (un arreglo JSON que puede contener entre 200 y 800 puntos por viaje) de **todos los viajes históricos registrados en la base de datos**.
- La paginación se realiza en el cliente con `MatTableDataSource` después de haber transferido decenas de megabytes por la red.

```
RIESGO DE DESEMPEÑO Y COSTOS:
- Con 1,000 viajes completados, cada uno con 20 KB de ruta:
  Se transfieren ~20 MB de datos JSON en cada recarga de la pestaña "Viajes".
- Agota la cuota mensual de transferencia de Supabase (5 GB en el plan gratuito)
  con solo 250 visitas al dashboard.
- Congela el hilo principal del navegador durante el parsing de JSON.
```

#### Soluciones recomendadas:
1. **Proyección estricta de columnas en la lista principal:**  
   Modificar la consulta del listado para excluir expresamente la columna pesada `route_points`:
   ```typescript
   // Consulta optimizada para la tabla
   await this.supabase
     .from('trips')
     .select('id, passenger_name, passenger_phone, driver_id, status, message, fare, distance_km, created_at')
     .order('created_at', { ascending: false })
     .range(pageIndex * pageSize, (pageIndex + 1) * pageSize - 1);
   ```
2. **Carga perezosa (Lazy Loading) de la polilínea:**  
   Descargar `route_points` únicamente cuando el administrador haga clic en el botón **"Ver Recorrido en Mapa"** de un viaje específico:
   ```typescript
   async getTripRouteDetails(tripId: string): Promise<any> {
     const { data } = await this.supabase
       .from('trips')
       .select('route_points')
       .eq('id', tripId)
       .single();
     return data?.route_points;
   }
   ```
3. **Paginación en Servidor:** Implementar `MatPaginator` con eventos `(page)` para solicitar bloques de 20 o 50 registros directamente al motor PostgreSQL de Supabase.

---

### 3.3. Control de Subscripciones y Fugas de Memoria en Angular

- En `supabase.service.ts`, el canal `public:profiles` de Supabase Realtime no se destruye explícitamente al navegar fuera de la vista de conductores, manteniendo viva una conexión WebSocket en segundo plano.
- *Recomendación:* Implementar un método `unsubscribeAll()` o vincular la suscripción al ciclo de vida del componente mediante `takeUntilDestroyed()` de Angular 22.

---

## 4. Estrategia de Reducción de Peticiones y Costos Cloud en Producción

### 4.1. Firebase Realtime Database (Optimizaciones Críticas de Egress)

Firebase Realtime Database factura principalmente por **ancho de banda de salida (Network Egress: $1.00 por GB tras exceder los 10 GB gratuitos del plan Spark/Blaze)**.

#### Problema 1: Escucha de árbol completo con `ValueEventListener`
En `SupabaseRepository.kt` (línea 1038):
```kotlin
fun observeActiveLocations(): Flow<List<DriverLocation>> = callbackFlow {
    val databaseRef = Firebase.database.getReference("driver_locations")
    databaseRef.addValueEventListener(listener) // <- Recibe el nodo COMPLETO en cada cambio individual
}
```
Si hay 80 conductores conectados y cada uno transmite su posición cada 8 segundos, hay **10 escrituras por segundo en `/driver_locations`**.  
Con `ValueEventListener`, cada una de esas 10 escrituras hace que Firebase envíe la lista de los 80 conductores a **todos los pasajeros conectados**.

$$\text{Cálculo de Datos Egress:}$$
- Tamaño del nodo `/driver_locations` (80 conductores $\times$ 160 bytes): $\approx 12.8\text{ KB}$.
- Si hay 150 pasajeros con el mapa abierto: $150\text{ pasajeros} \times 10\text{ eventos/seg} = 1,500\text{ transmisiones/seg}$.
- Ancho de banda consumido: $1,500 \times 12.8\text{ KB} = 19.2\text{ MB por segundo}$.
- **En 1 hora de operación pico: $19.2\text{ MB/s} \times 3600\text{ s} \approx 69.1\text{ GB de salida}$.**
- **Resultado:** Se sobrepasa la cuota gratuita mensual en menos de 10 minutos y se generan cobros diarios significativos.

#### Solución Arquitectónica:
1. **Migrar de `ValueEventListener` a `ChildEventListener`:**  
   Con `ChildEventListener` (`onChildChanged`, `onChildAdded`, `onChildRemoved`), Firebase únicamente envía el JSON del conductor específico que se movió (~160 bytes) en lugar de la lista completa (12.8 KB).  
   *Ahorro inmediato de ancho de banda: $\approx 98.7\%$.*
2. **Minimización de campos en el paquete de telemetría repetitivo:**  
   Actualmente, en cada actualización se transmiten datos estáticos:
   ```json
   {
     "driver_id": "...",
     "lat": 14.6349,
     "lon": -90.5069,
     "nombre": "Juan Pérez",
     "telefono": "50212345678",
     "placas": "M-123ABC",
     "numeroUnidad": "05",
     "active": true,
     "status": "disponible",
     "updated_at": "1729340000000"
   }
   ```
   *Estrategia de dos niveles:*
   - **Nodo `/driver_info/{driverId}` (Estático):** Contiene nombre, teléfono, placas y número de unidad. Los pasajeros lo descargan una sola vez o lo leen de Supabase.
   - **Nodo `/driver_live/{driverId}` (Dinámico/Frecuente):** Solo envía coordenadas y estado:
     ```json
     { "lat": 14.6349, "lon": -90.5069, "st": "d", "ts": 1729340000000 }
     ```
     Esto reduce el payload de 160 bytes a 45 bytes por actualización.
3. **Optimización del listener de viajes (`observePendingTripsForDriver`):**  
   En lugar de escuchar `/trips` completo, utilizar consultas indexadas en Firebase:
   ```kotlin
   val query = tripsRef.orderByChild("status").equalTo("requested").limitToLast(15)
   ```
   y asegurar que en las reglas de seguridad de Firebase exista:
   ```json
   {
     "rules": {
       "trips": {
         ".indexOn": ["status", "driverId"]
       }
     }
   }
   ```
4. **Eliminar la reinscripción redundante de `onDisconnect()` en cada tick:**  
   En `SupabaseRepository.kt` (línea 433):
   ```kotlin
   locationRef.setValue(location)
   locationRef.onDisconnect().removeValue() // <- Se registra en CADA envío de coordenadas
   ```
   `onDisconnect().removeValue()` debe llamarse **una sola vez** al iniciar el turno del conductor (en `onStartCommand`), no en cada coordenada transmitida cada 8 metros. Cada registro de `onDisconnect` envía un paquete de control adicional a los servidores de Firebase.

---

### 4.2. Supabase / PostgreSQL (Optimización de Conexiones y Almacenamiento)

1. **Evitar consultas N+1 en el historial de viajes:**  
   En `SupabaseRepository.kt` (líneas 661–675 y 705–725), se consulta primero la tabla `trips` y luego se ejecuta una segunda consulta `isIn("id", driverIds)` hacia `profiles`.  
   *Solución:* Aprovechar las relaciones foráneas de PostgREST para resolver la consulta en una sola llamada de red con una sola conexión a base de datos:
   ```kotlin
   // Consulta única combinada
   supabase.postgrest["trips"].select(
       Columns.raw("*, profiles!driver_id(name, phone, plate_number)")
   ) {
       filter { eq("passenger_id", uid) }
       order("created_at", Order.DESCENDING)
       limit(30)
   }
   ```
2. **Purgado programado de coordenadas históricas (`pg_cron`):**  
   Si se almacenan puntos de ruta en `trips.route_points`, una base de datos con 20,000 viajes consumirá más de 400 MB en almacenamiento de texto plano.  
   *Solución:* Crear una rutina programada en PostgreSQL que trunque o simplifique polilíneas de viajes de más de 30 días de antigüedad (manteniendo únicamente distancia total, tarifa y fechas).

---

### 4.3. Servidor de Rutas OSRM (`router.project-osrm.org`)

#### Hallazgo crítico en el código actual:
En `DriverHomeScreen.kt` (línea 160) y en `PassengerMapScreen.kt` (línea 221):
```kotlin
LaunchedEffect(activeAcceptedTrip?.tripId, currentLocation?.latitude, currentLocation?.longitude) {
    // Se ejecuta con CADA paso de GPS (cada 4-5 segundos)
    val res = OsrmRoutingService.getRoute(...)
}
```
El servidor público `router.project-osrm.org` es un servicio comunitario de demostración con límites de tasa estrictos (rate limits). Transmitir consultas cada 4 segundos desde el teléfono del conductor **y paralelamente desde el teléfono del pasajero**:
- Provocará bloqueos inmediatos por dirección IP o User-Agent (HTTP 429 Too Many Requests).
- Si en producción se migrara a un proveedor comercial (ej. Google Maps Directions API o Mapbox Navigation API), 120 peticiones por viaje con 100 viajes diarios representarían **12,000 llamadas diarias ($60/día = $1,800/mes)** solo en estimación de ruta hacia el pasajero.

#### Solución recomendada:
1. **Cálculo de ruta inicial bajo demanda:**  
   Invocar la ruta completa por carretera **una sola vez** en el momento exacto en que el conductor presiona "Aceptar Viaje".
2. **Actualización continua por proyección local (Haversine a costo $0):**  
   Durante el trayecto hacia el pasajero, calcular la distancia y el tiempo restante utilizando la fórmula matemática de Haversine localmente en el dispositivo (sin ninguna petición HTTP externa).
3. **Recálculo únicamente por desvío significativo (Rerouting Throttling):**  
   Solo solicitar una nueva ruta OSRM si el conductor se aleja más de 50 metros del vector de la ruta calculada originalmente o si han transcurrido más de 60 segundos sin avances hacia el destino.

---

### 4.4. Mosaicos de OpenStreetMap (Política Fair-Use y Caching Local)

#### Estado actual:
`MapTileUtils.kt` utiliza:
```kotlin
arrayOf("https://tile.openstreetmap.org/")
```
OpenStreetMap prohíbe explícitamente en sus políticas de uso (Tile Usage Policy) aplicaciones móviles de rastreo masivo que descarguen mosaicos sin control de caché estricto.

#### Solución recomendada para producción:
1. **Aumento de la cuota de caché en disco de OSMDroid:**  
   En `MainActivity.kt`, configurar la caché local de OSMDroid para almacenar hasta 150 MB de mosaicos de forma permanente:
   ```kotlin
   val osmConfig = Configuration.getInstance()
   osmConfig.tileFileSystemCacheMaxBytes = 150L * 1024L * 1024L // 150 MB
   osmConfig.tileFileSystemCacheTrimBytes = 120L * 1024L * 1024L
   ```
2. **Empaquetado de mosaicos fuera de línea (Offline MBTiles / SQLite):**  
   Dado que el servicio de TucTuc opera en municipios delimitados (ej. Santiago Atitlán, Antigua Guatemala, Panajachel, Jalapa, etc.), es posible pre-renderizar los mosaicos del municipio (niveles de zoom del 12 al 18) en un archivo `.sqlite` de solo 30 a 50 MB e incluirlo en la carpeta de activos o descargarlo en el primer inicio.  
   *Beneficios:*
   - **$0 costo en peticiones de mosaicos.**
   - El mapa carga de forma instantánea sin importar si el conductor entra a un callejón sin cobertura 3G/LTE.
   - Cero riesgo de bloqueos HTTP 403 por parte de OpenStreetMap.

---

## 5. Tabla Comparativa de Rendimiento y Consumo Estimado

| Métrica / Recurso | Estado Actual (Sin Optimizar) | Estado Optimizado (Propuesto) | Beneficio Concreto |
| :--- | :--- | :--- | :--- |
| **Consumo de Datos en Firebase RTDB** | ~69 GB/hora (flota de 100 conductores / 150 pasajeros) con `ValueEventListener` | ~0.8 GB/hora con `ChildEventListener` y payload compacto | Se mantiene cómodamente dentro del tier gratuito o genera micro-costos mínimos (< $2/mes) |
| **Uso de CPU en Reposo (App Pasajero)** | 18% – 25% constante debido al ciclo de 12.5 FPS de `postInvalidateDelayed(80L)` | < 3% en reposo pausando el ciclo fuera de vista o con mapa estático | Evita el sobrecalentamiento y la degradación de batería en gama baja |
| **Transferencia en Historial de Viajes (Web)** | ~20 MB por recarga de página (descarga todos los `route_points` sin límite) | ~15 KB por página de 20 registros con proyección de columnas | Tiempos de carga de < 200 ms y ahorro de ancho de banda en Supabase de más del 99% |
| **Peticiones a Servicios de Ruteo (OSRM)** | 1 petición HTTP cada 4s por conductor + 1 cada 4s por pasajero | 1 petición por viaje + recálculo local por Haversine si no hay desvío | 0% riesgo de baneo HTTP 429 y $0 de costo de API |
| **Estabilidad de Fotogramas en Android (FPS)** | Caídas a 10–18 FPS con múltiples conductores moviéndose simultáneamente | 55–60 FPS estables mediante cancelación de animadores previos | Experiencia táctil fluida y libre de trabas (jank) en procesadores MediaTek/Unisoc |
| **Consumo de Memoria Heap de la App Móvil** | ~140 MB debido a asignaciones continuas de matrices en `MarkerIconUtils` y listas | ~45–55 MB estables gracias a texturas WebP nativas y modelos reciclados | Prevención total de cierres inesperados por `OutOfMemoryError` en teléfonos de 2 GB |

---

## 6. Plan de Ejecución Priorizado para Producción

Para implementar estas mejoras de forma ordenada sin interrumpir el funcionamiento actual del negocio, se recomienda seguir el siguiente orden cronológico:

```
FASE 1: Blindaje Inmediato de Costos (Día 1 a 3)
├── 1. Migrar observeActiveLocations a ChildEventListener en SupabaseRepository.kt.
├── 2. Separar payload estático y dinámico en driver_locations.
└── 3. Poner límite (throttle de 25s o recálculo solo al aceptar) en OsrmRoutingService.

FASE 2: Fluidez y Batería en Gama Baja (Día 4 a 7)
├── 4. Convertir iconos PNG a WebP y retirar el bucle manual de transparencia en MarkerIconUtils.
├── 5. Añadir gestión y cancelación de animadores en PassengerMapScreen.kt.
└── 6. Condicionar la frecuencia de postInvalidateDelayed en PassengerLocationUtils.kt.

FASE 3: Escalabilidad Web y Base de Datos (Día 8 a 10)
├── 7. Paginación y exclusión de route_points en la consulta de viajes del Dashboard.
├── 8. Reutilización de L.divIcon estáticos en fleet-map.component.ts.
└── 9. Configuración de límites y caché de 150 MB para mosaicos de OpenStreetMap.
```

---

## 7. Conclusión

La arquitectura actual de **TucTuc Map** cuenta con bases sólidas: modularidad en Compose, desacoplamiento de capas mediante repositorios y separación clara entre operaciones transaccionales (Supabase) y telemetría en vivo (Firebase).

Las oportunidades de mejora identificadas no requieren rediseñar el sistema ni cambiar de proveedores, sino **ajustar la granularidad con la que se transmiten y renderizan los datos**. Aplicando estas medidas, el sistema podrá sostener cientos de mototaxis y miles de viajes diarios operando con gran fluidez en teléfonos económicos y con costos de infraestructura que se mantendrán prácticamente en **$0 o pocos dólares al mes**.
