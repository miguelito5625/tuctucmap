package com.example.tuctucmap.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DriverLocation(
    @SerialName("driver_id")
    val uid: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    @SerialName("active")
    val activo: Boolean = false,
    @SerialName("updated_at")
    val timestamp: String = "",
    val nombre: String = "",
    val telefono: String = "",
    val placas: String = ""
) {
    val latitud: Double get() = lat
    val longitud: Double get() = lon
}
