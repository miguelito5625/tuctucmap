package com.example.tuctucmap.ui.util

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import com.example.tuctucmap.R
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.IMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import kotlin.math.cos
import kotlin.math.sin

/**
 * Tipos de avatar disponibles para el indicador de ubicación del pasajero en el mapa.
 */
enum class PassengerAvatarType {
    /**
     * Avatar Quetzal (Activo por defecto): Ave nacional de Guatemala en versión animada,
     * moviendo alegremente su alita en saludo continuo 👋 al usuario con ondas de saludo
     * cian y pulso de radar GPS en sus patitas.
     */
    QUETZAL,

    /**
     * Avatar Clásico (Guardado para fácil reversión): Persona animada con chaqueta azul
     * eléctrico (#0066FF) y gorra amarilla TucTuc (#F9D423) saludando con el brazo alzado.
     */
    CLASSIC_HUMAN
}

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * CONFIGURACIÓN RÁPIDA DE AVATAR:
 * Para revertir o cambiar fácilmente al avatar anterior, simplemente cambia este
 * valor a [PassengerAvatarType.CLASSIC_HUMAN].
 * ─────────────────────────────────────────────────────────────────────────────
 */
var ACTIVE_PASSENGER_AVATAR: PassengerAvatarType = PassengerAvatarType.QUETZAL

/**
 * Overlay de ubicación del pasajero con icono animado saludando continuamente.
 * - Soporta tanto el nuevo Quetzal animado como el avatar humano clásico.
 * - Movimiento continuo en tiempo real (~22 FPS) sin bloquear el hilo principal.
 * - Pulso de radar cian bajo las patitas / pies indicando la señal GPS en vivo.
 */
class PassengerLocationOverlay(
    provider: IMyLocationProvider,
    private val mapView: MapView,
    context: Context,
    val avatarType: PassengerAvatarType = ACTIVE_PASSENGER_AVATAR
) : MyLocationNewOverlay(provider, mapView) {

    private val density = context.resources.displayMetrics.density

    // Carga de fotogramas según el avatar configurado
    private val frames: List<Bitmap> = when (avatarType) {
        PassengerAvatarType.QUETZAL -> QuetzalCharacterRenderer.generateWavingFrames(context, frameCount = 8, sizeDp = 72)
        PassengerAvatarType.CLASSIC_HUMAN -> PassengerCharacterRenderer.generateWavingFrames(context, frameCount = 8, sizeDp = 66)
    }

    private val screenPoint = Point()
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    // Pintura para el pulso de radar bajo los pies
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * density
    }

    override fun drawMyLocation(canvas: Canvas, pj: org.osmdroid.views.Projection, lastFix: android.location.Location) {
        if (!isMyLocationEnabled || frames.isEmpty()) return

        // 1. Convertir la coordenada GPS a píxeles de pantalla
        val geoPoint = GeoPoint(lastFix.latitude, lastFix.longitude)
        pj.toPixels(geoPoint, screenPoint)
        val px = screenPoint.x.toFloat()
        val py = screenPoint.y.toFloat()

        // 2. Onda expansiva de pulso bajo los pies (efecto señal GPS)
        val now = SystemClock.uptimeMillis()
        val rippleProgress = (now % 1500L) / 1500f
        val rippleRadius = (10f + rippleProgress * 26f) * density
        val rippleAlpha = ((1f - rippleProgress) * 130).toInt().coerceIn(0, 255)
        ripplePaint.color = Color.argb(rippleAlpha, 0, 229, 255) // Cian brillante
        canvas.drawOval(
            px - rippleRadius,
            py - (rippleRadius * 0.38f),
            px + rippleRadius,
            py + (rippleRadius * 0.38f),
            ripplePaint
        )

        // 3. Seleccionar frame según el tiempo actual (8 frames a ~100ms = ciclo de 800ms)
        val frameIndex = ((now / 100L) % frames.size).toInt()
        val frameBitmap = frames[frameIndex]

        if (!frameBitmap.isRecycled) {
            // Anclar el personaje de modo que sus pies/patitas coincidan con la coordenada GPS (py)
            val drawLeft = px - (frameBitmap.width / 2f)
            val drawTop = py - (frameBitmap.height * 0.88f)

            canvas.drawBitmap(frameBitmap, drawLeft, drawTop, framePaint)
        }

        // 4. Optimización de batería y GPU para gama media y baja:
        // Solo programar el siguiente fotograma si el avatar está dentro de la pantalla visible
        val isVisibleOnScreen = (px >= -150f && px <= (mapView.width + 150f) && py >= -150f && py <= (mapView.height + 150f))
        if (isVisibleOnScreen) {
            mapView.postInvalidateDelayed(125L)
        } else {
            // Fuera de vista, comprobar con muy baja frecuencia (1200ms) para cero consumo de CPU
            mapView.postInvalidateDelayed(1200L)
        }
    }

    override fun onDetach(mapView: MapView) {
        super.onDetach(mapView)
        // Nota: Los fotogramas se mantienen en la caché en memoria del objeto Renderer
        // para evitar bloqueos del RenderThread o excepciones por uso de bitmaps reciclados.
    }
}

