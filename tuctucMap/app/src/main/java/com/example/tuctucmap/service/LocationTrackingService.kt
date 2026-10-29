package com.example.tuctucmap.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import com.example.tuctucmap.MainActivity
import com.example.tuctucmap.R
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
        private const val ALERT_NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "tuctuc_driver_tracking_channel_v3"
        private const val ALERT_CHANNEL_ID = "tuctuc_driver_trip_alert_channel_v1"

        var isRunning = false
            private set
        val currentLocation = MutableStateFlow<Location?>(null)
        val driverStatus = MutableStateFlow("disponible") // "disponible", "ocupado", "en_pausa"

        // Conjunto compartido de IDs de viajes ya anunciados por voz o ignorados localmente por este conductor
        val announcedTripIds: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
        val ignoredBroadcastTripIds: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

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
            }
        }

        /**
         * Finaliza el rastreo del viaje y devuelve el resumen: distancia en km (con 2 decimales)
         * y la lista completa de puntos GPS registrados durante el recorrido.
         */
        fun finishTripTracking(): Pair<Double, List<SupabaseRepository.RoutePoint>> {
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
         * Consulta la distancia acumulada actual en km sin finalizar el viaje (para visualización en tiempo real).
         */
        fun getCurrentTripDistanceKm(): Double {
            return Math.round((activeTripDistanceMeters.value / 1000.0) * 100.0) / 100.0
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val res = tts?.setLanguage(Locale("es", "GT"))
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale("es", "MX"))
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
            driverUid = intent.getStringExtra("DRIVER_UID") ?: ""
            driverNombre = intent.getStringExtra("DRIVER_NOMBRE") ?: ""
            driverTelefono = intent.getStringExtra("DRIVER_TELEFONO") ?: ""
            driverPlacas = intent.getStringExtra("DRIVER_PLACAS") ?: ""
            driverUnidad = intent.getStringExtra("DRIVER_UNIDAD") ?: ""
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
     * Escucha solicitudes de viaje en segundo plano para alertar por voz (TTS) y notificación emergente
     * incluso cuando el conductor tiene la pantalla apagada o está en otra app (ej. WhatsApp o Maps).
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
                if (driverStatus.value != "disponible" || !activeTripId.isNullOrBlank()) return@collect

                val nextTrip = trips.firstOrNull { !ignoredBroadcastTripIds.contains(it.tripId) }
                if (nextTrip != null && announcedTripIds.add(nextTrip.tripId)) {
                    triggerTripAlert(nextTrip)
                }
            }
        }
    }

    private fun triggerTripAlert(trip: SupabaseRepository.TripInfo) {
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
            tts?.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "BG_TRIP_${trip.tripId}")
        }

        try {
            val launchIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                this,
                1,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val alertNotification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                .setContentTitle("¡Pasajero solicita un TucTuc!")
                .setContentText(safeMsg.ifBlank { "Toca aquí para ver la ubicación y aceptar el viaje" })
                .setSmallIcon(R.drawable.ic_tuc_tuc)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build()

            val manager = getSystemService(NotificationManager::class.java)
            manager?.notify(ALERT_NOTIFICATION_ID, alertNotification)
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
            .setSmallIcon(R.drawable.ic_tuc_tuc)
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
        isRunning = false
        isLocationUpdatesActive = false
        currentGpsPriority = null
        currentGpsIntervalMs = null
        currentLocation.value = null
        lastSentLocation = null
        lastSentTimestamp = 0L
        pendingTripsJob?.cancel()
        statusObserverJob?.cancel()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        tts?.stop()
        tts?.shutdown()
        if (driverUid.isNotBlank()) {
            CoroutineScope(Dispatchers.IO).launch {
                repository.removeLocation(driverUid)
            }
        }
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
