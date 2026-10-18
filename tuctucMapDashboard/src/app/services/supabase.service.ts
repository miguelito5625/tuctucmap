import { Injectable } from '@angular/core';
import { createClient, SupabaseClient } from '@supabase/supabase-js';
import { environment } from '../../environments/environment';
import { Observable, BehaviorSubject } from 'rxjs';
import {
  AppConfig,
  Driver,
  SubscriptionHistory,
  SubscriptionPlan,
  PaymentMethod,
  FinancialKPIs,
  PassengerProfile,
  LiveDriverLocation,
  SubscriptionPaymentRequest
} from '../core/models/driver.model';

@Injectable({
  providedIn: 'root'
})
export class SupabaseService {
  private supabase: SupabaseClient;
  private cachedConfig: AppConfig = {
    id: 'default',
    admin_whatsapp_phone: '50230000000',
    banrural_account: '3402-0987-65',
    bi_account: '027-019842-1',
    price_semanal: 25.00,
    price_quincenal: 45.00,
    price_mensual: 80.00,
    max_search_radius_km: 5.00
  };

  constructor() {
    this.supabase = createClient(environment.supabase.url, environment.supabase.key);
  }

  get currentUser() {
    return this.supabase.auth.getUser();
  }

  get session() {
    return this.supabase.auth.getSession();
  }

  async signIn(email: string, password: string) {
    return this.supabase.auth.signInWithPassword({ email, password });
  }

  async signOut() {
    return this.supabase.auth.signOut();
  }

  async getAppConfig(): Promise<AppConfig> {
    try {
      const { data, error } = await this.supabase
        .from('app_config')
        .select('*')
        .eq('id', 'default')
        .single();
      if (!error && data) {
        this.cachedConfig = {
          id: data.id,
          admin_whatsapp_phone: data.admin_whatsapp_phone || '50230000000',
          banrural_account: data.banrural_account || '3402-0987-65',
          bi_account: data.bi_account || '027-019842-1',
          price_semanal: Number(data.price_semanal) || 25.00,
          price_quincenal: Number(data.price_quincenal) || 45.00,
          price_mensual: Number(data.price_mensual) || 80.00,
          max_search_radius_km: Number(data.max_search_radius_km) || 5.00
        };
      }
    } catch {
      // Fallback a cachedConfig
    }
    return this.cachedConfig;
  }

  async updateAppConfig(config: Partial<AppConfig>): Promise<void> {
    const { error } = await this.supabase
      .from('app_config')
      .update({ ...config, updated_at: new Date().toISOString() })
      .eq('id', 'default');
    if (error) throw error;
    await this.getAppConfig();
  }

  async getCurrentUserProfile() {
    const { data: { user } } = await this.supabase.auth.getUser();
    if (!user) return null;
    const { data } = await this.supabase
      .from('profiles')
      .select('*')
      .eq('id', user.id)
      .single();
    return data;
  }

  async updateAdminName(newName: string) {
    const { data: { user } } = await this.supabase.auth.getUser();
    if (!user) throw new Error('No user logged in');
    
    const { error } = await this.supabase
      .from('profiles')
      .update({ name: newName })
      .eq('id', user.id);
      
    if (error) throw error;
  }

  async updateAdminPassword(newPassword: string) {
    const { error } = await this.supabase.auth.updateUser({
      password: newPassword
    });
    if (error) throw error;
  }

  private driversSubject = new BehaviorSubject<Driver[]>([]);
  private channel: any;

  getDrivers(): Observable<Driver[]> {
    this.fetchDrivers();

    if (!this.channel) {
      this.channel = this.supabase
        .channel('public:profiles')
        .on('postgres_changes', { event: '*', schema: 'public', table: 'profiles' }, () => {
          this.fetchDrivers();
        })
        .subscribe();
    }

    return this.driversSubject.asObservable();
  }

  private async fetchDrivers(): Promise<void> {
    try {
      await this.supabase.rpc('sync_server_time_and_expire');
    } catch {
      // Ignorar si falla silenciosamente
    }

    const { data, error } = await this.supabase
      .from('profiles')
      .select('*, driver_subscriptions(count)')
      .eq('role', 'driver');
    
    if (!error && data) {
      this.driversSubject.next(this.mapProfilesToDrivers(data));
    }
  }

