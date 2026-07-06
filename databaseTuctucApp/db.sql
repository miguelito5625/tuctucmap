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
    created_at TIMESTAMPTZ DEFAULT NOW()
);


-- 3. Crear tabla de viajes
CREATE TABLE IF NOT EXISTS public.trips (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    passenger_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE,
    driver_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE,
    status TEXT CHECK (status IN ('requested', 'accepted', 'completed', 'cancelled')) DEFAULT 'requested',
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
CREATE POLICY "Subscriptions viewable by everyone" ON public.driver_subscriptions FOR SELECT USING (true);
CREATE POLICY "Drivers can insert their own subscriptions" ON public.driver_subscriptions FOR INSERT WITH CHECK (auth.uid() = driver_id);
CREATE POLICY "Admins can insert subscriptions" ON public.driver_subscriptions FOR INSERT WITH CHECK (
  (SELECT role FROM public.profiles WHERE id = auth.uid()) = 'admin'
);


-- Reglas para Trips
CREATE POLICY "Trips viewable by participants" ON public.trips FOR SELECT USING (auth.uid() = passenger_id OR auth.uid() = driver_id);
CREATE POLICY "Passengers can request trips" ON public.trips FOR INSERT WITH CHECK (auth.uid() = passenger_id);
CREATE POLICY "Participants can update trips" ON public.trips FOR UPDATE USING (auth.uid() = passenger_id OR auth.uid() = driver_id);
-- Permitir select genérico para el dashboard usando el anon key si se quiere o roles especiales.
-- Para simplicidad del dashboard asumiendo que usa el anon key para ver viajes (debes cambiar esto a RLS más estricto después):
CREATE POLICY "Dashboard can view all trips" ON public.trips FOR SELECT USING (true);
