package com.example.tuctucmap.ui.screens.admin

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.example.tuctucmap.data.model.AdminTrip
import com.example.tuctucmap.data.repository.AdminRepository
import com.example.tuctucmap.data.repository.SupabaseRepository
import com.example.tuctucmap.ui.util.AppTileSource
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminTripsScreen(
    adminRepo: AdminRepository,
    supabaseRepo: SupabaseRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var trips by remember { mutableStateOf<List<AdminTrip>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTripForRoute by remember { mutableStateOf<AdminTrip?>(null) }

    fun loadTrips() {
        scope.launch {
            isLoading = true
            errorMessage = null
            val res = adminRepo.getCompletedTrips(limit = 120)
            if (res.isSuccess) {
                trips = res.getOrDefault(emptyList())
            } else {
                val err = res.exceptionOrNull()?.message ?: "Error al consultar los viajes"
                errorMessage = err
                android.util.Log.e("AdminTripsScreen", "Error loading trips: $err", res.exceptionOrNull())
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadTrips()
    }

    val filteredTrips = remember(trips, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) trips
        else {
            trips.filter { t ->
                t.resolvedPassengerName.lowercase().contains(q) ||
                        t.resolvedDriverName.lowercase().contains(q) ||
                        t.resolvedPassengerPhone.contains(q) ||
                        t.message.lowercase().contains(q)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Auditoría de Viajes", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        exportTripsCsv(context, trips)
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Exportar CSV", tint = Color(0xFFF59E0B))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E293B))
            )
        },
        containerColor = Color(0xFF0F172A)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Buscador
            Box(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar por pasajero, conductor o destino...", color = Color(0xFF94A3B8), fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFFF59E0B)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Limpiar", tint = Color(0xFF94A3B8))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF1E293B),
                        unfocusedContainerColor = Color(0xFF1E293B),
                        focusedBorderColor = Color(0xFFF59E0B),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFFF59E0B))
                }
            } else if (errorMessage != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Error al cargar la auditoría",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = errorMessage ?: "",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { loadTrips() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFF59E0B),
                                contentColor = Color(0xFF0F172A)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reintentar", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else if (filteredTrips.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No se encontraron registros de viajes", color = Color(0xFF94A3B8), fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredTrips, key = { it.id }) { trip ->
                        AdminTripCard(
                            trip = trip,
                            onViewRoute = {
                                selectedTripForRoute = trip
                            }
                        )
                    }
                }
            }
        }
    }

    // Modal de Visor de Ruta GPS
    selectedTripForRoute?.let { trip ->
        TripRouteModal(
            trip = trip,
            adminRepo = adminRepo,
            onDismiss = { selectedTripForRoute = null }
        )
    }
}

