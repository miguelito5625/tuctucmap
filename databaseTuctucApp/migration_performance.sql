-- Migración de optimización de rendimiento: Reporte de Funcionamiento (BUG-04 y BUG-08)

-- 1. Añadir columna opcional osrm_server_url a app_config si no existe
ALTER TABLE public.app_config 
ADD COLUMN IF NOT EXISTS osrm_server_url TEXT NOT NULL DEFAULT 'https://router.project-osrm.org/route/v1/driving/';

-- 2. Función RPC para cálculo nativo de métricas financieras de administración sin escanear miles de filas
CREATE OR REPLACE FUNCTION public.get_admin_financial_summary()
RETURNS TABLE (
  total_revenue NUMERIC,
  month_revenue NUMERIC
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF NOT public.is_admin() THEN
    RAISE EXCEPTION 'Acceso denegado: se requieren permisos de administrador';
  END IF;

  RETURN QUERY
  SELECT
    COALESCE(SUM(amount_paid), 0)::NUMERIC AS total_revenue,
    COALESCE(SUM(CASE 
      WHEN date_trunc('month', created_at) = date_trunc('month', CURRENT_TIMESTAMP) 
      THEN amount_paid 
      ELSE 0 
    END), 0)::NUMERIC AS month_revenue
  FROM public.driver_subscriptions;
END;
$$;

GRANT EXECUTE ON FUNCTION public.get_admin_financial_summary() TO authenticated;

-- 3. Función RPC para obtener pasajeros con su conteo exacto de viajes en una sola consulta relacional
CREATE OR REPLACE FUNCTION public.get_admin_passengers_summary()
RETURNS TABLE (
  id UUID,
  name TEXT,
  phone TEXT,
  email TEXT,
  created_at TIMESTAMPTZ,
  role TEXT,
  trips_count BIGINT
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF NOT public.is_admin() THEN
    RAISE EXCEPTION 'Acceso denegado: se requieren permisos de administrador';
  END IF;

  RETURN QUERY
  SELECT
    p.id,
    p.name,
    p.phone,
    p.email,
    p.created_at,
    p.role,
    COUNT(t.id) AS trips_count
  FROM public.profiles p
  LEFT JOIN public.trips t ON t.passenger_id = p.id::text
  WHERE p.role = 'passenger'
  GROUP BY p.id, p.name, p.phone, p.email, p.created_at, p.role
  ORDER BY p.created_at DESC;
END;
$$;

GRANT EXECUTE ON FUNCTION public.get_admin_passengers_summary() TO authenticated;

