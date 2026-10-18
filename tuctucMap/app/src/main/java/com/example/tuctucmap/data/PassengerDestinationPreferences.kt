package com.example.tuctucmap.data

import android.content.Context
import org.json.JSONArray

/**
 * Gestor de persistencia local para los destinos frecuentes del pasajero.
 * Almacena los últimos 5 destinos ingresados por el pasajero en SharedPreferences
 * sin valores de ejemplo iniciales.
 */
object PassengerDestinationPreferences {
    private const val PREFS_NAME = "tuctuc_prefs"
    private const val KEY_RECENT_DESTINATIONS = "passenger_recent_destinations"

    /**
     * Obtiene hasta 5 sugerencias de destinos basadas en el historial del pasajero.
     * Si es la primera vez o no hay historial guardado, retorna una lista vacía.
     */
    fun getRecentDestinations(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedJson = prefs.getString(KEY_RECENT_DESTINATIONS, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(savedJson)
            val list = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.optString(i)?.trim().orEmpty()
                if (item.isNotBlank() && !list.any { it.equals(item, ignoreCase = true) }) {
                    list.add(item)
                }
            }
            list.take(5)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Guarda el destino colocado en el input.
     * Lo posiciona al inicio de la lista y conserva únicamente los últimos 5 destinos únicos.
     */
    fun saveDestination(context: Context, destination: String) {
        val cleanDest = destination.trim()
        if (cleanDest.isBlank()) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentList = getRecentDestinations(context).toMutableList()

        // Eliminar ocurrencia previa sin distinción de mayúsculas para no duplicar
        currentList.removeAll { it.equals(cleanDest, ignoreCase = true) }
        currentList.add(0, cleanDest)

        val toSave = currentList.take(5)
        val jsonArray = JSONArray()
        toSave.forEach { jsonArray.put(it) }

        prefs.edit()
            .putString(KEY_RECENT_DESTINATIONS, jsonArray.toString())
            .apply()
    }

    /**
     * Limpia el historial local de destinos guardados.
     */
    fun clearRecentDestinations(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_RECENT_DESTINATIONS).apply()
    }
}
