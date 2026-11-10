package com.example.tuctucmap.ui.screens.driver

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.tuctucmap.data.repository.DriverGoogleLoginResult
import com.example.tuctucmap.data.repository.SupabaseRepository
import com.example.tuctucmap.data.service.GoogleAuthCancellationException
import com.example.tuctucmap.data.service.GoogleAuthHelper
import com.example.tuctucmap.ui.components.CompleteDriverGoogleRegistrationDialog
import com.example.tuctucmap.ui.components.GoogleSignInButton
import com.example.tuctucmap.ui.components.Vehicle360Viewer
import com.example.tuctucmap.ui.components.camera.CameraCaptureMode
import com.example.tuctucmap.ui.components.camera.CameraUtils
import com.example.tuctucmap.ui.components.camera.NativeCameraScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DriverRegisterScreen(
    repository: SupabaseRepository,
    onNavigateToLogin: () -> Unit,
    onRegisterSuccess: () -> Unit,
    onBackToOnboarding: () -> Unit
) {
    val context = LocalContext.current
    var nombre by remember { mutableStateOf("") }
    var telefono by remember { mutableStateOf("") }
    var placas by remember { mutableStateOf("") }
    var numeroUnidad by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isGoogleLoading by remember { mutableStateOf(false) }
    var pendingGoogleProfile by remember { mutableStateOf<DriverGoogleLoginResult.NeedsProfile?>(null) }
    var dialogLoading by remember { mutableStateOf(false) }
    var dialogError by remember { mutableStateOf<String?>(null) }

    // Estados para captura obligatoria de fotos (Conductor y 360° del Vehículo)
    var driverPhotoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var vehicle360Bitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var activeCameraMode by remember { mutableStateOf<CameraCaptureMode?>(null) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            errorMessage = "El permiso de cámara es obligatorio para capturar la fotografía y el escaneo 360°."
        }
    }

    val openCamera: (CameraCaptureMode) -> Unit = { mode ->
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            activeCameraMode = mode
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

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
                .padding(24.dp)
                .verticalScroll(scrollState),
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

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Crear Cuenta",
                fontSize = 28.sp,
                color = Color(0xFFF9D423),
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Regístrate para solicitar la activación de tu TucTuc en la plataforma",
                fontSize = 15.sp,
                color = Color.LightGray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            val focusManager = LocalFocusManager.current
            val handleRegister: () -> Unit = {
                if (nombre.isBlank() || telefono.isBlank() || placas.isBlank() || email.isBlank() || password.isBlank()) {
                    errorMessage = "Todos los campos principales son obligatorios"
                } else if (driverPhotoBitmap == null) {
                    errorMessage = "Debes tomar tu foto de conductor obligatoriamente"
                } else if (vehicle360Bitmaps.size < 8) {
                    errorMessage = "Debes completar el escaneo 360° de los 8 ángulos de tu vehículo"
                } else {
                    val cleanDigits = telefono.replace(Regex("[^0-9]"), "")
                    if (cleanDigits.length < 8) {
                        errorMessage = "Ingresa un número de teléfono válido (al menos 8 dígitos)"
                    } else if (password.length < 6) {
                        errorMessage = "La contraseña debe tener al menos 6 caracteres"
                    } else if (!isLoading) {
                        isLoading = true
                        scope.launch {
                            try {
                                val driverBytes = withContext(Dispatchers.Default) {
                                    CameraUtils.compressBitmap(driverPhotoBitmap!!)
                                }
                                val v360BytesList = withContext(Dispatchers.Default) {
                                    vehicle360Bitmaps.map { CameraUtils.compressBitmap(it) }
                                }
                                val result = repository.registerDriver(
                                    nombre = nombre.trim(),
                                    telefono = telefono.trim(),
                                    placas = placas.trim(),
                                    email = email.trim(),
                                    contrasena = password,
                                    numeroUnidad = numeroUnidad.trim(),
                                    driverPhotoBytes = driverBytes,
                                    vehicle360BytesList = v360BytesList
                                )
                                if (result.isSuccess) {
                                    onRegisterSuccess()
                                } else {
                                    errorMessage =
                                        com.example.tuctucmap.data.repository.SupabaseRepository.formatAuthError(result.exceptionOrNull())
                                }
                            } catch (e: Exception) {
                                errorMessage = e.localizedMessage ?: "Error al registrar conductor"
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                }
            }

            // Campo Nombre Completo
            OutlinedTextField(
                value = nombre,
                onValueChange = {
                    nombre = it
                    errorMessage = null
                },
                label = { Text("Nombre Completo", color = Color.LightGray) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.LightGray
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
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

            Spacer(modifier = Modifier.height(12.dp))

            // Campo Teléfono
            OutlinedTextField(
                value = telefono,
                onValueChange = {
                    telefono = it
                    errorMessage = null
                },
                label = { Text("Teléfono", color = Color.LightGray) },
                placeholder = { Text("8 dígitos (ej. 55554444)", color = Color.Gray) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = null,
                        tint = Color.LightGray
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
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

            Spacer(modifier = Modifier.height(12.dp))

            // Campo Placas del TucTuc
            OutlinedTextField(
                value = placas,
                onValueChange = {
                    placas = it
                    errorMessage = null
                },
                label = { Text("Placas del TucTuc", color = Color.LightGray) },
                placeholder = { Text("Ej. M-123XYZ", color = Color.Gray) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint = Color.LightGray
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
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

            Spacer(modifier = Modifier.height(12.dp))

            // Campo Número de Unidad / Padrón (Opcional en pueblos)
            OutlinedTextField(
                value = numeroUnidad,
                onValueChange = {
                    numeroUnidad = it
                    errorMessage = null
                },
                label = { Text("N° de Unidad / TucTuc (Opcional)", color = Color.LightGray) },
                placeholder = { Text("Ej. #14", color = Color.Gray) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint = Color.LightGray
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
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

            Spacer(modifier = Modifier.height(12.dp))

            // Campo Correo Electrónico
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

            Spacer(modifier = Modifier.height(12.dp))

            // Campo Contraseña
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    errorMessage = null
                },
                label = { Text("Contraseña", color = Color.LightGray) },
                placeholder = { Text("Mínimo 6 caracteres", color = Color.Gray) },
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
                    handleRegister()
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

            Spacer(modifier = Modifier.height(18.dp))

            // ── Fotografías Obligatorias de Verificación ─────────────────────────
            Text(
                text = "Fotografías Obligatorias de Verificación",
                color = Color(0xFFF9D423),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )
            Text(
                text = "El administrador revisará estas fotos para autorizar tu cuenta.",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )

            // Tarjeta 1: Foto del Conductor (Rostro)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { openCamera(CameraCaptureMode.DRIVER_PORTRAIT) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                border = BorderStroke(1.dp, if (driverPhotoBitmap != null) Color(0xFF10B981) else Color(0xFF475569))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (driverPhotoBitmap != null) {
                        Image(
                            bitmap = driverPhotoBitmap!!.asImageBitmap(),
                            contentDescription = "Foto capturada",
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .border(2.dp, Color(0xFF10B981), CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF334155)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFFF9D423), modifier = Modifier.size(28.dp))
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text("1. Tu Foto de Conductor (Rostro) *", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (driverPhotoBitmap != null) "✓ Fotografía capturada correctamente" else "Toca para abrir la cámara y tomar tu selfie",
                            color = if (driverPhotoBitmap != null) Color(0xFF10B981) else Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }

                    Icon(
                        imageVector = if (driverPhotoBitmap != null) Icons.Default.CheckCircle else Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = if (driverPhotoBitmap != null) Color(0xFF10B981) else Color(0xFFF9D423)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Tarjeta 2: Foto 360° del Vehículo (8 ángulos)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { openCamera(CameraCaptureMode.VEHICLE_360_SCAN) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                border = BorderStroke(1.dp, if (vehicle360Bitmaps.size == 8) Color(0xFF10B981) else Color(0xFF475569))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF334155)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🛺 360°", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text("2. Foto 360° de tu TucTuc (8 ángulos) *", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (vehicle360Bitmaps.size == 8) "✓ 8 de 8 ángulos listos (Toca para re-escanear)" else "Toma el teléfono y rodea el vehículo",
                                color = if (vehicle360Bitmaps.size == 8) Color(0xFF10B981) else Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }

                        Icon(
                            imageVector = if (vehicle360Bitmaps.size == 8) Icons.Default.CheckCircle else Icons.Default.FlipCameraAndroid,
                            contentDescription = null,
                            tint = if (vehicle360Bitmaps.size == 8) Color(0xFF10B981) else Color(0xFF00E5FF)
                        )
                    }

                    if (vehicle360Bitmaps.size == 8) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Vista previa interactiva (arrastra con el dedo):",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Vehicle360Viewer(
                            localBitmaps = vehicle360Bitmaps,
                            autoSpinOnStart = false,
                            modifier = Modifier.height(170.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

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

            // Botón de Registro con estado de carga uniforme
            Button(
                onClick = handleRegister,
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
                        text = "Registrarme",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = Color.White.copy(alpha = 0.15f))
                Text(
                    text = "O BIEN",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = Color.White.copy(alpha = 0.15f))
            }

            Spacer(modifier = Modifier.height(18.dp))

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
                                            onRegisterSuccess()
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
                text = "Registrarse con Google",
                isLoading = isGoogleLoading,
                enabled = !isLoading && !isGoogleLoading
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Enlace para volver a Iniciar Sesión
            Row(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "¿Ya tienes una cuenta? ",
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
                Text(
                    text = "Inicia sesión aquí",
                    color = Color(0xFFF9D423),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.clickable { onNavigateToLogin() }
                )
            }
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
                onConfirm = { nombre, telefono, placas, numeroUnidad, driverPhoto, vehicle360 ->
                    dialogLoading = true
                    dialogError = null
                    scope.launch {
                        try {
                            val driverBytes = driverPhoto?.let { bmp ->
                                withContext(Dispatchers.Default) { CameraUtils.compressBitmap(bmp) }
                            }
                            val v360BytesList = if (vehicle360.isNotEmpty()) {
                                withContext(Dispatchers.Default) { vehicle360.map { CameraUtils.compressBitmap(it) } }
                            } else null

                            val regResult = repository.completeDriverGoogleRegistration(
                                nombre = nombre,
                                telefono = telefono,
                                placas = placas,
                                numeroUnidad = numeroUnidad,
                                driverPhotoBytes = driverBytes,
                                vehicle360BytesList = v360BytesList
                            )
                            if (regResult.isSuccess) {
                                pendingGoogleProfile = null
                                onRegisterSuccess()
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

        // Pantalla de Cámara Nativa Integrada para Captura de Selfie o 360°
        if (activeCameraMode != null) {
            NativeCameraScreen(
                mode = activeCameraMode!!,
                onDismiss = { activeCameraMode = null },
                onDriverPhotoCaptured = { bmp ->
                    driverPhotoBitmap = bmp
                    activeCameraMode = null
                    errorMessage = null
                },
                onVehicle360Captured = { list ->
                    vehicle360Bitmaps = list
                    activeCameraMode = null
                    errorMessage = null
                }
            )
        }
    }
}

