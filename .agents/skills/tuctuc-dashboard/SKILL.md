---
name: tuctuc-dashboard
description: >-
  Guía y runbook especializado para desarrollo, arquitectura, autenticación administrativa, mapa de flota en vivo y compilación del panel Angular (/tuctucMapDashboard). Usar cuando se modifique o analice el dashboard web.
---

# Skill: Angular Dashboard (TucTuc Map)

Esta guía define la arquitectura, normas de seguridad y procedimientos operativos para el panel administrativo web ubicado en `tuctucMapDashboard/`.

---

## 1. Stack Tecnológico & Dependencias
- **Framework:** Angular (v22) con Standalone Components.
- **Componentes UI:** Angular Material (@angular/material).
- **Mapas:** Leaflet con OpenStreetMap (`leaflet`, `@types/leaflet`).
- **Estado & Asincronía:** RxJS, Signals de Angular.
- **Backend & Auth:** `@supabase/supabase-js` para operaciones relacionales y validación de sesiones.
- **Telemetría en Vivo:** Firebase Realtime Database para monitoreo en vivo de coordenadas de la flota.

---

## 2. Autenticación, Secretos y Seguridad Administrativa (RLS & XSS)
1. **Gestión de Secretos (`environment.ts`):**
   - `tuctucMapDashboard/src/environments/environment.ts` está excluido en `.gitignore` y contiene `supabase.url`, `supabase.key` y `firebase.databaseUrl`. La plantilla versionada en Git es `environment.example.ts`.
2. **Regla de Rol Obligatoria:**
   - Todo acceso y operación en el dashboard requiere que el usuario autenticado en Supabase tenga `role = 'admin'` en la tabla `public.profiles` (validado en backend con `public.is_admin()`).
   - `authGuard` y `LoginComponent` validan este rol para evitar intentos no autorizados y llamadas bloqueadas por políticas RLS (`403 Forbidden`).
3. **Sanitización Anti-XSS en Mapas Leaflet:**
   - Todo contenido interpolado dentro de `bindPopup()` en `fleet-map.component.ts` y `trips.component.ts` debe pasar por el helper `escapeHtml()` para prevenir ataques de Stored DOM XSS desde nombres o placas de usuarios.

---

## 3. Módulos y Funcionalidades Clave
1. **Monitoreo de Flota (`/fleet-map`):**
   - Implementado con Leaflet.
   - Escucha en tiempo real la colección `driver_locations` en Firebase Realtime Database y descarta automáticamente coordenadas fantasma con más de **3 minutos de antigüedad** (`TTL_MS = 3 * 60 * 1000`).
   - Pines diferenciados por estado del torito: verde para `Disponible`, naranja para `Ocupado`.
   - Panel lateral interactivo con lista de conductores y centrado dinámico de cámara en el mapa.
2. **Gestión y Cobranza de Suscripciones (`/drivers` y `/subscriptions`):**
   - Al cargar conductores (`fetchDrivers()`), invoca el RPC `sync_server_time_and_expire` para actualizar en base de datos las suscripciones vencidas antes de calcular KPIs.
   - **Renovación Acumulativa Justa:** Tanto `renewSubscription()` en `SupabaseService` como `calculatePlanExpiry()` en `DriversComponent` suman los nuevos días (7, 15, 30 o personalizados) a la fecha de vencimiento vigente (`subscription_end_date`) si el conductor paga por adelantado antes de vencer, garantizando que nunca pierda días pagados.
   - **Configuración Dinámica (`app_config`):** El mensaje de recordatorio de cobro por WhatsApp utiliza las cuentas bancarias y titular configurados en `public.app_config`.
   - Visualización de KPIs financieros, diálogo de renovación con comprobantes de pago y exportación a CSV.
3. **Auditoría de Viajes (`/trips`):**
   - Implementado en `TripsComponent` (`trips.component.ts`), tabla de viajes completados con paginación, ordenamiento, tarifas cobradas (`fare`), distancias recorridas (`distance_km`) y exportación completa a CSV.
   - **Identidad del Pasajero (Registrado o Invitado):** `getCompletedTrips()` resuelve el nombre, teléfono y flag `isRegistered` del pasajero (según si `trip.passenger_id` existe o es null). En la tabla y en el modal de ruta se muestran badges visuales (`Registrado` en verde esmeralda con `verified_user` vs `Sin cuenta` en pizarra con `person_outline`), y la exportación CSV incluye la columna `'Tipo Pasajero'`.
   - **Visor Modal de Ruta GPS:** Permite visualizar en un mapa interactivo Leaflet la trayectoria exacta paso a paso recorrida por el conductor desde la aceptación hasta la finalización del viaje (con popups sanitizados mediante `escapeHtml`).
4. **Directorio de Pasajeros (`/passengers`):**
   - Búsqueda en tiempo real por nombre/teléfono, historial de viajes solicitados y enlace directo de WhatsApp para atención y soporte.

5. **Diseño Responsivo Multi-Dispositivo (Desktop, Tablet, Mobile):**
   - **Shell (`shell.component`):** Usa `BreakpointObserver` (`(max-width: 991.98px)`) para alternar automáticamente el sidenav entre modo `'side'` (escritorio) y `'over'` (móvil/tablet con backdrop y cierre automático tras clic en ítem). Toolbar con reducción de elementos en pantallas ultra-compactas (`< 640px`).
   - **Tablas de Datos (`drivers`, `subscriptions`, `passengers`, `trips`):** Contenedores con scroll horizontal táctil suave (`-webkit-overflow-scrolling: touch`) y anchos mínimos de celda para evitar deformación o truncamiento de badges y acciones en pantallas reducidas.
   - **Mapa de Flota (`fleet-map`):** Layout adaptativo que conmuta de fila horizontal a columna vertical en pantallas `< 992px`, con altura contenida y scroll interno para la lista de conductores.
   - **Diálogos y Modales (`styles.css`):** Límites de `max-width: 95vw` y anchos fluidos para garantizar legibilidad completa en pantallas de teléfonos estándar (360px–480px).

---

## 4. Verificación y Compilación Obligatoria
Antes de concluir cualquier cambio en el panel administrativo, ejecutar y verificar el empaquetado de producción:

```powershell
# Desde el directorio tuctucMapDashboard/
cd tuctucMapDashboard
npm run build
```

- Verificar que la salida del build finalice sin errores de TypeScript, rutas o plantillas (`Application bundle generation complete`).
- Para desarrollo local, iniciar con:
  ```powershell
  npm start
  ```
