package com.example.tuctucmap.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.example.tuctucmap.R
import kotlin.math.roundToInt

/**
 * Gestor optimizado de iconos de marcadores para OSMDroid.
 *
 * Optimizaciones aplicadas para dispositivos de gama baja:
 * 1. Usa recursos WebP preprocesados en `drawable-nodpi` (`inScaled = false`) evitando picos de
 *    37-67 MB de RAM al decodificar en pantallas xxhdpi/xxxhdpi y eliminando los bucles de píxeles en CPU.
 * 2. Escala el icono de TucTuc dinámicamente según la densidad de pantalla (`48.dp`) en lugar de
 *    144px fijos que saturaban pantallas mdpi/hdpi.
 * 3. Retorna nuevas envolturas `BitmapDrawable(resources, cachedBitmap)` por cada llamada,
 *    compartiendo el mismo `Bitmap` en memoria pero aislando `alpha` y `bounds` entre marcadores.
 */
object MarkerIconUtils {

    @Volatile
    private var cachedActiveBitmap: Bitmap? = null

    @Volatile
    private var cachedPausedBitmap: Bitmap? = null

    @Volatile
    private var cachedAssignedBitmap: Bitmap? = null

    @Volatile
    private var cachedPassengerPickupBitmap: Bitmap? = null

    private fun getOrCreateActiveBitmap(context: Context): Bitmap {
        cachedActiveBitmap?.let { if (!it.isRecycled) return it }
        synchronized(this) {
            cachedActiveBitmap?.let { if (!it.isRecycled) return it }
            val density = context.resources.displayMetrics.density
            val targetSizePx = (48f * density).roundToInt().coerceIn(64, 192)
            val options = BitmapFactory.Options().apply {
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val rawBitmap = BitmapFactory.decodeResource(
                context.resources,
                R.drawable.ic_tuc_tuc,
                options
            )
            val scaled = if (rawBitmap != null) {
                if (rawBitmap.width == targetSizePx && rawBitmap.height == targetSizePx) {
                    rawBitmap
                } else {
                    Bitmap.createScaledBitmap(rawBitmap, targetSizePx, targetSizePx, true).also {
                        if (it != rawBitmap) rawBitmap.recycle()
                    }
                }
            } else {
                Bitmap.createBitmap(targetSizePx, targetSizePx, Bitmap.Config.ARGB_8888)
            }
            cachedActiveBitmap = scaled
            return scaled
        }
    }

    /**
     * Retorna un Drawable del TucTuc activo listo para asignarse a un Marker de OSMDroid.
     * Cada llamada retorna un `BitmapDrawable` independiente respaldado por el mismo `Bitmap` en caché,
     * evitando que `marker.setAlpha(...)` contamine a otros marcadores del mapa.
     */
    fun getTucTucIcon(context: Context): Drawable {
        return BitmapDrawable(context.resources, getOrCreateActiveBitmap(context))
    }

    /**
     * Retorna una versión en escala de grises y semitransparente del icono del TucTuc
     * para representar conductores temporalmente en pausa ("Ocupado"), generada por Canvas/ColorMatrix
     * sin bucles de píxeles en CPU.
     */
    fun getPausedTucTucIcon(context: Context): Drawable {
        val pausedBmp = cachedPausedBitmap?.takeIf { !it.isRecycled } ?: synchronized(this) {
            cachedPausedBitmap?.takeIf { !it.isRecycled } ?: run {
                val activeBmp = getOrCreateActiveBitmap(context)
                val grayBmp = Bitmap.createBitmap(
                    activeBmp.width,
                    activeBmp.height,
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(grayBmp)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
                    alpha = 180 // ~70% opacidad
                }
                canvas.drawBitmap(activeBmp, 0f, 0f, paint)
                cachedPausedBitmap = grayBmp
                grayBmp
            }
        }
        return BitmapDrawable(context.resources, pausedBmp)
    }

    /**
     * Devuelve el marcador de punto de recogida del pasajero (Pin verde esmeralda con Quetzal interior).
     */
    fun getPassengerPickupIcon(context: Context): Drawable {
        val pickupBmp = cachedPassengerPickupBitmap?.takeIf { !it.isRecycled } ?: synchronized(this) {
            cachedPassengerPickupBitmap?.takeIf { !it.isRecycled } ?: run {
                val density = context.resources.displayMetrics.density
                val width = (48 * density).toInt().coerceAtLeast(48)
                val height = (58 * density).toInt().coerceAtLeast(58)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)

                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 2.5f * density
                    color = Color.WHITE
                }

                val centerX = width / 2f
                val centerY = (width / 2f)
                val radius = (width / 2f) - (3 * density)

                // Pin: Triángulo inferior
                val tipPath = Path().apply {
                    moveTo(centerX - (9 * density), centerY + (radius * 0.55f))
                    lineTo(centerX, height.toFloat() - (2 * density))
                    lineTo(centerX + (9 * density), centerY + (radius * 0.55f))
                    close()
                }
                paint.color = Color.parseColor("#059669") // Verde esmeralda llamativo
                canvas.drawPath(tipPath, paint)

                // Círculo exterior (Verde esmeralda)
                canvas.drawCircle(centerX, centerY, radius, paint)
                // Borde exterior blanco
                canvas.drawCircle(centerX, centerY, radius, strokePaint)

                // Círculo interior blanco
                paint.color = Color.WHITE
                canvas.drawCircle(centerX, centerY, radius - (3.5f * density), paint)

                // Quetzal dentro del círculo decodificado sin escalado redundante
                val options = BitmapFactory.Options().apply {
                    inScaled = false
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val quetzalBmp = BitmapFactory.decodeResource(
                    context.resources,
                    R.drawable.ic_quetzal_passenger,
                    options
                )
                if (quetzalBmp != null) {
                    val iconSize = (radius * 1.4f)
                    val left = centerX - iconSize / 2f
                    val top = centerY - iconSize / 2f
                    val destRect = RectF(left, top, left + iconSize, top + iconSize)
                    val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                    canvas.drawBitmap(quetzalBmp, null, destRect, bmpPaint)
                    quetzalBmp.recycle()
                } else {
                    val fallback = ContextCompat.getDrawable(context, R.drawable.ic_quetzal_passenger)
                    if (fallback != null) {
                        val iconSize = (radius * 1.4f).toInt()
                        val left = (centerX - iconSize / 2f).toInt()
                        val top = (centerY - iconSize / 2f).toInt()
                        fallback.setBounds(left, top, left + iconSize, top + iconSize)
                        fallback.draw(canvas)
                    }
                }

                cachedPassengerPickupBitmap = bitmap
                bitmap
            }
        }
        return BitmapDrawable(context.resources, pickupBmp)
    }

