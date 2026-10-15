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
  - `profiles`: Perfiles de usuarios con columna `role` (`passenger`, `driver`, `admin`), teléfono, nombre, datos de torito (número de placas) y estado de aprobación para conductores (`is_approved`).
  - `trips`: Ciclo de vida y auditoría de viajes (`requested`, `accepted`, `completed`, `cancelled`, `rejected`), con referencias a `passenger_id` y `driver_id`.
  - `driver_subscriptions`: Monetización y control de membresías de conductores. Contiene `plan_type` (`trial`, `semanal`, `quincenal`, `mensual`, `personalizado`), `amount_paid` (NUMERIC), `payment_method` (`efectivo`, `transferencia`, `deposito_bancario`, `gratis_prueba`), `reference_number` (TEXT) y fechas de vigencia.
  - `driver_locations`: Coordenadas persistentes de respaldo en PostgreSQL (mientras que la telemetría en tiempo real de alta frecuencia se opera en Firebase Realtime Database).

---

## 2. Row Level Security (RLS) - Reglas Obligatorias
Toda tabla creada o modificada en Supabase **DEBE** tener habilitado RLS (`ALTER TABLE ... ENABLE ROW LEVEL SECURITY;`).
1. **Perfiles (`profiles`):**
   - Lectura: Los usuarios pueden leer sus propios perfiles; los administradores pueden leer todos. Los pasajeros pueden consultar información pública del conductor asignado.
   - Modificación: Solo el propio usuario autenticado (`auth.uid() = id`) o administradores pueden actualizar el perfil.
2. **Viajes (`trips`):**
   - Lectura e Inserción: Restringida a los participantes del viaje (`passenger_id = auth.uid()` o `driver_id = auth.uid()`) y a los administradores.
3. **Suscripciones (`driver_subscriptions`):**
   - Lectura: El conductor puede consultar sus propias suscripciones; el administrador puede consultar todas.
   - Creación/Edición: Estrictamente reservado a administradores (`role = 'admin'`).

---

## 3. Ejecución de Consultas y Modificaciones (`run_sql.js`)
Para consultar o modificar la base de datos directamente desde la terminal, utilizar el script Node.js ubicado en `databaseTuctucApp/run_sql.js`.

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
