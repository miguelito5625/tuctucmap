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
    unit_number TEXT DEFAULT NULL, -- Número de TucTuc / Padrón visible (ej. "TucTuc #14")
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
    admin_whatsapp TEXT NOT NULL DEFAULT '50230000000',
    banrural_account TEXT NOT NULL DEFAULT '3402-0987-65',
    bank_1_name TEXT NOT NULL DEFAULT 'Banrural (Monetaria)',
    bank_1_account TEXT NOT NULL DEFAULT '3402-0987-65',
    bi_account TEXT NOT NULL DEFAULT '027-019842-1',
    bank_2_name TEXT NOT NULL DEFAULT 'Banco Industrial (Ahorro)',
    bank_2_account TEXT NOT NULL DEFAULT '027-019842-1',
    account_holder_name TEXT NOT NULL DEFAULT 'Administración TucTuc Map',
    currency_symbol TEXT NOT NULL DEFAULT 'Q',
    price_daily NUMERIC(10,2) NOT NULL DEFAULT 10.00,
    price_semanal NUMERIC(10,2) NOT NULL DEFAULT 25.00,
    price_weekly NUMERIC(10,2) NOT NULL DEFAULT 25.00,
    price_quincenal NUMERIC(10,2) NOT NULL DEFAULT 45.00,
    price_mensual NUMERIC(10,2) NOT NULL DEFAULT 80.00,
    price_monthly NUMERIC(10,2) NOT NULL DEFAULT 80.00,
    max_search_radius_km NUMERIC(5,2) NOT NULL DEFAULT 5.00,
    search_radius_km NUMERIC(5,2) NOT NULL DEFAULT 5.00,
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Sincronizar trigger para mantener consistencia bidireccional entre nombres legacy y canónicos
CREATE OR REPLACE FUNCTION public.sync_app_config_fields()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.admin_whatsapp IS NOT NULL AND (OLD IS NULL OR OLD.admin_whatsapp IS DISTINCT FROM NEW.admin_whatsapp OR NEW.admin_whatsapp_phone IS NULL) THEN
        NEW.admin_whatsapp_phone := NEW.admin_whatsapp;
    ELSIF NEW.admin_whatsapp_phone IS NOT NULL THEN
        NEW.admin_whatsapp := NEW.admin_whatsapp_phone;
    END IF;

    IF NEW.bank_1_account IS NOT NULL AND (OLD IS NULL OR OLD.bank_1_account IS DISTINCT FROM NEW.bank_1_account OR NEW.banrural_account IS NULL) THEN
        NEW.banrural_account := NEW.bank_1_account;
    ELSIF NEW.banrural_account IS NOT NULL THEN
        NEW.bank_1_account := NEW.banrural_account;
    END IF;

    IF NEW.bank_2_account IS NOT NULL AND (OLD IS NULL OR OLD.bank_2_account IS DISTINCT FROM NEW.bank_2_account OR NEW.bi_account IS NULL) THEN
        NEW.bi_account := NEW.bank_2_account;
    ELSIF NEW.bi_account IS NOT NULL THEN
        NEW.bank_2_account := NEW.bi_account;
    END IF;

    IF NEW.price_weekly IS NOT NULL AND (OLD IS NULL OR OLD.price_weekly IS DISTINCT FROM NEW.price_weekly OR NEW.price_semanal IS NULL) THEN
        NEW.price_semanal := NEW.price_weekly;
    ELSIF NEW.price_semanal IS NOT NULL THEN
        NEW.price_weekly := NEW.price_semanal;
    END IF;

    IF NEW.price_monthly IS NOT NULL AND (OLD IS NULL OR OLD.price_monthly IS DISTINCT FROM NEW.price_monthly OR NEW.price_mensual IS NULL) THEN
        NEW.price_mensual := NEW.price_monthly;
    ELSIF NEW.price_mensual IS NOT NULL THEN
        NEW.price_monthly := NEW.price_mensual;
    END IF;

    IF NEW.search_radius_km IS NOT NULL AND (OLD IS NULL OR OLD.search_radius_km IS DISTINCT FROM NEW.search_radius_km OR NEW.max_search_radius_km IS NULL) THEN
        NEW.max_search_radius_km := NEW.search_radius_km;
    ELSIF NEW.max_search_radius_km IS NOT NULL THEN
        NEW.search_radius_km := NEW.max_search_radius_km;
    END IF;

    NEW.updated_at := NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sync_app_config_fields ON public.app_config;
