package com.example.tuctucmap.ui.screens.admin

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.tuctucmap.data.model.AdminPaymentRequest
import com.example.tuctucmap.data.model.AdminSubscriptionHistory
import com.example.tuctucmap.data.model.AppConfig
import com.example.tuctucmap.data.repository.AdminRepository
import com.example.tuctucmap.data.repository.SupabaseRepository
import com.example.tuctucmap.ui.util.CsvSecurityUtils
import com.example.tuctucmap.ui.util.ExcelExporter
import kotlinx.coroutines.launch
import java.io.File
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

enum class SubscriptionHistoryPeriodFilter(val label: String) {
    TODAY("Hoy"),
    LAST_3_DAYS("3 días"),
    LAST_7_DAYS("7 días"),
    CUSTOM("Rango personalizado")
}

private fun getSubscriptionPeriodRange(
    filter: SubscriptionHistoryPeriodFilter,
    customStartUtcMillis: Long? = null,
    customEndUtcMillis: Long? = null
): Pair<Long, Long> {
    if (filter == SubscriptionHistoryPeriodFilter.CUSTOM && customStartUtcMillis != null && customEndUtcMillis != null) {
        val startCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = minOf(customStartUtcMillis, customEndUtcMillis)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = maxOf(customStartUtcMillis, customEndUtcMillis)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return Pair(startCal.timeInMillis, endCal.timeInMillis)
    }

    val daysBack = when (filter) {
        SubscriptionHistoryPeriodFilter.TODAY -> 0
        SubscriptionHistoryPeriodFilter.LAST_3_DAYS -> 2
        SubscriptionHistoryPeriodFilter.LAST_7_DAYS -> 6
        SubscriptionHistoryPeriodFilter.CUSTOM -> 0
    }

    val startCal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (daysBack > 0) {
            add(Calendar.DAY_OF_YEAR, -daysBack)
        }
    }

    val endCal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }

    return Pair(startCal.timeInMillis, endCal.timeInMillis)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminSubscriptionsTab(
    adminRepo: AdminRepository,
    supabaseRepo: SupabaseRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTabIndex by remember { mutableStateOf(0) } // 0 = Solicitudes, 1 = Historial
    var requests by remember { mutableStateOf<List<AdminPaymentRequest>>(emptyList()) }
    var history by remember { mutableStateOf<List<AdminSubscriptionHistory>>(emptyList()) }
    var appConfig by remember { mutableStateOf(AppConfig()) }
    var isLoading by remember { mutableStateOf(true) }

    var selectedFilter by remember { mutableStateOf("pendiente") } // "pendiente", "aprobado", "rechazado", "todos"

    // Filtros del Historial Global (Por defecto: Hoy)
    var historyPeriodFilter by remember { mutableStateOf(SubscriptionHistoryPeriodFilter.TODAY) }
    var customHistoryStartUtcMillis by remember { mutableStateOf<Long?>(null) }
    var customHistoryEndUtcMillis by remember { mutableStateOf<Long?>(null) }
    var showHistoryDateRangePickerDialog by remember { mutableStateOf(false) }

    // Diálogos de acción
    var requestToApprove by remember { mutableStateOf<AdminPaymentRequest?>(null) }
    var requestToReject by remember { mutableStateOf<AdminPaymentRequest?>(null) }
    var rejectionReasonText by remember { mutableStateOf("") }

    fun loadData() {
        scope.launch {
            isLoading = true
            appConfig = supabaseRepo.getAppConfig()
            val reqRes = adminRepo.getPaymentRequests()
            if (reqRes.isSuccess) {
                requests = reqRes.getOrDefault(emptyList())
            }
            val histRes = adminRepo.getAllSubscriptionsHistory()
            if (histRes.isSuccess) {
                history = histRes.getOrDefault(emptyList())
            }
            isLoading = false
            adminRepo.refreshPendingCounts()
        }
    }

    LaunchedEffect(Unit) {
        loadData()
    }

    val pendingCount = remember(requests) { requests.count { it.status == "pendiente" } }

    val filteredRequests = remember(requests, selectedFilter) {
        if (selectedFilter == "todos") requests
        else requests.filter { it.status == selectedFilter }
    }

    val filteredHistory = remember(history, historyPeriodFilter, customHistoryStartUtcMillis, customHistoryEndUtcMillis) {
        val (startMs, endMs) = getSubscriptionPeriodRange(
            filter = historyPeriodFilter,
            customStartUtcMillis = customHistoryStartUtcMillis,
            customEndUtcMillis = customHistoryEndUtcMillis
        )
        history.filter { item ->
            val epoch = item.createdAtEpoch
            if (epoch > 0L) {
                epoch in startMs..endMs
            } else false
        }
    }

    val customRangeLabel = remember(customHistoryStartUtcMillis, customHistoryEndUtcMillis) {
        if (customHistoryStartUtcMillis != null && customHistoryEndUtcMillis != null) {
            val sdf = SimpleDateFormat("dd/MM", Locale.US)
            "${sdf.format(Date(customHistoryStartUtcMillis!!))} - ${sdf.format(Date(customHistoryEndUtcMillis!!))}"
        } else {
            "Personalizado"
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        // ── Pestañas Superiores ──────────────────────────────────────────────
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = Color(0xFF1E293B),
            contentColor = Color(0xFFF59E0B),
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                    color = Color(0xFFF59E0B)
                )
            }
        ) {
            Tab(
                selected = selectedTabIndex == 0,
                onClick = { selectedTabIndex = 0 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Solicitudes",
                            fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp
                        )
                        if (pendingCount > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = Color(0xFFEF4444),
                                shape = CircleShape
                            ) {
                                Text(
                                    text = "$pendingCount",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            )

            Tab(
                selected = selectedTabIndex == 1,
                onClick = { selectedTabIndex = 1 },
                text = {
                    Text(
                        "Historial Global",
                        fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp
                    )
                }
            )
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFF59E0B))
            }
        } else if (selectedTabIndex == 0) {
            // ── PESTAÑA 1: SOLICITUDES DE RENOVACIÓN ──────────────────────────
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Filtros de estado
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            FilterChip(
                                selected = selectedFilter == "pendiente",
                                onClick = { selectedFilter = "pendiente" },
                                label = { Text("Pendientes ($pendingCount)") },
                                colors = filterChipColors()
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedFilter == "aprobado",
                                onClick = { selectedFilter = "aprobado" },
                                label = { Text("Aprobadas") },
                                colors = filterChipColors()
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedFilter == "rechazado",
                                onClick = { selectedFilter = "rechazado" },
                                label = { Text("Rechazadas") },
                                colors = filterChipColors()
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedFilter == "todos",
                                onClick = { selectedFilter = "todos" },
                                label = { Text("Todas (${requests.size})") },
                                colors = filterChipColors()
                            )
                        }
                    }
                }

                if (filteredRequests.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 50.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No hay solicitudes en esta categoría", color = Color(0xFF94A3B8), fontSize = 14.sp)
                        }
                    }
                } else {
                    items(filteredRequests, key = { it.id }) { req ->
                        PaymentRequestCard(
                            request = req,
                            currencySymbol = appConfig.currencySymbol,
                            onOpenWhatsApp = {
                                openWhatsAppChat(context, req.driverPhone, req.driverName)
                            },
                            onApprove = {
                                requestToApprove = req
                            },
                            onReject = {
                                requestToReject = req
                                rejectionReasonText = ""
                            }
                        )
                    }
                }
            }
        } else {
            // ── PESTAÑA 2: HISTORIAL GLOBAL DE MEMBRESÍAS ─────────────────────
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Filtros de Período de Fecha
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            FilterChip(
                                selected = historyPeriodFilter == SubscriptionHistoryPeriodFilter.TODAY,
                                onClick = { historyPeriodFilter = SubscriptionHistoryPeriodFilter.TODAY },
                                label = { Text("Hoy") },
                                colors = filterChipColors()
                            )
                        }
                        item {
                            FilterChip(
                                selected = historyPeriodFilter == SubscriptionHistoryPeriodFilter.LAST_3_DAYS,
                                onClick = { historyPeriodFilter = SubscriptionHistoryPeriodFilter.LAST_3_DAYS },
                                label = { Text("3 días") },
                                colors = filterChipColors()
                            )
                        }
                        item {
                            FilterChip(
                                selected = historyPeriodFilter == SubscriptionHistoryPeriodFilter.LAST_7_DAYS,
                                onClick = { historyPeriodFilter = SubscriptionHistoryPeriodFilter.LAST_7_DAYS },
                                label = { Text("7 días") },
                                colors = filterChipColors()
                            )
                        }
                        item {
                            FilterChip(
                                selected = historyPeriodFilter == SubscriptionHistoryPeriodFilter.CUSTOM,
                                onClick = {
                                    historyPeriodFilter = SubscriptionHistoryPeriodFilter.CUSTOM
                                    showHistoryDateRangePickerDialog = true
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(14.dp))
                                },
                                label = {
                                    Text(
                                        if (historyPeriodFilter == SubscriptionHistoryPeriodFilter.CUSTOM && customHistoryStartUtcMillis != null)
                                            customRangeLabel
                                        else "Rango personalizado"
                                    )
                                },
                                colors = filterChipColors()
                            )
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${filteredHistory.size} Registros (${historyPeriodFilter.label})",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Button(
                            onClick = {
                                ExcelExporter.exportAndShareSubscriptionsExcel(
                                    context = context,
                                    history = filteredHistory,
                                    currencySymbol = appConfig.currencySymbol,
                                    periodLabel = if (historyPeriodFilter == SubscriptionHistoryPeriodFilter.CUSTOM && customHistoryStartUtcMillis != null)
                                        customRangeLabel
                                    else historyPeriodFilter.label
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Exportar Excel", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (filteredHistory.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 30.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SearchOff,
                                    contentDescription = null,
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "No hay registros para este período",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "No se encontraron pagos confirmados para el filtro: ${historyPeriodFilter.label}.",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                if (historyPeriodFilter != SubscriptionHistoryPeriodFilter.LAST_7_DAYS) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    OutlinedButton(
                                        onClick = { historyPeriodFilter = SubscriptionHistoryPeriodFilter.LAST_7_DAYS },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF59E0B)),
                                        border = BorderStroke(1.dp, Color(0xFFF59E0B))
                                    ) {
                                        Text("Ver últimos 7 días", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    items(filteredHistory, key = { it.id }) { item ->
                        SubscriptionHistoryCard(item = item, currencySymbol = appConfig.currencySymbol)
                    }
                }
            }
        }
    }

    // ── Diálogo para Aprobar Solicitud ───────────────────────────────────────
    requestToApprove?.let { req ->
        AlertDialog(
            onDismissRequest = { requestToApprove = null },
            title = { Text("¿Aprobar Renovación?", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column {
                    Text(
                        "Se renovará la membresía del conductor ${req.driverName} (${req.driverPlate}) por plan ${req.planType.uppercase()}.",
                        color = Color(0xFFCBD5E1),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Monto: ${appConfig.currencySymbol} ${req.amount} • Ref: ${req.referenceNumber.ifBlank { "Comprobante por WhatsApp" }}",
                        color = Color(0xFFF59E0B),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Se sumarán los nuevos días a su fecha de vencimiento actual de forma acumulativa justa.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = req.id
                        requestToApprove = null
                        scope.launch {
                            val res = adminRepo.approvePaymentRequest(id)
                            if (res.isSuccess) {
                                Toast.makeText(context, "Solicitud aprobada y vigencia extendida", Toast.LENGTH_LONG).show()
                                loadData()
                            } else {
                                Toast.makeText(context, "Error al aprobar solicitud", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("Aprobar Ahora", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { requestToApprove = null }) {
                    Text("Cancelar", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    // ── Diálogo para Rechazar Solicitud ───────────────────────────────────────
    requestToReject?.let { req ->
        AlertDialog(
            onDismissRequest = { requestToReject = null },
            title = { Text("Rechazar Solicitud", fontWeight = FontWeight.Bold, color = Color(0xFFEF4444)) },
            text = {
                Column {
                    Text(
                        "Indica el motivo de rechazo visible para ${req.driverName}:",
                        color = Color(0xFFCBD5E1),
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = rejectionReasonText,
                        onValueChange = { rejectionReasonText = it },
                        placeholder = { Text("Ej: Comprobante no coincide o ilegible...", color = Color(0xFF64748B), fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFEF4444),
                            unfocusedBorderColor = Color(0xFF475569)
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = req.id
                        val reason = rejectionReasonText.trim().ifBlank { "Comprobante rechazado por administración" }
                        requestToReject = null
                        scope.launch {
                            val res = adminRepo.rejectPaymentRequest(id, reason)
                            if (res.isSuccess) {
                                Toast.makeText(context, "Solicitud rechazada", Toast.LENGTH_SHORT).show()
                                loadData()
                            } else {
                                Toast.makeText(context, "Error al rechazar solicitud", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Rechazar", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { requestToReject = null }) {
                    Text("Cancelar", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    // ── Diálogo DateRangePicker para Rango Personalizado del Historial ─────────
    if (showHistoryDateRangePickerDialog) {
        val dateRangePickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = customHistoryStartUtcMillis ?: System.currentTimeMillis(),
            initialSelectedEndDateMillis = customHistoryEndUtcMillis ?: System.currentTimeMillis()
        )

        DatePickerDialog(
            onDismissRequest = { showHistoryDateRangePickerDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val start = dateRangePickerState.selectedStartDateMillis
                        val end = dateRangePickerState.selectedEndDateMillis ?: start
                        if (start != null && end != null) {
                            customHistoryStartUtcMillis = minOf(start, end)
                            customHistoryEndUtcMillis = maxOf(start, end)
                            historyPeriodFilter = SubscriptionHistoryPeriodFilter.CUSTOM
                            showHistoryDateRangePickerDialog = false
                        }
                    },
                    enabled = dateRangePickerState.selectedStartDateMillis != null
                ) {
                    Text("Aplicar", color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showHistoryDateRangePickerDialog = false }) {
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
                        text = "Seleccionar Rango de Suscripciones",
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp, end = 24.dp),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                headline = {
                    Text(
                        text = "Filtra por fecha de confirmación de pago",
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
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
                    currentYearContentColor = Color(0xFFF59E0B),
                    selectedYearContentColor = Color(0xFF0F172A),
                    selectedYearContainerColor = Color(0xFFF59E0B),
                    dayContentColor = Color.White,
                    disabledDayContentColor = Color(0xFF475569),
                    selectedDayContentColor = Color(0xFF0F172A),
                    selectedDayContainerColor = Color(0xFFF59E0B),
                    dayInSelectionRangeContentColor = Color(0xFF0F172A),
                    dayInSelectionRangeContainerColor = Color(0x66F59E0B)
                )
            )
        }
    }
}

@Composable
private fun PaymentRequestCard(
    request: AdminPaymentRequest,
    currencySymbol: String,
    onOpenWhatsApp: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit
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
                    Text(
                        text = request.driverName.ifBlank { "Conductor" },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val unitLabel = if (request.driverUnitNumber.isNotBlank()) "${request.driverUnitNumber} • " else ""
                    Text(
                        text = "$unitLabel${request.driverPlate} • 📞 ${request.driverPhone}",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                }

                // Chip de estado
                val (chipText, chipBg, chipColor) = when (request.status) {
                    "aprobado" -> Triple("Aprobado", Color(0x2210B981), Color(0xFF10B981))
                    "rechazado" -> Triple("Rechazado", Color(0x22EF4444), Color(0xFFEF4444))
                    else -> Triple("Pendiente", Color(0x22F59E0B), Color(0xFFF59E0B))
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = chipBg
                ) {
                    Text(
                        text = chipText,
                        color = chipColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Datos del Pago
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF0F172A),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Plan: ${request.planType.uppercase()}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(text = "$currencySymbol ${request.amount}", color = Color(0xFFF59E0B), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Ref / Boleta: ${request.referenceNumber.ifBlank { "Captura por WhatsApp" }}",
                        fontSize = 11.sp,
                        color = Color(0xFFCBD5E1)
                    )
                    Text(
                        text = "Fecha: ${formatEpochDate(request.createdAtEpoch)}",
                        fontSize = 10.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }

            if (!request.rejectionReason.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Motivo rechazo: ${request.rejectionReason}",
                    fontSize = 11.sp,
                    color = Color(0xFFEF4444)
                )
            }

            // Acciones si está pendiente
            if (request.status == "pendiente") {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenWhatsApp,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF10B981)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ver Chat", fontSize = 12.sp)
                    }

                    Button(
                        onClick = onApprove,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Aprobar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onReject,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Rechazar", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SubscriptionHistoryCard(
    item: AdminSubscriptionHistory,
    currencySymbol: String
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.driverName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.driverPlate} • Plan ${item.planType.uppercase()}",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )
                Text(
                    text = "Vigencia: ${formatEpochDate(item.createdAtEpoch)} al ${formatEpochDate(item.endDateEpoch)}",
                    fontSize = 10.sp,
                    color = Color(0xFF64748B)
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$currencySymbol ${item.amountPaid}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFF59E0B)
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (item.isCurrentlyActive) Color(0x2210B981) else Color(0x22EF4444)
                ) {
                    Text(
                        text = if (item.isCurrentlyActive) "Activo" else "Expirado",
                        color = if (item.isCurrentlyActive) Color(0xFF10B981) else Color(0xFFEF4444),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun filterChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = Color(0xFF1E293B),
    labelColor = Color(0xFF94A3B8),
    selectedContainerColor = Color(0xFFF59E0B),
    selectedLabelColor = Color(0xFF0F172A)
)

private fun formatEpochDate(epochMs: Long): String {
    if (epochMs <= 0L) return "N/A"
    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.US)
    return sdf.format(Date(epochMs))
}

private fun openWhatsAppChat(context: Context, phoneRaw: String, name: String) {
    try {
        val phone = phoneRaw.replace(Regex("[^0-9]"), "")
        val formattedPhone = if (phone.startsWith("502")) phone else "502$phone"
        val msg = "Hola $name, te saludamos de la administración de TucTuc Map sobre tu comprobante de suscripción."
        val encoded = URLEncoder.encode(msg, "UTF-8")
        val uri = Uri.parse("https://wa.me/$formattedPhone?text=$encoded")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "No se pudo abrir WhatsApp", Toast.LENGTH_SHORT).show()
    }
}

private fun exportAndShareCsv(
    context: Context,
    history: List<AdminSubscriptionHistory>,
    currency: String
) {
    try {
        val sb = StringBuilder()
        sb.append("ID,Conductor,Placa/Unidad,Plan,Monto,Metodo,Referencia,Fecha Pago,Vencimiento,Estado\n")
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        for (h in history) {
            val dateStr = if (h.createdAtEpoch > 0) sdf.format(Date(h.createdAtEpoch)) else ""
            val endStr = if (h.endDateEpoch > 0) sdf.format(Date(h.endDateEpoch)) else ""
            val status = if (h.isCurrentlyActive) "Activo" else "Expirado"
            sb.append(CsvSecurityUtils.escapeCsvCell(h.id)).append(",")
            sb.append(CsvSecurityUtils.escapeCsvCell(h.driverName)).append(",")
            sb.append(CsvSecurityUtils.escapeCsvCell(h.driverPlate)).append(",")
            sb.append(CsvSecurityUtils.escapeCsvCell(h.planType)).append(",")
            sb.append(h.amountPaid).append(",")
            sb.append(CsvSecurityUtils.escapeCsvCell(h.paymentMethod)).append(",")
            sb.append(CsvSecurityUtils.escapeCsvCell(h.referenceNumber)).append(",")
            sb.append(CsvSecurityUtils.escapeCsvCell(dateStr)).append(",")
            sb.append(CsvSecurityUtils.escapeCsvCell(endStr)).append(",")
            sb.append(CsvSecurityUtils.escapeCsvCell(status)).append("\n")
        }

        val cacheDir = context.cacheDir
        val file = File(cacheDir, "tuctuc_suscripciones_${System.currentTimeMillis()}.csv")
        file.writeText(sb.toString())

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "Reporte de Suscripciones TucTuc Map")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Compartir reporte CSV"))
    } catch (e: Exception) {
        Toast.makeText(context, "Error al generar CSV: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

