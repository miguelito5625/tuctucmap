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
import androidx.compose.material.icons.automirrored.filled.Chat
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
import com.example.tuctucmap.ui.util.ExternalNavigationUtils
import com.example.tuctucmap.data.model.parseIsoTimestamp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

enum class DriverHistoryPeriodFilter {
    TODAY,
    YESTERDAY,
    CUSTOM_RANGE
}

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
    var currentPeriodFilter by remember { mutableStateOf(DriverHistoryPeriodFilter.TODAY) }
    var customRangeStartUtcMillis by remember { mutableStateOf<Long?>(null) }
    var customRangeEndUtcMillis by remember { mutableStateOf<Long?>(null) }
    var showDatePickerDialog by remember { mutableStateOf(false) }
    var selectedTrip by remember { mutableStateOf<SupabaseRepository.CompletedTripResponseDto?>(null) }

    // Fechas en zona horaria local del dispositivo
    val localBounds = remember(currentPeriodFilter, customRangeStartUtcMillis, customRangeEndUtcMillis) {
        val startCal = Calendar.getInstance()
        val endCal = Calendar.getInstance()

        when (currentPeriodFilter) {
            DriverHistoryPeriodFilter.TODAY -> {
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
            DriverHistoryPeriodFilter.YESTERDAY -> {
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
            DriverHistoryPeriodFilter.CUSTOM_RANGE -> {
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
        Pair(startCal, endCal)
    }

    val sdfUtc = remember {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
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

    fun loadTrips() {
        val (startCal, endCal) = localBounds
        val startUtc = sdfUtc.format(startCal.time)
        val endUtc = sdfUtc.format(endCal.time)
        val startMs = startCal.timeInMillis
        val endMs = endCal.timeInMillis

        coroutineScope.launch {
            isLoading = true
            repository.getDriverTripHistory(startDateUtc = startUtc, endDateUtc = endUtc).onSuccess { list ->
                // Filtro estricto en memoria en la zona horaria local del dispositivo
                trips = list.filter { trip ->
                    val epoch = parseIsoTimestamp(trip.created_at)
                    epoch in startMs..endMs
                }.sortedByDescending { it.created_at }
            }.onFailure {
                Toast.makeText(context, "Error al cargar historial: ${it.message}", Toast.LENGTH_SHORT).show()
            }
            isLoading = false
        }
    }

    LaunchedEffect(currentPeriodFilter, customRangeStartUtcMillis, customRangeEndUtcMillis) {
        loadTrips()
    }

    // Configuración de límites para el selector de fechas (máximo 7 días hacia atrás hasta hoy)
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
                            text = when (currentPeriodFilter) {
                                DriverHistoryPeriodFilter.TODAY -> "Viajes de hoy"
                                DriverHistoryPeriodFilter.YESTERDAY -> "Viajes de ayer"
                                DriverHistoryPeriodFilter.CUSTOM_RANGE -> "Rango: $customRangeSummary"
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
                // ── Filtro de Período (Hoy, Ayer o DatePicker máx. 7 días hacia atrás) ──────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
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
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0x33F9D423)
                        ) {
                            Text(
                                text = when (currentPeriodFilter) {
                                    DriverHistoryPeriodFilter.TODAY -> "Solo Hoy"
                                    DriverHistoryPeriodFilter.YESTERDAY -> "Solo Ayer"
                                    DriverHistoryPeriodFilter.CUSTOM_RANGE -> customRangeSummary
                                },
                                color = Color(0xFFF9D423),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Chips de Selección: Hoy, Ayer, Rango de fechas (Date Picker)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Opción 1: Hoy
                        FilterChip(
                            selected = currentPeriodFilter == DriverHistoryPeriodFilter.TODAY,
                            onClick = {
                                currentPeriodFilter = DriverHistoryPeriodFilter.TODAY
                            },
                            label = { Text("Hoy") },
                            leadingIcon = if (currentPeriodFilter == DriverHistoryPeriodFilter.TODAY) {
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
                            selected = currentPeriodFilter == DriverHistoryPeriodFilter.YESTERDAY,
                            onClick = {
                                currentPeriodFilter = DriverHistoryPeriodFilter.YESTERDAY
                            },
                            label = { Text("Ayer") },
                            leadingIcon = if (currentPeriodFilter == DriverHistoryPeriodFilter.YESTERDAY) {
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

                        // Opción 3: Date picker (Rango máx. 7 días hacia atrás)
                        FilterChip(
                            selected = currentPeriodFilter == DriverHistoryPeriodFilter.CUSTOM_RANGE,
                            onClick = {
                                showDatePickerDialog = true
                            },
                            label = {
                                Text(
                                    if (currentPeriodFilter == DriverHistoryPeriodFilter.CUSTOM_RANGE) {
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

                // Modal DatePickerDialog con DateRangePicker
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
                                        currentPeriodFilter = DriverHistoryPeriodFilter.CUSTOM_RANGE
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

                HorizontalDivider(color = Color(0x1AFFFFFF), thickness = 1.dp)

                // ── Filtros de Estado ──────────────────────────────────────────
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
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
                                    ExternalNavigationUtils.openGoogleMaps(context, trip.passenger_lat, trip.passenger_lon)
                                },
                                onOpenWaze = {
                                    ExternalNavigationUtils.openWaze(context, trip.passenger_lat, trip.passenger_lon)
                                },
                                onWhatsApp = {
                                    openWhatsApp(context, trip.passenger_phone ?: "", trip.passenger_name ?: "")
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
                            Text(trip.passenger_name?.ifBlank { "Pasajero" } ?: "Pasajero", fontWeight = FontWeight.Bold, fontSize = 18.sp)
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

                            // Tipo de pasajero (Cuenta registrada vs Invitado)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Tipo: ", color = Color.LightGray, fontSize = 14.sp)
                                Surface(
                                    color = if (trip.isRegisteredPassenger) Color(0x284ADE80) else Color(0x2494A3B8),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, if (trip.isRegisteredPassenger) Color(0x664ADE80) else Color(0x4494A3B8))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (trip.isRegisteredPassenger) Icons.Default.CheckCircle else Icons.Default.PersonOutline,
                                            contentDescription = null,
                                            tint = if (trip.isRegisteredPassenger) Color(0xFF4ADE80) else Color(0xFFCBD5E1),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (trip.isRegisteredPassenger) "Usuario con Cuenta Registrada" else "Invitado (Solicitó sin cuenta)",
                                            color = if (trip.isRegisteredPassenger) Color(0xFF4ADE80) else Color(0xFFCBD5E1),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Mensaje: ${trip.message}", color = Color.White, fontSize = 14.sp)

                            if (trip.fare != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Paid, contentDescription = null, tint = Color(0xFFF9D423), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Cobro registrado: Q${String.format(Locale.US, "%.2f", trip.fare)}", color = Color(0xFFF9D423), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }

                            if (trip.distance_km != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Navigation, contentDescription = null, tint = Color(0xFF4ADE80), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Distancia recorrida: ${String.format(Locale.US, "%.2f", trip.distance_km)} km", color = Color(0xFF4ADE80), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }

                            if (!trip.route_points.isNullOrEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Puntos GPS de telemetría: ${trip.route_points.size}", color = Color(0xFF94A3B8), fontSize = 12.sp)
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Fecha: ${parseDate(trip.created_at)}", color = Color.LightGray, fontSize = 13.sp)

                            if (!trip.passenger_phone.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Teléfono: ${trip.passenger_phone}", color = Color.LightGray, fontSize = 13.sp)
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Botón Google Maps
                            Button(
                                onClick = {
                                    ExternalNavigationUtils.openGoogleMaps(context, trip.passenger_lat, trip.passenger_lon)
                                    selectedTrip = null
                                },
                                modifier = Modifier.fillMaxWidth().height(46.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A73E8)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Navigation, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Trazar Ruta con Google Maps", color = Color.White, fontWeight = FontWeight.Bold)
                            }

                            // Botón Waze
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    ExternalNavigationUtils.openWaze(context, trip.passenger_lat, trip.passenger_lon)
                                    selectedTrip = null
                                },
                                modifier = Modifier.fillMaxWidth().height(46.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D2FF), contentColor = Color(0xFF0F172A)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(imageVector = Icons.Default.NearMe, contentDescription = null, tint = Color(0xFF0F172A))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Trazar Ruta con Waze", color = Color(0xFF0F172A), fontWeight = FontWeight.ExtraBold)
                            }

                            // Botón WhatsApp
                            Spacer(modifier = Modifier.height(10.dp))
                            val hasPhone = !trip.passenger_phone.isNullOrBlank()
                            Button(
                                onClick = {
                                    openWhatsApp(context, trip.passenger_phone ?: "", trip.passenger_name ?: "")
                                    selectedTrip = null
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (hasPhone) Color(0xFF25D366) else Color(0xFF334155),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Chat,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (hasPhone) "Abrir Chat de WhatsApp" else "Chat no disponible (Sin teléfono)",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
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
    onOpenWaze: () -> Unit,
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
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (trip.isRegisteredPassenger) Color(0x334ADE80) else Color(0x33F9D423)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (trip.isRegisteredPassenger) Icons.Default.VerifiedUser else Icons.Default.Person,
                            contentDescription = null,
                            tint = if (trip.isRegisteredPassenger) Color(0xFF4ADE80) else Color(0xFFF9D423),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = trip.passenger_name?.ifBlank { "Pasajero" } ?: "Pasajero",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Surface(
                            color = if (trip.isRegisteredPassenger) Color(0x284ADE80) else Color(0x2494A3B8),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(0.5.dp, if (trip.isRegisteredPassenger) Color(0x664ADE80) else Color(0x4494A3B8))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (trip.isRegisteredPassenger) Icons.Default.CheckCircle else Icons.Default.PersonOutline,
                                    contentDescription = null,
                                    tint = if (trip.isRegisteredPassenger) Color(0xFF4ADE80) else Color(0xFFCBD5E1),
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = if (trip.isRegisteredPassenger) "Registrado" else "Sin cuenta",
                                    color = if (trip.isRegisteredPassenger) Color(0xFF4ADE80) else Color(0xFFCBD5E1),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
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

            // Métricas del viaje: Cobro registrado y Distancia recorrida
            if (trip.fare != null || trip.distance_km != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (trip.fare != null) {
                        Surface(
                            color = Color(0x28F9D423),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0x66F9D423))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Paid,
                                    contentDescription = null,
                                    tint = Color(0xFFF9D423),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Cobro: Q${String.format(Locale.US, "%.2f", trip.fare)}",
                                    color = Color(0xFFF9D423),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    if (trip.distance_km != null) {
                        Surface(
                            color = Color(0x284ADE80),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0x664ADE80))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Navigation,
                                    contentDescription = null,
                                    tint = Color(0xFF4ADE80),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${String.format(Locale.US, "%.2f", trip.distance_km)} km",
                                    color = Color(0xFF4ADE80),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── Botones de acción directos: Navegación externa y WhatsApp ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Botón Google Maps
                Button(
                    onClick = onOpenMaps,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAccepted) Color(0xFF1A73E8) else Color(0xFF334155),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Maps",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                // Botón Waze
                Button(
                    onClick = onOpenWaze,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00D2FF),
                        contentColor = Color(0xFF0F172A)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NearMe,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Waze",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                }
            }

            // Fila 2: Botón directo para abrir chat de WhatsApp con el pasajero
            Spacer(modifier = Modifier.height(8.dp))
            val hasPhone = !trip.passenger_phone.isNullOrBlank()
            Button(
                onClick = onWhatsApp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (hasPhone) Color(0xFF25D366) else Color(0xFF334155),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (hasPhone) "Chat de WhatsApp con Pasajero" else "Chat no disponible (Sin número registrado)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ── Funciones utilitarias para WhatsApp ───────────────────────────────────────

private fun openWhatsApp(context: Context, phone: String, passengerName: String = "") {
    val digits = phone.replace(Regex("[^0-9]"), "")
    if (digits.isBlank()) {
        Toast.makeText(context, "El pasajero no cuenta con número telefónico registrado", Toast.LENGTH_SHORT).show()
        return
    }
    // Si tiene 8 dígitos (formato estándar de Guatemala), anteponer el código de país 502
    val formattedPhone = if (digits.length == 8) "502$digits" else digits
    val greetingName = if (passengerName.isNotBlank() && passengerName != "Pasajero") " $passengerName" else ""
    val defaultMsg = "¡Hola$greetingName! Te saludo desde TucTuc Map sobre tu viaje."

    try {
        val url = "https://api.whatsapp.com/send?phone=$formattedPhone&text=${Uri.encode(defaultMsg)}"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            setPackage("com.whatsapp")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            val fallbackUrl = "https://wa.me/$formattedPhone?text=${Uri.encode(defaultMsg)}"
            val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallbackIntent)
        } catch (_: Exception) {
            Toast.makeText(context, "No se pudo abrir WhatsApp en este dispositivo", Toast.LENGTH_SHORT).show()
        }
    }
}