@Composable
private fun AdminTripCard(
    trip: AdminTrip,
    onViewRoute: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = trip.resolvedPassengerName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (trip.isRegisteredPassenger) Color(0x2210B981) else Color(0x2294A3B8)
                        ) {
                            Text(
                                text = if (trip.isRegisteredPassenger) "Registrado" else "Invitado",
                                color = if (trip.isRegisteredPassenger) Color(0xFF10B981) else Color(0xFF94A3B8),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (trip.resolvedPassengerPhone.isNotBlank()) {
                        Text(
                            text = "📞 ${trip.resolvedPassengerPhone}",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                // Tarifa Cobrada
                val fareText = if (trip.fare != null) "Q ${String.format(Locale.US, "%.0f", trip.fare)}" else "Q --"
                Text(
                    text = fareText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFF59E0B)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = Color(0xFF334155).copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            // Datos del Conductor y Destino
            Text(
                text = "🛺 Conductor: ${trip.resolvedDriverName}",
                fontSize = 13.sp,
                color = Color(0xFFCBD5E1),
                fontWeight = FontWeight.Medium
            )

            if (trip.message.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "📍 Destino: ${trip.message}",
                    fontSize = 12.sp,
                    color = Color(0xFF38BDF8),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val distText = if (trip.distanceKm != null) "${String.format(Locale.US, "%.1f", trip.distanceKm)} km" else "Distancia N/D"
                Text(
                    text = "$distText • ${formatTripDate(trip.createdAtEpoch)}",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )

                OutlinedButton(
                    onClick = onViewRoute,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Route, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ver Ruta GPS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun createCircleMarkerDrawable(context: Context, fillColor: Int, strokeColor: Int, sizeDp: Int = 16): Drawable {
    val density = context.resources.displayMetrics.density
    val sizePx = (sizeDp * density).toInt().coerceAtLeast(16)
    val strokePx = (2 * density).toInt().coerceAtLeast(2)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    paint.color = strokeColor
    paint.style = Paint.Style.FILL
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint)

    paint.color = fillColor
    paint.style = Paint.Style.FILL
    val innerRadius = (sizePx / 2f) - strokePx
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, innerRadius, paint)

    return BitmapDrawable(context.resources, bitmap)
}

@Composable
private fun TripRouteModal(
    trip: AdminTrip,
    adminRepo: AdminRepository,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var routePoints by remember { mutableStateOf<List<SupabaseRepository.RoutePoint>>(emptyList()) }
    var isLoadingRoute by remember { mutableStateOf(true) }

    LaunchedEffect(trip.id) {
        isLoadingRoute = true
        val res = adminRepo.getTripRoutePoints(trip.id)
        if (res.isSuccess) {
            routePoints = res.getOrDefault(emptyList())
        }
        isLoadingRoute = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.90f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            border = BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Cabecera: Icono de ruta, Título y Botón Cerrar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFFEF3C7).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Route,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Recorrido GPS del Conductor",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Trayectoria trazada desde aceptación hasta finalización del viaje",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Panel de Métricas / KPIs del Viaje (Replicando la barra de KPIs de Angular)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    border = BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        // Fila 1: Pasajero y Conductor
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Pasajero
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "PASAJERO",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF64748B)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = trip.resolvedPassengerName.ifBlank { "Pasajero" },
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                if (trip.isRegisteredPassenger) Color(0xFF064E3B)
                                                else Color(0xFF1E3A8A)
                                            )
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (trip.isRegisteredPassenger) "Cuenta Registrada" else "Sin cuenta (Invitado)",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (trip.isRegisteredPassenger) Color(0xFF34D399) else Color(0xFF93C5FD)
                                        )
                                    }
                                }
                                if (trip.resolvedPassengerPhone.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "📞 ${trip.resolvedPassengerPhone}",
                                        fontSize = 11.sp,
                                        color = Color(0xFF38BDF8)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Conductor
                            Column(modifier = Modifier.weight(0.9f)) {
                                Text(
                                    text = "CONDUCTOR",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF64748B)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = trip.resolvedDriverName.ifBlank { "TucTuc" },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = Color(0xFF334155), thickness = 0.8.dp)
                        Spacer(modifier = Modifier.height(8.dp))

                        // Fila 2: Tarifa Cobrada, Distancia Recorrida y Puntos Telemetría
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Tarifa Cobrada
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "TARIFA COBRADA",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF64748B)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                val fareStr = trip.fare?.let { "Q" + String.format(Locale.US, "%.2f", it) } ?: "No especificado"
                                Text(
                                    text = fareStr,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF9D423)
                                )
                            }

                            // Distancia Recorrida
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "DISTANCIA RECORRIDA",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF64748B)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                val distStr = trip.distanceKm?.let { String.format(Locale.US, "%.2f km", it) } ?: "No registrada"
                                Text(
                                    text = distStr,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF38BDF8)
                                )
                            }

                            // Puntos Telemetría
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "PUNTOS TELEMETRÍA",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF64748B)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${routePoints.size} waypoints",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF34D399)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Área del Mapa Interactivo OSMDroid
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E293B)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoadingRoute) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF38BDF8),
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Cargando telemetría del viaje...",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        val hasPoints = routePoints.isNotEmpty()
                        val hasPassengerLocation = trip.passengerLat != null && trip.passengerLon != null && trip.passengerLat != 0.0

                        if (!hasPoints && !hasPassengerLocation) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOff,
                                    contentDescription = null,
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Este viaje no tiene coordenadas GPS de ruta registradas",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 13.sp
                                )
                            }
                        } else {
                            AndroidView(
                                factory = { ctx ->
                                    MapView(ctx).apply {
                                        setTileSource(AppTileSource)
                                        setMultiTouchControls(true)
                                        isTilesScaledToDpi = true

                                        val allBoundsPoints = mutableListOf<GeoPoint>()

                                        // 1. Trazado de Polyline (Dorado TucTuc #F9D423)
                                        if (hasPoints) {
                                            val geoPoints = routePoints.map { GeoPoint(it.lat, it.lon) }
                                            allBoundsPoints.addAll(geoPoints)

                                            val polyline = Polyline().apply {
                                                outlinePaint.color = android.graphics.Color.parseColor("#F9D423")
                                                outlinePaint.strokeWidth = 10f
                                                setPoints(geoPoints)
                                            }
                                            overlays.add(polyline)

                                            // Marcador de Inicio (TucTuc al aceptar - Verde)
                                            val startPt = geoPoints.first()
                                            val startMarker = Marker(this).apply {
                                                position = startPt
                                                icon = createCircleMarkerDrawable(
                                                    ctx,
                                                    android.graphics.Color.parseColor("#22C55E"),
                                                    android.graphics.Color.WHITE,
                                                    16
                                                )
                                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                                title = "Punto de inicio del TucTuc"
                                                snippet = "Aceptación del viaje"
                                            }
                                            overlays.add(startMarker)
                                            startMarker.showInfoWindow()

                                            // Marcador de Fin (TucTuc al finalizar - Rojo)
                                            if (geoPoints.size > 1) {
                                                val endPt = geoPoints.last()
                                                val endFare = trip.fare?.let { "Q" + String.format(Locale.US, "%.2f", it) } ?: "-"
                                                val endMarker = Marker(this).apply {
                                                    position = endPt
                                                    icon = createCircleMarkerDrawable(
                                                        ctx,
                                                        android.graphics.Color.parseColor("#EF4444"),
                                                        android.graphics.Color.WHITE,
                                                        16
                                                    )
                                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                                    title = "Fin del viaje"
                                                    snippet = "Cobro: $endFare"
                                                }
                                                overlays.add(endMarker)
                                            }
                                        }

                                        // 2. Marcador de Recogida del Pasajero (Amarillo con borde oscuro)
                                        if (hasPassengerLocation) {
                                            val pPt = GeoPoint(trip.passengerLat!!, trip.passengerLon!!)
                                            allBoundsPoints.add(pPt)

                                            val passengerMarker = Marker(this).apply {
                                                position = pPt
                                                icon = createCircleMarkerDrawable(
                                                    ctx,
                                                    android.graphics.Color.parseColor("#F9D423"),
                                                    android.graphics.Color.parseColor("#0F172A"),
                                                    18
                                                )
                                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                                title = "Punto de recogida"
                                                snippet = trip.resolvedPassengerName.ifBlank { "Pasajero" }
                                            }
                                            overlays.add(passengerMarker)

                                            if (!hasPoints) {
                                                passengerMarker.showInfoWindow()
                                            }
                                        }

                                        // 3. Ajuste de zoom y encuadre (Auto-fit Bounds)
                                        if (allBoundsPoints.isNotEmpty()) {
                                            var minLat = allBoundsPoints.first().latitude
                                            var maxLat = allBoundsPoints.first().latitude
                                            var minLon = allBoundsPoints.first().longitude
                                            var maxLon = allBoundsPoints.first().longitude

                                            for (pt in allBoundsPoints) {
                                                if (pt.latitude < minLat) minLat = pt.latitude
                                                if (pt.latitude > maxLat) maxLat = pt.latitude
                                                if (pt.longitude < minLon) minLon = pt.longitude
                                                if (pt.longitude > maxLon) maxLon = pt.longitude
                                            }

                                            if (minLat == maxLat && minLon == maxLon) {
                                                controller.setZoom(15.5)
                                                controller.setCenter(GeoPoint(minLat, minLon))
                                            } else {
                                                val latMargin = (maxLat - minLat).coerceAtLeast(0.001) * 0.25
                                                val lonMargin = (maxLon - minLon).coerceAtLeast(0.001) * 0.25
                                                val boundingBox = org.osmdroid.util.BoundingBox(
                                                    maxLat + latMargin,
                                                    maxLon + lonMargin,
                                                    minLat - latMargin,
                                                    minLon - lonMargin
                                                )
                                                post {
                                                    zoomToBoundingBox(boundingBox, true, 80)
                                                }
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Barra de Leyenda de Marcadores (Identica al footer explicativo del dashboard)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LegendItem(color = Color(0xFF22C55E), text = "Inicio")
                    LegendItem(color = Color(0xFFEF4444), text = "Fin")
                    LegendItem(color = Color(0xFFF9D423), text = "Recogida")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .width(14.dp)
                                .height(3.dp)
                                .background(Color(0xFFF9D423), RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Ruta GPS", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = text, fontSize = 11.sp, color = Color(0xFF94A3B8))
    }
}

private fun formatTripDate(epochMs: Long): String {
    if (epochMs <= 0L) return "N/A"
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)
    return sdf.format(Date(epochMs))
}

private fun exportTripsCsv(context: Context, trips: List<AdminTrip>) {
    try {
        val sb = StringBuilder()
        sb.append("ID,Pasajero,Telefono Pasajero,Tipo,Conductor,Destino,Tarifa,Distancia Km,Fecha\n")
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        for (t in trips) {
            val dateStr = if (t.createdAtEpoch > 0) sdf.format(Date(t.createdAtEpoch)) else ""
            val tipo = if (t.isRegisteredPassenger) "Registrado" else "Invitado"
            val fare = t.fare ?: 0.0
            val dist = t.distanceKm ?: 0.0
            val cleanDest = t.message.replace("\"", "'")

            sb.append("\"${t.id}\",")
            sb.append("\"${t.resolvedPassengerName}\",")
            sb.append("\"${t.resolvedPassengerPhone}\",")
            sb.append("\"$tipo\",")
            sb.append("\"${t.resolvedDriverName}\",")
            sb.append("\"$cleanDest\",")
            sb.append("$fare,")
            sb.append("$dist,")
            sb.append("\"$dateStr\"\n")
        }

        val cacheDir = context.cacheDir
        val file = File(cacheDir, "tuctuc_viajes_${System.currentTimeMillis()}.csv")
        file.writeText(sb.toString())

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "Auditoría de Viajes TucTuc Map")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Compartir auditoría de viajes"))
    } catch (e: Exception) {
        Toast.makeText(context, "Error al generar CSV: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

