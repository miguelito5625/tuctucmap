package com.example.tuctucmap.data.crash

import android.content.Context
import android.util.Log
import com.example.tuctucmap.data.supabase
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Gestor de ciclo de vida para inicializar el interceptor de excepciones
 * y enviar los reportes pendientes almacenados localmente a Supabase.
 */
object CrashReporterManager {
    private const val TAG = "CrashReporterManager"
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Registra el interceptor global de excepciones no capturadas.
     * Debe invocarse en [com.example.tuctucmap.TucTucApplication.onCreate].
     */
    fun init(context: Context) {
        val currentHandler = Thread.getDefaultUncaughtExceptionHandler()
        if (currentHandler !is TucTucCrashHandler) {
            Thread.setDefaultUncaughtExceptionHandler(TucTucCrashHandler(context, currentHandler))
            Log.d(TAG, "Manejador global de errores TucTucCrashHandler registrado con éxito.")
        }
    }

    /**
     * Revisa el almacenamiento interno para despachar reportes pendientes a Supabase.
     * Se ejecuta en un hilo de E/S ([Dispatchers.IO]) para no afectar la fluidez del arranque.
     */
    suspend fun checkAndSendPendingReports(context: Context) = withContext(Dispatchers.IO) {
        val crashDir = File(context.filesDir, TucTucCrashHandler.CRASH_DIR_NAME)
        if (!crashDir.exists() || !crashDir.isDirectory) return@withContext

        val crashFiles = crashDir.listFiles { file -> file.extension.equals("json", ignoreCase = true) }
        if (crashFiles.isNullOrEmpty()) return@withContext

        Log.i(TAG, "Detectado(s) ${crashFiles.size} reporte(s) de error de ejecuciones anteriores.")

        // Procesar en orden cronológico
        val sortedFiles = crashFiles.sortedBy { it.lastModified() }

        for (file in sortedFiles) {
            try {
                val jsonString = file.readText()
                val report = json.decodeFromString<CrashReportDto>(jsonString)

                // Enviar a Supabase tabla app_crash_logs
                supabase.postgrest["app_crash_logs"].insert(report)

                // Eliminación segura tras confirmación exitosa de Supabase
                val deleted = file.delete()
                Log.i(TAG, "Reporte de error ${file.name} enviado a Supabase con éxito. Archivo local eliminado: $deleted")
            } catch (e: Exception) {
                // En caso de corte de red u otro fallo temporal, se preserva el archivo para el próximo inicio
                Log.w(TAG, "Fallo al enviar reporte ${file.name} a Supabase (se preserva para próximo arranque): ${e.message}")
            }
        }
    }
}
