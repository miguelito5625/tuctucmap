-- Migración: Tabla de Reportes de Errores y Crashes para Telemetría de la App Móvil
CREATE TABLE IF NOT EXISTS public.app_crash_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    user_email TEXT,
    role TEXT,
    device_brand TEXT,
    device_model TEXT,
    device_manufacturer TEXT,
    android_version TEXT,
    sdk_int INTEGER,
    app_version TEXT,
    build_number INTEGER,
    build_type TEXT,
    error_type TEXT NOT NULL,
    error_message TEXT,
    stack_trace TEXT NOT NULL,
    thread_name TEXT,
    extra_metadata JSONB DEFAULT '{}'::jsonb,
    resolved BOOLEAN DEFAULT FALSE,
    resolved_at TIMESTAMPTZ,
    resolved_by UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Habilitar RLS
ALTER TABLE public.app_crash_logs ENABLE ROW LEVEL SECURITY;

-- Permitir inserción anónima y autenticada (cualquier app puede enviar sus logs de fallo)
DROP POLICY IF EXISTS "Allow insert to app_crash_logs" ON public.app_crash_logs;
CREATE POLICY "Allow insert to app_crash_logs" ON public.app_crash_logs
  FOR INSERT
  WITH CHECK (true);

-- Solo administradores pueden ver los logs
DROP POLICY IF EXISTS "Admins can select app_crash_logs" ON public.app_crash_logs;
CREATE POLICY "Admins can select app_crash_logs" ON public.app_crash_logs
  FOR SELECT
  USING (public.is_admin());

-- Solo administradores pueden actualizar (ej. marcar como resuelto)
DROP POLICY IF EXISTS "Admins can update app_crash_logs" ON public.app_crash_logs;
CREATE POLICY "Admins can update app_crash_logs" ON public.app_crash_logs
  FOR UPDATE
  USING (public.is_admin());

-- Solo administradores pueden eliminar logs
DROP POLICY IF EXISTS "Admins can delete app_crash_logs" ON public.app_crash_logs;
CREATE POLICY "Admins can delete app_crash_logs" ON public.app_crash_logs
  FOR DELETE
  USING (public.is_admin());

-- Índices de consulta rápida
CREATE INDEX IF NOT EXISTS idx_crash_logs_created_at ON public.app_crash_logs (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_crash_logs_resolved ON public.app_crash_logs (resolved);

