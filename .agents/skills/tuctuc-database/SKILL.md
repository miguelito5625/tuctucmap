---
name: tuctuc-database
description: >-
  Guía y runbook especializado para la base de datos PostgreSQL en Supabase (/databaseTuctucApp), ejecución segura de consultas mediante run_sql.js, sincronización de db.sql y políticas de seguridad RLS.
---

# Skill: Base de Datos & Backend (Supabase PostgreSQL)

Esta guía define los estándares, esquemas y procedimientos de ejecución SQL para el backend de TucTuc Map ubicado en `databaseTuctucApp/`.

---

## 1. Arquitectura de Datos & Tablas Principales
- **Motor:** PostgreSQL alojado en Supabase.
- **Tablas Principales:**
  - `profiles`: Perfiles de usuarios con columna `role` (`passenger`, `driver`, `admin`), teléfono, nombre, datos de torito (`vehicle_plate`, `unit_number` para Número de Torito/Unidad municipal) y estado de aprobación/suscripción (`approval_status`, `subscription_status`, `subscription_end_date`).
  - `trips`: Ciclo de vida y auditoría de viajes (`requested`, `accepted`, `completed`, `cancelled`, `rejected`), con referencias a `passenger_id`, `driver_id`, tarifa cobrada (`fare NUMERIC`), distancia recorrida (`distance_km NUMERIC`) y trayectoria GPS paso a paso (`route_points JSONB`).
  - `driver_subscriptions`: Monetización y control de membresías de conductores. Contiene `plan_type` (`trial`, `semanal`, `quincenal`, `mensual`, `personalizado`), `amount_paid` (NUMERIC), `payment_method` (`efectivo`, `transferencia`, `deposito_bancario`, `gratis_prueba`), `reference_number` (TEXT) y fechas de vigencia.
  - `app_config`: Configuración comercial dinámica (fila única `id = 1`) con teléfono de WhatsApp del administrador, cuentas bancarias (`bank_1_*`, `bank_2_*`), titular, precios de planes y radio de geocerca (`search_radius_km`).
  - `subscription_payment_requests`: Solicitudes de renovación y números de boleta/referencia bancaria enviados por los conductores desde la app Android para conciliación administrativa.
  - `driver_locations`: Coordenadas persistentes de respaldo en PostgreSQL (mientras que la telemetría en tiempo real de alta frecuencia se opera en Firebase Realtime Database).

---

## 2. Row Level Security (RLS), Triggers y Funciones RPC
Toda tabla creada o modificada en Supabase **DEBE** tener habilitado RLS (`ALTER TABLE ... ENABLE ROW LEVEL SECURITY;`).
1. **Perfiles (`profiles`) y Triggers de Seguridad:**
   - Función `public.is_admin()` (`SECURITY DEFINER`) para verificar rol de administrador sin recursión RLS.
   - Trigger `trg_profile_insert_security`: Impide que un cliente se registre como `admin`, valida que la placa o número de unidad no estén duplicados y asigna automáticamente los 7 días de prueba (`NOW() + INTERVAL '7 days'`) desde el reloj del servidor.
   - Trigger `trg_create_initial_driver_trial`: Crea automáticamente el registro `'trial'` en `driver_subscriptions` al registrarse un conductor.
   - Trigger `trg_protect_profile_sensitive_columns`: Impide que un usuario no administrador modifique `role`, `approval_status`, `subscription_status` o `subscription_end_date`.
2. **Reloj del Servidor y Auto-Expiración (`sync_server_time_and_expire`):**
   - Función RPC que marca como `'expired'` las suscripciones vencidas según `NOW()` de PostgreSQL y devuelve la hora exacta del servidor (`server_time_ms`) para evitar fraudes por cambio de fecha en el teléfono.
3. **Configuración y Pagos (`app_config`, `subscription_payment_requests`):**
   - `app_config`: Lectura pública (`SELECT`), escritura exclusiva para administradores (`public.is_admin()`).
   - `subscription_payment_requests`: Los conductores pueden insertar y leer sus propios comprobantes; solo los administradores pueden aprobarlos o rechazarlos.

---

## 3. Ejecución de Consultas y Modificaciones (`run_sql.js` + `.env`)
Las credenciales de conexión ya **no** están hardcodeadas en los scripts; se leen automáticamente desde el archivo raíz `.env` mediante `databaseTuctucApp/db_config.js`.

### Procedimiento de Uso:
```powershell
# Desde el directorio databaseTuctucApp/
cd databaseTuctucApp

# Consulta de prueba:
node run_sql.js "SELECT id, email, role FROM public.profiles LIMIT 5;"

# Verificar estado de suscripciones:
node run_sql.js "SELECT id, driver_id, plan_type, amount_paid, payment_method FROM public.driver_subscriptions ORDER BY created_at DESC LIMIT 5;"
```

---

## 4. Buenas Prácticas de Migración
1. **Sincronización con `db.sql`:**
   - Todo cambio aplicado a la base de datos viva debe registrarse formalmente en `databaseTuctucApp/db.sql` para mantener el repositorio como fuente de verdad.
2. **Consultas de Validación:**
   - Después de aplicar una modificación DDL (alteración de tabla o cambio de política RLS), ejecutar inmediatamente una consulta SELECT o de prueba con `run_sql.js` para asegurar que las políticas y permisos funcionan como se espera.
