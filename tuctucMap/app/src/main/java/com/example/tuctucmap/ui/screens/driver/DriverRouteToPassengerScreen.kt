package com.example.tuctucmap.ui.screens.driver

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.tuctucmap.data.DriverFarePreferences
import com.example.tuctucmap.data.repository.SupabaseRepository
import com.example.tuctucmap.data.service.OsrmRoutingService
import com.example.tuctucmap.service.LocationTrackingService
import com.example.tuctucmap.ui.util.AppTileSource
import com.example.tuctucmap.ui.util.ExternalNavigationUtils
import com.example.tuctucmap.ui.util.MarkerIconUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.util.Locale

/**
 * Pantalla de Navegación Nivel 1: Trazado visual de ruta interactivo dentro de la app móvil.
 * Permite al conductor navegar hasta el punto de recogida del pasajero a costo $0 utilizando OSRM
 * y OpenStreetMap (OSMDroid), sin depender de Google Maps.
 * Funciona de forma totalmente desacoplada del pasajero: aunque el pasajero salga de la app o bloquee
 * su teléfono, las coordenadas de recogida se mantienen fijas y accesibles.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverRouteToPassengerScreen(
    repository: SupabaseRepository,
    trip: SupabaseRepository.TripInfo,
    onBack: () -> Unit,
    onTripCompleted: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val uid = repository.currentUserUid ?: ""

    val currentLocation by LocationTrackingService.currentLocation.collectAsState()
    val liveTripDistanceMeters by LocationTrackingService.activeTripDistanceMeters.collectAsState()

    var routeResult by remember { mutableStateOf<OsrmRoutingService.RouteResult?>(null) }
    var isLoadingRoute by remember { mutableStateOf(true) }
    var lastCalculationLocation by remember { mutableStateOf<android.location.Location?>(null) }

    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }
    var driverMarker by remember { mutableStateOf<Marker?>(null) }
    var passengerMarker by remember { mutableStateOf<Marker?>(null) }
    var routePolyline by remember { mutableStateOf<Polyline?>(null) }
    var routeCasingPolyline by remember { mutableStateOf<Polyline?>(null) }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> mapViewInstance?.onResume()
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> mapViewInstance?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapViewInstance?.onPause()
            mapViewInstance?.onDetach()
        }
    }

    var showFinishTripDialog by remember { mutableStateOf(false) }
    var fareInputText by remember { mutableStateOf("") }
    var suggestedFares by remember { mutableStateOf<List<Double>>(emptyList()) }
    var isSubmittingFinish by remember { mutableStateOf(false) }

    val tucTucIcon = remember(context) { MarkerIconUtils.getTucTucIcon(context) }
    val passengerIcon = remember(context) { MarkerIconUtils.getPassengerPickupIcon(context) }

    val passengerGeoPoint = remember(trip.passengerLat, trip.passengerLon) {
        GeoPoint(trip.passengerLat, trip.passengerLon)
    }

    // Función para solicitar y actualizar la ruta
    fun calculateAndDrawRoute(force: Boolean = false) {
        val currLoc = currentLocation ?: return
        if (passengerGeoPoint.latitude == 0.0 || passengerGeoPoint.longitude == 0.0) return

        if (!force && lastCalculationLocation != null) {
            val distFromLast = currLoc.distanceTo(lastCalculationLocation!!)
            if (distFromLast < 30f) {
                return // Ahorro de solicitudes si no se ha movido más de 30m
            }
        }

        coroutineScope.launch {
            isLoadingRoute = true
            val result = OsrmRoutingService.getRoute(
                startLat = currLoc.latitude,
                startLon = currLoc.longitude,
                endLat = passengerGeoPoint.latitude,
                endLon = passengerGeoPoint.longitude
            )
            routeResult = result
            lastCalculationLocation = currLoc
            isLoadingRoute = false

            // Actualizar overlays en el mapa
            mapViewInstance?.let { mapView ->
                if (result.points.isNotEmpty()) {
                    // Contorno/borde oscuro de contraste para que la línea resalte sobre cualquier calle
                    if (routeCasingPolyline == null) {
                        routeCasingPolyline = Polyline(mapView).apply {
                            outlinePaint.apply {
                                color = android.graphics.Color.parseColor("#0B192C")
                                strokeWidth = 16f
                                strokeCap = android.graphics.Paint.Cap.ROUND
                                strokeJoin = android.graphics.Paint.Join.ROUND
                                isAntiAlias = true
                            }
                        }
                        mapView.overlays.add(0, routeCasingPolyline)
                    }
                    routeCasingPolyline?.setPoints(result.points)

                    // Línea principal de ruta en cian vibrante
                    if (routePolyline == null) {
                        routePolyline = Polyline(mapView).apply {
                            outlinePaint.apply {
                                color = android.graphics.Color.parseColor("#00D2FF")
                                strokeWidth = 10f
                                strokeCap = android.graphics.Paint.Cap.ROUND
                                strokeJoin = android.graphics.Paint.Join.ROUND
                                isAntiAlias = true
                            }
                        }
                        mapView.overlays.add(1, routePolyline)
                    }
                    routePolyline?.setPoints(result.points)

                    mapView.invalidate()
                }
            }
        }
    }

    // Calcular ruta inicial al montar la pantalla o cuando se obtiene la primera fijación GPS
    LaunchedEffect(currentLocation?.latitude, currentLocation?.longitude) {
        if (currentLocation != null) {
            calculateAndDrawRoute(force = (routeResult == null))
        }
    }

    // Función para ajustar la cámara y ver el viaje completo
    fun zoomToFitRoute() {
        val map = mapViewInstance ?: return
        val currLoc = currentLocation
        val points = mutableListOf<GeoPoint>()
        if (currLoc != null) points.add(GeoPoint(currLoc.latitude, currLoc.longitude))
        points.add(passengerGeoPoint)
        routeResult?.points?.let { points.addAll(it) }

        if (points.isNotEmpty()) {
            var north = -90.0
            var south = 90.0
            var east = -180.0
            var west = 180.0

            for (p in points) {
                if (p.latitude > north) north = p.latitude
                if (p.latitude < south) south = p.latitude
                if (p.longitude > east) east = p.longitude
                if (p.longitude < west) west = p.longitude
            }

            val latSpan = (north - south).coerceAtLeast(0.003) * 0.25
            val lonSpan = (east - west).coerceAtLeast(0.003) * 0.25

            val bbox = BoundingBox(north + latSpan, east + lonSpan, south - latSpan, west - lonSpan)
            try {
                map.zoomToBoundingBox(bbox, true, 100)
            } catch (_: Exception) {
                map.controller.setCenter(passengerGeoPoint)
                map.controller.setZoom(16.0)
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF0F172A))
        ) {
            // ── 1. MAPA OSMDROID A PANTALLA COMPLETA ─────────────────────────────
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(AppTileSource)
                        setMultiTouchControls(true)
                        controller.setZoom(16.5)
                        controller.setCenter(passengerGeoPoint)

                        // Marcador de recogida del Pasajero
                        val pMarker = Marker(this).apply {
                            position = passengerGeoPoint
                            icon = passengerIcon
                            title = trip.passengerName.ifBlank { "Pasajero" }
                            snippet = "Recoger aquí: ${trip.message.ifBlank { "Sin notas adicionales" }}"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
                        overlays.add(pMarker)
                        passengerMarker = pMarker

                        // Marcador del TucTuc Conductor
                        val dMarker = Marker(this).apply {
                            icon = tucTucIcon
                            title = "Mi TucTuc"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        }
                        currentLocation?.let {
                            dMarker.position = GeoPoint(it.latitude, it.longitude)
                            overlays.add(dMarker)
                        }
                        driverMarker = dMarker

                        mapViewInstance = this
                    }
                },
                update = { map ->
                    currentLocation?.let { loc ->
                        val marker = driverMarker ?: return@let
                        val oldPos = marker.position
                        val moved = oldPos == null ||
                            kotlin.math.abs(oldPos.latitude - loc.latitude) > 0.000005 ||
                            kotlin.math.abs(oldPos.longitude - loc.longitude) > 0.000005
                        if (!map.overlays.contains(marker)) {
                            map.overlays.add(marker)
                        }
                        if (moved) {
                            marker.position = GeoPoint(loc.latitude, loc.longitude)
                            map.invalidate()
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Ajuste automático de encuadre en primera carga de ruta
            var hasAutoFitted by remember { mutableStateOf(false) }
            LaunchedEffect(routeResult) {
                if (routeResult != null && !hasAutoFitted && mapViewInstance != null) {
                    hasAutoFitted = true
                    zoomToFitRoute()
                }
            }

            // ── 2. BARRA SUPERIOR FLOTANTE DE NAVEGACIÓN (HUD) ───────────────────
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xF00F172A)),
                border = BorderStroke(1.dp, Color(0x334ADE80)),
                elevation = CardDefaults.cardElevation(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color(0xFF1E293B), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Regresar",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Hacia: ${trip.passengerName.ifBlank { "Pasajero" }}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        val distanceStr = routeResult?.formattedDistance ?: "Calculando..."
                        val durationStr = routeResult?.formattedDuration ?: "..."

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = Color(0x334ADE80),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "$distanceStr • ~$durationStr",
                                    color = Color(0xFF4ADE80),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            if (isLoadingRoute) {
                                Spacer(modifier = Modifier.width(6.dp))
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF00D2FF)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Botón directo para llamar al pasajero
                    if (trip.passengerPhone.isNotBlank()) {
                        IconButton(
                            onClick = {
                                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${trip.passengerPhone}"))
                                context.startActivity(dialIntent)
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFF22C55E), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = "Llamar",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // ── 3. BOTONES DE CONTROL DE CÁMARA Y RECALCULAR (LADO DERECHO) ──────
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Centrar en mi ubicación
                FloatingActionButton(
                    onClick = {
                        currentLocation?.let { loc ->
                            mapViewInstance?.controller?.animateTo(GeoPoint(loc.latitude, loc.longitude))
                            mapViewInstance?.controller?.setZoom(17.5)
                        }
                    },
                    modifier = Modifier.size(46.dp),
                    shape = CircleShape,
                    containerColor = Color(0xFF1E293B),
                    contentColor = Color(0xFF00D2FF),
                    elevation = FloatingActionButtonDefaults.elevation(6.dp)
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "Mi ubicación", modifier = Modifier.size(22.dp))
                }

                // Ver ruta completa (BoundingBox)
                FloatingActionButton(
                    onClick = { zoomToFitRoute() },
                    modifier = Modifier.size(46.dp),
                    shape = CircleShape,
                    containerColor = Color(0xFF1E293B),
                    contentColor = Color(0xFFF9D423),
                    elevation = FloatingActionButtonDefaults.elevation(6.dp)
                ) {
                    Icon(Icons.Default.ZoomOutMap, contentDescription = "Ver ruta completa", modifier = Modifier.size(22.dp))
                }

                // Recalcular ruta manualmente
                FloatingActionButton(
                    onClick = { calculateAndDrawRoute(force = true) },
                    modifier = Modifier.size(46.dp),
                    shape = CircleShape,
                    containerColor = Color(0xFF1E293B),
                    contentColor = Color.White,
                    elevation = FloatingActionButtonDefaults.elevation(6.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Recalcular ruta", modifier = Modifier.size(22.dp))
                }
            }

            // ── 4. TARJETA INFERIOR DE VIAJE EN CURSO & FINALIZACIÓN ──────────────
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xF20F172A)),
                shape = RoundedCornerShape(20.dp),
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
                            Text("🛺", fontSize = 22.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "EN RUTA AL PASAJERO",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF4ADE80)
                                )
                                Text(
                                    text = trip.message.ifBlank { "Recogida de pasajero" },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Distancia recorrida por el tuctuc en vivo
                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "${String.format(Locale.US, "%.2f", liveTripDistanceMeters / 1000.0)} km",
                                color = Color(0xFFF9D423),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Botón gigante para Terminar Viaje y Cobrar
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF9D423),
                            contentColor = Color(0xFF0F2027)
                        )
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Llegué / Finalizar Viaje",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
                    }

                    // ── BOTONES DE NAVEGACIÓN EXTERNA (GOOGLE MAPS Y WAZE) ───────────
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Botón Google Maps
                        Button(
                            onClick = {
                                ExternalNavigationUtils.openGoogleMaps(
                                    context = context,
                                    lat = trip.passengerLat,
                                    lon = trip.passengerLon
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1A73E8),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Navigation,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Google Maps",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                        }

                        // Botón Waze
                        Button(
                            onClick = {
                                ExternalNavigationUtils.openWaze(
                                    context = context,
                                    lat = trip.passengerLat,
                                    lon = trip.passengerLon
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00D2FF),
                                contentColor = Color(0xFF0F172A)
                            ),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NearMe,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFF0F172A)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Waze",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }

    // ── 5. DIÁLOGO DE FINALIZACIÓN Y COBRO (Cumple regla Compose con .imePadding() y .verticalScroll()) ──
    if (showFinishTripDialog) {
        val currentDistKm = LocationTrackingService.getCurrentTripDistanceKm()
        val estimatedDistanceKm = remember(trip, currentDistKm) {
            if (currentDistKm > 0.0) {
                currentDistKm
            } else if (currentLocation != null && trip.passengerLat != 0.0) {
                val results = FloatArray(1)
                android.location.Location.distanceBetween(
                    currentLocation!!.latitude, currentLocation!!.longitude,
                    trip.passengerLat, trip.passengerLon,
                    results
                )
                Math.round((results[0] / 1000.0) * 100.0) / 100.0
            } else {
                0.0
            }
        }

        AlertDialog(
            onDismissRequest = { if (!isSubmittingFinish) showFinishTripDialog = false },
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
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0x33F9D423)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Pasajero: ${trip.passengerName.ifBlank { "Pasajero" }}",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                            if (trip.message.isNotBlank()) {
                                Text(
                                    text = "Rumbo: \"${trip.message}\"",
                                    color = Color(0xFFF9D423),
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Distancia calculada: ${String.format(Locale.US, "%.2f", estimatedDistanceKm)} km",
                                color = Color(0xFF4ADE80),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Monto a cobrar (Q):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = fareInputText,
                        onValueChange = { fareInputText = it },
                        placeholder = { Text("Ej: 10 o 15", color = Color(0xFF64748B)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFF9D423),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Chips de tarifas sugeridas
                    if (suggestedFares.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Montos frecuentes:",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            suggestedFares.take(4).forEach { fareVal ->
                                val textVal = if (fareVal % 1.0 == 0.0) fareVal.toInt().toString() else fareVal.toString()
                                Surface(
                                    onClick = { fareInputText = textVal },
                                    color = if (fareInputText == textVal) Color(0xFFF9D423) else Color(0xFF1E293B),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color(0x33FFFFFF))
                                ) {
                                    Text(
                                        text = "Q$textVal",
                                        color = if (fareInputText == textVal) Color(0xFF0F2027) else Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val fareDouble = fareInputText.toDoubleOrNull() ?: 0.0
                        if (fareDouble <= 0.0) {
                            Toast.makeText(context, "Ingresa un monto válido para finalizar", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        isSubmittingFinish = true
                        DriverFarePreferences.saveFare(context, fareDouble)

                        coroutineScope.launch {
                            val (trackedDistanceKm, recordedPoints) = LocationTrackingService.finishTripTracking()
                            val finalDistance = if (trackedDistanceKm > 0.0) trackedDistanceKm else estimatedDistanceKm

                            repository.updateTripStatus(trip.tripId, "completed")
                            val saveResult = repository.saveTripRecord(
                                tripInfo = trip.copy(driverId = uid),
                                status = "completed",
                                driverIdOverride = uid,
                                fare = fareDouble,
                                distanceKm = finalDistance,
                                routePoints = recordedPoints
                            )

                            repository.deleteTrip(trip.tripId)
                            LocationTrackingService.driverStatus.value = "disponible"
                            repository.updateDriverStatus(uid, "disponible")

                            // Limpiar estado de viaje activo compartido y caché local persistente
                            LocationTrackingService.activeAcceptedTrip.value = null
                            context.getSharedPreferences("driver_active_trip_prefs", Context.MODE_PRIVATE).edit().clear().apply()

                            isSubmittingFinish = false
                            showFinishTripDialog = false
                            Toast.makeText(context, "¡Viaje finalizado y registrado con éxito!", Toast.LENGTH_LONG).show()
                            onTripCompleted()
                        }
                    },
                    enabled = !isSubmittingFinish,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E))
                ) {
                    if (isSubmittingFinish) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                    } else {
                        Text("Registrar y Cobrar", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showFinishTripDialog = false },
                    enabled = !isSubmittingFinish
                ) {
                    Text("Cancelar", color = Color(0xFF94A3B8))
                }
            }
        )
    }
}
