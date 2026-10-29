package com.example.tuctucmap

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.StatFs
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.example.tuctucmap.data.SecurePrefs
import com.example.tuctucmap.data.model.Driver
import com.example.tuctucmap.data.model.ServerClock
import com.example.tuctucmap.data.model.parseIsoTimestamp
import com.example.tuctucmap.data.repository.SupabaseRepository
import com.example.tuctucmap.data.supabase
import com.example.tuctucmap.ui.AppNavigation
import com.example.tuctucmap.ui.Screen
import com.example.tuctucmap.ui.screens.AnimatedSplashScreen
import com.example.tuctucmap.ui.theme.TuctucMapTheme
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.osmdroid.config.Configuration

class MainActivity : ComponentActivity() {

    private val repository = SupabaseRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        installSplashScreen()
        super.onCreate(savedInstanceState)

        val osmConfig = Configuration.getInstance()
        val currentUserAgent = "TucTucMap/1.0 (Android; app.tuctucmap.gt)"
        osmConfig.userAgentValue = currentUserAgent

        // Ajustar límites de memoria de tiles según el hardware del dispositivo (Low-RAM)
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val isLowRam = activityManager?.isLowRamDevice == true
        if (isLowRam) {
            osmConfig.cacheMapTileCount = 12.toShort()
            osmConfig.tileDownloadThreads = 2.toShort()
            osmConfig.tileFileSystemThreads = 2.toShort()
        }

        // Ejecutar I/O de preferencias, cálculo de StatFs y limpieza de caché en segundo plano (Dispatchers.IO)
        // para no bloquear el hilo principal durante el Cold Start.
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val osmdroidPrefs = getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE)
                val defaultPrefs = getSharedPreferences("${packageName}_preferences", Context.MODE_PRIVATE)

                osmdroidPrefs.edit().putString("osmdroid.userAgentValue", currentUserAgent).apply()
                defaultPrefs.edit().putString("osmdroid.userAgentValue", currentUserAgent).apply()

                osmConfig.load(applicationContext, osmdroidPrefs)
                osmConfig.userAgentValue = currentUserAgent

                val availableBytes = try {
                    StatFs(filesDir.absolutePath).availableBytes
                } catch (_: Exception) {
                    500L * 1024L * 1024L
                }
                val maxCacheCap = if (isLowRam) 60L * 1024L * 1024L else 150L * 1024L * 1024L
                val dynamicMaxBytes = (availableBytes / 10L).coerceIn(25L * 1024L * 1024L, maxCacheCap)

                osmConfig.tileFileSystemCacheMaxBytes = dynamicMaxBytes
                osmConfig.tileFileSystemCacheTrimBytes = (dynamicMaxBytes * 8L) / 10L
                osmConfig.save(applicationContext, osmdroidPrefs)

                val lastUserAgent = osmdroidPrefs.getString("applied_user_agent_v2", null)
                if (lastUserAgent != currentUserAgent) {
                    osmConfig.osmdroidTileCache?.deleteRecursively()
                    val osmdroidDir = java.io.File(filesDir, "osmdroid")
                    if (osmdroidDir.exists()) {
                        osmdroidDir.deleteRecursively()
                    }
                    osmdroidPrefs.edit().putString("applied_user_agent_v2", currentUserAgent).apply()
                }
            } catch (_: Exception) {}
        }

        enableEdgeToEdge()

        setContent {
            TuctucMapTheme {
                var initialScreen by remember { mutableStateOf<Screen?>(null) }
                var isSplashFinished by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    // Precargar configuración dinámica en paralelo sin bloquear la resolución de pantalla
                    launch(Dispatchers.IO) {
                        try {
                            withTimeoutOrNull(2500L) {
                                repository.getAppConfig()
                            }
                        } catch (_: Exception) {}
                    }

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

                                val cachedApproval = SecurePrefs.getDriverApprovalStatusCache(this@MainActivity)
                                val cachedSubEnd = SecurePrefs.getDriverSubEndCache(this@MainActivity)

                                val isSubExpiredCached = cachedSubEnd?.let {
                                    val expiryMs = parseIsoTimestamp(it)
                                    expiryMs > 0L && ServerClock.nowMillis() > expiryMs
                                } ?: false

                                // Intentar refrescar perfil del conductor en tiempo real (timeout rápido)
                                val driver = if (uid.isNotBlank()) {
                                    try {
                                        withTimeoutOrNull(2500L) {
                                            kotlinx.coroutines.suspendCancellableCoroutine<Driver?> { continuation ->
                                                repository.getDriverProfileOnce(uid) { d ->
                                                    if (continuation.isActive) continuation.resumeWith(Result.success(d))
                                                }
                                            }
                                        }
                                    } catch (_: Exception) { null }
                                } else null

                                if (driver != null) {
                                    SecurePrefs.saveDriverSubscriptionCache(
                                        this@MainActivity,
                                        driver.estadoAprobacion,
                                        driver.subscriptionEndDate
                                    )
                                    initialScreen = com.example.tuctucmap.ui.resolveDriverScreen(driver)
                                } else {
                                    // Si no hubo respuesta inmediata de red pero el conductor está autenticado:
                                    // Entrar según caché cifrada o DriverHome (DriverHomeScreen vigila el estado en tiempo real)
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