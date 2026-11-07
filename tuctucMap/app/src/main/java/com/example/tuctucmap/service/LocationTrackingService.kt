package com.example.tuctucmap.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.tuctucmap.MainActivity
import com.example.tuctucmap.R
import com.example.tuctucmap.data.SecurePrefs
import com.example.tuctucmap.data.model.ServerClock
import com.example.tuctucmap.data.repository.SupabaseRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Collections
import java.util.Locale

class LocationTrackingService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val repository = SupabaseRepository()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pendingTripsJob: Job? = null
    private var statusObserverJob: Job? = null
    private var isLocationUpdatesActive = false
    private var currentGpsPriority: Int? = null
    private var currentGpsIntervalMs: Long? = null

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private var driverUid: String = ""
    private var driverNombre: String = ""
    private var driverTelefono: String = ""
    private var driverPlacas: String = ""
    private var driverUnidad: String = ""

    // Control adaptativo de batería y datos móviles
    private var lastSentLocation: Location? = null
    private var lastSentTimestamp: Long = 0L
    private var lastFullMetadataSentTimestamp: Long = 0L

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "tuctuc_driver_tracking_channel_v3"
        private const val ALERT_CHANNEL_ID = "tuctuc_driver_trip_alert_channel_v1"

        const val ACTION_ACCEPT_TRIP = "com.example.tuctucmap.ACTION_ACCEPT_TRIP"
        const val ACTION_DISMISS_TRIP = "com.example.tuctucmap.ACTION_DISMISS_TRIP"
        const val EXTRA_TRIP_ID = "EXTRA_TRIP_ID"

        // IDs activos de notificaciones individuales de viaje emitidas al sistema Android
        val activeTripNotificationIds: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

        fun getTripNotificationId(tripId: String): Int {
            return 2000 + ((tripId.hashCode() and 0x3FFFFFFF) % 50000)
        }

        fun getTripRequestCode(tripId: String, actionType: Int): Int {
            return ((tripId.hashCode() and 0x1FFFFFFF) % 100000) * 4 + actionType
        }

        fun cancelTripNotification(context: Context, tripId: String) {
            try {
                val manager = context.getSystemService(NotificationManager::class.java)
                manager?.cancel(getTripNotificationId(tripId))
                activeTripNotificationIds.remove(tripId)
            } catch (_: Exception) {}
        }

        fun clearAllTripNotifications(context: Context) {
            try {
                val manager = context.getSystemService(NotificationManager::class.java)
                val copy = synchronized(activeTripNotificationIds) { activeTripNotificationIds.toList() }
                copy.forEach { id ->
                    manager?.cancel(getTripNotificationId(id))
                }
                activeTripNotificationIds.clear()
            } catch (_: Exception) {}
        }

        private fun createBoundedSet(maxEntries: Int = 200): MutableSet<String> {
            return Collections.synchronizedSet(
                Collections.newSetFromMap(
                    object : java.util.LinkedHashMap<String, Boolean>(maxEntries + 16, 0.75f, true) {
                        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
                            return size > maxEntries
                        }
                    }
                )
            )
        }

        @Volatile
        private var activeServiceInstance: LocationTrackingService? = null

        var isRunning = false
            private set
        val currentLocation = MutableStateFlow<Location?>(null)
        val driverStatus = MutableStateFlow("disponible") // "disponible", "ocupado", "en_pausa"
        val pendingTripsFlow = MutableStateFlow<List<SupabaseRepository.TripInfo>>(emptyList())

        // Conjunto compartido acotado (LRU <= 200) de IDs de viajes ya anunciados por voz o ignorados localmente
        val announcedTripIds: MutableSet<String> = createBoundedSet(200)
        val ignoredBroadcastTripIds: MutableSet<String> = createBoundedSet(200)

        fun stopSpeaking() {
            try {
                activeServiceInstance?.tts?.stop()
            } catch (_: Exception) {}
        }

        fun dismissBroadcastTrip(tripId: String) {
            ignoredBroadcastTripIds.add(tripId)
            stopSpeaking()
            pendingTripsFlow.value = pendingTripsFlow.value.filterNot { it.tripId == tripId }
            activeServiceInstance?.let { cancelTripNotification(it, tripId) }
        }

        /**
         * Permite aceptar un viaje pendiente atómicamente desde la notificación de Android
         * o desde cualquier componente de la aplicación.
         */
        fun acceptPendingTrip(
            context: Context? = null,
            tripId: String,
            onResult: ((SupabaseRepository.AcceptTripResult) -> Unit)? = null
        ) {
            val instance = activeServiceInstance
            val appContext = context?.applicationContext ?: instance?.applicationContext
            val driverId = instance?.driverUid?.ifBlank { null }
                ?: (appContext?.let { SecurePrefs.loadUserSession(it)?.user?.id })
                ?: ""

            if (driverId.isBlank()) return

            val driverName = instance?.driverNombre?.ifBlank { null } ?: "Conductor"
            val driverPhone = instance?.driverTelefono ?: ""
            val driverPlate = if (instance?.driverUnidad?.isNotBlank() == true) {
                "Unidad ${instance.driverUnidad}"
            } else {
                instance?.driverPlacas ?: ""
            }

            val targetScope = instance?.serviceScope ?: CoroutineScope(Dispatchers.IO)
            targetScope.launch {
                val currentPending = pendingTripsFlow.value
                val trip = currentPending.find { it.tripId == tripId }
                val repo = instance?.repository ?: SupabaseRepository()

                val result = repo.acceptTripAtomically(
                    tripId = tripId,
                    driverId = driverId,
                    driverName = driverName,
                    driverPhone = driverPhone,
                    driverPlate = driverPlate
                )

                withContext(Dispatchers.Main) {
                    when (result) {
                        is SupabaseRepository.AcceptTripResult.Success -> {
                            appContext?.let { clearAllTripNotifications(it) }
                            stopSpeaking()
                            driverStatus.value = "ocupado"
                            targetScope.launch(Dispatchers.IO) {
                                try {
                                    repo.updateDriverStatus(driverId, "ocupado")
                                } catch (_: Exception) {}
                            }
                            val accepted = (trip ?: SupabaseRepository.TripInfo(tripId = tripId)).copy(
                                driverId = driverId
                            )
                            startTripTracking(tripId, currentLocation.value, accepted)
                            appContext?.let {
                                Toast.makeText(
                                    it,
                                    "¡Viaje aceptado! En camino a recoger al pasajero.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        is SupabaseRepository.AcceptTripResult.AlreadyTaken -> {
                            appContext?.let { cancelTripNotification(it, tripId) }
                            stopSpeaking()
                            appContext?.let {
                                Toast.makeText(
                                    it,
                                    "¡Este viaje ya fue tomado por otro tuctuc!",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                        is SupabaseRepository.AcceptTripResult.Error -> {
                            appContext?.let {
                                Toast.makeText(
                                    it,
                                    "Error al aceptar viaje: ${result.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                    onResult?.invoke(result)
                }
            }
        }

        // Control de rastreo de viaje activo del conductor
        var activeTripId: String? = null
            private set
        val activeAcceptedTrip = MutableStateFlow<SupabaseRepository.TripInfo?>(null)
        val activeTripDistanceMeters = MutableStateFlow(0.0)
        private val _tripRoutePoints = mutableListOf<SupabaseRepository.RoutePoint>()
        val activeTripRoutePoints: List<SupabaseRepository.RoutePoint>
            get() = synchronized(_tripRoutePoints) { _tripRoutePoints.toList() }

        private var lastTripRecordedLocation: Location? = null

        /**
         * Inicia el registro de ruta y distancia para un viaje recién aceptado.
         */
        fun startTripTracking(tripId: String, initialLocation: Location? = null, tripInfo: SupabaseRepository.TripInfo? = null) {
            if (activeTripId == tripId && activeAcceptedTrip.value != null) return // Ya está rastreando este viaje
            activeTripId = tripId
            if (tripInfo != null) {
                activeAcceptedTrip.value = tripInfo
            }
            activeTripDistanceMeters.value = 0.0
            synchronized(_tripRoutePoints) {
                _tripRoutePoints.clear()
                val locToUse = initialLocation ?: currentLocation.value
                if (locToUse != null) {
                    lastTripRecordedLocation = locToUse
                    _tripRoutePoints.add(
                        SupabaseRepository.RoutePoint(
                            lat = locToUse.latitude,
                            lon = locToUse.longitude,
                            timestamp = ServerClock.nowMillis()
                        )
                    )
                } else {
                    lastTripRecordedLocation = null
                }
            }
        }

        /**
         * Registra una nueva posición en el viaje activo si el conductor se desplazó.
         */
        fun recordTripLocation(location: Location) {
            if (activeTripId.isNullOrBlank()) return

            val lastLoc = lastTripRecordedLocation
            if (lastLoc == null) {
                lastTripRecordedLocation = location
                synchronized(_tripRoutePoints) {
                    _tripRoutePoints.add(
                        SupabaseRepository.RoutePoint(
                            lat = location.latitude,
                            lon = location.longitude,
                            timestamp = ServerClock.nowMillis()
                        )
                    )
                }
                return
            }

            val distanceMeters = location.distanceTo(lastLoc)
            // Filtro contra saltos espurios de GPS (ruido < 1.5m o teletransportes > 2000m)
            if (distanceMeters in 1.5f..2000.0f) {
                activeTripDistanceMeters.value += distanceMeters.toDouble()
                // Registrar punto en la polilínea cada >= 8 metros para optimizar memoria y base de datos
                if (distanceMeters >= 8.0f) {
                    lastTripRecordedLocation = location
                    synchronized(_tripRoutePoints) {
                        _tripRoutePoints.add(
                            SupabaseRepository.RoutePoint(
                                lat = location.latitude,
                                lon = location.longitude,
                                timestamp = ServerClock.nowMillis()
                            )
                        )
                    }
                }
            } else if (distanceMeters > 2000.0f && location.accuracy <= 30.0f) {
                // Recuperación de caché antigua: si hubo un salto > 2000m pero el nuevo punto GPS tiene buena precisión,
                // actualizar lastTripRecordedLocation para desbloquear el rastreo continuo de waypoints
                lastTripRecordedLocation = location
                synchronized(_tripRoutePoints) {
                    _tripRoutePoints.add(
                        SupabaseRepository.RoutePoint(
                            lat = location.latitude,
                            lon = location.longitude,
                            timestamp = ServerClock.nowMillis()
                        )
                    )
                }
            }
        }

        /**
         * Finaliza el rastreo del viaje y devuelve el resumen: distancia en km (con 2 decimales)
         * y la lista completa de puntos GPS registrados durante el recorrido.
         */
        fun finishTripTracking(): Pair<Double, List<SupabaseRepository.RoutePoint>> {
            // Asegurar registrar la posición GPS actual de destino final antes de cerrar
            val finalLoc = currentLocation.value
            if (finalLoc != null) {
                val lastLoc = lastTripRecordedLocation
                if (lastLoc != null) {
                    val finalDeltaMeters = finalLoc.distanceTo(lastLoc)
                    if (finalDeltaMeters in 3.0f..2000.0f) {
                        activeTripDistanceMeters.value += finalDeltaMeters.toDouble()
                        synchronized(_tripRoutePoints) {
                            _tripRoutePoints.add(
                                SupabaseRepository.RoutePoint(
                                    lat = finalLoc.latitude,
                                    lon = finalLoc.longitude,
                                    timestamp = ServerClock.nowMillis()
                                )
                            )
                        }
                    }
                } else {
                    synchronized(_tripRoutePoints) {
                        if (_tripRoutePoints.isEmpty()) {
                            _tripRoutePoints.add(
                                SupabaseRepository.RoutePoint(
                                    lat = finalLoc.latitude,
                                    lon = finalLoc.longitude,
                                    timestamp = ServerClock.nowMillis()
                                )
                            )
                        }
                    }
                }
            }

            val totalMeters = activeTripDistanceMeters.value
            val distanceKm = Math.round((totalMeters / 1000.0) * 100.0) / 100.0
            val points = synchronized(_tripRoutePoints) { _tripRoutePoints.toList() }

            // Limpiar estado de rastreo y viaje activo compartido
            activeTripId = null
            activeAcceptedTrip.value = null
            activeTripDistanceMeters.value = 0.0
            lastTripRecordedLocation = null
            synchronized(_tripRoutePoints) {
                _tripRoutePoints.clear()
            }

            return Pair(distanceKm, points)
        }

        /**
         * Obtiene una copia en caliente de los puntos GPS registrados hasta el momento sin finalizar el viaje.
         */
        fun getTripRoutePointsSnapshot(): List<SupabaseRepository.RoutePoint> {
            return synchronized(_tripRoutePoints) { _tripRoutePoints.toList() }
        }

        /**
         * Consulta la distancia acumulada actual en km sin finalizar el viaje (para visualización en tiempo real).
         */
        fun getCurrentTripDistanceKm(): Double {
            return Math.round((activeTripDistanceMeters.value / 1000.0) * 100.0) / 100.0
        }
    }

    override fun onCreate() {
        super.onCreate()
        activeServiceInstance = this
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val spanishLocales = listOf(
                    Locale.forLanguageTag("es-GT"),
                    Locale.forLanguageTag("es-419"),
                    Locale.forLanguageTag("es-MX"),
                    Locale.forLanguageTag("es-US"),
                    Locale.forLanguageTag("es-ES"),
                    Locale.forLanguageTag("es")
                )
                for (loc in spanishLocales) {
                    val res = tts?.setLanguage(loc)
                    if (res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED) {
                        break
                    }
                }
                isTtsReady = true
            }
        }
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    currentLocation.value = location
                    recordTripLocation(location)
                    updateSupabaseLocation(location)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            when (intent.action) {
                ACTION_DISMISS_TRIP -> {
                    val tripId = intent.getStringExtra(EXTRA_TRIP_ID) ?: ""
                    if (tripId.isNotBlank()) {
                        dismissBroadcastTrip(tripId)
                        cancelTripNotification(this, tripId)
                    }
                    return START_STICKY
                }
                ACTION_ACCEPT_TRIP -> {
                    val tripId = intent.getStringExtra(EXTRA_TRIP_ID) ?: ""
                    if (tripId.isNotBlank()) {
                        acceptPendingTrip(this, tripId)
                    }
                    return START_STICKY
                }
            }

            driverUid = intent.getStringExtra("DRIVER_UID") ?: driverUid
            driverNombre = intent.getStringExtra("DRIVER_NOMBRE") ?: driverNombre
            driverTelefono = intent.getStringExtra("DRIVER_TELEFONO") ?: driverTelefono
            driverPlacas = intent.getStringExtra("DRIVER_PLACAS") ?: driverPlacas
            driverUnidad = intent.getStringExtra("DRIVER_UNIDAD") ?: driverUnidad
        }

        if (driverUid.isBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        isRunning = true
        lastFullMetadataSentTimestamp = 0L
        // Registrar limpieza por desconexión en Firebase una sola vez al inicio de turno
        repository.registerOnDisconnectCleanup(driverUid)
        createNotificationChannel()
        val notification = createNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startLocationUpdates()
        startBackgroundTripAlertObserver()

        return START_STICKY
    }

    /**
     * Escucha solicitudes de viaje en segundo plano para alertar por voz (TTS) y emitir
     * una notificación nativa independiente e interactiva por cada pasajero que solicita viaje.
     */
    private fun startBackgroundTripAlertObserver() {
        pendingTripsJob?.cancel()
        if (driverUid.isBlank()) return

        pendingTripsJob = serviceScope.launch {
            repository.observePendingTripsForDriver(
                driverId = driverUid,
                driverCoordProvider = {
                    val loc = currentLocation.value
                    if (loc != null) Pair(loc.latitude, loc.longitude) else null
                }
            ).collect { trips ->
                val visibleTrips = trips.filterNot { ignoredBroadcastTripIds.contains(it.tripId) }
                pendingTripsFlow.value = visibleTrips
                if (driverStatus.value != "disponible" || !activeTripId.isNullOrBlank()) {
                    stopSpeaking()
                    clearAllTripNotifications(this@LocationTrackingService)
                    return@collect
                }

                // Identificar IDs de viajes actualmente vigentes
                val currentTripIds = visibleTrips.map { it.tripId }.toSet()

                // Cancelar notificaciones de viajes que ya expiraron, fueron cancelados o tomados por otro tuctuc
                val staleTripIds = synchronized(activeTripNotificationIds) {
                    activeTripNotificationIds.filterNot { currentTripIds.contains(it) }
                }
                staleTripIds.forEach { staleId ->
                    cancelTripNotification(this@LocationTrackingService, staleId)
                }

                // Emitir o actualizar una notificación individual e independiente por cada pasajero solicitando viaje
                for (trip in visibleTrips) {
                    val isNew = announcedTripIds.add(trip.tripId)
                    if (isNew) {
                        triggerTripSoundAndVoice(trip)
                    }
                    showTripAlertNotification(trip)
                }
            }
        }
    }

    private fun triggerTripSoundAndVoice(trip: SupabaseRepository.TripInfo) {
        try {
            val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(applicationContext, notificationUri)
            ringtone?.play()
        } catch (_: Exception) {}

        val safeMsg = SupabaseRepository.sanitizeTripMessage(trip.message)
        if (isTtsReady) {
            val msg = if (safeMsg.isNotBlank()) {
                "Nueva solicitud de viaje. El pasajero dice: $safeMsg"
            } else {
                "Nueva solicitud de viaje cercana disponible."
            }
            tts?.speak(msg, TextToSpeech.QUEUE_ADD, null, "BG_TRIP_${trip.tripId}")
        }
    }

    private fun showTripAlertNotification(trip: SupabaseRepository.TripInfo) {
        try {
            val safeMsg = SupabaseRepository.sanitizeTripMessage(trip.message)
                .ifBlank { "Pasajero esperando en ubicación GPS" }

            val distanceText = if (currentLocation.value != null) {
                val results = FloatArray(1)
                Location.distanceBetween(
                    currentLocation.value!!.latitude,
                    currentLocation.value!!.longitude,
                    trip.passengerLat,
                    trip.passengerLon,
                    results
                )
                val distMeters = results[0]
                if (distMeters > 1000) {
                    String.format(Locale.getDefault(), "A %.1f km de ti", distMeters / 1000)
                } else {
                    "A ${distMeters.toInt()} m de ti"
                }
            } else {
                "Pasajero esperando"
            }

            val passengerTitle = if (trip.passengerName.isNotBlank()) {
                "¡Solicitud de Viaje! • ${trip.passengerName}"
            } else {
                "¡Solicitud de Viaje!"
            }

            // Al tocar el cuerpo de la notificación: abrir MainActivity
            val contentIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(EXTRA_TRIP_ID, trip.tripId)
            }
            val contentPendingIntent = PendingIntent.getActivity(
                this,
                getTripRequestCode(trip.tripId, 0),
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Acción nativa: ACEPTAR VIAJE directamente desde la notificación
            val acceptIntent = Intent(this, MainActivity::class.java).apply {
                action = ACTION_ACCEPT_TRIP
                putExtra(EXTRA_TRIP_ID, trip.tripId)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val acceptPendingIntent = PendingIntent.getActivity(
                this,
                getTripRequestCode(trip.tripId, 1),
                acceptIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Acción nativa: IGNORAR directamente desde la notificación
            val dismissIntent = Intent(this, LocationTrackingService::class.java).apply {
                action = ACTION_DISMISS_TRIP
                putExtra(EXTRA_TRIP_ID, trip.tripId)
            }
            val dismissPendingIntent = PendingIntent.getService(
                this,
                getTripRequestCode(trip.tripId, 2),
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val bigText = buildString {
                append("📍 DESTINO: $safeMsg\n")
                append("📏 DISTANCIA: $distanceText\n")
                if (trip.passengerName.isNotBlank()) {
                    append("👤 PASAJERO: ${trip.passengerName}\n")
                }
                if (trip.fare != null && trip.fare > 0) {
                    append("💵 TARIFA ESTIMADA: Q${String.format(Locale.US, "%.2f", trip.fare)}\n")
                }
                append("⏱️ Toca 'Aceptar viaje' para tomar este viaje de inmediato.")
            }

            val notifId = getTripNotificationId(trip.tripId)

            val alertNotification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                .setContentTitle(passengerTitle)
                .setContentText("📍 $safeMsg • $distanceText")
                .setSubText(distanceText)
                .setSmallIcon(R.drawable.ic_stat_tuctuc)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .setBigContentTitle(passengerTitle)
                        .setSummaryText(distanceText)
                        .bigText(bigText)
                )
                .setContentIntent(contentPendingIntent)
                .addAction(
                    R.drawable.ic_stat_tuctuc,
                    "Aceptar viaje",
                    acceptPendingIntent
                )
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Ignorar",
                    dismissPendingIntent
                )
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setColor(0xFFF9D423.toInt())
                .setWhen(if (trip.timestamp > 0) trip.timestamp else ServerClock.nowMillis())
                .setShowWhen(true)
                .build()

            val manager = getSystemService(NotificationManager::class.java)
            manager?.notify(notifId, alertNotification)
            activeTripNotificationIds.add(trip.tripId)
        } catch (_: Exception) {}
    }

    private fun applyLocationRequest(priority: Int, intervalMs: Long) {
        if (isLocationUpdatesActive && currentGpsPriority == priority && currentGpsIntervalMs == intervalMs) {
            return
        }
        currentGpsPriority = priority
        currentGpsIntervalMs = intervalMs

        val locationRequest = LocationRequest.Builder(priority, intervalMs)
            .setMinUpdateIntervalMillis((intervalMs * 0.8).toLong())
            .build()

        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            isLocationUpdatesActive = true
        } catch (unlikely: SecurityException) {
            isLocationUpdatesActive = false
            isRunning = false
            stopSelf()
        }
    }

    private fun startLocationUpdates() {
        // Inicializar con alta precisión para inicio de turno
        applyLocationRequest(Priority.PRIORITY_HIGH_ACCURACY, 5000L)

        // Obtener de inmediato la última ubicación conocida para no demorar la aparición en el mapa
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                if (lastLoc != null && currentLocation.value == null) {
                    currentLocation.value = lastLoc
                    recordTripLocation(lastLoc)
                    updateSupabaseLocation(lastLoc)
                }
            }
        } catch (_: SecurityException) {}

        // Monitoreo adaptativo de energía: relajar GPS en pausa para no agotar la batería del teléfono
        statusObserverJob?.cancel()
        statusObserverJob = serviceScope.launch {
            driverStatus.collect { status ->
                if (status != "disponible") {
                    stopSpeaking()
                    clearAllTripNotifications(this@LocationTrackingService)
                }
                if (status == "en_pausa") {
                    // Modo reposo/estación: precisión balanceada y ciclo de 15 segundos
                    applyLocationRequest(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 15000L)
                } else {
                    // Modo activo/disponible u ocupado en viaje: alta precisión a 5 segundos
                    applyLocationRequest(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
                }
            }
        }
    }

    private fun updateSupabaseLocation(location: Location) {
        if (driverUid.isBlank()) return

        val now = ServerClock.nowMillis()
        val lastLoc = lastSentLocation
        val distanceMoved = if (lastLoc != null) location.distanceTo(lastLoc) else Float.MAX_VALUE

        // MUESTREO ADAPTATIVO:
        // Solo enviar a Firebase si:
        // 1. Es la primera lectura, o
        // 2. Se desplazó al menos 8 metros, o
        // 3. Pasaron 45 segundos (heartbeat para mantener presencia en línea en paradas).
        val shouldSend = (lastLoc == null) || (distanceMoved >= 8.0f) || ((now - lastSentTimestamp) >= 45_000L)

        if (shouldSend) {
            val includeMetadata = (lastLoc == null) || ((now - lastFullMetadataSentTimestamp) >= 180_000L)
            if (includeMetadata) {
                lastFullMetadataSentTimestamp = now
            }
            lastSentLocation = location
            lastSentTimestamp = now
            val currentStatus = driverStatus.value

            serviceScope.launch {
                repository.updateLocation(
                    uid = driverUid,
                    nombre = driverNombre,
                    telefono = driverTelefono,
                    placas = driverPlacas,
                    latitud = location.latitude,
                    longitud = location.longitude,
                    status = currentStatus,
                    numeroUnidad = driverUnidad,
                    includeStaticMetadata = includeMetadata
                )
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "TucTuc Map - Rastreo de Conductor",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificación persistente de servicio de ubicación del conductor en turno"
                setShowBadge(true)
            }
            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "TucTuc Map - Solicitudes de Viaje",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alertas sonoras de nuevas solicitudes de pasajeros cercanos"
                enableVibration(true)
                setShowBadge(true)
            }
            manager?.createNotificationChannel(serviceChannel)
            manager?.createNotificationChannel(alertChannel)
        }
    }

    private fun createNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TucTuc Map - Conductor en Turno")
            .setContentText("Transmitiendo ubicación y atento a solicitudes")
            .setSmallIcon(R.drawable.ic_stat_tuctuc)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        clearAllTripNotifications(this)
        if (activeServiceInstance === this) {
            activeServiceInstance = null
        }
        isRunning = false
        isLocationUpdatesActive = false
        currentGpsPriority = null
        currentGpsIntervalMs = null
        currentLocation.value = null
        pendingTripsFlow.value = emptyList()
        lastSentLocation = null
        lastSentTimestamp = 0L
        pendingTripsJob?.cancel()
        statusObserverJob?.cancel()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        tts?.stop()
        tts?.shutdown()
        if (driverUid.isNotBlank()) {
            val uidToRemove = driverUid
            CoroutineScope(Dispatchers.IO).launch {
                // Si el servicio fue reiniciado rápidamente o está activa otra instancia, no borrar ubicación
                if (!isRunning && activeServiceInstance == null) {
                    repository.removeLocation(uidToRemove)
                }
            }
        }
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
