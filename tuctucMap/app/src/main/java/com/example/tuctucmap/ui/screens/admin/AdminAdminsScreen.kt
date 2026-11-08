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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tuctucmap.data.model.AdminUserDto
import com.example.tuctucmap.data.repository.AdminRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminAdminsScreen(
    adminRepo: AdminRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var adminsList by remember { mutableStateOf<List<AdminUserDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    var emailToPromote by remember { mutableStateOf("") }
    var isPromoting by remember { mutableStateOf(false) }

    var adminToRevoke by remember { mutableStateOf<AdminUserDto?>(null) }
    var isRevoking by remember { mutableStateOf(false) }

    fun loadAdmins() {
        isLoading = true
        scope.launch {
            val res = adminRepo.fetchAdminAccounts()
            if (res.isSuccess) {
                adminsList = res.getOrDefault(emptyList())
            } else {
                Toast.makeText(context, "Error al cargar administradores", Toast.LENGTH_SHORT).show()
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadAdmins()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Gestión de Administradores",
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
                actions = {
                    IconButton(onClick = { loadAdmins() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualizar", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
            )
        },
        containerColor = Color(0xFF0F172A)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Card de Agregar / Promover Nuevo Admin
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                border = BorderStroke(1.dp, Color(0x3338BDF8)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "PROMOVER NUEVO ADMINISTRADOR",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8)
                        )
                    }

                    Text(
                        text = "Ingresa el correo electrónico de un usuario registrado en la app para concederle permisos administrativos.",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        lineHeight = 16.sp
                    )

                    OutlinedTextField(
                        value = emailToPromote,
                        onValueChange = { emailToPromote = it },
                        label = { Text("Correo Electrónico") },
                        placeholder = { Text("ejemplo@correo.com") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedLabelColor = Color(0xFF38BDF8),
                            unfocusedLabelColor = Color(0xFF94A3B8),
                            cursorColor = Color(0xFF38BDF8),
                            focusedLeadingIconColor = Color(0xFF38BDF8),
                            unfocusedLeadingIconColor = Color(0xFF94A3B8)
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Button(
                        onClick = {
                            if (emailToPromote.isBlank()) {
                                Toast.makeText(context, "Ingresa un correo", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isPromoting = true
                            scope.launch {
                                val res = adminRepo.promoteUserToAdmin(emailToPromote)
                                isPromoting = false
                                if (res.isSuccess) {
                                    emailToPromote = ""
                                    loadAdmins()
                                    Toast.makeText(context, "¡Usuario promovido a Administrador!", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, res.exceptionOrNull()?.message ?: "Error al promover", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        enabled = !isPromoting && emailToPromote.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                    ) {
                        if (isPromoting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.AddModerator, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Asignar Permisos de Administrador", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Lista de Administradores Actuales
            Text(
                text = "ADMINISTRADORES ACTIVOS (${adminsList.size})",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF94A3B8),
                modifier = Modifier.padding(start = 4.dp)
            )

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFFF59E0B))
                }
            } else if (adminsList.isEmpty()) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No se encontraron administradores", color = Color(0xFF94A3B8), fontSize = 13.sp)
                    }
                }
            } else {
                adminsList.forEach { admin ->
                    val isMe = admin.id == adminRepo.currentAdminUid
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        border = if (isMe) BorderStroke(1.dp, Color(0xFFF59E0B)) else null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(if (isMe) Color(0xFFF59E0B) else Color(0xFF334155)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isMe) Icons.Default.Shield else Icons.Default.Person,
                                        contentDescription = null,
                                        tint = if (isMe) Color(0xFF0F172A) else Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = admin.displayName,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 14.sp
                                        )
                                        if (isMe) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0x33F59E0B)
                                            ) {
                                                Text(
                                                    text = "TÚ",
                                                    color = Color(0xFFF59E0B),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    if (!admin.email.isNullOrBlank()) {
                                        Text(
                                            text = admin.email,
                                            fontSize = 12.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                    if (!admin.phone.isNullOrBlank()) {
                                        Text(
                                            text = "📞 ${admin.phone}",
                                            fontSize = 11.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                }
                            }

                            if (!isMe) {
                                IconButton(
                                    onClick = { adminToRevoke = admin }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RemoveModerator,
                                        contentDescription = "Revocar Administrador",
                                        tint = Color(0xFFEF4444)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }

    // Diálogo de Confirmación para Revocar Administrador
    if (adminToRevoke != null) {
        val target = adminToRevoke!!
        AlertDialog(
            onDismissRequest = { if (!isRevoking) adminToRevoke = null },
            title = {
                Text(
                    text = "¿Revocar Rol de Administrador?",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "¿Estás seguro de que deseas retirar los permisos de administrador a '${target.displayName}' (${target.email ?: ""})? El usuario pasará a ser una cuenta de pasajero normal.",
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        isRevoking = true
                        scope.launch {
                            val res = adminRepo.revokeAdminRole(target.id)
                            isRevoking = false
                            if (res.isSuccess) {
                                adminToRevoke = null
                                loadAdmins()
                                Toast.makeText(context, "Permisos de administrador revocados", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, res.exceptionOrNull()?.message ?: "Error al revocar", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = !isRevoking,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isRevoking) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Revocar Permisos", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { adminToRevoke = null },
                    enabled = !isRevoking
                ) {
                    Text("Cancelar", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }
}

