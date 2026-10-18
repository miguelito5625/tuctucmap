package com.example.tuctucmap.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.example.tuctucmap.R

/**
 * Gestor optimizado de iconos de marcadores para OSMDroid.
 * Procesa el mapa de bits con fondo transparente una sola vez y lo almacena
 * en memoria, evitando cálculos pesados de píxeles y recolección de basura
 * repetitiva en el hilo principal durante recomposiciones de Compose.
 */
object MarkerIconUtils {
    @Volatile
    private var cachedTucTucIcon: Drawable? = null

    fun getTucTucIcon(context: Context): Drawable {
        cachedTucTucIcon?.let { return it }

        synchronized(this) {
            cachedTucTucIcon?.let { return it }

            val drawable = ContextCompat.getDrawable(context, R.drawable.ic_tuc_tuc)
            val processed = if (drawable is BitmapDrawable) {
                val originalBitmap = drawable.bitmap
                val size = 144
                val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, size, size, true)
                val width = scaledBitmap.width
                val height = scaledBitmap.height
                val pixels = IntArray(width * height)
                scaledBitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                for (i in pixels.indices) {
                    val pixel = pixels[i]
                    val r = (pixel shr 16) and 0xff
                    val g = (pixel shr 8) and 0xff
                    val b = pixel and 0xff
                    if (r > 230 && g > 230 && b > 230) {
                        pixels[i] = 0x00000000
                    }
                }
                val transparentBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                transparentBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
                BitmapDrawable(context.resources, transparentBitmap)
            } else {
                drawable ?: BitmapDrawable(context.resources, Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
            }

            cachedTucTucIcon = processed
            return processed
        }
    }

    @Volatile
    private var cachedPausedTucTucIcon: Drawable? = null

    /**
     * Devuelve una versión tintada en ámbar y atenuada del icono del tuctuc
     * para representar visualmente en el mapa a conductores en pausa u ocupados.
     */
    fun getPausedTucTucIcon(context: Context): Drawable {
        cachedPausedTucTucIcon?.let { return it }

        synchronized(this) {
            cachedPausedTucTucIcon?.let { return it }

            val baseDrawable = getTucTucIcon(context)
            val processed = if (baseDrawable is BitmapDrawable) {
                val src = baseDrawable.bitmap
                val w = src.width
                val h = src.height
                val pixels = IntArray(w * h)
                src.getPixels(pixels, 0, w, 0, 0, w, h)

                for (i in pixels.indices) {
                    val pixel = pixels[i]
                    val a = (pixel shr 24) and 0xff
                    if (a > 0) {
                        val r = (pixel shr 16) and 0xff
                        val g = (pixel shr 8) and 0xff
                        val b = pixel and 0xff

                        // Desaturación y tinte cálido ámbar/naranja (#FFA726)
                        val gray = (r * 0.299 + g * 0.587 + b * 0.114).toInt()
                        val tintedR = (gray * 0.35 + 255 * 0.65).toInt().coerceIn(0, 255)
                        val tintedG = (gray * 0.35 + 167 * 0.65).toInt().coerceIn(0, 255)
                        val tintedB = (gray * 0.35 + 38 * 0.65).toInt().coerceIn(0, 255)

                        val finalA = (a * 0.85f).toInt().coerceIn(0, 255)
                        pixels[i] = (finalA shl 24) or (tintedR shl 16) or (tintedG shl 8) or tintedB
                    }
                }

                val pausedBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                pausedBitmap.setPixels(pixels, 0, w, 0, 0, w, h)
                BitmapDrawable(context.resources, pausedBitmap)
            } else {
                baseDrawable
            }

            cachedPausedTucTucIcon = processed
            return processed
        }
    }

    @Volatile
    private var cachedPassengerPickupIcon: Drawable? = null

    /**
     * Genera un pin de recogida distintivo para el pasajero con el icono del Quetzal
     * sobre un fondo circular blanco con contorno verde esmeralda y punta inferior.
     */
    fun getPassengerPickupIcon(context: Context): Drawable {
        cachedPassengerPickupIcon?.let { return it }

        synchronized(this) {
            cachedPassengerPickupIcon?.let { return it }

            val density = context.resources.displayMetrics.density
            val width = (52 * density).toInt()
            val height = (64 * density).toInt()
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)

            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            val strokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 3f * density
                color = android.graphics.Color.WHITE
            }

            val centerX = width / 2f
            val radius = (width / 2f) - (4 * density)
            val centerY = radius + (3 * density)

            // Pin: Triángulo inferior
            val tipPath = android.graphics.Path().apply {
                moveTo(centerX - (9 * density), centerY + (radius * 0.55f))
                lineTo(centerX, height.toFloat() - (2 * density))
                lineTo(centerX + (9 * density), centerY + (radius * 0.55f))
                close()
            }
            paint.color = android.graphics.Color.parseColor("#059669") // Verde esmeralda llamativo
            canvas.drawPath(tipPath, paint)

            // Círculo exterior (Verde esmeralda)
            canvas.drawCircle(centerX, centerY, radius, paint)
            // Borde exterior blanco
            canvas.drawCircle(centerX, centerY, radius, strokePaint)

            // Círculo interior blanco
            paint.color = android.graphics.Color.WHITE
            canvas.drawCircle(centerX, centerY, radius - (3.5f * density), paint)

            // Quetzal o pasajero dentro del círculo
            val quetzal = ContextCompat.getDrawable(context, R.drawable.ic_quetzal_passenger)
            if (quetzal != null) {
                val iconSize = (radius * 1.4f).toInt()
                val left = (centerX - iconSize / 2f).toInt()
                val top = (centerY - iconSize / 2f).toInt()
                quetzal.setBounds(left, top, left + iconSize, top + iconSize)
                quetzal.draw(canvas)
            }

            val result = BitmapDrawable(context.resources, bitmap)
            cachedPassengerPickupIcon = result
            return result
        }
    }

    @Volatile
    private var cachedAssignedTucTucIcon: Drawable? = null

    /**
     * Devuelve una versión destacada del TucTuc con halo verde esmeralda / cian brillante
     * para identificar inequívocamente al conductor que aceptó el viaje del pasajero.
     */
    fun getAssignedDriverTucTucIcon(context: Context): Drawable {
        cachedAssignedTucTucIcon?.let { return it }

        synchronized(this) {
            cachedAssignedTucTucIcon?.let { return it }

            val baseDrawable = getTucTucIcon(context)
            val processed = if (baseDrawable is BitmapDrawable) {
                val src = baseDrawable.bitmap
                val density = context.resources.displayMetrics.density
                val haloPadding = (8 * density).toInt()
                val w = src.width + haloPadding * 2
                val h = src.height + haloPadding * 2
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)

                // Halo circular cian neón translúcido
                val haloPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.parseColor("#4D00E5FF")
                    style = android.graphics.Paint.Style.FILL
                }
                canvas.drawCircle(w / 2f, h / 2f, (w / 2f) - (2 * density), haloPaint)

                // Anillo exterior brillante cian
                val ringPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.parseColor("#00E5FF")
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = 3f * density
                }
                canvas.drawCircle(w / 2f, h / 2f, (w / 2f) - (3 * density), ringPaint)

                // Dibujar el icono del TucTuc centrado
                canvas.drawBitmap(src, haloPadding.toFloat(), haloPadding.toFloat(), null)
                BitmapDrawable(context.resources, bitmap)
            } else {
                baseDrawable
            }

            cachedAssignedTucTucIcon = processed
            return processed
        }
    }
}


