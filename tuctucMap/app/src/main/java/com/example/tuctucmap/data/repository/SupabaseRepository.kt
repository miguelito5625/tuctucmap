package com.example.tuctucmap.data.repository

import com.example.tuctucmap.data.model.Driver
import com.example.tuctucmap.data.model.Passenger
import com.example.tuctucmap.data.model.DriverLocation
import com.example.tuctucmap.data.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

class SupabaseRepository {

    val currentUserUid: String?
        get() = supabase.auth.currentUserOrNull()?.id

    val isUserLoggedIn: Boolean
        get() = supabase.auth.currentUserOrNull() != null

    suspend fun logout() {
        supabase.auth.signOut()
    }

    suspend fun registerDriver(
        nombre: String,
        telefono: String,
        placas: String,
        email: String,
        contrasena: String
    ): Result<Unit> {
        return try {
            supabase.auth.signUpWith(Email) {
                this.email = email
                this.password = contrasena
            }
            
            var uid = currentUserUid
            if (uid == null) {
                try {
                    supabase.auth.signInWith(Email) {
                        this.email = email
                        this.password = contrasena
                    }
                    uid = currentUserUid
                } catch (e: Exception) { }
            }
            
            if (uid == null) return Result.failure(Exception("Por favor desactiva 'Confirm email' en Supabase (Authentication -> Providers -> Email)"))
            
            
            // Insert profile con 7 días de prueba gratis y estado aprobado
            val now = java.time.Instant.now()
            val trialStartDate = now.toString() // ISO 8601
            val trialEndDate = now.plusSeconds(7 * 24 * 60 * 60).toString()
            
            val profile = mapOf(
                "id" to uid,
                "email" to email,
                "name" to nombre,
                "phone" to telefono,
                "role" to "driver",
                "plate_number" to placas,
                "approval_status" to "aprobado",
                "subscription_end_date" to trialEndDate
            )
            supabase.postgrest["profiles"].insert(profile)
            
            val subscription = mapOf(
                "driver_id" to uid,
                "start_date" to trialStartDate,
                "end_date" to trialEndDate
            )
            supabase.postgrest["driver_subscriptions"].insert(subscription)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loginDriver(
        email: String,
        contrasena: String
    ): Result<Unit> {
        return try {
            supabase.auth.signInWith(Email) {
                this.email = email
                this.password = contrasena
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loginPassengerWithEmail(email: String, password: String): Result<Unit> {
        return try {
            supabase.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun registerPassengerWithEmail(
        nombre: String,
        telefono: String,
        email: String,
        contrasena: String
    ): Result<Unit> {
        return try {
            supabase.auth.signUpWith(Email) {
                this.email = email
                this.password = contrasena
            }
            
            var uid = currentUserUid
            if (uid == null) {
                try {
                    supabase.auth.signInWith(Email) {
                        this.email = email
                        this.password = contrasena
                    }
                    uid = currentUserUid
                } catch (e: Exception) { }
            }
            
            if (uid == null) return Result.failure(Exception("Por favor desactiva 'Confirm email' en Supabase (Authentication -> Providers -> Email)"))
            
            
            val profile = mapOf(
                "id" to uid,
                "email" to email,
                "name" to nombre,
                "phone" to telefono,
                "role" to "passenger"
            )
            supabase.postgrest["profiles"].insert(profile)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getDriverProfileOnce(uid: String, onResult: (Driver?) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val driver = supabase.postgrest["profiles"].select {
                    filter { eq("id", uid) }
                }.decodeSingleOrNull<Driver>()
                onResult(driver)
            } catch (e: Exception) {
                onResult(null)
            }
        }
    }

    fun getPassengerProfileOnce(uid: String, onResult: (Passenger?) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val passenger = supabase.postgrest["profiles"].select {
                    filter { eq("id", uid) }
                }.decodeSingleOrNull<Passenger>()
                onResult(passenger)
            } catch (e: Exception) {
                onResult(null)
            }
        }
    }

    @kotlinx.serialization.Serializable
    private data class LocationUpdateDto(
        val driver_id: String,
        val lat: Double,
        val lon: Double,
        val active: Boolean
    )

    suspend fun updateLocation(uid: String, nombre: String, telefono: String, placas: String, latitud: Double, longitud: Double) {
        try {
            val location = mapOf(
                "driver_id" to uid,
                "lat" to latitud,
                "lon" to longitud,
                "nombre" to nombre,
                "telefono" to telefono,
                "placas" to placas,
                "active" to true,
                "updated_at" to System.currentTimeMillis().toString()
            )
            Firebase.database.getReference("driver_locations").child(uid).setValue(location)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun removeLocation(uid: String) {
        try {
            Firebase.database.getReference("driver_locations").child(uid).removeValue()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    data class TripInfo(
        val tripId: String = "",
        val passengerId: String = "",
        val driverId: String = "",
        val message: String = "",
        val passengerLat: Double = 0.0,
        val passengerLon: Double = 0.0,
        val status: String = "", // "requested", "accepted", "completed", "cancelled"
        val timestamp: Long = 0L
    )

    @kotlinx.serialization.Serializable
    data class CompletedTripDto(
        val passenger_id: String,
        val driver_id: String,
        val passenger_lat: Double,
        val passenger_lon: Double,
        val status: String,
        val message: String
    )

    @kotlinx.serialization.Serializable
    data class CompletedTripResponseDto(
        val id: String = "",
        val passenger_id: String = "",
        val driver_id: String = "",
        val passenger_lat: Double = 0.0,
        val passenger_lon: Double = 0.0,
        val status: String = "",
        val message: String = "",
        val created_at: String = "",
        var passenger_name: String = "Desconocido",
        var driver_name: String = "Desconocido",
        var passenger_phone: String = "",
        var driver_phone: String = ""
    )

    // History Trips (Supabase)
    suspend fun saveCompletedTripToSupabase(tripInfo: TripInfo): Result<Unit> {
        return try {
            val dto = CompletedTripDto(
                passenger_id = tripInfo.passengerId,
                driver_id = tripInfo.driverId,
                passenger_lat = tripInfo.passengerLat,
                passenger_lon = tripInfo.passengerLon,
                status = "completed",
                message = tripInfo.message
            )
            supabase.postgrest["trips"].insert(dto)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPassengerTripHistory(): Result<List<CompletedTripResponseDto>> {
        return try {
            val uid = currentUserUid ?: return Result.failure(Exception("No user"))
            val trips = supabase.postgrest["trips"].select {
                filter { eq("passenger_id", uid) }
            }.decodeList<CompletedTripResponseDto>()

            // Fetch driver names
            val driverIds = trips.map { it.driver_id }.distinct()
            if (driverIds.isNotEmpty()) {
                val drivers = supabase.postgrest["profiles"].select {
                    filter { isIn("id", driverIds) }
                }.decodeList<Driver>()
                val driverMap = drivers.associateBy({ it.id }, { it.nombre })
                val driverPhoneMap = drivers.associateBy({ it.id }, { it.telefono })
                trips.forEach { trip ->
                    trip.driver_name = driverMap[trip.driver_id] ?: "Desconocido"
                    trip.driver_phone = driverPhoneMap[trip.driver_id] ?: ""
                }
            }

            Result.success(trips)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getDriverTripHistory(): Result<List<CompletedTripResponseDto>> {
        return try {
            val uid = currentUserUid ?: return Result.failure(Exception("No user"))
            val trips = supabase.postgrest["trips"].select {
                filter { eq("driver_id", uid) }
            }.decodeList<CompletedTripResponseDto>()

            // Fetch passenger names
            val passengerIds = trips.map { it.passenger_id }.distinct()
            if (passengerIds.isNotEmpty()) {
                val passengers = supabase.postgrest["profiles"].select {
                    filter { isIn("id", passengerIds) }
                }.decodeList<Passenger>()
                val passengerMap = passengers.associateBy({ it.id }, { it.nombre })
                val passengerPhoneMap = passengers.associateBy({ it.id }, { it.telefono })
                trips.forEach { trip ->
                    trip.passenger_name = passengerMap[trip.passenger_id] ?: "Desconocido"
                    trip.passenger_phone = passengerPhoneMap[trip.passenger_id] ?: ""
                }
            }

            Result.success(trips)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Trips via Firebase
    suspend fun requestTrip(driverId: String, lat: Double, lon: Double, message: String): Result<String> {
        return try {
            val uid = currentUserUid ?: return Result.failure(Exception("No user"))
            val tripsRef = Firebase.database.getReference("trips")
            val newTripRef = tripsRef.push()
            val tripId = newTripRef.key ?: java.util.UUID.randomUUID().toString()
            
            val trip = TripInfo(
                tripId = tripId,
                passengerId = uid,
                driverId = driverId,
                message = message,
                passengerLat = lat,
                passengerLon = lon,
                status = "requested",
                timestamp = System.currentTimeMillis()
            )
            newTripRef.setValue(trip)
            Result.success(tripId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observePendingTripsForDriver(driverId: String): Flow<List<TripInfo>> = callbackFlow {
        val tripsRef = Firebase.database.getReference("trips")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val pendingTrips = mutableListOf<TripInfo>()
                val now = System.currentTimeMillis()
                for (child in snapshot.children) {
                    val trip = child.getValue(TripInfo::class.java)
                    if (trip != null && trip.driverId == driverId) {
                        if (trip.status == "requested") {
                            // Filter out trips older than 60 seconds (TTL)
                            if (now - trip.timestamp <= 60000) {
                                pendingTrips.add(trip)
                            } else {
                                // Cleanup expired requested trips
                                child.ref.removeValue()
                            }
                        } else {
                            // Cleanup terminal states older than 2 minutes
                            if (now - trip.timestamp > 120000) {
                                child.ref.removeValue()
                            }
                        }
                    }
                }
                pendingTrips.sortBy { it.timestamp } // Oldest requests first
                trySend(pendingTrips)
            }
            override fun onCancelled(error: DatabaseError) {
                error.toException().printStackTrace()
            }
        }
        val query = tripsRef.orderByChild("driverId").equalTo(driverId)
        query.addValueEventListener(listener)
        awaitClose { query.removeEventListener(listener) }
    }

    fun observeTrip(tripId: String): Flow<TripInfo?> = callbackFlow {
        val tripRef = Firebase.database.getReference("trips").child(tripId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val trip = snapshot.getValue(TripInfo::class.java)
                trySend(trip)
            }
            override fun onCancelled(error: DatabaseError) {
                error.toException().printStackTrace()
            }
        }
        tripRef.addValueEventListener(listener)
        awaitClose { tripRef.removeEventListener(listener) }
    }

    suspend fun updateTripStatus(tripId: String, status: String): Result<Unit> {
        return try {
            Firebase.database.getReference("trips").child(tripId).child("status").setValue(status)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteTrip(tripId: String): Result<Unit> {
        return try {
            Firebase.database.getReference("trips").child(tripId).removeValue()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeActiveLocations(): Flow<List<DriverLocation>> = callbackFlow {
        val databaseRef = Firebase.database.getReference("driver_locations")
        
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val locations = mutableListOf<DriverLocation>()
                for (child in snapshot.children) {
                    try {
                        val active = child.child("active").getValue(Boolean::class.java) ?: false
                        if (active) {
                            val uid = child.child("driver_id").getValue(String::class.java) ?: ""
                            val lat = child.child("lat").getValue(Double::class.java) ?: 0.0
                            val lon = child.child("lon").getValue(Double::class.java) ?: 0.0
                            val nombre = child.child("nombre").getValue(String::class.java) ?: ""
                            val telefono = child.child("telefono").getValue(String::class.java) ?: ""
                            val placas = child.child("placas").getValue(String::class.java) ?: ""
                            
                            locations.add(
                                DriverLocation(
                                    uid = uid,
                                    lat = lat,
                                    lon = lon,
                                    activo = active,
                                    nombre = nombre,
                                    telefono = telefono,
                                    placas = placas
                                )
                            )
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                trySend(locations)
            }
            override fun onCancelled(error: DatabaseError) {
                error.toException().printStackTrace()
            }
        }
        
        databaseRef.addValueEventListener(listener)
        awaitClose { databaseRef.removeEventListener(listener) }
    }

    fun observeDriverProfile(uid: String): Flow<Driver?> = callbackFlow {
        val channel = supabase.channel("public:profiles:driver_$uid")
        val flow = channel.postgresChangeFlow<PostgresAction>("public") { 
            table = "profiles"
        }
        
        launch {
            channel.subscribe()
            try {
                val initialProfile = supabase.postgrest["profiles"].select {
                    filter { eq("id", uid) }
                }.decodeSingleOrNull<Driver>()
                trySend(initialProfile)
            } catch (e: Exception) { }
            
            flow.collect {
                try {
                    val profile = supabase.postgrest["profiles"].select {
                        filter { eq("id", uid) }
                    }.decodeSingleOrNull<Driver>()
                    trySend(profile)
                } catch(e: Exception) { }
            }
        }
        
        awaitClose { launch { channel.unsubscribe() } }
    }
}
