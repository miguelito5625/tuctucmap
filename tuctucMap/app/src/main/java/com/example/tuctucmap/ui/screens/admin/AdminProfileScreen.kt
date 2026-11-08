package com.example.tuctucmap.ui.screens.admin

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tuctucmap.data.model.AdminProfileDto
import com.example.tuctucmap.data.repository.AdminRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminProfileScreen(
    adminRepo: AdminRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var adminProfile by remember { mutableStateOf<AdminProfileDto?>(null) }
    var isLoadingProfile by remember { mutableStateOf(true) }

    // Formulario de datos
    var nombre by remember { mutableStateOf("") }
    var telefono by remember { mutableStateOf("") }
    var isSavingData by remember { mutableStateOf(false) }

    // Formulario de contraseña segura con reautenticación
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isCurrentPasswordVisible by remember { mutableStateOf(false) }
    var isNewPasswordVisible by remember { mutableStateOf(false) }
    var isConfirmPasswordVisible by remember { mutableStateOf(false) }
    var isUpdatingPassword by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val res = adminRepo.fetchAdminProfile()
        if (res.isSuccess) {
            val prof = res.getOrNull()
            adminProfile = prof
            nombre = prof?.name ?: ""
            telefono = prof?.phone ?: ""
        }
        isLoadingProfile = false
    }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedBorderColor = Color(0xFFF59E0B),
        unfocusedBorderColor = Color(0xFF334155),
        focusedLabelColor = Color(0xFFF59E0B),
        unfocusedLabelColor = Color(0xFF94A3B8),
        cursorColor = Color(0xFFF59E0B),
        focusedLeadingIconColor = Color(0xFFF59E0B),
        unfocusedLeadingIconColor = Color(0xFF94A3B8),
        focusedTrailingIconColor = Color(0xFFF59E0B),
        unfocusedTrailingIconColor = Color(0xFF94A3B8)
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Mi Perfil y Seguridad",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
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
                    containerColor = Color(0xFF0F172A)
                )
            )
        },
        containerColor = Color(0xFF0F172A)
    ) { padding ->
        if (isLoadingProfile) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFFF59E0B))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header del Administrador
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    border = BorderStroke(1.dp, Color(0x33F59E0B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF59E0B)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = Color(0xFF0F172A),
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Column {
                            Text(
                                text = nombre.ifBlank { "Administrador" },
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = adminProfile?.email ?: adminRepo.currentAdminEmail ?: "admin@tuctucmap.gt",
                                color = Color(0xFF38BDF8),
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0x22F59E0B)
                            ) {
                                Text(
                                    text = "ROL: ADMINISTRADOR GENERAL",
                                    color = Color(0xFFF59E0B),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                // Sección 1: Datos Personales
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "DATOS DE LA CUENTA",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8)
                        )

                        OutlinedTextField(
                            value = nombre,
                            onValueChange = { nombre = it },
                            label = { Text("Nombre Completo") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            singleLine = true,
                            colors = textFieldColors,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = telefono,
                            onValueChange = { telefono = it },
                            label = { Text("Teléfono de Contacto") },
                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            colors = textFieldColors,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Button(
                            onClick = {
                                if (nombre.trim().isBlank()) {
                                    Toast.makeText(context, "El nombre no puede estar vacío", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                isSavingData = true
                                scope.launch {
                                    val res = adminRepo.updateAdminProfile(nombre, telefono)
                                    isSavingData = false
                                    if (res.isSuccess) {
                                        Toast.makeText(context, "Datos de perfil actualizados", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Error: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            enabled = !isSavingData,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            if (isSavingData) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF0F172A), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Save, contentDescription = null, tint = Color(0xFF0F172A))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Guardar Datos del Perfil", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Sección 2: Seguridad y Cambio de Contraseña Seguro
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LockReset, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "CAMBIO SEGURO DE CONTRASEÑA",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8)
                            )
                        }

                        Text(
                            text = "Por tu seguridad, se requiere reautenticar tu contraseña actual antes de establecer la nueva.",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            lineHeight = 16.sp
                        )

                        // Contraseña Actual
                        OutlinedTextField(
                            value = currentPassword,
                            onValueChange = { currentPassword = it },
                            label = { Text("Contraseña Actual") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { isCurrentPasswordVisible = !isCurrentPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isCurrentPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null
                                    )
                                }
                            },
                            visualTransformation = if (isCurrentPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            colors = textFieldColors,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Nueva Contraseña
                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            label = { Text("Nueva Contraseña (mín. 6 caracteres)") },
                            leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { isNewPasswordVisible = !isNewPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isNewPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null
                                    )
                                }
                            },
                            visualTransformation = if (isNewPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            colors = textFieldColors,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Confirmar Contraseña
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = { Text("Confirmar Nueva Contraseña") },
                            leadingIcon = { Icon(Icons.Default.CheckCircleOutline, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { isConfirmPasswordVisible = !isConfirmPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isConfirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null
                                    )
                                }
                            },
                            visualTransformation = if (isConfirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            colors = textFieldColors,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Button(
                            onClick = {
                                if (currentPassword.isBlank()) {
                                    Toast.makeText(context, "Ingresa tu contraseña actual", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (newPassword.length < 6) {
                                    Toast.makeText(context, "La nueva contraseña debe tener al menos 6 caracteres", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (newPassword != confirmPassword) {
                                    Toast.makeText(context, "Las contraseñas no coinciden", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                isUpdatingPassword = true
                                scope.launch {
                                    val res = adminRepo.changePasswordSecurely(currentPassword, newPassword)
                                    isUpdatingPassword = false
                                    if (res.isSuccess) {
                                        currentPassword = ""
                                        newPassword = ""
                                        confirmPassword = ""
                                        Toast.makeText(context, "¡Contraseña actualizada exitosamente!", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, res.exceptionOrNull()?.message ?: "Error al cambiar contraseña", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            enabled = !isUpdatingPassword,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            if (isUpdatingPassword) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.SecurityUpdateGood, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Actualizar Contraseña", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

