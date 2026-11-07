package com.example.tuctucmap.ui.screens.driver

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Map
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
import com.example.tuctucmap.data.model.AppConfig
import com.example.tuctucmap.data.repository.SupabaseRepository
import kotlinx.coroutines.flow.collectLatest

@Composable
fun DriverPendingScreen(
    repository: SupabaseRepository,
    onApproved: () -> Unit,
    onLogout: () -> Unit,
    onSubscriptionExpired: () -> Unit = {}
) {
    val context = LocalContext.current
    val uid = repository.currentUserUid ?: ""
    var driverName by remember { mutableStateOf("") }
    var appConfig by remember { mutableStateOf(SupabaseRepository.cachedAppConfig) }

    // Consultar siempre la configuración comercial más reciente desde la base de datos
    LaunchedEffect(Unit) {
        val latestConfig = repository.getAppConfig(force = true)
        appConfig = latestConfig
    }

    // Escuchar el perfil del conductor en tiempo real
    LaunchedEffect(uid) {
        if (uid.isNotBlank()) {
            repository.observeDriverProfile(uid).collectLatest { driver ->
                if (driver != null) {
                    driverName = driver.nombre
                    com.example.tuctucmap.data.SecurePrefs.saveDriverSubscriptionCache(
                        context,
                        driver.estadoAprobacion,
                        driver.subscriptionEndDate
                    )
                    when {
                        driver.isSubscriptionExpired -> onSubscriptionExpired()
                        driver.estadoAprobacion == "aprobado" -> onApproved()
                    }
                }
            }
        }
    }

    // Animación de pulso para el indicador de revisión
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

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
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Ícono de reloj/revisión latiendo
            Card(
                modifier = Modifier
                    .size(100.dp)
                    .scale(scale)
                    .padding(8.dp),
                shape = RoundedCornerShape(50.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF9D423))
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "⏳", fontSize = 44.sp)
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            Text(
                text = "Cuenta en Revisión",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (driverName.isNotBlank()) {
                Text(
                    text = "Hola, $driverName",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFF9D423),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Text(
                text = "Tu solicitud ha sido recibida con éxito. Por motivos de seguridad, un administrador debe verificar y aprobar tu cuenta desde la consola antes de poder ingresar.",
                fontSize = 15.sp,
                color = Color.LightGray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0x22FFFFFF)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFFF9D423)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Esta pantalla se actualizará automáticamente tan pronto como seas aprobado.",
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Botón para contactar al Administrador por WhatsApp
            Button(
                onClick = {
                    val driverNameForMsg = driverName.ifBlank { "Conductor" }
                    val message = "Hola Administrador de TucTuc Map, soy $driverNameForMsg. Acabo de registrarme en la app y mi cuenta de conductor se encuentra en revisión. Solicito amablemente la validación y aprobación de mi cuenta para poder iniciar labores."
                    val targetPhone = appConfig.adminWhatsappPhone.ifBlank { appConfig.adminWhatsapp }
                    openAdminWhatsApp(context, targetPhone, message)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF25D366),
                    contentColor = Color.White
                )
            ) {
                Text(text = "💬", fontSize = 18.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Contactar al Administrador",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Botón para cerrar sesión
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = BorderStroke(1.dp, Color.White)
            ) {
                Icon(imageVector = Icons.Default.Logout, contentDescription = "Cerrar Sesión")
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Cancelar / Cerrar Sesión",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

private fun openAdminWhatsApp(context: Context, phone: String, message: String) {
    val digits = phone.replace(Regex("[^0-9]"), "")
    if (digits.isBlank()) {
        Toast.makeText(context, "No se encontró el teléfono del administrador configurado.", Toast.LENGTH_SHORT).show()
        return
    }
    val formattedPhone = if (digits.length == 8) "502$digits" else digits
    try {
        val url = "https://api.whatsapp.com/send?phone=$formattedPhone&text=${Uri.encode(message)}"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            setPackage("com.whatsapp")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            val fallbackUrl = "https://wa.me/$formattedPhone?text=${Uri.encode(message)}"
            val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallbackIntent)
        } catch (_: Exception) {
            Toast.makeText(context, "No se pudo abrir WhatsApp en este dispositivo.", Toast.LENGTH_SHORT).show()
        }
    }
}

