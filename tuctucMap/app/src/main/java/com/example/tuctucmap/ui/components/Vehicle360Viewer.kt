package com.example.tuctucmap.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tuctucmap.ui.components.camera.VEHICLE_360_STEPS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

// Cache estática en memoria para fotogramas 360 ya descargados (evita re-descargar al reabrir el visor)
private val bitmapMemoryCache = mutableMapOf<String, Bitmap>()

@Composable
fun Vehicle360Viewer(
    modifier: Modifier = Modifier,
    imageUrls: List<String> = emptyList(),
    localBitmaps: List<Bitmap> = emptyList(),
    autoSpinOnStart: Boolean = false,
    contentDescription: String = "Visor 360° del TucTuc"
) {
    val scope = rememberCoroutineScope()
    var currentAngleIndex by remember { mutableIntStateOf(0) }
    var loadedBitmaps by remember { mutableStateOf<List<Bitmap?>>(emptyList()) }
    var isLoadingRemote by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var isAutoSpinning by remember { mutableStateOf(false) }
    var showDragHint by remember { mutableStateOf(true) }

    // Determinar fuente de imágenes (locales de la cámara o remotas de Supabase)
    val hasLocal = localBitmaps.isNotEmpty()
    val totalAngles = if (hasLocal) localBitmaps.size else imageUrls.size

    LaunchedEffect(imageUrls, localBitmaps) {
        if (hasLocal) {
            loadedBitmaps = localBitmaps
            isLoadingRemote = false
            loadError = null
        } else if (imageUrls.isNotEmpty()) {
            isLoadingRemote = true
            loadError = null
            withContext(Dispatchers.IO) {
                try {
                    val result = imageUrls.map { url ->
                        bitmapMemoryCache[url] ?: run {
                            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                                connectTimeout = 10_000
                                readTimeout = 15_000
                                doInput = true
                            }
                            conn.connect()
                            val stream: InputStream = conn.inputStream
                            val bmp = BitmapFactory.decodeStream(stream)
                            stream.close()
                            conn.disconnect()
                            bmp?.also { bitmapMemoryCache[url] = it }
                        }
                    }
                    withContext(Dispatchers.Main) {
                        loadedBitmaps = result
                        isLoadingRemote = false
                        if (result.all { it == null }) {
                            loadError = "No se pudieron cargar las fotos del vehículo"
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isLoadingRemote = false
                        loadError = e.localizedMessage ?: "Error al descargar fotos"
                    }
                }
            }
        } else {
            loadedBitmaps = emptyList()
            isLoadingRemote = false
        }
    }

    // Auto-rotación opcional al iniciar
    LaunchedEffect(loadedBitmaps, autoSpinOnStart) {
        if (autoSpinOnStart && loadedBitmaps.size > 1 && !isAutoSpinning) {
            isAutoSpinning = true
            for (i in 0 until (loadedBitmaps.size * 2)) {
                delay(120)
                currentAngleIndex = (currentAngleIndex + 1) % loadedBitmaps.size
            }
            isAutoSpinning = false
        }
    }

    // Ocultar hint de deslizamiento después de 3.5 segundos o al interactuar
    LaunchedEffect(Unit) {
        delay(3500)
        showDragHint = false
    }

    val stepInfo = remember(currentAngleIndex, totalAngles) {
        if (currentAngleIndex in VEHICLE_360_STEPS.indices) {
            VEHICLE_360_STEPS[currentAngleIndex]
        } else {
            null
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0F172A))
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoadingRemote -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFFF9D423), strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Cargando modelo 360°...",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            loadError != null || totalAngles == 0 || loadedBitmaps.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = loadError ?: "Sin fotos 360° registradas para este vehículo",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            else -> {
                val currentBitmap = loadedBitmaps.getOrNull(currentAngleIndex)
                var accumulatedDrag by remember { mutableFloatStateOf(0f) }
                val dragThreshold = with(LocalDensity.current) { 26.dp.toPx() }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .pointerInput(totalAngles) {
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    showDragHint = false
                                    isAutoSpinning = false
                                },
                                onHorizontalDrag = { _, dragAmount ->
                                    accumulatedDrag += dragAmount
                                    if (accumulatedDrag > dragThreshold) {
                                        // Arrastre hacia la derecha -> rotar en sentido antihorario
                                        currentAngleIndex = if (currentAngleIndex - 1 < 0) totalAngles - 1 else currentAngleIndex - 1
                                        accumulatedDrag = 0f
                                    } else if (accumulatedDrag < -dragThreshold) {
                                        // Arrastre hacia la izquierda -> rotar en sentido horario
                                        currentAngleIndex = (currentAngleIndex + 1) % totalAngles
                                        accumulatedDrag = 0f
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (currentBitmap != null) {
                        Image(
                            bitmap = currentBitmap.asImageBitmap(),
                            contentDescription = contentDescription,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF1E293B)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Fotograma no disponible", color = Color.Gray, fontSize = 12.sp)
                        }
                    }

                    // Gradiente sutil en parte inferior para legibilidad de insignias
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(70.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color(0xCC000000))
                                )
                            )
                    )

                    // Insignia superior con ángulo actual y botón de auto-giro
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .align(Alignment.TopCenter),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xDD0F172A),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF9D423))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("🛺 360°", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF9D423))
                                Text(
                                    text = stepInfo?.let { "${it.guideTag} • ${it.title}" } ?: "Ángulo ${currentAngleIndex + 1}/$totalAngles",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }

                        // Botón de auto-giro demo
                        Surface(
                            shape = CircleShape,
                            color = Color(0xDD0F172A),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF475569)),
                            modifier = Modifier.size(36.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    if (!isAutoSpinning && totalAngles > 1) {
                                        scope.launch {
                                            isAutoSpinning = true
                                            showDragHint = false
                                            for (step in 0 until totalAngles) {
                                                delay(120)
                                                currentAngleIndex = (currentAngleIndex + 1) % totalAngles
                                            }
                                            isAutoSpinning = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Autorenew,
                                    contentDescription = "Girar 360° automáticamente",
                                    tint = if (isAutoSpinning) Color(0xFFF9D423) else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Puntos indicadores de ángulo en la parte inferior
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(totalAngles) { index ->
                            val isSelected = index == currentAngleIndex
                            Box(
                                modifier = Modifier
                                    .size(if (isSelected) 8.dp else 5.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) Color(0xFFF9D423) else Color(0x66FFFFFF)
                                    )
                            )
                        }
                    }

                    // Cartel animado de sugerencia táctil ("← Desliza para girar →")
                    AnimatedVisibility(
                        visible = showDragHint && !isAutoSpinning,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xCC000000),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66FFFFFF))
                        ) {
                            Text(
                                text = "👆 Desliza con el dedo para girar 360°",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

