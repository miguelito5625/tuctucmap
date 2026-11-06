package com.example.tuctucmap

import android.app.Application
import android.content.Context
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

        // Precalentar Supabase y dependencias de red/reflexión en segundo plano (Dispatchers.IO)
        // para evitar congelar el hilo principal durante el arranque inicial.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                supabase
            } catch (_: Exception) {}
        }
    }
}
