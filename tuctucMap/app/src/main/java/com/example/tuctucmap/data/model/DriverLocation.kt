package com.example.tuctucmap.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.*

@Serializable
data class DriverLocation(
    @SerialName("driver_id")
    val uid: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    @SerialName("active")
    val activo: Boolean = false,
    @SerialName("updated_at")
    val timestamp: String = "",
    val nombre: String = "",
    val telefono: String = "",
    val placas: String = "",
    val numeroUnidad: String = "",
    val status: String = "disponible" // "disponible", "ocupado", "en_pausa"
) {
    val latitud: Double get() = lat
    val longitud: Double get() = lon

    val isAvailable: Boolean get() = status.equals("disponible", ignoreCase = true)
    val isBusyOrPaused: Boolean get() = !isAvailable

    /**
     * Convierte el timestamp de actualización a milisegundos de época.
     */
    val updatedAtMillis: Long
        get() {
            return try {
                timestamp.toLongOrNull() ?: 0L
            } catch (_: Exception) {
                0L
            }
        }

    /**
     * Determina si el conductor ha enviado un latido reciente (por defecto en los últimos 5 minutos).
     */
    fun isRecentlyActive(maxAgeMillis: Long = 300_000L): Boolean {
        if (!activo) return false
        val lastMs = updatedAtMillis
        if (lastMs <= 0L) return true // Compatibilidad si no venía timestamp
        return (ServerClock.nowMillis() - lastMs) <= maxAgeMillis
    }

    /**
     * Calcula la distancia en metros entre este conductor y una coordenada dada (Fórmula de Haversine).
     */
    fun distanceMetersTo(targetLat: Double, targetLon: Double): Double {
        if (lat == 0.0 && lon == 0.0) return 0.0
        val earthRadius = 6371000.0 // metros
        val dLat = Math.toRadians(targetLat - lat)
        val dLon = Math.toRadians(targetLon - lon)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat)) * cos(Math.toRadians(targetLat)) *
                sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    /**
     * Estima el tiempo de llegada en minutos asumiendo una velocidad promedio en mototaxi (~25 km/h).
     */
    fun estimatedArrivalMinutes(targetLat: Double, targetLon: Double, avgSpeedKmh: Double = 25.0): Int {
        val distMeters = distanceMetersTo(targetLat, targetLon)
        val metersPerMinute = (avgSpeedKmh * 1000.0) / 60.0
        val mins = (distMeters / metersPerMinute).roundToInt()
        return mins.coerceAtLeast(1)
    }

    /**
     * Retorna una etiqueta amigable de distancia (ej. "250 m" o "1.4 km").
     */
    fun formattedDistance(targetLat: Double, targetLon: Double): String {
        val meters = distanceMetersTo(targetLat, targetLon)
        return if (meters < 1000) {
            "${meters.toInt()} m"
        } else {
            String.format(java.util.Locale.US, "%.1f km", meters / 1000.0)
        }
    }
}
