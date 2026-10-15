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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
    onViewProfile: () -> Unit = {}
) {
    val context = LocalContext.current
    val uid = repository.currentUserUid ?: ""

    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var pendingTrips by remember { mutableStateOf<List<SupabaseRepository.TripInfo>>(emptyList()) }
    var showRenewalDialog by remember { mutableStateOf(false) }
    var activeAcceptedTrip by remember { mutableStateOf<SupabaseRepository.TripInfo?>(null) }

    val currentLocation by LocationTrackingService.currentLocation.collectAsState()
    val driverAvailability by LocationTrackingService.driverStatus.collectAsState()
    var isServiceRunning by remember { mutableStateOf(LocationTrackingService.isRunning) }
    var showPermissionError by remember { mutableStateOf(false) }
    var driverProfile by remember { mutableStateOf<Driver?>(null) }

    // Contador de viajes completados hoy
    var completedTripsToday by remember { mutableIntStateOf(0) }
    fun refreshTripStats() {
        coroutineScope.launch {
            repository.getDriverTripHistory().onSuccess { tripList ->
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                completedTripsToday = tripList.count {
                    (it.status == "accepted" || it.status == "completed") && it.created_at.startsWith(todayStr)
                }
            }
        }
    }

    LaunchedEffect(uid) {
        refreshTripStats()
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

    // Alarma auditiva y voz al llegar solicitudes pendientes
    var previousTripsCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(pendingTrips.size, activeAcceptedTrip, driverAvailability, isServiceRunning) {
        if (activeAcceptedTrip == null && isServiceRunning && driverAvailability == "disponible") {
            if (pendingTrips.size > previousTripsCount) {
                val latestTrip = pendingTrips.firstOrNull()
                try {
                    val uri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                    val r = android.media.RingtoneManager.getRingtone(context, uri)
                    r.play()
                } catch (_: Exception) {}

                if (latestTrip != null && textToSpeech != null) {
                    try {
                        val msg = latestTrip.message.ifBlank { "Solicitud de viaje" }
                        textToSpeech?.speak("¡Nueva solicitud de viaje! $msg", TextToSpeech.QUEUE_FLUSH, null, null)
                    } catch (_: Exception) {}
                }
            }
        } else {
            try { textToSpeech?.stop() } catch (_: Exception) {}
        }
        previousTripsCount = pendingTrips.size
    }

    // Escuchar solicitudes pendientes en Firebase
    LaunchedEffect(uid) {
        if (uid.isNotBlank()) {
            repository.observePendingTripsForDriver(uid).collectLatest { trips ->
                pendingTrips = trips
            }
        }
    }

    // Sincronizar viaje activo aceptado por el conductor en Firebase
    LaunchedEffect(uid) {
        if (uid.isNotBlank()) {
            repository.observeAcceptedTripForDriver(uid).collectLatest { active ->
                if (active != null) {
                    activeAcceptedTrip = active
                }
            }
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
                try {
                    context.getSharedPreferences("tuctuc_prefs", Context.MODE_PRIVATE)
                        .edit()
                        .putString("driver_last_approval_status", driver.estadoAprobacion)
                        .putString("driver_last_sub_end", driver.subscriptionEndDate)
                        .apply()
                } catch (_: Exception) {}

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

    // Cálculo preventivo de suscripción
    val expiryMs = driverProfile?.expiryTimestampMillis ?: 0L
    val now = System.currentTimeMillis()
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
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xF2162634),
                    border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { coroutineScope.launch { drawerState.open() } },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Menú",
                                    tint = Color.White
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            Column {
                                Text(
                                    text = "TucTuc Conductor",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = driverProfile?.placas?.let { "Placas: $it" } ?: "Torito en línea",
                                    color = Color(0xFFF9D423),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Pastilla de estado de suscripción & Avatar de perfil
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isExpiringSoon) {
                                Surface(
                                    onClick = { showRenewalDialog = true },
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0x33FFA726),
                                    border = BorderStroke(1.dp, Color(0xFFFFA726))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
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
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0x224ADE80),
                                    border = BorderStroke(1.dp, Color(0x444ADE80))
                                ) {
                                    Text(
                                        text = "Plan Activo",
                                        color = Color(0xFF81C784),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = onViewProfile,
                                modifier = Modifier.size(36.dp)
                            ) {
                                val initial = (driverProfile?.nombre?.trim()?.firstOrNull() ?: 'C').uppercaseChar().toString()
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
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F2027)
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Cuerpo Desplazable del Cockpit ──────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 10.dp),
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
                            border = BorderStroke(1.5.dp, Color(0xFFFFB74D))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFFFB74D),
                                    modifier = Modifier.size(26.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Tu suscripción vence pronto",
                                        color = Color(0xFFFFB74D),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "Vence en $hoursRemaining horas. Renueva para mantener tu cuenta activa.",
                                        color = Color.White,
                                        fontSize = 11.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Button(
                                    onClick = { showRenewalDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB74D), contentColor = Color(0xFF0F2027)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Renovar", fontWeight = FontWeight.Bold, fontSize = 11.sp)
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
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF131D26)),
                            border = BorderStroke(1.5.dp, Color(0x55EF5350)),
                            elevation = CardDefaults.cardElevation(8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x22EF5350))
                                        .border(2.dp, Color(0x66EF5350), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PowerSettingsNew,
                                        contentDescription = null,
                                        tint = Color(0xFFFF5252),
                                        modifier = Modifier.size(34.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Text(
                                    text = "ESTÁS FUERA DE SERVICIO",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    letterSpacing = 0.5.sp
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "Los pasajeros no pueden verte en el mapa ni enviarte viajes. Inicia turno para comenzar a trabajar.",
                                    fontSize = 13.sp,
                                    color = Color(0xFF94A3B8),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 10.dp)
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                // BOTÓN GIGANTE: INICIAR TURNO
                                Button(
                                    onClick = onConnectToggle,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(64.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF2E7D32),
                                        contentColor = Color.White
                                    ),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ElectricRickshaw,
                                        contentDescription = null,
                                        modifier = Modifier.size(26.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "⚡ INICIAR TURNO (EN LÍNEA)",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                        }
                    } else {
                        // ── ESTADO: EN LÍNEA ────────────────────────────────────────
                        val isAvailable = driverAvailability == "disponible"
                        val borderColor = if (isAvailable) Color(0xFF4ADE80) else Color(0xFFFFA726)

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF131D26)),
                            border = BorderStroke(2.dp, borderColor),
                            elevation = CardDefaults.cardElevation(10.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Indicador de radar o transmisión
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(if (isAvailable) Color(0xFF4ADE80) else Color(0xFFFFA726))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isAvailable) "EN LÍNEA (DISPONIBLE)" else "EN LÍNEA (EN PAUSA)",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        letterSpacing = 0.5.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isAvailable) Color(0x1A4ADE80) else Color(0x1AFFA726),
                                    border = BorderStroke(1.dp, if (isAvailable) Color(0x334ADE80) else Color(0x33FFA726)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = if (isAvailable) "📡 RADAR DE VIAJES ACTIVO" else "⏸️ MODO PAUSA TEMPORAL",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (isAvailable) Color(0xFF4ADE80) else Color(0xFFFFA726)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = if (isAvailable)
                                                "Transmitiendo coordenadas GPS. Recibirás solicitudes con alerta de voz."
                                            else
                                                "Estás descansando. No recibirás nuevos viajes hasta reactivarte.",
                                            fontSize = 12.sp,
                                            color = Color.LightGray,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(18.dp))

                                // Botonera de control de turno en línea
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Botón de alternancia Pausa / Activar
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
                                            .height(54.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isAvailable) Color(0xFFEF6C00) else Color(0xFF2E7D32),
                                            contentColor = Color.White
                                        )
                                    ) {
                                        Icon(
                                            imageVector = if (isAvailable) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isAvailable) "Pausar" else "Reanudar",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }

                                    // Botón Desconectar Turno
                                    OutlinedButton(
                                        onClick = onConnectToggle,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(54.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(1.5.dp, Color(0xFFFF5252)),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = Color(0xFFFF5252)
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PowerSettingsNew,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Finalizar",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (showPermissionError) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Se requieren permisos de ubicación para poder transmitir tu ubicación en tiempo real.",
                            color = Color(0xFFFF6B6B),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 3. TARJETA DE RESUMEN DE LA JORNADA (KPIs RÁPIDOS)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0x1AFFFFFF)),
                        border = BorderStroke(1.dp, Color(0x22FFFFFF))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📊 Tu jornada de hoy",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(
                                    onClick = { refreshTripStats() },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("Actualizar", fontSize = 11.sp, color = Color(0xFFF9D423))
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Métrica 1: Viajes Hoy
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF16232F)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("Viajes Hoy", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "$completedTripsToday",
                                            color = Color(0xFFF9D423),
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }

                                // Métrica 2: Estado GPS
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF16232F)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("Señal GPS", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (currentLocation != null) "Óptima 🛰️" else "Buscando...",
                                            color = if (currentLocation != null) Color(0xFF4ADE80) else Color(0xFFFFB74D),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Métrica 3: Torito
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF16232F)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("Vehículo", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = driverProfile?.placas ?: "---",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 4. ACCESOS RÁPIDOS (HERRAMIENTAS DE APOYO)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Botón secundario: Ver Mapa en Vivo (opcional, no forzado)
                        Button(
                            onClick = onViewMap,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1E3A5F),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Mapa de Flota", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        // Botón secundario: Historial de Viajes
                        Button(
                            onClick = onViewHistory,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2C5364),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Historial", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 5. RESUMEN DE PERFIL Y VENCIMIENTO
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0x12FFFFFF))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = driverProfile?.nombre ?: "Conductor",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Tel: ${driverProfile?.telefono ?: "..."}",
                                        fontSize = 12.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }

                                TextButton(onClick = onViewProfile) {
                                    Text("Editar", color = Color(0xFFF9D423), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }

                            val sevenDaysMs = 7L * 24 * 60 * 60 * 1000
                            val spanishLocale = Locale.forLanguageTag("es")
                            val (expiryText, expiryColor) = when {
                                expiryMs == 0L -> Pair("Sin fecha asignada", Color(0xFF64748B))
                                now > expiryMs -> Pair("Venció: ${SimpleDateFormat("dd/MM/yyyy", spanishLocale).format(Date(expiryMs))}", Color(0xFFFF5252))
                                (expiryMs - now) <= sevenDaysMs -> Pair("Vence: ${SimpleDateFormat("dd/MM/yyyy", spanishLocale).format(Date(expiryMs))}", Color(0xFFFFA726))
                                else -> Pair("Vence: ${SimpleDateFormat("dd/MM/yyyy", spanishLocale).format(Date(expiryMs))}", Color(0xFF4CAF50))
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = expiryColor, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = expiryText, fontSize = 12.sp, color = expiryColor, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(100.dp)) // Espacio para evitar solapamiento con tarjetas flotantes
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

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    try {
                                        val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${activeAcceptedTrip!!.passengerLat},${activeAcceptedTrip!!.passengerLon}")
                                        val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                                        context.startActivity(mapIntent)
                                    } catch (_: Exception) {}
                                },
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                            ) {
                                Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Abrir GPS", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }

                            if (activeAcceptedTrip!!.passengerPhone.isNotBlank()) {
                                Button(
                                    onClick = {
                                        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${activeAcceptedTrip!!.passengerPhone}"))
                                        context.startActivity(dialIntent)
                                    },
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                                ) {
                                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFF9D423))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Llamar", fontSize = 13.sp, color = Color(0xFFF9D423))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    val tripToFinish = activeAcceptedTrip
                                    val tid = tripToFinish?.tripId
                                    if (tid != null) {
                                        repository.updateTripStatus(tid, "completed")
                                        val saveResult = repository.saveTripRecord(
                                            tripInfo = tripToFinish.copy(driverId = uid),
                                            status = "completed",
                                            driverIdOverride = uid
                                        )
                                        if (saveResult.isFailure) {
                                            android.util.Log.e("DriverHomeScreen", "Error al guardar viaje en Supabase", saveResult.exceptionOrNull())
                                        }
                                    }
                                    LocationTrackingService.driverStatus.value = "disponible"
                                    repository.updateDriverStatus(uid, "disponible")
                                    activeAcceptedTrip = null
                                    try { textToSpeech?.stop() } catch (_: Exception) {}
                                    refreshTripStats()
                                    Toast.makeText(context, "Viaje finalizado. ¡Estás disponible nuevamente!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF9D423), contentColor = Color(0xFF0F2027))
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Terminar Viaje / Volver a Disponible", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
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
                                                coroutineScope.launch {
                                                    kotlinx.coroutines.delay(200)
                                                    repository.saveTripRecord(trip, "rejected")
                                                    repository.updateTripStatus(trip.tripId, "cancelled")
                                                    kotlinx.coroutines.delay(10000)
                                                    repository.deleteTrip(trip.tripId)
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

                                    // Barra de tiempo restante (60s)
                                    var timeLeft by remember { mutableLongStateOf(60000L - (System.currentTimeMillis() - trip.timestamp)) }
                                    LaunchedEffect(trip.timestamp) {
                                        while (timeLeft > 0) {
                                            kotlinx.coroutines.delay(1000)
                                            timeLeft = 60000L - (System.currentTimeMillis() - trip.timestamp)
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
                                                    driverPlate = driverProfile?.placas ?: ""
                                                )

                                                when (acceptResult) {
                                                    is SupabaseRepository.AcceptTripResult.Success -> {
                                                        LocationTrackingService.driverStatus.value = "ocupado"
                                                        repository.updateDriverStatus(uid, "ocupado")
                                                        val acceptedTrip = trip.copy(driverId = uid)
                                                        activeAcceptedTrip = acceptedTrip
                                                        try { textToSpeech?.stop() } catch (_: Exception) {}
                                                        Toast.makeText(context, "¡Viaje aceptado! En camino a recoger al pasajero.", Toast.LENGTH_SHORT).show()
                                                    }
                                                    is SupabaseRepository.AcceptTripResult.AlreadyTaken -> {
                                                        Toast.makeText(
                                                            context,
                                                            "¡Este viaje ya fue tomado por otro torito!",
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

            // ── MODAL DE RENOVACIÓN DE SUSCRIPCIÓN ──────────────────────────────
            if (showRenewalDialog) {
                AlertDialog(
                    onDismissRequest = { showRenewalDialog = false },
                    icon = { Icon(Icons.Default.AccountBalance, contentDescription = null, tint = Color(0xFFF9D423), modifier = Modifier.size(36.dp)) },
                    title = {
                        Text("Renovar Suscripción de Conductor", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Para renovar tu cuenta, deposita el monto de tu plan y envía la foto de tu boleta al administrador:", fontSize = 13.sp, color = Color.LightGray)

                            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("• Semanal (7 días): Q 25.00", color = Color(0xFFF9D423), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text("• Quincenal (15 días): Q 45.00", color = Color(0xFFF9D423), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text("• Mensual (30 días): Q 80.00", color = Color(0xFF4ADE80), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }

                            Card(colors = CardDefaults.cardColors(containerColor = Color(0x22FFFFFF))) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("Banrural: 3402-0987-65", color = Color.White, fontSize = 13.sp)
                                        IconButton(onClick = { copyToClipboard("Banrural", "3402098765") }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color(0xFFF9D423), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    val driverName = driverProfile?.nombre ?: ""
                                    val plate = driverProfile?.placas ?: ""
                                    val msg = "Hola Administrador de TucTucMap, soy $driverName (Placas: $plate). Deseo renovar mi suscripción. Adjunto mi boleta de pago:"
                                    val adminPhone = "50230000000"
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
                        TextButton(onClick = { showRenewalDialog = false }) {
                            Text("Cerrar", color = Color.LightGray)
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
        ContextCompat.startForegroundService(context, intent)
    } else {
        context.stopService(intent)
    }
}
