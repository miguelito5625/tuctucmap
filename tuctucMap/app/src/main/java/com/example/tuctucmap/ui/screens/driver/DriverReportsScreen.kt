package com.example.tuctucmap.ui.screens.driver

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tuctucmap.data.model.parseIsoTimestamp
import com.example.tuctucmap.data.repository.SupabaseRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Pantalla de Reportes y Ganancias para Conductores.
 *
 * Estrategia de rendimiento y datos:
 * - Descarga por defecto el paquete de viajes de 1 semana (7 días) para el conductor autenticado.
 * - Cada conductor únicamente visualiza sus propios registros (aislamiento por driver_id).
 * - El filtrado entre hoy (por defecto), 2 a 7 días se realiza 100% en local en memoria,
 *   evitando peticiones innecesarias a la base de datos y brindando respuesta instantánea.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverReportsScreen(
    repository: SupabaseRepository,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var allWeeklyTrips by remember { mutableStateOf<List<SupabaseRepository.CompletedTripResponseDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Filtro seleccionado en días (1..7). Por defecto: 1 día (Hoy)
    var selectedDaysFilter by remember { mutableIntStateOf(1) }
    // Tipo de filtro rápido: "today", "yesterday", "3days", "7days"
    var quickFilterType by remember { mutableStateOf("today") }

    fun loadWeeklyData() {
        coroutineScope.launch {
            isLoading = true
            repository.getDriverWeeklyReportData().onSuccess { tripsList ->
                allWeeklyTrips = tripsList.sortedByDescending { parseIsoTimestamp(it.created_at) }
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadWeeklyData()
    }

    val spanishLocale = remember { Locale.forLanguageTag("es") }

    // Cálculo de inicio del día de hoy en la zona horaria del dispositivo
    val (startOfTodayMs, startOfYesterdayMs) = remember {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val today = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterday = cal.timeInMillis
        Pair(today, yesterday)
    }

    // Filtrado local en memoria
    val filteredTrips = remember(allWeeklyTrips, selectedDaysFilter, quickFilterType) {
        when (quickFilterType) {
            "yesterday" -> {
                // Solo viajes ocurridos ayer (desde 00:00 de ayer hasta 00:00 de hoy)
                allWeeklyTrips.filter { trip ->
                    val epoch = parseIsoTimestamp(trip.created_at)
                    epoch in startOfYesterdayMs until startOfTodayMs
                }
            }
            else -> {
                // Rango de últimos X días contando desde el inicio del día actual
                val cutOffMs = if (selectedDaysFilter == 1) {
                    startOfTodayMs
                } else {
                    startOfTodayMs - ((selectedDaysFilter - 1) * 24L * 60L * 60L * 1000L)
                }
                allWeeklyTrips.filter { trip ->
                    val epoch = parseIsoTimestamp(trip.created_at)
                    epoch >= cutOffMs
                }
            }
        }
    }

    // Métricas del período filtrado
    val totalEarnings = remember(filteredTrips) {
        filteredTrips.sumOf { it.fare ?: 0.0 }
    }
    val tripsCount = remember(filteredTrips) {
        filteredTrips.size
    }
    val avgEarningsPerTrip = remember(totalEarnings, tripsCount) {
        if (tripsCount > 0) totalEarnings / tripsCount else 0.0
    }
    val totalDistanceKm = remember(filteredTrips) {
        filteredTrips.sumOf { it.distance_km ?: 0.0 }
    }

    // Agrupación por días para el desglose
    val dayFormat = remember { SimpleDateFormat("EEEE d 'de' MMMM", Locale.forLanguageTag("es-GT")) }
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

    data class DailyReportGroup(
        val dateLabel: String,
        val epochDay: Long,
        val totalFare: Double,
        val trips: List<SupabaseRepository.CompletedTripResponseDto>
    )

    val dailyGroups = remember(filteredTrips) {
        val groups = mutableMapOf<String, MutableList<SupabaseRepository.CompletedTripResponseDto>>()
        val dayEpochs = mutableMapOf<String, Long>()

        val cal = Calendar.getInstance()
        filteredTrips.forEach { trip ->
            val epoch = parseIsoTimestamp(trip.created_at)
            cal.timeInMillis = epoch
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val dayStart = cal.timeInMillis

            val label = when {
                dayStart == startOfTodayMs -> "Hoy"
                dayStart == startOfYesterdayMs -> "Ayer"
                else -> dayFormat.format(Date(epoch)).replaceFirstChar { it.uppercase() }
            }
            groups.getOrPut(label) { mutableListOf() }.add(trip)
            dayEpochs[label] = dayStart
        }

        groups.map { (label, list) ->
            DailyReportGroup(
                dateLabel = label,
                epochDay = dayEpochs[label] ?: 0L,
                totalFare = list.sumOf { it.fare ?: 0.0 },
                trips = list
            )
        }.sortedByDescending { it.epochDay }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Reportes y Ganancias",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Resumen de ingresos y viajes de tu unidad",
                            color = Color(0xFF94A3B8),
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
                    IconButton(onClick = { loadWeeklyData() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Recargar datos de la semana",
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
                            Color(0xFF1B323D),
                            Color(0xFF2C5364)
                        )
                    )
                )
                .padding(innerPadding)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFFF9D423))
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Calculando métricas de la semana...",
                            color = Color(0xFFCBD5E1),
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // ── 1. SELECTOR DE FILTRO RÁPIDO (En Memoria Local) ──
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Filtrar período:",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0x33F9D423)
                                ) {
                                    Text(
                                        text = "Datos de 1 semana en local",
                                        color = Color(0xFFF9D423),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // Fila de chips de filtro principal
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                item {
                                    FilterChip(
                                        selected = quickFilterType == "today",
                                        onClick = {
                                            quickFilterType = "today"
                                            selectedDaysFilter = 1
                                        },
                                        label = { Text("Hoy") },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Today,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFFF9D423),
                                            selectedLabelColor = Color(0xFF0F2027),
                                            selectedLeadingIconColor = Color(0xFF0F2027),
                                            containerColor = Color(0x22FFFFFF),
                                            labelColor = Color.White
                                        )
                                    )
                                }

                                item {
                                    FilterChip(
                                        selected = quickFilterType == "yesterday",
                                        onClick = {
                                            quickFilterType = "yesterday"
                                        },
                                        label = { Text("Ayer") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFFF9D423),
                                            selectedLabelColor = Color(0xFF0F2027),
                                            containerColor = Color(0x22FFFFFF),
                                            labelColor = Color.White
                                        )
                                    )
                                }

                                item {
                                    FilterChip(
                                        selected = quickFilterType == "3days",
                                        onClick = {
                                            quickFilterType = "3days"
                                            selectedDaysFilter = 3
                                        },
                                        label = { Text("Últimos 3 días") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFFF9D423),
                                            selectedLabelColor = Color(0xFF0F2027),
                                            containerColor = Color(0x22FFFFFF),
                                            labelColor = Color.White
                                        )
                                    )
                                }

                                item {
                                    FilterChip(
                                        selected = quickFilterType == "7days",
                                        onClick = {
                                            quickFilterType = "7days"
                                            selectedDaysFilter = 7
                                        },
                                        label = { Text("Semana completa (7 días)") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFFF9D423),
                                            selectedLabelColor = Color(0xFF0F2027),
                                            containerColor = Color(0x22FFFFFF),
                                            labelColor = Color.White
                                        )
                                    )
                                }
                            }

                            // Subselector de días individuales (1 a 7 días)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Días:",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                (1..7).forEach { dayNum ->
                                    val isSelected = quickFilterType != "yesterday" && selectedDaysFilter == dayNum
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) Color(0xFFF9D423) else Color(0x22FFFFFF)
                                            )
                                            .clickable {
                                                selectedDaysFilter = dayNum
                                                quickFilterType = when (dayNum) {
                                                    1 -> "today"
                                                    3 -> "3days"
                                                    7 -> "7days"
                                                    else -> "custom_$dayNum"
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "$dayNum",
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
                                            color = if (isSelected) Color(0xFF0F2027) else Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── 2. HERO CARD: RESUMEN DE GANANCIAS Y VIAJES ──
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF152534)),
                            border = BorderStroke(1.dp, Color(0x33F9D423))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "GANANCIAS DEL PERÍODO",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF94A3B8),
                                            letterSpacing = 1.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Q ${"%.2f".format(Locale.US, totalEarnings)}",
                                            fontSize = 32.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF4ADE80)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(CircleShape)
                                            .background(Color(0x224ADE80)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Payments,
                                            contentDescription = null,
                                            tint = Color(0xFF4ADE80),
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(18.dp))
                                HorizontalDivider(color = Color(0x22FFFFFF), thickness = 1.dp)
                                Spacer(modifier = Modifier.height(14.dp))

                                // Grid de 3 sub-métricas
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Total Viajes
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.DirectionsCar,
                                                contentDescription = null,
                                                tint = Color(0xFFF9D423),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Viajes",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 11.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "$tripsCount",
                                            color = Color.White,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // Promedio / Viaje
                                    Column(modifier = Modifier.weight(1.2f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.TrendingUp,
                                                contentDescription = null,
                                                tint = Color(0xFF38BDF8),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Prom. / Viaje",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 11.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Q ${"%.2f".format(Locale.US, avgEarningsPerTrip)}",
                                            color = Color.White,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // Distancia total
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Route,
                                                contentDescription = null,
                                                tint = Color(0xFFA78BFA),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Recorrido",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 11.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${"%.1f".format(Locale.US, totalDistanceKm)} km",
                                            color = Color.White,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── 3. DESGLOSE DIARIO (SI HAY MÁS DE 1 DÍA) ──
                    if (dailyGroups.isNotEmpty() && (selectedDaysFilter > 1 || quickFilterType == "7days" || quickFilterType == "3days")) {
                        item {
                            Text(
                                text = "Desglose por Día",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        items(dailyGroups) { group ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFF152534),
                                border = BorderStroke(1.dp, Color(0x22FFFFFF)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = group.dateLabel,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${group.trips.size} viajes completados",
                                            fontSize = 12.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }

                                    Text(
                                        text = "Q ${"%.2f".format(Locale.US, group.totalFare)}",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 16.sp,
                                        color = Color(0xFF4ADE80)
                                    )
                                }
                            }
                        }
                    }

                    // ── 4. DETALLE DE VIAJES DEL PERÍODO ──
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Detalle de Viajes (${filteredTrips.size})",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            if (filteredTrips.isNotEmpty()) {
                                Text(
                                    text = "Cobros registrados",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                    }

                    if (filteredTrips.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0x22FFFFFF))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.MonetizationOn,
                                        contentDescription = null,
                                        tint = Color(0xFF64748B),
                                        modifier = Modifier.size(42.dp)
                                    )
                                    Text(
                                        text = "Sin viajes ni cobros en este período",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = "Los cobros de los viajes finalizados aparecerán aquí con sus ganancias.",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        items(filteredTrips) { trip ->
                            val epoch = parseIsoTimestamp(trip.created_at)
                            val timeStr = if (epoch > 0L) timeFormat.format(Date(epoch)) else "--:--"
                            val dateStr = if (epoch > 0L) SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(epoch)) else ""

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFF152534),
                                border = BorderStroke(1.dp, Color(0x22FFFFFF)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color(0x22F9D423)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = Color(0xFFF9D423),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Column {
                                            Text(
                                                text = trip.passenger_name?.ifBlank { "Pasajero" } ?: "Pasajero",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "$dateStr • $timeStr",
                                                    color = Color(0xFF94A3B8),
                                                    fontSize = 11.sp
                                                )
                                                if (trip.distance_km != null && trip.distance_km > 0.0) {
                                                    Text(
                                                        text = " • ${"%.1f".format(Locale.US, trip.distance_km)} km",
                                                        color = Color(0xFF38BDF8),
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }
                                            if (!trip.message.isNullOrBlank() && trip.message != "Viaje realizado") {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = trip.message,
                                                    color = Color(0xFFCFD8DC),
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }

                                    // Tarifa cobrada
                                    val fareValue = trip.fare
                                    if (fareValue != null && fareValue > 0.0) {
                                        Text(
                                            text = "+Q ${"%.2f".format(Locale.US, fareValue)}",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 16.sp,
                                            color = Color(0xFF4ADE80)
                                        )
                                    } else {
                                        Text(
                                            text = "Sin cobro",
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 12.sp,
                                            color = Color(0xFF94A3B8)
                                        )
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
