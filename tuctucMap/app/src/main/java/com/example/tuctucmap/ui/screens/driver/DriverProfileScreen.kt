package com.example.tuctucmap.ui.screens.driver

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tuctucmap.data.model.Driver
import com.example.tuctucmap.data.model.parseIsoTimestamp
import com.example.tuctucmap.data.repository.SupabaseRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverProfileScreen(
    repository: SupabaseRepository,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val uid = repository.currentUserUid ?: ""
    val authEmail = repository.currentUserEmail ?: ""

    var driverProfile by remember { mutableStateOf<Driver?>(null) }
    var isLoadingProfile by remember { mutableStateOf(true) }

    // Formulario de datos
    var nombre by remember { mutableStateOf("") }
    var telefono by remember { mutableStateOf("") }
    var placas by remember { mutableStateOf("") }
    var numeroUnidad by remember { mutableStateOf("") }
    var isSavingData by remember { mutableStateOf(false) }

    // Formulario de contraseña
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isNewPasswordVisible by remember { mutableStateOf(false) }
    var isConfirmPasswordVisible by remember { mutableStateOf(false) }
    var isUpdatingPassword by remember { mutableStateOf(false) }

    // Cargar perfil del conductor en tiempo real
    LaunchedEffect(uid) {
        if (uid.isNotBlank()) {
            repository.observeDriverProfile(uid).collectLatest { driver ->
                driverProfile = driver
                if (driver != null && nombre.isEmpty() && telefono.isEmpty() && placas.isEmpty()) {
                    nombre = driver.nombre
                    telefono = driver.telefono
                    placas = driver.placas
                    numeroUnidad = driver.numeroUnidad
                }
                isLoadingProfile = false
            }
        } else {
            isLoadingProfile = false
        }
    }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedBorderColor = Color(0xFFF9D423),
        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
        focusedLabelColor = Color(0xFFF9D423),
        unfocusedLabelColor = Color(0xFFB0BEC5),
        cursorColor = Color(0xFFF9D423),
        focusedLeadingIconColor = Color(0xFFF9D423),
        unfocusedLeadingIconColor = Color(0xFFB0BEC5),
        focusedTrailingIconColor = Color(0xFFF9D423),
        unfocusedTrailingIconColor = Color(0xFFB0BEC5)
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Mi Perfil de Conductor",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F2027)
                )
            )
        }
    ) { padding ->
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
                .padding(padding)
        ) {
            if (isLoadingProfile) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFFF9D423))
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // ── Card Superior de Resumen ─────────────────────────────
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0x22FFFFFF))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                val initial = (driverProfile?.nombre?.trim()?.firstOrNull() ?: 'C')
                                    .uppercaseChar().toString()
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .background(
                                            brush = Brush.linearGradient(
                                                listOf(Color(0xFFF9D423), Color(0xFFFFB300))
                                            )
                                        )
                                        .border(2.dp, Color.White.copy(alpha = 0.7f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = initial,
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF0F2027)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = driverProfile?.nombre?.ifBlank { "Conductor" } ?: "Conductor",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFFF9D423)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DirectionsCar,
                                                contentDescription = null,
                                                tint = Color(0xFF0F2027),
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Conductor de TucTuc",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0F2027)
                                            )
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                            // Estado de suscripción y placas
                            val isExpired = driverProfile?.isSubscriptionExpired == true
                            val expiryFormatted = formatExpiryDate(driverProfile?.fechaVencimientoSuscripcion)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.DirectionsCar,
                                        contentDescription = null,
                                        tint = Color(0xFFF9D423),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = driverProfile?.displayIdentifier?.ifBlank { "Sin placas" } ?: "Sin placas",
                                        fontSize = 13.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isExpired) Color(0x33FF5252) else Color(0x334CAF50),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isExpired) Color(0x66FF5252) else Color(0x664CAF50)
                                    )
                                ) {
                                    Text(
                                        text = if (isExpired) "Suscripción Vencida" else "Suscripción Activa",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isExpired) Color(0xFFFF8A80) else Color(0xFFA5D6A7),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            if (!isExpired && expiryFormatted.isNotBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarMonth,
                                        contentDescription = null,
                                        tint = Color(0xFFB0BEC5),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    val remainingDays = driverProfile?.remainingDays ?: 0
                                    val remainingSuffix = if (remainingDays > 1) " • $remainingDays días restantes" else if (remainingDays == 1) " • 1 día restante" else ""
                                    Text(
                                        text = "Vence el: $expiryFormatted$remainingSuffix",
                                        fontSize = 12.sp,
                                        color = Color(0xFFCFD8DC)
                                    )
                                }
                            }
                        }
                    }

                    // ── Card 1: Datos del Conductor y Vehículo ─────────────────
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0x18FFFFFF))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = Color(0xFFF9D423),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Datos del Conductor y Vehículo",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            OutlinedTextField(
                                value = nombre,
                                onValueChange = { nombre = it },
                                label = { Text("Nombre Completo") },
                                leadingIcon = {
                                    Icon(Icons.Default.Person, contentDescription = null)
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = textFieldColors,
                                shape = RoundedCornerShape(12.dp)
                            )

                            OutlinedTextField(
                                value = telefono,
                                onValueChange = { telefono = it },
                                label = { Text("Teléfono / WhatsApp") },
                                leadingIcon = {
                                    Icon(Icons.Default.Phone, contentDescription = null)
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = textFieldColors,
                                shape = RoundedCornerShape(12.dp),
                                supportingText = {
                                    Text(
                                        text = "Número de contacto visible para los pasajeros",
                                        color = Color(0xFFB0BEC5),
                                        fontSize = 12.sp
                                    )
                                }
                            )

                            OutlinedTextField(
                                value = placas,
                                onValueChange = { placas = it.uppercase() },
                                label = { Text("Número de Placas del Mototaxi") },
                                leadingIcon = {
                                    Icon(Icons.Default.DirectionsCar, contentDescription = null)
                                },
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Characters
                                ),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = textFieldColors,
                                shape = RoundedCornerShape(12.dp),
                                supportingText = {
                                    Text(
                                        text = "Identificación de tu mototaxi en el mapa",
                                        color = Color(0xFFB0BEC5),
                                        fontSize = 12.sp
                                    )
                                }
                            )

                            OutlinedTextField(
                                value = numeroUnidad,
                                onValueChange = { numeroUnidad = it },
                                label = { Text("N° de Unidad / Padrón (Opcional)") },
                                placeholder = { Text("Ej. Torito #14") },
                                leadingIcon = {
                                    Icon(Icons.Default.Tag, contentDescription = null)
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = textFieldColors,
                                shape = RoundedCornerShape(12.dp),
                                supportingText = {
                                    Text(
                                        text = "Número visible en el techo/puerta de tu mototaxi",
                                        color = Color(0xFFB0BEC5),
                                        fontSize = 12.sp
                                    )
                                }
                            )

                            val emailToShow = driverProfile?.email?.ifBlank { authEmail } ?: authEmail
                            OutlinedTextField(
                                value = emailToShow,
                                onValueChange = { },
                                enabled = false,
                                label = { Text("Correo Electrónico (Acceso)") },
                                leadingIcon = {
                                    Icon(Icons.Default.Email, contentDescription = null)
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = Color.White.copy(alpha = 0.8f),
                                    disabledBorderColor = Color.White.copy(alpha = 0.2f),
                                    disabledLabelColor = Color(0xFFB0BEC5),
                                    disabledLeadingIconColor = Color(0xFFB0BEC5)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Button(
                                onClick = {
                                    if (nombre.trim().isBlank()) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Por favor ingresa tu nombre")
                                        }
                                        return@Button
                                    }
                                    if (telefono.trim().isBlank()) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Por favor ingresa tu teléfono")
                                        }
                                        return@Button
                                    }
                                    if (placas.trim().isBlank()) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Por favor ingresa el número de placas")
                                        }
                                        return@Button
                                    }

                                    isSavingData = true
                                    scope.launch {
                                        val result = repository.updateDriverProfile(
                                            name = nombre.trim(),
                                            phone = telefono.trim(),
                                            plateNumber = placas.trim(),
                                            unitNumber = numeroUnidad.trim()
                                        )
                                        isSavingData = false
                                        if (result.isSuccess) {
                                            snackbarHostState.showSnackbar("¡Datos de conductor actualizados con éxito!")
                                        } else {
                                            snackbarHostState.showSnackbar(
                                                "Error al actualizar datos: ${result.exceptionOrNull()?.message ?: "Error desconocido"}"
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                enabled = !isSavingData,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFF9D423),
                                    contentColor = Color(0xFF0F2027)
                                )
                            ) {
                                if (isSavingData) {
                                    CircularProgressIndicator(
                                        color = Color(0xFF0F2027),
                                        modifier = Modifier.size(24.dp)
                                    )
                                } else {
                                    Icon(Icons.Default.Save, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Guardar Datos del Conductor",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        }
                    }

                    // ── Card 2: Seguridad y Contraseña ───────────────────────
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0x18FFFFFF))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = Color(0xFFF9D423),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Cambiar Contraseña",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            Text(
                                text = "Ingresa tu nueva contraseña para actualizar tu acceso.",
                                fontSize = 13.sp,
                                color = Color(0xFFB0BEC5)
                            )

                            OutlinedTextField(
                                value = newPassword,
                                onValueChange = { newPassword = it },
                                label = { Text("Nueva Contraseña") },
                                leadingIcon = {
                                    Icon(Icons.Default.Lock, contentDescription = null)
                                },
                                trailingIcon = {
                                    IconButton(onClick = { isNewPasswordVisible = !isNewPasswordVisible }) {
                                        Icon(
                                            imageVector = if (isNewPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = "Alternar visibilidad"
                                        )
                                    }
                                },
                                visualTransformation = if (isNewPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = textFieldColors,
                                shape = RoundedCornerShape(12.dp)
                            )

                            OutlinedTextField(
                                value = confirmPassword,
                                onValueChange = { confirmPassword = it },
                                label = { Text("Confirmar Nueva Contraseña") },
                                leadingIcon = {
                                    Icon(Icons.Default.Lock, contentDescription = null)
                                },
                                trailingIcon = {
                                    IconButton(onClick = { isConfirmPasswordVisible = !isConfirmPasswordVisible }) {
                                        Icon(
                                            imageVector = if (isConfirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = "Alternar visibilidad"
                                        )
                                    }
                                },
                                visualTransformation = if (isConfirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = textFieldColors,
                                shape = RoundedCornerShape(12.dp)
                            )

                            Button(
                                onClick = {
                                    if (newPassword.length < 6) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("La contraseña debe tener al menos 6 caracteres")
                                        }
                                        return@Button
                                    }
                                    if (newPassword != confirmPassword) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Las contraseñas no coinciden")
                                        }
                                        return@Button
                                    }

                                    isUpdatingPassword = true
                                    scope.launch {
                                        val result = repository.updatePassword(newPassword)
                                        isUpdatingPassword = false
                                        if (result.isSuccess) {
                                            newPassword = ""
                                            confirmPassword = ""
                                            snackbarHostState.showSnackbar("¡Contraseña actualizada correctamente!")
                                        } else {
                                            snackbarHostState.showSnackbar(
                                                "Error al actualizar contraseña: ${result.exceptionOrNull()?.message ?: "Error desconocido"}"
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                enabled = !isUpdatingPassword,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2C5364),
                                    contentColor = Color.White
                                )
                            ) {
                                if (isUpdatingPassword) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                } else {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Actualizar Contraseña",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

private fun formatExpiryDate(dateStr: String?): String {
    if (dateStr.isNullOrBlank()) return ""
    val ts = parseIsoTimestamp(dateStr)
    if (ts <= 0L) return ""
    val sdf = SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("es", "GT"))
    return sdf.format(Date(ts))
}