  private mapProfilesToDrivers(data: any[]): Driver[] {
    return data.map(item => ({
      uid: item.id,
      nombre: item.name || '',
      email: item.email || '',
      telefono: item.phone || '',
      placas: item.plate_number || '',
      numeroUnidad: item.unit_number || '',
      estadoAprobacion: item.approval_status,
      fechaVencimientoSuscripcion: item.subscription_end_date ? new Date(item.subscription_end_date).getTime() : 0,
      suscripcionesCount: Array.isArray(item.driver_subscriptions) ? (item.driver_subscriptions[0]?.count ?? item.driver_subscriptions.length) : 0
    }));
  }

  async getAllSubscriptions(): Promise<SubscriptionHistory[]> {
    const { data, error } = await this.supabase
      .from('driver_subscriptions')
      .select('*, profiles(name, plate_number)')
      .order('created_at', { ascending: false });

    if (error || !data) return [];
    return this.mapToSubscriptionHistory(data);
  }

  async getDriverSubscriptions(driverId: string): Promise<SubscriptionHistory[]> {
    const { data, error } = await this.supabase
      .from('driver_subscriptions')
      .select('*, profiles(name, plate_number)')
      .eq('driver_id', driverId)
      .order('created_at', { ascending: false });

    if (error || !data) return [];
    return this.mapToSubscriptionHistory(data);
  }

  private mapToSubscriptionHistory(data: any[]): SubscriptionHistory[] {
    const now = Date.now();
    return data.map(item => {
      const end = new Date(item.end_date).getTime();
      return {
        id: item.id,
        driver_id: item.driver_id,
        driverName: item.profiles?.name || 'Desconocido',
        driverPlate: item.profiles?.plate_number || '',
        start_date: new Date(item.start_date).getTime(),
        end_date: end,
        created_at: new Date(item.created_at).getTime(),
        status: end >= now ? 'Activo' : 'Expirado',
        plan_type: item.plan_type || 'semanal',
        amount_paid: Number(item.amount_paid) || 0,
        payment_method: item.payment_method || 'deposito_bancario',
        reference_number: item.reference_number || ''
      };
    });
  }

  async getFinancialStats(): Promise<FinancialKPIs> {
    const cfg = await this.getAppConfig();
    const { data: subs } = await this.supabase
      .from('driver_subscriptions')
      .select('amount_paid, created_at, end_date');

    const { data: approvedDrivers } = await this.supabase
      .from('profiles')
      .select('id, subscription_end_date')
      .eq('role', 'driver')
      .eq('approval_status', 'aprobado');

    const now = Date.now();
    const sevenDaysLater = now + (7 * 24 * 60 * 60 * 1000);
    const currentMonth = new Date().getMonth();
    const currentYear = new Date().getFullYear();

    let totalRevenue = 0;
    let monthRevenue = 0;

    (subs || []).forEach(s => {
      const amt = Number(s.amount_paid) || 0;
      totalRevenue += amt;
      const d = new Date(s.created_at);
      if (d.getMonth() === currentMonth && d.getFullYear() === currentYear) {
        monthRevenue += amt;
      }
    });

    let expiringIn7Days = 0;
    let activeFleetCount = 0;

    (approvedDrivers || []).forEach(d => {
      if (d.subscription_end_date) {
        const exp = new Date(d.subscription_end_date).getTime();
        if (exp >= now) {
          activeFleetCount++;
          if (exp <= sevenDaysLater) {
            expiringIn7Days++;
          }
        }
      }
    });

    return {
      totalRevenue,
      monthRevenue,
      expiringIn7Days,
      expiringAmountExpected: expiringIn7Days * cfg.price_semanal,
      activeFleetCount
    };
  }

  async getPassengers(): Promise<PassengerProfile[]> {
    const { data: profiles, error } = await this.supabase
      .from('profiles')
      .select('*')
      .eq('role', 'passenger')
      .order('created_at', { ascending: false });

    if (error || !profiles) return [];

    const { data: trips } = await this.supabase
      .from('trips')
      .select('passenger_id');

    const tripCounts: Record<string, number> = {};
    (trips || []).forEach(t => {
      if (t.passenger_id) {
        tripCounts[t.passenger_id] = (tripCounts[t.passenger_id] || 0) + 1;
      }
    });

    return profiles.map(p => ({
      id: p.id,
      name: p.name || 'Sin nombre',
      email: p.email || 'Sin correo',
      phone: p.phone || 'Sin teléfono',
      created_at: p.created_at,
      tripsCount: tripCounts[p.id] || 0
    }));
  }

