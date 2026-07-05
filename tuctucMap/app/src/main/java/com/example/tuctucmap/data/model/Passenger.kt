package com.example.tuctucmap.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Passenger(
    val id: String = "",
    @SerialName("name")
    val nombre: String = "",
    @SerialName("phone")
    val telefono: String = "",
    val email: String = "",
    val role: String = "passenger"
)
