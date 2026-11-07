package com.example.tuctucmap.ui.screens.driver

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tuctucmap.data.repository.DriverGoogleLoginResult
import com.example.tuctucmap.data.repository.SupabaseRepository
import com.example.tuctucmap.data.service.GoogleAuthCancellationException
import com.example.tuctucmap.data.service.GoogleAuthHelper
import com.example.tuctucmap.ui.components.CompleteDriverGoogleRegistrationDialog
import com.example.tuctucmap.ui.components.GoogleSignInButton
import kotlinx.coroutines.launch

@Composable
fun DriverLoginScreen(
    repository: SupabaseRepository,
    onNavigateToRegister: () -> Unit,
    onLoginSuccess: () -> Unit,
    onBackToOnboarding: () -> Unit,
    onExploreMapAsGuest: () -> Unit = onBackToOnboarding
) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isGoogleLoading by remember { mutableStateOf(false) }
    var pendingGoogleProfile by remember { mutableStateOf<DriverGoogleLoginResult.NeedsProfile?>(null) }
    var dialogLoading by remember { mutableStateOf(false) }
    var dialogError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

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
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Fila de encabezado para regresar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackToOnboarding) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Volver",
                            tint = Color.White
                        )
                    }
                    Text(
                        text = "Volver al inicio",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(40.dp))

                Text(
                    text = "🛺 Conductor",
                    fontSize = 28.sp,
                    color = Color(0xFFF9D423),
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Inicia sesión para comenzar a transmitir tu ubicación",
                    fontSize = 15.sp,
                    color = Color.LightGray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(36.dp))

                val focusManager = LocalFocusManager.current
                val handleLogin: () -> Unit = {
                    if (email.isBlank() || password.isBlank()) {
                        errorMessage = "Por favor completa todos los campos"
                    } else if (!isLoading) {
                        isLoading = true
                        scope.launch {
                            try {
                                val result = repository.loginDriver(email.trim(), password)
                                if (result.isSuccess) {
                                    onLoginSuccess()
                                } else {
                                    errorMessage =
                                        com.example.tuctucmap.data.repository.SupabaseRepository.formatAuthError(result.exceptionOrNull())
                                }
                            } catch (e: Exception) {
                                errorMessage = e.localizedMessage ?: "Error al iniciar sesión"
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                }

                // Campo de Correo Electrónico
                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        errorMessage = null
                    },
                    label = { Text("Correo Electrónico", color = Color.LightGray) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            tint = Color.LightGray
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFF9D423),
                        unfocusedBorderColor = Color.White,
                        focusedLabelColor = Color(0xFFF9D423),
                        unfocusedLabelColor = Color.White,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Campo de Contraseña
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = null
                    },
                    label = { Text("Contraseña", color = Color.LightGray) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color.LightGray
                        )
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        focusManager.clearFocus()
                        handleLogin()
                    }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFF9D423),
                        unfocusedBorderColor = Color.White,
                        focusedLabelColor = Color(0xFFF9D423),
                        unfocusedLabelColor = Color.White,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Mensaje de Error
                errorMessage?.let { error ->
                    Text(
                        text = error,
                        color = Color(0xFFFF6B6B),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                // Botón de Iniciar Sesión con estado de carga uniforme
                Button(
                    onClick = handleLogin,
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF9D423),
                        contentColor = Color(0xFF0F2027)
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF0F2027)
                        )
                    } else {
                        Text(
                            text = "Iniciar Sesión",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF37474F))
                    Text(
                        text = " O ",
                        color = Color(0xFF90A4AE),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF37474F))
                }

                Spacer(modifier = Modifier.height(16.dp))

                GoogleSignInButton(
                    onClick = {
                        isGoogleLoading = true
                        errorMessage = null
                        scope.launch {
                            try {
                                val googleResult = GoogleAuthHelper.getGoogleCredentials(context)
                                if (googleResult.isSuccess) {
                                    val userData = googleResult.getOrThrow()
                                    val loginResult = repository.loginDriverWithGoogle(
                                        idToken = userData.idToken,
                                        rawNonce = userData.rawNonce,
                                        googleEmail = userData.email,
                                        googleDisplayName = userData.displayName
                                    )
                                    if (loginResult.isSuccess) {
                                        when (val status = loginResult.getOrThrow()) {
                                            is DriverGoogleLoginResult.Success -> {
                                                onLoginSuccess()
                                            }
                                            is DriverGoogleLoginResult.NeedsProfile -> {
                                                pendingGoogleProfile = status
                                            }
                                        }
                                    } else {
                                        errorMessage = SupabaseRepository.formatAuthError(loginResult.exceptionOrNull())
                                    }
                                } else {
                                    val err = googleResult.exceptionOrNull()
                                    if (err !is GoogleAuthCancellationException) {
                                        errorMessage = err?.localizedMessage ?: "No se pudo conectar con Google."
                                    }
                                }
                            } catch (e: Exception) {
                                if (e !is GoogleAuthCancellationException) {
                                    errorMessage = e.localizedMessage ?: "Error al autenticar con Google."
                                }
                            } finally {
                                isGoogleLoading = false
                            }
                        }
                    },
                    text = "Continuar con Google",
                    isLoading = isGoogleLoading,
                    enabled = !isLoading && !isGoogleLoading
                )

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Diálogo de Registro Rápido para Nuevos Conductores con Google
            pendingGoogleProfile?.let { pending ->
                CompleteDriverGoogleRegistrationDialog(
                    initialName = pending.displayName,
                    googleEmail = pending.email,
                    isLoading = dialogLoading,
                    errorMessage = dialogError,
                    onDismiss = {
                        scope.launch {
                            repository.cancelGooglePendingSession()
                            pendingGoogleProfile = null
                            dialogError = null
                        }
                    },
                    onConfirm = { nombre, telefono, placas, numeroUnidad ->
                        dialogLoading = true
                        dialogError = null
                        scope.launch {
                            try {
                                val regResult = repository.completeDriverGoogleRegistration(
                                    nombre = nombre,
                                    telefono = telefono,
                                    placas = placas,
                                    numeroUnidad = numeroUnidad
                                )
                                if (regResult.isSuccess) {
                                    pendingGoogleProfile = null
                                    onLoginSuccess()
                                } else {
                                    dialogError = SupabaseRepository.formatAuthError(regResult.exceptionOrNull())
                                }
                            } catch (e: Exception) {
                                dialogError = e.localizedMessage ?: "Error al guardar el perfil de conductor."
                            } finally {
                                dialogLoading = false
                            }
                        }
                    }
                )
            }

            // Enlace para crear cuenta
            Row(
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "¿No tienes una cuenta? ",
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
                Text(
                    text = "Regístrate aquí",
                    color = Color(0xFFF9D423),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.clickable { onNavigateToRegister() }
                )
            }

            Text(
                text = "🗺️ Explorar Mapa como Pasajero",
                color = Color(0xFF38BDF8),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clickable { onExploreMapAsGuest() }
                    .padding(bottom = 20.dp)
            )
        }
    }
}
