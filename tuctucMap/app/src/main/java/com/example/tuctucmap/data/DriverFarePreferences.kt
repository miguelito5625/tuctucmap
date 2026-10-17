package com.example.tuctucmap.data

import android.content.Context

/**
 * Gestor de persistencia local para las sugerencias de cobro del conductor.
 * Extrae y almacena dinámicamente los últimos 5 precios de viajes cobrados
 * con anterioridad en el almacenamiento local del dispositivo móvil (SharedPreferences).
 */
object DriverFarePreferences {
    private const val PREFS_NAME = "tuctuc_prefs"
    private const val KEY_RECENT_FARES = "driver_recent_fares"

    // Sugerencias predeterminadas del sistema cuando aún no hay historial previo
    val DEFAULT_FARES = listOf(10.0, 15.0, 20.0)

    /**
     * Obtiene hasta 5 sugerencias de precios basadas en los viajes anteriores del conductor.
     * Si hay menos de 5 o el historial está vacío, complementa con los valores base (Q10, Q15, Q20).
     */
    fun getRecentFares(context: Context): List<Double> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedString = prefs.getString(KEY_RECENT_FARES, null)

        val list = if (!savedString.isNullOrBlank()) {
            savedString.split(",")
                .mapNotNull { it.trim().toDoubleOrNull() }
                .filter { it > 0.0 }
                .distinct()
        } else {
            emptyList()
        }

        // Si no hay valores guardados, retornar los defaults
        if (list.isEmpty()) {
            return DEFAULT_FARES
        }

        // Combinar con los defaults para asegurar una experiencia rica de sugerencias
        val combined = (list + DEFAULT_FARES).distinct().take(5)
        return combined
    }

    /**
     * Guarda el precio cobrado en el último viaje.
     * Lo coloca al inicio de la lista y conserva únicamente los últimos 5 precios.
     */
    fun saveFare(context: Context, newFare: Double) {
        if (newFare <= 0.0) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentList = getRecentFares(context).toMutableList()

        // Eliminar si ya existía para moverlo al frente como el más reciente
        currentList.remove(newFare)
        currentList.add(0, newFare)

        val toSave = currentList.distinct().take(5)
        prefs.edit()
            .putString(KEY_RECENT_FARES, toSave.joinToString(","))
            .apply()
    }
}
