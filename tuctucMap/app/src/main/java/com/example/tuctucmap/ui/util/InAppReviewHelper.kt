package com.example.tuctucmap.ui.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Gestor del flujo nativo de calificación in-app de Google Play (In-App Review API).
 * Permite desplegar el diálogo de calificación tras cierta actividad del usuario
 * (ej. viajes completados con éxito) y respetar periodos de enfriamiento (cooldowns)
 * para no saturar al usuario, cumpliendo con las buenas prácticas y cuotas de Google Play.
 */
object InAppReviewHelper {

    private const val TAG = "InAppReviewHelper"
    private const val PREFS_NAME = "tuctuc_in_app_review_prefs"
    private const val KEY_COMPLETED_TRIPS = "completed_trips_count"
    private const val KEY_LAST_PROMPT_TIMESTAMP = "last_prompt_timestamp"

    /** Umbral mínimo de actividades exitosas para solicitar calificación por primera vez */
    private const val MIN_TRIPS_FOR_FIRST_REVIEW = 3

    /** Periodo de enfriamiento entre solicitudes sucesivas (60 días) */
    private const val COOLDOWN_MILLIS = 60L * 24 * 60 * 60 * 1000L

    /**
     * Registra un viaje completado con éxito (para pasajero o conductor) y verifica
     * automáticamente si se cumplen las condiciones para solicitar la reseña in-app.
     */
    fun recordSuccessfulTripAndRequestReview(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentTrips = prefs.getInt(KEY_COMPLETED_TRIPS, 0) + 1
            prefs.edit().putInt(KEY_COMPLETED_TRIPS, currentTrips).apply()

            val lastPromptTime = prefs.getLong(KEY_LAST_PROMPT_TIMESTAMP, 0L)
            val now = System.currentTimeMillis()
            val timeSinceLast = now - lastPromptTime

            val isFirstPromptEligible = (lastPromptTime == 0L && currentTrips >= MIN_TRIPS_FOR_FIRST_REVIEW)
            val isCooldownElapsed = (lastPromptTime > 0L && timeSinceLast >= COOLDOWN_MILLIS)

            Log.d(TAG, "Trip recorded. Total: $currentTrips, FirstEligible: $isFirstPromptEligible, CooldownElapsed: $isCooldownElapsed")

            if (isFirstPromptEligible || isCooldownElapsed) {
                launchInAppReview(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in recordSuccessfulTripAndRequestReview: ${e.message}")
        }
    }

    /**
     * Inicia el flujo oficial de Google Play Review API de manera asíncrona y segura.
     */
    private fun launchInAppReview(context: Context) {
        val activity = context.findActivity()
        if (activity == null) {
            Log.w(TAG, "Cannot launch InAppReview: Activity context not found.")
            return
        }

        try {
            val manager = ReviewManagerFactory.create(context)
            val request = manager.requestReviewFlow()
            request.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val reviewInfo = task.result
                    val flow = manager.launchReviewFlow(activity, reviewInfo)
                    flow.addOnCompleteListener {
                        // El flujo de Google Play concluyó (el usuario pudo calificar o cerrar el diálogo)
                        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        prefs.edit().putLong(KEY_LAST_PROMPT_TIMESTAMP, System.currentTimeMillis()).apply()
                        Log.d(TAG, "In-app review flow completed successfully.")
                    }
                } else {
                    Log.w(TAG, "Failed to request review flow: ${task.exception?.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during launchInAppReview: ${e.message}")
        }
    }

    /**
     * Resuelve de forma segura la instancia de Activity actual a partir de cualquier Context de Compose.
     */
    private fun Context.findActivity(): Activity? {
        var current = this
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }
}

