package com.example.tuctucmap.ui.screens.passenger

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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tuctucmap.data.model.Passenger
import com.example.tuctucmap.data.repository.SupabaseRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassengerProfileScreen(
    repository: SupabaseRepository,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val uid = repository.currentUserUid ?: ""
    val authEmail = repository.currentUserEmail ?: ""

    var passengerProfile by remember { mutableStateOf<Passenger?>(null) }
    var isLoadingProfile by remember { mutableStateOf(true) }

    // Formulario de datos
    var nombre by remember { mutableStateOf("") }
    var telefono by remember { mutableStateOf("") }
    var isSavingData by remember { mutableStateOf(false) }

    // Formulario de contraseña
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isNewPasswordVisible by remember { mutableStateOf(false) }
    var isConfirmPasswordVisible by remember { mutableStateOf(false) }
    var isUpdatingPassword by remember { mutableStateOf(false) }

    // Cargar perfil en tiempo real
    LaunchedEffect(uid) {
        if (uid.isNotBlank()) {
            repository.observePassengerProfile(uid).collectLatest { profile ->
                passengerProfile = profile
                if (profile != null && nombre.isEmpty() && telefono.isEmpty()) {
                    nombre = profile.nombre
                    telefono = profile.telefono
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
                        text = "Mi Perfil",
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            val initial = (passengerProfile?.nombre?.trim()?.firstOrNull() ?: 'P')
                                .uppercaseChar().toString()
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(
                                        brush = Brush.linearGradient(
                                            listOf(Color(0xFFF9D423), Color(0xFFFF9800))
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
                                    text = passengerProfile?.nombre?.ifBlank { "Pasajero" } ?: "Pasajero",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0x3300C9FF),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x6600C9FF))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            tint = Color(0xFF80D8FF),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Cuenta de Pasajero",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF80D8FF)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── Card 1: Datos Personales ─────────────────────────────
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
                                    text = "Datos Personales",
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
                                        text = "Los conductores usarán este número para contactarte",
                                        color = Color(0xFFB0BEC5),
                                        fontSize = 12.sp
                                    )
                                }
                            )

                            val emailToShow = passengerProfile?.email?.ifBlank { authEmail } ?: authEmail
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

                                    isSavingData = true
                                    scope.launch {
                                        val result = repository.updatePassengerProfile(
                                            name = nombre.trim(),
                                            phone = telefono.trim()
                                        )
                                        isSavingData = false
                                        if (result.isSuccess) {
                                            snackbarHostState.showSnackbar("¡Datos guardados con éxito!")
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
                                        text = "Guardar Datos Personales",
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
