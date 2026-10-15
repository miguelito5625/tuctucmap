-- db.sql para TucTuc Map en Supabase

-- 1. Crear tabla de perfiles para pasajeros y conductores
CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID REFERENCES auth.users(id) ON DELETE CASCADE PRIMARY KEY,
    email TEXT,
    name TEXT,
    phone TEXT,
    role TEXT CHECK (role IN ('passenger', 'driver', 'admin')),
    subscription_end_date TIMESTAMPTZ,
    approval_status TEXT DEFAULT 'pending', -- "pending" o "approved" para conductores
    plate_number TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 1.5 Crear tabla de historial de suscripciones para conductores
CREATE TABLE IF NOT EXISTS public.driver_subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE,
    start_date TIMESTAMPTZ DEFAULT NOW(),
    end_date TIMESTAMPTZ NOT NULL,
    plan_type TEXT CHECK (plan_type IN ('trial', 'semanal', 'quincenal', 'mensual')) DEFAULT 'semanal',
    amount_paid NUMERIC(10,2) DEFAULT 0.00,
    payment_method TEXT CHECK (payment_method IN ('efectivo', 'transferencia', 'deposito_bancario', 'gratis_prueba')) DEFAULT 'deposito_bancario',
    reference_number TEXT,
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
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Habilitar Row Level Security (RLS)
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.trips ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.driver_subscriptions ENABLE ROW LEVEL SECURITY;

-- Reglas para Profiles
CREATE POLICY "Profiles viewable by everyone" ON public.profiles FOR SELECT USING (true);
CREATE POLICY "Users can insert their own profile" ON public.profiles FOR INSERT WITH CHECK (auth.uid() = id);
CREATE POLICY "Users can update their own profile" ON public.profiles FOR UPDATE USING (auth.uid() = id);
CREATE POLICY "Admins can update profiles" ON public.profiles FOR UPDATE USING (
  (SELECT role FROM public.profiles WHERE id = auth.uid()) = 'admin'
);

-- Reglas para Driver Subscriptions
CREATE POLICY "Subscriptions viewable by owner and admin" ON public.driver_subscriptions 
  FOR SELECT USING (
    auth.uid() = driver_id OR 
    (SELECT role FROM public.profiles WHERE id = auth.uid()) = 'admin'
  );
CREATE POLICY "Drivers can insert their own subscriptions" ON public.driver_subscriptions FOR INSERT WITH CHECK (auth.uid() = driver_id);
CREATE POLICY "Admins can insert subscriptions" ON public.driver_subscriptions FOR INSERT WITH CHECK (
  (SELECT role FROM public.profiles WHERE id = auth.uid()) = 'admin'
);
CREATE POLICY "Admins can update subscriptions" ON public.driver_subscriptions FOR UPDATE USING (
  (SELECT role FROM public.profiles WHERE id = auth.uid()) = 'admin'
);
CREATE POLICY "Admins can delete subscriptions" ON public.driver_subscriptions FOR DELETE USING (
  (SELECT role FROM public.profiles WHERE id = auth.uid()) = 'admin'
);


-- Reglas para Trips
CREATE POLICY "Trips viewable by participants and admin" ON public.trips 
  FOR SELECT USING (
    auth.uid() = passenger_id OR 
    auth.uid() = driver_id OR 
    (SELECT role FROM public.profiles WHERE id = auth.uid()) = 'admin'
  );

CREATE POLICY "Trips insertable by participants" ON public.trips 
  FOR INSERT WITH CHECK (
    auth.uid() = passenger_id OR 
    auth.uid() = driver_id
  );

CREATE POLICY "Participants and admin can update trips" ON public.trips 
  FOR UPDATE USING (
    auth.uid() = passenger_id OR 
    auth.uid() = driver_id OR 
    (SELECT role FROM public.profiles WHERE id = auth.uid()) = 'admin'
  );

-- Función administrativa segura para eliminar usuarios de auth.users y profiles
CREATE OR REPLACE FUNCTION public.delete_user_by_admin(target_user_id UUID)
RETURNS VOID AS $$
BEGIN
  IF (SELECT role FROM public.profiles WHERE id = auth.uid()) = 'admin' THEN
    DELETE FROM auth.users WHERE id = target_user_id;
  ELSE
    RAISE EXCEPTION 'No autorizado: se requiere rol admin';
  END IF;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

