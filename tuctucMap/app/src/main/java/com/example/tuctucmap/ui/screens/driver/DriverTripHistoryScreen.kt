package com.example.tuctucmap.ui.screens.driver

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tuctucmap.data.repository.SupabaseRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverTripHistoryScreen(
    repository: SupabaseRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var trips by remember { mutableStateOf<List<SupabaseRepository.CompletedTripResponseDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedFilter by remember { mutableStateOf("all") } // "all", "accepted", "rejected"
    var selectedTrip by remember { mutableStateOf<SupabaseRepository.CompletedTripResponseDto?>(null) }

    fun loadTrips() {
        coroutineScope.launch {
            isLoading = true
            repository.getDriverTripHistory().onSuccess {
                trips = it.sortedByDescending { trip -> trip.created_at }
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadTrips()
    }

    val dateFormat = remember {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }
    }

    fun parseDate(dateStr: String): String {
        return try {
            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val date = isoFormat.parse(dateStr)
            if (date != null) dateFormat.format(date) else dateStr
        } catch (e: Exception) {
            dateStr
        }
    }

    val acceptedCount = remember(trips) {
        trips.count { it.status == "accepted" || it.status == "completed" }
    }
    val rejectedCount = remember(trips) {
        trips.count { it.status == "rejected" || it.status == "cancelled" }
    }

    val filteredTrips = remember(trips, selectedFilter) {
        when (selectedFilter) {
            "accepted" -> trips.filter { it.status == "accepted" || it.status == "completed" }
            "rejected" -> trips.filter { it.status == "rejected" || it.status == "cancelled" }
            else -> trips
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Historial de Viajes",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tus viajes aceptados y rechazados",
                            color = Color.LightGray,
                            fontSize = 12.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { loadTrips() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Recargar",
                            tint = Color(0xFFF9D423)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F2027))
            )
        }
    ) { innerPadding ->
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
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ── Filtros Rápidos ──────────────────────────────────────────
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedFilter == "all",
                            onClick = { selectedFilter = "all" },
                            label = { Text("Todos (${trips.size})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFF9D423),
                                selectedLabelColor = Color(0xFF0F2027),
                                containerColor = Color(0x33FFFFFF),
                                labelColor = Color.White
                            )
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedFilter == "accepted",
                            onClick = { selectedFilter = "accepted" },
                            label = { Text("Aceptados ($acceptedCount)") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (selectedFilter == "accepted") Color(0xFF0F2027) else Color(0xFF4CAF50)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF4CAF50),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0x33FFFFFF),
                                labelColor = Color.White
                            )
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedFilter == "rejected",
                            onClick = { selectedFilter = "rejected" },
                            label = { Text("Rechazados ($rejectedCount)") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Cancel,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (selectedFilter == "rejected") Color.White else Color(0xFFFF5252)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFF5252),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0x33FFFFFF),
                                labelColor = Color.White
                            )
                        )
                    }
                }

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFFF9D423))
                    }
                } else if (trips.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = Color.LightGray,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No tienes viajes en tu historial",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Los viajes que aceptes o rechaces quedarán registrados aquí para que puedas consultar la ruta en Google Maps en cualquier momento.",
                                color = Color.LightGray,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                } else if (filteredTrips.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No hay viajes en esta categoría",
                            color = Color.LightGray,
                            fontSize = 16.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredTrips, key = { it.id.ifBlank { it.created_at } }) { trip ->
                            DriverTripCard(
                                trip = trip,
                                formattedDate = parseDate(trip.created_at),
                                onOpenMaps = {
                                    openGoogleMaps(context, trip.passenger_lat, trip.passenger_lon)
                                },
                                onWhatsApp = {
                                    openWhatsApp(context, trip.passenger_phone)
                                },
                                onClick = {
                                    selectedTrip = trip
                                }
                            )
                        }
                    }
                }
            }

            // ── Diálogo de Opciones / Detalles ────────────────────────────────
            if (selectedTrip != null) {
                val trip = selectedTrip!!
                val isAccepted = trip.status == "accepted" || trip.status == "completed"
                AlertDialog(
                    onDismissRequest = { selectedTrip = null },
                    containerColor = Color(0xFF1E293B),
                    titleContentColor = Color.White,
                    textContentColor = Color.LightGray,
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = Color(0xFFF9D423),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(trip.passenger_name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    },
                    text = {
                        Column {
                            // Estado
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Estado: ", color = Color.LightGray, fontSize = 14.sp)
                                Text(
                                    text = when (trip.status) {
                                        "completed" -> "Completado"
                                        "accepted" -> "Aceptado"
                                        "rejected" -> "Rechazado"
                                        "cancelled" -> "Cancelado"
                                        else -> trip.status
                                    },
                                    color = if (isAccepted) Color(0xFF4CAF50) else Color(0xFFFF5252),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Mensaje: ${trip.message}", color = Color.White, fontSize = 14.sp)

                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Fecha: ${parseDate(trip.created_at)}", color = Color.LightGray, fontSize = 13.sp)

                            if (trip.passenger_phone.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Teléfono: ${trip.passenger_phone}", color = Color.LightGray, fontSize = 13.sp)
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Botón Google Maps
                            Button(
                                onClick = {
                                    openGoogleMaps(context, trip.passenger_lat, trip.passenger_lon)
                                    selectedTrip = null
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Navigation, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Trazar Ruta (Google Maps)", color = Color.White, fontWeight = FontWeight.Bold)
                            }

                            // Botón WhatsApp
                            if (trip.passenger_phone.isNotBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        openWhatsApp(context, trip.passenger_phone)
                                        selectedTrip = null
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Phone, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Contactar por WhatsApp", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { selectedTrip = null }) {
                            Text("Cerrar", color = Color(0xFFF9D423))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun DriverTripCard(
    trip: SupabaseRepository.CompletedTripResponseDto,
    formattedDate: String,
    onOpenMaps: () -> Unit,
    onWhatsApp: () -> Unit,
    onClick: () -> Unit
) {
    val isAccepted = trip.status == "accepted" || trip.status == "completed"
    val isRejected = trip.status == "rejected" || trip.status == "cancelled"

    val statusLabel = when (trip.status) {
        "completed" -> "Completado"
        "accepted" -> "Aceptado"
        "rejected" -> "Rechazado"
        "cancelled" -> "Cancelado"
        else -> trip.status
    }

    val statusBg = when {
        isAccepted -> Color(0x284CAF50)
        isRejected -> Color(0x28FF5252)
        else -> Color(0x28F9D423)
    }

    val statusBorder = when {
        isAccepted -> Color(0xFF4CAF50)
        isRejected -> Color(0xFFFF5252)
        else -> Color(0xFFF9D423)
    }

    val statusTextColor = when {
        isAccepted -> Color(0xFF81C784)
        isRejected -> Color(0xFFFF8A80)
        else -> Color(0xFFF9D423)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        border = BorderStroke(1.dp, Color(0x25FFFFFF)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Fila superior: Pasajero y Badge de estado
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
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0x33F9D423)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color(0xFFF9D423),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = trip.passenger_name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Badge de estado
                Surface(
                    color = statusBg,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, statusBorder),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isAccepted) Icons.Default.CheckCircle else Icons.Default.Cancel,
                            contentDescription = null,
                            tint = statusTextColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = statusLabel,
                            color = statusTextColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Mensaje del viaje
            Text(
                text = "Mensaje: \"${trip.message}\"",
                color = Color(0xFFECEFF1),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Fecha y hora
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = null,
                    tint = Color.LightGray,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = formattedDate,
                    color = Color.LightGray,
                    fontSize = 12.sp
                )

                if (trip.passenger_lat != 0.0 && trip.passenger_lon != 0.0) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFF4FC3F7),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "Ruta guardada",
                        color = Color(0xFF4FC3F7),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── Botones de acción directos ─────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Botón principal: Abrir Ruta en Google Maps
                Button(
                    onClick = onOpenMaps,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAccepted) Color(0xFF1976D2) else Color(0xFF334155),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Abrir en Maps",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Botón secundario: WhatsApp si hay teléfono
                if (trip.passenger_phone.isNotBlank()) {
                    IconButton(
                        onClick = onWhatsApp,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF25D366))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "WhatsApp",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

// ── Funciones utilitarias para navegación y WhatsApp ─────────────────────────

private fun openGoogleMaps(context: Context, lat: Double, lon: Double) {
    if (lat == 0.0 && lon == 0.0) {
        Toast.makeText(context, "No hay coordenadas disponibles para este viaje", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val uri = Uri.parse("google.navigation:q=$lat,$lon")
        val mapIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
        }
        if (mapIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(mapIntent)
        } else {
            val fallbackUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lon")
            context.startActivity(Intent(Intent.ACTION_VIEW, fallbackUri))
        }
    } catch (e: Exception) {
        try {
            val fallbackUri = Uri.parse("geo:$lat,$lon?q=$lat,$lon")
            context.startActivity(Intent(Intent.ACTION_VIEW, fallbackUri))
        } catch (e2: Exception) {
            Toast.makeText(context, "No se pudo abrir Google Maps ni el navegador", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun openWhatsApp(context: Context, phone: String) {
    if (phone.isBlank()) {
        Toast.makeText(context, "El pasajero no cuenta con número telefónico registrado", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val cleanPhone = phone.replace("+", "").replace(" ", "").replace("-", "")
        val url = "https://api.whatsapp.com/send?phone=$cleanPhone"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "No se pudo abrir WhatsApp", Toast.LENGTH_SHORT).show()
    }
}
