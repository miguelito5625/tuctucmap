package com.example.tuctucmap.ui.components.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Definición de los 8 ángulos cardinales y ordinales para la captura y visualización 360° del TucTuc.
 */
data class VehicleAngleStep(
    val stepIndex: Int,
    val angleDegrees: Int,
    val title: String,
    val instruction: String,
    val guideTag: String
)

val VEHICLE_360_STEPS = listOf(
    VehicleAngleStep(0, 0, "Frente", "Colócate frente al TucTuc encuadrando el parabrisas y luces", "0°"),
    VehicleAngleStep(1, 45, "Frontal Derecha", "Muévete hacia la esquina frontal derecha (45°)", "45°"),
    VehicleAngleStep(2, 90, "Costado Derecho", "Ubícate frente al lateral derecho completo", "90°"),
    VehicleAngleStep(3, 135, "Trasera Derecha", "Pasa a la esquina posterior derecha (135°)", "135°"),
    VehicleAngleStep(4, 180, "Parte Trasera", "Encuadra la parte posterior del vehículo", "180°"),
    VehicleAngleStep(5, 225, "Trasera Izquierda", "Pasa a la esquina posterior izquierda (225°)", "225°"),
    VehicleAngleStep(6, 270, "Costado Izquierdo", "Ubícate frente al lateral izquierdo completo", "270°"),
    VehicleAngleStep(7, 315, "Frontal Izquierda", "Completa la vuelta en la esquina frontal izquierda (315°)", "315°")
)

object CameraUtils {

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
