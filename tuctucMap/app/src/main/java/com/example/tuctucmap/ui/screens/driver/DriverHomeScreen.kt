package com.example.tuctucmap.ui.screens.driver

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.tuctucmap.data.model.Driver
import com.example.tuctucmap.data.repository.SupabaseRepository
import com.example.tuctucmap.service.LocationTrackingService
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun DriverHomeScreen(
    repository: SupabaseRepository,
    onLogout: () -> Unit,
    onSubscriptionExpired: () -> Unit = {},
    onApprovalRevoked: () -> Unit = {},
    onViewMap: () -> Unit = {},
    onViewHistory: () -> Unit = {}
) {
    val context = LocalContext.current
    val uid = repository.currentUserUid ?: ""

    val coroutineScope = rememberCoroutineScope()
    var pendingTrips by remember { mutableStateOf<List<SupabaseRepository.TripInfo>>(emptyList()) }
    var isUpdatingTrip by remember { mutableStateOf(false) }
    
    val currentLocation by LocationTrackingService.currentLocation.collectAsState()
    
    var previousTripsCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(pendingTrips.size) {
        if (pendingTrips.size > previousTripsCount) {
            try {
                val uri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                val r = android.media.RingtoneManager.getRingtone(context, uri)
                r.play()
            } catch (e: Exception) {}
        }
        previousTripsCount = pendingTrips.size
    }

    LaunchedEffect(uid) {
        if (uid.isNotBlank()) {
            repository.observePendingTripsForDriver(uid).collectLatest { trips ->
                pendingTrips = trips
            }
        }
    }

    var driverProfile by remember { mutableStateOf<Driver?>(null) }
    var isServiceRunning by remember { mutableStateOf(LocationTrackingService.isRunning) }
    var showPermissionError by remember { mutableStateOf(false) }

    // Escuchar el perfil en tiempo real: detecta cambios de estado y fecha de vencimiento
    LaunchedEffect(uid) {
        if (uid.isNotBlank()) {
            var subscriptionExpiredHandled = false
            var statusRevokedHandled = false

            repository.observeDriverProfile(uid).collectLatest { driver ->
                if (driver == null) return@collectLatest
                driverProfile = driver

                when (driver.estadoAprobacion) {
                    "rechazado", "pendiente" -> {
                        if (!statusRevokedHandled) {
                            statusRevokedHandled = true
                            if (LocationTrackingService.isRunning) {
                                toggleService(context, driver, false)
                            }
                            onApprovalRevoked()
                        }
                    }
                    "aprobado" -> {
                        val expiry = driver.fechaVencimientoSuscripcion
                        // subscription_end_date
                        val expiryMs = if (expiry.isNotBlank()) {
                            try {
                                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                (sdf.parse(expiry)?.time ?: 0L)
                            } catch (e: Exception) { 0L }
                        } else 0L
                        val isExpired = expiryMs > 0L && System.currentTimeMillis() > expiryMs
                        if (isExpired && !subscriptionExpiredHandled) {
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

    // ── Permisos de ubicación ────────────────────────────────────────────────
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
        permissionLauncher.launch(locationPermissions)
    }

    // ── Lambda de conectar/desconectar ───────────────────────────────────────
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
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !postNotificationsGranted) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    permissionLauncher.launch(locationPermissions)
                }
            }
        }
    }


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F2027),
                        Color(0xFF203A43),
                        Color(0xFF2C5364)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Cabecera ─────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TucTuc Conductor",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = {
                        if (isServiceRunning) toggleService(context, driverProfile, false)
                        onLogout()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Logout,
                        contentDescription = "Cerrar Sesión",
                        tint = Color(0xFFFF5252)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Card del perfil ───────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0x15FFFFFF))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = driverProfile?.nombre ?: "Cargando perfil...",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.DirectionsCar, contentDescription = "Placas", tint = Color(0xFFF9D423))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = "Placas: ${driverProfile?.placas ?: "..."}", fontSize = 16.sp, color = Color.LightGray)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Phone, contentDescription = "Teléfono", tint = Color(0xFFF9D423))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = "Teléfono: ${driverProfile?.telefono ?: "..."}", fontSize = 16.sp, color = Color.LightGray)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Fecha de vencimiento de suscripción
                    val trialStartStr = driverProfile?.fechaVencimientoSuscripcion ?: ""
                    val expiryMs = if (trialStartStr.isNotBlank()) {
                        try {
                            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                            (sdf.parse(trialStartStr)?.time ?: 0L)
                        } catch (e: Exception) { 0L }
                    } else 0L
                    val now = System.currentTimeMillis()
                    val sevenDaysMs = 7L * 24 * 60 * 60 * 1000
                    val (expiryText, expiryColor) = when {
                        expiryMs == 0L -> Pair("Sin fecha asignada", Color(0xFF64748B))
                        now > expiryMs -> Pair("Venció: ${SimpleDateFormat("dd/MM/yyyy", Locale("es")).format(Date(expiryMs))}", Color(0xFFFF5252))
                        (expiryMs - now) <= sevenDaysMs -> Pair("Vence: ${SimpleDateFormat("dd/MM/yyyy", Locale("es")).format(Date(expiryMs))}", Color(0xFFFFA726))
                        else -> Pair("Vence: ${SimpleDateFormat("dd/MM/yyyy", Locale("es")).format(Date(expiryMs))}", Color(0xFF4CAF50))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.CalendarMonth, contentDescription = "Vencimiento", tint = expiryColor)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = expiryText, fontSize = 16.sp, color = expiryColor)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── Estado de conexión ────────────────────────────────────────────
            Text(
                text = if (isServiceRunning) "ESTADO: EN LÍNEA" else "ESTADO: DESCONECTADO",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isServiceRunning) Color(0xFF4CAF50) else Color(0xFFFF5252)
            )

            Text(
                text = if (isServiceRunning) {
                    "Tu ubicación se está transmitiendo. Los pasajeros te verán en el mapa."
                } else {
                    "Conéctate para aparecer en el mapa y permitir que te contacten."
                },
                fontSize = 14.sp,
                color = Color.LightGray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Botón de encendido circular ───────────────────────────────────
            Button(
                onClick = onConnectToggle,
                modifier = Modifier.size(160.dp),
                shape = RoundedCornerShape(80.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isServiceRunning) Color(0xFFFF5252) else Color(0xFF4CAF50)
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isServiceRunning) "Desconectar" else "Conectar",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            if (showPermissionError) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Se requieren permisos de ubicación para poder transmitir tu ubicación en tiempo real.",
                    color = Color(0xFFFF6B6B),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Botón: Ver mapa en pantalla completa ──────────────────────────
            Button(
                onClick = onViewMap,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1E3A5F),
                    contentColor = Color.White
                )
            ) {
                Icon(imageVector = Icons.Default.Map, contentDescription = null)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Ver Conductores en el Mapa",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onViewHistory,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C5364), contentColor = Color.White)
            ) {
                Icon(imageVector = Icons.Default.History, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Historial de Viajes", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        // ── Lista de Solicitudes Pendientes (Flotante) ──────────────────────────
        if (pendingTrips.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = 40.dp, start = 16.dp, end = 16.dp)
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(pendingTrips, key = { it.tripId }) { trip ->
                    var isVisible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { isVisible = true }
                    
                    AnimatedVisibility(
                        visible = isVisible,
                        enter = fadeIn(animationSpec = tween(500)) + slideInVertically(initialOffsetY = { -it }),
                        exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(animationSpec = tween(500))
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E3A5F)),
                            elevation = CardDefaults.cardElevation(4.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("¡Nueva solicitud!", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                                    // Distancia
                                    val distanceText = if (currentLocation != null) {
                                        val results = FloatArray(1)
                                        android.location.Location.distanceBetween(
                                            currentLocation!!.latitude, currentLocation!!.longitude,
                                            trip.passengerLat, trip.passengerLon,
                                            results
                                        )
                                        val distMeters = results[0]
                                        if (distMeters > 1000) String.format(Locale.getDefault(), "%.1f km", distMeters / 1000) else "${distMeters.toInt()} m"
                                    } else "..."
                                    Text(distanceText, color = Color.LightGray, fontSize = 12.sp)
                                }
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Mensaje: ${trip.message}", color = Color(0xFF4CAF50), fontSize = 13.sp)
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Tiempo Restante (Barra y Texto)
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
                                        modifier = Modifier.weight(1f).height(6.dp),
                                        color = if (progress > 0.3f) Color(0xFFF9D423) else Color(0xFFFF5252),
                                        trackColor = Color(0xFF0F2027)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("${(timeLeft / 1000).coerceAtLeast(0)}s", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    OutlinedButton(
                                        onClick = {
                                            isVisible = false
                                            coroutineScope.launch {
                                                kotlinx.coroutines.delay(300)
                                                repository.updateTripStatus(trip.tripId, "cancelled")
                                                kotlinx.coroutines.delay(10000)
                                                repository.deleteTrip(trip.tripId)
                                            }
                                        },
                                        modifier = Modifier.height(36.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                    ) {
                                        Text("Rechazar", color = Color(0xFFFF5252), fontSize = 13.sp)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            isVisible = false
                                            coroutineScope.launch {
                                                kotlinx.coroutines.delay(300)
                                                repository.saveCompletedTripToSupabase(trip)
                                                repository.updateTripStatus(trip.tripId, "accepted")
                                                // Abrir mapa
                                                try {
                                                    val uri = android.net.Uri.parse("google.navigation:q=${trip.passengerLat},${trip.passengerLon}")
                                                    val mapIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                                                    mapIntent.setPackage("com.google.android.apps.maps")
                                                    if (mapIntent.resolveActivity(context.packageManager) != null) {
                                                        context.startActivity(mapIntent)
                                                    } else {
                                                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
                                                    }
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                                kotlinx.coroutines.delay(10000)
                                                repository.deleteTrip(trip.tripId)
                                            }
                                        },
                                        modifier = Modifier.height(36.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                                    ) {
                                        Text("Aceptar", fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }
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
