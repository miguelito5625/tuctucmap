package com.example.tuctucmap.ui.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Utilidades para compartir la aplicación TucTuc Map y gestionar la calificación
 * oficial en Google Play Store mediante intents nativos de Android.
 */
object AppShareUtils {

    const val PLAY_STORE_BASE_WEB_URL = "https://play.google.com/store/apps/details?id="
    const val PLAY_STORE_MARKET_URI = "market://details?id="

    /**
     * Despliega el menú selector nativo de Android (Share Sheet) para compartir el enlace
     * de descarga de TucTuc Map por WhatsApp, Telegram, SMS, redes sociales o cualquier
     * aplicación compatible instalada en el dispositivo.
     */
    fun shareApp(context: Context) {
        val packageName = context.packageName
        val appUrl = "$PLAY_STORE_BASE_WEB_URL$packageName"
        val shareMessage = "¡Hola! Te recomiendo TucTuc Map para ver y pedir toritos/mototaxis en tiempo real de forma segura. Descárgala aquí: $appUrl"

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "TucTuc Map - Movilidad Local Segura")
            putExtra(Intent.EXTRA_TEXT, shareMessage)
        }
        val chooserIntent = Intent.createChooser(sendIntent, "Compartir TucTuc Map con...").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(chooserIntent)
        } catch (_: Exception) {
            Toast.makeText(context, "No se encontró una aplicación para compartir", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Redirige al usuario directamente a la ficha oficial de TucTuc Map en Google Play Store
     * para que pueda calificar y dejar su reseña.
     * Utiliza el protocolo nativo market:// y cuenta con fallback transparente al navegador web
     * si la app de la tienda no se encuentra disponible.
     */
    fun openPlayStoreForRating(context: Context) {
        val packageName = context.packageName
        val marketUri = Uri.parse("$PLAY_STORE_MARKET_URI$packageName")
        val marketIntent = Intent(Intent.ACTION_VIEW, marketUri).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NO_HISTORY or
                Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                Intent.FLAG_ACTIVITY_NEW_TASK
            )
        }
        try {
            context.startActivity(marketIntent)
        } catch (_: ActivityNotFoundException) {
            openPlayStoreWebFallback(context, packageName)
        } catch (_: Exception) {
            openPlayStoreWebFallback(context, packageName)
        }
    }

    /**
     * Abre la URL web de Google Play Store en el navegador predeterminado del sistema.
     */
    private fun openPlayStoreWebFallback(context: Context, packageName: String) {
        val webUri = Uri.parse("$PLAY_STORE_BASE_WEB_URL$packageName")
        val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(webIntent)
        } catch (_: Exception) {
            Toast.makeText(context, "No se pudo abrir la tienda de aplicaciones", Toast.LENGTH_SHORT).show()
        }
    }
}

