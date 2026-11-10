package com.example.tuctucmap.ui.components.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Definición de los 4 ángulos cardinales principales para la captura y visualización 360° del TucTuc.
 */
data class VehicleAngleStep(
    val stepIndex: Int,
    val angleDegrees: Int,
    val title: String,
    val instruction: String,
    val guideTag: String
)

val VEHICLE_360_STEPS = listOf(
    VehicleAngleStep(0, 0, "Frente", "Colócate frente al TucTuc encuadrando el frente completo", "0°"),
    VehicleAngleStep(1, 90, "Costado Derecho", "Ubícate frente al lateral derecho completo", "90°"),
    VehicleAngleStep(2, 180, "Parte Trasera", "Encuadra la parte posterior del vehículo", "180°"),
    VehicleAngleStep(3, 270, "Costado Izquierdo", "Ubícate frente al lateral izquierdo completo", "270°")
)

object CameraUtils {

    /**
     * Recorta el bitmap al centro en una relación de aspecto exacta 1:1 (cuadrada).
     */
    fun cropToSquare(bitmap: Bitmap): Bitmap {
        val size = minOf(bitmap.width, bitmap.height)
        val x = (bitmap.width - size) / 2
        val y = (bitmap.height - size) / 2
        val square = Bitmap.createBitmap(bitmap, x, y, size, size)
        if (square != bitmap) {
            bitmap.recycle()
        }
        return square
    }

    /**
     * Corrige la orientación del bitmap basada en los metadatos EXIF del sensor de la cámara.
     */
    fun fixExifOrientation(bytes: ByteArray): Bitmap {
        val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalArgumentException("No se pudo decodificar el bitmap")

        return try {
            val exif = ExifInterface(ByteArrayInputStream(bytes))
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

            val rotationDegrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }

            if (rotationDegrees != 0f) {
                val matrix = Matrix().apply { postRotate(rotationDegrees) }
                val rotated = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
                if (rotated != original) {
                    original.recycle()
                }
                rotated
            } else {
                original
            }
        } catch (_: Exception) {
            original
        }
    }

    /**
     * Escala y comprime la imagen a formato JPEG optimizado (max 960px, calidad 80%)
     * para asegurar subidas ultra rápidas (< 100 KB por imagen) en redes móviles.
     */
    fun compressBitmap(bitmap: Bitmap, maxDimension: Int = 960, quality: Int = 80): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val scaled = if (width > maxDimension || height > maxDimension) {
            val ratio = width.toFloat() / height.toFloat()
            val newWidth: Int
            val newHeight: Int
            if (width > height) {
                newWidth = maxDimension
                newHeight = (maxDimension / ratio).toInt().coerceAtLeast(1)
            } else {
                newHeight = maxDimension
                newWidth = (maxDimension * ratio).toInt().coerceAtLeast(1)
            }
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } else {
            bitmap
        }

        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        if (scaled != bitmap) {
            scaled.recycle()
        }
        return out.toByteArray()
    }
}