CREATE TRIGGER trg_sync_app_config_fields
BEFORE INSERT OR UPDATE ON public.app_config
FOR EACH ROW
EXECUTE FUNCTION public.sync_app_config_fields();

INSERT INTO public.app_config (
    id, admin_whatsapp_phone, admin_whatsapp,
    banrural_account, bank_1_name, bank_1_account,
    bi_account, bank_2_name, bank_2_account,
    account_holder_name, currency_symbol,
    price_daily, price_semanal, price_weekly, price_quincenal, price_mensual, price_monthly,
    max_search_radius_km, search_radius_km
)
VALUES (
    'default', '50230000000', '50230000000',
    '3402-0987-65', 'Banrural (Monetaria)', '3402-0987-65',
    '027-019842-1', 'Banco Industrial (Ahorro)', '027-019842-1',
    'Administración TucTuc Map', 'Q',
    10.00, 25.00, 25.00, 45.00, 80.00, 80.00,
    5.00, 5.00
)
ON CONFLICT (id) DO NOTHING;

-- 2.5 Crear tabla de notificaciones de boletas de pago enviadas por conductores
CREATE TABLE IF NOT EXISTS public.subscription_payment_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE NOT NULL,
    plan_type TEXT CHECK (plan_type IN ('semanal', 'quincenal', 'mensual', 'personalizado')) DEFAULT 'semanal',
    amount NUMERIC(10,2) NOT NULL DEFAULT 25.00,
    payment_method TEXT CHECK (payment_method IN ('efectivo', 'transferencia', 'deposito_bancario')) DEFAULT 'deposito_bancario',
    reference_number TEXT DEFAULT '',
    status TEXT CHECK (status IN ('pendiente', 'aprobado', 'rechazado')) DEFAULT 'pendiente',
    rejection_reason TEXT,
    reviewed_at TIMESTAMPTZ,
    reviewed_by UUID REFERENCES public.profiles(id),
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
CREATE INDEX IF NOT EXISTS idx_trips_route_points_cleanup ON public.trips(created_at) WHERE route_points IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_profiles_active_driver_sub ON public.profiles(subscription_end_date) WHERE role = 'driver' AND approval_status = 'aprobado';

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

-- Función para purgar polilíneas GPS antiguas (> 30 días) conservando tarifas, distancias y registros históricos
CREATE OR REPLACE FUNCTION public.cleanup_old_trip_route_points()
RETURNS INT AS $$
DECLARE
  v_cleaned INT;
BEGIN
  UPDATE public.trips
  SET route_points = NULL
  WHERE created_at < NOW() - INTERVAL '30 days'
    AND route_points IS NOT NULL;
  GET DIAGNOSTICS v_cleaned = ROW_COUNT;
  RETURN v_cleaned;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

-- Función RPC ultraligera de solo lectura para sincronizar la hora real del servidor (0 lecturas/escrituras en tablas)
CREATE OR REPLACE FUNCTION public.sync_server_time_and_expire()
RETURNS BIGINT AS $$
  SELECT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT;
$$ LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public;

-- Tareas programadas en segundo plano con pg_cron (escalable a 1,000+ conductores sin afectar latencia de clientes)
CREATE EXTENSION IF NOT EXISTS pg_cron;

SELECT cron.schedule(
  'tuctuc-cleanup-old-route-points',
  '0 9 * * *', -- 03:00 AM hora de Guatemala (UTC-6)
  'SELECT public.cleanup_old_trip_route_points();'
);

SELECT cron.schedule(
  'tuctuc-expire-driver-subscriptions',
  '0 * * * *', -- Cada hora en el minuto 0
  $$UPDATE public.profiles SET approval_status = 'suscripcion_vencida' WHERE role = 'driver' AND approval_status = 'aprobado' AND subscription_end_date IS NOT NULL AND subscription_end_date < NOW();$$
);

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

