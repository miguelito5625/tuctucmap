package com.example.tuctucmap.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Driver(
    val id: String = "",
    @SerialName("name")
    val nombre: String = "",
    @SerialName("phone")
    val telefono: String = "",
    @SerialName("plate_number")
    val placas: String = "",
    @SerialName("approval_status")
    val estadoAprobacion: String = "pendiente",
    @SerialName("subscription_end_date")
    val fechaVencimientoSuscripcion: String = ""
) {
    // Alias de compatibilidad
    val uid: String get() = id
}
