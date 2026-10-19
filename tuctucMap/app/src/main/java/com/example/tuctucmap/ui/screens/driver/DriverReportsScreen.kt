package com.example.tuctucmap.ui.screens.driver

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class DriverReportsPeriodFilter {
    TODAY,
    YESTERDAY,
    CUSTOM_RANGE
}

data class WeeklyDayStat(
    val dayKey: String,
    val dayLabel: String,
    val dayNumber: String,
    val fullDateLabel: String,
    val isToday: Boolean,
    val isYesterday: Boolean,
    val totalEarnings: Double,
    val tripsCount: Int
)

data class DailyReportGroup(
    val dateLabel: String,
    val epochDay: Long,
    val totalFare: Double,
    val trips: List<SupabaseRepository.CompletedTripResponseDto>
)

/**
 * Pantalla de Reportes y Ganancias para Conductores.
 *
 * Estrategia de rendimiento y datos:
 * - Descarga el paquete de viajes de 1 semana (7 días) para el conductor autenticado.
 * - Filtros rápidos: Hoy, Ayer y Rango de Fechas personalizado (máx. 7 días hacia atrás).
 * - Gráfica de barras semanal con animación escalonada dinámica, alternador de Ganancias y Viajes.
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

    // Filtros de período (Misma convención que Historial de Viajes)
    var currentPeriodFilter by remember { mutableStateOf(DriverReportsPeriodFilter.TODAY) }
    var customRangeStartUtcMillis by remember { mutableStateOf<Long?>(null) }
    var customRangeEndUtcMillis by remember { mutableStateOf<Long?>(null) }
    var showDatePickerDialog by remember { mutableStateOf(false) }

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

    // Fechas límites para selector de fechas (máximo 7 días hacia atrás hasta hoy)
    val todayLocal = remember { Calendar.getInstance() }
    val maxAllowedUtcMidnight = remember(todayLocal) {
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(todayLocal.get(Calendar.YEAR), todayLocal.get(Calendar.MONTH), todayLocal.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
        }.timeInMillis
    }
    val minAllowedUtcMidnight = remember(maxAllowedUtcMidnight) {
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = maxAllowedUtcMidnight
            add(Calendar.DAY_OF_YEAR, -7)
        }.timeInMillis
    }

    val selectableDates = remember(minAllowedUtcMidnight, maxAllowedUtcMidnight) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                return utcTimeMillis in minAllowedUtcMidnight..maxAllowedUtcMidnight
            }
            override fun isSelectableYear(year: Int): Boolean {
                val minYear = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                    timeInMillis = minAllowedUtcMidnight
                }.get(Calendar.YEAR)
                val maxYear = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                    timeInMillis = maxAllowedUtcMidnight
                }.get(Calendar.YEAR)
                return year in minYear..maxYear
            }
        }
    }

    // Cálculo estricto de inicio y fin del período en la zona horaria del dispositivo
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

    val (activeStartMs, activeEndMs) = remember(currentPeriodFilter, customRangeStartUtcMillis, customRangeEndUtcMillis) {
        val startCal = Calendar.getInstance()
        val endCal = Calendar.getInstance()

        when (currentPeriodFilter) {
            DriverReportsPeriodFilter.TODAY -> {
                startCal.apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                endCal.apply {
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
            }
            DriverReportsPeriodFilter.YESTERDAY -> {
                startCal.apply {
                    add(Calendar.DAY_OF_YEAR, -1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                endCal.apply {
                    add(Calendar.DAY_OF_YEAR, -1)
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
            }
            DriverReportsPeriodFilter.CUSTOM_RANGE -> {
                val startUtcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                    timeInMillis = customRangeStartUtcMillis ?: System.currentTimeMillis()
                }
                val endUtcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                    timeInMillis = customRangeEndUtcMillis ?: (customRangeStartUtcMillis ?: System.currentTimeMillis())
                }

                startCal.apply {
                    set(startUtcCal.get(Calendar.YEAR), startUtcCal.get(Calendar.MONTH), startUtcCal.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                endCal.apply {
                    set(endUtcCal.get(Calendar.YEAR), endUtcCal.get(Calendar.MONTH), endUtcCal.get(Calendar.DAY_OF_MONTH), 23, 59, 59)
                    set(Calendar.MILLISECOND, 999)
                }
            }
        }
        Pair(startCal.timeInMillis, endCal.timeInMillis)
    }

    val shortDateSdf = remember { SimpleDateFormat("dd/MM", Locale.getDefault()) }
    val fullDateSdf = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    val customRangeSummary = remember(customRangeStartUtcMillis, customRangeEndUtcMillis) {
        if (customRangeStartUtcMillis != null && customRangeEndUtcMillis != null) {
            val startUtcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = customRangeStartUtcMillis!! }
            val endUtcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = customRangeEndUtcMillis!! }
            val startLocal = Calendar.getInstance().apply {
                set(startUtcCal.get(Calendar.YEAR), startUtcCal.get(Calendar.MONTH), startUtcCal.get(Calendar.DAY_OF_MONTH))
            }
            val endLocal = Calendar.getInstance().apply {
                set(endUtcCal.get(Calendar.YEAR), endUtcCal.get(Calendar.MONTH), endUtcCal.get(Calendar.DAY_OF_MONTH))
            }
            if (customRangeStartUtcMillis == customRangeEndUtcMillis) {
                fullDateSdf.format(startLocal.time)
            } else {
                "${shortDateSdf.format(startLocal.time)} - ${shortDateSdf.format(endLocal.time)}"
            }
        } else {
            "Rango de fechas"
        }
    }

    // Filtrado local en memoria estricto por jornada
    val filteredTrips = remember(allWeeklyTrips, activeStartMs, activeEndMs) {
        allWeeklyTrips.filter { trip ->
            val epoch = parseIsoTimestamp(trip.created_at)
            epoch in activeStartMs..activeEndMs
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

    // Estadísticas completas de los 7 días de la semana para la gráfica de barras
    val weeklyDayStats = remember(allWeeklyTrips) {
        val stats = mutableListOf<WeeklyDayStat>()
        val dayNameFormat = SimpleDateFormat("EEE", Locale.forLanguageTag("es-GT"))
        val dayNumFormat = SimpleDateFormat("d", Locale.getDefault())
        val fullDateFormat = SimpleDateFormat("EEEE d 'de' MMMM", Locale.forLanguageTag("es-GT"))

        val todayCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayMidnightMs = todayCal.timeInMillis

        val yesterdayCal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val yesterdayMidnightMs = yesterdayCal.timeInMillis

        for (i in 6 downTo 0) {
            val startCal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -i)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startMs = startCal.timeInMillis
            val endMs = Calendar.getInstance().apply {
                timeInMillis = startMs
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }.timeInMillis

            val dayTrips = allWeeklyTrips.filter { trip ->
                val epoch = parseIsoTimestamp(trip.created_at)
                epoch in startMs..endMs
            }

            val earnings = dayTrips.sumOf { it.fare ?: 0.0 }
            val count = dayTrips.size
            val isToday = (startMs == todayMidnightMs)
            val isYesterday = (startMs == yesterdayMidnightMs)

            val rawDayName = dayNameFormat.format(startCal.time).replace(".", "").trim()
            val formattedDayName = rawDayName.replaceFirstChar { it.uppercase() }

            stats.add(
                WeeklyDayStat(
                    dayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(startCal.time),
                    dayLabel = if (isToday) "Hoy" else if (isYesterday) "Ayer" else formattedDayName,
                    dayNumber = dayNumFormat.format(startCal.time),
                    fullDateLabel = fullDateFormat.format(startCal.time).replaceFirstChar { it.uppercase() },
                    isToday = isToday,
                    isYesterday = isYesterday,
                    totalEarnings = earnings,
                    tripsCount = count
                )
            )
        }
        stats
    }

    // Agrupación por días para el desglose (cuando el rango personalizado tiene más de 1 día)
    val dayFormat = remember { SimpleDateFormat("EEEE d 'de' MMMM", Locale.forLanguageTag("es-GT")) }
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

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
                            text = when (currentPeriodFilter) {
                                DriverReportsPeriodFilter.TODAY -> "Reporte de hoy"
                                DriverReportsPeriodFilter.YESTERDAY -> "Reporte de ayer"
                                DriverReportsPeriodFilter.CUSTOM_RANGE -> "Rango: $customRangeSummary"
                            },
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
                    // ── 1. SELECTOR DE FILTRO DE PERÍODO (Hoy, Ayer, DatePicker) ──
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarToday,
                                        contentDescription = null,
                                        tint = Color(0xFFF9D423),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Periodo:",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0x33F9D423)
                                ) {
                                    Text(
                                        text = when (currentPeriodFilter) {
                                            DriverReportsPeriodFilter.TODAY -> "Solo Hoy"
                                            DriverReportsPeriodFilter.YESTERDAY -> "Solo Ayer"
                                            DriverReportsPeriodFilter.CUSTOM_RANGE -> customRangeSummary
                                        },
                                        color = Color(0xFFF9D423),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // Fila de 3 Chips: Hoy, Ayer, Rango de fechas
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Opción 1: Hoy
                                FilterChip(
                                    selected = currentPeriodFilter == DriverReportsPeriodFilter.TODAY,
                                    onClick = { currentPeriodFilter = DriverReportsPeriodFilter.TODAY },
                                    label = { Text("Hoy") },
                                    leadingIcon = if (currentPeriodFilter == DriverReportsPeriodFilter.TODAY) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Today,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFFF9D423),
                                        selectedLabelColor = Color(0xFF0F2027),
                                        selectedLeadingIconColor = Color(0xFF0F2027),
                                        containerColor = Color(0x22FFFFFF),
                                        labelColor = Color.White
                                    )
                                )

                                // Opción 2: Ayer
                                FilterChip(
                                    selected = currentPeriodFilter == DriverReportsPeriodFilter.YESTERDAY,
                                    onClick = { currentPeriodFilter = DriverReportsPeriodFilter.YESTERDAY },
                                    label = { Text("Ayer") },
                                    leadingIcon = if (currentPeriodFilter == DriverReportsPeriodFilter.YESTERDAY) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.History,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFFF9D423),
                                        selectedLabelColor = Color(0xFF0F2027),
                                        selectedLeadingIconColor = Color(0xFF0F2027),
                                        containerColor = Color(0x22FFFFFF),
                                        labelColor = Color.White
                                    )
                                )

                                // Opción 3: Rango de fechas (DatePicker modal)
                                FilterChip(
                                    selected = currentPeriodFilter == DriverReportsPeriodFilter.CUSTOM_RANGE,
                                    onClick = { showDatePickerDialog = true },
                                    label = {
                                        Text(
                                            if (currentPeriodFilter == DriverReportsPeriodFilter.CUSTOM_RANGE) {
                                                customRangeSummary
                                            } else {
                                                "Rango de fechas"
                                            }
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.DateRange,
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
                                            text = when (currentPeriodFilter) {
                                                DriverReportsPeriodFilter.TODAY -> "GANANCIAS DE HOY"
                                                DriverReportsPeriodFilter.YESTERDAY -> "GANANCIAS DE AYER"
                                                DriverReportsPeriodFilter.CUSTOM_RANGE -> "GANANCIAS DEL PERÍODO"
                                            },
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

                    // ── 3. DESGLOSE DIARIO (SI HAY MÁS DE 1 DÍA EN EL RANGO SELECCIONADO) ──
                    if (dailyGroups.size > 1 && currentPeriodFilter == DriverReportsPeriodFilter.CUSTOM_RANGE) {
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

                    // ── 4. GRÁFICA DE BARRAS DE LA SEMANA (GANANCIAS Y VIAJES) ──
                    item {
                        DriverWeeklyBarChart(weeklyStats = weeklyDayStats)
                    }

                    // ── 5. DETALLE DE VIAJES DEL PERÍODO ──
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

            // Modal DatePickerDialog con DateRangePicker (máx. 7 días hacia atrás)
            if (showDatePickerDialog) {
                val dateRangePickerState = rememberDateRangePickerState(
                    initialSelectedStartDateMillis = customRangeStartUtcMillis ?: maxAllowedUtcMidnight,
                    initialSelectedEndDateMillis = customRangeEndUtcMillis ?: maxAllowedUtcMidnight,
                    selectableDates = selectableDates
                )

                DatePickerDialog(
                    onDismissRequest = { showDatePickerDialog = false },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val start = dateRangePickerState.selectedStartDateMillis
                                val end = dateRangePickerState.selectedEndDateMillis ?: start
                                if (start != null && end != null) {
                                    customRangeStartUtcMillis = minOf(start, end)
                                    customRangeEndUtcMillis = maxOf(start, end)
                                    currentPeriodFilter = DriverReportsPeriodFilter.CUSTOM_RANGE
                                    showDatePickerDialog = false
                                }
                            },
                            enabled = dateRangePickerState.selectedStartDateMillis != null
                        ) {
                            Text("Aplicar", color = Color(0xFFF9D423), fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePickerDialog = false }) {
                            Text("Cancelar", color = Color.White)
                        }
                    },
                    colors = DatePickerDefaults.colors(
                        containerColor = Color(0xFF1E293B)
                    )
                ) {
                    DateRangePicker(
                        state = dateRangePickerState,
                        title = {
                            Text(
                                text = "Rango de fechas (máx. 7 días)",
                                modifier = Modifier.padding(start = 24.dp, top = 16.dp, end = 24.dp),
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        showModeToggle = false,
                        colors = DatePickerDefaults.colors(
                            containerColor = Color(0xFF1E293B),
                            titleContentColor = Color.White,
                            headlineContentColor = Color.White,
                            weekdayContentColor = Color(0xFF94A3B8),
                            subheadContentColor = Color(0xFFCBD5E1),
                            navigationContentColor = Color.White,
                            yearContentColor = Color.White,
                            currentYearContentColor = Color(0xFFF9D423),
                            selectedYearContentColor = Color(0xFF0F2027),
                            selectedYearContainerColor = Color(0xFFF9D423),
                            dayContentColor = Color.White,
                            disabledDayContentColor = Color(0xFF475569),
                            selectedDayContentColor = Color(0xFF0F2027),
                            selectedDayContainerColor = Color(0xFFF9D423),
                            dayInSelectionRangeContentColor = Color(0xFF0F2027),
                            dayInSelectionRangeContainerColor = Color(0x66F9D423)
                        ),
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
        }
    }
}

/**
 * Gráfica de Barras de la Semana para Conductores:
 * - Renderiza los últimos 7 días con animación escalonada barra por barra al iniciarse.
 * - Permite alternar entre métrica de "Q Ganancias" y "Viajes".
 * - Tarjeta interactiva para explorar métricas detalladas al tocar cualquier barra.
 */
