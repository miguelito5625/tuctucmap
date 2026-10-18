package com.example.tuctucmap.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Utilidades para iniciar navegación asistida paso a paso hacia coordenadas geográficas
 * utilizando aplicaciones de mapas externas instaladas en el dispositivo (Google Maps y Waze).
 */
object ExternalNavigationUtils {

    /**
     * Inicia la navegación paso a paso hacia el destino en Google Maps.
     * Si la app oficial no está instalada, abre la ruta en el navegador web.
     */
    fun openGoogleMaps(context: Context, lat: Double, lon: Double) {
        if (lat == 0.0 && lon == 0.0) {
            Toast.makeText(context, "No hay coordenadas disponibles para este destino", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            // Intento 1: Navegación nativa paso a paso en la app oficial de Google Maps
            val uri = Uri.parse("google.navigation:q=$lat,$lon&mode=d")
            val mapIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(mapIntent)
        } catch (_: Exception) {
            try {
                // Intento 2: URL universal de Google Maps (navegador o selector del sistema)
                val fallbackUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lon")
                val fallbackIntent = Intent(Intent.ACTION_VIEW, fallbackUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            } catch (_: Exception) {
                try {
                    // Intento 3: Intent geo genérico
                    val geoUri = Uri.parse("geo:$lat,$lon?q=$lat,$lon")
                    val geoIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(geoIntent)
                } catch (_: Exception) {
                    Toast.makeText(context, "No se pudo abrir Google Maps", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Inicia la navegación paso a paso hacia el destino en Waze.
     * Si la app no está instalada, abre el enlace universal de navegación en el navegador.
     */
    fun openWaze(context: Context, lat: Double, lon: Double) {
        if (lat == 0.0 && lon == 0.0) {
            Toast.makeText(context, "No hay coordenadas disponibles para este destino", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            // Intento 1: Protocolo nativo waze:// con parámetro de navegación inmediata
            val uri = Uri.parse("waze://?ll=$lat,$lon&navigate=yes")
            val wazeIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.waze")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(wazeIntent)
        } catch (_: Exception) {
            try {
                // Intento 2: Deep link universal web de Waze
                val webUri = Uri.parse("https://waze.com/ul?ll=$lat,$lon&navigate=yes")
                val fallbackIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            } catch (_: Exception) {
                Toast.makeText(context, "No se pudo abrir Waze", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
