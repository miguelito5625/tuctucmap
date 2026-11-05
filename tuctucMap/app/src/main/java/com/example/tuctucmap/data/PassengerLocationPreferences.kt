package com.example.tuctucmap.data

import android.content.Context
import android.location.LocationManager
import org.osmdroid.util.GeoPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Gestor de persistencia local para la última ubicación conocida del pasajero.
 * Permite que los mapas de pasajeros (hayan o no iniciado sesión) carguen
 * inmediatamente en la última ubicación real registrada en el dispositivo,
 * evitando mostrar coordenadas arbitrarias o de otras ciudades mientras el sensor GPS
 * obtiene el primer fix satelital.
 */
object PassengerLocationPreferences {
    private const val PREFS_NAME = "tuctuc_prefs"
    private const val KEY_LAST_PASSENGER_LAT = "passenger_last_latitude"
    private const val KEY_LAST_PASSENGER_LON = "passenger_last_longitude"
    private const val KEY_LAST_PASSENGER_TIME = "passenger_last_location_time"

    // Coordenadas de contingencia inicial predeterminada si no existe ninguna lectura local previa ni en el sistema
    const val DEFAULT_FALLBACK_LAT = 14.6349
    const val DEFAULT_FALLBACK_LON = -90.5069

    @Volatile
    private var inMemoryLastLat: Double = 0.0
    @Volatile
    private var inMemoryLastLon: Double = 0.0
    @Volatile
    private var lastSaveTimestampMs: Long = 0L

    /**
     * Guarda de forma persistente la última ubicación conocida del pasajero en SharedPreferences.
     */
    fun saveLastLocation(context: Context, latitude: Double, longitude: Double) {
        if (!isValidCoordinate(latitude, longitude)) return

        inMemoryLastLat = latitude
        inMemoryLastLon = longitude
        lastSaveTimestampMs = System.currentTimeMillis()

        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LAST_PASSENGER_LAT, latitude.toString())
                .putString(KEY_LAST_PASSENGER_LON, longitude.toString())
                .putLong(KEY_LAST_PASSENGER_TIME, lastSaveTimestampMs)
                .apply()
        } catch (_: Exception) {}
    }

    /**
     * Guarda la ubicación con control de frecuencia (throttling) para no saturar
     * el almacenamiento interno ni degradar el rendimiento con cada tick GPS:
     * Guarda si ha transcurrido más de [minIntervalMs] (ej. 5 seg) o si el usuario se ha desplazado más de [minDistanceMeters] (ej. 8m).
     */
    fun saveLastLocationThrottled(
        context: Context,
        latitude: Double,
        longitude: Double,
        minIntervalMs: Long = 5000L,
        minDistanceMeters: Double = 8.0
    ) {
        if (!isValidCoordinate(latitude, longitude)) return

        val now = System.currentTimeMillis()
        val elapsed = now - lastSaveTimestampMs

        if (inMemoryLastLat != 0.0 && inMemoryLastLon != 0.0) {
            val distance = calculateDistanceMeters(inMemoryLastLat, inMemoryLastLon, latitude, longitude)
            if (elapsed < minIntervalMs && distance < minDistanceMeters) {
                return
            }
        }

        saveLastLocation(context, latitude, longitude)
    }

    /**
     * Obtiene la última ubicación guardada en el dispositivo si existe y es válida.
     */
    fun getLastSavedLocation(context: Context): GeoPoint? {
        if (inMemoryLastLat != 0.0 && inMemoryLastLon != 0.0 && isValidCoordinate(inMemoryLastLat, inMemoryLastLon)) {
            return GeoPoint(inMemoryLastLat, inMemoryLastLon)
        }

        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val latStr = prefs.getString(KEY_LAST_PASSENGER_LAT, null)
            val lonStr = prefs.getString(KEY_LAST_PASSENGER_LON, null)
            if (latStr != null && lonStr != null) {
                val lat = latStr.toDoubleOrNull()
                val lon = lonStr.toDoubleOrNull()
                if (lat != null && lon != null && isValidCoordinate(lat, lon)) {
                    inMemoryLastLat = lat
                    inMemoryLastLon = lon
                    return GeoPoint(lat, lon)
                }
            }
        } catch (_: Exception) {}

        return null
    }

    /**
     * Obtiene la mejor ubicación para inicializar el mapa inmediatamente:
     * 1. Ubicación local guardada previamente en SharedPreferences.
     * 2. Si no hay guardada (ej. primera instalación o almacenamiento borrado), consulta última ubicación conocida del sistema (LocationManager).
     * 3. Fallback seguro por defecto (DEFAULT_FALLBACK_LAT, DEFAULT_FALLBACK_LON).
     */
    fun getInitialMapLocation(context: Context): GeoPoint {
        val saved = getLastSavedLocation(context)
        if (saved != null) {
            return saved
        }

        // Intento con LocationManager del sistema en caso de primer arranque
        try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            if (lm != null) {
                val providers = listOf(
                    LocationManager.GPS_PROVIDER,
                    LocationManager.NETWORK_PROVIDER,
                    LocationManager.PASSIVE_PROVIDER
                )
                for (provider in providers) {
                    try {
                        val loc = lm.getLastKnownLocation(provider)
                        if (loc != null && isValidCoordinate(loc.latitude, loc.longitude)) {
                            saveLastLocation(context, loc.latitude, loc.longitude)
                            return GeoPoint(loc.latitude, loc.longitude)
                        }
                    } catch (_: SecurityException) {
                        // Permiso aún no concedido
                    }
                }
            }
        } catch (_: Exception) {}

        return GeoPoint(DEFAULT_FALLBACK_LAT, DEFAULT_FALLBACK_LON)
    }

    /**
     * Valida que las coordenadas no sean nulas, cero absoluto (Null Island) ni fuera de rango geográfico.
     */
    fun isValidCoordinate(lat: Double, lon: Double): Boolean {
        return lat != 0.0 && lon != 0.0 && lat in -90.0..90.0 && lon in -180.0..180.0
    }

    private fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}

