package com.example.tuctucmap.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class UserRoleDto(
    val id: String = "",
    val role: String = "passenger",
    val name: String? = null
)

@Serializable
data class AdminProfileDto(
    val id: String = "",
    val email: String? = null,
    val name: String? = null,
    val phone: String? = null,
    val role: String = "admin",
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class AdminUserDto(
    val id: String = "",
    val email: String? = null,
    val name: String? = null,
    val phone: String? = null,
    val role: String = "admin",
    @SerialName("created_at") val createdAt: String? = null
) {
    val displayName: String get() = name?.trim()?.ifBlank { email ?: "Admin" } ?: (email ?: "Admin")
}

@Serializable
data class DriverDossierDto(
    val driver: AdminDriver,
    val totalPaidSubscriptions: Double = 0.0,
    val subscriptionsCount: Int = 0,
    val subscriptions: List<AdminSubscriptionHistory> = emptyList(),
    val totalTripsCompleted: Int = 0,
    val totalFaresEarned: Double = 0.0
)

@Serializable
data class AdminDriver(
    val id: String = "",
    val email: String? = null,
    @SerialName("name")
    private val rawNombre: String? = null,
    @SerialName("phone")
    private val rawTelefono: String? = null,
    @SerialName("plate_number")
    private val rawPlacas: String? = null,
    @SerialName("unit_number")
    private val rawNumeroUnidad: String? = null,
    @SerialName("approval_status")
    val estadoAprobacion: String = "pendiente",
    @SerialName("subscription_end_date")
    val fechaVencimientoSuscripcion: String? = null,
    @SerialName("driver_photo_url")
    val driverPhotoUrl: String? = null,
    @SerialName("vehicle_photos_360")
    val vehiclePhotos360: List<String>? = null,
    val role: String = "driver"
) {
    val nombre: String get() = rawNombre?.trim() ?: ""
    val telefono: String get() = rawTelefono?.trim() ?: ""
    val placas: String get() = rawPlacas?.trim() ?: ""
    val numeroUnidad: String get() = rawNumeroUnidad?.trim() ?: ""
    val displayIdentifier: String
        get() = if (numeroUnidad.isNotBlank()) "$numeroUnidad • $placas" else placas

    val expiryTimestampMillis: Long
        get() = parseIsoTimestamp(fechaVencimientoSuscripcion)

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

    val remainingDays: Int
        get() = calculateRemainingDays(expiryTimestampMillis, ServerClock.nowMillis())
}

@Serializable
data class AdminPaymentRequest(
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
    val reviewedAt: String? = null,
    @SerialName("reviewed_by")
    val reviewedBy: String? = null,
    // Datos populados del conductor
    var driverName: String = "",
    var driverPhone: String = "",
    var driverPlate: String = "",
    var driverUnitNumber: String = ""
) {
    val createdAtEpoch: Long get() = parseIsoTimestamp(createdAt)
    val reviewedAtEpoch: Long get() = parseIsoTimestamp(reviewedAt)
}

@Serializable
data class AdminSubscriptionHistory(
    val id: String = "",
    @SerialName("driver_id")
    val driverId: String = "",
    @SerialName("plan_type")
    val planType: String = "semanal",
    @SerialName("amount_paid")
    val amountPaid: Double = 0.0,
    @SerialName("payment_method")
    val paymentMethod: String = "deposito_bancario",
    @SerialName("reference_number")
    val referenceNumber: String = "",
    @SerialName("start_date")
    val startDate: String = "",
    @SerialName("end_date")
    val endDate: String = "",
    @SerialName("created_at")
    val createdAt: String = "",
    var status: String = "aprobado",
    @SerialName("rejection_reason")
    var rejectionReason: String? = null,
    var driverName: String = "Conductor",
    var driverPlate: String = ""
) {
    val createdAtEpoch: Long get() = parseIsoTimestamp(createdAt)
    val endDateEpoch: Long get() = parseIsoTimestamp(endDate)
    val isCurrentlyActive: Boolean get() = status != "rechazado" && endDateEpoch >= ServerClock.nowMillis()
}

@Serializable
data class AdminFinancialStats(
    val totalRevenue: Double = 0.0,
    val monthRevenue: Double = 0.0,
    val activeFleetCount: Int = 0,
    val expiringIn7Days: Int = 0,
    val expiringAmountExpected: Double = 0.0
)

@Serializable
data class AdminTrip(
    val id: String = "",
    @SerialName("passenger_id")
    val passengerId: String? = null,
    @SerialName("passenger_name")
    val passengerNameDirect: String? = null,
    @SerialName("passenger_phone")
    val passengerPhoneDirect: String? = null,
    @SerialName("driver_id")
    val driverId: String? = null,
    val status: String? = "completed",
    @SerialName("message")
    private val rawMessage: String? = null,
    val fare: Double? = null,
    @SerialName("distance_km")
    val distanceKm: Double? = null,
    @SerialName("passenger_lat")
    val passengerLat: Double? = null,
    @SerialName("passenger_lon")
    val passengerLon: Double? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    var resolvedPassengerName: String = "",
    var resolvedPassengerPhone: String = "",
    var resolvedDriverName: String = "",
    var isRegisteredPassenger: Boolean = false
) {
    val message: String get() = rawMessage ?: ""
    val createdAtEpoch: Long get() = createdAt?.let { parseIsoTimestamp(it) } ?: 0L
}

@Serializable
data class AdminFinancialSummaryDto(
    @SerialName("total_revenue")
    val totalRevenue: Double = 0.0,
    @SerialName("month_revenue")
    val monthRevenue: Double = 0.0
)

@Serializable
data class AdminPassenger(
    val id: String = "",
    @SerialName("name")
    private val rawNombre: String? = null,
    @SerialName("phone")
    private val rawTelefono: String? = null,
    @SerialName("email")
    private val rawEmail: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    val role: String = "passenger",
    @SerialName("trips_count")
    var tripsCount: Int = 0
) {
    val nombre: String get() = rawNombre ?: ""
    val telefono: String get() = rawTelefono ?: ""
    val email: String get() = rawEmail ?: ""
    val createdAtEpoch: Long get() = createdAt?.let { parseIsoTimestamp(it) } ?: 0L
}

@Serializable
data class ApprovePaymentRequestRpcParams(
    @SerialName("p_request_id")
    val requestId: String,
    @SerialName("p_custom_end_date")
    val customEndDate: String? = null
)

@Serializable
data class ApproveDriverRpcParams(
    @SerialName("p_driver_id")
    val driverId: String,
    @SerialName("p_grant_trial")
    val grantTrial: Boolean = true,
    @SerialName("p_trial_days")
    val trialDays: Int = 7
)

@Serializable
data class RejectPaymentRequestRpcParams(
    @SerialName("p_request_id")
    val requestId: String,
    @SerialName("p_rejection_reason")
    val rejectionReason: String? = null
)

@Serializable
data class DeleteUserByAdminRpcParams(
    @SerialName("target_user_id")
    val targetUserId: String
)


@Serializable
data class AdminCrashLog(
    val id: String = "",
    @SerialName("user_id") val userId: String? = null,
    @SerialName("user_email") val userEmail: String? = null,
    val role: String? = null,
    @SerialName("device_brand") val deviceBrand: String = "",
    @SerialName("device_model") val deviceModel: String = "",
    @SerialName("device_manufacturer") val deviceManufacturer: String = "",
    @SerialName("android_version") val androidVersion: String = "",
    @SerialName("sdk_int") val sdkInt: Int = 0,
    @SerialName("app_version") val appVersion: String = "",
    @SerialName("build_number") val buildNumber: Int = 0,
    @SerialName("build_type") val buildType: String = "",
    @SerialName("error_type") val errorType: String = "",
    @SerialName("error_message") val errorMessage: String? = null,
    @SerialName("stack_trace") val stackTrace: String = "",
    @SerialName("thread_name") val threadName: String? = null,
    val resolved: Boolean = false,
    @SerialName("resolved_at") val resolvedAt: String? = null,
    @SerialName("resolved_by") val resolvedBy: String? = null,
    @SerialName("extra_metadata") val extraMetadata: JsonElement? = null,
    val solution: String? = null,
    @SerialName("created_at") val createdAt: String = ""
) {
    val createdAtEpoch: Long get() = parseIsoTimestamp(createdAt)
    val displayDevice: String get() = if (deviceManufacturer.isNotBlank() && deviceModel.isNotBlank()) {
        "$deviceManufacturer $deviceModel"
    } else deviceModel.ifBlank { deviceBrand }
}
