package com.example.tuctucmap

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.tuctucmap.data.repository.SupabaseRepository
import com.example.tuctucmap.data.supabase
import com.example.tuctucmap.ui.AppNavigation
import com.example.tuctucmap.ui.Screen
import com.example.tuctucmap.ui.theme.TuctucMapTheme
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration

class MainActivity : ComponentActivity() {

    private val repository = SupabaseRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(
            applicationContext,
            getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE)
        )
        Configuration.getInstance().userAgentValue = packageName

        enableEdgeToEdge()

        setContent {
            TuctucMapTheme {
                var initialScreen by remember { mutableStateOf<Screen?>(null) }

                LaunchedEffect(Unit) {
                    val sharedPrefs = getSharedPreferences("tuctuc_prefs", Context.MODE_PRIVATE)
                    val savedRole = sharedPrefs.getString("user_role", null)

                    // Restaurar tokens si existen
                    val accessToken  = sharedPrefs.getString("supabase_access", null)
                    val refreshToken = sharedPrefs.getString("supabase_refresh", null)

                    if (savedRole != null && accessToken != null && refreshToken != null) {
                        // Intentar restaurar la sesión
                        try {
                            supabase.auth.importAuthToken(accessToken, refreshToken)

                            // Esperar a que Supabase resuelva (Authenticated o NotAuthenticated)
                            val status = supabase.auth.sessionStatus
                                .filter {
                                    it is SessionStatus.Authenticated ||
                                    it is SessionStatus.NotAuthenticated
                                }
                                .first()

                            if (status is SessionStatus.Authenticated) {
                                // Guardar tokens renovados
                                sharedPrefs.edit()
                                    .putString("supabase_access", status.session.accessToken)
                                    .putString("supabase_refresh", status.session.refreshToken)
                                    .apply()

                                // Navegar según rol
                                when (savedRole) {
                                    "passenger" -> initialScreen = Screen.PassengerHome
                                    "driver" -> {
                                        val uid = repository.currentUserUid ?: ""
                                        repository.getDriverProfileOnce(uid) { driver ->
                                            initialScreen = when (driver?.estadoAprobacion) {
                                                "aprobado" -> Screen.DriverHome
                                                "suscripcion_vencida" -> Screen.DriverSubscriptionExpired
                                                else -> if (driver != null) Screen.DriverPending else Screen.DriverLogin
                                            }
                                        }
                                    }
                                    else -> initialScreen = Screen.PassengerLogin
                                }
                            } else {
                                // Sesión inválida/expirada
                                sharedPrefs.edit()
                                    .remove("supabase_access")
                                    .remove("supabase_refresh")
                                    .remove("user_role")
                                    .apply()
                                initialScreen = Screen.PassengerLogin
                            }
                        } catch (e: Exception) {
                            initialScreen = Screen.PassengerLogin
                        }
                    } else {
                        // Sin sesión guardada
                        initialScreen = Screen.PassengerLogin
                    }

                    // Guardar tokens de forma reactiva cuando la sesión cambie
                    CoroutineScope(Dispatchers.IO).launch {
                        supabase.auth.sessionStatus.collect { status ->
                            when (status) {
                                is SessionStatus.Authenticated -> {
                                    sharedPrefs.edit()
                                        .putString("supabase_access", status.session.accessToken)
                                        .putString("supabase_refresh", status.session.refreshToken)
                                        .apply()
                                }
                                else -> {}
                            }
                        }
                    }
                }

                if (initialScreen != null) {
                    AppNavigation(
                        repository = repository,
                        initialScreen = initialScreen!!
                    )
                } else {
                    // Pantalla de carga mientras se restaura la sesión
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF0F2027)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFFF9D423))
                    }
                }
            }
        }
    }
}