  async getLiveDrivers(): Promise<LiveDriverLocation[]> {
    try {
      const baseUrl = (environment.firebase?.databaseUrl || 'https://tuctuc-map-default-rtdb.firebaseio.com').replace(/\/+$/, '');
      const res = await fetch(`${baseUrl}/driver_locations.json`);
      const data = await res.json();
      if (!data) return [];
      const now = Date.now();
      const maxAgeMs = 180_000; // TTL de 3 minutos igual que en la app Android
      return Object.values(data).filter((d: any) => {
        if (!d || !d.active) return false;
        const updatedMs = Number(d.updated_at || 0);
        if (updatedMs > 0 && now - updatedMs > maxAgeMs) {
          return false;
        }
        return true;
      }) as LiveDriverLocation[];
    } catch (e) {
      console.error('Error fetching live drivers from Firebase:', e);
      return [];
    }
  }

  async getCompletedTrips(): Promise<any[]> {
    const { data, error } = await this.supabase
        .from('trips')
        .select('*')
        .order('created_at', { ascending: false });

    if (error || !data) return [];
    
    const profilesResult = await this.supabase.from('profiles').select('id, name');
    const profiles = profilesResult.data || [];
    const profileMap = new Map(profiles.map(p => [p.id, p.name]));

    return data.map(trip => {
      let routePoints = trip.route_points;
      if (typeof routePoints === 'string') {
        try {
          routePoints = JSON.parse(routePoints);
        } catch {
          routePoints = null;
        }
      }

      const directPassengerName = trip.passenger_name?.trim();
      const mappedPassengerName = trip.passenger_id ? profileMap.get(trip.passenger_id) : null;
      const passengerName = directPassengerName || mappedPassengerName || 'Pasajero (Invitado)';
      const isRegistered = Boolean(trip.passenger_id);

      return {
        id: trip.id,
        passengerName,
        passengerPhone: trip.passenger_phone || '',
        isRegistered,
        driverName: profileMap.get(trip.driver_id) || 'Torito',
        status: trip.status,
        message: trip.message || 'Sin mensaje',
        fare: trip.fare != null ? Number(trip.fare) : null,
        distance_km: trip.distance_km != null ? Number(trip.distance_km) : null,
        route_points: Array.isArray(routePoints) ? routePoints : null,
        passenger_lat: trip.passenger_lat,
        passenger_lon: trip.passenger_lon,
        created_at: new Date(trip.created_at).getTime()
      };
    });
  }

  async approveDriver(uid: string): Promise<void> {
    const { error } = await this.supabase.from('profiles').update({ approval_status: 'aprobado' }).eq('id', uid);
    if (error) throw error;
    await this.fetchDrivers();
  }

  async rejectDriver(uid: string): Promise<void> {
    const { error } = await this.supabase.from('profiles').update({ approval_status: 'rechazado' }).eq('id', uid);
    if (error) throw error;
    await this.fetchDrivers();
  }

  async deleteDriver(uid: string): Promise<void> {
    try {
      const { error: rpcError } = await this.supabase.rpc('delete_user_by_admin', { target_user_id: uid });
      if (rpcError) {
        console.warn('RPC delete_user_by_admin fallo, aplicando fallback en profiles:', rpcError);
        const { error } = await this.supabase.from('profiles').delete().eq('id', uid);
        if (error) throw error;
      }
    } catch {
      const { error } = await this.supabase.from('profiles').delete().eq('id', uid);
      if (error) throw error;
    }
    await this.fetchDrivers();
  }

