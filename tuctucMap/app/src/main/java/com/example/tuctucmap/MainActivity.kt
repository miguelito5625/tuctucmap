package com.example.tuctucmap

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import com.example.tuctucmap.data.model.Driver
import com.example.tuctucmap.data.repository.SupabaseRepository
import com.example.tuctucmap.data.supabase
import com.example.tuctucmap.ui.AppNavigation
import com.example.tuctucmap.ui.Screen
import com.example.tuctucmap.ui.screens.AnimatedSplashScreen
import com.example.tuctucmap.ui.theme.TuctucMapTheme
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration

import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : ComponentActivity() {

    private val repository = SupabaseRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        val osmConfig = Configuration.getInstance()
        val osmdroidPrefs = getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE)
        val defaultPrefs = getSharedPreferences("${packageName}_preferences", Context.MODE_PRIVATE)
        val currentUserAgent = "TucTucMap/1.0 (Android; app.tuctucmap.gt)"

        osmdroidPrefs.edit().putString("osmdroid.userAgentValue", currentUserAgent).apply()
        defaultPrefs.edit().putString("osmdroid.userAgentValue", currentUserAgent).apply()

        osmConfig.load(applicationContext, osmdroidPrefs)
        osmConfig.userAgentValue = currentUserAgent
        osmConfig.save(applicationContext, osmdroidPrefs)

        // Limpiar caché de tiles si venía con versión anterior o bloqueada
        val lastUserAgent = osmdroidPrefs.getString("applied_user_agent_v2", null)
        if (lastUserAgent != currentUserAgent) {
            try {
                osmConfig.osmdroidTileCache?.deleteRecursively()
                val osmdroidDir = java.io.File(filesDir, "osmdroid")
                if (osmdroidDir.exists()) {
                    osmdroidDir.deleteRecursively()
                }
            } catch (_: Exception) {}
            osmdroidPrefs.edit().putString("applied_user_agent_v2", currentUserAgent).apply()
        }

        enableEdgeToEdge()

        setContent {
            TuctucMapTheme {
                var initialScreen by remember { mutableStateOf<Screen?>(null) }
                var isSplashFinished by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    val sharedPrefs = getSharedPreferences("tuctuc_prefs", Context.MODE_PRIVATE)
                    val savedRole = sharedPrefs.getString("user_role", null)

                    if (savedRole.isNullOrBlank()) {
                        // Usuario sin sesión previa iniciada: acceso público directo al mapa como invitado
                        initialScreen = Screen.PassengerMap
                        return@LaunchedEffect
                    }

                    // Esperar a que Supabase resuelva la sesión persistida desde almacenamiento seguro
                    val status = try {
                        withTimeoutOrNull(3000L) {
                            supabase.auth.sessionStatus
                                .filter { it !is SessionStatus.Initializing }
                                .first()
                        }
                    } catch (_: Exception) { null }

                    val isAuthed = status is SessionStatus.Authenticated ||
                            status is SessionStatus.RefreshFailure ||
                            repository.isUserLoggedIn

                    if (isAuthed) {
                        when (savedRole) {
                            "passenger" -> {
                                initialScreen = Screen.PassengerMap
                            }
                            "driver" -> {
                                val uid = repository.currentUserUid
                                    ?: (status as? SessionStatus.Authenticated)?.session?.user?.id
                                    ?: ""

                                val cachedApproval = sharedPrefs.getString("driver_last_approval_status", null)
                                val cachedSubEnd = sharedPrefs.getString("driver_last_sub_end", null)

                                val isSubExpiredCached = cachedSubEnd?.let {
                                    try {
                                        val expiryInstant = java.time.Instant.parse(it)
                                        java.time.Instant.now().isAfter(expiryInstant)
                                    } catch (_: Exception) { false }
                                } ?: false

                                // Intentar refrescar perfil del conductor en tiempo real (timeout rápido)
                                val driver = if (uid.isNotBlank()) {
                                    try {
                                        withTimeoutOrNull(2000L) {
                                            kotlinx.coroutines.suspendCancellableCoroutine<Driver?> { continuation ->
                                                repository.getDriverProfileOnce(uid) { d ->
                                                    if (continuation.isActive) continuation.resumeWith(Result.success(d))
                                                }
                                            }
                                        }
                                    } catch (_: Exception) { null }
                                } else null

                                if (driver != null) {
                                    sharedPrefs.edit()
                                        .putString("driver_last_approval_status", driver.estadoAprobacion)
                                        .putString("driver_last_sub_end", driver.subscriptionEndDate)
                                        .apply()
                                    initialScreen = com.example.tuctucmap.ui.resolveDriverScreen(driver)
                                } else {
                                    // Si no hubo respuesta inmediata de red pero el conductor está autenticado:
                                    // Entrar según caché o DriverHome (DriverHomeScreen vigila el estado en tiempo real)
                                    initialScreen = when {
                                        isSubExpiredCached -> Screen.DriverSubscriptionExpired
                                        cachedApproval == "pendiente" || cachedApproval == "rechazado" -> Screen.DriverPending
                                        else -> Screen.DriverHome
                                    }
                                }
                            }
                            else -> {
                                initialScreen = Screen.PassengerMap
                            }
                        }
                    } else {
                        // Sesión explícitamente no autenticada en Supabase (expirada definitivamente o revocada)
                        when (savedRole) {
                            "driver" -> initialScreen = Screen.DriverLogin
                            else -> initialScreen = Screen.PassengerMap
                        }
                    }
                }

                Crossfade(
                    targetState = isSplashFinished && initialScreen != null,
                    animationSpec = tween(durationMillis = 350),
                    label = "SplashCrossfade"
                ) { isReady ->
                    if (isReady && initialScreen != null) {
                        AppNavigation(
                            repository = repository,
                            initialScreen = initialScreen!!
                        )
                    } else {
                        AnimatedSplashScreen(
                            onAnimationFinished = {
                                isSplashFinished = true
                            }
                        )
                    }
                }
            }
        }
    }
}