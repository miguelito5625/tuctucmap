package com.example.tuctucmap.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Driver(
    val id: String = "",
    val email: String = "",
    @SerialName("name")
    val nombre: String = "",
    @SerialName("phone")
    val telefono: String = "",
    @SerialName("plate_number")
    val placas: String = "",
    @SerialName("approval_status")
    val estadoAprobacion: String = "pendiente",
    @SerialName("subscription_end_date")
    val fechaVencimientoSuscripcion: String = ""
) {
    // Alias de compatibilidad
    val uid: String get() = id
    val subscriptionEndDate: String get() = fechaVencimientoSuscripcion
    val approvalStatus: String get() = estadoAprobacion

    /**
     * Retorna el timestamp en milisegundos de la fecha de vencimiento, o 0L si es inválida o vacía.
     */
    val expiryTimestampMillis: Long
        get() = parseIsoTimestamp(fechaVencimientoSuscripcion)

    /**
     * Determina si la suscripción está vencida.
     * Retorna true si:
     * 1. El estado es explícitamente "suscripcion_vencida", o
     * 2. El estado es "aprobado", tiene fecha de vencimiento y dicha fecha ya expiró en el pasado.
     */
    val isSubscriptionExpired: Boolean
        get() {
            if (estadoAprobacion == "suscripcion_vencida") return true
            if (estadoAprobacion == "aprobado") {
                val expiryMs = expiryTimestampMillis
                if (expiryMs > 0L && System.currentTimeMillis() > expiryMs) {
                    return true
                }
            }
            return false
        }

    /**
     * Determina si el conductor está aprobado Y su suscripción está activa y vigente.
     */
    val isApprovedAndActive: Boolean
        get() = estadoAprobacion == "aprobado" && !isSubscriptionExpired
}

/**
 * Parsea una cadena de fecha ISO 8601 (procedente de Supabase TIMESTAMPTZ) a milisegundos de época.
 */
fun parseIsoTimestamp(dateStr: String?): Long {
    if (dateStr.isNullOrBlank()) return 0L
    
    // API 26+ (Android 8.0+)
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        try {
            return java.time.Instant.parse(dateStr).toEpochMilli()
        } catch (_: Exception) {}
        try {
            return java.time.OffsetDateTime.parse(dateStr).toInstant().toEpochMilli()
        } catch (_: Exception) {}
    }

    // Fallbacks para formatos ISO 8601 comunes
    val patterns = arrayOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss"
    )
    for (pattern in patterns) {
        try {
            val sdf = java.text.SimpleDateFormat(pattern, java.util.Locale.US)
            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val date = sdf.parse(dateStr)
            if (date != null) return date.time
        } catch (_: Exception) {}
    }

    return 0L
}