/**
 * Generador de fotogramas para el nuevo Quetzal animado saludando continuamente.
 * Incluye caché estática en memoria para cero impacto en la UI durante recomposiciones.
 */
object QuetzalCharacterRenderer {

    @Volatile
    private var cachedFrames: List<Bitmap>? = null

    fun generateWavingFrames(context: Context, frameCount: Int = 8, sizeDp: Int = 72): List<Bitmap> {
        cachedFrames?.let { return it }

        synchronized(this) {
            cachedFrames?.let { return it }

            val density = context.resources.displayMetrics.density
            val sizePx = (sizeDp * density).toInt()

            val baseBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.ic_quetzal_passenger)
                ?: return PassengerCharacterRenderer.generateWavingFrames(context, frameCount, sizeDp)

            val frames = (0 until frameCount).map { i ->
                val phase = i.toFloat() / frameCount.toFloat()
                createQuetzalFrame(baseBitmap, phase, sizePx, density)
            }

            if (!baseBitmap.isRecycled) {
                baseBitmap.recycle()
            }

            cachedFrames = frames
            return frames
        }
    }

    private fun createQuetzalFrame(baseBitmap: Bitmap, phase: Float, sizePx: Int, density: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val sinVal = sin(phase * 2 * Math.PI.toFloat())

        // Rebote y respiración alegre rítmica
        val bounceY = sinVal * 1.8f * density
        // Inclinación rítmica del personaje al saludar (-4° a +4°) alrededor de las patitas
        val tiltDeg = sinVal * 4.0f

        // Punto de anclaje de las patitas en el fotograma (coincide con el radar de GPS a 88% de altura)
        val anchorX = sizePx * 0.50f
        val anchorY = sizePx * 0.88f

        // En la ilustración 1024x1024, el centro de las patitas está en x=563, y=950
        val charW = sizePx * 0.78f
        val charH = charW // Formato cuadrado 1024x1024
        val charLeft = anchorX - (charW * (563f / 1024f))
        val charTop = anchorY - (charH * (950f / 1024f))

        // 1. Dibujar el Quetzal saludando con balanceo rítmico alrededor de sus patitas
        canvas.save()
        canvas.translate(anchorX, anchorY)
        canvas.rotate(tiltDeg)
        canvas.translate(-anchorX, -anchorY + bounceY)

        val destRect = RectF(charLeft, charTop, charLeft + charW, charTop + charH)
        val quetzalPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(baseBitmap, null, destRect, quetzalPaint)

        // 2. Ondas de saludo 👋 en cian neón (#00E5FF) al lado de la punta del ala que saluda
        // La punta del ala en la ilustración se ubica en x=930, y=380
        val wingTipX = charLeft + (charW * (930f / 1024f))
        val wingTipY = charTop + (charH * (380f / 1024f))

        val waveArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.4f * density
            color = 0xFF00E5FF.toInt() // Cian neón brillante
            strokeCap = Paint.Cap.ROUND
        }

        val arc1Rect = RectF(
            wingTipX + (3f * density), wingTipY - (14f * density),
            wingTipX + (17f * density), wingTipY + (0f * density)
        )
        canvas.drawArc(arc1Rect, 295f, 100f, false, waveArcPaint)

        val arc2Rect = RectF(
            wingTipX + (9f * density), wingTipY - (20f * density),
            wingTipX + (27f * density), wingTipY - (2f * density)
        )
        canvas.drawArc(arc2Rect, 300f, 90f, false, waveArcPaint)

        canvas.restore() // Fin de transformación corporal

        return bitmap
    }
}

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * IMPLEMENTACIÓN ANTERIOR (GUARDADA Y OCULTA PARA FÁCIL REVERSIÓN):
 * Generador procedural de fotogramas para la figura humana saludando
 * (Chaqueta azul #0066FF, gorra amarilla #F9D423 y contorno blanco grueso).
 * ─────────────────────────────────────────────────────────────────────────────
 */
object PassengerCharacterRenderer {

    @Volatile
    private var cachedFrames: List<Bitmap>? = null

    fun generateWavingFrames(context: Context, frameCount: Int = 8, sizeDp: Int = 66): List<Bitmap> {
        cachedFrames?.let { return it }

        synchronized(this) {
            cachedFrames?.let { return it }

            val density = context.resources.displayMetrics.density
            val sizePx = (sizeDp * density).toInt()

            val frames = (0 until frameCount).map { i ->
                val phase = i.toFloat() / frameCount.toFloat()
                createPersonFrame(phase, sizePx, density)
            }

            cachedFrames = frames
            return frames
        }
    }

    private fun createPersonFrame(phase: Float, sizePx: Int, density: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val cx = sizePx / 2f
        val groundY = sizePx * 0.88f

        // Ángulo del antebrazo saludando: oscila suavemente de -32° a +24°
        val waveAngleDeg = -6f + (sin(phase * 2 * Math.PI.toFloat()) * 28f)
        val waveAngleRad = Math.toRadians(waveAngleDeg.toDouble()).toFloat()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // ── 1. Sombra en el suelo bajo los pies ───────────────────────────────
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000
        canvas.drawOval(
            cx - (15f * density), groundY - (4f * density),
            cx + (15f * density), groundY + (5f * density),
            paint
        )

        // Parámetros corporales escalados por densidad
        val headCx = cx - (3f * density)
        val headCy = groundY - (44f * density)
        val headRadius = 9f * density

        val torsoTop = headCy + (10f * density)
        val torsoBottom = groundY - (14f * density)
        val torsoLeft = cx - (12f * density)
        val torsoRight = cx + (6f * density)

        val legWidth = 6f * density
        val lLegLeft = cx - (10f * density)
        val rLegLeft = cx - (2f * density)
        val legTop = torsoBottom - (2f * density)
        val legBottom = groundY - (1f * density)

        val lShoulderX = torsoLeft + (1f * density)
        val lShoulderY = torsoTop + (4f * density)
        val lHandX = torsoLeft - (5f * density)
        val lHandY = torsoTop + (22f * density)

        val rShoulderX = torsoRight - (1f * density)
        val rShoulderY = torsoTop + (4f * density)
        val elbowX = rShoulderX + (10f * density)
        val elbowY = rShoulderY - (10f * density)

        val foreLen = 14f * density
        val handX = elbowX + foreLen * sin(waveAngleRad)
        val handY = elbowY - foreLen * cos(waveAngleRad)

        // ── 2. CAPA BLANCA EXTERIOR (Contorno estilo sticker para máximo contraste) ──
        paint.color = Color.WHITE
        val contourPadding = 3.5f * density

        // Piernas y zapatos blanco
        canvas.drawRoundRect(
            RectF(lLegLeft - contourPadding, legTop, lLegLeft + legWidth + contourPadding, legBottom + contourPadding),
            4f * density, 4f * density, paint
        )
        canvas.drawRoundRect(
            RectF(rLegLeft - contourPadding, legTop, rLegLeft + legWidth + contourPadding, legBottom + contourPadding),
            4f * density, 4f * density, paint
        )
        // Torso blanco
        canvas.drawRoundRect(
            RectF(torsoLeft - contourPadding, torsoTop - contourPadding, torsoRight + contourPadding, torsoBottom + contourPadding),
            8f * density, 8f * density, paint
        )
        // Brazo izquierdo blanco
        paint.strokeWidth = 10f * density
        paint.style = Paint.Style.STROKE
        canvas.drawLine(lShoulderX, lShoulderY, lHandX, lHandY, paint)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(lHandX, lHandY, 5f * density, paint)

        // Brazo derecho saludando blanco
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 11f * density
        canvas.drawLine(rShoulderX, rShoulderY, elbowX, elbowY, paint)
        canvas.drawLine(elbowX, elbowY, handX, handY, paint)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(handX, handY, 7f * density, paint)

        // Cabeza blanca
        canvas.drawCircle(headCx, headCy, headRadius + contourPadding, paint)

        // ── 3. DETALLES Y COLORES DEL PERSONAJE ────────────────────────────────
        // Pantalones (Azul Marino / Slate #142337)
        paint.style = Paint.Style.FILL
        paint.color = 0xFF142337.toInt()
        canvas.drawRoundRect(RectF(lLegLeft, legTop, lLegLeft + legWidth, legBottom), 3f * density, 3f * density, paint)
        canvas.drawRoundRect(RectF(rLegLeft, legTop, rLegLeft + legWidth, legBottom), 3f * density, 3f * density, paint)

        // Tenis / Zapatos blancos
        paint.color = Color.WHITE
        canvas.drawOval(
            RectF(lLegLeft - (1.5f * density), legBottom - (2.5f * density), lLegLeft + legWidth + (1.5f * density), legBottom + (1.5f * density)),
            paint
        )
        canvas.drawOval(
            RectF(rLegLeft - (1.5f * density), legBottom - (2.5f * density), rLegLeft + legWidth + (1.5f * density), legBottom + (1.5f * density)),
            paint
        )

        // Chaqueta Azul Eléctrico Vibrante (#0066FF)
        paint.color = 0xFF0066FF.toInt()
        canvas.drawRoundRect(RectF(torsoLeft, torsoTop, torsoRight, torsoBottom), 6f * density, 6f * density, paint)

        // Detalle de franja amarilla TucTuc (#F9D423) en el pecho
        paint.color = 0xFFF9D423.toInt()
        canvas.drawRoundRect(
            RectF(cx - (5f * density), torsoTop + (2f * density), cx - (1f * density), torsoTop + (11f * density)),
            2f * density, 2f * density, paint
        )

        // Brazo izquierdo (manga azul + mano color piel)
        paint.color = 0xFF0066FF.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f * density
        canvas.drawLine(lShoulderX, lShoulderY, lHandX, lHandY - (2f * density), paint)
        paint.style = Paint.Style.FILL
        paint.color = 0xFFFFD1A4.toInt() // Tono piel
        canvas.drawCircle(lHandX, lHandY, 3.2f * density, paint)

        // Brazo derecho saludando (mangas azul eléctrico)
        paint.color = 0xFF0066FF.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5.5f * density
        canvas.drawLine(rShoulderX, rShoulderY, elbowX, elbowY, paint)
        canvas.drawLine(elbowX, elbowY, handX, handY, paint)

        // Mano saludando (Tono piel con palma y dedos alzados)
        paint.style = Paint.Style.FILL
        paint.color = 0xFFFFD1A4.toInt()
        canvas.drawCircle(handX, handY, 4.2f * density, paint)

        // Cabeza (Tono piel)
        canvas.drawCircle(headCx, headCy, headRadius, paint)

        // Gorra deportiva TucTuc en Amarillo brillante (#F9D423)
        paint.color = 0xFFF9D423.toInt()
        val capRect = RectF(headCx - headRadius, headCy - headRadius, headCx + headRadius, headCy + (1f * density))
        canvas.drawArc(capRect, 180f, 180f, true, paint)
        // Visera de la gorra
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.2f * density
        canvas.drawLine(headCx - headRadius - (1.5f * density), headCy + (1f * density), headCx + headRadius + (3.5f * density), headCy + (1f * density), paint)

        // Ojos y sonrisa amistosa
        paint.style = Paint.Style.FILL
        paint.color = 0xFF2A2A2A.toInt()
        canvas.drawCircle(headCx - (3f * density), headCy, 1f * density, paint)
        canvas.drawCircle(headCx + (2f * density), headCy, 1f * density, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f * density
        val smileRect = RectF(headCx - (3.5f * density), headCy + (1.5f * density), headCx + (2.5f * density), headCy + (4.5f * density))
        canvas.drawArc(smileRect, 20f, 140f, false, paint)

        // ── 4. Ondas de saludo 👋 en cian brillante al lado de la mano ─────────
        paint.color = 0xFF00E5FF.toInt() // Cian neón
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.8f * density
        for (offset in listOf(6f * density, 10f * density)) {
            val arcCx = handX + offset * sin(waveAngleRad + 0.35f)
            val arcCy = handY - offset * cos(waveAngleRad + 0.35f)
            val arcRect = RectF(arcCx - (4f * density), arcCy - (4f * density), arcCx + (4f * density), arcCy + (4f * density))
            canvas.drawArc(arcRect, 210f, 120f, false, paint)
        }

        return bitmap
    }
}
