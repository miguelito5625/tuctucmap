package com.example.tuctucmap.ui.screens.driver

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.KeyboardType
import com.example.tuctucmap.data.DriverFarePreferences
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.tuctucmap.data.model.Driver
import com.example.tuctucmap.data.repository.SupabaseRepository
import com.example.tuctucmap.service.LocationTrackingService
import com.example.tuctucmap.ui.components.DriverDrawerItem
import com.example.tuctucmap.ui.components.DriverDrawerSheet
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pantalla principal del conductor (HUD / Cockpit Operativo).
 * Diseñada para máxima ergonomía, velocidad y ahorro de batería y datos en calle.
 * El mapa general ya no se carga por defecto para evitar distracciones y consumo innecesario;
 * se accede a él de forma opcional mediante [onViewMap] o en el Drawer.
 */
@Composable
fun DriverHomeScreen(
    repository: SupabaseRepository,
    onLogout: () -> Unit,
    onSubscriptionExpired: () -> Unit = {},
    onApprovalRevoked: () -> Unit = {},
    onViewMap: () -> Unit = {},
    onViewHistory: () -> Unit = {},
    onViewSubscription: () -> Unit = {},
    onViewProfile: () -> Unit = {},
    onNavigateToRoute: (SupabaseRepository.TripInfo) -> Unit = {}
) {
    val context = LocalContext.current
    val uid = repository.currentUserUid ?: ""

    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var pendingTrips by remember { mutableStateOf<List<SupabaseRepository.TripInfo>>(emptyList()) }
    var showRenewalDialog by remember { mutableStateOf(false) }

    // Cache local persistente y estado compartido de viaje activo del conductor
    val tripSharedPrefs = remember(context) { context.getSharedPreferences("driver_active_trip_prefs", Context.MODE_PRIVATE) }
    val activeAcceptedTrip by LocationTrackingService.activeAcceptedTrip.collectAsState()

    // Inicializar desde caché persistente si el servicio aún no tiene viaje cargado
    LaunchedEffect(Unit) {
        if (LocationTrackingService.activeAcceptedTrip.value == null) {
            val cachedTripId = tripSharedPrefs.getString("trip_id", null)
            if (!cachedTripId.isNullOrBlank()) {
                LocationTrackingService.activeAcceptedTrip.value = SupabaseRepository.TripInfo(
                    tripId = cachedTripId,
                    passengerId = tripSharedPrefs.getString("passenger_id", "") ?: "",
                    passengerName = tripSharedPrefs.getString("passenger_name", "") ?: "",
                    passengerPhone = tripSharedPrefs.getString("passenger_phone", "") ?: "",
                    message = tripSharedPrefs.getString("message", "") ?: "",
                    status = tripSharedPrefs.getString("status", "accepted") ?: "accepted",
                    passengerLat = tripSharedPrefs.getString("passenger_lat", "0.0")?.toDoubleOrNull() ?: 0.0,
                    passengerLon = tripSharedPrefs.getString("passenger_lon", "0.0")?.toDoubleOrNull() ?: 0.0
                )
            }
        }
    }

    // Persistir o limpiar el caché local automáticamente según el estado del viaje compartido
    LaunchedEffect(activeAcceptedTrip) {
        if (activeAcceptedTrip != null) {
            val t = activeAcceptedTrip!!
            tripSharedPrefs.edit()
                .putString("trip_id", t.tripId)
                .putString("passenger_id", t.passengerId)
                .putString("passenger_name", t.passengerName)
                .putString("passenger_phone", t.passengerPhone)
                .putString("message", t.message)
                .putString("status", t.status)
                .putString("passenger_lat", t.passengerLat.toString())
                .putString("passenger_lon", t.passengerLon.toString())
                .apply()
        } else {
            tripSharedPrefs.edit().clear().apply()
        }
    }

    var showFinishTripDialog by remember { mutableStateOf(false) }
    var fareInputText by remember { mutableStateOf("") }
    var suggestedFares by remember { mutableStateOf<List<Double>>(emptyList()) }
    val liveTripDistanceMeters by LocationTrackingService.activeTripDistanceMeters.collectAsState()

    val currentLocation by LocationTrackingService.currentLocation.collectAsState()
    val driverAvailability by LocationTrackingService.driverStatus.collectAsState()
    var isServiceRunning by remember { mutableStateOf(LocationTrackingService.isRunning) }
    var showPermissionError by remember { mutableStateOf(false) }
    var driverProfile by remember { mutableStateOf<Driver?>(null) }
    var appConfig by remember { mutableStateOf(SupabaseRepository.cachedAppConfig) }

    // Contador ligero de viajes completados hoy (filtrado en servidor)
    var completedTripsToday by remember { mutableIntStateOf(0) }
    fun refreshTripStats() {
        coroutineScope.launch {
            repository.getDriverTodayTripsCount().onSuccess { count ->
                completedTripsToday = count
            }
        }
    }

    LaunchedEffect(uid) {
        appConfig = repository.getAppConfig()
        refreshTripStats()
    }

    // Cálculo continuo de distancia y tiempo estimado OSRM hacia el punto de recogida del pasajero
    var routeSummaryToPassenger by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(activeAcceptedTrip?.tripId, currentLocation?.latitude, currentLocation?.longitude) {
        val trip = activeAcceptedTrip
        val loc = currentLocation
        if (trip != null && loc != null && trip.passengerLat != 0.0 && trip.passengerLon != 0.0) {
            val res = com.example.tuctucmap.data.service.OsrmRoutingService.getRoute(
                startLat = loc.latitude,
                startLon = loc.longitude,
                endLat = trip.passengerLat,
                endLon = trip.passengerLon
            )
            routeSummaryToPassenger = "${res.formattedDistance} • ~${res.formattedDuration}"
        } else {
            routeSummaryToPassenger = null
        }
    }

    // ── Motor de voz Text-To-Speech para anuncios de solicitudes entrantes ──────────────
    var textToSpeech by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(context) {
        var ttsRef: TextToSpeech? = null
        val listener = TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                val spanishLocales = listOf(
                    Locale.forLanguageTag("es-419"), // Español Latinoamérica
                    Locale.forLanguageTag("es-GT"),  // Español Guatemala
                    Locale.forLanguageTag("es-US"),  // Español EE.UU.
                    Locale.forLanguageTag("es-ES"),  // Español España
                    Locale.forLanguageTag("es")      // Español general
                )
                var langSet = false
                for (loc in spanishLocales) {
                    val result = ttsRef?.setLanguage(loc)
                    if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                        langSet = true
                        break
                    }
                }
                if (!langSet) {
                    ttsRef?.language = Locale.getDefault()
                }
            }
        }
        val tts = TextToSpeech(context, listener)
        ttsRef = tts
        textToSpeech = tts
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    // Alarma auditiva y voz al llegar solicitudes pendientes (coordinado con LocationTrackingService para no duplicar audio)
    var previousTripsCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(pendingTrips.size, activeAcceptedTrip, driverAvailability, isServiceRunning) {
        if (activeAcceptedTrip == null && isServiceRunning && driverAvailability == "disponible") {
            if (pendingTrips.size > previousTripsCount) {
                val latestTrip = pendingTrips.firstOrNull()
                if (latestTrip != null && LocationTrackingService.announcedTripIds.add(latestTrip.tripId)) {
                    try {
                        val uri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                        val r = android.media.RingtoneManager.getRingtone(context, uri)
                        r.play()
                    } catch (_: Exception) {}

                    if (textToSpeech != null) {
                        try {
                            val msg = latestTrip.message.ifBlank { "Solicitud de viaje" }
                            textToSpeech?.speak("¡Nueva solicitud de viaje! $msg", TextToSpeech.QUEUE_FLUSH, null, null)
                        } catch (_: Exception) {}
                    }
                }
            }
        } else {
            try { textToSpeech?.stop() } catch (_: Exception) {}
        }
        previousTripsCount = pendingTrips.size
    }

    // Escuchar solicitudes pendientes en Firebase dentro del radio configurado y excluyendo ignoradas localmente
    LaunchedEffect(uid) {
        if (uid.isNotBlank()) {
            repository.observePendingTripsForDriver(
                driverId = uid,
                driverCoordProvider = {
                    val loc = currentLocation
                    if (loc != null) Pair(loc.latitude, loc.longitude) else null
                }
            ).collectLatest { trips ->
                pendingTrips = trips.filterNot { LocationTrackingService.ignoredBroadcastTripIds.contains(it.tripId) }
            }
        }
    }

    // Sincronizar viaje activo aceptado por el conductor en Firebase
    LaunchedEffect(uid) {
        if (uid.isNotBlank()) {
            repository.observeAcceptedTripForDriver(uid).collectLatest { active ->
                LocationTrackingService.activeAcceptedTrip.value = active
                if (active == null) {
                    tripSharedPrefs.edit().clear().apply()
                }
            }
        }
    }

    LaunchedEffect(activeAcceptedTrip?.tripId) {
        val active = activeAcceptedTrip
        if (active != null && active.tripId.isNotBlank()) {
            LocationTrackingService.startTripTracking(active.tripId, currentLocation)
        }
    }

    // Escuchar perfil del conductor en Supabase
    LaunchedEffect(uid) {
        if (uid.isNotBlank()) {
            var subscriptionExpiredHandled = false
            var statusRevokedHandled = false

            repository.observeDriverProfile(uid).collectLatest { driver ->
                if (driver == null) return@collectLatest
                driverProfile = driver
                com.example.tuctucmap.data.SecurePrefs.saveDriverSubscriptionCache(
                    context,
                    driver.estadoAprobacion,
                    driver.subscriptionEndDate
                )

                when {
                    driver.estadoAprobacion in listOf("rechazado", "pendiente") -> {
                        if (!statusRevokedHandled) {
                            statusRevokedHandled = true
                            if (LocationTrackingService.isRunning) {
                                toggleService(context, driver, false)
                            }
                            onApprovalRevoked()
                        }
                    }
                    driver.isSubscriptionExpired -> {
                        if (!subscriptionExpiredHandled) {
                            subscriptionExpiredHandled = true
                            if (LocationTrackingService.isRunning) {
                                toggleService(context, driver, false)
                            }
                            onSubscriptionExpired()
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    // Permisos de ubicación y notificaciones
    val locationPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            showPermissionError = false
            toggleService(context, driverProfile, true)
            isServiceRunning = true
        } else {
            showPermissionError = true
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (fineLocationGranted) {
            toggleService(context, driverProfile, true)
            isServiceRunning = true
        } else {
            permissionLauncher.launch(locationPermissions)
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notifGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!notifGranted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val onConnectToggle: () -> Unit = {
        if (isServiceRunning) {
            toggleService(context, driverProfile, false)
            isServiceRunning = false
        } else {
            val fineLocationGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            val postNotificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

            if (fineLocationGranted && postNotificationsGranted) {
                toggleService(context, driverProfile, true)
                isServiceRunning = true
            } else if (!postNotificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                permissionLauncher.launch(locationPermissions)
            }
        }
    }

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "$label copiado al portapapeles", Toast.LENGTH_SHORT).show()
    }

    // Cálculo preventivo de suscripción con reloj sincronizado del servidor
    val expiryMs = driverProfile?.expiryTimestampMillis ?: 0L
    val now = com.example.tuctucmap.data.model.ServerClock.nowMillis()
    val hoursRemaining = if (expiryMs > now) (expiryMs - now) / (1000 * 3600) else 0L
    val isExpiringSoon = hoursRemaining in 1..72 // Menos de 3 días

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DriverDrawerSheet(
                driver = driverProfile,
                currentEmail = repository.currentUserEmail ?: "",
                selectedItem = DriverDrawerItem.HOME,
                onSelectItem = { item ->
                    coroutineScope.launch { drawerState.close() }
                    when (item) {
                        DriverDrawerItem.HOME -> Unit
                        DriverDrawerItem.MAP -> onViewMap()
                        DriverDrawerItem.HISTORY -> onViewHistory()
                        DriverDrawerItem.SUBSCRIPTIONS -> onViewSubscription()
                        DriverDrawerItem.PROFILE -> onViewProfile()
                    }
                },
                onLogout = {
                    coroutineScope.launch {
                        drawerState.close()
                        if (isServiceRunning) toggleService(context, driverProfile, false)
                        onLogout()
                    }
                }
            )
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0B131B),
                            Color(0xFF11222E),
                            Color(0xFF162D3D)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                // ── Cabecera Superior Ejecutiva (HUD Header) ─────────────────────
                val shortDriverName = remember(driverProfile?.nombre) {
                    driverProfile?.nombre
                        ?.substringBefore("(")
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?: "Conductor"
                }
                val daysRemaining = com.example.tuctucmap.data.model.calculateRemainingDays(expiryMs, now)

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF152534),
                    border = BorderStroke(1.dp, Color(0x26FFFFFF)),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { coroutineScope.launch { drawerState.open() } },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x14FFFFFF))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Menú",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Hola, $shortDriverName",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "🛺 ${driverProfile?.displayIdentifier?.takeIf { it.isNotBlank() } ?: "Sin placa asignada"}",
                                color = Color(0xFFF9D423),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Pastilla compacta de estado de suscripción (sin salto de línea)
                        if (isExpiringSoon) {
                            Surface(
                                onClick = { showRenewalDialog = true },
                                shape = RoundedCornerShape(50),
                                color = Color(0x29FFA726),
                                border = BorderStroke(1.dp, Color(0xFFFFA726))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = Color(0xFFFFA726),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${hoursRemaining}h",
                                        color = Color(0xFFFFA726),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        } else {
                            Surface(
                                onClick = { showRenewalDialog = true },
                                shape = RoundedCornerShape(50),
                                color = Color(0x224ADE80),
                                border = BorderStroke(1.dp, Color(0x554ADE80))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF4ADE80))
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Activo",
                                        color = Color(0xFF86EFAC),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = onViewProfile,
                            modifier = Modifier.size(36.dp)
                        ) {
                            val initial = (shortDriverName.firstOrNull() ?: 'C').uppercaseChar().toString()
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(Color(0xFFF9D423), Color(0xFFFFB300))))
                                    .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = initial,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF0F2027)
                                )
                            }
                        }
                    }
                }

                // ── Cuerpo Desplazable del Cockpit ──────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 1. Alerta de Suscripción Preventiva (si aplica)
                    if (isExpiringSoon) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0x33FF9800)),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFFFFB74D))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFFFB74D),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Suscripción próxima a vencer",
                                        color = Color(0xFFFFB74D),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "Quedan $hoursRemaining horas vigentes.",
                                        color = Color.White,
                                        fontSize = 11.sp
                                    )
                                }
                                Button(
                                    onClick = { showRenewalDialog = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFFFB74D),
                                        contentColor = Color(0xFF0F2027)
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text("Renovar", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // 2. HERO STATUS CARD (Consola de Trabajo Principal)
                    if (!isServiceRunning) {
                        // ── ESTADO: DESCONECTADO ────────────────────────────────────
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF152534)),
                            border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                            elevation = CardDefaults.cardElevation(8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Color(0x22EF5350))
                                            .border(1.dp, Color(0x55EF5350), RoundedCornerShape(16.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PowerSettingsNew,
                                            contentDescription = null,
                                            tint = Color(0xFFFF6B6B),
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = Color(0x26EF5350)
                                        ) {
                                            Text(
                                                text = "FUERA DE SERVICIO",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFFFF8A80),
                                                letterSpacing = 0.6.sp,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Turno desconectado",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Activa tu turno para aparecer en el mapa y recibir carreras con aviso por voz.",
                                            fontSize = 12.sp,
                                            color = Color(0xFF94A3B8),
                                            lineHeight = 16.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = onConnectToggle,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(54.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF10B981),
                                        contentColor = Color(0xFF06281E)
                                    ),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ElectricRickshaw,
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "INICIAR TURNO EN LÍNEA",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.4.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    } else {
                        // ── ESTADO: EN LÍNEA ────────────────────────────────────────
                        val isAvailable = driverAvailability == "disponible"
                        val accentColor = if (isAvailable) Color(0xFF10B981) else Color(0xFFF59E0B)

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF152534)),
                            border = BorderStroke(1.5.dp, accentColor.copy(alpha = 0.7f)),
                            elevation = CardDefaults.cardElevation(8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 18.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(accentColor.copy(alpha = 0.16f))
                                            .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isAvailable) Icons.Default.ElectricRickshaw else Icons.Default.Pause,
                                            contentDescription = null,
                                            tint = accentColor,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = accentColor.copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = if (isAvailable) "RADAR ACTIVO • DISPONIBLE" else "TURNO EN PAUSA",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = accentColor,
                                                letterSpacing = 0.6.sp,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = if (isAvailable) "Recibiendo solicitudes" else "Modo descanso activo",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = if (isAvailable)
                                                "Tu tuctuc es visible en el mapa y las alertas por voz están activas."
                                            else
                                                "No recibirás nuevos viajes hasta que reanudes tu turno.",
                                            fontSize = 12.sp,
                                            color = Color(0xFF94A3B8),
                                            lineHeight = 16.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            val newStatus = if (isAvailable) "ocupado" else "disponible"
                                            LocationTrackingService.driverStatus.value = newStatus
                                            coroutineScope.launch {
                                                repository.updateDriverStatus(uid, newStatus)
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(50.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isAvailable) Color(0xFFF59E0B) else Color(0xFF10B981),
                                            contentColor = Color(0xFF0F172A)
                                        )
                                    ) {
                                        Icon(
                                            imageVector = if (isAvailable) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(19.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isAvailable) "Pausar" else "Reanudar",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 14.sp,
                                            maxLines = 1
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = onConnectToggle,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(50.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(1.5.dp, Color(0xFFFF6B6B)),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = Color(0xFFFF6B6B)
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PowerSettingsNew,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Terminar",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 14.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (showPermissionError) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Activa los permisos de ubicación para transmitir tu posición en tiempo real.",
                            color = Color(0xFFFF6B6B),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 3. MÉTRICAS RÁPIDAS DE LA JORNADA (3 tarjetas limpias)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Resumen de tu jornada",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Actualizar",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFF9D423),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { refreshTripStats() }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // KPI 1: Viajes Hoy
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF152534),
                            border = BorderStroke(1.dp, Color(0x22FFFFFF))
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "$completedTripsToday",
                                    color = Color(0xFFF9D423),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Viajes hoy",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // KPI 2: Señal GPS
                        val gpsReady = currentLocation != null
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF152534),
                            border = BorderStroke(1.dp, Color(0x22FFFFFF))
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (gpsReady) "Óptima" else "En espera",
                                    color = if (gpsReady) Color(0xFF4ADE80) else Color(0xFFFFB74D),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Señal GPS",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                        }

                        // KPI 3: Días restantes de Suscripción
                        Surface(
                            onClick = { onViewSubscription() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF152534),
                            border = BorderStroke(1.dp, Color(0x22FFFFFF))
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (daysRemaining > 1) "$daysRemaining días" else if (daysRemaining == 1) "1 día" else if (hoursRemaining > 0) "${hoursRemaining}h" else "0 días",
                                    color = if (isExpiringSoon) Color(0xFFFFB74D) else Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Días restantes",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 4. ACCESOS RÁPIDOS (Cuadrícula 2x2 de Herramientas)
                    Text(
                        text = "Herramientas del conductor",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Acceso 1: Mapa en Vivo
                        Surface(
                            onClick = onViewMap,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF152534),
                            border = BorderStroke(1.dp, Color(0x2638BDF8))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0x2238BDF8)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Map,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Mapa en Vivo",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "Ver cobertura",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        // Acceso 2: Historial de Viajes
                        Surface(
                            onClick = onViewHistory,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF152534),
                            border = BorderStroke(1.dp, Color(0x26F9D423))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0x22F9D423)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.History,
                                        contentDescription = null,
                                        tint = Color(0xFFF9D423),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Mis Viajes",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "Historial y cobros",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 5. TARJETA COMPACTA DE PERFIL Y RENOVACIÓN DE SUSCRIPCIÓN
                    val spanishLocale = Locale.forLanguageTag("es")
                    val formattedExpiryDate = if (expiryMs > 0L) {
                        SimpleDateFormat("dd MMM yyyy", spanishLocale).format(Date(expiryMs))
                    } else {
                        "Sin fecha"
                    }

                    Surface(
                        onClick = onViewProfile,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF152534),
                        border = BorderStroke(1.dp, Color(0x22FFFFFF))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${driverProfile?.nombre ?: "Conductor"} • Tel: ${driverProfile?.telefono ?: "---"}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.CalendarMonth,
                                        contentDescription = null,
                                        tint = Color(0xFF4ADE80),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Suscripción vence: $formattedExpiryDate",
                                        fontSize = 12.sp,
                                        color = Color(0xFF94A3B8),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            TextButton(
                                onClick = { showRenewalDialog = true },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Renovar",
                                    color = Color(0xFFF9D423),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(90.dp)) // Espacio para tarjetas flotantes inferiores
                }
            }

            // ── CAPA FLOTANTE: VIAJE EN CURSO ────────────────────────────────────
            if (activeAcceptedTrip != null) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(2.dp, Color(0xFF4CAF50)),
                    elevation = CardDefaults.cardElevation(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🛺", fontSize = 24.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "VIAJE EN CURSO",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF4ADE80)
                                    )
                                    Text(
                                        text = activeAcceptedTrip!!.passengerName.ifBlank { "Pasajero" },
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                            Surface(
                                color = Color(0x334CAF50),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "EN RUTA",
                                    color = Color(0xFF81C784),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        if (activeAcceptedTrip!!.message.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = Color(0xFF1E293B),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Rumbo: \"${activeAcceptedTrip!!.message}\"",
                                    color = Color(0xFFF9D423),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }

                        // Distancia en tiempo real recorrida por el conductor desde que aceptó
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0x334ADE80)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Navigation,
                                    contentDescription = null,
                                    tint = Color(0xFF4ADE80),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Distancia en trayecto: ${String.format(Locale.US, "%.2f", liveTripDistanceMeters / 1000.0)} km",
                                    color = Color(0xFF4ADE80),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Resumen OSRM de ruta hacia el pasajero
                        if (routeSummaryToPassenger != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                color = Color(0x3300D2FF),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0x6600D2FF)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Route,
                                        contentDescription = null,
                                        tint = Color(0xFF00D2FF),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Ruta al Pasajero: $routeSummaryToPassenger",
                                        color = Color(0xFF00D2FF),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // BOTÓN PRINCIPAL: NAVEGACIÓN EN APP (NIVEL 1)
                            Button(
                                onClick = {
                                    onNavigateToRoute(activeAcceptedTrip!!)
                                },
                                modifier = Modifier.weight(1.3f).height(46.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A86B))
                            ) {
                                Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Navegar al Pasajero", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            if (activeAcceptedTrip!!.passengerPhone.isNotBlank()) {
                                Button(
                                    onClick = {
                                        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${activeAcceptedTrip!!.passengerPhone}"))
                                        context.startActivity(dialIntent)
                                    },
                                    modifier = Modifier.weight(0.9f).height(46.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                                ) {
                                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFF9D423))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Llamar", fontSize = 12.sp, color = Color(0xFFF9D423))
                                }
                            }

                            // Botón de fallback externo a Google Maps
                            IconButton(
                                onClick = {
                                    try {
                                        val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${activeAcceptedTrip!!.passengerLat},${activeAcceptedTrip!!.passengerLon}")
                                        val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                                        context.startActivity(mapIntent)
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "No se pudo abrir app externa de mapas", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .size(46.dp)
                                    .background(Color(0xFF1E293B), RoundedCornerShape(10.dp))
                            ) {
                                Icon(
                                    Icons.Default.Map,
                                    contentDescription = "Abrir Google Maps",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                val list = DriverFarePreferences.getRecentFares(context)
                                suggestedFares = list
                                if (fareInputText.isBlank() && list.isNotEmpty()) {
                                    val first = list.first()
                                    fareInputText = if (first % 1.0 == 0.0) first.toInt().toString() else first.toString()
                                }
                                showFinishTripDialog = true
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF9D423), contentColor = Color(0xFF0F2027))
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Terminar Viaje / Cobrar al Pasajero", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }

            // ── DIÁLOGO DE FINALIZACIÓN Y COBRO CON SUGERENCIAS DE LOCALSTORAGE ────
            if (showFinishTripDialog && activeAcceptedTrip != null) {
                val tripToFinish = activeAcceptedTrip!!
                val currentDistKm = LocationTrackingService.getCurrentTripDistanceKm()

                // Si no se detectó suficiente desplazamiento (ej: mock o estático), calcular Haversine con pasajero
                val estimatedDistanceKm = remember(tripToFinish, currentDistKm) {
                    if (currentDistKm > 0.0) {
                        currentDistKm
                    } else if (currentLocation != null && tripToFinish.passengerLat != 0.0) {
                        val results = FloatArray(1)
                        android.location.Location.distanceBetween(
                            currentLocation!!.latitude, currentLocation!!.longitude,
                            tripToFinish.passengerLat, tripToFinish.passengerLon,
                            results
                        )
                        Math.round((results[0] / 1000.0) * 100.0) / 100.0
                    } else {
                        0.0
                    }
                }

                AlertDialog(
                    onDismissRequest = { showFinishTripDialog = false },
                    containerColor = Color(0xFF0F172A),
                    titleContentColor = Color.White,
                    textContentColor = Color.LightGray,
                    shape = RoundedCornerShape(20.dp),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF4ADE80),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Finalizar Viaje",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color.White
                            )
                        }
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .imePadding()
                        ) {
                            // Resumen del trayecto
                            Surface(
                                color = Color(0xFF1E293B),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0x33F9D423)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Person,
                                            contentDescription = null,
                                            tint = Color(0xFFF9D423),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Pasajero: ${tripToFinish.passengerName.ifBlank { "Pasajero" }}",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Navigation,
                                            contentDescription = null,
                                            tint = Color(0xFF4ADE80),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Distancia recorrida: $estimatedDistanceKm km",
                                            color = Color(0xFF4ADE80),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                    if (tripToFinish.message.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Rumbo: \"${tripToFinish.message}\"",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "¿Cuánto cobraste por este viaje?",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Precios habituales (de tus últimos viajes):",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Sugerencias extraídas del LocalStorage del teléfono
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(suggestedFares) { fare ->
                                    val fareStr = if (fare % 1.0 == 0.0) fare.toInt().toString() else fare.toString()
                                    val isSelected = fareInputText == fareStr

                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { fareInputText = fareStr },
                                        label = {
                                            Text(
                                                text = "Q${if (fare % 1.0 == 0.0) fare.toInt() else String.format(Locale.US, "%.2f", fare)}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFFF9D423),
                                            selectedLabelColor = Color(0xFF0F2027),
                                            containerColor = Color(0xFF1E293B),
                                            labelColor = Color.White
                                        ),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) Color(0xFFF9D423) else Color(0x44FFFFFF)
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Campo de entrada editable para el monto cobrado
                            OutlinedTextField(
                                value = fareInputText,
                                onValueChange = { input ->
                                    if (input.all { it.isDigit() || it == '.' }) {
                                        fareInputText = input
                                    }
                                },
                                label = { Text("Monto cobrado al pasajero") },
                                prefix = {
                                    Text(
                                        "Q ",
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFFF9D423),
                                        fontSize = 16.sp
                                    )
                                },
                                placeholder = { Text("0.00") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFFF9D423),
                                    unfocusedBorderColor = Color(0x66FFFFFF),
                                    focusedLabelColor = Color(0xFFF9D423),
                                    unfocusedLabelColor = Color(0xFF94A3B8),
                                    cursorColor = Color(0xFFF9D423),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val fareValue = fareInputText.toDoubleOrNull()
                                if (fareValue == null || fareValue <= 0.0) {
                                    Toast.makeText(context, "Por favor ingresa un monto válido mayor a 0", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                // Guardar en LocalStorage para actualizar las sugerencias
                                DriverFarePreferences.saveFare(context, fareValue)

                                coroutineScope.launch {
                                    val tid = tripToFinish.tripId
                                    val (trackedDistanceKm, points) = LocationTrackingService.finishTripTracking()
                                    val finalDistance = if (trackedDistanceKm > 0.0) trackedDistanceKm else estimatedDistanceKm

                                    if (tid.isNotBlank()) {
                                        repository.updateTripStatus(tid, "completed")
                                        val saveResult = repository.saveTripRecord(
                                            tripInfo = tripToFinish.copy(driverId = uid),
                                            status = "completed",
                                            driverIdOverride = uid,
                                            fare = fareValue,
                                            distanceKm = finalDistance,
                                            routePoints = points
                                        )
                                        if (saveResult.isFailure) {
                                            android.util.Log.e("DriverHomeScreen", "Error al guardar viaje en Supabase", saveResult.exceptionOrNull())
                                        }
                                        repository.deleteTrip(tid)
                                    }

                                    LocationTrackingService.driverStatus.value = "disponible"
                                    repository.updateDriverStatus(uid, "disponible")
                                    LocationTrackingService.activeAcceptedTrip.value = null
                                    tripSharedPrefs.edit().clear().apply()
                                    showFinishTripDialog = false
                                    try { textToSpeech?.stop() } catch (_: Exception) {}
                                    refreshTripStats()
                                    Toast.makeText(
                                        context,
                                        "¡Viaje finalizado! Cobro: Q${String.format(Locale.US, "%.2f", fareValue)} | Distancia: ${finalDistance} km",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF9D423), contentColor = Color(0xFF0F2027)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Guardar y Finalizar", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showFinishTripDialog = false },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF94A3B8))
                        ) {
                            Text("Cancelar")
                        }
                    }
                )
            }

            // ── CAPA FLOTANTE: SOLICITUDES DE VIAJE ENTRANTES (60s) ──────────────
            if (pendingTrips.isNotEmpty() && activeAcceptedTrip == null && driverAvailability == "disponible" && isServiceRunning) {
                LazyColumn(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp, start = 14.dp, end = 14.dp)
                        .heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(pendingTrips, key = { it.tripId }) { trip ->
                        var isVisible by remember { mutableStateOf(true) }

                        AnimatedVisibility(
                            visible = isVisible,
                            enter = fadeIn(animationSpec = tween(400)) + slideInVertically(initialOffsetY = { it }),
                            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(animationSpec = tween(400))
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                elevation = CardDefaults.cardElevation(14.dp),
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(2.5.dp, Color(0xFFF9D423))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    // Encabezado: Título + Distancia + Botón Seguro de Descartar
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(text = "🔔", fontSize = 22.sp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = "¡SOLICITUD DE VIAJE!",
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Color.White,
                                                    fontSize = 16.sp
                                                )
                                                val distanceText = if (currentLocation != null) {
                                                    val results = FloatArray(1)
                                                    android.location.Location.distanceBetween(
                                                        currentLocation!!.latitude, currentLocation!!.longitude,
                                                        trip.passengerLat, trip.passengerLon,
                                                        results
                                                    )
                                                    val distMeters = results[0]
                                                    if (distMeters > 1000) String.format(Locale.getDefault(), "A %.1f km de ti", distMeters / 1000) else "A ${distMeters.toInt()} m de ti"
                                                } else "Pasajero esperando"
                                                Text(
                                                    text = distanceText,
                                                    color = Color(0xFF4ADE80),
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        // Botón discreto para descartar en la esquina superior derecha
                                        IconButton(
                                            onClick = {
                                                isVisible = false
                                                val isBroadcast = trip.driverId.isBlank() || trip.driverId == "broadcast"
                                                if (isBroadcast) {
                                                    // Solicitud general (broadcast): solo ignorar localmente en este teléfono
                                                    // sin cancelar el viaje para el pasajero ni para los demás conductores.
                                                    LocationTrackingService.ignoredBroadcastTripIds.add(trip.tripId)
                                                    pendingTrips = pendingTrips.filterNot { it.tripId == trip.tripId }
                                                } else {
                                                    // Solicitud directa a este conductor específico
                                                    coroutineScope.launch {
                                                        kotlinx.coroutines.delay(200)
                                                        repository.saveTripRecord(trip, "rejected")
                                                        repository.updateTripStatus(trip.tripId, "cancelled")
                                                        kotlinx.coroutines.delay(10000)
                                                        repository.deleteTrip(trip.tripId)
                                                    }
                                                }
                                            },
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(Color(0x33FF5252))
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Ignorar solicitud",
                                                tint = Color(0xFFFF5252),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Contenedor de referencia / destino del pasajero en alto contraste solar
                                    Surface(
                                        color = Color(0xFF1E293B),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, Color(0x66F9D423)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text(
                                                text = "PUNTO DE RECOGIDA Y RUMBO:",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = trip.message.ifBlank { "Pasajero esperando en ubicación GPS" },
                                                color = Color(0xFFF9D423),
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Barra de tiempo restante (60s) sincronizada con ServerClock
                                    var timeLeft by remember { mutableLongStateOf(60000L - (com.example.tuctucmap.data.model.ServerClock.nowMillis() - trip.timestamp)) }
                                    LaunchedEffect(trip.timestamp) {
                                        while (timeLeft > 0) {
                                            kotlinx.coroutines.delay(1000)
                                            timeLeft = 60000L - (com.example.tuctucmap.data.model.ServerClock.nowMillis() - trip.timestamp)
                                        }
                                        if (timeLeft <= 0) {
                                            isVisible = false
                                        }
                                    }
                                    val progress = (timeLeft.toFloat() / 60000f).coerceIn(0f, 1f)

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        LinearProgressIndicator(
                                            progress = { progress },
                                            modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)),
                                            color = if (progress > 0.3f) Color(0xFFF9D423) else Color(0xFFFF5252),
                                            trackColor = Color(0xFF334155)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "${(timeLeft / 1000).coerceAtLeast(0)}s",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    // BOTÓN GIGANTE DE ACEPTACIÓN (72dp)
                                    Button(
                                        onClick = {
                                            isVisible = false
                                            coroutineScope.launch {
                                                val acceptResult = repository.acceptTripAtomically(
                                                    tripId = trip.tripId,
                                                    driverId = uid,
                                                    driverName = driverProfile?.nombre ?: "Conductor",
                                                    driverPhone = driverProfile?.telefono ?: "",
                                                    driverPlate = driverProfile?.displayIdentifier?.ifBlank { driverProfile?.placas ?: "" } ?: ""
                                                )

                                                when (acceptResult) {
                                                    is SupabaseRepository.AcceptTripResult.Success -> {
                                                        LocationTrackingService.driverStatus.value = "ocupado"
                                                        repository.updateDriverStatus(uid, "ocupado")
                                                        val acceptedTrip = trip.copy(driverId = uid)
                                                        LocationTrackingService.startTripTracking(trip.tripId, currentLocation, acceptedTrip)
                                                        try { textToSpeech?.stop() } catch (_: Exception) {}
                                                        Toast.makeText(context, "¡Viaje aceptado! En camino a recoger al pasajero.", Toast.LENGTH_SHORT).show()
                                                        onNavigateToRoute(acceptedTrip)
                                                    }
                                                    is SupabaseRepository.AcceptTripResult.AlreadyTaken -> {
                                                        Toast.makeText(
                                                            context,
                                                            "¡Este viaje ya fue tomado por otro tuctuc!",
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                    }
                                                    is SupabaseRepository.AcceptTripResult.Error -> {
                                                        Toast.makeText(
                                                            context,
                                                            "Error de conexión: ${acceptResult.message}",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(72.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(28.dp),
                                            tint = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "ACEPTAR VIAJE",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White,
                                            letterSpacing = 1.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── MODAL DE RENOVACIÓN DE SUSCRIPCIÓN (DINÁMICO DESDE SUPABASE APP_CONFIG) ──
            if (showRenewalDialog) {
                var boletaRefInput by remember { mutableStateOf("") }
                var selectedPlan by remember { mutableStateOf("semanal") }
                var isSubmittingBoleta by remember { mutableStateOf(false) }

                AlertDialog(
                    onDismissRequest = { showRenewalDialog = false },
                    icon = { Icon(Icons.Default.AccountBalance, contentDescription = null, tint = Color(0xFFF9D423), modifier = Modifier.size(36.dp)) },
                    title = {
                        Text("Renovar Suscripción de Conductor", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .imePadding()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Para renovar tu cuenta, deposita el monto de tu plan (los días se sumarán a tu fecha actual de vencimiento sin perder vigencia):", fontSize = 13.sp, color = Color.LightGray)

                            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = String.format(Locale.US, "• Semanal (7 días): Q %.2f", appConfig.priceSemanal),
                                        color = Color(0xFFF9D423),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = String.format(Locale.US, "• Quincenal (15 días): Q %.2f", appConfig.priceQuincenal),
                                        color = Color(0xFFF9D423),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = String.format(Locale.US, "• Mensual (30 días): Q %.2f", appConfig.priceMensual),
                                        color = Color(0xFF4ADE80),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            Card(colors = CardDefaults.cardColors(containerColor = Color(0x22FFFFFF))) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("Banrural: ${appConfig.banruralAccount}", color = Color.White, fontSize = 13.sp)
                                        IconButton(onClick = { copyToClipboard("Banrural", appConfig.banruralAccount.replace("-", "")) }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color(0xFFF9D423), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("Industrial: ${appConfig.biAccount}", color = Color.White, fontSize = 13.sp)
                                        IconButton(onClick = { copyToClipboard("Banco Industrial", appConfig.biAccount.replace("-", "")) }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color(0xFFF9D423), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = boletaRefInput,
                                onValueChange = { boletaRefInput = it },
                                label = { Text("N° de Boleta o Transferencia (Opcional)", fontSize = 12.sp) },
                                placeholder = { Text("Ej. 84920145") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFFF9D423),
                                    unfocusedBorderColor = Color(0x55FFFFFF),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedLabelColor = Color(0xFFF9D423),
                                    unfocusedLabelColor = Color.LightGray
                                )
                            )

                            if (boletaRefInput.isNotBlank()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                    listOf("semanal" to "7d", "quincenal" to "15d", "mensual" to "30d").forEach { (planKey, label) ->
                                        FilterChip(
                                            selected = selectedPlan == planKey,
                                            onClick = { selectedPlan = planKey },
                                            label = { Text(label, fontSize = 12.sp) }
                                        )
                                    }
                                }
                                Button(
                                    onClick = {
                                        isSubmittingBoleta = true
                                        val amount = when (selectedPlan) {
                                            "quincenal" -> appConfig.priceQuincenal
                                            "mensual" -> appConfig.priceMensual
                                            else -> appConfig.priceSemanal
                                        }
                                        coroutineScope.launch {
                                            val res = repository.submitPaymentRequest(
                                                planType = selectedPlan,
                                                amount = amount,
                                                referenceNumber = boletaRefInput
                                            )
                                            isSubmittingBoleta = false
                                            res.onSuccess {
                                                Toast.makeText(context, "Boleta registrada en el sistema. Envía también la foto por WhatsApp.", Toast.LENGTH_LONG).show()
                                            }.onFailure { err ->
                                                Toast.makeText(context, "Error: ${err.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    enabled = !isSubmittingBoleta,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF9D423), contentColor = Color(0xFF0F172A))
                                ) {
                                    Text("Registrar N° de Boleta en App", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }

                            Button(
                                onClick = {
                                    val driverName = driverProfile?.nombre ?: ""
                                    val plate = driverProfile?.displayIdentifier ?: ""
                                    val refExtra = if (boletaRefInput.isNotBlank()) " (Boleta N°: ${boletaRefInput.trim()})" else ""
                                    val msg = "Hola Administrador de TucTucMap, soy $driverName ($plate). Deseo renovar mi suscripción$refExtra. Adjunto mi boleta de pago:"
                                    val adminPhone = appConfig.adminWhatsappPhone.replace(Regex("[^0-9]"), "")
                                    val waUrl = "https://api.whatsapp.com/send?phone=$adminPhone&text=${Uri.encode(msg)}"
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(waUrl)))
                                    showRenewalDialog = false
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366), contentColor = Color.White)
                            ) {
                                Icon(Icons.Default.Chat, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Enviar Boleta por WhatsApp", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    },
                    confirmButton = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = {
                                showRenewalDialog = false
                                onViewSubscription()
                            }) {
                                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF38BDF8))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Ver Solicitudes", color = Color(0xFF38BDF8))
                            }
                            TextButton(onClick = { showRenewalDialog = false }) {
                                Text("Cerrar", color = Color.LightGray)
                            }
                        }
                    },
                    containerColor = Color(0xFF0F172A),
                    textContentColor = Color.White
                )
            }
        }
    }
}

// ── Iniciar/Detener el Foreground Service del Conductor ───────────────────────
private fun toggleService(context: Context, driver: Driver?, start: Boolean) {
    val intent = Intent(context, LocationTrackingService::class.java)
    if (start && driver != null) {
        intent.putExtra("DRIVER_UID", driver.id)
        intent.putExtra("DRIVER_NOMBRE", driver.nombre)
        intent.putExtra("DRIVER_TELEFONO", driver.telefono)
        intent.putExtra("DRIVER_PLACAS", driver.placas)
        intent.putExtra("DRIVER_UNIDAD", driver.numeroUnidad)
        ContextCompat.startForegroundService(context, intent)
    } else {
        context.stopService(intent)
    }
}
