package com.example.tuctucmap.ui.screens.driver

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tuctucmap.data.model.SubscriptionRenewalRequest
import com.example.tuctucmap.data.SecurePrefs
import com.example.tuctucmap.data.repository.SupabaseRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun DriverSubscriptionExpiredScreen(
    repository: SupabaseRepository,
    onRenewed: () -> Unit,
    onLogout: () -> Unit,
    onViewMap: () -> Unit = {},
    onApprovalRevoked: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val uid = repository.currentUserUid ?: ""
    var driverName by remember { mutableStateOf("") }
    var driverPlate by remember { mutableStateOf("") }
    var driverPhone by remember { mutableStateOf("") }
    var expiredDateText by remember { mutableStateOf("") }
    var appConfig by remember { mutableStateOf(SupabaseRepository.cachedAppConfig) }

    var boletaRefInput by remember { mutableStateOf("") }
    var selectedPlan by remember { mutableStateOf("semanal") }
    var isSubmittingBoleta by remember { mutableStateOf(false) }
    var requests by remember { mutableStateOf<List<SubscriptionRenewalRequest>>(emptyList()) }
    var isLoadingRequests by remember { mutableStateOf(false) }

    fun loadRequests() {
        coroutineScope.launch {
            isLoadingRequests = true
            val res = repository.getDriverRenewalRequests()
            requests = res.getOrDefault(emptyList())
            isLoadingRequests = false
        }
    }

    LaunchedEffect(Unit) {
        appConfig = repository.getAppConfig()
        loadRequests()
    }

    // Escuchar en tiempo real si el admin renueva la suscripción
    LaunchedEffect(uid) {
        if (uid.isNotBlank()) {
            repository.observeDriverProfile(uid).collectLatest { driver ->
                if (driver != null) {
                    driverName = driver.nombre
                    driverPlate = driver.displayIdentifier
                    driverPhone = driver.telefono

                    val expiryMs = driver.expiryTimestampMillis
                    if (expiryMs > 0L) {
                        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                        expiredDateText = sdf.format(java.util.Date(expiryMs))
                    }
                    SecurePrefs.saveDriverSubscriptionCache(
                        context,
                        driver.estadoAprobacion,
                        driver.subscriptionEndDate
                    )

                    when {
                        driver.estadoAprobacion in listOf("rechazado", "pendiente") -> {
                            onApprovalRevoked()
                        }
                        driver.isApprovedAndActive -> {
                            onRenewed()
                        }
                    }
                }
            }
        }
    }

    // Animación de pulso para el ícono de advertencia
    val infiniteTransition = rememberInfiniteTransition(label = "warningPulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "warningScale"
    )

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "$label copiado al portapapeles", Toast.LENGTH_SHORT).show()
    }

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
            ),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Ícono de advertencia latiendo con fondo rojo/naranja
            Card(
                modifier = Modifier
                    .size(90.dp)
                    .scale(scale)
                    .padding(4.dp),
                shape = RoundedCornerShape(45.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE53935))
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "⚠️", fontSize = 40.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Suscripción Vencida",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            if (driverName.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Hola, $driverName",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFF8A65),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Tu cuota de suscripción ha expirado. Para volver a aparecer en el mapa y recibir llamadas de pasajeros, renueva tu suscripción depositando tu cuota:",
                fontSize = 14.sp,
                color = Color.LightGray,
                textAlign = TextAlign.Center
            )

            if (expiredDateText.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Venció el: $expiredDateText",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFFFB74D),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Planes Disponibles (Dinámicos desde Supabase app_config) ──────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Planes de Suscripción",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("• Plan Semanal (7 días):", color = Color.LightGray, fontSize = 13.sp)
                        Text(
                            String.format(Locale.US, "Q %.2f", appConfig.priceSemanal),
                            color = Color(0xFFF9D423),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("• Plan Quincenal (15 días):", color = Color.LightGray, fontSize = 13.sp)
                        Text(
                            String.format(Locale.US, "Q %.2f", appConfig.priceQuincenal),
                            color = Color(0xFFF9D423),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("• Plan Mensual (30 días):", color = Color.LightGray, fontSize = 13.sp)
                        Text(
                            String.format(Locale.US, "Q %.2f", appConfig.priceMensual),
                            color = Color(0xFF4ADE80),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Cuentas Bancarias para Depósito ───────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0x22FFFFFF)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountBalance, contentDescription = null, tint = Color(0xFFF9D423), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Cuentas de Depósito / Transferencia",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    // Banrural
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Banrural Monetaria:", color = Color.LightGray, fontSize = 12.sp)
                            Text(appConfig.banruralAccount, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                        IconButton(onClick = { copyToClipboard("Cuenta Banrural", appConfig.banruralAccount.replace("-", "")) }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = Color(0xFFF9D423), modifier = Modifier.size(18.dp))
                        }
                    }

                    HorizontalDivider(color = Color(0x22FFFFFF), modifier = Modifier.padding(vertical = 4.dp))

                    // Banco Industrial
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Banco Industrial Monetaria:", color = Color.LightGray, fontSize = 12.sp)
                            Text(appConfig.biAccount, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                        IconButton(onClick = { copyToClipboard("Cuenta Banco Industrial", appConfig.biAccount.replace("-", "")) }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = Color(0xFFF9D423), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Registro Rápido de Número de Boleta en App ────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Registrar Número de Boleta",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    OutlinedTextField(
                        value = boletaRefInput,
                        onValueChange = { boletaRefInput = it },
                        label = { Text("Número de boleta o comprobante", fontSize = 12.sp) },
                        placeholder = { Text("Ej. 84920145") },
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("semanal" to "Semanal", "quincenal" to "Quincenal", "mensual" to "Mensual").forEach { (key, label) ->
                            FilterChip(
                                selected = selectedPlan == key,
                                onClick = { selectedPlan = key },
                                label = { Text(label, fontSize = 12.sp) }
                            )
                        }
                    }
                    if (boletaRefInput.isNotBlank()) {
                        Button(
                            onClick = {
                                isSubmittingBoleta = true
                                val amount = when (selectedPlan) {
                                    "quincenal" -> appConfig.priceQuincenal
                                    "mensual" -> appConfig.priceMensual
                                    else -> appConfig.priceSemanal
                                }
                                coroutineScope.launch {
                                    val res = repository.submitPaymentRequest(
                                        planType = selectedPlan,
                                        amount = amount,
                                        referenceNumber = boletaRefInput
                                    )
                                    isSubmittingBoleta = false
                                    res.onSuccess {
                                        Toast.makeText(context, "Boleta registrada. Ahora envía la foto por WhatsApp para activación inmediata.", Toast.LENGTH_LONG).show()
                                        loadRequests()
                                    }.onFailure { err ->
                                        Toast.makeText(context, "Error: ${err.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = !isSubmittingBoleta,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF9D423), contentColor = Color(0xFF0F2027))
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Notificar N° de Boleta en App", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ── Botón: Enviar Boleta por WhatsApp ─────────────────────────────
            Button(
                onClick = {
                    val refExtra = if (boletaRefInput.isNotBlank()) " (Boleta N°: ${boletaRefInput.trim()})" else ""
                    val message = "Hola Administrador de TucTucMap, soy el conductor $driverName ($driverPlate, Tel: $driverPhone). Acabo de realizar el depósito para renovar mi suscripción ($selectedPlan)$refExtra. Adjunto mi comprobante:"
                    val adminPhone = appConfig.adminWhatsappPhone.replace(Regex("[^0-9]"), "")
                    val waUrl = "https://api.whatsapp.com/send?phone=$adminPhone&text=${Uri.encode(message)}"
                    val waIntent = Intent(Intent.ACTION_VIEW, Uri.parse(waUrl))
                    context.startActivity(waIntent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF25D366),
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Default.Chat, contentDescription = null)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Enviar Boleta por WhatsApp",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Botón: Verificar si el Admin ya Activó ────────────────────────
            Button(
                onClick = {
                    coroutineScope.launch {
                        if (uid.isNotBlank()) {
                            repository.getDriverProfileOnce(uid) { d ->
                                if (d != null && d.isApprovedAndActive) {
                                    Toast.makeText(context, "¡Suscripción aprobada y activa!", Toast.LENGTH_SHORT).show()
                                    onRenewed()
                                } else {
                                    Toast.makeText(context, "Tu renovación sigue en revisión. Si enviaste tu captura por WhatsApp, el admin la validará pronto.", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                        loadRequests()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF10B981),
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Verificar Activación Ahora", fontWeight = FontWeight.Bold)
            }

            // ── Historial de Solicitudes de Renovación del Conductor ──────────
            if (requests.isNotEmpty()) {
                Spacer(modifier = Modifier.height(18.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Historial de Solicitudes",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "${requests.size} enviadas",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    requests.forEach { req ->
                        val statusText = when (req.status) {
                            "aprobado" -> "Aprobada"
                            "rechazado" -> "Rechazada"
                            else -> "Pendiente de Validación"
                        }
                        val statusColor = when (req.status) {
                            "aprobado" -> Color(0xFF4ADE80)
                            "rechazado" -> Color(0xFFF87171)
                            else -> Color(0xFFFBBF24)
                        }
                        val statusBg = when (req.status) {
                            "aprobado" -> Color(0x2210B981)
                            "rechazado" -> Color(0x22EF4444)
                            else -> Color(0x22F59E0B)
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF162936)),
                            border = BorderStroke(1.dp, Color(0x22FFFFFF))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Plan ${req.planType.replaceFirstChar { it.uppercase() }} • Q${req.amount.toInt()}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color.White
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = statusBg,
                                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
                                    ) {
                                        Text(
                                            text = statusText,
                                            fontSize = 11.sp,
                                            color = statusColor,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                if (req.referenceNumber.isNotBlank()) {
                                    Text(
                                        text = "Boleta: ${req.referenceNumber}",
                                        fontSize = 12.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }

                                if (req.status == "rechazado" && !req.rejectionReason.isNullOrBlank()) {
                                    Text(
                                        text = "Motivo de rechazo: ${req.rejectionReason}",
                                        fontSize = 11.sp,
                                        color = Color(0xFFFF8A80)
                                    )
                                } else if (req.status == "pendiente") {
                                    Text(
                                        text = "El admin revisará tu comprobante enviado por WhatsApp.",
                                        fontSize = 11.sp,
                                        color = Color(0xFFFBBF24)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Botón: Ver mapa de conductores activos (solo lectura)
            Button(
                onClick = onViewMap,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1E3A5F),
                    contentColor = Color.White
                )
            ) {
                Icon(imageVector = Icons.Default.Map, contentDescription = null)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Ver Conductores en el Mapa",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Botón de cerrar sesión
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE53935))
            ) {
                Icon(imageVector = Icons.Default.Logout, contentDescription = "Cerrar Sesión")
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Cerrar Sesión",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
