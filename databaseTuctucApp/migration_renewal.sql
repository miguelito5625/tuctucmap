-- Migración: Mejoras al sistema de renovaciones y solicitudes de suscripción

-- 1. Asegurar columnas de auditoría y flexibilidad en subscription_payment_requests
ALTER TABLE public.subscription_payment_requests 
  ALTER COLUMN reference_number DROP NOT NULL,
  ALTER COLUMN reference_number SET DEFAULT '',
  ADD COLUMN IF NOT EXISTS rejection_reason TEXT,
  ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS reviewed_by UUID REFERENCES public.profiles(id);

-- 2. Función RPC para que el Administrador apruebe una solicitud de renovación
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
  -- Verificar permisos de administrador
  IF NOT public.is_admin() THEN
    RAISE EXCEPTION 'Acceso denegado: solo administradores pueden aprobar solicitudes de renovación.';
  END IF;

  -- Obtener la solicitud
  SELECT * INTO v_req FROM public.subscription_payment_requests WHERE id = p_request_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Solicitud de renovación no encontrada.';
  END IF;

  IF v_req.status = 'aprobado' THEN
    RAISE EXCEPTION 'Esta solicitud ya fue aprobada previamente.';
  END IF;

  -- Obtener fecha de vencimiento actual del conductor
  SELECT subscription_end_date INTO v_current_end 
  FROM public.profiles 
  WHERE id = v_req.driver_id;

  -- Calcular nueva fecha de vencimiento:
  IF p_custom_end_date IS NOT NULL THEN
    v_new_end := p_custom_end_date;
  ELSE
    -- Renovación acumulativa justa: si aún le quedan días, sumar a partir de su vencimiento actual
    IF v_current_end IS NOT NULL AND v_current_end > NOW() THEN
      v_base_time := v_current_end;
    ELSE
      v_base_time := NOW();
    END IF;

    CASE v_req.plan_type
      WHEN 'mensual' THEN v_days_to_add := 30;
      WHEN 'quincenal' THEN v_days_to_add := 15;
      WHEN 'personalizado' THEN v_days_to_add := 7;
      ELSE v_days_to_add := 7; -- 'semanal'
    END CASE;

    v_new_end := v_base_time + (v_days_to_add || ' days')::INTERVAL;
  END IF;

  -- 1. Actualizar el perfil del conductor
  UPDATE public.profiles
  SET approval_status = 'aprobado',
      subscription_end_date = v_new_end
  WHERE id = v_req.driver_id;

  -- 2. Registrar en driver_subscriptions para auditoría histórica
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

  -- 3. Marcar la solicitud como aprobada
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

-- 3. Función RPC para que el Administrador rechace una solicitud de renovación
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
