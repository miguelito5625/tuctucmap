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
import com.example.tuctucmap.data.model.DriverDossierDto
import com.example.tuctucmap.data.repository.AdminRepository
import com.example.tuctucmap.data.repository.SupabaseRepository
import com.example.tuctucmap.ui.components.DriverAvatar
import com.example.tuctucmap.ui.components.Vehicle360Viewer
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
    var selectedFilter by remember { mutableStateOf("todos") } // "todos", "pendientes", "aprobados", "por_vencer", "vencidos", "suspendidos"

    // Estado para BottomSheet de renovación manual
    var driverToRenew by remember { mutableStateOf<AdminDriver?>(null) }
    var showRenewSheet by remember { mutableStateOf(false) }

    // Estado para Ficha 360° / Expediente
    var driverToViewDossier by remember { mutableStateOf<AdminDriver?>(null) }
    var showDossierSheet by remember { mutableStateOf(false) }

    // Estado para Edición de Conductor
    var driverToEdit by remember { mutableStateOf<AdminDriver?>(null) }
    var isSavingEdit by remember { mutableStateOf(false) }

    // Estado para Suspensión / Reactivación
    var driverToToggleSuspend by remember { mutableStateOf<AdminDriver?>(null) }
    var isTogglingSuspend by remember { mutableStateOf(false) }

    // Estado para Aprobación de Conductor con Período de Prueba
    var driverToApprove by remember { mutableStateOf<AdminDriver?>(null) }
    var isApprovingDriver by remember { mutableStateOf(false) }

    // Estado para diálogo de confirmación de eliminación
    var driverToDelete by remember { mutableStateOf<AdminDriver?>(null) }
    var isDeletingDriver by remember { mutableStateOf(false) }
    var approvingDriverIds by remember { mutableStateOf(emptySet<String>()) }
    var rejectingDriverIds by remember { mutableStateOf(emptySet<String>()) }

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
            adminRepo.refreshPendingCounts()
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
                "suspendidos" -> d.estadoAprobacion == "suspendido"
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
                        contentPadding = PaddingValues(end = 16.dp),
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
                        item {
                            val suspendedCount = remember(drivers) { drivers.count { it.estadoAprobacion == "suspendido" } }
                            FilterChip(
                                selected = selectedFilter == "suspendidos",
                                onClick = { selectedFilter = "suspendidos" },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Suspendidos")
                                        if (suspendedCount > 0) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = Color(0xFFEA580C),
                                                shape = CircleShape
                                            ) {
                                                Text(
                                                    text = "$suspendedCount",
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
                            isApproving = approvingDriverIds.contains(driver.id) || (isApprovingDriver && driverToApprove?.id == driver.id),
                            isRejecting = rejectingDriverIds.contains(driver.id),
                            onOpenDossier = {
                                driverToViewDossier = driver
                                showDossierSheet = true
                            },
                            onEdit = {
                                driverToEdit = driver
                            },
                            onToggleSuspend = {
                                driverToToggleSuspend = driver
                            },
                            onApprove = {
                                driverToApprove = driver
                            },
                            onReject = {
                                val driverId = driver.id
                                rejectingDriverIds = rejectingDriverIds + driverId
                                scope.launch {
                                    try {
                                        val res = adminRepo.rejectDriver(driverId)
                                        if (res.isSuccess) {
                                            Toast.makeText(context, "Conductor rechazado", Toast.LENGTH_SHORT).show()
                                            loadData()
                                        } else {
                                            val err = res.exceptionOrNull()?.localizedMessage ?: "Error al rechazar"
                                            Toast.makeText(context, "Error: $err", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error: ${e.localizedMessage ?: "Error desconocido"}", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        rejectingDriverIds = rejectingDriverIds - driverId
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
        val renewSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                showRenewSheet = false
                driverToRenew = null
            },
            sheetState = renewSheetState,
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

    // ── BottomSheet de Ficha 360° / Expediente ──────────────────────────────
    if (showDossierSheet && driverToViewDossier != null) {
        val dossierSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                showDossierSheet = false
                driverToViewDossier = null
            },
            sheetState = dossierSheetState,
            containerColor = Color(0xFF1E293B)
        ) {
            DriverDossierBottomSheetContent(
                driver = driverToViewDossier!!,
                appConfig = appConfig,
                adminRepo = adminRepo,
                onEdit = {
                    val d = driverToViewDossier
                    showDossierSheet = false
                    driverToViewDossier = null
                    driverToEdit = d
                },
                onRenew = {
                    val d = driverToViewDossier
                    showDossierSheet = false
                    driverToViewDossier = null
                    driverToRenew = d
                    showRenewSheet = true
                },
                onWhatsApp = {
                    openWhatsAppReminder(context, driverToViewDossier!!, appConfig)
                },
                onClose = {
                    showDossierSheet = false
                    driverToViewDossier = null
                }
            )
        }
    }

    // ── Diálogo de Edición de Conductor ─────────────────────────────────────
    driverToEdit?.let { target ->
        EditDriverDialog(
            driver = target,
            onDismiss = { driverToEdit = null },
            onSave = { name, phone, unitNumber, plateNumber ->
                isSavingEdit = true
                scope.launch {
                    try {
                        val res = adminRepo.updateDriverDetails(
                            driverId = target.id,
                            name = name,
                            phone = phone,
                            unitNumber = unitNumber,
                            plateNumber = plateNumber
                        )
                        if (res.isSuccess) {
                            Toast.makeText(context, "Datos actualizados exitosamente", Toast.LENGTH_SHORT).show()
                            loadData()
                            driverToEdit = null
                        } else {
                            val err = res.exceptionOrNull()?.localizedMessage ?: "Error al actualizar"
                            Toast.makeText(context, "Error: $err", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error: ${e.localizedMessage ?: "Error desconocido"}", Toast.LENGTH_SHORT).show()
                    } finally {
                        isSavingEdit = false
                    }
                }
            },
            isSaving = isSavingEdit
        )
    }

    // ── Diálogo de Suspensión / Reactivación ─────────────────────────────────
    driverToToggleSuspend?.let { target ->
        val willSuspend = target.estadoAprobacion != "suspendido"
        SuspendToggleDialog(
            driver = target,
            onDismiss = { driverToToggleSuspend = null },
            onConfirm = {
                isTogglingSuspend = true
                scope.launch {
                    try {
                        val res = adminRepo.setDriverSuspended(target.id, suspend = willSuspend)
                        if (res.isSuccess) {
                            val msg = if (willSuspend) "Conductor suspendido operativamente" else "Conductor reactivado con éxito"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            loadData()
                            driverToToggleSuspend = null
                        } else {
                            val err = res.exceptionOrNull()?.localizedMessage ?: "Error al actualizar estado"
                            Toast.makeText(context, "Error: $err", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error: ${e.localizedMessage ?: "Error desconocido"}", Toast.LENGTH_SHORT).show()
                    } finally {
                        isTogglingSuspend = false
                    }
                }
            },
            isProcessing = isTogglingSuspend
        )
    }

    // ── Diálogo de Aprobación de Conductor y Período de Prueba ─────────────
    driverToApprove?.let { target ->
        ApproveDriverDialog(
            driver = target,
            onDismiss = { driverToApprove = null },
            onConfirm = { grantTrial, trialDays ->
                isApprovingDriver = true
                scope.launch {
                    try {
                        val res = adminRepo.approveDriver(
                            uid = target.id,
                            grantTrial = grantTrial,
                            trialDays = trialDays
                        )
                        if (res.isSuccess) {
                            val msg = if (grantTrial) {
                                "Conductor aprobado con $trialDays días de prueba gratuita"
                            } else {
                                "Conductor aprobado sin prueba (requiere membresía para operar)"
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            loadData()
                            driverToApprove = null
                        } else {
                            val err = res.exceptionOrNull()?.localizedMessage ?: "Error al aprobar conductor"
                            Toast.makeText(context, "Error: $err", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error: ${e.localizedMessage ?: "Error desconocido"}", Toast.LENGTH_SHORT).show()
                    } finally {
                        isApprovingDriver = false
                    }
                }
            },
            isProcessing = isApprovingDriver
        )
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
                        isDeletingDriver = true
                        scope.launch {
                            try {
                                val res = adminRepo.deleteDriver(id)
                                if (res.isSuccess) {
                                    Toast.makeText(context, "Conductor eliminado", Toast.LENGTH_SHORT).show()
                                    loadData()
                                    driverToDelete = null
                                } else {
                                    Toast.makeText(context, "Error al eliminar conductor", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.localizedMessage ?: "Error desconocido"}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isDeletingDriver = false
                            }
                        }
                    },
                    enabled = !isDeletingDriver,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    if (isDeletingDriver) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Eliminar", color = Color.White)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { driverToDelete = null },
                    enabled = !isDeletingDriver
                ) {
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
    isApproving: Boolean = false,
    isRejecting: Boolean = false,
    onOpenDossier: () -> Unit,
    onEdit: () -> Unit,
    onToggleSuspend: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onOpenRenew: () -> Unit,
    onSendWhatsApp: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        onClick = onOpenDossier,
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
                // Avatar con foto real del conductor o inicial con fallback
                DriverAvatar(
                    photoUrl = driver.driverPhotoUrl,
                    name = driver.nombre,
                    size = 48.dp
                )

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
                            text = { Text("Ficha 360° / Expediente", color = Color.White) },
                            onClick = {
                                menuExpanded = false
                                onOpenDossier()
                            },
                            leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, tint = Color(0xFFF59E0B)) }
                        )
                        DropdownMenuItem(
                            text = { Text("Contactar por WhatsApp", color = Color.White) },
                            onClick = {
                                menuExpanded = false
                                onSendWhatsApp()
                            },
                            leadingIcon = { Icon(Icons.Default.Chat, contentDescription = null, tint = Color(0xFF10B981)) }
                        )
                        DropdownMenuItem(
                            text = { Text("Editar Datos", color = Color.White) },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFF38BDF8)) }
                        )
                        DropdownMenuItem(
                            text = {
                                val label = if (driver.estadoAprobacion == "suspendido") "Reactivar Conductor" else "Suspender Conductor"
                                val textColor = if (driver.estadoAprobacion == "suspendido") Color(0xFF10B981) else Color(0xFFF97316)
                                Text(label, color = textColor)
                            },
                            onClick = {
                                menuExpanded = false
                                onToggleSuspend()
                            },
                            leadingIcon = {
                                if (driver.estadoAprobacion == "suspendido") {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF10B981))
                                } else {
                                    Icon(Icons.Default.Block, contentDescription = null, tint = Color(0xFFF97316))
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Renovar Membresía", color = Color.White) },
                            onClick = {
                                menuExpanded = false
                                onOpenRenew()
                            },
                            leadingIcon = { Icon(Icons.Default.AddCard, contentDescription = null, tint = Color(0xFFF59E0B)) }
                        )
                        DropdownMenuItem(
                            text = { Text("Eliminar Conductor", color = Color(0xFFEF4444)) },
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
                    "suspendido" -> Triple("Suspendido", Color(0x33EA580C), Color(0xFFF97316))
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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (driver.estadoAprobacion == "pendiente") {
                    val isActionRunning = isApproving || isRejecting

                    Button(
                        onClick = onApprove,
                        enabled = !isActionRunning,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        if (isApproving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Aprobar",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onReject,
                        enabled = !isActionRunning,
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0x14EF4444),
                            contentColor = Color(0xFFEF4444)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        if (isRejecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color(0xFFEF4444),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Rechazar",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = onSendWhatsApp,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF10B981)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "WhatsApp",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Button(
                        onClick = onOpenRenew,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.AddCard, contentDescription = null, tint = Color(0xFF0F172A), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Renovar",
                            color = Color(0xFF0F172A),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
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
    var isConfirming by remember { mutableStateOf(false) }

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
                    isConfirming = true
                    onConfirm(selectedPlan, amt, paymentMethod, referenceText)
                },
                enabled = !isConfirming,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B))
            ) {
                if (isConfirming) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(0xFF0F172A), strokeWidth = 2.dp)
                } else {
                    Text("Confirmar", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                }
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

@Composable
private fun EditDriverDialog(
    driver: AdminDriver,
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String, unitNumber: String, plateNumber: String) -> Unit,
    isSaving: Boolean
) {
    var name by remember { mutableStateOf(driver.nombre) }
    var phone by remember { mutableStateOf(driver.telefono) }
    var unitNumber by remember { mutableStateOf(driver.numeroUnidad) }
    var plateNumber by remember { mutableStateOf(driver.placas) }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = {
            Text(
                text = "Editar Datos del Conductor",
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre Completo") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFF59E0B),
                        unfocusedBorderColor = Color(0xFF475569)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Teléfono") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFF59E0B),
                        unfocusedBorderColor = Color(0xFF475569)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = unitNumber,
                    onValueChange = { unitNumber = it },
                    label = { Text("Número de Unidad") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFF59E0B),
                        unfocusedBorderColor = Color(0xFF475569)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = plateNumber,
                    onValueChange = { plateNumber = it },
                    label = { Text("Número de Placas") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFF59E0B),
                        unfocusedBorderColor = Color(0xFF475569)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, phone, unitNumber, plateNumber) },
                enabled = !isSaving && name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B))
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF0F172A), strokeWidth = 2.dp)
                } else {
                    Text("Guardar Cambios", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("Cancelar", color = Color(0xFF94A3B8))
            }
        },
        containerColor = Color(0xFF1E293B)
    )
}

