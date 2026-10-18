package com.example.tuctucmap.ui.screens.driver

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import com.example.tuctucmap.service.LocationTrackingService
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.tuctucmap.data.model.DriverLocation
import com.example.tuctucmap.data.repository.SupabaseRepository
import com.example.tuctucmap.ui.util.AppTileSource
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import android.animation.ValueAnimator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.AccelerateInterpolator
/**
 * Pantalla de mapa en pantalla completa.
 * Muestra todos los conductores activos en tiempo real.
 * El botón de regreso (← o hardware back) regresa a la pantalla anterior.
 *
 * [repository]  repositorio Firebase para escuchar ubicaciones activas
 * [onBack]      callback para volver a la pantalla anterior
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverMapFullscreenScreen(
    repository: SupabaseRepository,
    onBack: () -> Unit,
    onNavigateToRoute: (SupabaseRepository.TripInfo) -> Unit = {}
) {
    val context = LocalContext.current

    // Colectar las ubicaciones activas en tiempo real
    val driverLocations = remember { mutableStateListOf<DriverLocation>() }
    LaunchedEffect(Unit) {
        repository.observeActiveLocations().collectLatest { locations ->
            driverLocations.clear()
            driverLocations.addAll(locations)
        }
    }

    // Ícono del Tuc Tuc con fondo transparente y caché optimizada
    val tucTucIcon = remember(context) {
        com.example.tuctucmap.ui.util.MarkerIconUtils.getTucTucIcon(context)
    }
    val pausedTucTucIcon = remember(context) {
        com.example.tuctucmap.ui.util.MarkerIconUtils.getPausedTucTucIcon(context)
    }

    // Permiso de ubicación
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasLocationPermission = (perms[Manifest.permission.ACCESS_FINE_LOCATION] ?: false)
                || (perms[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false)
    }
    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    var selectedDriver by remember { mutableStateOf<DriverLocation?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }
    var myLocationOverlayInstance by remember { mutableStateOf<MyLocationNewOverlay?>(null) }
    val driverMarkers = remember { mutableMapOf<String, Marker>() }

    val coroutineScope = rememberCoroutineScope()
    val uid = repository.currentUserUid ?: ""
    var driverProfile by remember { mutableStateOf<com.example.tuctucmap.data.model.Driver?>(null) }
    LaunchedEffect(uid) {
        if (uid.isNotBlank()) {
            repository.observeDriverProfile(uid).collectLatest { driverProfile = it }
        }
    }

    var pendingTrips by remember { mutableStateOf<List<SupabaseRepository.TripInfo>>(emptyList()) }
    var activeAcceptedTrip by remember { mutableStateOf<SupabaseRepository.TripInfo?>(null) }
    LaunchedEffect(uid) {
        if (uid.isNotBlank()) {
            repository.observePendingTripsForDriver(uid).collectLatest { trips ->
                pendingTrips = trips
            }
        }
    }
    LaunchedEffect(uid) {
        if (uid.isNotBlank()) {
            repository.observeAcceptedTripForDriver(uid).collectLatest { active ->
                activeAcceptedTrip = active
            }
        }
    }

    val currentLocation by LocationTrackingService.currentLocation.collectAsState()
    val driverStatus by LocationTrackingService.driverStatus.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "TucTucs Disponibles",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 18.sp
                        )
                        val availableCount = driverLocations.count { it.isAvailable }
                        val pausedCount = driverLocations.size - availableCount
                        val subtitleText = when {
                            driverLocations.isEmpty() -> "Sin conductores activos"
                            pausedCount == 0 -> "$availableCount conductor${if (availableCount != 1) "es" else ""} en línea"
                            availableCount == 0 -> "0 disponibles ($pausedCount en pausa)"
                            else -> "$availableCount disponible${if (availableCount != 1) "s" else ""} • $pausedCount en pausa"
                        }
                        Text(
                            text = subtitleText,
                            color = if (availableCount > 0) Color(0xFF4CAF50) else Color(0xFFFFB74D),
                            fontSize = 13.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Regresar",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F2027)
                )
            )
        },
        floatingActionButton = {
            if (hasLocationPermission && pendingTrips.isEmpty()) {
                FloatingActionButton(
                    onClick = {
                        myLocationOverlayInstance?.myLocation?.let { loc ->
                            mapViewInstance?.controller?.animateTo(loc)
                        }
                    },
                    containerColor = Color(0xFFF9D423),
                    contentColor = Color(0xFF0F2027)
                ) {
                    Icon(imageVector = Icons.Default.MyLocation, contentDescription = "Mi ubicación")
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF2C5364))
        ) {
            // ── Mapa ─────────────────────────────────────────────────────────
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(AppTileSource)
                        setMultiTouchControls(true)
                        controller.setZoom(16.0)
                        controller.setCenter(GeoPoint(14.6349, -90.5069))

                        if (hasLocationPermission) {
                            val myLocOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this).apply {
                                enableMyLocation()
                                runOnFirstFix {
                                    myLocation?.let { loc -> post { controller.animateTo(loc) } }
                                }
                            }
                            overlays.add(myLocOverlay)
                            myLocationOverlayInstance = myLocOverlay
                        }
                        mapViewInstance = this
                    }
                },
                update = { mapView ->
                    val currentUids = driverLocations.map { it.uid }.toSet()
                    val removedUids = driverMarkers.keys.filter { it !in currentUids }
                    for (uid in removedUids) {
                        val marker = driverMarkers.remove(uid)
                        if (marker != null) {
                            val startLat = marker.position.latitude
                            val exitAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
                                duration = 500L
                                interpolator = AccelerateInterpolator()
                                addUpdateListener { animation ->
                                    val fraction = animation.animatedValue as Float
                                    marker.setAlpha(fraction)
                                    val currentLat = startLat + 0.002 * (1f - fraction)
                                    marker.position = GeoPoint(currentLat, marker.position.longitude)
                                    mapView.invalidate()
                                }
                                addListener(object : AnimatorListenerAdapter() {
                                    override fun onAnimationEnd(animation: Animator) {
                                        mapView.overlays.remove(marker)
                                        mapView.invalidate()
                                    }
                                })
                            }
                            exitAnimator.start()
                        }
                    }

                    driverLocations.forEach { driverLoc ->
                        val newGeoPoint = GeoPoint(driverLoc.latitud, driverLoc.longitud)
                        var marker = driverMarkers[driverLoc.uid]

                        val isAvail = driverLoc.isAvailable
                        val targetIcon = if (isAvail) tucTucIcon else pausedTucTucIcon
                        val targetAlpha = if (isAvail) 1.0f else 0.65f
                        val statusTag = if (isAvail) "🟢 DISPONIBLE" else "🟠 EN PAUSA"

                        if (marker == null) {
                            val startLat = newGeoPoint.latitude + 0.002
                            marker = Marker(mapView).apply {
                                position = GeoPoint(startLat, newGeoPoint.longitude)
                                setAlpha(0f)
                                title = driverLoc.nombre
                                snippet = "Placas: ${driverLoc.placas} • $statusTag"
                                icon = targetIcon
                                setOnMarkerClickListener { _, _ ->
                                    selectedDriver = driverLoc
                                    showBottomSheet = true
                                    true
                                }
                            }
                            driverMarkers[driverLoc.uid] = marker
                            mapView.overlays.add(marker)

                            val entranceAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                                duration = 1000L
                                interpolator = OvershootInterpolator()
                                addUpdateListener { animation ->
                                    val fraction = animation.animatedValue as Float
                                    val currentLat = startLat + (newGeoPoint.latitude - startLat) * fraction
                                    marker.position = GeoPoint(currentLat, newGeoPoint.longitude)
                                    marker.setAlpha((fraction * targetAlpha).coerceIn(0f, targetAlpha))
                                    mapView.invalidate()
                                }
                            }
                            entranceAnimator.start()
                        } else {
                            val oldGeoPoint = marker.position
                            if (oldGeoPoint.latitude != newGeoPoint.latitude || oldGeoPoint.longitude != newGeoPoint.longitude) {
                                val animator = ValueAnimator.ofFloat(0f, 1f)
                                animator.duration = 4000L
                                animator.interpolator = LinearInterpolator()
                                animator.addUpdateListener { animation ->
                                    val fraction = animation.animatedFraction
                                    val lat = oldGeoPoint.latitude + (newGeoPoint.latitude - oldGeoPoint.latitude) * fraction
                                    val lon = oldGeoPoint.longitude + (newGeoPoint.longitude - oldGeoPoint.longitude) * fraction
                                    marker?.position = GeoPoint(lat, lon)
                                    mapView.invalidate()
                                }
                                animator.start()
                            }
                            marker.title = driverLoc.nombre
                            marker.snippet = "Placas: ${driverLoc.placas} • $statusTag"
                            marker.icon = targetIcon
                            marker.setAlpha(targetAlpha)
                        }
                    }
                    mapView.invalidate()
                },
                modifier = Modifier.fillMaxSize()
            )

            // ── Mensaje si no hay conductores activos ─────────────────────────
            if (driverLocations.isEmpty()) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 90.dp, start = 16.dp, end = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xCC0F2027))
                ) {
                    Text(
                        text = "🛺  Sin conductores activos en este momento",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                }
            }

            // ── Banner Flotante de Viaje Activo (Navegación al Pasajero) ──────
            if (activeAcceptedTrip != null) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xF20F172A)),
                    border = BorderStroke(1.5.dp, Color(0xFF4ADE80)),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "VIAJE EN CURSO",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF4ADE80)
                            )
                            Text(
                                text = "Pasajero: ${activeAcceptedTrip!!.passengerName.ifBlank { "Pasajero" }}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { onNavigateToRoute(activeAcceptedTrip!!) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A86B)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ver Ruta", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ── Solicitudes Pendientes Flotantes en Vivo sobre el Mapa (Fase 2 - Acción 2 de ANALISIS_UX_MOVIL.md) ──
            if (pendingTrips.isNotEmpty() && driverStatus == "disponible") {
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
                                elevation = CardDefaults.cardElevation(12.dp),
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(2.5.dp, Color(0xFFF9D423))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    // Encabezado con Distancia y Descarte Seguro
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
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Text(text = "✕", color = Color(0xFF94A3B8), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Contenedor de referencia del pasajero (Alto Contraste)
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFF1E293B),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("👤", fontSize = 14.sp)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = trip.passengerName.ifBlank { "Pasajero" },
                                                    color = Color(0xFFF9D423),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp
                                                )
                                                if (trip.passengerPhone.isNotBlank()) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "• ${trip.passengerPhone}",
                                                        color = Color(0xFF94A3B8),
                                                        fontSize = 13.sp
                                                    )
                                                }
                                            }
                                            if (trip.message.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = "\"${trip.message}\"",
                                                    color = Color.White,
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    // BOTÓN GIGANTE DE ACEPTACIÓN (66dp)
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
                                                        LocationTrackingService.startTripTracking(trip.tripId, currentLocation, acceptedTrip)
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
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF2E7D32),
                                            contentColor = Color.White
                                        ),
                                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                                    ) {
                                        Text(
                                            text = "ACEPTAR VIAJE",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 18.sp,
                                            letterSpacing = 1.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Bottom Sheet: detalles del conductor seleccionado ─────────────
            if (showBottomSheet && selectedDriver != null) {
                val driver = selectedDriver!!
                ModalBottomSheet(
                    onDismissRequest = {
                        showBottomSheet = false
                        selectedDriver = null
                    },
                    containerColor = Color(0xFF203A43),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = driver.nombre,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Placas del TucTuc: ${driver.placas}",
                                    fontSize = 16.sp,
                                    color = Color(0xFFF9D423),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(text = "🛺", fontSize = 40.sp)
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "Ponte en contacto con el conductor:",
                            fontSize = 14.sp,
                            color = Color.LightGray,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Button(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${driver.telefono}"))
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.weight(1f).height(54.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF4CAF50),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(imageVector = Icons.Default.Call, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Llamar", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }

                            Button(
                                onClick = {
                                    var phone = driver.telefono.replace(" ", "").replace("-", "")
                                    if (phone.length == 8) phone = "502$phone"
                                    val msg = "Hola, vi tu TucTuc en el mapa de la app. ¿Estás disponible para un viaje?"
                                    val url = "https://api.whatsapp.com/send?phone=$phone&text=${Uri.encode(msg)}"
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                },
                                modifier = Modifier.weight(1f).height(54.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF25D366),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(imageVector = Icons.Default.Chat, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("WhatsApp", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}
