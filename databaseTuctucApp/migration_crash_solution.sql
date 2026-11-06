-- Migración: Añadir columna 'solution' con límite de 300 caracteres a app_crash_logs
ALTER TABLE public.app_crash_logs
ADD COLUMN IF NOT EXISTS solution VARCHAR(300);

-- Llenar la solución para las incidencias que ya fueron resueltas
UPDATE public.app_crash_logs
SET solution = 'Prueba intencional de telemetría auditada: captura local, despacho al inicio y sincronización con Supabase verificado exitosamente.'
WHERE resolved = true AND (solution IS NULL OR solution = '');
