package com.example.tuctucmap.data

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

// TODO: Replace with your actual Supabase URL and Anon Key, 
// preferably loaded from BuildConfig or local.properties
const val SUPABASE_URL = "https://doypobovndxkbkikkozq.supabase.co"
const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImRveXBvYm92bmR4a2JraWtrb3pxIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODMwMjMwMjQsImV4cCI6MjA5ODU5OTAyNH0.RCqCfUBCD1E_HXiWi9vw6OxwVlf7chddvVLCCTRewZU"

val supabase = createSupabaseClient(
    supabaseUrl = SUPABASE_URL,
    supabaseKey = SUPABASE_KEY
) {
    install(Auth)
    install(Postgrest)
    install(Realtime)
}
