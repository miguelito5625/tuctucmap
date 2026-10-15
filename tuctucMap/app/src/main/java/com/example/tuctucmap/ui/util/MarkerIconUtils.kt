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
     * Devuelve una versión tintada en ámbar y atenuada del icono del torito
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
}

