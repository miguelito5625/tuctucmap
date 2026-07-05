import { Injectable } from '@angular/core';
import { createClient, SupabaseClient } from '@supabase/supabase-js';
import { environment } from '../../environments/environment';
import { Observable, BehaviorSubject } from 'rxjs';
import { Driver, DriverStatus, SubscriptionHistory } from '../core/models/driver.model';

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
        .on('postgres_changes', { event: '*', schema: 'public', table: 'profiles' }, payload => {
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
        status: end >= now ? 'Activo' : 'Expirado'
      };
    });
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
    const { error } = await this.supabase.from('profiles').delete().eq('id', uid);
    if (error) throw error;
    await this.fetchDrivers();
  }

  async renewSubscription(uid: string): Promise<void> {
    const start = new Date();
    const end = new Date(start.getTime() + (7 * 24 * 60 * 60 * 1000));
    
    const { error: profileError } = await this.supabase.from('profiles').update({ 
      approval_status: 'aprobado',
      subscription_end_date: end.toISOString()
    }).eq('id', uid);
    if (profileError) throw profileError;

    const { error: subError } = await this.supabase.from('driver_subscriptions').insert({
      driver_id: uid,
      start_date: start.toISOString(),
      end_date: end.toISOString()
    });
    if (subError) throw subError;
    await this.fetchDrivers();
  }

  async setSubscriptionExpiry(uid: string, timestamp: number): Promise<void> {
    const end = new Date(timestamp);
    const start = new Date();
    const { error: profileError } = await this.supabase.from('profiles').update({ 
      subscription_end_date: end.toISOString() 
    }).eq('id', uid);
    if (profileError) throw profileError;

    const { error: subError } = await this.supabase.from('driver_subscriptions').insert({
      driver_id: uid,
      start_date: start.toISOString(),
      end_date: end.toISOString()
    });
    if (subError) throw subError;
    await this.fetchDrivers();
  }
}