@Composable
private fun SuspendToggleDialog(
    driver: AdminDriver,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    isProcessing: Boolean
) {
    val isSuspended = driver.estadoAprobacion == "suspendido"
    val title = if (isSuspended) "¿Reactivar Conductor?" else "¿Suspender Conductor?"
    val actionText = if (isSuspended) "Reactivar" else "Suspender"
    val color = if (isSuspended) Color(0xFF10B981) else Color(0xFFF97316)
    val body = if (isSuspended) {
        "¿Deseas reactivar operativamente a ${driver.nombre.ifBlank { "este conductor" }} (${driver.displayIdentifier})? Volverá a estar activo para conectarse al servicio."
    } else {
        "¿Deseas suspender temporalmente a ${driver.nombre.ifBlank { "este conductor" }} (${driver.displayIdentifier})? No podrá recibir viajes mientras permanezca suspendido."
    }

    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        title = { Text(title, fontWeight = FontWeight.Bold, color = Color.White) },
        text = { Text(body, color = Color(0xFFCBD5E1)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isProcessing,
                colors = ButtonDefaults.buttonColors(containerColor = color)
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text(actionText, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isProcessing) {
                Text("Cancelar", color = Color(0xFF94A3B8))
            }
        },
        containerColor = Color(0xFF1E293B)
    )
}

