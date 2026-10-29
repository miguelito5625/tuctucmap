package com.example.tuctucmap.ui.screens.passenger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tuctucmap.data.repository.SupabaseRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassengerLoginScreen(
    repository: SupabaseRepository,
    onLoginSuccess: () -> Unit,
    onSelectDriver: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onExploreMapAsGuest: () -> Unit = {},
    onBack: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "TucTuc Map",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = Color.White)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menú", tint = Color.White)
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("🛺 Acceso Conductores") },
                            onClick = {
                                menuExpanded = false
                                onSelectDriver()
                            }
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
                .imePadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Logo / Ícono de la app
                Text(text = "🛺", fontSize = 64.sp)

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Bienvenido a TucTuc Map",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Inicia sesión para pedir un tuctuc",
                    fontSize = 14.sp,
                    color = Color(0xFFB0BEC5),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(36.dp))

                // ── Campo Correo ─────────────────────────────────────────────
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; errorMessage = null },
                    label = { Text("Correo Electrónico") },
                    leadingIcon = {
                        Icon(Icons.Default.Email, contentDescription = null, tint = Color(0xFF78909C))
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFF9D423),
                        unfocusedBorderColor = Color(0xFF455A64),
                        focusedLabelColor = Color(0xFFF9D423),
                        unfocusedLabelColor = Color(0xFF78909C),
                        cursorColor = Color(0xFFF9D423)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ── Campo Contraseña ─────────────────────────────────────────
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorMessage = null },
                    label = { Text("Contraseña") },
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF78909C))
                    },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        TextButton(onClick = { passwordVisible = !passwordVisible }) {
                            Text(
                                text = if (passwordVisible) "Ocultar" else "Ver",
                                color = Color(0xFF78909C),
                                fontSize = 12.sp
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFF9D423),
                        unfocusedBorderColor = Color(0xFF455A64),
                        focusedLabelColor = Color(0xFFF9D423),
                        unfocusedLabelColor = Color(0xFF78909C),
                        cursorColor = Color(0xFFF9D423)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // ── Error ────────────────────────────────────────────────────
                errorMessage?.let {
                    Text(
                        text = it,
                        color = Color(0xFFFF6B6B),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // ── Botón Iniciar Sesión ──────────────────────────────────────────────
                Button(
                    onClick = {
                        if (email.isBlank() || password.isBlank()) {
                            errorMessage = "Por favor completa todos los campos"
                            return@Button
                        }
                        isLoading = true
                        scope.launch {
                            val result = repository.loginPassengerWithEmail(email.trim(), password)
                            isLoading = false
                            if (result.isSuccess) {
                                onLoginSuccess()
                            } else {
                                errorMessage = com.example.tuctucmap.data.repository.SupabaseRepository.formatAuthError(result.exceptionOrNull())
                            }
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF9D423),
                        contentColor = Color(0xFF0F2027)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = Color(0xFF0F2027))
                    } else {
                        Text(
                            "Iniciar Sesión",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Ir a Registrarse ──────────────────────────────────────────────
                TextButton(onClick = onNavigateToRegister) {
                    Text(
                        text = "¿No tienes cuenta? Regístrate aquí",
                        color = Color(0xFF78909C),
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ── Explorar Mapa sin Iniciar Sesión ──────────────────────────────
                OutlinedButton(
                    onClick = onExploreMapAsGuest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF9D423)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF9D423)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("🗺️ Explorar Mapa sin Iniciar Sesión", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ── Conductor ─────────────────────────────────────────────────────
                TextButton(onClick = onSelectDriver) {
                    Text(
                        text = "🛺 ¿Eres Conductor? Entrar o Registrar TucTuc",
                        color = Color(0xFFF9D423),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
