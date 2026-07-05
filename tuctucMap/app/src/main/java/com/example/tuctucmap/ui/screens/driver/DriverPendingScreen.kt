package com.example.tuctucmap.ui.screens.driver

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tuctucmap.data.repository.SupabaseRepository
import kotlinx.coroutines.flow.collectLatest

@Composable
fun DriverPendingScreen(
    repository: SupabaseRepository,
    onApproved: () -> Unit,
    onLogout: () -> Unit,
    onViewMap: () -> Unit = {},
    onSubscriptionExpired: () -> Unit = {}
) {
    val uid = repository.currentUserUid ?: ""
    var driverName by remember { mutableStateOf("") }

    // Escuchar el perfil del conductor en tiempo real
    LaunchedEffect(uid) {
        if (uid.isNotBlank()) {
            repository.observeDriverProfile(uid).collectLatest { driver ->
                if (driver != null) {
                    driverName = driver.nombre
                    when (driver.estadoAprobacion) {
                        "aprobado" -> onApproved()
                        "suscripcion_vencida" -> onSubscriptionExpired()
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

            Spacer(modifier = Modifier.height(32.dp))

            // Botón: Ver mapa de conductores activos
            Button(
                onClick = onViewMap,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
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
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

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
