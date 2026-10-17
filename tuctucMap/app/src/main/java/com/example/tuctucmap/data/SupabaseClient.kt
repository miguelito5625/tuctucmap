package com.example.tuctucmap.data

import com.example.tuctucmap.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

val SUPABASE_URL: String = BuildConfig.SUPABASE_URL
val SUPABASE_KEY: String = BuildConfig.SUPABASE_KEY

val supabase by lazy {
    createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_KEY
    ) {
        install(Auth) {
            sessionManager = SecurePrefs
            autoLoadFromStorage = true
            alwaysAutoRefresh = true
        }
        install(Postgrest)
        install(Realtime)
    }
}
