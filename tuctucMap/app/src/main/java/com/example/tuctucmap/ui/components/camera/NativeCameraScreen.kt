package com.example.tuctucmap.ui.components.camera

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.tuctucmap.ui.components.Vehicle360Viewer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

enum class CameraCaptureMode {
    DRIVER_PORTRAIT,
    VEHICLE_360_SCAN
}

@Composable
fun NativeCameraScreen(
    mode: CameraCaptureMode,
    onDismiss: () -> Unit,
    onDriverPhotoCaptured: (Bitmap) -> Unit = {},
    onVehicle360Captured: (List<Bitmap>) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var lensFacing by remember {
        mutableStateOf(
            if (mode == CameraCaptureMode.DRIVER_PORTRAIT) CameraSelector.LENS_FACING_FRONT
            else CameraSelector.LENS_FACING_BACK
        )
    }

    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var previewView: PreviewView? by remember { mutableStateOf(null) }
    var isCapturing by remember { mutableStateOf(false) }

    // Estado para modo foto conductor
    var capturedDriverPhoto by remember { mutableStateOf<Bitmap?>(null) }

    // Estado para modo 360 vehículo (8 ángulos)
    var currentStepIndex by remember { mutableIntStateOf(0) }
    val capturedVehicleBitmaps = remember { mutableStateListOf<Bitmap?>().apply { repeat(8) { add(null) } } }
    var show360VerificationPreview by remember { mutableStateOf(false) }

    // Vincular CameraX cuando cambia lensFacing
    LaunchedEffect(lensFacing) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val cameraProvider = withContext(Dispatchers.IO) { cameraProviderFuture.get() }

        val preview = Preview.Builder().build().also {
            previewView?.let { pv -> it.setSurfaceProvider(pv.surfaceProvider) }
        }

        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                capture
            )
            imageCapture = capture
        } catch (e: Exception) {
            android.util.Log.e("NativeCamera", "Error al vincular cámara: ${e.message}")
        }
    }

    fun takePhoto(onBitmapReady: (Bitmap) -> Unit) {
        val capture = imageCapture ?: return
        if (isCapturing) return
        isCapturing = true

        capture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    scope.launch(Dispatchers.Default) {
                        try {
                            val buffer: ByteBuffer = image.planes[0].buffer
                            val bytes = ByteArray(buffer.remaining())
                            buffer.get(bytes)
                            val bitmap = CameraUtils.fixExifOrientation(bytes)
                            withContext(Dispatchers.Main) {
                                isCapturing = false
                                onBitmapReady(bitmap)
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) { isCapturing = false }
                        } finally {
                            image.close()
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    isCapturing = false
                }
            }
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Vista de confirmación previa del Conductor (Selfie)
    // ─────────────────────────────────────────────────────────────────────────
    if (mode == CameraCaptureMode.DRIVER_PORTRAIT && capturedDriverPhoto != null) {
        val photo = capturedDriverPhoto!!
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A))
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Verifica tu Foto de Conductor",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF9D423)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Asegúrate de que tu rostro se observe claramente y sin sombras",
                    fontSize = 13.sp,
                    color = Color.LightGray,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))

                Image(
                    bitmap = photo.asImageBitmap(),
                    contentDescription = "Foto capturada",
                    modifier = Modifier
                        .size(280.dp)
                        .clip(CircleShape)
                        .border(3.dp, Color(0xFFF9D423), CircleShape)
                )

                Spacer(modifier = Modifier.height(32.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = { capturedDriverPhoto = null },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Repetir")
                    }

                    Button(
                        onClick = { onDriverPhotoCaptured(photo) },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF9D423))
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Aceptar", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        return
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Vista de verificación 360° interactiva antes de guardar
    // ─────────────────────────────────────────────────────────────────────────
    if (mode == CameraCaptureMode.VEHICLE_360_SCAN && show360VerificationPreview) {
        val nonNullBitmaps = capturedVehicleBitmaps.filterNotNull()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "🎉 ¡Escaneo 360° Completado!",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF9D423)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Prueba mover el vehículo con el dedo para verificar los 8 ángulos:",
                    fontSize = 13.sp,
                    color = Color.LightGray,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))

                Vehicle360Viewer(
                    localBitmaps = nonNullBitmaps,
                    autoSpinOnStart = true,
                    modifier = Modifier.height(280.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            show360VerificationPreview = false
                            currentStepIndex = 0
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Re-escanear")
                    }

                    Button(
                        onClick = { onVehicle360Captured(nonNullBitmaps) },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Guardar 360°", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        return
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Pantalla de Cámara Nativa en Vivo con Guías Visuales
    // ─────────────────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Vista previa nativa de la cámara
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    previewView = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Barra superior de controles (Cerrar y Cambiar Cámara)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 16.dp, end = 16.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(44.dp)
                    .background(Color(0x88000000), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
            }

            // Cambiar lente cámara (frontal/trasera)
            IconButton(
                onClick = {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }
                },
                modifier = Modifier
                    .size(44.dp)
                    .background(Color(0x88000000), CircleShape)
            ) {
                Icon(Icons.Default.FlipCameraAndroid, contentDescription = "Cambiar cámara", tint = Color.White)
            }
        }

        // Overlay central según el modo
        if (mode == CameraCaptureMode.DRIVER_PORTRAIT) {
            // Silueta / Guía de Rostro
            Box(
                modifier = Modifier
                    .size(260.dp, 330.dp)
                    .align(Alignment.Center)
                    .border(2.dp, Color(0xFFF9D423), RoundedCornerShape(140.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Ubica tu rostro dentro del marco",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .background(Color(0x99000000), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        } else {
            // Guía del Escaneo 360 del Vehículo
            val step = VEHICLE_360_STEPS.getOrElse(currentStepIndex) { VEHICLE_360_STEPS[0] }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 96.dp, start = 16.dp, end = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xCC0F172A),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF9D423))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "🛺 Paso ${currentStepIndex + 1} de 8 • ${step.guideTag} ${step.title}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF9D423)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = step.instruction,
                            fontSize = 12.sp,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Marco guía para el vehículo
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.90f)
                    .height(280.dp)
                    .align(Alignment.Center)
                    .border(2.dp, Color(0xAA00E5FF), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Encuadra el TucTuc completo",
                    color = Color(0xFF00E5FF),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .background(Color(0x88000000), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        // Barra inferior: Miniaturas (en modo 360) y Botón de Captura
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xEE000000))
                    )
                )
                .padding(bottom = 36.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (mode == CameraCaptureMode.VEHICLE_360_SCAN) {
                // Tira de miniaturas de los 8 pasos
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(VEHICLE_360_STEPS) { index, s ->
                        val bmp = capturedVehicleBitmaps.getOrNull(index)
                        val isCurrent = index == currentStepIndex
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E293B))
                                .border(
                                    width = if (isCurrent) 2.dp else 1.dp,
                                    color = if (isCurrent) Color(0xFFF9D423) else if (bmp != null) Color(0xFF10B981) else Color(0xFF475569),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    currentStepIndex = index
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .align(Alignment.TopEnd)
                                        .background(Color(0xFF10B981), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                }
                            } else {
                                Text(s.guideTag, fontSize = 10.sp, color = Color.LightGray)
                            }
                        }
                    }
                }
            }

            // Disparador de foto central
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(Color(0x44FFFFFF))
                    .clickable(enabled = !isCapturing) {
                        takePhoto { bitmap ->
                            if (mode == CameraCaptureMode.DRIVER_PORTRAIT) {
                                capturedDriverPhoto = bitmap
                            } else {
                                capturedVehicleBitmaps[currentStepIndex] = bitmap
                                // Avanzar al siguiente ángulo si no es el último
                                if (currentStepIndex < 7) {
                                    currentStepIndex++
                                } else {
                                    // Todos los ángulos completados
                                    if (capturedVehicleBitmaps.all { it != null }) {
                                        show360VerificationPreview = true
                                    }
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isCapturing) {
                    CircularProgressIndicator(color = Color(0xFFF9D423), modifier = Modifier.size(40.dp))
                } else {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(if (mode == CameraCaptureMode.DRIVER_PORTRAIT) Color(0xFFF9D423) else Color(0xFF00E5FF))
                    )
                }
            }
        }
    }
}