@Composable
private fun ApproveDriverDialog(
    driver: AdminDriver,
    onDismiss: () -> Unit,
    onConfirm: (grantTrial: Boolean, trialDays: Int) -> Unit,
    isProcessing: Boolean
) {
    var grantTrial by remember { mutableStateOf(true) }
    var trialDaysText by remember { mutableStateOf("7") }
    val quickDays = listOf(3, 7, 15, 30)

    val trialDays = trialDaysText.toIntOrNull() ?: 0
    val isFormValid = !grantTrial || trialDays > 0

    val expiryPreview = remember(grantTrial, trialDays) {
        if (!grantTrial || trialDays <= 0) null
        else {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, trialDays)
            val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            fmt.format(cal.time)
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF10B981)
                )
                Text(
                    text = "Aprobar Conductor",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Tarjeta de Identificación del Conductor
                Surface(
                    color = Color(0xFF0F172A),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF334155)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B)
                            )
                        }
                        Column {
                            Text(
                                text = driver.nombre.ifBlank { "Sin nombre" },
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "${driver.displayIdentifier} • 📞 ${driver.telefono.ifBlank { "Sin teléfono" }}",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // Switch de Período de Prueba
                Surface(
                    color = Color(0xFF0F172A).copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "Asignar período de prueba",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (grantTrial) "El conductor tendrá acceso inmediato sin costo inicial" else "El conductor requerirá abonar membresía antes de operar",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                        }
                        Switch(
                            checked = grantTrial,
                            onCheckedChange = { grantTrial = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF10B981),
                                checkedTrackColor = Color(0xFF10B981).copy(alpha = 0.3f),
                                uncheckedThumbColor = Color(0xFF64748B),
                                uncheckedTrackColor = Color(0xFF1E293B)
                            )
                        )
                    }
                }

                if (grantTrial) {
                    // Selección de días
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Duración del período de prueba",
                            color = Color(0xFFCBD5E1),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )

                        // Chips rápidos
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            quickDays.forEach { days ->
                                val isSelected = trialDaysText == days.toString()
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { trialDaysText = days.toString() },
                                    color = if (isSelected) Color(0xFF10B981) else Color(0xFF0F172A),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSelected) Color(0xFF10B981) else Color(0xFF334155)
                                    )
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "$days d",
                                            color = if (isSelected) Color(0xFF0F172A) else Color.White,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Input manual de días
                        OutlinedTextField(
                            value = trialDaysText,
                            onValueChange = { input ->
                                if (input.all { it.isDigit() } && input.length <= 3) {
                                    trialDaysText = input
                                }
                            },
                            label = { Text("Cantidad de días") },
                            suffix = { Text("días", color = Color(0xFF94A3B8)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF10B981),
                                unfocusedBorderColor = Color(0xFF475569)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Preview de vigencia
                        if (trialDays > 0 && expiryPreview != null) {
                            Surface(
                                color = Color(0x1A10B981),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x3310B981)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Event,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Vigencia hasta: $expiryPreview",
                                            color = Color(0xFF10B981),
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = "Al término, requerirá membresía activa.",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "Ingresa un número de días válido mayor a 0.",
                                color = Color(0xFFEF4444),
                                fontSize = 11.sp
                            )
                        }
                    }
                } else {
                    // Advertencia de aprobación sin período de prueba
                    Surface(
                        color = Color(0x1AF59E0B),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33F59E0B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(18.dp).padding(top = 2.dp)
                            )
                            Column {
                                Text(
                                    text = "Aprobación sin prueba",
                                    color = Color(0xFFF59E0B),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "El conductor será aprobado pero figurará 'Sin suscripción'. No podrá conectarse hasta registrar su primer pago.",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(grantTrial, trialDays) },
                enabled = !isProcessing && isFormValid,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (grantTrial) "Aprobar ($trialDays d)" else "Aprobar sin prueba",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isProcessing) {
                Text("Cancelar", color = Color(0xFF94A3B8))
            }
        },
        containerColor = Color(0xFF1E293B)
    )
}