  async renewSubscription(
    uid: string,
    planType: SubscriptionPlan = 'semanal',
    amountPaid: number = 25.00,
    paymentMethod: PaymentMethod = 'deposito_bancario',
    referenceNumber: string = ''
  ): Promise<void> {
    const now = new Date();
    let baseTimeMs = now.getTime();

    // Si el conductor aún tiene días vigentes, sumar los nuevos días a su fecha actual de vencimiento
    const { data: currentProfile } = await this.supabase
      .from('profiles')
      .select('subscription_end_date')
      .eq('id', uid)
      .single();

    if (currentProfile?.subscription_end_date) {
      const existingEndMs = new Date(currentProfile.subscription_end_date).getTime();
      if (existingEndMs > baseTimeMs) {
        baseTimeMs = existingEndMs;
      }
    }

    const days = planType === 'mensual' ? 30 : (planType === 'quincenal' ? 15 : 7);
    const end = new Date(baseTimeMs + (days * 24 * 60 * 60 * 1000));
    
    const { error: profileError } = await this.supabase.from('profiles').update({ 
      approval_status: 'aprobado',
      subscription_end_date: end.toISOString()
    }).eq('id', uid);
    if (profileError) throw profileError;

    const { error: subError } = await this.supabase.from('driver_subscriptions').insert({
      driver_id: uid,
      start_date: now.toISOString(),
      end_date: end.toISOString(),
      plan_type: planType,
      amount_paid: amountPaid,
      payment_method: paymentMethod,
      reference_number: referenceNumber
    });
    if (subError) throw subError;
    await this.fetchDrivers();
  }

  async setSubscriptionExpiry(
    uid: string,
    timestamp: number,
    planType: SubscriptionPlan = 'semanal',
    amountPaid: number = 25.00,
    paymentMethod: PaymentMethod = 'deposito_bancario',
    referenceNumber: string = ''
  ): Promise<void> {
    const end = new Date(timestamp);
    const start = new Date();
    const { error: profileError } = await this.supabase.from('profiles').update({ 
      approval_status: 'aprobado',
      subscription_end_date: end.toISOString() 
    }).eq('id', uid);
    if (profileError) throw profileError;

    const { error: subError } = await this.supabase.from('driver_subscriptions').insert({
      driver_id: uid,
      start_date: start.toISOString(),
      end_date: end.toISOString(),
      plan_type: planType,
      amount_paid: amountPaid,
      payment_method: paymentMethod,
      reference_number: referenceNumber
    });
    if (subError) throw subError;
    await this.fetchDrivers();
  }

  // ── Solicitudes de Renovación (subscription_payment_requests) ───────────

  async getPaymentRequests(): Promise<SubscriptionPaymentRequest[]> {
    const { data, error } = await this.supabase
      .from('subscription_payment_requests')
      .select('*, profiles:driver_id(name, phone, plate_number, unit_number)')
      .order('created_at', { ascending: false });

    if (error || !data) {
      console.error('Error al obtener solicitudes de renovación:', error);
      return [];
    }

    return data.map((item: any) => ({
      id: item.id,
      driver_id: item.driver_id,
      driverName: item.profiles?.name || 'Conductor',
      driverPhone: item.profiles?.phone || '',
      driverPlate: item.profiles?.plate_number || '',
      driverUnitNumber: item.profiles?.unit_number || '',
      plan_type: item.plan_type || 'semanal',
      amount: Number(item.amount) || 0,
      payment_method: item.payment_method || 'deposito_bancario',
      reference_number: item.reference_number || '',
      status: item.status || 'pendiente',
      rejection_reason: item.rejection_reason || null,
      reviewed_at: item.reviewed_at ? new Date(item.reviewed_at).getTime() : null,
      reviewed_by: item.reviewed_by || null,
      created_at: new Date(item.created_at).getTime()
    }));
  }

  async approvePaymentRequest(requestId: string, customEndDate?: Date): Promise<void> {
    const params: any = { p_request_id: requestId };
    if (customEndDate) {
      params.p_custom_end_date = customEndDate.toISOString();
    }
    const { data, error } = await this.supabase.rpc('approve_subscription_payment_request', params);
    if (error) {
      console.error('Error al aprobar solicitud de renovación:', error);
      throw error;
    }
    await this.fetchDrivers();
  }

  async rejectPaymentRequest(requestId: string, rejectionReason?: string): Promise<void> {
    const { data, error } = await this.supabase.rpc('reject_subscription_payment_request', {
      p_request_id: requestId,
      p_rejection_reason: rejectionReason || null
    });
    if (error) {
      console.error('Error al rechazar solicitud de renovación:', error);
      throw error;
    }
    await this.fetchDrivers();
  }

  async getPendingPaymentRequestsCount(): Promise<number> {
    try {
      const { count, error } = await this.supabase
        .from('subscription_payment_requests')
        .select('*', { count: 'exact', head: true })
        .eq('status', 'pendiente');
      if (error) return 0;
      return count || 0;
    } catch {
      return 0;
    }
  }
}