    /**
     * Devuelve una versión destacada del TucTuc con halo cian brillante
     * para identificar inequívocamente al conductor que aceptó el viaje del pasajero.
     */
    fun getAssignedDriverTucTucIcon(context: Context): Drawable {
        val assignedBmp = cachedAssignedBitmap?.takeIf { !it.isRecycled } ?: synchronized(this) {
            cachedAssignedBitmap?.takeIf { !it.isRecycled } ?: run {
                val src = getOrCreateActiveBitmap(context)
                val density = context.resources.displayMetrics.density
                val haloPadding = (8 * density).toInt()
                val w = src.width + haloPadding * 2
                val h = src.height + haloPadding * 2
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)

                // Halo circular cian neón translúcido
                val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#4D00E5FF")
                    style = Paint.Style.FILL
                }
                canvas.drawCircle(w / 2f, h / 2f, (w / 2f) - (2 * density), haloPaint)

                // Anillo exterior brillante cian
                val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#00E5FF")
                    style = Paint.Style.STROKE
                    strokeWidth = 3f * density
                }
                canvas.drawCircle(w / 2f, h / 2f, (w / 2f) - (3 * density), ringPaint)

                // Dibujar el icono del TucTuc centrado
                canvas.drawBitmap(src, haloPadding.toFloat(), haloPadding.toFloat(), null)
                cachedAssignedBitmap = bitmap
                bitmap
            }
        }
        return BitmapDrawable(context.resources, assignedBmp)
    }
}
