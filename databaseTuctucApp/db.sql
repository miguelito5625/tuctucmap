-- db.sql para TucTuc Map en Supabase (Esquema Maestro Blindado)

-- 1. Crear tabla de perfiles para pasajeros, conductores y administradores
CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID REFERENCES auth.users(id) ON DELETE CASCADE PRIMARY KEY,
    email TEXT,
    name TEXT,
    phone TEXT,
    role TEXT CHECK (role IN ('passenger', 'driver', 'admin')),
    subscription_end_date TIMESTAMPTZ,
    approval_status TEXT DEFAULT 'pendiente', -- 'pendiente', 'aprobado', 'rechazado', 'suscripcion_vencida'
    plate_number TEXT,
    unit_number TEXT DEFAULT NULL, -- Número de Torito / Padrón visible (ej. "Torito #14")
    created_at TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS unit_number TEXT DEFAULT NULL;
ALTER TABLE public.profiles ALTER COLUMN approval_status SET DEFAULT 'pendiente';

-- 1.5 Crear tabla de historial de suscripciones para conductores
CREATE TABLE IF NOT EXISTS public.driver_subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE,
    start_date TIMESTAMPTZ DEFAULT NOW(),
    end_date TIMESTAMPTZ NOT NULL,
    plan_type TEXT CHECK (plan_type IN ('trial', 'semanal', 'quincenal', 'mensual', 'personalizado')) DEFAULT 'semanal',
    amount_paid NUMERIC(10,2) DEFAULT 0.00,
    payment_method TEXT CHECK (payment_method IN ('efectivo', 'transferencia', 'deposito_bancario', 'gratis_prueba')) DEFAULT 'deposito_bancario',
    reference_number TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Asegurar que el constraint de plan_type soporte 'personalizado'
ALTER TABLE public.driver_subscriptions DROP CONSTRAINT IF EXISTS driver_subscriptions_plan_type_check;
ALTER TABLE public.driver_subscriptions ADD CONSTRAINT driver_subscriptions_plan_type_check
    CHECK (plan_type IN ('trial', 'semanal', 'quincenal', 'mensual', 'personalizado'));

