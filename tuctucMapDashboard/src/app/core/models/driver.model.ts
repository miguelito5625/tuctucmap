/**
 * Represents the possible approval states of a driver,
 * matching the values used in the tuctucMap Android app.
 */
export type DriverStatus = 'pendiente' | 'aprobado' | 'rechazado' | 'suscripcion_vencida';

export type SubscriptionPlan = 'trial' | 'semanal' | 'quincenal' | 'mensual' | 'personalizado';
export type PaymentMethod = 'efectivo' | 'transferencia' | 'deposito_bancario' | 'gratis_prueba';

export interface AppConfig {
  id: string;
  admin_whatsapp: string;
  admin_whatsapp_phone: string;
  bank_1_name: string;
  bank_1_account: string;
  banrural_account: string;
  bank_2_name: string;
  bank_2_account: string;
  bi_account: string;
  account_holder_name: string;
  currency_symbol: string;
  price_daily: number;
  price_weekly: number;
  price_semanal: number;
  price_quincenal: number;
  price_monthly: number;
  price_mensual: number;
  search_radius_km: number;
  max_search_radius_km: number;
  updated_at?: string;
}

export interface SubscriptionHistory {
  id: string;
  driver_id: string;
  driverName?: string;
  driverPlate?: string;
  start_date: number;
  end_date: number;
  created_at: number;
  status: 'Activo' | 'Expirado';
  plan_type?: SubscriptionPlan;
  amount_paid?: number;
  payment_method?: PaymentMethod;
  reference_number?: string;
}

export interface Driver {
  uid: string;
  nombre: string;
  email: string;
  telefono: string;
  placas: string;
  numeroUnidad?: string;
  estadoAprobacion: DriverStatus;
  fechaVencimientoSuscripcion?: number;
  suscripcionesCount?: number;
}

export interface PassengerProfile {
  id: string;
  name: string;
  email: string;
  phone: string;
  created_at: string;
  tripsCount?: number;
}

export interface LiveDriverLocation {
  driver_id: string;
  nombre: string;
  telefono: string;
  placas: string;
  numeroUnidad?: string;
  lat: number;
  lon: number;
  active: boolean;
  status?: string; // "disponible", "ocupado", "en_pausa"
  updated_at?: string;
}

export interface FinancialKPIs {
  totalRevenue: number;
  monthRevenue: number;
  expiringIn7Days: number;
  expiringAmountExpected: number;
  activeFleetCount: number;
}

export type RenewalRequestStatus = 'pendiente' | 'aprobado' | 'rechazado';

export interface SubscriptionPaymentRequest {
  id: string;
  driver_id: string;
  driverName?: string;
  driverPhone?: string;
  driverPlate?: string;
  driverUnitNumber?: string;
  plan_type: SubscriptionPlan;
  amount: number;
  payment_method: PaymentMethod;
  reference_number: string;
  status: RenewalRequestStatus;
  rejection_reason?: string | null;
  reviewed_at?: number | null;
  reviewed_by?: string | null;
  created_at: number;
}

