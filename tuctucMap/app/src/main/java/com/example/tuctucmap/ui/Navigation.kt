package com.example.tuctucmap.ui

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.example.tuctucmap.data.repository.SupabaseRepository
import com.example.tuctucmap.ui.screens.passenger.PassengerLoginScreen
import com.example.tuctucmap.ui.screens.passenger.PassengerRegisterScreen
import com.example.tuctucmap.ui.screens.passenger.PassengerHomeScreen
import com.example.tuctucmap.ui.screens.driver.*
import com.example.tuctucmap.ui.screens.passenger.PassengerMapScreen
import kotlinx.coroutines.launch

sealed class Screen {
    object PassengerLogin : Screen()
    object PassengerRegister : Screen()
    object PassengerHome : Screen()
    object PassengerMap : Screen()
    object DriverLogin : Screen()
    object DriverRegister : Screen()
    object DriverPending : Screen()
    object DriverHome : Screen()
    object DriverSubscriptionExpired : Screen()
    /** Mapa en pantalla completa — accesible desde cualquier pantalla de conductor */
    data class DriverMapFullscreen(val returnTo: Screen) : Screen()
    object PassengerTripHistory : Screen()
    object DriverTripHistory : Screen()
}

// Mapea el estadoAprobacion del conductor a la pantalla correspondiente
private fun resolveDriverScreen(estadoAprobacion: String): Screen = when (estadoAprobacion) {
    "aprobado" -> Screen.DriverHome
    "suscripcion_vencida" -> Screen.DriverSubscriptionExpired
    else -> Screen.DriverPending // "pendiente" y cualquier otro estado
}

@Composable
fun AppNavigation(
    repository: SupabaseRepository,
    initialScreen: Screen = Screen.PassengerLogin
) {
    var currentScreen by remember { mutableStateOf(initialScreen) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Helper para guardar rol en SharedPreferences
    fun saveRole(role: String) {
        context.getSharedPreferences("tuctuc_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("user_role", role)
            .apply()
    }

    fun clearRole() {
        context.getSharedPreferences("tuctuc_prefs", Context.MODE_PRIVATE)
            .edit()
            .remove("user_role")
            .apply()
    }

    when (val screen = currentScreen) {
        is Screen.PassengerLogin -> {
            PassengerLoginScreen(
                repository = repository,
                onLoginSuccess = {
                    saveRole("passenger")
                    currentScreen = Screen.PassengerHome
                },
                onSelectDriver = {
                    if (repository.isUserLoggedIn) {
                        val uid = repository.currentUserUid ?: ""
                        repository.getDriverProfileOnce(uid) { driver ->
                            currentScreen = if (driver != null) {
                                resolveDriverScreen(driver.estadoAprobacion)
                            } else {
                                Screen.DriverLogin
                            }
                        }
                    } else {
                        currentScreen = Screen.DriverLogin
                    }
                },
                onNavigateToRegister = {
                    currentScreen = Screen.PassengerRegister
                }
            )
        }
        is Screen.PassengerRegister -> {
            PassengerRegisterScreen(
                repository = repository,
                onRegisterSuccess = {
                    saveRole("passenger")
                    currentScreen = Screen.PassengerHome
                },
                onNavigateToLogin = {
                    currentScreen = Screen.PassengerLogin
                }
            )
        }
        is Screen.PassengerHome -> {
            PassengerHomeScreen(
                repository = repository,
                onSearchTucTuc = {
                    currentScreen = Screen.PassengerMap
                },
                onViewHistory = {
                    currentScreen = Screen.PassengerTripHistory
                },
                onLogout = {
                    clearRole()
                    scope.launch { repository.logout() }
                    currentScreen = Screen.PassengerLogin
                }
            )
        }
        is Screen.PassengerTripHistory -> {
            com.example.tuctucmap.ui.screens.passenger.PassengerTripHistoryScreen(
                repository = repository,
                onBack = {
                    currentScreen = Screen.PassengerHome
                }
            )
        }
        is Screen.PassengerMap -> {
            PassengerMapScreen(
                repository = repository,
                onBackToOnboarding = {
                    currentScreen = Screen.PassengerHome
                }
            )
        }
        is Screen.DriverLogin -> {
            DriverLoginScreen(
                repository = repository,
                onNavigateToRegister = {
                    currentScreen = Screen.DriverRegister
                },
                onLoginSuccess = {
                    saveRole("driver")
                    val uid = repository.currentUserUid ?: ""
                    repository.getDriverProfileOnce(uid) { driver ->
                        currentScreen = if (driver != null) {
                            resolveDriverScreen(driver.estadoAprobacion)
                        } else {
                            Screen.DriverPending
                        }
                    }
                },
                onBackToOnboarding = {
                    currentScreen = Screen.PassengerLogin
                }
            )
        }
        is Screen.DriverRegister -> {
            DriverRegisterScreen(
                repository = repository,
                onNavigateToLogin = {
                    currentScreen = Screen.DriverLogin
                },
                onRegisterSuccess = {
                    saveRole("driver")
                    currentScreen = Screen.DriverHome  // Auto-aprobado con 7 días de prueba
                },
                onBackToOnboarding = {
                    currentScreen = Screen.PassengerLogin
                }
            )
        }
        is Screen.DriverPending -> {
            DriverPendingScreen(
                repository = repository,
                onApproved = {
                    currentScreen = Screen.DriverHome
                },
                onSubscriptionExpired = {
                    currentScreen = Screen.DriverSubscriptionExpired
                },
                onLogout = {
                    clearRole()
                    scope.launch { repository.logout() }
                    currentScreen = Screen.PassengerLogin
                },
                onViewMap = {
                    currentScreen = Screen.DriverMapFullscreen(returnTo = Screen.DriverPending)
                }
            )
        }
        is Screen.DriverHome -> {
            DriverHomeScreen(
                repository = repository,
                onLogout = {
                    clearRole()
                    scope.launch { repository.logout() }
                    currentScreen = Screen.PassengerLogin
                },
                onSubscriptionExpired = {
                    currentScreen = Screen.DriverSubscriptionExpired
                },
                onApprovalRevoked = {
                    currentScreen = Screen.DriverPending
                },
                onViewMap = {
                    currentScreen = Screen.DriverMapFullscreen(returnTo = Screen.DriverHome)
                },
                onViewHistory = {
                    currentScreen = Screen.DriverTripHistory
                }
            )
        }
        is Screen.DriverTripHistory -> {
            com.example.tuctucmap.ui.screens.driver.DriverTripHistoryScreen(
                repository = repository,
                onBack = {
                    currentScreen = Screen.DriverHome
                }
            )
        }
        is Screen.DriverSubscriptionExpired -> {
            DriverSubscriptionExpiredScreen(
                repository = repository,
                onRenewed = {
                    currentScreen = Screen.DriverHome
                },
                onLogout = {
                    clearRole()
                    scope.launch { repository.logout() }
                    currentScreen = Screen.PassengerLogin
                },
                onViewMap = {
                    currentScreen = Screen.DriverMapFullscreen(returnTo = Screen.DriverSubscriptionExpired)
                }
            )
        }
        is Screen.DriverMapFullscreen -> {
            DriverMapFullscreenScreen(
                repository = repository,
                onBack = {
                    currentScreen = screen.returnTo
                }
            )
        }
    }
}
