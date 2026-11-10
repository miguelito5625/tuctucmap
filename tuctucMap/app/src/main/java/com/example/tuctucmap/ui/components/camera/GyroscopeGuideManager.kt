package com.example.tuctucmap.ui.components.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs

/**
 * Gestor del giroscopio y sensor de rotación para la captura asistida automática en 360°.
 * Utiliza TYPE_GAME_ROTATION_VECTOR (inmune a interferencias magnéticas de carrocerías metálicas)
 * con fallback a TYPE_ROTATION_VECTOR.
 */
class GyroscopeGuideManager(
    context: Context,
    private val onOrientationChanged: (relativeAngle: Float, isAvailable: Boolean) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private var initialAzimuthDegrees: Float? = null
    var isCalibrated: Boolean = false
        private set

    val isSensorAvailable: Boolean get() = rotationSensor != null

    fun startListening() {
        initialAzimuthDegrees = null
        isCalibrated = false
        rotationSensor?.let { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        } ?: run {
            onOrientationChanged(0f, false)
        }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
    }

    fun calibrateInitialHeading() {
        initialAzimuthDegrees = null
        isCalibrated = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_GAME_ROTATION_VECTOR ||
            event.sensor.type == Sensor.TYPE_ROTATION_VECTOR
        ) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)

            // orientationAngles[0] es el azimut en radianes [-PI, PI]
            val rawAzimuthDeg = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
            val normalizedAzimuth = (rawAzimuthDeg + 360f) % 360f

            if (initialAzimuthDegrees == null) {
                initialAzimuthDegrees = normalizedAzimuth
                isCalibrated = true
            }

            val base = initialAzimuthDegrees ?: normalizedAzimuth
            // Ángulo relativo positivo en sentido horario (0° a 360°)
            val relative = (normalizedAzimuth - base + 360f) % 360f
            onOrientationChanged(relative, true)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        /**
         * Calcula la diferencia angular más corta entre dos ángulos en grados (-180° a +180°).
         */
        fun getShortestAngleDelta(current: Float, target: Float): Float {
            var diff = (current - target + 180f) % 360f - 180f
            if (diff < -180f) diff += 360f
            return diff
        }

        /**
         * Determina si el ángulo actual está dentro del margen de tolerancia del ángulo objetivo.
         */
        fun isWithinTolerance(current: Float, target: Float, toleranceDegrees: Float = 12f): Boolean {
            val delta = abs(getShortestAngleDelta(current, target))
            return delta <= toleranceDegrees
        }
    }
}

