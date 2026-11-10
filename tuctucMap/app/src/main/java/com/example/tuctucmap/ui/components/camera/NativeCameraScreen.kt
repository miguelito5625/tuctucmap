package com.example.tuctucmap.ui.components.camera

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.draw.clipToBounds
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
import kotlinx.coroutines.delay
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

    // Estado para modo 360 vehículo (4 ángulos)
    var isScanStarted by remember { mutableStateOf(false) }
    var currentStepIndex by remember { mutableIntStateOf(0) }
    val capturedVehicleBitmaps = remember { mutableStateListOf<Bitmap?>().apply { repeat(4) { add(null) } } }
    var show360VerificationPreview by remember { mutableStateOf(false) }

    // Estados para asistencia guiada por Giroscopio / Orientación
    var relativeAngle by remember { mutableFloatStateOf(0f) }
    var isSensorAvailable by remember { mutableStateOf(true) }
    var isAutoCaptureEnabled by remember { mutableStateOf(true) }
    var alignmentProgress by remember { mutableFloatStateOf(0f) }
    var showFlashEffect by remember { mutableStateOf(false) }

    // Instancia del gestor de giroscopio
    val gyroManager = remember(context) {
        GyroscopeGuideManager(context) { angle, available ->
            relativeAngle = angle
            isSensorAvailable = available
        }
    }

    // Inicializar y detener escucha del giroscopio
    DisposableEffect(mode) {
        if (mode == CameraCaptureMode.VEHICLE_360_SCAN) {
            gyroManager.startListening()
        }
        onDispose {
            gyroManager.stopListening()
        }
    }

    // Servicio de vibración para confirmación sensorial
    val vibrator = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator ?: (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun triggerSnapFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(120)
            }
        } catch (_: Exception) {}
    }

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

        val isFrontCamera = lensFacing == CameraSelector.LENS_FACING_FRONT

        capture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val rotation = image.imageInfo.rotationDegrees
                    scope.launch(Dispatchers.Default) {
                        try {
                            val buffer: ByteBuffer = image.planes[0].buffer
                            val bytes = ByteArray(buffer.remaining())
                            buffer.get(bytes)
                            var bitmap = CameraUtils.fixExifOrientation(bytes, fallbackRotation = rotation)
                            // Si se capturó con la cámara frontal, reflejar horizontalmente (modo espejo)
                            // para que la imagen final sea idéntica a lo que el conductor vio en el visor antes de disparar
                            if (isFrontCamera) {
                                bitmap = CameraUtils.flipHorizontal(bitmap)
                            }
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

    // Ángulo objetivo del paso actual
    val currentTargetAngle = remember(currentStepIndex) {
        VEHICLE_360_STEPS.getOrElse(currentStepIndex) { VEHICLE_360_STEPS[0] }.angleDegrees.toFloat()
    }

    val isCurrentlyAligned = remember(relativeAngle, currentTargetAngle, isSensorAvailable) {
        isSensorAvailable && GyroscopeGuideManager.isWithinTolerance(relativeAngle, currentTargetAngle, toleranceDegrees = 12f)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Temporizador de disparo automático por giroscopio:
    // Al alinearse con el ángulo objetivo por 320ms, dispara la captura automáticamente.
    // Solo inicia cuando el usuario ha presionado "¡Estoy listo!".
    // ─────────────────────────────────────────────────────────────────────────
    LaunchedEffect(isScanStarted, isCurrentlyAligned, currentStepIndex, isCapturing, isAutoCaptureEnabled, show360VerificationPreview) {
        if (mode == CameraCaptureMode.VEHICLE_360_SCAN &&
            isScanStarted &&
            isCurrentlyAligned &&
            !isCapturing &&
            isAutoCaptureEnabled &&
            !show360VerificationPreview
        ) {
            for (step in 1..8) {
                delay(40)
                alignmentProgress = step / 8f
            }

            takePhoto { bitmap ->
                capturedVehicleBitmaps[currentStepIndex] = bitmap
                triggerSnapFeedback()
                scope.launch {
                    showFlashEffect = true
                    delay(140)
                    showFlashEffect = false
                }
                alignmentProgress = 0f
                if (currentStepIndex < 3) {
                    currentStepIndex++
                } else {
                    if (capturedVehicleBitmaps.all { it != null }) {
                        show360VerificationPreview = true
                    }
                }
            }
        } else {
            alignmentProgress = 0f
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Vista de confirmación previa del Conductor (Selfie 1:1)
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
                    text = "Foto en formato cuadrado 1:1 para tu perfil oficial",
                    fontSize = 13.sp,
                    color = Color.LightGray,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))

                Image(
                    bitmap = photo.asImageBitmap(),
                    contentDescription = "Foto capturada 1:1",
                    modifier = Modifier
                        .size(280.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(3.dp, Color(0xFFF9D423), RoundedCornerShape(16.dp))
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
    // Vista de verificación 360° interactiva antes de guardar (4 ángulos)
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
                    text = "Prueba mover el vehículo con el dedo para verificar los 4 ángulos capturados:",
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
                            for (i in 0 until capturedVehicleBitmaps.size) {
                                capturedVehicleBitmaps[i] = null
                            }
                            isScanStarted = false
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
    // Modo 1: Foto del Conductor (Relación de Aspecto 1:1, Sin Guía de Rostro)
    // ─────────────────────────────────────────────────────────────────────────
    if (mode == CameraCaptureMode.DRIVER_PORTRAIT) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Visor de Cámara con Relación de Aspecto 1:1 (cuadrada)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .align(Alignment.Center)
                    .clipToBounds()
            ) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            previewView = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Destello visual de obturador
                AnimatedVisibility(
                    visible = showFlashEffect,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White.copy(alpha = 0.65f))
                    )
                }
            }

            // Barra superior de controles
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

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xAA0F172A),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF9D423))
                ) {
                    Text(
                        text = "Foto del Conductor (1:1)",
                        color = Color(0xFFF9D423),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

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

            // Barra inferior con botón disparador
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 44.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Encuadra tu rostro en el recuadro cuadrado",
                    color = Color.LightGray,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(Color(0x44FFFFFF))
                        .clickable(enabled = !isCapturing) {
                            takePhoto { bitmap ->
                                triggerSnapFeedback()
                                scope.launch {
                                    showFlashEffect = true
                                    delay(140)
                                    showFlashEffect = false
                                }
                                capturedDriverPhoto = CameraUtils.cropToSquare(bitmap)
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
                                .background(Color(0xFFF9D423))
                        )
                    }
                }
            }
        }
        return
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Modo 2: Escaneo 360° del Vehículo (4 Ángulos, Inicio Manual Guiado por Giroscopio)
    // ─────────────────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Vista previa nativa de la cámara a pantalla completa
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    previewView = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Destello visual de obturador al disparar automáticamente
        AnimatedVisibility(
            visible = showFlashEffect,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.65f))
            )
        }

        // Controles superiores de la cámara
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

                // Botón de calibración a 0° inicial
                Surface(
                    onClick = {
                        gyroManager.calibrateInitialHeading()
                        alignmentProgress = 0f
                    },
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xAA0F172A),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF9D423))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Explore, contentDescription = null, tint = Color(0xFFF9D423), modifier = Modifier.size(16.dp))
                        Text("Calibrar 0°", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                // Switch de disparo automático
                Surface(
                    onClick = { isAutoCaptureEnabled = !isAutoCaptureEnabled },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isAutoCaptureEnabled) Color(0xCC10B981) else Color(0xAA334155)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isAutoCaptureEnabled) Icons.Default.Bolt else Icons.Default.TouchApp,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isAutoCaptureEnabled) "Auto: SÍ" else "Manual",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
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

            // Guía del Escaneo 360 del Vehículo asistida por Giroscopio
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
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "🛺 Paso ${currentStepIndex + 1} de 4 • ${step.guideTag} ${step.title}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF9D423)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (!isScanStarted) {
                                "Encuadra el frente del vehículo y presiona el botón cuando estés listo"
                            } else if (isAutoCaptureEnabled) {
                                "Camina rodeando el vehículo. La foto se tomará sola al alinearte."
                            } else {
                                step.instruction
                            },
                            fontSize = 12.sp,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Brújula / Radar Giroscópico Interactivo (4 cuadrantes)
                OrbitGyroscopeHud(
                    relativeAngle = relativeAngle,
                    targetAngle = currentTargetAngle,
                    isAligned = isCurrentlyAligned,
                    alignmentProgress = alignmentProgress,
                    isSensorAvailable = isSensorAvailable
                )
            }

            // Marco guía para el vehículo
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.90f)
                    .height(240.dp)
                    .align(Alignment.Center)
                    .border(
                        width = if (isCurrentlyAligned && isScanStarted) 2.5.dp else 1.5.dp,
                        color = if (isCurrentlyAligned && isScanStarted) Color(0xFF10B981) else Color(0x6600E5FF),
                        shape = RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (!isScanStarted) {
                    Text(
                        text = "Encuadra el frente del vehículo",
                        color = Color(0xFFF9D423),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .background(Color(0x88000000), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                } else if (isCurrentlyAligned) {
                    Surface(
                        color = Color(0xCC10B981),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "📸 ¡TOMANDO FOTO AUTOMÁTICA!",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                } else {
                    Text(
                        text = "Mantén el vehículo centrado",
                        color = Color(0xFF00E5FF),
                        fontSize = 11.sp,
                        modifier = Modifier
                            .background(Color(0x88000000), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Barra inferior: Miniaturas de los 4 pasos y Botón de Captura manual
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
                // Tira de miniaturas de los 4 pasos
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(VEHICLE_360_STEPS) { index, s ->
                        val bmp = capturedVehicleBitmaps.getOrNull(index)
                        val isCurrent = index == currentStepIndex
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E293B))
                                .border(
                                    width = if (isCurrent) 2.dp else 1.dp,
                                    color = if (isCurrent) Color(0xFFF9D423) else if (bmp != null) Color(0xFF10B981) else Color(0xFF475569),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    currentStepIndex = index
                                    alignmentProgress = 0f
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
                                Text(s.guideTag, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.LightGray)
                            }
                        }
                    }
                }

                // Disparador manual
                if (!isScanStarted) {
                    Text(
                        text = "📸 Presiona para capturar Frente e iniciar 360°",
                        color = Color(0xFFF9D423),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(bottom = 10.dp)
                            .background(Color(0xCC0F172A), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFFF9D423).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(Color(0x44FFFFFF))
                        .clickable(enabled = !isCapturing) {
                            takePhoto { bitmap ->
                                triggerSnapFeedback()
                                capturedVehicleBitmaps[currentStepIndex] = bitmap
                                scope.launch {
                                    showFlashEffect = true
                                    delay(140)
                                    showFlashEffect = false
                                }
                                if (!isScanStarted) {
                                    gyroManager.calibrateInitialHeading()
                                    isScanStarted = true
                                }
                                if (currentStepIndex < 3) {
                                    currentStepIndex++
                                } else {
                                    if (capturedVehicleBitmaps.all { it != null }) {
                                        show360VerificationPreview = true
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
                                .background(
                                    when {
                                        !isScanStarted -> Color(0xFFF9D423)
                                        isCurrentlyAligned -> Color(0xFF10B981)
                                        else -> Color(0xFF00E5FF)
                                    }
                                )
                        )
                    }
                }
            }
        }
    }
