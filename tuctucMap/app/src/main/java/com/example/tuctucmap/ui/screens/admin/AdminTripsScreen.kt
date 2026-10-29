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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripRouteModal(
    trip: AdminTrip,
    adminRepo: AdminRepository,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var routePoints by remember { mutableStateOf<List<com.example.tuctucmap.data.repository.SupabaseRepository.RoutePoint>>(emptyList()) }
    var isLoadingRoute by remember { mutableStateOf(true) }

    LaunchedEffect(trip.id) {
        isLoadingRoute = true
        val res = adminRepo.getTripRoutePoints(trip.id)
        if (res.isSuccess) {
            routePoints = res.getOrDefault(emptyList())
        }
        isLoadingRoute = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E293B)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Trayectoria GPS del Viaje",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "${trip.resolvedPassengerName} ➔ ${trip.resolvedDriverName}",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                }
            }

            if (isLoadingRoute) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF38BDF8))
                }
            } else if (routePoints.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Este viaje no tiene puntos GPS de ruta registrados",
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

                            val geoPoints = routePoints.map { GeoPoint(it.lat, it.lon) }
                            val polyline = Polyline().apply {
                                outlinePaint.color = android.graphics.Color.parseColor("#38BDF8")
                                outlinePaint.strokeWidth = 10f
                                setPoints(geoPoints)
                            }
                            overlays.add(polyline)

                            // Marcador Inicio
                            val startPt = geoPoints.first()
                            val startMarker = Marker(this).apply {
                                position = startPt
                                title = "Inicio del Viaje"
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            }
                            overlays.add(startMarker)

                            // Marcador Fin
                            val endPt = geoPoints.last()
                            val endMarker = Marker(this).apply {
                                position = endPt
                                title = "Fin del Viaje"
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            }
                            overlays.add(endMarker)

                            controller.setZoom(15.5)
                            controller.setCenter(startPt)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "${routePoints.size} Puntos GPS registrados", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    val fare = trip.fare?.let { "Q ${String.format(Locale.US, "%.0f", it)}" } ?: ""
                    Text(text = "Tarifa: $fare", color = Color(0xFFF59E0B), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
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

