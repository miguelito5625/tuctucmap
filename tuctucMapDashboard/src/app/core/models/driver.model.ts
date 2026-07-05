/**
 * Represents the possible approval states of a driver,
 * matching the values used in the tuctucMap Android app.
 */
export type DriverStatus = 'pendiente' | 'aprobado' | 'rechazado' | 'suscripcion_vencida';

export interface SubscriptionHistory {
  id: string;
  driver_id: string;
  driverName?: string;
  driverPlate?: string;
  start_date: number;
  end_date: number;
  created_at: number;
  status: 'Activo' | 'Expirado';
}

/**
 * TypeScript interface that mirrors the Kotlin data class
 * stored under the `drivers/{uid}` node in Firebase Realtime Database.
 */
export interface Driver {
  /** Firebase Auth UID — also used as the node key in the database. */
  uid: string;
  /** Full name of the driver. */
  nombre: string;
  /** Email address. */
  email: string;
  /** Phone number. */
  telefono: string;
  /** Vehicle license plate (Tuc Tuc). */
  placas: string;
  /** Current approval status. Defaults to 'pendiente' on registration. */
  estadoAprobacion: DriverStatus;
  /**
   * Subscription expiry date as a Unix timestamp in milliseconds.
   * A value of 0 or undefined means no expiry date is set.
   * Compatible with Kotlin Long field in the Android app.
   */
  fechaVencimientoSuscripcion?: number;
  /** Number of subscriptions the driver has had. */
  suscripcionesCount?: number;
}
