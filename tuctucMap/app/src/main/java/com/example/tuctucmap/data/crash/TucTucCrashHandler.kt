package com.example.tuctucmap.data.crash

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.tuctucmap.BuildConfig
import com.example.tuctucmap.data.SecurePrefs
import com.example.tuctucmap.data.supabase
import io.github.jan.supabase.auth.auth
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Interceptor global de excepciones no capturadas.
 * Se encarga de persistir el error en el almacenamiento interno de forma síncrona
 * antes de que el proceso sea terminado por el sistema operativo.
 */
class TucTucCrashHandler(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            saveCrashReportLocally(thread, throwable)
        } catch (e: Throwable) {
            // Prevenir excepciones secundarias que puedan ocultar el error original
            Log.e(TAG, "Error crítico al intentar guardar el crash report localmente", e)
        } finally {
            // Siempre delegar al manejador original de Android para un cierre limpio del proceso
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun saveCrashReportLocally(thread: Thread, throwable: Throwable) {
        val stackTraceString = Log.getStackTraceString(throwable)
        val errorType = throwable.javaClass.name
        val errorMessage = throwable.message ?: throwable.localizedMessage

        val userId = try {
            supabase.auth.currentUserOrNull()?.id
        } catch (_: Throwable) {
            null
        }

        val userEmail = try {
            supabase.auth.currentUserOrNull()?.email
        } catch (_: Throwable) {
            null
        }

        val role = try {
            SecurePrefs.getUserRole(context)
        } catch (_: Throwable) {
            null
        }

        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val createdAt = isoFormat.format(Date())

        val report = CrashReportDto(
            userId = userId,
            userEmail = userEmail,
            role = role,
            deviceBrand = Build.BRAND.orEmpty().ifBlank { "unknown" },
            deviceModel = Build.MODEL.orEmpty().ifBlank { "unknown" },
            deviceManufacturer = Build.MANUFACTURER.orEmpty().ifBlank { "unknown" },
            androidVersion = Build.VERSION.RELEASE.orEmpty().ifBlank { "unknown" },
            sdkInt = Build.VERSION.SDK_INT,
            appVersion = BuildConfig.VERSION_NAME,
            buildNumber = BuildConfig.VERSION_CODE,
            buildType = BuildConfig.BUILD_TYPE,
            errorType = errorType,
            errorMessage = errorMessage,
            stackTrace = stackTraceString,
            threadName = thread.name,
            createdAt = createdAt
        )

        val crashDir = File(context.filesDir, CRASH_DIR_NAME)
        if (!crashDir.exists()) {
            crashDir.mkdirs()
        }

        val crashFile = File(crashDir, "crash_${System.currentTimeMillis()}.json")
        crashFile.writeText(json.encodeToString(report))
        Log.i(TAG, "Reporte de fallo guardado con éxito en: ${crashFile.name}")
    }

    companion object {
        const val TAG = "TucTucCrashHandler"
        const val CRASH_DIR_NAME = "tuctuc_crashes"
    }
}

