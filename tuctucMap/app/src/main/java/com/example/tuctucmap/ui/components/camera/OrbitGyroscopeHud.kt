package com.example.tuctucmap.ui.components.camera

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun OrbitGyroscopeHud(
    relativeAngle: Float,
    targetAngle: Float,
    isAligned: Boolean,
    alignmentProgress: Float, // 0.0f a 1.0f mientras se mantiene firme
    isSensorAvailable: Boolean,
    modifier: Modifier = Modifier
) {
    val delta = GyroscopeGuideManager.getShortestAngleDelta(relativeAngle, targetAngle)
    val absDelta = abs(delta)

    val hudColor by animateColorAsState(
        targetValue = when {
            isAligned -> Color(0xFF10B981) // Verde esmeralda (alineado)
            absDelta < 25f -> Color(0xFFF59E0B) // Ámbar (cerca)
            else -> Color(0xFF00E5FF) // Celeste tuctuc (navegando)
        },
        label = "hudColor"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Brújula / Radar orbital circular
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape)
                .background(Color(0x990F172A))
                .border(2.dp, hudColor.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = (size.minDimension / 2f) - 8f

                // Dibujar círculo orbital de fondo
                drawCircle(
                    color = Color.White.copy(alpha = 0.15f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )

                // Dibujar los 8 puntos cardinales/ordinales
                for (step in VEHICLE_360_STEPS) {
                    val angleRad = Math.toRadians((step.angleDegrees - 90).toDouble())
                    val x = center.x + (radius * cos(angleRad)).toFloat()
                    val y = center.y + (radius * sin(angleRad)).toFloat()

                    val isStepTarget = step.angleDegrees == targetAngle.toInt()
                    drawCircle(
                        color = if (isStepTarget) Color(0xFFF9D423) else Color.White.copy(alpha = 0.35f),
                        radius = if (isStepTarget) 6.dp.toPx() else 3.dp.toPx(),
                        center = Offset(x, y)
                    )
                }

                // Arco de progreso de disparo automático cuando está alineado
                if (isAligned && alignmentProgress > 0f) {
                    drawArc(
                        color = Color(0xFF10B981),
                        startAngle = -90f,
                        sweepAngle = alignmentProgress * 360f,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Aguja indicadora de orientación actual del teléfono
                val headingRad = Math.toRadians((relativeAngle - 90).toDouble())
                val needleX = center.x + (radius * 0.85f * cos(headingRad)).toFloat()
                val needleY = center.y + (radius * 0.85f * sin(headingRad)).toFloat()

                drawLine(
                    color = hudColor,
                    start = center,
                    end = Offset(needleX, needleY),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )

                drawCircle(
                    color = hudColor,
                    radius = 5.dp.toPx(),
                    center = Offset(needleX, needleY)
                )
            }

            // Icono central del TucTuc con grado objetivo
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "🛺",
                    fontSize = 20.sp
                )
                Text(
                    text = "${targetAngle.toInt()}°",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = hudColor
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Cartel dinámico con instrucciones de giro y disparo automático
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xDD0F172A),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, hudColor)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isAligned) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "¡Alineado! Mantén firme...",
                        color = Color(0xFF10B981),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else if (isSensorAvailable) {
                    val turnDirection = if (delta < 0) "a la derecha ➔" else "⬅ a la izquierda"
                    Text(
                        text = "Camina $turnDirection (${absDelta.toInt()}° restante)",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Text(
                        text = "Modo manual (Presiona el disparador)",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

