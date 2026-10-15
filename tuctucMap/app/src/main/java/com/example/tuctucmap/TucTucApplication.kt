package com.example.tuctucmap

import android.app.Application
import android.content.Context

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
    }
}
