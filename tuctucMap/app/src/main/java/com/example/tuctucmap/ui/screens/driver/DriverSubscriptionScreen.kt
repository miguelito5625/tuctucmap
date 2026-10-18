package com.example.tuctucmap.ui.screens.driver

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tuctucmap.data.model.AppConfig
import com.example.tuctucmap.data.model.Driver
import com.example.tuctucmap.data.model.SubscriptionRenewalRequest
import com.example.tuctucmap.data.repository.SupabaseRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverSubscriptionScreen(
    repository: SupabaseRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val uid = repository.currentUserUid ?: ""

    var driver by remember { mutableStateOf<Driver?>(null) }
    var appConfig by remember { mutableStateOf(SupabaseRepository.cachedAppConfig) }
    var requests by remember { mutableStateOf<List<SubscriptionRenewalRequest>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSubmitting by remember { mutableStateOf(false) }

    var selectedPlan by remember { mutableStateOf("semanal") }
    var referenceInput by remember { mutableStateOf("") }

    fun loadData() {
        coroutineScope.launch {
            isLoading = true
            appConfig = repository.getAppConfig()
            if (uid.isNotBlank()) {
                repository.getDriverProfileOnce(uid) { d ->
                    driver = d
                }
                val res = repository.getDriverRenewalRequests()
                requests = res.getOrDefault(emptyList())
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadData()
    }

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "$label copiado al portapapeles", Toast.LENGTH_SHORT).show()
    }

    fun openWhatsApp(req: SubscriptionRenewalRequest? = null) {
        val dName = driver?.nombre ?: "Conductor"
        val dPlate = driver?.displayIdentifier ?: ""
        val plan = req?.planType ?: selectedPlan
        val ref = req?.referenceNumber ?: referenceInput.trim()
        val refExtra = if (ref.isNotBlank()) " (Comprobante / Boleta: $ref)" else ""

        val message = "Hola Administrador de TucTucMap, soy el conductor $dName ($dPlate). Solicito la renovación de mi suscripción ($plan)$refExtra. Adjunto mi captura de pantalla del comprobante de pago:"
        val adminPhone = appConfig.adminWhatsappPhone.replace(Regex("[^0-9]"), "")
        val waUrl = "https://api.whatsapp.com/send?phone=$adminPhone&text=${Uri.encode(message)}"
        val waIntent = Intent(Intent.ACTION_VIEW, Uri.parse(waUrl))
        try {
            context.startActivity(waIntent)
        } catch (_: Exception) {
            Toast.makeText(context, "No se pudo abrir WhatsApp", Toast.LENGTH_SHORT).show()
        }
    }

    fun submitRequest() {
        if (isSubmitting) return
        isSubmitting = true
        val amount = when (selectedPlan) {
            "quincenal" -> appConfig.priceQuincenal
            "mensual" -> appConfig.priceMensual
            else -> appConfig.priceSemanal
        }

        coroutineScope.launch {
            val res = repository.submitPaymentRequest(
                planType = selectedPlan,
                amount = amount,
                paymentMethod = "deposito_bancario",
                referenceNumber = referenceInput.trim()
            )
            isSubmitting = false
            res.onSuccess {
                Toast.makeText(context, "¡Solicitud registrada! Ahora envía tu captura por WhatsApp para validación.", Toast.LENGTH_LONG).show()
                openWhatsApp()
                referenceInput = ""
                // Refrescar historial de solicitudes
                val reqs = repository.getDriverRenewalRequests()
                requests = reqs.getOrDefault(emptyList())
            }.onFailure { err ->
                Toast.makeText(context, "Error: ${err.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Mi Suscripción",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 19.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { loadData() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Actualizar",
                            tint = Color(0xFFF9D423)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F2027)
                )
            )
        },
        containerColor = Color(0xFF0F2027)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0F2027),
                            Color(0xFF1B323D),
                            Color(0xFF2C5364)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // ── 1. Tarjeta de Estado de Suscripción ─────────────────────────
                val isExpired = driver?.isSubscriptionExpired == true
                val expiryMs = driver?.expiryTimestampMillis ?: 0L
                val sdf = SimpleDateFormat("dd 'de' MMMM, yyyy • HH:mm", Locale("es", "GT"))
                val expiryFormatted = if (expiryMs > 0L) sdf.format(Date(expiryMs)) else "Sin fecha asignada"

                val nowMs = System.currentTimeMillis()
                val diffDays = if (expiryMs > nowMs) {
                    ((expiryMs - nowMs) / (1000 * 60 * 60 * 24)).toInt()
                } else 0

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isExpired) Color(0x33EF4444) else Color(0x2210B981)
                    ),
                    border = BorderStroke(
                        1.5.dp,
                        if (isExpired) Color(0x88EF4444) else Color(0x6610B981)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isExpired) Color(0xFFEF4444) else Color(0xFF10B981)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isExpired) Icons.Default.Warning else Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = if (isExpired) "Suscripción Vencida" else "Suscripción Activa",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isExpired) Color(0xFFFF8A80) else Color(0xFFA7F3D0)
                                )
                                Text(
                                    text = if (isExpired) "Debes renovar para recibir viajes" else "Vigencia hasta: $expiryFormatted",
                                    fontSize = 13.sp,
                                    color = Color(0xFFE2E8F0)
                                )
                            }
                        }

                        if (!isExpired && diffDays in 0..7) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0x33F59E0B),
                                border = BorderStroke(1.dp, Color(0x66F59E0B))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.HourglassTop,
                                        contentDescription = null,
                                        tint = Color(0xFFFBBF24),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (diffDays == 0) "¡Tu suscripción vence hoy! Renueva con tiempo." else "Te quedan $diffDays días de vigencia. Puedes renovar por adelantado.",
                                        fontSize = 12.sp,
                                        color = Color(0xFFFBBF24),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                // ── 2. Sección: Solicitar Renovación ───────────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF162936)),
                    border = BorderStroke(1.dp, Color(0x33FFFFFF))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.AddCard, contentDescription = null, tint = Color(0xFFF9D423))
                            Text(
                                text = "Solicitar Renovación",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Text(
                            text = "Selecciona tu plan, realiza tu depósito o transferencia y notifica al administrador para activar tu cuenta:",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
                            lineHeight = 16.sp
                        )

                        // Selector de Planes
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                Triple("semanal", "Semanal", "${appConfig.currencySymbol}${appConfig.priceSemanal.toInt()} (7d)"),
                                Triple("quincenal", "Quincenal", "${appConfig.currencySymbol}${appConfig.priceQuincenal.toInt()} (15d)"),
                                Triple("mensual", "Mensual", "${appConfig.currencySymbol}${appConfig.priceMensual.toInt()} (30d)")
                            ).forEach { (key, label, price) ->
                                val isSelected = selectedPlan == key
                                Surface(
                                    onClick = { selectedPlan = key },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) Color(0xFFF9D423) else Color(0xFF0F2027),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) Color(0xFFF9D423) else Color(0x44FFFFFF)
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color(0xFF0F2027) else Color.White
                                        )
                                        Text(
                                            text = price,
                                            fontSize = 11.sp,
                                            color = if (isSelected) Color(0xFF0F2027) else Color(0xFF94A3B8)
                                        )
                                    }
                                }
                            }
                        }

                        // Cuentas Bancarias
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0F2027), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (appConfig.accountHolderName.isNotBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Titular: ",
                                        fontSize = 11.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                    Text(
                                        text = appConfig.accountHolderName,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFF1F5F9)
                                    )
                                }
                                HorizontalDivider(color = Color(0x22FFFFFF))
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("${appConfig.bank1Name}:", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                    Text(appConfig.bank1Account, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                                }
                                IconButton(
                                    onClick = { copyToClipboard(appConfig.bank1Name, appConfig.bank1Account) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                                }
                            }

                            if (appConfig.bank2Account.isNotBlank()) {
                                HorizontalDivider(color = Color(0x22FFFFFF))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("${appConfig.bank2Name}:", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                        Text(appConfig.bank2Account, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                                    }
                                    IconButton(
                                        onClick = { copyToClipboard(appConfig.bank2Name, appConfig.bank2Account) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        // Campo N° de Boleta o Referencia (Opcional)
                        OutlinedTextField(
                            value = referenceInput,
                            onValueChange = { referenceInput = it },
                            label = { Text("No. de Boleta o Comprobante (Opcional)") },
                            placeholder = { Text("Ej. 98451203") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFF9D423),
                                unfocusedBorderColor = Color(0x55FFFFFF),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedLabelColor = Color(0xFFF9D423),
                                unfocusedLabelColor = Color.LightGray
                            )
                        )

                        // Botón de Enviar Solicitud
                        Button(
                            onClick = { submitRequest() },
                            enabled = !isSubmitting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFF9D423),
                                contentColor = Color(0xFF0F2027)
                            )
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF0F2027))
                            } else {
                                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Registrar Solicitud y Enviar Foto", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // ── 3. Sección: Historial de Solicitudes de Renovación ──────────
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.History, contentDescription = null, tint = Color(0xFF38BDF8))
                            Text(
                                text = "Mis Solicitudes de Renovación",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        if (requests.isNotEmpty()) {
                            Text(
                                text = "${requests.size} registradas",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color(0xFFF9D423))
                        }
                    } else if (requests.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0x22FFFFFF))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.ReceiptLong,
                                    contentDescription = null,
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = "No has enviado solicitudes aún.",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Cuando solicites renovar, aquí verás el estado de validación del administrador en tiempo real.",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        requests.forEach { req ->
                            RenewalRequestItem(
                                request = req,
                                onChatWhatsApp = { openWhatsApp(req) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun RenewalRequestItem(
    request: SubscriptionRenewalRequest,
    onChatWhatsApp: () -> Unit
) {
    val reqDateStr = remember(request.createdAt) {
        val ms = request.createdAtEpoch
        if (ms > 0L) {
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(ms))
        } else ""
    }

    val (statusLabel, statusColor, statusBg, statusIcon) = when (request.status) {
        "aprobado" -> Quadruple(
            "Aprobada",
            Color(0xFF4ADE80),
            Color(0x2210B981),
            Icons.Default.CheckCircle
        )
        "rechazado" -> Quadruple(
            "Rechazada",
            Color(0xFFF87171),
            Color(0x22EF4444),
            Icons.Default.Cancel
        )
        else -> Quadruple(
            "Pendiente de Revisión",
            Color(0xFFFBBF24),
            Color(0x22F59E0B),
            Icons.Default.HourglassTop
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF13222B)),
        border = BorderStroke(1.dp, Color(0x22FFFFFF))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Plan ${request.planType.replaceFirstChar { it.uppercase() }}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (reqDateStr.isNotBlank()) {
                        Text(
                            text = reqDateStr,
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = statusBg,
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(14.dp))
                        Text(text = statusLabel, fontSize = 11.sp, color = statusColor, fontWeight = FontWeight.Bold)
                    }
                }
            }

            HorizontalDivider(color = Color(0x15FFFFFF))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Monto: Q${String.format(Locale.US, "%.2f", request.amount)}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF38BDF8)
                    )
                    if (request.referenceNumber.isNotBlank()) {
                        Text(
                            text = "Boleta: ${request.referenceNumber}",
                            fontSize = 12.sp,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }

                if (request.status != "aprobado") {
                    Button(
                        onClick = onChatWhatsApp,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0x2225D366),
                            contentColor = Color(0xFF25D366)
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        border = BorderStroke(1.dp, Color(0x6625D366))
                    ) {
                        Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("WhatsApp", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Si está pendiente, mensaje informativo
            if (request.status == "pendiente") {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0x15FBBF24),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "El administrador está validando tu comprobante por WhatsApp para activar tu renovación.",
                        fontSize = 11.sp,
                        color = Color(0xFFFBBF24),
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            // Si fue rechazada, mostrar motivo
            if (request.status == "rechazado" && !request.rejectionReason.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0x22EF4444),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFF87171), modifier = Modifier.size(16.dp))
                        Text(
                            text = "Motivo: ${request.rejectionReason}",
                            fontSize = 11.sp,
                            color = Color(0xFFFF8A80)
                        )
                    }
                }
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
