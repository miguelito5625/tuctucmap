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

import androidx.activity.compose.BackHandler

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
    val backStack = remember { mutableStateListOf(initialScreen) }
    val currentScreen = backStack.last()

    BackHandler(enabled = backStack.size > 1) {
        backStack.removeLast()
    }

    fun navigate(screen: Screen, clearStack: Boolean = false) {
        if (clearStack) {
            backStack.clear()
            backStack.add(screen)
        } else {
            if (backStack.lastOrNull() != screen) {
                backStack.add(screen)
            }
        }
    }

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
                    navigate(Screen.PassengerHome, clearStack = true)
                },
                onSelectDriver = {
                    if (repository.isUserLoggedIn) {
                        val uid = repository.currentUserUid ?: ""
                        repository.getDriverProfileOnce(uid) { driver ->
                            if (driver != null) {
                                navigate(resolveDriverScreen(driver.estadoAprobacion), clearStack = true)
                            } else {
                                navigate(Screen.DriverLogin, clearStack = true)
                            }
                        }
                    } else {
                        navigate(Screen.DriverLogin, clearStack = true)
                    }
                },
                onNavigateToRegister = {
                    navigate(Screen.PassengerRegister)
                }
            )
        }
        is Screen.PassengerRegister -> {
            PassengerRegisterScreen(
                repository = repository,
                onRegisterSuccess = {
                    saveRole("passenger")
                    navigate(Screen.PassengerHome, clearStack = true)
                },
                onNavigateToLogin = {
                    navigate(Screen.PassengerLogin, clearStack = true)
                }
            )
        }
        is Screen.PassengerHome -> {
            PassengerHomeScreen(
                repository = repository,
                onSearchTucTuc = {
                    navigate(Screen.PassengerMap)
                },
                onViewHistory = {
                    navigate(Screen.PassengerTripHistory)
                },
                onLogout = {
                    clearRole()
                    scope.launch { repository.logout() }
                    navigate(Screen.PassengerLogin, clearStack = true)
                }
            )
        }
        is Screen.PassengerTripHistory -> {
            com.example.tuctucmap.ui.screens.passenger.PassengerTripHistoryScreen(
                repository = repository,
                onBack = {
                    backStack.removeLast()
                }
            )
        }
        is Screen.PassengerMap -> {
            PassengerMapScreen(
                repository = repository,
                onBackToOnboarding = {
                    backStack.removeLast()
                }
            )
        }
        is Screen.DriverLogin -> {
            DriverLoginScreen(
                repository = repository,
                onNavigateToRegister = {
                    navigate(Screen.DriverRegister)
                },
                onLoginSuccess = {
                    saveRole("driver")
                    val uid = repository.currentUserUid ?: ""
                    repository.getDriverProfileOnce(uid) { driver ->
                        if (driver != null) {
                            navigate(resolveDriverScreen(driver.estadoAprobacion), clearStack = true)
                        } else {
                            navigate(Screen.DriverPending, clearStack = true)
                        }
                    }
                },
                onBackToOnboarding = {
                    navigate(Screen.PassengerLogin, clearStack = true)
                }
            )
        }
        is Screen.DriverRegister -> {
            DriverRegisterScreen(
                repository = repository,
                onNavigateToLogin = {
                    navigate(Screen.DriverLogin, clearStack = true)
                },
                onRegisterSuccess = {
                    saveRole("driver")
                    navigate(Screen.DriverHome, clearStack = true)  // Auto-aprobado con 7 días de prueba
                },
                onBackToOnboarding = {
                    navigate(Screen.PassengerLogin, clearStack = true)
                }
            )
        }
        is Screen.DriverPending -> {
            DriverPendingScreen(
                repository = repository,
                onApproved = {
                    navigate(Screen.DriverHome, clearStack = true)
                },
                onSubscriptionExpired = {
                    navigate(Screen.DriverSubscriptionExpired, clearStack = true)
                },
                onLogout = {
                    clearRole()
                    scope.launch { repository.logout() }
                    navigate(Screen.PassengerLogin, clearStack = true)
                },
                onViewMap = {
                    navigate(Screen.DriverMapFullscreen(returnTo = Screen.DriverPending))
                }
            )
        }
        is Screen.DriverHome -> {
            DriverHomeScreen(
                repository = repository,
                onLogout = {
                    clearRole()
                    scope.launch { repository.logout() }
                    navigate(Screen.PassengerLogin, clearStack = true)
                },
                onSubscriptionExpired = {
                    navigate(Screen.DriverSubscriptionExpired, clearStack = true)
                },
                onApprovalRevoked = {
                    navigate(Screen.DriverPending, clearStack = true)
                },
                onViewMap = {
                    navigate(Screen.DriverMapFullscreen(returnTo = Screen.DriverHome))
                },
                onViewHistory = {
                    navigate(Screen.DriverTripHistory)
                }
            )
        }
        is Screen.DriverTripHistory -> {
            com.example.tuctucmap.ui.screens.driver.DriverTripHistoryScreen(
                repository = repository,
                onBack = {
                    backStack.removeLast()
                }
            )
        }
        is Screen.DriverSubscriptionExpired -> {
            DriverSubscriptionExpiredScreen(
                repository = repository,
                onRenewed = {
                    navigate(Screen.DriverHome, clearStack = true)
                },
                onLogout = {
                    clearRole()
                    scope.launch { repository.logout() }
                    navigate(Screen.PassengerLogin, clearStack = true)
                },
                onViewMap = {
                    navigate(Screen.DriverMapFullscreen(returnTo = Screen.DriverSubscriptionExpired))
                }
            )
        }
        is Screen.DriverMapFullscreen -> {
            DriverMapFullscreenScreen(
                repository = repository,
                onBack = {
                    backStack.removeLast()
                }
            )
        }
    }
}
