package com.example.tuctucmap.data.crash

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CrashReportDto(
    @SerialName("user_id") val userId: String? = null,
    @SerialName("user_email") val userEmail: String? = null,
    val role: String? = null,
    @SerialName("device_brand") val deviceBrand: String,
    @SerialName("device_model") val deviceModel: String,
    @SerialName("device_manufacturer") val deviceManufacturer: String,
    @SerialName("android_version") val androidVersion: String,
    @SerialName("sdk_int") val sdkInt: Int,
    @SerialName("app_version") val appVersion: String,
    @SerialName("build_number") val buildNumber: Int,
    @SerialName("build_type") val buildType: String,
    @SerialName("error_type") val errorType: String,
    @SerialName("error_message") val errorMessage: String? = null,
    @SerialName("stack_trace") val stackTrace: String,
    @SerialName("thread_name") val threadName: String? = null,
    @SerialName("created_at") val createdAt: String
)
