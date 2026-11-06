package com.example.tuctucmap

import android.app.Application
import android.content.Context
import com.example.tuctucmap.data.crash.CrashReporterManager
import com.example.tuctucmap.data.supabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TucTucApplication : Application() {

    companion object {
        private var _instance: TucTucApplication? = null
        val instance: TucTucApplication
            get() = _instance ?: error("TucTucApplication no ha sido inicializada")

        val appContext: Context
            get() = instance.applicationContext
    }

    override fun onCreate() {
        super.onCreate()
        _instance = this

        // Inicializar interceptor global de errores no capturados
        CrashReporterManager.init(this)

        // Precalentar Supabase y despachar reportes pendientes de errores en segundo plano
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                supabase
                CrashReporterManager.checkAndSendPendingReports(applicationContext)
            } catch (_: Exception) {}
        }
    }
}