-- Función RPC para que el Administrador apruebe una solicitud de renovación y extienda vigencia automáticamente
CREATE OR REPLACE FUNCTION public.approve_subscription_payment_request(
  p_request_id UUID,
  p_custom_end_date TIMESTAMPTZ DEFAULT NULL
)
RETURNS JSONB AS $$
DECLARE
  v_req RECORD;
  v_current_end TIMESTAMPTZ;
  v_base_time TIMESTAMPTZ;
  v_days_to_add INT;
  v_new_end TIMESTAMPTZ;
BEGIN
  IF NOT public.is_admin() THEN
    RAISE EXCEPTION 'Acceso denegado: solo administradores pueden aprobar solicitudes de renovación.';
  END IF;

  SELECT * INTO v_req FROM public.subscription_payment_requests WHERE id = p_request_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Solicitud de renovación no encontrada.';
  END IF;

  IF v_req.status = 'aprobado' THEN
    RAISE EXCEPTION 'Esta solicitud ya fue aprobada previamente.';
  END IF;

  SELECT subscription_end_date INTO v_current_end 
  FROM public.profiles 
  WHERE id = v_req.driver_id;

  IF p_custom_end_date IS NOT NULL THEN
    v_new_end := p_custom_end_date;
  ELSE
    IF v_current_end IS NOT NULL AND v_current_end > NOW() THEN
      v_base_time := v_current_end;
    ELSE
      v_base_time := NOW();
    END IF;

    CASE v_req.plan_type
      WHEN 'mensual' THEN v_days_to_add := 30;
      WHEN 'quincenal' THEN v_days_to_add := 15;
      WHEN 'personalizado' THEN v_days_to_add := 7;
      ELSE v_days_to_add := 7;
    END CASE;

    v_new_end := v_base_time + (v_days_to_add || ' days')::INTERVAL;
  END IF;

  UPDATE public.profiles
  SET approval_status = 'aprobado',
      subscription_end_date = v_new_end
  WHERE id = v_req.driver_id;

  INSERT INTO public.driver_subscriptions (
    driver_id,
    start_date,
    end_date,
    plan_type,
    amount_paid,
    payment_method,
    reference_number
  ) VALUES (
    v_req.driver_id,
    NOW(),
    v_new_end,
    v_req.plan_type,
    v_req.amount,
    v_req.payment_method,
    COALESCE(v_req.reference_number, '')
  );

  UPDATE public.subscription_payment_requests
  SET status = 'aprobado',
      reviewed_at = NOW(),
      reviewed_by = auth.uid()
  WHERE id = p_request_id;

  RETURN jsonb_build_object(
    'success', true,
    'new_end_date', v_new_end,
    'driver_id', v_req.driver_id
  );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

-- Función RPC para que el Administrador rechace una solicitud de renovación
CREATE OR REPLACE FUNCTION public.reject_subscription_payment_request(
  p_request_id UUID,
  p_rejection_reason TEXT DEFAULT NULL
)
RETURNS JSONB AS $$
DECLARE
  v_req RECORD;
BEGIN
  IF NOT public.is_admin() THEN
    RAISE EXCEPTION 'Acceso denegado: solo administradores pueden rechazar solicitudes de renovación.';
  END IF;

  SELECT * INTO v_req FROM public.subscription_payment_requests WHERE id = p_request_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Solicitud no encontrada.';
  END IF;

  UPDATE public.subscription_payment_requests
  SET status = 'rechazado',
      rejection_reason = p_rejection_reason,
      reviewed_at = NOW(),
      reviewed_by = auth.uid()
  WHERE id = p_request_id;

  RETURN jsonb_build_object('success', true, 'driver_id', v_req.driver_id);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

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

-- Habilitar Supabase Realtime para tablas requeridas en el Dashboard
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_publication_tables 
    WHERE pubname = 'supabase_realtime' AND tablename = 'subscription_payment_requests'
  ) THEN
    ALTER PUBLICATION supabase_realtime ADD TABLE public.subscription_payment_requests;
  END IF;
END $$;
