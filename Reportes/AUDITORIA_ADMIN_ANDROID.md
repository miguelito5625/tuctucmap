# Auditoría Funcional, Arquitectónica y de Mejoras: Módulo Administrador Android (`TucTuc Map`)

**Fecha de auditoría:** 7 de noviembre de 2026  
**Versión auditada:** `TucTuc Map Admin v1.2.0` (`com.tuctucmap.app`)  
**Dispositivo de pruebas (ADB):** Emulador Android Studio `emulator-5554` (Resolución `720x1280`, Android SDK)  
**Cuenta utilizada:** `admin@tuctucmap.com` (UUID: `11111111-1111-4111-8111-111111111111`, Rol: `admin`)  
**Alcance:** Exclusivamente aplicación móvil nativa Android (`tuctucMap/`) y su integración con Supabase PostgreSQL (`databaseTuctucApp/`) y Firebase RTDB.

---

## 1. Inventario de Funcionalidades del Administrador en Android

El módulo de administrador en Android se articula a través de un contenedor principal con barra de navegación inferior de 4 pestañas ([`AdminMainScreen.kt`](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/admin/AdminMainScreen.kt)), un menú lateral deslizable ([`AdminDrawerSheet`](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/tuctucMap/app/src/main/java/com/example/tuctucmap/ui/components/AppDrawer.kt#L716-L923)) y 3 pantallas secundarias de navegación directa.

### 1.1. Pestañas Principales (`BottomNavigationBar` en `AdminMainScreen`)

| # | Pestaña / Pantalla | Archivo Fuente | Funcionalidades Disponibles |
| :--- | :--- | :--- | :--- |
| **Tab 0** | **Conductores & Flota** | [`AdminDriversTab.kt`](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/admin/AdminDriversTab.kt) | • **4 tarjetas KPI horizontales:** `Ingresos Mes`, `Total Recaudado`, `Flota Activa`, `Por vencer (7d)`.<br>• **Buscador en tiempo real:** Por nombre, placa o número de unidad.<br>• **Chips de filtrado:** `Todos (N)`, `Pendientes (N)`, `Activos`, `Por Vencer`, `Vencidos`.<br>• **Tarjetas de conductor (`DriverAdminCard`):** Avatar, nombre, unidad, placa, teléfono, badge de aprobación (`Aprobado`, `Pendiente`, `Rechazado`) y vigencia en días.<br>• **Acciones rápidas en tarjeta:** Botones `Aprobar` / `Rechazar` (solo si está `pendiente`), `Recordar Pago` (abre WhatsApp con plantilla de cobro) y `Renovar` (abre hoja modal de renovación manual).<br>• **Menú contextual (`⋮`):** `Contactar WhatsApp`, `Renovar Membresía` y `Eliminar Conductor` (invoca RPC `delete_user_by_admin`). |
| **Tab 1** | **Suscripciones & Pagos** | [`AdminSubscriptionsTab.kt`](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/admin/AdminSubscriptionsTab.kt) | • **Sub-pestaña 1 (`Solicitudes`):** Bandeja de solicitudes de pago enviadas por conductores (`subscription_payment_requests` con `status = 'pendiente'`). Permite `Aprobar` (RPC `approve_subscription_payment_request`), `Rechazar` (con motivo opcional vía RPC `reject_subscription_payment_request`) o contactar por WhatsApp.<br>• **Sub-pestaña 2 (`Historial Global`):** Lista combinada de pagos históricos (`driver_subscriptions`) y solicitudes resueltas. Incluye filtros de período (`Hoy`, `3 días`, `7 días`, `Rango personalizado`), filtros de estado (`Todos`, `Aprobados`, `Rechazados`) y botón `Exportar Excel` (`.xls` SpreadsheetML vía [`ExcelExporter.kt`](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/tuctucMap/app/src/main/java/com/example/tuctucmap/ui/util/ExcelExporter.kt)). |
| **Tab 2** | **Mapa de Flota en Vivo** | [`AdminFleetMapTab.kt`](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/admin/AdminFleetMapTab.kt) | • **Mapa OSMDroid en tiempo real:** Escucha el nodo `driver_locations` en Firebase RTDB y cruza metadatos con `profiles` de Supabase.<br>• **Píldora superior de estado:** Contadores en vivo `N Disp.`, `N Ocupados`, `N Total` (filtrando conductores con reporte en los últimos 3 minutos).<br>• **FAB `Ver Lista`:** Abre un `ModalBottomSheet` (`Flota en Vivo (N)`) que lista conductores conectados y permite centrar la cámara del mapa al tocar uno. |
| **Tab 3** | **Configuración Comercial** | [`AdminBusinessConfigTab.kt`](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/admin/AdminBusinessConfigTab.kt) | • **WhatsApp Administrativo:** Campo numérico con código de país y botón `Probar` (abre `wa.me`).<br>• **Cuentas Bancarias para Depósito:** `Banco 1 (Nombre/Tipo y Cuenta)`, `Banco 2 (Nombre/Tipo y Cuenta)` y `Nombre del Titular`.<br>• **Precios de Suscripción (Q):** Campos numéricos para planes `Diario`, `Semanal`, `Quincenal` y `Mensual`.<br>• **Radio de Búsqueda / Geocerca (Km):** Campo numérico de cobertura.<br>• **Botón `Guardar Configuración Comercial`:** Actualiza la fila `id = 'default'` en `public.app_config`. |

### 1.2. Pantallas Secundarias y Acciones del Menú Lateral (`AdminDrawerSheet`)

| Opción en Drawer | Destino / Archivo Fuente | Funcionalidades Disponibles |
| :--- | :--- | :--- |
| **Auditoría de Viajes** | [`AdminTripsScreen.kt`](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/admin/AdminTripsScreen.kt) | • Buscador por pasajero, conductor o destino.<br>• Filtros de período (`Hoy`, `3 días`, `5 días`, `7 días`, `Rango de fechas`) y filtro multi-conductor (`DriverFilterDialog`).<br>• Resumen de viajes filtrados y suma de tarifas (`TOTAL RECAUDADO`).<br>• Tarjetas de viaje con distinción `Registrado` vs `Invitado`, botón de llamada al pasajero y botón `Ver Ruta GPS` (`TripRouteModal` con trazado OSMDroid de inicio, recogida, fin y waypoints).<br>• Exportación a Excel (`.xls`) desde el botón superior de compartir. |
| **Directorio de Pasajeros** | [`AdminPassengersScreen.kt`](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/admin/AdminPassengersScreen.kt) | • Consume el RPC `get_admin_passengers_summary()`.<br>• Buscador por nombre, teléfono o correo.<br>• Tarjetas de pasajero con cantidad de viajes solicitados, fecha de registro y botones rápidos de `WhatsApp` y `Llamar`. |
| **Reportes de Errores** | [`AdminCrashLogsScreen.kt`](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/admin/AdminCrashLogsScreen.kt) | • **Tab 0 (`Incidentes`):** KPIs (`Total`, `Pendientes`, `Resueltos`), buscador, chips de estado y tarjetas de crash que abren un `ModalBottomSheet` con stacktrace completo, copiar reporte, eliminar registro o marcar como resuelto con nota de solución.<br>• **Tab 1 (`Métricas & Gráficas`):** Barra de tasa de resolución (%) y rankings de modelos de dispositivo, versiones de Android, versiones de app y tipos de excepción. |
| **Compartir aplicación** | `AppDrawer.kt` (`shareApp`) | • Abre el chooser nativo de Android con el enlace de descarga en Google Play Store. |
| **Ver Mapa como Pasajero** | `Navigation.kt` (`passenger_map`) | • Lleva al administrador a `PassengerMapScreen` sin cerrar su sesión. |
| **Cerrar Sesión** | `SupabaseRepository.logout()` | • Cierra la sesión en Supabase Auth y redirige a `passenger_map` en modo invitado. |

---

## 2. Resultados de las Pruebas Realizadas en Emulador vía ADB (`emulator-5554`)

Todas las pruebas se ejecutaron interactuando directamente sobre el emulador `emulator-5554` mediante `adb shell input`, `uiautomator dump` y `screencap`, respetando estrictamente la restricción de **no alterar datos reales en Supabase**.

| Flujo / Pantalla Probada | Acciones Ejecutadas vía ADB | Resultado Observado | Estado |
| :--- | :--- | :--- | :--- |
| **1. Inicio de Sesión Admin** | Desde modo invitado (`PassengerMapScreen`) -> Drawer -> `Acceder a mi Cuenta` -> ingreso de `admin@tuctucmap.com` / `123456` -> `Iniciar Sesión`. | Autenticación exitosa en Supabase Auth; el listener global detecta `role = 'admin'` y redirige automáticamente a `AdminMainScreen`. **Nota UX:** El administrador no tiene pantalla de login propia; entra por la pantalla de pasajeros (*"Inicia sesión para pedir un tuctuc"*) y al cerrar el teclado el `ScrollView` queda desplazado. | ⚠️ Funcional con fricción UX |
| **2. Tab 0: Conductores & Flota** | Inspección de KPIs, buscador, chips de filtro y tarjetas de los 2 conductores (`Carlos Morales` y `Mario Roberto`). | Se muestran 2 conductores aprobados con `"60 días restantes"`, `Ingresos Mes: Q 25` y `Total Recaudado: Q 75`. Las tarjetas KPI 3 y 4 y los chips de filtro derechos quedan cortados en pantalla de `720px` de ancho. | ⚠️ Problemas visuales / truncamiento |
| **3. Menú Contextual (`⋮`) del Conductor** | Tap en `Opciones` (`⋮`) sobre `Carlos Morales (Conductor 1)`. | Despliega `Contactar WhatsApp`, `Renovar Membresía` y `Eliminar Conductor`. Se observan **iconos duplicados** (icono vectorial + emoji) en cada opción. | 🐛 Bug visual confirmado |
| **4. Modal `Renovar Membresía`** | Tap en `Renovar Membresía` -> inspección del `ModalBottomSheet` -> cierre con `Back` (sin confirmar escritura). | El sheet se abre a media altura (`y=640..1280`), cortando el campo `Monto cobrado (Q)` y ocultando los métodos de pago, notas y botones `Cancelar`/`Confirmar`. Además, **solo ofrece 3 planes** (`Semanal`, `Quincenal`, `Mensual`), omitiendo el plan `Diario` (`Q10.0`). | 🐛 Bug funcional y de UI |
| **5. Diálogo `Eliminar Conductor`** | Tap en `⋮` -> `Eliminar Conductor` -> inspección de `AlertDialog` -> cierre con `Back` (sin eliminar). | Muestra advertencia clara de eliminación permanente para `Carlos Morales (Conductor 1) (Torito #01 • M-101ABC)` con botones `Cancelar` y `Eliminar`. | ✅ Correcto |
| **6. Tab 1: Pagos (`Solicitudes`)** | Tap en pestaña `Pagos` -> inspección de sub-pestaña `Solicitudes`. | Muestra `Bandeja de Solicitudes (Al día)` y estado vacío `"¡Bandeja al día! No hay solicitudes de renovación pendientes por atender"`, consistente con `0` filas en `subscription_payment_requests`. | ✅ Correcto |
| **7. Tab 1: Pagos (`Historial Global`)** | Tap en `Historial Global` -> prueba de filtro por defecto `Hoy` y cambio a filtro `7 días`. | • Con filtro por defecto `Hoy`, muestra `0 Registros (Hoy)` y el botón `"Ver últimos 7 días"` aparece **recortado y oculto** detrás de la barra de navegación inferior.<br>• Al cambiar a `7 días`, muestra `1 Registros (7 días)` (`Carlos Morales`, `Q 25.0`, `Activo`, `05/11/2026 al 12/11/2026`), pero **oculta** las otras 4 suscripciones históricas de octubre 2026 salvo que el usuario use un rango manual. Tampoco muestra el método de pago ni las notas en la tarjeta. | 🐛 Bugs de UI y filtros deficientes |
| **8. Tab 2: Mapa de Flota en Vivo** | Tap en pestaña `Mapa` -> inspección de mapa OSMDroid y píldora superior -> tap en FAB `Ver Lista`. | Mapa renderiza correctamente Ciudad de Guatemala con contador `0 Disp. • 0 Ocupados • 0 Total`. El FAB abre la hoja `Flota en Vivo (0)` indicando `"No hay conductores activos en los últimos 3 minutos"`. | ✅ Correcto |
| **9. Tab 3: Configuración Comercial** | Tap en pestaña `Config` -> inspección y scroll completo del formulario (sin pulsar `Guardar`). | Carga en vivo los valores de `public.app_config`: WhatsApp `50255801894`, `Banrural (Monetaria)`, `Banco Industrial (Ahorro)`, titular `Administración TucTuc Map`, precios `10.0`, `25.0`, `45.0`, `80.0` y radio `5.0 km`. Faltan controles en UI para `currency_symbol` y `osrm_server_url`. | ⚠️ Incompleto respecto a BD |
| **10. Menú Lateral (`AdminDrawerSheet`)** | Tap en icono hamburguesa (`Menú`) -> scroll vertical completo del drawer. | Muestra encabezado `Panel Admin` (`admin@tuctucmap.com`) y 9 opciones. **No existe ninguna opción de Perfil del Administrador ni Cambio de Contraseña.** El pie de versión (`v1.2.0`) oculta un crash intencional si se toca 5 veces. | ❌ Falta gestión de perfil/clave admin |
| **11. Pantalla `Auditoría de Viajes`** | Tap en `Auditoría de Viajes` -> inspección de lista y resumen -> tap en `Ver Ruta GPS` (`TripRouteModal`). | Lista los 2 viajes de hoy (`Q 20` c/u, `TOTAL RECAUDADO: Q 40.00`). La hora se muestra en **UTC crudo** (`22:49` en vez de `16:49` local). En `TripRouteModal`, el teléfono del pasajero invitado se corta a `"📞 55..."`. | 🐛 Bug de zona horaria y truncamiento |
| **12. Pantalla `Directorio de Pasajeros`** | Tap en `Directorio de Pasajeros` -> inspección de tarjetas de `Maria Lopez` y `Juan Gomez`. | Muestra los 2 pasajeros registrados y su conteo de viajes (`1` y `0`). **No muestra el correo electrónico** en la tarjeta (aunque el buscador dice buscar por correo) y los botones circulares de `WhatsApp` y `Llamar` **se traslapan visualmente**. | 🐛 Bug visual de solapamiento |
| **13. Pantalla `Reportes de Errores`** | Tap en `Reportes de Errores` -> inspección de `Incidentes (0)` y `Métricas & Gráficas`. | Ambas pestañas cargan correctamente sin errores (`0` incidentes en `public.app_crash_logs`, tasa de resolución `100.0%`). El chip `Solo Resueltos (0)` se corta en el borde derecho. | ✅ Funcional |

### 2.1. Operaciones No Ejecutadas por Restricción de Integridad de Datos
De acuerdo con las restricciones de la auditoría (*"No ejecutes operaciones que alteren información real"*), las siguientes acciones de escritura se verificaron mediante inspección de sus modales/diálogos en el emulador y auditoría estática de su código y RPCs en Supabase, sin disparar la mutación final:
1. **Confirmar renovación manual de membresía (`RenewSubscriptionBottomSheetContent` -> `Confirmar`):** Alteraría `profiles.subscription_end_date` e insertaría una fila en `driver_subscriptions`.
2. **Aprobar / Rechazar solicitud de pago (`AdminSubscriptionsTab`):** No habían registros pendientes en `subscription_payment_requests` (`0` filas en vivo), por lo que se auditó el código de `PendingPaymentRequestCard` y los RPCs `approve_subscription_payment_request` / `reject_subscription_payment_request`.
3. **Aprobar / Rechazar conductor pendiente (`AdminDriversTab`):** Los 2 conductores en BD ya tenían `approval_status = 'aprobado'`. Se auditó el flujo en `AdminRepository.approveDriver` / `rejectDriver` y los triggers de `db.sql`.
4. **Confirmar eliminación de conductor (`Eliminar Conductor` -> `Eliminar`):** Borraría permanentemente la cuenta en `auth.users` y `public.profiles` vía `delete_user_by_admin`.
5. **Guardar Configuración Comercial (`AdminBusinessConfigTab`):** Sobrescribiría `public.app_config`.
6. **Resolver o eliminar un Crash Log (`AdminCrashLogsScreen`):** `public.app_crash_logs` tiene `0` registros en vivo; se auditó `AdminCrashLogsScreen.kt` y `SupabaseRepository.kt`.

---

## 3. Errores Funcionales, Bugs Lógicos y Comportamientos Inesperados

### 3.1. Errores Críticos y de Lógica de Negocio

1. **Bug Crítico en Aprobación de Conductores Nuevos (Conductor aprobado queda sin período de prueba ni suscripción):**
   - **Causa en BD ([`db.sql:L205-225`](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/databaseTuctucApp/db.sql#L205-L225)):** Al registrarse un conductor nuevo, el trigger `BEFORE INSERT` (`trg_profile_insert_security`) fuerza `NEW.approval_status := 'pendiente'` y `NEW.subscription_end_date := NULL`.
   - **Efecto en Trigger Trial ([`db.sql:L234-254`](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/databaseTuctucApp/db.sql#L234-L254)):** El trigger `AFTER INSERT` (`trg_create_initial_driver_trial`) exige `IF NEW.role = 'conductor' AND NEW.subscription_end_date IS NOT NULL`, condición que **jamás se cumple** en un registro real porque el trigger `BEFORE INSERT` acaba de anular `subscription_end_date`.
   - **Efecto en App Android ([`AdminRepository.kt:L71-82`](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/tuctucMap/app/src/main/java/com/example/tuctucmap/data/repository/AdminRepository.kt#L71-L82)):** Cuando el administrador pulsa **`Aprobar`** en `AdminDriversTab`, `approveDriver(driverId)` únicamente ejecuta `UPDATE profiles SET approval_status = 'aprobado'`, **sin asignar `subscription_end_date = NOW() + 7 days`** ni insertar el registro `'trial'` en `driver_subscriptions`. El conductor recién aprobado pasa inmediatamente a estado `"Sin suscripción"` (bloqueado para operar) a menos que el admin le cargue un pago manual.

2. **Inconsistencia y Ruptura del Plan `'diario'` entre Configuración, Renovación Manual y Base de Datos:**
   - En [`AdminBusinessConfigTab.kt:L196-205`](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/admin/AdminBusinessConfigTab.kt#L196-L205), el administrador puede configurar el precio del plan **Diario** (`price_daily = 10.00`).
   - En [`AdminDriversTab.kt:L780-784`](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/admin/AdminDriversTab.kt#L780-L784), el modal `Renovar Membresía` solo incluye `listOf("semanal" to "Semanal\n(7d)", "quincenal" to "Quincenal\n(15d)", "mensual" to "Mensual\n(30d)")`, impidiendo renovar por día.
   - En [`db.sql:L35-36`](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/databaseTuctucApp/db.sql#L35-L36) y [`db.sql:L136`](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/databaseTuctucApp/db.sql#L136), las restricciones `CHECK` de `driver_subscriptions.plan_type` y `subscription_payment_requests.plan_type` solo permiten `('trial', 'semanal', 'quincenal', 'mensual', 'personalizado')`. Si se enviara `'diario'`, PostgreSQL lanzaría un error de violación de constraint, y el RPC `approve_subscription_payment_request` ([`db.sql:L358-367`](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/databaseTuctucApp/db.sql#L358-L367)) tampoco contempla `'diario'` en su bloque `CASE`.

3. **Renovación Manual No Atómica desde el Cliente (`renewDriverSubscriptionManual`):**
   - En [`AdminRepository.kt:L117-178`](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/tuctucMap/app/src/main/java/com/example/tuctucmap/data/repository/AdminRepository.kt#L117-L178), la renovación manual ejecuta **dos llamadas HTTP REST separadas** desde el teléfono: primero actualiza `profiles.subscription_end_date` y luego inserta en `driver_subscriptions`.
   - Si la conexión móvil falla tras la primera llamada o falla el `INSERT` en `driver_subscriptions`, el conductor recibe días de suscripción gratis sin que quede registro contable en el historial financiero. De hecho, en la base de datos actual se observa esta desincronización: `conductor1@gmail.com` y `conductor2@gmail.com` tienen `profiles.subscription_end_date = 2027-01-06` (`60 días restantes`), mientras que en `driver_subscriptions` sus últimas vigencias terminan el `2026-11-12` y `2026-10-24` respectivamente.

4. **Auditoría de Viajes (`getCompletedTrips`) No Filtra por Estado y Muestra Horas en UTC:**
   - En [`AdminRepository.kt:L451-482`](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/tuctucMap/app/src/main/java/com/example/tuctucmap/data/repository/AdminRepository.kt#L451-L482), la función se llama `getCompletedTrips`, pero la consulta a `public.trips` **no incluye `eq("status", "completed")`**, ni el modelo [`AdminTripRecord`](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/tuctucMap/app/src/main/java/com/example/tuctucmap/data/model/AdminModels.kt#L156-L185) mapea la columna `status`. Cualquier viaje `cancelled` o `rejected` aparecería mezclado como viaje válido con tarifa `Q --`.
   - En [`AdminTripsScreen.kt:L1318-1324`](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/admin/AdminTripsScreen.kt#L1318-L1324), `formatIsoDateTime` recorta el texto ISO en vez de parsear `Instant`/`OffsetDateTime` a la zona horaria local (`America/Guatemala`, UTC-6), mostrando `07/11/2026 22:49` para un viaje ocurrido a las `16:49` hora local.

5. **Sobrescritura Silenciosa del Monto en `approve_subscription_payment_request`:**
   - El RPC `approve_subscription_payment_request` ([`db.sql:L358-367`](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/databaseTuctucApp/db.sql#L358-L367)) ignora el monto (`v_req.amount`) reportado en la solicitud cuando el plan es `semanal`, `quincenal` o `mensual`, reemplazándolo forzosamente por el precio de lista actual de `app_config`. Si el administrador acordó un descuento o promoción, o si las tarifas cambiaron después de que el conductor depositó, el registro contable en `driver_subscriptions.amount_paid` quedará alterado.

6. **Crash Intencional Oculto en el Drawer de Producción (`DrawerEasterEggVersionText`):**
   - En [`AppDrawer.kt:L926-963`](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/tuctucMap/app/src/main/java/com/example/tuctucmap/ui/components/AppDrawer.kt#L926-L963), pulsar 5 veces sobre el texto `"TucTuc Map Admin • v1.2.0"` lanza un `RuntimeException("Prueba de Telemetría TucTuc Map - Crash intencional")` que cierra abruptamente la app en producción.

### 3.2. Problemas de Interfaz (UI/UX) Confirmados en Emulador

1. **Botón `"Ver últimos 7 días"` recortado por la barra inferior en `AdminSubscriptionsTab`:** En la sub-pestaña `Historial Global`, cuando el filtro por defecto (`Hoy`) no tiene registros, la tarjeta de estado vacío se extiende hasta `y=1072` y el botón de acción rápida `"Ver últimos 7 días"` queda cortado e ilegible bajo el `NavigationBar`.
2. **Hoja modal `Renovar Membresía` abierta a media altura:** En `AdminDriversTab`, el `ModalBottomSheet` no utiliza `rememberModalBottomSheetState(skipPartiallyExpanded = true)`, ocultando los selectores de método de pago (`Efectivo`, `Transferencia`, `Depósito Bancario`), el campo de referencia y los botones de confirmación.
3. **Iconos duplicados en menú contextual de conductores (`⋮`):** En `AdminDriversTab`, los 3 `DropdownMenuItem` muestran simultáneamente un `Icon` vectorial de Material y un emoji (`💬`, `💳`, `🗑️`) en el texto.
4. **Solapamiento visual de botones en `Directorio de Pasajeros`:** En `AdminPassengersScreen`, los botones circulares de `WhatsApp` (verde) y `Llamar` (azul) dentro de `PassengerCard` se superponen entre sí (`bounds="[496,560][576,656]"` y `[576,560][672,656]`).
5. **Truncamiento de tarjetas KPI, chips y textos en pantallas estándar (`720px`):**
   - En `AdminDriversTab`, la 3ra tarjeta KPI (`Flota Activa`) se corta a `"Flo... 2 Tuc..."` y la 4ta (`Por vencer (7d)`) queda fuera de pantalla sin indicador de scroll.
   - En `TripRouteModal`, el teléfono del pasajero se trunca a `"📞 55..."`.
   - En `AdminPassengersScreen` y `AdminDriversTab`, los nombres se cortan (`"Carlos Morales (Cond..."`, `"Maria Lopez (Pasaj..."`).

---

## 4. Comparación entre la App Android y la Estructura de Supabase

Se auditó el esquema en vivo de Supabase (`information_schema.columns` y `information_schema.routines`) frente a los modelos ([`AdminModels.kt`](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/tuctucMap/app/src/main/java/com/example/tuctucmap/data/model/AdminModels.kt)) y repositorios ([`AdminRepository.kt`](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/tuctucMap/app/src/main/java/com/example/tuctucmap/data/repository/AdminRepository.kt)) de la app Android:

| Tabla / RPC en Supabase | Columnas / Capacidades en BD | Uso Actual en Admin Android | Brechas, Datos Desaprovechados o Inconsistencias |
| :--- | :--- | :--- | :--- |
| **`public.profiles`** | `id`, `name`, `phone`, `email`, `role`, `plate_number`, `unit_number`, `is_available`, `is_busy`, `approval_status`, `subscription_end_date`, `updated_at` | Leída en `getDrivers`, `getFleetLocations`, `get_admin_passengers_summary`; actualizada en `approveDriver`, `rejectDriver`, `renewDriverSubscriptionManual`. | • **No se puede editar desde Admin Android** el nombre, teléfono, placa (`plate_number`), unidad (`unit_number`) ni corregir manualmente `subscription_end_date` de un conductor.<br>• **No se puede suspender/bloquear** a un conductor ya aprobado (los botones `Aprobar`/`Rechazar` desaparecen cuando `approval_status != 'pendiente'`).<br>• En `AdminPassengersScreen`, `email` se descarga pero **no se muestra en la UI**.<br>• En la BD en vivo, los pasajeros tienen `approval_status = 'pendiente'` por el `DEFAULT` de la columna (`db.sql:L11`). |
| **`public.driver_subscriptions`** | `id`, `driver_id`, `plan_type`, `amount_paid`, `start_date`, `end_date`, `payment_method`, `notes`, `created_at` | Leída e insertada en `AdminRepository`. | • En `SubscriptionHistoryCard` (`AdminSubscriptionsTab`), **no se muestran** `payment_method` (`efectivo`, `transferencia`, `deposito_bancario`), `notes` ni `created_at`, aunque sí se descargan en `AdminSubscriptionRecord`.<br>• Falta columna `created_by` (UUID del admin que registró el pago manual) y `receipt_url` (comprobante fotográfico).<br>• El constraint `CHECK` de `plan_type` no incluye `'diario'`. |
| **`public.subscription_payment_requests`** | `id`, `driver_id`, `plan_type`, `amount`, `payment_method`, `reference_note`, `status`, `admin_note`, `reviewed_by`, `reviewed_at`, `created_at`, `updated_at` | Leída y gestionada en `AdminSubscriptionsTab` vía RPCs `approve_...` y `reject_...`. | • **No se aprovecha el parámetro `p_custom_end_date`** del RPC `approve_subscription_payment_request(p_request_id, p_admin_note, p_custom_end_date)` desde Android (`AdminRepository.kt:L313` nunca lo envía).<br>• En el `Historial Global`, las solicitudes aprobadas se ocultan (`filter { it.status == "rechazado" }`) para no duplicar con `driver_subscriptions`, pero al hacerlo **se pierde la trazabilidad** de quién la aprobó (`reviewed_by`), cuándo (`reviewed_at`) y la nota del admin (`admin_note`). |
| **`public.app_config`** | `id`, `admin_whatsapp`, `bank_1_name`, `bank_1_account`, `bank_2_name`, `bank_2_account`, `account_holder`, `price_daily`, `price_semanal`, `price_quincenal`, `price_mensual`, `currency_symbol`, `search_radius_km`, `osrm_server_url`, `updated_at` | Leída y actualizada en `AdminBusinessConfigTab`. | • **`osrm_server_url`** existe en la tabla (`db.sql:L59`), pero **no está mapeada** en `AppConfig` (`AdminModels.kt`) ni en `AdminBusinessConfigTab`.<br>• **`currency_symbol`** (`'Q'`) está en `AppConfig`, pero **no tiene campo de texto** en `AdminBusinessConfigTab`.<br>• `openWhatsAppReminder` (`AdminDriversTab.kt:L937`) solo envía en el mensaje de cobro los precios `Semanal` y `Mensual`, omitiendo `Diario` y `Quincenal`. |
| **`public.trips`** | `id`, `passenger_id`, `driver_id`, `pickup_lat/lng`, `destination_text`, `status`, `fare`, `distance_km`, `route_points`, `guest_name`, `guest_phone`, `created_at`, `completed_at`, `updated_at` | Leída en `AdminTripsScreen` (`getCompletedTrips` y `getTripRoutePoints`). | • `getCompletedTrips` **no filtra por `status = 'completed'`** ni muestra `status` ni `completed_at` en la tarjeta.<br>• `pickup_lat` y `pickup_lng` se descargan pero no se muestran como dirección o enlace a Google Maps en la tarjeta del viaje.<br>• El job `pg_cron` (`cleanup_old_trip_route_points`) borra `route_points` a los 30 días, pero la app no informa al admin cuando una ruta fue depurada por antigüedad. |
| **`public.app_crash_logs`** | `id`, `user_id`, `user_role`, `user_name`, `user_phone`, `app_version_name`, `app_version_code`, `os_version`, `device_model`, `screen_context`, `error_type`, `error_message`, `stack_trace`, `extra_metadata`, `resolved`, `resolved_at`, `resolved_by`, `solution`, `created_at` | Leída, actualizada y eliminada en `AdminCrashLogsScreen`. | • **`extra_metadata` (`JSONB`)**, **`resolved_at`** y **`resolved_by`** existen en Supabase (`db.sql:L682-685`), pero **no están incluidos** en el modelo `AdminCrashLog` (`AdminModels.kt:L212-239`) ni se muestran en el detalle del incidente. |
| **RPC `get_admin_financial_summary()`** | Retorna únicamente `total_revenue` y `month_revenue` consultando `driver_subscriptions`. | Llamado en `AdminRepository.getFinancialSummary`. | • Usa `CURRENT_TIMESTAMP` (UTC) en lugar de ` CURRENT_TIMESTAMP AT TIME ZONE 'America/Guatemala'`, por lo que los pagos realizados el último día del mes después de las 18:00 (hora Guatemala) se contabilizan en el mes siguiente.<br>• Solo devuelve 2 números brutos; no desglosa por mes histórico, tipo de plan, método de pago ni descuenta gastos. |

---

## 5. Evaluación Especial por Ejes de Auditoría

### 5.1. Eje 1: Gestión de Administradores y Cambio Seguro de Contraseña
* **Estado actual:** **Inexistente en el módulo Admin.** Mientras que los módulos de Pasajero ([`PassengerProfileScreen.kt`](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/tuctucMap/app/src/main/java/com/example/tuctucmap/ui/screens/passenger/PassengerProfileScreen.kt)) y Conductor (`DriverProfileScreen.kt`) sí cuentan con una pantalla `"Mi Perfil y Contraseña"` en su menú lateral, `AdminDrawerSheet` carece por completo de acceso a perfil o seguridad.
* **Deficiencia de seguridad en el cambio de contraseña actual:** El método [`SupabaseRepository.updatePassword(newPassword)`](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/tuctucMap/app/src/main/java/com/example/tuctucmap/data/repository/SupabaseRepository.kt) cambia la contraseña en Supabase Auth sin solicitar ni verificar la **contraseña actual** del usuario. Si el teléfono del administrador queda desbloqueado en manos de un tercero con sesión activa, cualquiera podría cambiar la clave maestra sin conocer la actual.
* **Ausencia de gestión multi-administrador:** No existe pantalla para editar el nombre/teléfono del administrador, consultar qué cuentas tienen `role = 'admin'` en `public.profiles`, promover o revocar permisos de administrador secundarios ni auditar qué administrador ejecutó cada aprobación o renovación.

### 5.2. Eje 2: Control de Conductores, Suscripciones y Solicitudes
* **Fortalezas:** Flujo ágil de cobro por WhatsApp con mensaje prellenado, semáforo visual claro de días restantes (`<= 5 días` en rojo, `> 5 días` en verde) y bandeja dedicada para solicitudes de renovación (`subscription_payment_requests`).
* **Debilidades críticas:**
  1. Un conductor nuevo aprobado con el botón `Aprobar` queda sin fecha de vencimiento (`Sin suscripción`) por el conflicto entre `trg_profile_insert_security`, `trg_create_initial_driver_trial` y `approveDriver()`.
  2. Un conductor ya aprobado **no puede ser suspendido temporalmente** (solo existe `Eliminar Conductor` de forma destructiva e irreversible).
  3. No se pueden editar datos operativos del conductor (cambio de unidad `#`, cambio de placa `M-...`, corrección de teléfono o nombre) ni ajustar manualmente su fecha de corte (`subscription_end_date`).
  4. Al tocar un conductor en `AdminDriversTab`, no existe una vista de **Expediente del Conductor** que muestre su historial individual de pagos, viajes realizados y solicitudes previas.

### 5.3. Eje 3: Control Financiero (Pagos, Pendientes e Historial de Transacciones)
* **Control de pagos realizados:** El administrador puede registrar pagos manuales (`Renovar Membresía`) y aprobar solicitudes, pero el **`Historial Global`** en `AdminSubscriptionsTab` es insuficiente para una auditoría contable seria:
  - Por defecto filtra por **`Hoy`**, mostrando la pantalla vacía casi todo el tiempo.
  - Solo ofrece chips de `Hoy`, `3 días`, `7 días` y `Rango personalizado` (carece de `Este Mes`, `Mes Anterior`, `Últimos 3 Meses` o `Todo el Historial`).
  - No tiene barra de búsqueda por nombre/unidad del conductor ni filtro por método de pago (`Efectivo` vs `Transferencia` vs `Depósito`).
  - No muestra una barra totalizadora con la **suma en Quetzales (`Q`) de los registros filtrados** en pantalla (a diferencia de `AdminTripsScreen` que sí suma el total filtrado).
  - En las tarjetas del historial no se visualiza el método de pago ni la referencia bancaria/nota.
* **Identificación de pagos pendientes (Cuentas por Cobrar):**
  - El KPI `"Por vencer (7d)"` calcula el monto esperado multiplicando todos los conductores por vencer por la tarifa **semanal** (`expiringIn7Days * cfg.priceSemanal` en [`AdminRepository.kt:L234`](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/tuctucMap/app/src/main/java/com/example/tuctucmap/data/repository/AdminRepository.kt#L234)), ignorando qué plan suele pagar cada conductor y **excluyendo a los conductores ya vencidos (`Vencidos`)** que representan cartera morosa inmediata.

### 5.4. Eje 4: Ganancias del Propietario (Ingresos, Gastos, Ganancias Netas y Mes a Mes)
* **Estado actual:** **Críticamente incompleto.** El propietario del negocio **no cuenta con un panel financiero**. Únicamente ve dos tarjetas pequeñas en la pestaña de conductores (`Ingresos Mes: Q 25` y `Total Recaudado: Q 75`).
* **Confusión conceptual de métricas:** En `AdminTripsScreen` se muestra el indicador **`TOTAL RECAUDADO: Q 40.00`**, que corresponde al dinero cobrado por los conductores a los pasajeros en sus carreras, **no a ingresos del propietario de la app**. Llamar `"Total Recaudado"` a ambas métricas en distintas pantallas induce a error financiero.
* **Ausencia total de Gastos (`Egresos`) y Ganancia Neta (`Utilidad`):** Ni la app Android ni la base de datos en Supabase tienen soporte para registrar costos operativos del negocio (servidores Supabase/Firebase, campañas de marketing, comisiones bancarias, soporte, papelería, etc.). Por ende, es imposible calcular la **Ganancia Neta (`Ingresos por Suscripciones - Gastos Operativos`)** ni comparar el rendimiento **mes a mes (MoM)**.

### 5.5. Eje 5: Estructura y Consistencia en Supabase
* La base de datos cuenta con buen diseño de Row Level Security (RLS) apoyado en `public.is_admin()`, pero presenta 4 problemas estructurales para el administrador:
  1. Trigger `trg_create_initial_driver_trial` inoperante para nuevos conductores debido a `trg_profile_insert_security`.
  2. Constraints `CHECK` de `plan_type` que excluyen el plan `'diario'`.
  3. Ausencia de un RPC transaccional para la renovación manual por parte del administrador (`renew_driver_subscription_by_admin`).
  4. Ausencia de tablas para control de egresos (`admin_expenses`) y auditoría financiera mensual.

---

## 6. Funcionalidades Faltantes y Recomendaciones Priorizadas

A continuación se detallan todas las mejoras funcionales, correctivas y nuevas capacidades recomendadas, indicando su **utilidad**, **prioridad** (`Crítica`, `Alta`, `Media`, `Baja`) y los **requerimientos en Supabase**:

### Prioridad CRÍTICA

| # | Mejora / Nueva Funcionalidad | Utilidad para el Administrador y el Negocio | Información / Cambios Requeridos en Supabase |
| :--- | :--- | :--- | :--- |
| **C-01** | **Panel Financiero del Propietario (Ingresos, Gastos, Ganancia Neta y Comparativa Mes a Mes)** | Permite al dueño conocer la rentabilidad real del negocio (`Ingresos por Suscripciones - Gastos Operativos = Utilidad Neta`), analizar tendencias mensuales, filtrar por rangos de fechas y desglosar ingresos por método de pago y tipo de plan. | • Crear tabla `public.admin_expenses` (ver Sección 7).<br>• Crear RPC `public.get_owner_financial_dashboard(p_start_date, p_end_date)` con zona horaria `'America/Guatemala'`. |
| **C-02** | **Pantalla de Perfil del Administrador y Cambio Seguro de Contraseña con Reautenticación** | Garantiza que el administrador pueda cambiar su contraseña de forma segura (exigiendo validar primero la contraseña actual) y actualizar su nombre y teléfono desde `AdminDrawerSheet`. | • Verificar contraseña actual mediante `signInWithPassword(email, currentPassword)` en Supabase Auth antes de invocar `updateUser { password = newPassword }`.<br>• Permitir `UPDATE` en `public.profiles` (`name`, `phone`) para el propio `auth.uid()`. |
| **C-03** | **Corrección del Flujo de Aprobación Inicial de Conductores y Otorgamiento Atómico de Trial (RESUELTO ✅)** | Evita que los conductores recién aprobados por el administrador queden bloqueados con `"Sin suscripción"`. Al pulsar "Aprobar", se despliega un diálogo donde el administrador decide si otorga período de prueba gratuito (Switch) y la cantidad exacta de días (chips rápidos de 3, 7, 15 y 30 días o campo numérico libre con cálculo dinámico de vigencia). Si no se otorga prueba, el conductor se aprueba con estado "Sin suscripción" para que requiera abonar membresía antes de operar. Todo se ejecuta atómicamente mediante RPC en Supabase y fue verificado en emulador. | • RPC `public.approve_driver_by_admin(p_driver_id UUID, p_grant_trial BOOLEAN DEFAULT TRUE, p_trial_days INT DEFAULT 7)` que actualiza `profiles.approval_status = 'aprobado'`, calcula `subscription_end_date` e inserta el registro `'trial'` en `driver_subscriptions`. |
| **C-04** | **RPC Transaccional para Renovación Manual (`renew_driver_subscription_by_admin`) y Soporte Completo del Plan `'diario'`** | Impide inconsistencias entre `profiles.subscription_end_date` y `driver_subscriptions` cuando falla la red móvil, y habilita el cobro del plan `Diario` (`Q10.00`) que ya existe en Configuración Comercial. | • Actualizar `CHECK` constraints en `driver_subscriptions` y `subscription_payment_requests` para incluir `'diario'`.<br>• Crear RPC `public.renew_driver_subscription_by_admin(...)` que actualice `profiles`, inserte en `driver_subscriptions` (guardando `created_by = auth.uid()`) y soporte `'diario'` en `approve_subscription_payment_request`. |

### Prioridad ALTA

| # | Mejora / Nueva Funcionalidad | Utilidad para el Administrador y el Negocio | Información / Cambios Requeridos en Supabase |
| :--- | :--- | :--- | :--- |
| **A-01** | **Edición de Datos del Conductor y Suspensión / Reactivación Operativa** | Permite al administrador corregir errores en el nombre, teléfono, placa o número de unidad de un conductor, ajustar manualmente su fecha de corte y suspender temporalmente a un conductor por infracciones sin tener que eliminar su cuenta e historial. | • Agregar valor `'suspendido'` al estado de aprobación o usar `approval_status IN ('pendiente', 'aprobado', 'rechazado', 'suspendido')` en `public.profiles`.<br>• Crear RPC o usar `UPDATE public.profiles` bajo política RLS `Admins can update all profiles` para editar `name`, `phone`, `plate_number`, `unit_number`, `approval_status` y `subscription_end_date`. |
| **A-02** | **Mejora Integral del Historial de Transacciones (`AdminSubscriptionsTab`)** | Permite auditar pagos sin fricción: agregar filtros rápidos `Este Mes`, `Mes Anterior` y `Todo`, buscador por conductor, filtro por método de pago (`Efectivo`, `Transferencia`, `Depósito`), tarjeta resumen con el **Total Cobrado (`Q`) del filtro activo**, y visualización de `payment_method`, `notes` y fecha/hora exacta en cada tarjeta. | • No requiere nuevas tablas; aprovechar las columnas ya existentes en `public.driver_subscriptions` (`payment_method`, `notes`, `created_at`) y agregar columna opcional `created_by UUID REFERENCES public.profiles(id)` para saber qué admin registró el cobro. |
| **A-03** | **Módulo de Cuentas por Cobrar y Cartera Vencida (Renovaciones Pendientes)** | Permite al administrador ver una lista priorizada de conductores **vencidos** y **próximos a vencer**, estimando el ingreso esperado según el **último plan pagado por cada conductor** (en lugar de asumir plan semanal para todos) y enviando recordatorios por WhatsApp con los 4 planes tarifarios (`Diario`, `Semanal`, `Quincenal`, `Mensual`). | • Consultar en `public.driver_subscriptions` el último `plan_type` y `amount_paid` de cada conductor activo/vencido (`DISTINCT ON (driver_id) ... ORDER BY created_at DESC`). |
| **A-04** | **Corrección de Bugs Visuales y de Zona Horaria en Android (`AdminDriversTab`, `AdminSubscriptionsTab`, `AdminTripsScreen`, `AdminPassengersScreen`)** | Corrige: 1) `ModalBottomSheet` de renovación con `skipPartiallyExpanded = true`; 2) recorte del botón `"Ver últimos 7 días"`; 3) solapamiento de botones en `Directorio de Pasajeros`; 4) conversión de fechas UTC a `America/Guatemala` en `Auditoría de Viajes`; 5) filtro de `status` en viajes; y 6) eliminación del crash intencional en `AppDrawer`. | • Cambios exclusivamente en capa UI/Repositorio de Android (`tuctucMap/`); en `AdminTripsScreen` leer columna `status` y `completed_at` ya existentes en `public.trips`. |

### Prioridad MEDIA

| # | Mejora / Nueva Funcionalidad | Utilidad para el Administrador y el Negocio | Información / Cambios Requeridos en Supabase |
| :--- | :--- | :--- | :--- |
| **M-01** | **Expediente Individual por Conductor (Ficha 360°)** | Al tocar la tarjeta de un conductor en `AdminDriversTab`, abre una vista detallada con sus KPIs individuales: total pagado en suscripciones, historial de renovaciones, cantidad de viajes completados, ingresos generados en carreras y última ubicación reportada. | • Consultas filtradas por `driver_id` sobre `public.driver_subscriptions` y `public.trips`. |
| **M-02** | **Soporte de Comprobantes Fotográficos de Pago (Boletas / Transferencias)** | Permite que el conductor adjunte foto de la boleta bancaria o captura de transferencia al enviar una solicitud en `subscription_payment_requests`, y que el administrador visualice la imagen antes de aprobarla. | • Agregar columna `receipt_url TEXT` en `public.subscription_payment_requests` y `public.driver_subscriptions`.<br>• Crear bucket privado/autenticado `payment-receipts` en **Supabase Storage** con políticas RLS para conductores (subida propia) y administradores (lectura global). |
| **M-03** | **Gestión de Cuentas Administrativas y Bitácora de Auditoría (`Admin Audit Log`)** | Permite al propietario principal ver qué usuarios tienen `role = 'admin'`, agregar/revocar administradores secundarios y consultar quién aprobó pagos, cambió precios o eliminó cuentas. | • Aprovechar `reviewed_by` en `subscription_payment_requests`, `resolved_by` en `app_crash_logs`, y crear tabla `public.admin_audit_logs (id, admin_id, action_type, target_id, details JSONB, created_at)`. |
| **M-04** | **Gestión Completa de Pasajeros en `AdminPassengersScreen`** | Muestra el correo electrónico en la tarjeta del pasajero, permite ver el historial de viajes solicitados por ese pasajero y habilita una opción para bloquear o eliminar cuentas de pasajeros abusivos o fraudulentos. | • Usar el RPC existente `public.delete_user_by_admin(target_user_id)` o agregar columna `is_blocked BOOLEAN DEFAULT false` en `public.profiles`. |

### Prioridad BAJA

| # | Mejora / Nueva Funcionalidad | Utilidad para el Administrador y el Negocio | Información / Cambios Requeridos en Supabase |
| :--- | :--- | :--- | :--- |
| **B-01** | **Exposición de `currency_symbol` y `osrm_server_url` en `AdminBusinessConfigTab`** | Permite cambiar el símbolo monetario (`Q`, `$`, etc.) y la URL del servidor de ruteo OSRM desde la app sin requerir intervención en base de datos. | • Las columnas `currency_symbol` y `osrm_server_url` **ya existen** en `public.app_config`; solo falta mapearlas en `AdminModels.kt`, `AdminRepository.kt` y `AdminBusinessConfigTab.kt`. |
| **B-02** | **Visualización de `extra_metadata`, `resolved_at` y `resolved_by` en `AdminCrashLogsScreen`** | Enriquecer el modal de detalle de fallos mostrando metadatos adicionales del dispositivo y la fecha/autor de resolución del incidente. | • Las columnas **ya existen** en `public.app_crash_logs`; solo falta incluirlas en `AdminCrashLog` (`AdminModels.kt`). |

---

## 7. Propuesta de Diseño: Panel de Control Financiero y Ganancias del Propietario

Para que el propietario de **TucTuc Map** tenga un control financiero claro, exacto y auditable mes a mes desde la aplicación Android, se propone la siguiente arquitectura de datos en Supabase y diseño funcional en la app móvil.

### 7.1. Separación Clara de Conceptos Financieros
Es fundamental separar en la interfaz dos flujos de dinero distintos:
1. **Finanzas del Propietario (Negocio SaaS / Suscripciones):** Dinero que entra directamente a la caja o cuentas bancarias del propietario por el pago de membresías de los conductores (`driver_subscriptions.amount_paid`), menos los gastos operativos de mantener y promocionar la plataforma (`admin_expenses.amount`).
2. **Volumen Operativo de la Flota (GMV de Viajes):** Dinero que los pasajeros pagan directamente a los conductores por las carreras (`trips.fare`). En `AdminTripsScreen`, la etiqueta `"TOTAL RECAUDADO"` debe renombrarse a **`"VOLUMEN DE CARRERAS (CONDUCTORES)"`** para no confundirlo con la recaudación del propietario.

### 7.2. Esquema Propuesto en Supabase para Gastos y Cierre Mensual

#### A. Nueva Tabla de Gastos Operativos (`public.admin_expenses`)
```sql
CREATE TABLE IF NOT EXISTS public.admin_expenses (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    category TEXT NOT NULL CHECK (category IN (
        'infraestructura', -- Supabase, Firebase, servidor OSRM, dominios
        'marketing',       -- Publicidad, volantes, promociones a conductores/pasajeros
        'operaciones',     -- Telefonía/WhatsApp, soporte, comisiones bancarias
        'impuestos',       -- Contabilidad, impuestos locales
        'otros'
    )),
    description TEXT NOT NULL,
    amount NUMERIC(10, 2) NOT NULL CHECK (amount > 0),
    payment_method TEXT DEFAULT 'transferencia' CHECK (payment_method IN ('efectivo', 'transferencia', 'tarjeta', 'deposito_bancario')),
    expense_date DATE NOT NULL DEFAULT (CURRENT_DATE AT TIME ZONE 'America/Guatemala'),
    reference_note TEXT,
    created_by UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

ALTER TABLE public.admin_expenses ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Solo administradores gestionan gastos operativos"
    ON public.admin_expenses FOR ALL
    TO authenticated
    USING (public.is_admin())
    WITH CHECK (public.is_admin());
```

#### B. RPC Financiero Integral y Mes a Mes (`public.get_owner_financial_report`)
Un único procedimiento almacenado (`SECURITY DEFINER`, protegido con `public.is_admin()`) que calcule todos los indicadores convirtiendo las marcas de tiempo a `'America/Guatemala'`:
- **Parámetros:** `p_start_date DATE`, `p_end_date DATE`.
- **Retorno JSONB estructurado:**
  1. **`kpis_periodo`:**
     - `gross_revenue`: Suma de `driver_subscriptions.amount_paid` (donde `plan_type != 'trial'`) en el rango seleccionado.
     - `total_expenses`: Suma de `admin_expenses.amount` en el rango seleccionado.
     - `net_profit`: `gross_revenue - total_expenses` (**Ganancia Neta del Propietario**).
     - `profit_margin_pct`: `(net_profit / NULLIF(gross_revenue, 0)) * 100`.
     - `paid_subscriptions_count`: Total de renovaciones pagadas en el período.
     - `active_drivers_arpu`: Ingreso promedio por conductor activo.
  2. **`desglose_ingresos`:**
     - Por método de pago (`efectivo`, `transferencia`, `deposito_bancario`): monto y porcentaje (permite cuadrar caja chica en efectivo vs. estados de cuenta de Banrural y Banco Industrial).
     - Por tipo de plan (`diario`, `semanal`, `quincenal`, `mensual`, `personalizado`): cantidad de planes vendidos y monto total.
  3. **`serie_mensual_historica` (Tabla y Gráfica Mes a Mes - últimos 6 o 12 meses):**
     - Lista ordenada por mes (`YYYY-MM`, ej. `2026-10`, `2026-11`) con:
       - `month_label` (`"Oct 2026"`, `"Nov 2026"`)
       - `revenue` (Ingresos brutos del mes)
       - `expenses` (Gastos del mes)
       - `net_profit` (Ganancia neta del mes)
       - `mom_growth_pct` (% de crecimiento o caída respecto al mes inmediato anterior)
  4. **`cuentas_por_cobrar` (Proyección de Cartera):**
     - `overdue_drivers_count` y `overdue_estimated_amount` (conductores ya vencidos).
     - `expiring_7d_drivers_count` y `expiring_7d_estimated_amount` (conductores que vencen en los próximos 7 días, calculado según su último plan contratado).

### 7.3. Diseño de Interfaz en la App Android (`AdminFinanceScreen` / Pestaña `Finanzas`)

Se recomienda transformar la actual pestaña `Pagos` (o incorporar una vista superior en `Suscripciones & Pagos` / acceso directo en `AdminDrawerSheet` llamado **"Finanzas y Ganancias"**) dividida en **3 vistas claras**:

1. **Vista 1: Resumen Ejecutivo y Ganancias (`Dashboard del Propietario`)**
   - **Selector superior de período:** Chips rápidos (`Este Mes`, `Mes Anterior`, `Últimos 3 Meses`, `Este Año`, `Personalizado`).
   - **Tarjeta Principal de Estado de Resultados (P&L):**
     - **Ingresos Cobrados:** `Q X,XXX.XX` (verde)
     - **Gastos Operativos:** `- Q XXX.XX` (rojo)
     - **Ganancia Neta del Propietario:** `= Q X,XXX.XX` (destacado en grande con badge de margen `%` y variación `+/- % vs. mes anterior`).
   - **Conciliación de Caja y Bancos:** Tarjeta que separa cuánto dinero entró en **Efectivo** vs. **Transferencia / Depósito Bancario**, permitiendo al dueño cuadrar sus cuentas de Banrural y Banco Industrial al instante.
   - **Tabla / Gráfico Comparativo Mes a Mes:** Barras comparativas (`Ingresos` vs `Gastos` vs `Ganancia Neta`) para cada mes del año, permitiendo tocar cualquier mes para ver el detalle de ese período.
   - **Botón `Exportar Reporte Financiero (Excel)`:** Genera un archivo `.xls` con 3 hojas o secciones: *Resumen Mensual (Mes a Mes)*, *Detalle de Ingresos por Suscripciones* y *Detalle de Gastos Operativos*.

2. **Vista 2: Registro de Gastos (`Egresos del Negocio`)**
   - Lista de gastos del período con botón flotante `+ Registrar Gasto` (`ModalBottomSheet` con categoría, monto, fecha, método de pago y descripción) y opción de eliminar/editar registros erróneos.

3. **Vista 3: Cobranza, Solicitudes e Historial de Suscripciones**
   - Unifica la actual bandeja de `Solicitudes Pendientes`, una nueva pestaña de `Por Cobrar / Vencidos` (con botón directo de cobro por WhatsApp) y el `Historial Global` mejorado con buscador, filtro por método de pago y sumatoria total en Quetzales.

