package com.example.tuctucmap.data.repository

import android.util.Log
import com.example.tuctucmap.data.model.*
import com.example.tuctucmap.data.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.text.SimpleDateFormat
import java.util.*

class AdminRepository(private val supabaseRepo: SupabaseRepository) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _pendingCounts = MutableStateFlow(Pair(0, 0))
    val pendingCounts: StateFlow<Pair<Int, Int>> = _pendingCounts.asStateFlow()

    private val _unresolvedCrashesCount = MutableStateFlow(0)
    val unresolvedCrashesCount: StateFlow<Int> = _unresolvedCrashesCount.asStateFlow()

    val currentAdminUid: String?
        get() = supabase.auth.currentUserOrNull()?.id

    val currentAdminEmail: String?
        get() = supabase.auth.currentUserOrNull()?.email

    suspend fun getUserRole(uid: String): String? = withContext(Dispatchers.IO) {
        try {
            val res = supabase.postgrest["profiles"].select {
                filter { eq("id", uid) }
            }.decodeSingleOrNull<UserRoleDto>()
            res?.role
        } catch (_: Exception) {
            null
        }
    }

    suspend fun isCurrentUserAdmin(): Boolean = withContext(Dispatchers.IO) {
        val uid = currentAdminUid ?: return@withContext false
        val role = getUserRole(uid)
        role == "admin"
    }

    suspend fun fetchDrivers(): Result<List<AdminDriver>> = withContext(Dispatchers.IO) {
        try {
            supabaseRepo.syncServerTimeAndExpire()
            val list = supabase.postgrest["profiles"].select {
                filter { eq("role", "driver") }
                order("created_at", Order.DESCENDING)
            }.decodeList<AdminDriver>()
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun approveDriver(
        uid: String,
        grantTrial: Boolean = true,
        trialDays: Int = 7
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val params = ApproveDriverRpcParams(
                driverId = uid,
                grantTrial = grantTrial,
                trialDays = trialDays
            )
            supabase.postgrest.rpc("approve_driver_by_admin", params)
            refreshPendingCounts()
            Result.success(Unit)
        } catch (e: Exception) {
            // Fallback transaccional por PostgREST directo
            try {
                val payload = mutableMapOf<String, Any?>("approval_status" to "aprobado")
                if (grantTrial && trialDays > 0) {
                    val cal = Calendar.getInstance()
                    cal.add(Calendar.DAY_OF_YEAR, trialDays)
                    val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }
                    val newEndDate = isoFormat.format(cal.time)
                    payload["subscription_end_date"] = newEndDate
                    supabase.postgrest["profiles"].update(payload) {
                        filter { eq("id", uid) }
                    }
                    supabase.postgrest["driver_subscriptions"].insert(
                        mapOf(
                            "driver_id" to uid,
                            "start_date" to isoFormat.format(Date()),
                            "end_date" to newEndDate,
                            "plan_type" to "trial",
                            "amount_paid" to 0.0,
                            "payment_method" to "gratis_prueba",
                            "reference_number" to "TRIAL-${trialDays}D"
                        )
                    )
                } else {
                    payload["subscription_end_date"] = null
                    supabase.postgrest["profiles"].update(payload) {
                        filter { eq("id", uid) }
                    }
                }
                refreshPendingCounts()
                Result.success(Unit)
            } catch (fallbackEx: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun rejectDriver(uid: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val payload = mapOf("approval_status" to "rechazado")
            supabase.postgrest["profiles"].update(payload) {
                filter { eq("id", uid) }
            }
            refreshPendingCounts()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setDriverSuspended(uid: String, suspend: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val status = if (suspend) "suspendido" else "aprobado"
            val payload = mutableMapOf<String, Any>(
                "approval_status" to status
            )
            if (suspend) {
                payload["is_available"] = false
                payload["is_busy"] = false
            }
            supabase.postgrest["profiles"].update(payload) {
                filter { eq("id", uid) }
            }
            refreshPendingCounts()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateDriverDetails(
        driverId: String,
        name: String,
        phone: String,
        plateNumber: String,
        unitNumber: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val payload = mapOf(
                "name" to name.trim(),
                "phone" to phone.trim(),
                "plate_number" to plateNumber.trim().uppercase(),
                "unit_number" to unitNumber.trim()
            )
            supabase.postgrest["profiles"].update(payload) {
                filter { eq("id", driverId) }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchDriverDossier(driverId: String): Result<DriverDossierDto> = withContext(Dispatchers.IO) {
        try {
            val driver = supabase.postgrest["profiles"].select {
                filter { eq("id", driverId) }
            }.decodeSingle<AdminDriver>()

            val subs = supabase.postgrest["driver_subscriptions"].select {
                filter { eq("driver_id", driverId) }
                order("created_at", Order.DESCENDING)
            }.decodeList<AdminSubscriptionHistory>()

            for (s in subs) {
                s.driverName = driver.nombre
                s.driverPlate = driver.placas
            }

            val totalPaid = subs.filter { it.status != "rechazado" && it.planType != "trial" }
                .sumOf { it.amountPaid }

            @Serializable
            data class TripStatDto(val fare: Double? = null, val status: String? = null)

            val trips = try {
                supabase.postgrest["trips"].select(Columns.list("fare", "status")) {
                    filter {
                        eq("driver_id", driverId)
                        eq("status", "completed")
                    }
                }.decodeList<TripStatDto>()
            } catch (_: Exception) {
                emptyList()
            }

            val totalCompleted = trips.size
            val totalFares = trips.sumOf { it.fare ?: 0.0 }

            Result.success(
                DriverDossierDto(
                    driver = driver,
                    totalPaidSubscriptions = totalPaid,
                    subscriptionsCount = subs.size,
                    subscriptions = subs,
                    totalTripsCompleted = totalCompleted,
                    totalFaresEarned = totalFares
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchAdminProfile(): Result<AdminProfileDto> = withContext(Dispatchers.IO) {
        val uid = currentAdminUid ?: return@withContext Result.failure(Exception("No hay sesión de administrador activa"))
        try {
            val prof = supabase.postgrest["profiles"].select {
                filter { eq("id", uid) }
            }.decodeSingle<AdminProfileDto>()
            Result.success(prof)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateAdminProfile(name: String, phone: String): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = currentAdminUid ?: return@withContext Result.failure(Exception("No autenticado"))
        try {
            supabase.postgrest["profiles"].update(
                mapOf("name" to name.trim(), "phone" to phone.trim())
            ) {
                filter { eq("id", uid) }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun changePasswordSecurely(currentPassword: String, newPassword: String): Result<Unit> = withContext(Dispatchers.IO) {
        val email = currentAdminEmail ?: return@withContext Result.failure(Exception("No hay correo asociado a la sesión"))
        if (newPassword.length < 6) {
            return@withContext Result.failure(Exception("La nueva contraseña debe tener al menos 6 caracteres"))
        }
        try {
            // Reautenticar primero para verificar que la contraseña actual es legítima
            supabase.auth.signInWith(Email) {
                this.email = email
                this.password = currentPassword
            }
        } catch (_: Exception) {
            return@withContext Result.failure(Exception("La contraseña actual es incorrecta. Verifica e intenta de nuevo."))
        }

        try {
            supabase.auth.updateUser {
                password = newPassword
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchAdminAccounts(): Result<List<AdminUserDto>> = withContext(Dispatchers.IO) {
        try {
            val list = supabase.postgrest["profiles"].select {
                filter { eq("role", "admin") }
                order("created_at", Order.ASCENDING)
            }.decodeList<AdminUserDto>()
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun promoteUserToAdmin(emailToPromote: String): Result<Unit> = withContext(Dispatchers.IO) {
        val targetEmail = emailToPromote.trim().lowercase()
        if (targetEmail.isBlank()) {
            return@withContext Result.failure(Exception("Ingresa un correo electrónico"))
        }
        try {
            val target = supabase.postgrest["profiles"].select {
                filter { eq("email", targetEmail) }
            }.decodeSingleOrNull<AdminUserDto>()

            if (target == null) {
                return@withContext Result.failure(Exception("No se encontró ningún usuario registrado con el correo '$targetEmail'."))
            }

            if (target.role == "admin") {
                return@withContext Result.failure(Exception("El usuario ya tiene rol de administrador."))
            }

            supabase.postgrest["profiles"].update(mapOf("role" to "admin")) {
                filter { eq("id", target.id) }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun revokeAdminRole(targetAdminId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val myUid = currentAdminUid
        if (targetAdminId == myUid) {
            return@withContext Result.failure(Exception("Por seguridad, no puedes revocar tu propia cuenta de administrador."))
        }
        try {
            supabase.postgrest["profiles"].update(mapOf("role" to "passenger")) {
                filter { eq("id", targetAdminId) }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteDriver(uid: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            try {
                supabase.postgrest.rpc(
                    "delete_user_by_admin",
                    DeleteUserByAdminRpcParams(targetUserId = uid)
                )
            } catch (_: Exception) {
                // Fallback directo a profiles si la RPC falla por alguna restricción
                supabase.postgrest["profiles"].delete {
                    filter { eq("id", uid) }
                }
            }
            refreshPendingCounts()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun renewDriverSubscriptionManual(
        driverId: String,
        planType: String = "semanal",
        amountPaid: Double = 25.0,
        paymentMethod: String = "deposito_bancario",
        referenceNumber: String = ""
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val nowMs = ServerClock.nowMillis()
            var baseTimeMs = nowMs

            // Consultar si aún tiene vigencia acumulable
            val currentProfile = supabase.postgrest["profiles"].select {
                filter { eq("id", driverId) }
            }.decodeSingleOrNull<AdminDriver>()

            val existingExpiryMs = currentProfile?.expiryTimestampMillis ?: 0L
            if (existingExpiryMs > baseTimeMs) {
                baseTimeMs = existingExpiryMs
            }

            val days = when (planType.lowercase()) {
                "mensual" -> 30
                "quincenal" -> 15
                "diario" -> 1
                else -> 7 // semanal o personalizado
            }
            val newEndMs = baseTimeMs + (days.toLong() * 24L * 60L * 60L * 1000L)
            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val newEndIso = isoFormat.format(Date(newEndMs))
            val nowIso = isoFormat.format(Date(nowMs))

            // 1. Actualizar perfil
            supabase.postgrest["profiles"].update(
                mapOf(
                    "approval_status" to "aprobado",
                    "subscription_end_date" to newEndIso
                )
            ) {
                filter { eq("id", driverId) }
            }

            // 2. Registrar en historial de membresías
            val subPayload = mapOf(
                "driver_id" to driverId,
                "start_date" to nowIso,
                "end_date" to newEndIso,
                "plan_type" to planType,
                "amount_paid" to amountPaid.toString(),
                "payment_method" to paymentMethod,
                "reference_number" to referenceNumber
            )
            supabase.postgrest["driver_subscriptions"].insert(subPayload)

            refreshPendingCounts()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFinancialStats(): Result<AdminFinancialStats> = withContext(Dispatchers.IO) {
        try {
            val cfg = supabaseRepo.getAppConfig()
            val nowMs = ServerClock.nowMillis()
            val sevenDaysLaterMs = nowMs + (7L * 24L * 60L * 60L * 1000L)

            val calNow = Calendar.getInstance()
            calNow.timeInMillis = nowMs
            val currentMonth = calNow.get(Calendar.MONTH)
            val currentYear = calNow.get(Calendar.YEAR)

            val (totalRevenue, monthRevenue) = try {
                val rpcSummary = supabase.postgrest.rpc("get_admin_financial_summary")
                    .decodeSingle<AdminFinancialSummaryDto>()
                Pair(rpcSummary.totalRevenue, rpcSummary.monthRevenue)
            } catch (e: Exception) {
                android.util.Log.w("AdminRepository", "RPC get_admin_financial_summary fallback: ${e.message}")
                val subs = supabase.postgrest["driver_subscriptions"].select().decodeList<AdminSubscriptionHistory>()
                var total = 0.0
                var month = 0.0
                for (sub in subs) {
                    val amt = sub.amountPaid
                    total += amt
                    if (sub.createdAtEpoch > 0L) {
                        val c = Calendar.getInstance().apply { timeInMillis = sub.createdAtEpoch }
                        if (c.get(Calendar.MONTH) == currentMonth && c.get(Calendar.YEAR) == currentYear) {
                            month += amt
                        }
                    }
                }
                Pair(total, month)
            }

            val drivers = supabase.postgrest["profiles"].select {
                filter {
                    eq("role", "driver")
                    eq("approval_status", "aprobado")
                }
            }.decodeList<AdminDriver>()

            var activeFleetCount = 0
            var expiringIn7Days = 0

            for (d in drivers) {
                val exp = d.expiryTimestampMillis
                if (exp >= nowMs) {
                    activeFleetCount++
                    if (exp <= sevenDaysLaterMs) {
                        expiringIn7Days++
                    }
                }
            }

            val priceForExpected = if (cfg.priceSemanal > 0.0) cfg.priceSemanal else 25.0
            val stats = AdminFinancialStats(
                totalRevenue = totalRevenue,
                monthRevenue = monthRevenue,
                activeFleetCount = activeFleetCount,
                expiringIn7Days = expiringIn7Days,
                expiringAmountExpected = expiringIn7Days * priceForExpected
            )
            Result.success(stats)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPaymentRequests(
        status: String? = "pendiente",
        limit: Long = 100
    ): Result<List<AdminPaymentRequest>> = withContext(Dispatchers.IO) {
        try {
            val requests = supabase.postgrest["subscription_payment_requests"].select {
                filter {
                    if (!status.isNullOrBlank()) {
                        eq("status", status)
                    }
                }
                order("created_at", Order.DESCENDING)
                limit(limit)
            }.decodeList<AdminPaymentRequest>()

            // Obtener únicamente los perfiles de los conductores involucrados en estas solicitudes
            val driverIds = requests.map { it.driverId }.filter { it.isNotBlank() }.distinct()
            val driverMap = if (driverIds.isNotEmpty()) {
                val drivers = supabase.postgrest["profiles"].select {
                    filter { isIn("id", driverIds) }
                }.decodeList<AdminDriver>()
                drivers.associateBy { it.id }
            } else {
                emptyMap()
            }

            for (req in requests) {
                val driver = driverMap[req.driverId]
                if (driver != null) {
                    req.driverName = driver.nombre.ifBlank { "Conductor" }
                    req.driverPhone = driver.telefono
                    req.driverPlate = driver.placas
                    req.driverUnitNumber = driver.numeroUnidad
                } else {
                    req.driverName = "Conductor"
                }
            }

            Result.success(requests)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun approvePaymentRequest(
        requestId: String,
        customEndDate: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            supabase.postgrest.rpc(
                "approve_subscription_payment_request",
                ApprovePaymentRequestRpcParams(
                    requestId = requestId,
                    customEndDate = customEndDate
                )
            )
            refreshPendingCounts()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun rejectPaymentRequest(
        requestId: String,
        reason: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            supabase.postgrest.rpc(
                "reject_subscription_payment_request",
                RejectPaymentRequestRpcParams(
                    requestId = requestId,
                    rejectionReason = reason
                )
            )
            refreshPendingCounts()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAllSubscriptionsHistory(
        startDateIso: String? = null,
        endDateIso: String? = null,
        limit: Long = 300
    ): Result<List<AdminSubscriptionHistory>> = withContext(Dispatchers.IO) {
        try {
            // 1. Membresías aprobadas / registradas
            val approvedList = supabase.postgrest["driver_subscriptions"].select {
                filter {
                    if (!startDateIso.isNullOrBlank()) {
                        gte("created_at", startDateIso)
                    }
                    if (!endDateIso.isNullOrBlank()) {
                        lte("created_at", endDateIso)
                    }
                }
                order("created_at", Order.DESCENDING)
                limit(limit)
            }.decodeList<AdminSubscriptionHistory>()

            // 2. Solicitudes de pago rechazadas
            val rejectedRequests = supabase.postgrest["subscription_payment_requests"].select {
                filter {
                    eq("status", "rechazado")
                    if (!startDateIso.isNullOrBlank()) {
                        gte("created_at", startDateIso)
                    }
                    if (!endDateIso.isNullOrBlank()) {
                        lte("created_at", endDateIso)
                    }
                }
                order("created_at", Order.DESCENDING)
                limit(limit)
            }.decodeList<AdminPaymentRequest>()

            val rejectedItems = rejectedRequests.map { req ->
                AdminSubscriptionHistory(
                    id = req.id,
                    driverId = req.driverId,
                    planType = req.planType,
                    amountPaid = req.amount,
                    paymentMethod = req.paymentMethod,
                    referenceNumber = req.referenceNumber,
                    startDate = req.createdAt,
                    endDate = "",
                    createdAt = req.createdAt,
                    status = "rechazado",
                    rejectionReason = req.rejectionReason,
                    driverName = req.driverName,
                    driverPlate = req.driverPlate
                )
            }

            // 3. Obtener nombres y placas de conductores para todos los registros
            val driverIds = (approvedList.map { it.driverId } + rejectedItems.map { it.driverId })
                .filter { it.isNotBlank() }
                .distinct()

            val map = if (driverIds.isNotEmpty()) {
                val drivers = supabase.postgrest["profiles"].select {
                    filter { isIn("id", driverIds) }
                }.decodeList<AdminDriver>()
                drivers.associateBy { it.id }
            } else {
                emptyMap()
            }

            for (item in approvedList) {
                item.status = "aprobado"
                val d = map[item.driverId]
                if (d != null) {
                    item.driverName = d.nombre.ifBlank { "Conductor" }
                    item.driverPlate = d.displayIdentifier
                }
            }

            for (item in rejectedItems) {
                val d = map[item.driverId]
                if (d != null) {
                    item.driverName = d.nombre.ifBlank { "Conductor" }
                    item.driverPlate = d.displayIdentifier
                }
            }

            val combined = (approvedList + rejectedItems).sortedByDescending { it.createdAtEpoch }
            Result.success(combined)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    data class TripDateBounds(
        val minEpochMs: Long,
        val maxEpochMs: Long
    )

    suspend fun getTripDateBounds(): Result<TripDateBounds> = withContext(Dispatchers.IO) {
        try {
            @kotlinx.serialization.Serializable
            data class CreatedAtDto(@SerialName("created_at") val createdAt: String? = null)

            val oldest = supabase.postgrest["trips"].select(Columns.list("created_at")) {
                order("created_at", Order.ASCENDING)
                limit(1)
            }.decodeList<CreatedAtDto>().firstOrNull()

            val newest = supabase.postgrest["trips"].select(Columns.list("created_at")) {
                order("created_at", Order.DESCENDING)
                limit(1)
            }.decodeList<CreatedAtDto>().firstOrNull()

            val now = System.currentTimeMillis()
            val minEpoch = oldest?.createdAt?.let { parseIsoTimestamp(it) } ?: now
            val maxEpoch = newest?.createdAt?.let { parseIsoTimestamp(it) } ?: now

            Result.success(TripDateBounds(minEpochMs = minEpoch, maxEpochMs = maxEpoch.coerceAtLeast(now)))
        } catch (e: Exception) {
            android.util.Log.e("AdminRepository", "Error getting trip date bounds: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getCompletedTrips(
        startDateIso: String? = null,
        endDateIso: String? = null,
        driverIds: List<String>? = null,
        limit: Long = 500
    ): Result<List<AdminTrip>> = withContext(Dispatchers.IO) {
        try {
            val tripCols = Columns.list(
                "id", "passenger_id", "passenger_name", "passenger_phone",
                "driver_id", "status", "message", "fare", "distance_km",
                "passenger_lat", "passenger_lon", "created_at"
            )

            val trips = supabase.postgrest["trips"].select(columns = tripCols) {
                filter {
                    eq("status", "completed")
                    if (!startDateIso.isNullOrBlank()) {
                        gte("created_at", startDateIso)
                    }
                    if (!endDateIso.isNullOrBlank()) {
                        lte("created_at", endDateIso)
                    }
                    if (!driverIds.isNullOrEmpty()) {
                        isIn("driver_id", driverIds)
                    }
                }
                order("created_at", Order.DESCENDING)
                limit(limit)
            }.decodeList<AdminTrip>()

            @kotlinx.serialization.Serializable
            data class ProfileLookup(
                val id: String = "",
                val name: String? = null,
                val phone: String? = null
            )

            val userIds = trips.flatMap { listOfNotNull(it.driverId, it.passengerId) }
                .filter { it.isNotBlank() }
                .distinct()

            val profileMap = if (userIds.isNotEmpty()) {
                try {
                    val list = supabase.postgrest["profiles"]
                        .select(columns = Columns.list("id", "name", "phone")) {
                            filter { isIn("id", userIds) }
                        }
                        .decodeList<ProfileLookup>()
                    list.associateBy { it.id }
                } catch (e: Exception) {
                    android.util.Log.w("AdminRepository", "Profile lookup fallback in getCompletedTrips: ${e.message}")
                    emptyMap()
                }
            } else emptyMap()

            for (t in trips) {
                val directName = t.passengerNameDirect?.trim()
                val mappedPassenger = t.passengerId?.let { profileMap[it] }
                t.resolvedPassengerName = if (!directName.isNullOrBlank()) {
                    directName
                } else if (!mappedPassenger?.name.isNullOrBlank()) {
                    mappedPassenger!!.name!!
                } else {
                    "Pasajero (Invitado)"
                }

                t.resolvedPassengerPhone = t.passengerPhoneDirect?.trim()
                    ?.ifBlank { null }
                    ?: mappedPassenger?.phone?.trim()
                    ?: ""

                t.isRegisteredPassenger = !t.passengerId.isNullOrBlank()

                val driver = t.driverId?.let { profileMap[it] }
                t.resolvedDriverName = driver?.name?.trim()?.ifBlank { "TucTuc" } ?: "TucTuc"
            }

            Result.success(trips)
        } catch (e: Exception) {
            android.util.Log.e("AdminRepository", "Error in getCompletedTrips: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getTripRoutePoints(tripId: String): Result<List<SupabaseRepository.RoutePoint>> = withContext(Dispatchers.IO) {
        try {
            @kotlinx.serialization.Serializable
            data class TripRouteDto(
                @SerialName("route_points")
                val routePoints: JsonElement? = null
            )

            val data = supabase.postgrest["trips"].select(Columns.list("route_points")) {
                filter { eq("id", tripId) }
            }.decodeSingleOrNull<TripRouteDto>()

            val raw = data?.routePoints
            if (raw == null || raw is JsonNull) {
                return@withContext Result.success(emptyList())
            }

            val list = mutableListOf<SupabaseRepository.RoutePoint>()
            when (raw) {
                is JsonArray -> {
                    for (elem in raw) {
                        try {
                            val pt = json.decodeFromJsonElement<SupabaseRepository.RoutePoint>(elem)
                            if (pt.lat != 0.0 && pt.lon != 0.0) {
                                list.add(pt)
                            }
                        } catch (_: Exception) {
                            if (elem is JsonObject) {
                                val lat = elem["lat"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                                val lon = elem["lon"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                                val ts = elem["timestamp"]?.jsonPrimitive?.longOrNull ?: 0L
                                if (lat != 0.0 && lon != 0.0) {
                                    list.add(SupabaseRepository.RoutePoint(lat, lon, ts))
                                }
                            }
                        }
                    }
                }
                is JsonPrimitive -> {
                    val str = raw.content.trim()
                    if (str.isNotBlank()) {
                        try {
                            val parsed = json.decodeFromString<List<SupabaseRepository.RoutePoint>>(str)
                            list.addAll(parsed.filter { it.lat != 0.0 && it.lon != 0.0 })
                        } catch (_: Exception) {
                            try {
                                val element = json.parseToJsonElement(str)
                                if (element is JsonArray) {
                                    for (elem in element) {
                                        try {
                                            val pt = json.decodeFromJsonElement<SupabaseRepository.RoutePoint>(elem)
                                            if (pt.lat != 0.0 && pt.lon != 0.0) {
                                                list.add(pt)
                                            }
                                        } catch (_: Exception) {
                                            if (elem is JsonObject) {
                                                val lat = elem["lat"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                                                val lon = elem["lon"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                                                val ts = elem["timestamp"]?.jsonPrimitive?.longOrNull ?: 0L
                                                if (lat != 0.0 && lon != 0.0) {
                                                    list.add(SupabaseRepository.RoutePoint(lat, lon, ts))
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }
                else -> {}
            }
            Result.success(list)
        } catch (e: Exception) {
            android.util.Log.e("AdminRepository", "Error in getTripRoutePoints: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getPassengers(): Result<List<AdminPassenger>> = withContext(Dispatchers.IO) {
        try {
            val list = try {
                supabase.postgrest.rpc("get_admin_passengers_summary")
                    .decodeList<AdminPassenger>()
            } catch (e: Exception) {
                android.util.Log.w("AdminRepository", "RPC get_admin_passengers_summary fallback: ${e.message}")
                val fallbackList = supabase.postgrest["profiles"].select {
                    filter { eq("role", "passenger") }
                    order("created_at", Order.DESCENDING)
                }.decodeList<AdminPassenger>()

                val passengerIds = fallbackList.map { it.id }.filter { it.isNotBlank() }
                if (passengerIds.isNotEmpty()) {
                    @kotlinx.serialization.Serializable
                    data class TripPassengerIdDto(val passenger_id: String? = null)

                    val trips = supabase.postgrest["trips"].select(columns = Columns.list("passenger_id")) {
                        filter { isIn("passenger_id", passengerIds) }
                    }.decodeList<TripPassengerIdDto>()
                    val counts = mutableMapOf<String, Int>()
                    for (t in trips) {
                        t.passenger_id?.let { pid ->
                            counts[pid] = (counts[pid] ?: 0) + 1
                        }
                    }
                    for (p in fallbackList) {
                        p.tripsCount = counts[p.id] ?: 0
                    }
                }
                fallbackList
            }

            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @Serializable
    private data class UpdateAppConfigDto(
        @SerialName("admin_whatsapp")
        val adminWhatsapp: String,
        @SerialName("admin_whatsapp_phone")
        val adminWhatsappPhone: String,
        @SerialName("bank_1_name")
        val bank1Name: String,
        @SerialName("bank_1_account")
        val bank1Account: String,
        @SerialName("banrural_account")
        val banruralAccount: String,
        @SerialName("bank_2_name")
        val bank2Name: String,
        @SerialName("bank_2_account")
        val bank2Account: String,
        @SerialName("bi_account")
        val biAccount: String,
        @SerialName("account_holder_name")
        val accountHolderName: String,
        @SerialName("currency_symbol")
        val currencySymbol: String,
        @SerialName("price_daily")
        val priceDaily: Double,
        @SerialName("price_weekly")
        val priceWeekly: Double,
        @SerialName("price_semanal")
        val priceSemanal: Double,
        @SerialName("price_quincenal")
        val priceQuincenal: Double,
        @SerialName("price_monthly")
        val priceMonthly: Double,
        @SerialName("price_mensual")
        val priceMensual: Double,
        @SerialName("search_radius_km")
        val searchRadiusKm: Double,
        @SerialName("max_search_radius_km")
        val maxSearchRadiusKm: Double
    )

    suspend fun updateAppConfig(config: AppConfig): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val dto = UpdateAppConfigDto(
                adminWhatsapp = config.adminWhatsapp,
                adminWhatsappPhone = config.adminWhatsapp,
                bank1Name = config.bank1Name,
                bank1Account = config.bank1Account,
                banruralAccount = config.bank1Account,
                bank2Name = config.bank2Name,
                bank2Account = config.bank2Account,
                biAccount = config.bank2Account,
                accountHolderName = config.accountHolderName,
                currencySymbol = config.currencySymbol,
                priceDaily = config.priceDaily,
                priceWeekly = config.priceWeekly,
                priceSemanal = config.priceWeekly,
                priceQuincenal = config.priceQuincenal,
                priceMonthly = config.priceMonthly,
                priceMensual = config.priceMonthly,
                searchRadiusKm = config.searchRadiusKm,
                maxSearchRadiusKm = config.searchRadiusKm
            )
            supabase.postgrest["app_config"].update(dto) {
                filter { eq("id", "default") }
            }
            supabaseRepo.getAppConfig(force = true)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("AdminRepository", "Error actualizando app_config", e)
            Result.failure(e)
        }
    }

    @Serializable
    private data class PendingCountIdDto(val id: String = "")

    /**
     * Consulta Supabase y actualiza de inmediato el StateFlow de conteos pendientes
     * (conductores pendientes, solicitudes de pago pendientes).
     * Retorna el Pair(pendingDrivers, pendingRequests).
     */
    suspend fun refreshPendingCounts(): Pair<Int, Int> = withContext(Dispatchers.IO) {
        try {
            val pendingDrivers = supabase.postgrest["profiles"].select(Columns.list("id")) {
                filter {
                    eq("role", "driver")
                    eq("approval_status", "pendiente")
                }
            }.decodeList<PendingCountIdDto>().size

            val pendingRequests = supabase.postgrest["subscription_payment_requests"].select(Columns.list("id")) {
                filter { eq("status", "pendiente") }
            }.decodeList<PendingCountIdDto>().size

            val pendingCrashes = try {
                supabase.postgrest["app_crash_logs"].select(Columns.list("id")) {
                    filter { eq("resolved", false) }
                }.decodeList<PendingCountIdDto>().size
            } catch (_: Exception) { 0 }
            _unresolvedCrashesCount.value = pendingCrashes

            val result = Pair(pendingDrivers, pendingRequests)
            _pendingCounts.value = result
            result
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w("AdminRepository", "Error al refrescar conteos pendientes: ${e.message}")
            _pendingCounts.value
        }
    }

    /**
     * Emite los conteos pendientes reactivamente a través de StateFlow
     * (conductores pendientes, solicitudes de pago pendientes).
     */
    fun observePendingCounts(): Flow<Pair<Int, Int>> = _pendingCounts.asStateFlow()

    // --- Telemetría y Reportes de Errores (Crash Logs) ---

    suspend fun getCrashLogs(filterResolved: Boolean? = null): Result<List<AdminCrashLog>> = withContext(Dispatchers.IO) {
        try {
            val list = supabase.postgrest["app_crash_logs"].select {
                if (filterResolved != null) {
                    filter { eq("resolved", filterResolved) }
                }
                order("created_at", Order.DESCENDING)
            }.decodeList<AdminCrashLog>()
            Result.success(list)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }

    suspend fun setCrashLogResolved(id: String, resolved: Boolean, solution: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date())

            val trimmedSolution = if (resolved && !solution.isNullOrBlank()) {
                solution.trim().take(300)
            } else null

            val payload = mapOf(
                "resolved" to resolved.toString(),
                "resolved_at" to if (resolved) nowIso else null,
                "resolved_by" to if (resolved) currentAdminUid else null,
                "solution" to trimmedSolution
            )
            supabase.postgrest["app_crash_logs"].update(payload) {
                filter { eq("id", id) }
            }
            refreshPendingCounts()
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }

    suspend fun deleteCrashLog(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            supabase.postgrest["app_crash_logs"].delete {
                filter { eq("id", id) }
            }
            refreshPendingCounts()
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }
}

