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

## 2. Autenticación y Seguridad Administrativa (RLS)
1. **Regla de Rol Obligatoria:**
   - Todo acceso y operación en el dashboard requiere que el usuario autenticado en Supabase tenga `role = 'admin'` en la tabla `public.profiles`.
   - `authGuard` y `LoginComponent` validan este rol para evitar intentos no autorizados y llamadas bloqueadas por políticas RLS (`403 Forbidden`).
2. **Operaciones Privilegiadas:**
   - La aprobación de conductores y la creación/renovación de registros en `driver_subscriptions` están restringidas por RLS a administradores.

---

## 3. Módulos y Funcionalidades Clave
1. **Monitoreo de Flota (`/fleet-map`):**
   - Implementado con Leaflet.
   - Escucha en tiempo real la colección `driver_locations` en Firebase Realtime Database.
   - Pines diferenciados por estado del torito: verde para `Disponible`, naranja para `Ocupado`.
   - Panel lateral interactivo con lista de conductores y centrado dinámico de cámara en el mapa.
2. **Gestión y Cobranza de Suscripciones (`/drivers` y `/subscriptions`):**
   - Visualización de KPIs financieros: ingresos totales recaudados, ingresos del mes actual y alertas de suscripciones por expirar en los próximos 7 días con montos proyectados.
   - Botón de cobro rápido vía WhatsApp en cada fila para contactar al conductor con su saldo, fecha de vencimiento y datos de cuentas bancarias en un solo clic.
   - Diálogo de renovación con planes preconfigurados (Semanal Q25, Quincenal Q45, Mensual Q80, Personalizado) y registro de comprobantes de pago.
   - Función de exportación a CSV para reportes de recaudación y padrón de conductores.
3. **Auditoría de Viajes (`/trips`):**
   - Implementado en `TripsComponent` (`trips.component.ts`), tabla de viajes completados con paginación, ordenamiento y exportación a CSV para conciliación municipal.
4. **Directorio de Pasajeros (`/passengers`):**
   - Búsqueda en tiempo real por nombre/teléfono, historial de viajes solicitados y enlace directo de WhatsApp para atención y soporte.

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
