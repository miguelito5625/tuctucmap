package com.example.tuctucmap.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Reloj sincronizado con el servidor (Firebase .info/serverTimeOffset y Supabase RPC)
 * para impedir que el cambio manual de fecha/hora del teléfono evada el vencimiento de suscripción.
 */
object ServerClock {
    @Volatile
    var offsetMillis: Long = 0L

    fun nowMillis(): Long = System.currentTimeMillis() + offsetMillis

    fun updateFromServerEpoch(serverEpochMillis: Long) {
        if (serverEpochMillis > 1_700_000_000_000L) {
            offsetMillis = serverEpochMillis - System.currentTimeMillis()
        }
    }
}

@Serializable
data class AppConfig(
    val id: String = "default",
    @SerialName("admin_whatsapp_phone")
    val adminWhatsappPhone: String = "50230000000",
    @SerialName("admin_whatsapp")
    val adminWhatsapp: String = "50230000000",
    @SerialName("banrural_account")
    val banruralAccount: String = "3402-0987-65",
    @SerialName("bank_1_name")
    val bank1Name: String = "Banrural (Monetaria)",
    @SerialName("bank_1_account")
    val bank1Account: String = "3402-0987-65",
    @SerialName("bi_account")
    val biAccount: String = "027-019842-1",
    @SerialName("bank_2_name")
    val bank2Name: String = "Banco Industrial (Ahorro)",
    @SerialName("bank_2_account")
    val bank2Account: String = "027-019842-1",
    @SerialName("account_holder_name")
    val accountHolderName: String = "Administración TucTuc Map",
    @SerialName("currency_symbol")
    val currencySymbol: String = "Q",
    @SerialName("price_daily")
    val priceDaily: Double = 10.0,
    @SerialName("price_semanal")
    val priceSemanal: Double = 25.0,
    @SerialName("price_weekly")
    val priceWeekly: Double = 25.0,
    @SerialName("price_quincenal")
    val priceQuincenal: Double = 45.0,
    @SerialName("price_mensual")
    val priceMensual: Double = 80.0,
    @SerialName("price_monthly")
    val priceMonthly: Double = 80.0,
    @SerialName("max_search_radius_km")
    val maxSearchRadiusKm: Double = 5.0,
    @SerialName("search_radius_km")
    val searchRadiusKm: Double = 5.0
)

@Serializable
data class SubscriptionRenewalRequest(
    val id: String = "",
    @SerialName("driver_id")
    val driverId: String = "",
    @SerialName("plan_type")
    val planType: String = "semanal",
    val amount: Double = 25.0,
    @SerialName("payment_method")
    val paymentMethod: String = "deposito_bancario",
    @SerialName("reference_number")
    val referenceNumber: String = "",
    val status: String = "pendiente",
    @SerialName("rejection_reason")
    val rejectionReason: String? = null,
    @SerialName("created_at")
    val createdAt: String = "",
    @SerialName("reviewed_at")
    val reviewedAt: String? = null
) {
    val createdAtEpoch: Long get() = parseIsoTimestamp(createdAt)
    val reviewedAtEpoch: Long get() = parseIsoTimestamp(reviewedAt)
}

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
    @SerialName("unit_number")
    val numeroUnidad: String = "",
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
     * Etiqueta amigable que prioriza el número de unidad/padrón si existe, junto con la placa.
     */
    val displayIdentifier: String
        get() = if (numeroUnidad.isNotBlank()) "$numeroUnidad • $placas" else placas

    /**
     * Retorna el timestamp en milisegundos de la fecha de vencimiento, o 0L si es inválida o vacía.
     */
    val expiryTimestampMillis: Long
        get() = parseIsoTimestamp(fechaVencimientoSuscripcion)

    /**
     * Determina si la suscripción está vencida usando la hora sincronizada del servidor (ServerClock).
     */
    val isSubscriptionExpired: Boolean
        get() {
            if (estadoAprobacion == "suscripcion_vencida") return true
            if (estadoAprobacion == "aprobado") {
                val expiryMs = expiryTimestampMillis
                if (expiryMs > 0L && ServerClock.nowMillis() > expiryMs) {
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

    /**
     * Días restantes de vigencia de la suscripción con redondeo hacia arriba (ceil)
     * para que coincida de forma natural con los días calendario restantes.
     */
    val remainingDays: Int
        get() = calculateRemainingDays(expiryTimestampMillis, ServerClock.nowMillis())
}

/**
 * Calcula los días restantes de vigencia entre la fecha de expiración y la fecha actual.
 * Utiliza ceil (redondeo hacia arriba) para que la cantidad de días coincida con la diferencia
 * calendario visual (p. ej. si vence el 25 y hoy es 17, faltan 8 días en lugar de truncar a 7).
 */
fun calculateRemainingDays(expiryMs: Long, nowMs: Long = ServerClock.nowMillis()): Int {
    if (expiryMs <= 0L || expiryMs <= nowMs) return 0
    val diffMs = expiryMs - nowMs
    val daysDouble = diffMs.toDouble() / (1000.0 * 60.0 * 60.0 * 24.0)
    return kotlin.math.ceil(daysDouble).toInt()
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
