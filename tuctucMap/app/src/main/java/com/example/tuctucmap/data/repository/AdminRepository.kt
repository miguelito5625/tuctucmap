package com.example.tuctucmap.data.repository

import com.example.tuctucmap.data.model.*
import com.example.tuctucmap.data.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.*
import java.text.SimpleDateFormat
import java.util.*

class AdminRepository(private val supabaseRepo: SupabaseRepository) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

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

    suspend fun approveDriver(uid: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val payload = mapOf("approval_status" to "aprobado")
            supabase.postgrest["profiles"].update(payload) {
                filter { eq("id", uid) }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun rejectDriver(uid: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val payload = mapOf("approval_status" to "rechazado")
            supabase.postgrest["profiles"].update(payload) {
                filter { eq("id", uid) }
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

            val subs = supabase.postgrest["driver_subscriptions"].select().decodeList<AdminSubscriptionHistory>()
            val drivers = supabase.postgrest["profiles"].select {
                filter {
                    eq("role", "driver")
                    eq("approval_status", "aprobado")
                }
            }.decodeList<AdminDriver>()

            var totalRevenue = 0.0
            var monthRevenue = 0.0

            for (sub in subs) {
                val amt = sub.amountPaid
                totalRevenue += amt
                if (sub.createdAtEpoch > 0L) {
                    val c = Calendar.getInstance().apply { timeInMillis = sub.createdAtEpoch }
                    if (c.get(Calendar.MONTH) == currentMonth && c.get(Calendar.YEAR) == currentYear) {
                        monthRevenue += amt
                    }
                }
            }

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

    suspend fun getPaymentRequests(): Result<List<AdminPaymentRequest>> = withContext(Dispatchers.IO) {
        try {
            val requests = supabase.postgrest["subscription_payment_requests"].select {
                order("created_at", Order.DESCENDING)
            }.decodeList<AdminPaymentRequest>()

            // Obtener lista de perfiles de conductores para cruzar nombres y placas
            val drivers = supabase.postgrest["profiles"].select {
                filter { eq("role", "driver") }
            }.decodeList<AdminDriver>()
            val driverMap = drivers.associateBy { it.id }

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
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAllSubscriptionsHistory(): Result<List<AdminSubscriptionHistory>> = withContext(Dispatchers.IO) {
        try {
            val list = supabase.postgrest["driver_subscriptions"].select {
                order("created_at", Order.DESCENDING)
            }.decodeList<AdminSubscriptionHistory>()

            val drivers = supabase.postgrest["profiles"].select {
                filter { eq("role", "driver") }
            }.decodeList<AdminDriver>()
            val map = drivers.associateBy { it.id }

            for (item in list) {
                val d = map[item.driverId]
                if (d != null) {
                    item.driverName = d.nombre.ifBlank { "Conductor" }
                    item.driverPlate = d.displayIdentifier
                }
            }

            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCompletedTrips(limit: Long = 100): Result<List<AdminTrip>> = withContext(Dispatchers.IO) {
        try {
            val tripCols = Columns.list(
                "id", "passenger_id", "passenger_name", "passenger_phone",
                "driver_id", "status", "message", "fare", "distance_km",
                "passenger_lat", "passenger_lon", "created_at"
            )

            val trips = supabase.postgrest["trips"].select(columns = tripCols) {
                order("created_at", Order.DESCENDING)
                limit(limit)
            }.decodeList<AdminTrip>()

            @kotlinx.serialization.Serializable
            data class ProfileLookup(
                val id: String = "",
                val name: String? = null,
                val phone: String? = null
            )

            val profileMap = try {
                val list = supabase.postgrest["profiles"]
                    .select(columns = Columns.list("id", "name", "phone"))
                    .decodeList<ProfileLookup>()
                list.associateBy { it.id }
            } catch (e: Exception) {
                android.util.Log.w("AdminRepository", "Profile lookup fallback in getCompletedTrips: ${e.message}")
                emptyMap()
            }

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
            val list = supabase.postgrest["profiles"].select {
                filter { eq("role", "passenger") }
                order("created_at", Order.DESCENDING)
            }.decodeList<AdminPassenger>()

            @kotlinx.serialization.Serializable
            data class TripPassengerIdDto(val passenger_id: String? = null)

            val trips = supabase.postgrest["trips"].select(columns = Columns.list("passenger_id")).decodeList<TripPassengerIdDto>()
            val counts = mutableMapOf<String, Int>()
            for (t in trips) {
                t.passenger_id?.let { pid ->
                    counts[pid] = (counts[pid] ?: 0) + 1
                }
            }

            for (p in list) {
                p.tripsCount = counts[p.id] ?: 0
            }

            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateAppConfig(config: AppConfig): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val payload = mapOf(
                "admin_whatsapp" to config.adminWhatsapp,
                "admin_whatsapp_phone" to config.adminWhatsapp,
                "bank_1_name" to config.bank1Name,
                "bank_1_account" to config.bank1Account,
                "banrural_account" to config.bank1Account,
                "bank_2_name" to config.bank2Name,
                "bank_2_account" to config.bank2Account,
                "bi_account" to config.bank2Account,
                "account_holder_name" to config.accountHolderName,
                "currency_symbol" to config.currencySymbol,
                "price_daily" to config.priceDaily,
                "price_weekly" to config.priceWeekly,
                "price_semanal" to config.priceWeekly,
                "price_quincenal" to config.priceQuincenal,
                "price_monthly" to config.priceMonthly,
                "price_mensual" to config.priceMonthly,
                "search_radius_km" to config.searchRadiusKm,
                "max_search_radius_km" to config.searchRadiusKm
            )
            supabase.postgrest["app_config"].update(payload) {
                filter { eq("id", "default") }
            }
            supabaseRepo.getAppConfig(force = true)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Emite los conteos pendientes (Conductores pendientes, Solicitudes de pago pendientes)
     */
    fun observePendingCounts(): Flow<Pair<Int, Int>> = flow {
        while (true) {
            try {
                val pendingDrivers = supabase.postgrest["profiles"].select {
                    filter {
                        eq("role", "driver")
                        eq("approval_status", "pendiente")
                    }
                }.decodeList<AdminDriver>().size

                val pendingRequests = supabase.postgrest["subscription_payment_requests"].select {
                    filter { eq("status", "pendiente") }
                }.decodeList<AdminPaymentRequest>().size

                emit(Pair(pendingDrivers, pendingRequests))
            } catch (_: Exception) {
                emit(Pair(0, 0))
            }
            kotlinx.coroutines.delay(12_000L) // Refresco suave cada 12 segundos
        }
    }.flowOn(Dispatchers.IO)
}

