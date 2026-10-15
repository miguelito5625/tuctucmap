package com.example.tuctucmap.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.example.tuctucmap.data.repository.SupabaseRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow

class LocationTrackingService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val repository = SupabaseRepository()
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    private var driverUid: String = ""
    private var driverNombre: String = ""
    private var driverTelefono: String = ""
    private var driverPlacas: String = ""

    // Control adaptativo de batería y datos móviles
    private var lastSentLocation: Location? = null
    private var lastSentTimestamp: Long = 0L

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "tuctuc_tracking_channel"
        var isRunning = false
            private set
        val currentLocation = MutableStateFlow<Location?>(null)
        val driverStatus = MutableStateFlow("disponible") // "disponible", "ocupado", "en_pausa"
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    currentLocation.value = location
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
        }

        if (driverUid.isBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        isRunning = true
        createNotificationChannel()
        val notification = createNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startLocationUpdates()

        return START_STICKY
    }

    private fun startLocationUpdates() {
        // Intervalo de 5 segundos con precisión alta
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMinUpdateIntervalMillis(4000L)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (unlikely: SecurityException) {
            isRunning = false
            stopSelf()
        }
    }

    private fun updateSupabaseLocation(location: Location) {
        if (driverUid.isBlank()) return

        val now = System.currentTimeMillis()
        val lastLoc = lastSentLocation
        val distanceMoved = if (lastLoc != null) location.distanceTo(lastLoc) else Float.MAX_VALUE

        // MUESTREO ADAPTATIVO:
        // Solo enviar a Firebase si:
        // 1. Es la primera lectura, o
        // 2. Se desplazó al menos 8 metros, o
        // 3. Pasaron 45 segundos (heartbeat para mantener presencia en línea en paradas).
        val shouldSend = (lastLoc == null) || (distanceMoved >= 8.0f) || ((now - lastSentTimestamp) >= 45_000L)

        if (shouldSend) {
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
                    status = currentStatus
                )
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Tuc Tuc Tracking Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Tuc Tuc Activo")
            .setContentText("Transmitiendo tu ubicación en tiempo real...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        fusedLocationClient.removeLocationUpdates(locationCallback)
        if (driverUid.isNotBlank()) {
            serviceScope.launch {
                repository.removeLocation(driverUid)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