@Composable
private fun DriverWeeklyBarChart(
    weeklyStats: List<WeeklyDayStat>,
    modifier: Modifier = Modifier
) {
    var selectedMetric by remember { mutableStateOf("earnings") } // "earnings" o "trips"
    var selectedDayIndex by remember(weeklyStats) {
        mutableIntStateOf(weeklyStats.indexOfFirst { it.isToday }.takeIf { it >= 0 } ?: (weeklyStats.size - 1).coerceAtLeast(0))
    }

    val totalWeeklyEarnings = remember(weeklyStats) { weeklyStats.sumOf { it.totalEarnings } }
    val totalWeeklyTrips = remember(weeklyStats) { weeklyStats.sumOf { it.tripsCount } }
    val maxEarnings = remember(weeklyStats) { (weeklyStats.maxOfOrNull { it.totalEarnings } ?: 1.0).coerceAtLeast(1.0) }
    val maxTrips = remember(weeklyStats) { (weeklyStats.maxOfOrNull { it.tripsCount } ?: 1).coerceAtLeast(1) }

    // Animación de aparición escalonada barra por barra al iniciarse
    val barAnimations = remember(weeklyStats) {
        List(weeklyStats.size) { Animatable(0f) }
    }

    LaunchedEffect(weeklyStats) {
        barAnimations.forEachIndexed { index, anim ->
            launch {
                delay(index * 85L) // Desfase escalonado entre barras sucesivas
                anim.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            }
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF152534)),
        border = BorderStroke(1.dp, Color(0x33F9D423))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Fila 1: Título y Selector de Métrica (Ganancias / Viajes)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0x22F9D423)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BarChart,
                            contentDescription = null,
                            tint = Color(0xFFF9D423),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Rendimiento Semanal",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Últimos 7 días",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                }

                // Alternador de Métrica (Ganancias / Viajes)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0F1E29),
                    border = BorderStroke(1.dp, Color(0x22FFFFFF))
                ) {
                    Row(modifier = Modifier.padding(3.dp)) {
                        Surface(
                            onClick = { selectedMetric = "earnings" },
                            shape = RoundedCornerShape(9.dp),
                            color = if (selectedMetric == "earnings") Color(0xFF4ADE80) else Color.Transparent
                        ) {
                            Text(
                                text = "Q Ganancias",
                                color = if (selectedMetric == "earnings") Color(0xFF0F2027) else Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Surface(
                            onClick = { selectedMetric = "trips" },
                            shape = RoundedCornerShape(9.dp),
                            color = if (selectedMetric == "trips") Color(0xFFF9D423) else Color.Transparent
                        ) {
                            Text(
                                text = "Viajes",
                                color = if (selectedMetric == "trips") Color(0xFF0F2027) else Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Fila 2: Resumen rápido de la semana
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x1F2C5364))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = null,
                        tint = Color(0xFF4ADE80),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Total semana:",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Q ${String.format(Locale.US, "%.2f", totalWeeklyEarnings)}",
                        color = Color(0xFF4ADE80),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "$totalWeeklyTrips viajes",
                        color = Color(0xFFF9D423),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Fila 3: Gráfica de Barras Dinámica (7 columnas)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                weeklyStats.forEachIndexed { index, stat ->
                    val isSelected = (index == selectedDayIndex)
                    val anim = barAnimations.getOrElse(index) { Animatable(1f) }
                    val animProgress = anim.value

                    val ratio = if (selectedMetric == "earnings") {
                        if (maxEarnings > 0) (stat.totalEarnings / maxEarnings).toFloat() else 0f
                    } else {
                        if (maxTrips > 0) (stat.tripsCount.toFloat() / maxTrips.toFloat()) else 0f
                    }

                    val maxHeightDp = 85.dp
                    val currentBarHeight = (maxHeightDp * ratio * animProgress).coerceAtLeast(
                        if (ratio > 0f) 6.dp * animProgress else 0.dp
                    )

                    val barGradient = if (selectedMetric == "earnings") {
                        if (stat.isToday) {
                            Brush.verticalGradient(listOf(Color(0xFF4ADE80), Color(0xFF06B6D4)))
                        } else {
                            Brush.verticalGradient(listOf(Color(0xFF34D399), Color(0xFF0D9488)))
                        }
                    } else {
                        if (stat.isToday) {
                            Brush.verticalGradient(listOf(Color(0xFFF9D423), Color(0xFFFB923C)))
                        } else {
                            Brush.verticalGradient(listOf(Color(0xFFFDE047), Color(0xFFEAB308)))
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedDayIndex = index }
                            .padding(horizontal = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        // Valor arriba de la barra
                        Text(
                            text = if (selectedMetric == "earnings") {
                                if (stat.totalEarnings > 0) "Q${stat.totalEarnings.toInt()}" else "0"
                            } else {
                                "${stat.tripsCount}"
                            },
                            color = if (isSelected) Color(0xFFF9D423) else Color(0xFF94A3B8).copy(alpha = animProgress.coerceIn(0f, 1f)),
                            fontSize = 9.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )

                        // Contenedor de la barra (Pista + Barra animada)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(maxHeightDp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) Color(0x33F9D423) else Color(0x1F2C5364)),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            if (currentBarHeight > 0.dp) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(if (isSelected) 0.85f else 0.75f)
                                        .height(currentBarHeight)
                                        .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                                        .background(barGradient)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Etiqueta del día ("Hoy", "Ayer", "Lun"...)
                        Text(
                            text = stat.dayLabel,
                            color = when {
                                stat.isToday -> Color(0xFFF9D423)
                                isSelected -> Color.White
                                else -> Color(0xFF94A3B8)
                            },
                            fontSize = 10.sp,
                            fontWeight = if (stat.isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )

                        // Número del día ("19", "18"...)
                        Text(
                            text = stat.dayNumber,
                            color = if (isSelected) Color(0xFFF9D423) else Color(0xFF64748B),
                            fontSize = 9.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Fila 4: Tarjeta informativa del día seleccionado
            val currentDayStat = weeklyStats.getOrNull(selectedDayIndex)
            if (currentDayStat != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0F1E29),
                    border = BorderStroke(1.dp, if (currentDayStat.isToday) Color(0x55F9D423) else Color(0x22FFFFFF)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (currentDayStat.isToday) Icons.Default.Today else Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = if (currentDayStat.isToday) Color(0xFFF9D423) else Color(0xFF38BDF8),
                                modifier = Modifier.size(16.dp)
                            )
                            Column {
                                Text(
                                    text = currentDayStat.fullDateLabel,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (currentDayStat.tripsCount > 0) "${currentDayStat.tripsCount} viajes registrados" else "Sin viajes",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Text(
                            text = "Q ${String.format(Locale.US, "%.2f", currentDayStat.totalEarnings)}",
                            color = Color(0xFF4ADE80),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}
