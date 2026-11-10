package com.example.tuctucmap.ui.components

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.tuctucmap.ui.components.camera.CameraCaptureMode
import com.example.tuctucmap.ui.components.camera.NativeCameraScreen

@Composable
fun CompleteDriverGoogleRegistrationDialog(
    initialName: String,
    googleEmail: String,
    onDismiss: () -> Unit,
    onConfirm: (nombre: String, telefono: String, placas: String, numeroUnidad: String, driverPhoto: Bitmap?, vehicle360: List<Bitmap>) -> Unit,
    isLoading: Boolean = false,
    errorMessage: String? = null
) {
    val context = LocalContext.current
    var nombre by remember { mutableStateOf(initialName) }
    var telefono by remember { mutableStateOf("") }
    var placas by remember { mutableStateOf("") }
    var numeroUnidad by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    // Estados para captura obligatoria de fotos
    var driverPhotoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var vehicle360Bitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var activeCameraMode by remember { mutableStateOf<CameraCaptureMode?>(null) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            localError = "El permiso de cámara es requerido para tomar las fotos obligatorias."
        }
    }

    val openCamera: (CameraCaptureMode) -> Unit = { mode ->
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            activeCameraMode = mode
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Dialog(
        onDismissRequest = { if (!isLoading && activeCameraMode == null) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .wrapContentHeight()
                    .align(Alignment.Center)
                    .imePadding(),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1E293B),
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🛺 Completa tu Registro",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF9D423),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Cuenta de Google conectada:\n$googleEmail",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Nombre
                    OutlinedTextField(
                        value = nombre,
                        onValueChange = { nombre = it; localError = null },
                        label = { Text("Nombre Completo") },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF94A3B8))
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFF9D423),
                            unfocusedBorderColor = Color(0xFF475569),
                            focusedLabelColor = Color(0xFFF9D423),
                            unfocusedLabelColor = Color(0xFF94A3B8)
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Teléfono
                    OutlinedTextField(
                        value = telefono,
                        onValueChange = { telefono = it; localError = null },
                        label = { Text("Teléfono / WhatsApp *") },
                        leadingIcon = {
                            Icon(Icons.Default.Phone, contentDescription = null, tint = Color(0xFF94A3B8))
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFF9D423),
                            unfocusedBorderColor = Color(0xFF475569),
                            focusedLabelColor = Color(0xFFF9D423),
                            unfocusedLabelColor = Color(0xFF94A3B8)
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Placas
                    OutlinedTextField(
                        value = placas,
                        onValueChange = { placas = it.uppercase(); localError = null },
                        label = { Text("Placa del TucTuc * (Ej: M-123XYZ)") },
                        leadingIcon = {
                            Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = Color(0xFF94A3B8))
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFF9D423),
                            unfocusedBorderColor = Color(0xFF475569),
                            focusedLabelColor = Color(0xFFF9D423),
                            unfocusedLabelColor = Color(0xFF94A3B8)
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Número de Unidad (Opcional)
                    OutlinedTextField(
                        value = numeroUnidad,
                        onValueChange = { numeroUnidad = it },
                        label = { Text("Número de Unidad (Opcional)") },
                        leadingIcon = {
                            Icon(Icons.Default.Numbers, contentDescription = null, tint = Color(0xFF94A3B8))
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFF9D423),
                            unfocusedBorderColor = Color(0xFF475569),
                            focusedLabelColor = Color(0xFFF9D423),
                            unfocusedLabelColor = Color(0xFF94A3B8)
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Sección Fotos Obligatorias
                    Text(
                        text = "Fotografías Obligatorias de Verificación",
                        color = Color(0xFFF9D423),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Foto Conductor
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openCamera(CameraCaptureMode.DRIVER_PORTRAIT) },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        border = BorderStroke(1.dp, if (driverPhotoBitmap != null) Color(0xFF10B981) else Color(0xFF475569))
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (driverPhotoBitmap != null) {
                                Image(
                                    bitmap = driverPhotoBitmap!!.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .border(1.5.dp, Color(0xFF10B981), CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color(0xFFF9D423), modifier = Modifier.size(24.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Foto de Rostro *", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    if (driverPhotoBitmap != null) "✓ Capturada" else "Toca para tomar selfie",
                                    color = if (driverPhotoBitmap != null) Color(0xFF10B981) else Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }
                            Icon(
                                imageVector = if (driverPhotoBitmap != null) Icons.Default.CheckCircle else Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = if (driverPhotoBitmap != null) Color(0xFF10B981) else Color.Gray
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Foto 360° Vehículo
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openCamera(CameraCaptureMode.VEHICLE_360_SCAN) },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        border = BorderStroke(1.dp, if (vehicle360Bitmaps.size == 4) Color(0xFF10B981) else Color(0xFF475569))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("🛺 360°", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Foto 360° del Vehículo *", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        if (vehicle360Bitmaps.size == 4) "✓ 4 de 4 ángulos capturados" else "Rodea el TucTuc tomando 4 fotos",
                                        color = if (vehicle360Bitmaps.size == 4) Color(0xFF10B981) else Color(0xFF94A3B8),
                                        fontSize = 11.sp
                                    )
                                }
                                Icon(
                                    imageVector = if (vehicle360Bitmaps.size == 4) Icons.Default.CheckCircle else Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = if (vehicle360Bitmaps.size == 4) Color(0xFF10B981) else Color.Gray
                                )
                            }

                            if (vehicle360Bitmaps.size == 4) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Vehicle360Viewer(
                                    localBitmaps = vehicle360Bitmaps,
                                    modifier = Modifier.height(140.dp)
                                )
                            }
                        }
                    }

                    val displayError = localError ?: errorMessage
                    if (!displayError.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = displayError,
                            color = Color(0xFFFF6B6B),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Acciones
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            enabled = !isLoading,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8))
                        ) {
                            Text("Cancelar")
                        }

                        Button(
                            onClick = {
                                if (nombre.isBlank() || telefono.isBlank() || placas.isBlank()) {
                                    localError = "Completa tu nombre, teléfono y número de placa"
                                    return@Button
                                }
                                if (driverPhotoBitmap == null) {
                                    localError = "Debes tomar tu foto de conductor obligatoriamente"
                                    return@Button
                                }
                                if (vehicle360Bitmaps.size < 4) {
                                    localError = "Debes completar las 4 fotos 360° del vehículo"
                                    return@Button
                                }
                                onConfirm(nombre.trim(), telefono.trim(), placas.trim(), numeroUnidad.trim(), driverPhotoBitmap, vehicle360Bitmaps)
                            },
                            enabled = !isLoading,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFF9D423),
                                contentColor = Color(0xFF0F2027)
                            )
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF0F2027)
                                )
                            } else {
                                Text("Guardar", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Vista de Cámara Nativa en diálogo
            if (activeCameraMode != null) {
                NativeCameraScreen(
                    mode = activeCameraMode!!,
                    onDismiss = { activeCameraMode = null },
                    onDriverPhotoCaptured = { bmp ->
                        driverPhotoBitmap = bmp
                        activeCameraMode = null
                        localError = null
                    },
                    onVehicle360Captured = { list ->
                        vehicle360Bitmaps = list
                        activeCameraMode = null
                        localError = null
                    }
                )
            }
        }
    }
}