@Composable
private fun DriverDossierBottomSheetContent(
    driver: AdminDriver,
    appConfig: AppConfig,
    adminRepo: AdminRepository,
    onEdit: () -> Unit,
    onRenew: () -> Unit,
    onWhatsApp: () -> Unit,
    onClose: () -> Unit
) {
    var dossier by remember { mutableStateOf<DriverDossierDto?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(driver.id) {
        isLoading = true
        val res = adminRepo.fetchDriverDossier(driver.id)
        if (res.isSuccess) {
            dossier = res.getOrNull()
        }
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Cabecera con avatar y nombre
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            DriverAvatar(
                photoUrl = driver.driverPhotoUrl,
                name = driver.nombre,
                size = 58.dp
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = driver.nombre.ifBlank { "Sin nombre" },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${driver.displayIdentifier} • 📞 ${driver.telefono}",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8)
                )
                if (driver.placas.isNotBlank()) {
                    Text(
                        text = "Placas: ${driver.placas}",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }

            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color(0xFF94A3B8))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Tarjeta de Inspección Visual 360° del Vehículo y Conductor ───────
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🛺 Inspección Visual 360°",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF9D423)
                    )
                    val photoCount = driver.vehiclePhotos360?.size ?: 0
                    Surface(
                        color = if (photoCount == 8) Color(0x3310B981) else Color(0x33F97316),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (photoCount == 8) "8/8 Ángulos Verificados" else "$photoCount/8 Ángulos",
                            color = if (photoCount == 8) Color(0xFF10B981) else Color(0xFFF97316),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Arrastra el dedo horizontalmente para girar y verificar el estado del mototaxi:",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )

                Spacer(modifier = Modifier.height(10.dp))
                Vehicle360Viewer(
                    imageUrls = driver.vehiclePhotos360 ?: emptyList(),
                    autoSpinOnStart = false,
                    modifier = Modifier.height(230.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFFF59E0B))
            }
        } else {
            val d = dossier
            if (d == null) {
                Text("No se pudo cargar el expediente completo", color = Color(0xFFEF4444), fontSize = 13.sp)
            } else {
                // Métricas clave (KPIs)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Total Aportado", fontSize = 11.sp, color = Color(0xFF94A3B8))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "${appConfig.currencySymbol} ${String.format(Locale.US, "%.0f", d.totalPaidSubscriptions)}",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                            Text("${d.subscriptionsCount} pagos", fontSize = 10.sp, color = Color(0xFF64748B))
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Viajes Completados", fontSize = 11.sp, color = Color(0xFF94A3B8))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "${d.totalTripsCompleted}",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8)
                            )
                            Text("Cobrado: ${appConfig.currencySymbol} ${String.format(Locale.US, "%.0f", d.totalFaresEarned)}", fontSize = 10.sp, color = Color(0xFF64748B))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Estado de membresía
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Estado de Membresía Actual", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFF59E0B))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Plan actual:", fontSize = 13.sp, color = Color(0xFF94A3B8))
                            val currentPlan = d.subscriptions.firstOrNull()?.planType ?: "semanal"
                            Text(currentPlan.replaceFirstChar { it.uppercase() }, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Vence el:", fontSize = 13.sp, color = Color(0xFF94A3B8))
                            Text(
                                d.driver.fechaVencimientoSuscripcion?.take(10) ?: "Sin fecha",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (d.driver.isSubscriptionExpired) Color(0xFFEF4444) else Color.White
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Estado Operativo:", fontSize = 13.sp, color = Color(0xFF94A3B8))
                            Text(
                                d.driver.estadoAprobacion.replaceFirstChar { it.uppercase() },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (d.driver.estadoAprobacion) {
                                    "aprobado" -> Color(0xFF10B981)
                                    "suspendido" -> Color(0xFFF97316)
                                    else -> Color(0xFFEF4444)
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Historial de suscripciones recientes
                Text("Historial de Pagos y Renovaciones", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(6.dp))

                if (d.subscriptions.isEmpty()) {
                    Text("No hay historial de suscripciones registrado", color = Color(0xFF64748B), fontSize = 12.sp)
                } else {
                    for (sub in d.subscriptions.take(5)) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "${sub.planType.replaceFirstChar { it.uppercase() }} • ${sub.paymentMethod.ifBlank { "depósito" }}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Vence: ${sub.endDate.take(10).ifBlank { sub.startDate.take(10) }}",
                                        fontSize = 11.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                                Text(
                                    text = "${appConfig.currencySymbol} ${String.format(Locale.US, "%.0f", sub.amountPaid)}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Botones de acción rápida
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onWhatsApp,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF10B981)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("WhatsApp", fontSize = 11.5.sp, maxLines = 1, softWrap = false, fontWeight = FontWeight.SemiBold)
            }

            OutlinedButton(
                onClick = onEdit,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Editar", fontSize = 11.5.sp, maxLines = 1, softWrap = false, fontWeight = FontWeight.SemiBold)
            }

            Button(
                onClick = onRenew,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.AddCard, contentDescription = null, tint = Color(0xFF0F172A), modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Renovar", color = Color(0xFF0F172A), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

