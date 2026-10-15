package com.example.tuctucmap.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tuctucmap.R
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Representación de un puff de humo generado en la llanta trasera durante
 * el ralentí, el patinado (burnout) y la aceleración de salida.
 */
private data class SmokePuffDef(
    val spawnProgress: Float,
    val duration: Float,
    val initialRadiusDp: Float,
    val maxRadiusDp: Float,
    val driftXDp: Float,
    val driftYDp: Float,
    val baseAlpha: Float,
    val color: Color
)

@Composable
fun AnimatedSplashScreen(
    modifier: Modifier = Modifier,
    onAnimationFinished: () -> Unit
) {
    val animProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 2350, easing = LinearEasing)
        )
        onAnimationFinished()
    }

    val p = animProgress.value
    val density = LocalDensity.current

    // Constantes de dimensiones
    val vehicleWidthDp = 195.dp
    val vehicleHeightDp = 152.dp

    // --- CÁLCULO DE FÍSICAS DE MOVIMIENTO ---

    // 1. Desplazamiento horizontal (Offset X)
    val vehicleOffsetX = when {
        // Fase de Entrada (0.00 -> 0.28): Llega rápido desde la derecha y frena en el centro
        p < 0.28f -> {
            val pe = (p / 0.28f).coerceIn(0f, 1f)
            // Curva EaseOutCubic: frena suavemente al llegar al centro
            val factor = 1f - (1f - pe).pow(3)
            (360.dp * (1f - factor))
        }
        // Fase Central (0.28 -> 0.78): Ralentí, texto y aceleración en el sitio
        p < 0.78f -> 0.dp
        // Fase de Salida (0.78 -> 1.00): Sale disparado a máxima aceleración hacia la izquierda
        else -> {
            val px = ((p - 0.78f) / 0.22f).coerceIn(0f, 1f)
            // Curva EaseInQuad / Expo: aceleración progresiva
            val factor = px * px * (1.2f - 0.2f * px)
            (-500.dp * factor)
        }
    }

    // 2. Inclinación de chasis (Pitch / Rotation)
    val vehicleRotation = when {
        // Al frenar en entrada: ligera inclinación hacia adelante (-2° -> 0°)
        p < 0.28f -> {
            val pe = p / 0.28f
            -2.5f * (1f - pe)
        }
        // Ralentí con vibración del motor
        p < 0.58f -> {
            sin(p * 180f) * 0.5f
        }
        // Quemada de llanta / Aceleración inicial: se levanta el frente por torque (-5° squat)
        p < 0.78f -> {
            val pb = (p - 0.58f) / 0.20f
            val squat = -5.0f * sin(pb * PI.toFloat())
            squat + sin(p * 320f) * 0.9f
        }
        // Disparo de salida
        else -> {
            val px = (p - 0.78f) / 0.22f
            -4.0f * (1f - px)
        }
    }

    // 3. Vibración vertical por ralentí del motor (Offset Y)
    val vehicleVibrationY = when {
        p in 0.28f..0.58f -> {
            (sin(p * 140f) * 1.5f).dp
        }
        p in 0.58f..0.78f -> {
            // Vibración más intensa al acelerar el motor
            (sin(p * 280f) * 2.8f).dp
        }
        else -> 0.dp
    }

    // 4. Desvanecimiento general al concluir la animación
    val overallAlpha = when {
        p > 0.92f -> ((1f - p) / 0.08f).coerceIn(0f, 1f)
        else -> 1f
    }

    // Humos precalculados de la llanta trasera (detonados según progreso p)
    val smokePuffs = remember {
        listOf(
            // Humos de frenada / entrada
            SmokePuffDef(0.22f, 0.22f, 6f, 22f, 18f, -12f, 0.40f, Color(0xFFD6E3E8)),
            SmokePuffDef(0.26f, 0.24f, 8f, 28f, 22f, -16f, 0.45f, Color(0xFFC7D7DC)),
            // Humos sutiles de ralentí
            SmokePuffDef(0.40f, 0.22f, 5f, 18f, 16f, -14f, 0.30f, Color(0xFFD6E3E8)),
            SmokePuffDef(0.50f, 0.22f, 6f, 20f, 18f, -16f, 0.35f, Color(0xFFC7D7DC)),
            // Humos intensos de aceleración / burnout (quema de llanta)
            SmokePuffDef(0.59f, 0.28f, 9f, 38f, 24f, -20f, 0.65f, Color(0xFFEFF5F8)),
            SmokePuffDef(0.63f, 0.28f, 12f, 44f, 28f, -24f, 0.70f, Color(0xFFDFE9ED)),
            SmokePuffDef(0.67f, 0.28f, 14f, 50f, 32f, -28f, 0.75f, Color(0xFFF3F8FA)),
            SmokePuffDef(0.71f, 0.26f, 16f, 56f, 38f, -32f, 0.75f, Color(0xFFE2ECF0)),
            SmokePuffDef(0.75f, 0.25f, 18f, 62f, 44f, -36f, 0.80f, Color(0xFFFFFFFF)),
            // Humo de estela de despegue
            SmokePuffDef(0.79f, 0.20f, 15f, 58f, 50f, -25f, 0.60f, Color(0xFFD2E1E6)),
            SmokePuffDef(0.83f, 0.17f, 12f, 48f, 60f, -20f, 0.50f, Color(0xFFBFD2D8))
        )
    }

    // Contenedor principal con el fondo nativo exacto #233439
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF233439))
            .alpha(overallAlpha),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Zona del vehículo + efectos de humo
            Box(
                modifier = Modifier
                    .width(vehicleWidthDp)
                    .height(vehicleHeightDp),
                contentAlignment = Alignment.Center
            ) {
                // Canvas de humo y skid marks renderizado debajo del mototaxi
                Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val w = size.width
                    val h = size.height

                    // Coordenadas relativas de la llanta trasera en el sprite:
                    // x ~ 81% del ancho, y ~ 85% de la altura
                    val rearWheelX = w * 0.81f
                    val rearWheelY = h * 0.85f
                    val groundY = h * 0.96f

                    // 1. Marca de derrape de llanta (skid mark en el asfalto) durante el burnout
                    if (p >= 0.60f) {
                        val skidFactor = ((p - 0.60f) / 0.18f).coerceIn(0f, 1f)
                        val skidAlpha = if (p < 0.85f) 0.5f * skidFactor else 0.5f * (1f - (p - 0.85f) / 0.15f)
                        val skidWidth = 45f * skidFactor
                        drawRoundRect(
                            color = Color(0xFF141F23).copy(alpha = skidAlpha),
                            topLeft = Offset(rearWheelX - 8f, groundY - 2f),
                            size = Size(skidWidth, 4f),
                            cornerRadius = CornerRadius(2f, 2f)
                        )
                    }

                    // 2. Renderizado de cada puff de humo generado
                    for (puff in smokePuffs) {
                        if (p >= puff.spawnProgress) {
                            val life = ((p - puff.spawnProgress) / puff.duration).coerceIn(0f, 1f)
                            if (life < 1f) {
                                val currentAlpha = puff.baseAlpha * (1f - life)
                                val initialR = with(density) { puff.initialRadiusDp.dp.toPx() }
                                val maxR = with(density) { puff.maxRadiusDp.dp.toPx() }
                                val currentR = initialR + (maxR - initialR) * sqrt(life)

                                val driftX = with(density) { puff.driftXDp.dp.toPx() } * life
                                val driftY = with(density) { puff.driftYDp.dp.toPx() } * life

                                // Si el vehículo ya aceleró hacia la izquierda (p > 0.78),
                                // el humo permanece en el suelo donde se generó
                                val smokeWorldOffsetX = if (p > 0.78f) {
                                    with(density) { -vehicleOffsetX.toPx() * (1f - life * 0.4f) }
                                } else 0f

                                val puffCenter = Offset(
                                    x = rearWheelX + driftX + smokeWorldOffsetX,
                                    y = rearWheelY + driftY
                                )

                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            puff.color.copy(alpha = currentAlpha),
                                            puff.color.copy(alpha = currentAlpha * 0.5f),
                                            puff.color.copy(alpha = 0f)
                                        ),
                                        center = puffCenter,
                                        radius = currentR
                                    ),
                                    radius = currentR,
                                    center = puffCenter
                                )
                            }
                        }
                    }

                    // 3. Líneas de velocidad / aceleración cuando arranca
                    if (p in 0.78f..0.92f) {
                        val speedFactor = ((p - 0.78f) / 0.14f).coerceIn(0f, 1f)
                        val speedAlpha = (1f - speedFactor) * 0.7f
                        val lineY1 = rearWheelY - 12f
                        val lineY2 = rearWheelY - 32f
                        val lineY3 = rearWheelY + 8f

                        drawLine(
                            color = Color.White.copy(alpha = speedAlpha),
                            start = Offset(rearWheelX + 15f, lineY1),
                            end = Offset(rearWheelX + 65f + 40f * speedFactor, lineY1),
                            strokeWidth = 3f
                        )
                        drawLine(
                            color = Color.White.copy(alpha = speedAlpha * 0.8f),
                            start = Offset(rearWheelX + 25f, lineY2),
                            end = Offset(rearWheelX + 85f + 50f * speedFactor, lineY2),
                            strokeWidth = 2.5f
                        )
                        drawLine(
                            color = Color.White.copy(alpha = speedAlpha * 0.6f),
                            start = Offset(rearWheelX + 10f, lineY3),
                            end = Offset(rearWheelX + 50f + 30f * speedFactor, lineY3),
                            strokeWidth = 2f
                        )
                    }
                }

                // Sprite del Mototaxi con desplazamiento, rotación y vibración
                Image(
                    painter = painterResource(id = R.drawable.splash_tuc_tuc),
                    contentDescription = "TucTuc Mototaxi",
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(x = vehicleOffsetX, y = vehicleVibrationY)
                        .rotate(vehicleRotation)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Texto "TucTuc Map" con animación de letras independientes
            AnimatedAppName(
                progress = p,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

/**
 * Letras animadas con aparición fluida, escalado suave y desaparición coordinada.
 */
@Composable
private fun AnimatedAppName(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val text = "TucTuc Map"
    val startReveal = 0.28f
    val letterStep = 0.026f

    // Desvanecimiento del texto durante la salida veloz
    val exitAlpha = when {
        progress >= 0.80f -> {
            (1f - (progress - 0.80f) / 0.14f).coerceIn(0f, 1f)
        }
        else -> 1f
    }

    Row(
        modifier = modifier.alpha(exitAlpha),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        text.forEachIndexed { index, char ->
            val charSpawn = startReveal + (index * letterStep)
            val charProgress = ((progress - charSpawn) / 0.08f).coerceIn(0f, 1f)

            // Letra animada (fade-in + slide-up + scale)
            val letterAlpha = charProgress
            val letterOffsetY = ((1f - charProgress) * 16f).dp
            val letterScale = 0.75f + (0.25f * charProgress)

            Text(
                text = if (char == ' ') "\u00A0" else char.toString(),
                color = if (index >= 7) Color(0xFFFFCC00) else Color.White,
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier
                    .offset(y = letterOffsetY)
                    .alpha(letterAlpha)
                    .scale(letterScale)
            )
        }
    }
}
