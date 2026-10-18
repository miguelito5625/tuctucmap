-- Migración para soporte completo de configuración comercial editable (CONFIGURACION_Y_SECRETOS_REQUERIDOS.md Punto 2)

ALTER TABLE public.app_config 
  ADD COLUMN IF NOT EXISTS admin_whatsapp TEXT,
  ADD COLUMN IF NOT EXISTS bank_1_name TEXT DEFAULT 'Banrural (Monetaria)',
  ADD COLUMN IF NOT EXISTS bank_1_account TEXT DEFAULT '3402-0987-65',
  ADD COLUMN IF NOT EXISTS bank_2_name TEXT DEFAULT 'Banco Industrial (Ahorro)',
  ADD COLUMN IF NOT EXISTS bank_2_account TEXT DEFAULT '027-019842-1',
  ADD COLUMN IF NOT EXISTS account_holder_name TEXT DEFAULT 'Administración TucTuc Map',
  ADD COLUMN IF NOT EXISTS currency_symbol TEXT DEFAULT 'Q',
  ADD COLUMN IF NOT EXISTS price_daily NUMERIC(10,2) DEFAULT 10.00,
  ADD COLUMN IF NOT EXISTS price_weekly NUMERIC(10,2) DEFAULT 25.00,
  ADD COLUMN IF NOT EXISTS price_monthly NUMERIC(10,2) DEFAULT 80.00,
  ADD COLUMN IF NOT EXISTS search_radius_km NUMERIC(5,2) DEFAULT 5.00;

-- Sincronizar trigger para mantener consistencia bidireccional entre nombres legacy y canónicos
CREATE OR REPLACE FUNCTION public.sync_app_config_fields()
RETURNS TRIGGER AS $$
BEGIN
    -- admin_whatsapp <-> admin_whatsapp_phone
    IF NEW.admin_whatsapp IS NOT NULL AND (OLD IS NULL OR OLD.admin_whatsapp IS DISTINCT FROM NEW.admin_whatsapp OR NEW.admin_whatsapp_phone IS NULL) THEN
        NEW.admin_whatsapp_phone := NEW.admin_whatsapp;
    ELSIF NEW.admin_whatsapp_phone IS NOT NULL THEN
        NEW.admin_whatsapp := NEW.admin_whatsapp_phone;
    END IF;

    -- bank_1_account <-> banrural_account
    IF NEW.bank_1_account IS NOT NULL AND (OLD IS NULL OR OLD.bank_1_account IS DISTINCT FROM NEW.bank_1_account OR NEW.banrural_account IS NULL) THEN
        NEW.banrural_account := NEW.bank_1_account;
    ELSIF NEW.banrural_account IS NOT NULL THEN
        NEW.bank_1_account := NEW.banrural_account;
    END IF;

    -- bank_2_account <-> bi_account
    IF NEW.bank_2_account IS NOT NULL AND (OLD IS NULL OR OLD.bank_2_account IS DISTINCT FROM NEW.bank_2_account OR NEW.bi_account IS NULL) THEN
        NEW.bi_account := NEW.bank_2_account;
    ELSIF NEW.bi_account IS NOT NULL THEN
        NEW.bank_2_account := NEW.bi_account;
    END IF;

    -- price_weekly <-> price_semanal
    IF NEW.price_weekly IS NOT NULL AND (OLD IS NULL OR OLD.price_weekly IS DISTINCT FROM NEW.price_weekly OR NEW.price_semanal IS NULL) THEN
        NEW.price_semanal := NEW.price_weekly;
    ELSIF NEW.price_semanal IS NOT NULL THEN
        NEW.price_weekly := NEW.price_semanal;
    END IF;

    -- price_monthly <-> price_mensual
    IF NEW.price_monthly IS NOT NULL AND (OLD IS NULL OR OLD.price_monthly IS DISTINCT FROM NEW.price_monthly OR NEW.price_mensual IS NULL) THEN
        NEW.price_mensual := NEW.price_monthly;
    ELSIF NEW.price_mensual IS NOT NULL THEN
        NEW.price_monthly := NEW.price_mensual;
    END IF;

    -- search_radius_km <-> max_search_radius_km
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

-- Inicializar valores iniciales con los datos existentes
UPDATE public.app_config 
SET 
  admin_whatsapp = admin_whatsapp_phone,
  bank_1_name = COALESCE(bank_1_name, 'Banrural (Monetaria)'),
  bank_1_account = banrural_account,
  bank_2_name = COALESCE(bank_2_name, 'Banco Industrial (Ahorro)'),
  bank_2_account = bi_account,
  account_holder_name = COALESCE(account_holder_name, 'Administración TucTuc Map'),
  currency_symbol = COALESCE(currency_symbol, 'Q'),
  price_daily = COALESCE(price_daily, 10.00),
  price_weekly = price_semanal,
  price_monthly = price_mensual,
  search_radius_km = max_search_radius_km
WHERE id = 'default';
