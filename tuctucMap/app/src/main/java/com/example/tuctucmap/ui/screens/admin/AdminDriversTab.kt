package com.example.tuctucmap.ui.screens.admin

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tuctucmap.data.model.AdminDriver
import com.example.tuctucmap.data.model.AdminFinancialStats
import com.example.tuctucmap.data.model.AppConfig
import com.example.tuctucmap.data.repository.AdminRepository
import com.example.tuctucmap.data.repository.SupabaseRepository
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDriversTab(
    adminRepo: AdminRepository,
    supabaseRepo: SupabaseRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var drivers by remember { mutableStateOf<List<AdminDriver>>(emptyList()) }
    var financialStats by remember { mutableStateOf(AdminFinancialStats()) }
    var appConfig by remember { mutableStateOf(AppConfig()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("todos") } // "todos", "pendientes", "aprobados", "por_vencer", "vencidos"

    // Estado para BottomSheet de renovación manual
    var driverToRenew by remember { mutableStateOf<AdminDriver?>(null) }
    var showRenewSheet by remember { mutableStateOf(false) }

    // Estado para diálogo de confirmación de eliminación
    var driverToDelete by remember { mutableStateOf<AdminDriver?>(null) }

    fun loadData() {
        scope.launch {
            isLoading = true
            appConfig = supabaseRepo.getAppConfig()
            val driversRes = adminRepo.fetchDrivers()
            if (driversRes.isSuccess) {
                drivers = driversRes.getOrDefault(emptyList())
            }
            val statsRes = adminRepo.getFinancialStats()
            if (statsRes.isSuccess) {
                financialStats = statsRes.getOrDefault(AdminFinancialStats())
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadData()
    }

    val filteredDrivers = remember(drivers, searchQuery, selectedFilter) {
        val q = searchQuery.trim().lowercase()
        drivers.filter { d ->
            val matchesQuery = q.isEmpty() ||
                    d.nombre.lowercase().contains(q) ||
                    d.telefono.contains(q) ||
                    d.placas.lowercase().contains(q) ||
                    d.numeroUnidad.lowercase().contains(q)

            val matchesFilter = when (selectedFilter) {
                "pendientes" -> d.estadoAprobacion == "pendiente"
                "aprobados" -> d.estadoAprobacion == "aprobado" && !d.isSubscriptionExpired
                "por_vencer" -> d.estadoAprobacion == "aprobado" && !d.isSubscriptionExpired && d.remainingDays in 1..7
                "vencidos" -> d.isSubscriptionExpired || d.estadoAprobacion == "suscripcion_vencida"
                else -> true
            }
            matchesQuery && matchesFilter
        }
    }

    val pendingCount = remember(drivers) { drivers.count { it.estadoAprobacion == "pendiente" } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFF59E0B))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ── KPIs Financieros ─────────────────────────────────────────
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            KpiCard(
                                title = "Ingresos Mes",
                                value = "${appConfig.currencySymbol} ${String.format(Locale.US, "%.0f", financialStats.monthRevenue)}",
                                subtitle = "Recaudado este mes",
                                color = Color(0xFF10B981)
                            )
                        }
                        item {
                            KpiCard(
                                title = "Total Recaudado",
                                value = "${appConfig.currencySymbol} ${String.format(Locale.US, "%.0f", financialStats.totalRevenue)}",
                                subtitle = "Histórico global",
                                color = Color(0xFF38BDF8)
                            )
                        }
                        item {
                            KpiCard(
                                title = "Flota Activa",
                                value = "${financialStats.activeFleetCount}",
                                subtitle = "TucTucs vigentes",
                                color = Color(0xFFF59E0B)
                            )
                        }
                        item {
                            KpiCard(
                                title = "Por vencer (7d)",
                                value = "${financialStats.expiringIn7Days}",
                                subtitle = "Esp. ${appConfig.currencySymbol} ${String.format(Locale.US, "%.0f", financialStats.expiringAmountExpected)}",
                                color = Color(0xFFEF4444)
                            )
                        }
                    }
                }

                // ── Buscador ─────────────────────────────────────────────────
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Buscar por nombre, placa o unidad...", color = Color(0xFF94A3B8), fontSize = 14.sp) },
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
                        )
                    )
                }

                // ── Chips de Filtro ──────────────────────────────────────────
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            FilterChip(
                                selected = selectedFilter == "todos",
                                onClick = { selectedFilter = "todos" },
                                label = { Text("Todos (${drivers.size})") },
                                colors = filterChipColors()
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedFilter == "pendientes",
                                onClick = { selectedFilter = "pendientes" },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Pendientes")
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
                                },
                                colors = filterChipColors()
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedFilter == "aprobados",
                                onClick = { selectedFilter = "aprobados" },
                                label = { Text("Activos") },
                                colors = filterChipColors()
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedFilter == "por_vencer",
                                onClick = { selectedFilter = "por_vencer" },
                                label = { Text("Por Vencer") },
                                colors = filterChipColors()
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedFilter == "vencidos",
                                onClick = { selectedFilter = "vencidos" },
                                label = { Text("Vencidos") },
                                colors = filterChipColors()
                            )
                        }
                    }
                }

                // ── Lista de Conductores ─────────────────────────────────────
                if (filteredDrivers.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No se encontraron conductores con estos filtros", color = Color(0xFF94A3B8), fontSize = 14.sp)
                        }
                    }
                } else {
                    items(filteredDrivers, key = { it.id }) { driver ->
                        AdminDriverCard(
                            driver = driver,
                            appConfig = appConfig,
                            onApprove = {
                                scope.launch {
                                    val res = adminRepo.approveDriver(driver.id)
                                    if (res.isSuccess) {
                                        Toast.makeText(context, "Conductor aprobado con éxito", Toast.LENGTH_SHORT).show()
                                        loadData()
                                    } else {
                                        Toast.makeText(context, "Error al aprobar", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onReject = {
                                scope.launch {
                                    val res = adminRepo.rejectDriver(driver.id)
                                    if (res.isSuccess) {
                                        Toast.makeText(context, "Conductor rechazado", Toast.LENGTH_SHORT).show()
                                        loadData()
                                    } else {
                                        Toast.makeText(context, "Error al rechazar", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onOpenRenew = {
                                driverToRenew = driver
                                showRenewSheet = true
                            },
                            onSendWhatsApp = {
                                openWhatsAppReminder(context, driver, appConfig)
                            },
                            onDelete = {
                                driverToDelete = driver
                            }
                        )
                    }
                }
            }
        }
    }

    // ── BottomSheet de Renovación Manual ─────────────────────────────────────
    if (showRenewSheet && driverToRenew != null) {
        ModalBottomSheet(
            onDismissRequest = {
                showRenewSheet = false
                driverToRenew = null
            },
            containerColor = Color(0xFF1E293B)
        ) {
            RenewSubscriptionBottomSheetContent(
                driver = driverToRenew!!,
                appConfig = appConfig,
                onConfirm = { plan, amount, method, ref ->
                    scope.launch {
                        val res = adminRepo.renewDriverSubscriptionManual(
                            driverId = driverToRenew!!.id,
                            planType = plan,
                            amountPaid = amount,
                            paymentMethod = method,
                            referenceNumber = ref
                        )
                        showRenewSheet = false
                        driverToRenew = null
                        if (res.isSuccess) {
                            Toast.makeText(context, "Membresía renovada exitosamente", Toast.LENGTH_LONG).show()
                            loadData()
                        } else {
                            Toast.makeText(context, "Error al renovar membresía", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onCancel = {
                    showRenewSheet = false
                    driverToRenew = null
                }
            )
        }
    }

    // ── Diálogo de confirmación para eliminar ────────────────────────────────
    driverToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { driverToDelete = null },
            title = { Text("¿Eliminar Conductor?", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Text(
                    "¿Estás seguro de que deseas eliminar permanentemente a ${target.nombre.ifBlank { "este conductor" }} (${target.displayIdentifier})? Esta acción no se puede deshacer.",
                    color = Color(0xFFCBD5E1)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = target.id
                        driverToDelete = null
                        scope.launch {
                            val res = adminRepo.deleteDriver(id)
                            if (res.isSuccess) {
                                Toast.makeText(context, "Conductor eliminado", Toast.LENGTH_SHORT).show()
                                loadData()
                            } else {
                                Toast.makeText(context, "Error al eliminar conductor", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Eliminar", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { driverToDelete = null }) {
                    Text("Cancelar", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }
}

@Composable
private fun KpiCard(
    title: String,
    value: String,
    subtitle: String,
    color: Color
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        modifier = Modifier.width(140.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = title, fontSize = 11.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = color)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, fontSize = 10.sp, color = Color(0xFF64748B), maxLines = 1)
        }
    }
}

@Composable
private fun AdminDriverCard(
    driver: AdminDriver,
    appConfig: AppConfig,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onOpenRenew: () -> Unit,
    onSendWhatsApp: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Avatar con inicial
                val initial = (driver.nombre.trim().firstOrNull() ?: 'T').uppercaseChar().toString()
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFFF59E0B), Color(0xFFD97706))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initial,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = driver.nombre.ifBlank { "Sin nombre" },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${driver.displayIdentifier} • 📞 ${driver.telefono.ifBlank { "Sin teléfono" }}",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                }

                // Menú de 3 puntos
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Opciones", tint = Color(0xFF94A3B8))
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(Color(0xFF0F172A))
                    ) {
                        DropdownMenuItem(
                            text = { Text("💬 Contactar WhatsApp", color = Color.White) },
                            onClick = {
                                menuExpanded = false
                                onSendWhatsApp()
                            },
                            leadingIcon = { Icon(Icons.Default.Chat, contentDescription = null, tint = Color(0xFF10B981)) }
                        )
                        DropdownMenuItem(
                            text = { Text("💳 Renovar Membresía", color = Color.White) },
                            onClick = {
                                menuExpanded = false
                                onOpenRenew()
                            },
                            leadingIcon = { Icon(Icons.Default.AddCard, contentDescription = null, tint = Color(0xFFF59E0B)) }
                        )
                        DropdownMenuItem(
                            text = { Text("🗑️ Eliminar Conductor", color = Color(0xFFEF4444)) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444)) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFF334155).copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(10.dp))

            // Fila de estado y vencimiento
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Chip de Aprobación
                val (statusText, statusBg, statusColor) = when (driver.estadoAprobacion) {
                    "aprobado" -> Triple("Aprobado", Color(0x2210B981), Color(0xFF10B981))
                    "rechazado" -> Triple("Rechazado", Color(0x22EF4444), Color(0xFFEF4444))
                    "suscripcion_vencida" -> Triple("Vencido", Color(0x22F43F5E), Color(0xFFF43F5E))
                    else -> Triple("Pendiente", Color(0x22F59E0B), Color(0xFFF59E0B))
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusBg
                ) {
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Días restantes o fecha
                val expiryText = if (driver.fechaVencimientoSuscripcion.isNullOrBlank()) {
                    "Sin suscripción"
                } else if (driver.isSubscriptionExpired) {
                    "Expirado"
                } else {
                    "${driver.remainingDays} días restantes"
                }
                val expiryColor = if (driver.isSubscriptionExpired) Color(0xFFEF4444) else Color(0xFF10B981)

                Text(
                    text = expiryText,
                    color = expiryColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Botones de acción rápida
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (driver.estadoAprobacion == "pendiente") {
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
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Rechazar", fontSize = 12.sp)
                    }
                } else {
                    OutlinedButton(
                        onClick = onSendWhatsApp,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF10B981)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("WhatsApp", fontSize = 12.sp)
                    }

                    Button(
                        onClick = onOpenRenew,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.AddCard, contentDescription = null, tint = Color(0xFF0F172A), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Renovar", color = Color(0xFF0F172A), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun RenewSubscriptionBottomSheetContent(
    driver: AdminDriver,
    appConfig: AppConfig,
    onConfirm: (plan: String, amount: Double, method: String, reference: String) -> Unit,
    onCancel: () -> Unit
) {
    var selectedPlan by remember { mutableStateOf("semanal") }
    var paymentMethod by remember { mutableStateOf("deposito_bancario") }
    var amountText by remember { mutableStateOf(appConfig.priceSemanal.toString()) }
    var referenceText by remember { mutableStateOf("") }

    LaunchedEffect(selectedPlan) {
        amountText = when (selectedPlan) {
            "mensual" -> appConfig.priceMensual.toString()
            "quincenal" -> appConfig.priceQuincenal.toString()
            "diario" -> appConfig.priceDaily.toString()
            else -> appConfig.priceSemanal.toString()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            text = "Renovar Membresía",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "Conductor: ${driver.nombre} (${driver.displayIdentifier})",
            fontSize = 13.sp,
            color = Color(0xFF94A3B8)
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Selector de Plan
        Text("Selecciona el Plan:", fontSize = 13.sp, color = Color(0xFFCBD5E1), fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val plans = listOf(
                "semanal" to "Semanal\n(7d)",
                "quincenal" to "Quincenal\n(15d)",
                "mensual" to "Mensual\n(30d)"
            )
            for ((key, label) in plans) {
                val isSelected = selectedPlan == key
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) Color(0xFFF59E0B) else Color(0xFF334155))
                        .clickable { selectedPlan = key }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) Color(0xFF0F172A) else Color.White,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Monto a Cobrar
        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it },
            label = { Text("Monto cobrado (${appConfig.currencySymbol})", color = Color(0xFF94A3B8)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFFF59E0B),
                unfocusedBorderColor = Color(0xFF475569)
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Método de pago
        Text("Método de Pago:", fontSize = 13.sp, color = Color(0xFFCBD5E1))
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val methods = listOf(
                "deposito_bancario" to "Depósito",
                "transferencia" to "Transferencia",
                "efectivo" to "Efectivo"
            )
            for ((mKey, mLabel) in methods) {
                val isSelected = paymentMethod == mKey
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) Color(0x33F59E0B) else Color(0xFF334155))
                        .border(1.dp, if (isSelected) Color(0xFFF59E0B) else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable { paymentMethod = mKey }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mLabel,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) Color(0xFFF59E0B) else Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // No. de Boleta / Referencia
        OutlinedTextField(
            value = referenceText,
            onValueChange = { referenceText = it },
            label = { Text("No. Boleta o Referencia (Opcional)", color = Color(0xFF94A3B8)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFFF59E0B),
                unfocusedBorderColor = Color(0xFF475569)
            )
        )

        Spacer(modifier = Modifier.height(22.dp))

        // Botones de Confirmar / Cancelar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8))
            ) {
                Text("Cancelar")
            }

            Button(
                onClick = {
                    val amt = amountText.toDoubleOrNull() ?: 25.0
                    onConfirm(selectedPlan, amt, paymentMethod, referenceText)
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B))
            ) {
                Text("Confirmar", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
    }
}

@Composable
private fun filterChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = Color(0xFF1E293B),
    labelColor = Color(0xFF94A3B8),
    selectedContainerColor = Color(0xFFF59E0B),
    selectedLabelColor = Color(0xFF0F172A)
)

private fun openWhatsAppReminder(context: Context, driver: AdminDriver, config: AppConfig) {
    try {
        val phone = driver.telefono.replace(Regex("[^0-9]"), "")
        val formattedPhone = if (phone.startsWith("502")) phone else "502$phone"
        val message = """
            Hola ${driver.nombre.ifBlank { "Conductor" }}, te saludamos de la administración de *TucTuc Map*.
            
            Recordatorio sobre el servicio para tu unidad *${driver.displayIdentifier}*.
            Cuentas para depósito o transferencia:
            • ${config.bank1Name}: ${config.bank1Account}
            • ${config.bank2Name}: ${config.bank2Account}
            Titular: ${config.accountHolderName}
            
            Tarifas:
            • Semanal: ${config.currencySymbol} ${config.priceSemanal}
            • Mensual: ${config.currencySymbol} ${config.priceMensual}
            
            ¡Gracias por formar parte de la flota de TucTuc Map! 🛺
        """.trimIndent()

        val encoded = URLEncoder.encode(message, "UTF-8")
        val uri = Uri.parse("https://wa.me/$formattedPhone?text=$encoded")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "No se pudo abrir WhatsApp", Toast.LENGTH_SHORT).show()
    }
}

