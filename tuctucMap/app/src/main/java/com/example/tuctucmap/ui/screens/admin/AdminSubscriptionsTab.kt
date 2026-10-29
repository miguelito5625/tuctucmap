package com.example.tuctucmap.ui.screens.admin

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import kotlinx.coroutines.launch
import java.io.File
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

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
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${history.size} Registros Confirmados",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Button(
                            onClick = {
                                exportAndShareCsv(context, history, appConfig.currencySymbol)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Exportar CSV", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (history.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 50.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Aún no hay historial de pagos registrado", color = Color(0xFF94A3B8), fontSize = 14.sp)
                        }
                    }
                } else {
                    items(history, key = { it.id }) { item ->
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
            sb.append("\"${h.id}\",")
            sb.append("\"${h.driverName}\",")
            sb.append("\"${h.driverPlate}\",")
            sb.append("\"${h.planType}\",")
            sb.append("${h.amountPaid},")
            sb.append("\"${h.paymentMethod}\",")
            sb.append("\"${h.referenceNumber}\",")
            sb.append("\"$dateStr\",")
            sb.append("\"$endStr\",")
            sb.append("\"$status\"\n")
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