-- 2. Crear tabla de configuración dinámica del negocio (WhatsApp admin, cuentas bancarias, tarifas, radio km)
CREATE TABLE IF NOT EXISTS public.app_config (
    id TEXT PRIMARY KEY DEFAULT 'default',
    admin_whatsapp_phone TEXT NOT NULL DEFAULT '50230000000',
    banrural_account TEXT NOT NULL DEFAULT '3402-0987-65',
    bi_account TEXT NOT NULL DEFAULT '027-019842-1',
    price_semanal NUMERIC(10,2) NOT NULL DEFAULT 25.00,
    price_quincenal NUMERIC(10,2) NOT NULL DEFAULT 45.00,
    price_mensual NUMERIC(10,2) NOT NULL DEFAULT 80.00,
    max_search_radius_km NUMERIC(5,2) NOT NULL DEFAULT 5.00,
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

INSERT INTO public.app_config (id, admin_whatsapp_phone, banrural_account, bi_account, price_semanal, price_quincenal, price_mensual, max_search_radius_km)
VALUES ('default', '50230000000', '3402-0987-65', '027-019842-1', 25.00, 45.00, 80.00, 5.00)
ON CONFLICT (id) DO NOTHING;

-- 2.5 Crear tabla de notificaciones de boletas de pago enviadas por conductores
CREATE TABLE IF NOT EXISTS public.subscription_payment_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE NOT NULL,
    plan_type TEXT CHECK (plan_type IN ('semanal', 'quincenal', 'mensual', 'personalizado')) DEFAULT 'semanal',
    amount NUMERIC(10,2) NOT NULL DEFAULT 25.00,
    payment_method TEXT CHECK (payment_method IN ('efectivo', 'transferencia', 'deposito_bancario')) DEFAULT 'deposito_bancario',
    reference_number TEXT NOT NULL,
    status TEXT CHECK (status IN ('pendiente', 'aprobado', 'rechazado')) DEFAULT 'pendiente',
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 3. Crear tabla de viajes
CREATE TABLE IF NOT EXISTS public.trips (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    passenger_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE,
    driver_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE,
    status TEXT CHECK (status IN ('requested', 'accepted', 'completed', 'cancelled', 'rejected')) DEFAULT 'requested',
    passenger_lat DOUBLE PRECISION,
    passenger_lon DOUBLE PRECISION,
    message TEXT,
    fare NUMERIC(10,2) DEFAULT NULL,
    distance_km NUMERIC(10,2) DEFAULT NULL,
    route_points JSONB DEFAULT NULL,
    passenger_name TEXT DEFAULT NULL,
    passenger_phone TEXT DEFAULT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Índices de rendimiento para consultas por fecha y conductor
CREATE INDEX IF NOT EXISTS idx_trips_driver_created ON public.trips(driver_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_trips_passenger_created ON public.trips(passenger_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_subscriptions_driver_created ON public.driver_subscriptions(driver_id, created_at DESC);

-- ============================================================================
-- FUNCIONES DE SEGURIDAD Y TRIGGERS (PREVENCIÓN DE ESCALACIÓN Y FRAUDE)
-- ============================================================================

-- Función auxiliar sin recursión para verificar si el usuario actual es admin
CREATE OR REPLACE FUNCTION public.is_admin()
RETURNS BOOLEAN AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.profiles
    WHERE id = auth.uid() AND role = 'admin'
  );
$$ LANGUAGE sql SECURITY DEFINER SET search_path = public STABLE;

-- Trigger 1: Blindar INSERT en profiles (hora del servidor para los 7 días de prueba y bloqueo de rol admin)
CREATE OR REPLACE FUNCTION public.handle_profile_insert_security()
RETURNS TRIGGER AS $$
DECLARE
  normalized_plate TEXT;
  plate_already_used BOOLEAN := FALSE;
BEGIN
  IF current_user NOT IN ('postgres', 'service_role') AND NOT public.is_admin() THEN
    -- Impedir que alguien se registre como admin desde el cliente
    IF NEW.role = 'admin' OR NEW.role IS NULL THEN
      NEW.role := 'passenger';
    END IF;

    IF NEW.role = 'driver' THEN
      normalized_plate := UPPER(REGEXP_REPLACE(COALESCE(NEW.plate_number, ''), '[^A-Z0-9]', '', 'g'));
      IF LENGTH(normalized_plate) > 0 THEN
        SELECT EXISTS (
          SELECT 1 FROM public.profiles
          WHERE id <> NEW.id
            AND UPPER(REGEXP_REPLACE(COALESCE(plate_number, ''), '[^A-Z0-9]', '', 'g')) = normalized_plate
        ) INTO plate_already_used;
      END IF;

      IF plate_already_used THEN
        -- Si la placa ya fue registrada antes, no otorgar otros 7 días gratis automáticos
        NEW.approval_status := 'pendiente';
        NEW.subscription_end_date := NOW();
      ELSE
        -- Calcular los 7 días de prueba estrictamente con el reloj del servidor PostgreSQL
        NEW.approval_status := 'aprobado';
        NEW.subscription_end_date := NOW() + INTERVAL '7 days';
      END IF;
    ELSE
      NEW.approval_status := NULL;
      NEW.subscription_end_date := NULL;
    END IF;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

DROP TRIGGER IF EXISTS trg_profile_insert_security ON public.profiles;
CREATE TRIGGER trg_profile_insert_security
  BEFORE INSERT ON public.profiles
  FOR EACH ROW
  EXECUTE FUNCTION public.handle_profile_insert_security();

-- Trigger 2: Crear automáticamente el registro de prueba en driver_subscriptions desde el servidor
CREATE OR REPLACE FUNCTION public.create_initial_driver_trial_subscription()
RETURNS TRIGGER AS $$
BEGIN
  IF NEW.role = 'driver' AND NEW.subscription_end_date IS NOT NULL AND NEW.subscription_end_date > NOW() THEN
    IF NOT EXISTS (SELECT 1 FROM public.driver_subscriptions WHERE driver_id = NEW.id) THEN
      INSERT INTO public.driver_subscriptions (
        driver_id, start_date, end_date, plan_type, amount_paid, payment_method, reference_number
      ) VALUES (
        NEW.id, NOW(), NEW.subscription_end_date, 'trial', 0.00, 'gratis_prueba', 'TRIAL-AUTO'
      );
    END IF;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

DROP TRIGGER IF EXISTS trg_create_initial_driver_trial ON public.profiles;
CREATE TRIGGER trg_create_initial_driver_trial
  AFTER INSERT ON public.profiles
  FOR EACH ROW
  EXECUTE FUNCTION public.create_initial_driver_trial_subscription();

-- Trigger 3: Blindar UPDATE en profiles (impedir que un conductor modifique su fecha de vencimiento, estado o rol)
CREATE OR REPLACE FUNCTION public.protect_profile_sensitive_columns()
RETURNS TRIGGER AS $$
BEGIN
  IF current_user NOT IN ('postgres', 'service_role') AND NOT public.is_admin() THEN
    NEW.role := OLD.role;
    NEW.approval_status := OLD.approval_status;
    NEW.subscription_end_date := OLD.subscription_end_date;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

DROP TRIGGER IF EXISTS trg_protect_profile_sensitive_columns ON public.profiles;
CREATE TRIGGER trg_protect_profile_sensitive_columns
  BEFORE UPDATE ON public.profiles
  FOR EACH ROW
  EXECUTE FUNCTION public.protect_profile_sensitive_columns();

-- Función RPC para sincronizar hora real del servidor y expirar suscripciones vencidas automáticamente
CREATE OR REPLACE FUNCTION public.sync_server_time_and_expire()
RETURNS BIGINT AS $$
BEGIN
  UPDATE public.profiles
  SET approval_status = 'suscripcion_vencida'
  WHERE role = 'driver'
    AND approval_status = 'aprobado'
    AND subscription_end_date IS NOT NULL
    AND subscription_end_date < NOW();

  RETURN (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

-- Función administrativa segura para eliminar usuarios de auth.users y profiles
CREATE OR REPLACE FUNCTION public.delete_user_by_admin(target_user_id UUID)
RETURNS VOID AS $$
BEGIN
  IF public.is_admin() THEN
    DELETE FROM auth.users WHERE id = target_user_id;
  ELSE
    RAISE EXCEPTION 'No autorizado: se requiere rol admin';
  END IF;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, auth;

-- ============================================================================
-- HABILITAR ROW LEVEL SECURITY (RLS) Y POLÍTICAS
-- ============================================================================

ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.trips ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.driver_subscriptions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.app_config ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.subscription_payment_requests ENABLE ROW LEVEL SECURITY;

-- Reglas para Profiles (Bloqueado para usuarios anónimos no autenticados)
DROP POLICY IF EXISTS "Profiles viewable by everyone" ON public.profiles;
DROP POLICY IF EXISTS "Profiles viewable by authenticated users" ON public.profiles;
CREATE POLICY "Profiles viewable by authenticated users" ON public.profiles
  FOR SELECT USING (auth.uid() IS NOT NULL);

DROP POLICY IF EXISTS "Users can insert their own profile" ON public.profiles;
CREATE POLICY "Users can insert their own profile" ON public.profiles
  FOR INSERT WITH CHECK (auth.uid() = id);

DROP POLICY IF EXISTS "Users can update their own profile" ON public.profiles;
CREATE POLICY "Users can update their own profile" ON public.profiles
  FOR UPDATE USING (auth.uid() = id);

DROP POLICY IF EXISTS "Admins can update profiles" ON public.profiles;
CREATE POLICY "Admins can update profiles" ON public.profiles
  FOR UPDATE USING (public.is_admin());

DROP POLICY IF EXISTS "Admins can delete profiles" ON public.profiles;
CREATE POLICY "Admins can delete profiles" ON public.profiles
  FOR DELETE USING (public.is_admin());

-- Reglas para Driver Subscriptions (Eliminada la inserción directa por conductores)
DROP POLICY IF EXISTS "Subscriptions viewable by owner and admin" ON public.driver_subscriptions;
CREATE POLICY "Subscriptions viewable by owner and admin" ON public.driver_subscriptions
  FOR SELECT USING (auth.uid() = driver_id OR public.is_admin());

DROP POLICY IF EXISTS "Drivers can insert their own subscriptions" ON public.driver_subscriptions;
DROP POLICY IF EXISTS "Admins can insert subscriptions" ON public.driver_subscriptions;
CREATE POLICY "Admins can insert subscriptions" ON public.driver_subscriptions
  FOR INSERT WITH CHECK (public.is_admin());

DROP POLICY IF EXISTS "Admins can update subscriptions" ON public.driver_subscriptions;
CREATE POLICY "Admins can update subscriptions" ON public.driver_subscriptions
  FOR UPDATE USING (public.is_admin());

DROP POLICY IF EXISTS "Admins can delete subscriptions" ON public.driver_subscriptions;
CREATE POLICY "Admins can delete subscriptions" ON public.driver_subscriptions
  FOR DELETE USING (public.is_admin());

-- Reglas para App Config
DROP POLICY IF EXISTS "App config viewable by everyone" ON public.app_config;
CREATE POLICY "App config viewable by everyone" ON public.app_config
  FOR SELECT USING (true);

DROP POLICY IF EXISTS "Admins can manage app config" ON public.app_config;
CREATE POLICY "Admins can manage app config" ON public.app_config
  FOR ALL USING (public.is_admin()) WITH CHECK (public.is_admin());

-- Reglas para Subscription Payment Requests (Boletas notificadas por conductores)
DROP POLICY IF EXISTS "Drivers can view and insert own payment requests" ON public.subscription_payment_requests;
CREATE POLICY "Drivers can view and insert own payment requests" ON public.subscription_payment_requests
  FOR SELECT USING (auth.uid() = driver_id OR public.is_admin());

DROP POLICY IF EXISTS "Drivers can insert payment requests" ON public.subscription_payment_requests;
CREATE POLICY "Drivers can insert payment requests" ON public.subscription_payment_requests
  FOR INSERT WITH CHECK (auth.uid() = driver_id);

DROP POLICY IF EXISTS "Admins can update payment requests" ON public.subscription_payment_requests;
CREATE POLICY "Admins can update payment requests" ON public.subscription_payment_requests
  FOR UPDATE USING (public.is_admin());

DROP POLICY IF EXISTS "Admins can delete payment requests" ON public.subscription_payment_requests;
CREATE POLICY "Admins can delete payment requests" ON public.subscription_payment_requests
  FOR DELETE USING (public.is_admin());

-- Reglas para Trips
DROP POLICY IF EXISTS "Trips viewable by participants and admin" ON public.trips;
CREATE POLICY "Trips viewable by participants and admin" ON public.trips
  FOR SELECT USING (
    auth.uid() = passenger_id OR
    auth.uid() = driver_id OR
    public.is_admin()
  );

DROP POLICY IF EXISTS "Trips insertable by participants" ON public.trips;
CREATE POLICY "Trips insertable by participants" ON public.trips
  FOR INSERT WITH CHECK (
    auth.uid() = passenger_id OR
    auth.uid() = driver_id
  );

DROP POLICY IF EXISTS "Participants and admin can update trips" ON public.trips;
CREATE POLICY "Participants and admin can update trips" ON public.trips
  FOR UPDATE USING (
    auth.uid() = passenger_id OR
    auth.uid() = driver_id OR
    public.is_admin()
  );
