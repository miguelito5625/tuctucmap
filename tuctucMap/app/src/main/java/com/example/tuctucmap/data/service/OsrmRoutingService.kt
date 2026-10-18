package com.example.tuctucmap.data.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.*

/**
 * Servicio de ruteo OSRM (Open Source Routing Machine) para OpenStreetMap.
 * Calcula rutas por carretera entre el conductor y el pasajero a costo $0.
 * Si el servidor público tarda o no hay conexión, ofrece fallback geométrico inmediato.
 */
object OsrmRoutingService {

    data class RouteResult(
        val points: List<GeoPoint>,
        val distanceMeters: Double,
        val durationSeconds: Double,
        val isSuccess: Boolean
    ) {
        val formattedDistance: String
            get() = if (distanceMeters >= 1000.0) {
                String.format(Locale.US, "%.1f km", distanceMeters / 1000.0)
            } else {
                "${distanceMeters.roundToInt()} m"
            }

        val formattedDuration: String
            get() {
                val mins = ceil(durationSeconds / 60.0).toInt().coerceAtLeast(1)
                return if (mins >= 60) {
                    val hours = mins / 60
                    val remMins = mins % 60
                    "${hours}h ${remMins}m"
                } else {
                    "$mins min"
                }
            }
    }

    /**
     * Obtiene el trazado de la ruta por calles desde [startLat, startLon] hasta [endLat, endLon].
     */
    suspend fun getRoute(
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double
    ): RouteResult = withContext(Dispatchers.IO) {
        // Validación de coordenadas válidas
        if (startLat == 0.0 || startLon == 0.0 || endLat == 0.0 || endLon == 0.0) {
            return@withContext RouteResult(
                points = emptyList(),
                distanceMeters = 0.0,
                durationSeconds = 0.0,
                isSuccess = false
            )
        }

        try {
            // Formato OSRM: /route/v1/driving/{lon1},{lat1};{lon2},{lat2}?overview=full&geometries=geojson
            val urlString = String.format(
                Locale.US,
                "https://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson",
                startLon, startLat, endLon, endLat
            )

            val url = URL(urlString)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", "TucTucMap/1.0 (Android; app.tuctucmap.gt)")
                setRequestProperty("Accept", "application/json")
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.use { it.readText() }
                connection.disconnect()

                val json = JSONObject(response)
                val code = json.optString("code")
                if (code.equals("Ok", ignoreCase = true)) {
                    val routes = json.optJSONArray("routes")
                    if (routes != null && routes.length() > 0) {
                        val firstRoute = routes.getJSONObject(0)
                        val distance = firstRoute.optDouble("distance", 0.0)
                        val duration = firstRoute.optDouble("duration", 0.0)

                        val geometry = firstRoute.optJSONObject("geometry")
                        val coordsArray = geometry?.optJSONArray("coordinates")
                        val points = mutableListOf<GeoPoint>()

                        if (coordsArray != null) {
                            for (i in 0 until coordsArray.length()) {
                                val coord = coordsArray.getJSONArray(i)
                                val lon = coord.getDouble(0)
                                val lat = coord.getDouble(1)
                                points.add(GeoPoint(lat, lon))
                            }
                        }

                        if (points.isNotEmpty()) {
                            return@withContext RouteResult(
                                points = points,
                                distanceMeters = distance,
                                durationSeconds = duration,
                                isSuccess = true
                            )
                        }
                    }
                }
            } else {
                connection.disconnect()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback: Haversine directo con velocidad promedio de tuctuc en calles (~25 km/h)
        val fallbackDistance = calculateHaversineMeters(startLat, startLon, endLat, endLon)
        val estimatedSpeedMps = 25.0 * 1000.0 / 3600.0
        val fallbackDuration = fallbackDistance / estimatedSpeedMps

        RouteResult(
            points = listOf(GeoPoint(startLat, startLon), GeoPoint(endLat, endLon)),
            distanceMeters = fallbackDistance,
            durationSeconds = fallbackDuration,
            isSuccess = false
        )
    }

    fun calculateHaversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
