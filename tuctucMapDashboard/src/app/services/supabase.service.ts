import { Injectable } from '@angular/core';
import { createClient, SupabaseClient } from '@supabase/supabase-js';
import { environment } from '../../environments/environment';
import { Observable, BehaviorSubject } from 'rxjs';
import {
  Driver,
  DriverStatus,
  SubscriptionHistory,
  SubscriptionPlan,
  PaymentMethod,
  FinancialKPIs,
  PassengerProfile,
  LiveDriverLocation
} from '../core/models/driver.model';

@Injectable({
  providedIn: 'root'
})
export class SupabaseService {
  private supabase: SupabaseClient;

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
      expiringAmountExpected: expiringIn7Days * 25.00,
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
      const res = await fetch('https://tuctucmap-default-rtdb.firebaseio.com/driver_locations.json');
      const data = await res.json();
      if (!data) return [];
      return Object.values(data).filter((d: any) => d && d.active) as LiveDriverLocation[];
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

    return data.map(trip => ({
        id: trip.id,
        passengerName: profileMap.get(trip.passenger_id) || 'Desconocido',
        driverName: profileMap.get(trip.driver_id) || 'Desconocido',
        status: trip.status,
        message: trip.message || 'Sin mensaje',
        created_at: new Date(trip.created_at).getTime()
    }));
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
    const start = new Date();
    const days = planType === 'mensual' ? 30 : (planType === 'quincenal' ? 15 : 7);
    const end = new Date(start.getTime() + (days * 24 * 60 * 60 * 1000));
    
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
}
