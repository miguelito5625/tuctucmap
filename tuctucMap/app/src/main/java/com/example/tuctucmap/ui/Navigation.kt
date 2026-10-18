package com.example.tuctucmap.ui

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.example.tuctucmap.data.model.Driver
import com.example.tuctucmap.data.repository.SupabaseRepository
import com.example.tuctucmap.ui.screens.passenger.PassengerLoginScreen
import com.example.tuctucmap.ui.screens.passenger.PassengerRegisterScreen
import com.example.tuctucmap.ui.screens.passenger.PassengerProfileScreen
import com.example.tuctucmap.ui.screens.driver.*
import com.example.tuctucmap.ui.screens.passenger.PassengerMapScreen
import kotlinx.coroutines.launch

import androidx.activity.compose.BackHandler

sealed class Screen {
    object PassengerLogin : Screen()
    object PassengerRegister : Screen()
    object PassengerMap : Screen()
    object DriverLogin : Screen()
    object DriverRegister : Screen()
    object DriverPending : Screen()
    object DriverHome : Screen()
    object DriverSubscriptionExpired : Screen()
    /** Mapa en pantalla completa — accesible desde cualquier pantalla de conductor */
    data class DriverMapFullscreen(val returnTo: Screen) : Screen()
    /** Navegación nivel 1 con trazado de ruta en app hacia el pasajero */
    data class DriverRouteToPassenger(val trip: com.example.tuctucmap.data.repository.SupabaseRepository.TripInfo) : Screen()
    object PassengerTripHistory : Screen()
    object DriverTripHistory : Screen()
    object DriverReports : Screen()
    object PassengerProfile : Screen()
    object DriverProfile : Screen()
    object DriverSubscription : Screen()
}

// Mapea el perfil del conductor a la pantalla correspondiente
fun resolveDriverScreen(driver: Driver?, defaultIfNull: Screen = Screen.DriverLogin): Screen {
    if (driver == null) return defaultIfNull
    return when {
        driver.isSubscriptionExpired -> Screen.DriverSubscriptionExpired
        driver.estadoAprobacion == "aprobado" -> Screen.DriverHome
        else -> Screen.DriverPending // "pendiente", "rechazado", etc.
    }
}

@Composable
fun AppNavigation(
    repository: SupabaseRepository,
    initialScreen: Screen = Screen.PassengerMap
) {
    val backStack = remember { mutableStateListOf(initialScreen) }
    val currentScreen = backStack.last()

    BackHandler(enabled = backStack.size > 1) {
        backStack.removeAt(backStack.lastIndex)
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
                    navigate(Screen.PassengerMap, clearStack = true)
                },
                onSelectDriver = {
                    if (repository.isUserLoggedIn) {
                        val uid = repository.currentUserUid ?: ""
                        repository.getDriverProfileOnce(uid) { driver ->
                            if (driver != null) {
                                navigate(resolveDriverScreen(driver), clearStack = true)
                            } else {
                                navigate(Screen.DriverLogin)
                            }
                        }
                    } else {
                        navigate(Screen.DriverLogin)
                    }
                },
                onNavigateToRegister = {
                    navigate(Screen.PassengerRegister)
                },
                onExploreMapAsGuest = {
                    navigate(Screen.PassengerMap, clearStack = true)
                },
                onBack = if (backStack.size > 1) { { backStack.removeAt(backStack.lastIndex) } } else null
            )
        }
        is Screen.PassengerRegister -> {
            PassengerRegisterScreen(
                repository = repository,
                onRegisterSuccess = {
                    saveRole("passenger")
                    navigate(Screen.PassengerMap, clearStack = true)
                },
                onNavigateToLogin = {
                    navigate(Screen.PassengerLogin, clearStack = true)
                },
                onExploreMapAsGuest = {
                    navigate(Screen.PassengerMap, clearStack = true)
                },
                onBack = if (backStack.size > 1) { { backStack.removeAt(backStack.lastIndex) } } else null
            )
        }
        is Screen.PassengerTripHistory -> {
            com.example.tuctucmap.ui.screens.passenger.PassengerTripHistoryScreen(
                repository = repository,
                onBack = {
                    backStack.removeAt(backStack.lastIndex)
                }
            )
        }
        is Screen.PassengerMap -> {
            PassengerMapScreen(
                repository = repository,
                onBack = if (backStack.size > 1) { { backStack.removeAt(backStack.lastIndex) } } else null,
                onNavigateToPassengerLogin = {
                    navigate(Screen.PassengerLogin)
                },
                onNavigateToPassengerRegister = {
                    navigate(Screen.PassengerRegister)
                },
                onNavigateToDriverLogin = {
                    navigate(Screen.DriverLogin)
                },
                onNavigateToDriverRegister = {
                    navigate(Screen.DriverRegister)
                },
                onNavigateToPassengerProfile = {
                    navigate(Screen.PassengerProfile)
                },
                onNavigateToPassengerHistory = {
                    navigate(Screen.PassengerTripHistory)
                },
                onLogout = {
                    scope.launch {
                        clearRole()
                        com.example.tuctucmap.data.SecurePrefs.clearTokens(context)
                        repository.logout()
                        navigate(Screen.PassengerLogin, clearStack = true)
                    }
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
                            com.example.tuctucmap.data.SecurePrefs.saveDriverSubscriptionCache(
                                context,
                                driver.estadoAprobacion,
                                driver.subscriptionEndDate
                            )
                            navigate(resolveDriverScreen(driver, defaultIfNull = Screen.DriverHome), clearStack = true)
                        } else {
                            navigate(Screen.DriverHome, clearStack = true)
                        }
                    }
                },
                onBackToOnboarding = {
                    if (backStack.size > 1) {
                        backStack.removeAt(backStack.lastIndex)
                    } else {
                        navigate(Screen.PassengerMap, clearStack = true)
                    }
                },
                onExploreMapAsGuest = {
                    navigate(Screen.PassengerMap, clearStack = true)
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
                    val uid = repository.currentUserUid ?: ""
                    repository.getDriverProfileOnce(uid) { driver ->
                        if (driver != null) {
                            com.example.tuctucmap.data.SecurePrefs.saveDriverSubscriptionCache(
                                context,
                                driver.estadoAprobacion,
                                driver.subscriptionEndDate
                            )
                            navigate(resolveDriverScreen(driver, defaultIfNull = Screen.DriverHome), clearStack = true)
                        } else {
                            navigate(Screen.DriverHome, clearStack = true)
                        }
                    }
                },
                onBackToOnboarding = {
                    if (backStack.size > 1) {
                        backStack.removeAt(backStack.lastIndex)
                    } else {
                        navigate(Screen.PassengerMap, clearStack = true)
                    }
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
                    scope.launch {
                        clearRole()
                        com.example.tuctucmap.data.SecurePrefs.clearTokens(context)
                        repository.logout()
                        navigate(Screen.DriverLogin, clearStack = true)
                    }
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
                    scope.launch {
                        clearRole()
                        com.example.tuctucmap.data.SecurePrefs.clearTokens(context)
                        repository.logout()
                        navigate(Screen.DriverLogin, clearStack = true)
                    }
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
                },
                onViewReports = {
                    navigate(Screen.DriverReports)
                },
                onViewSubscription = {
                    navigate(Screen.DriverSubscription)
                },
                onViewProfile = {
                    navigate(Screen.DriverProfile)
                },
                onNavigateToRoute = { trip ->
                    navigate(Screen.DriverRouteToPassenger(trip))
                }
            )
        }
        is Screen.DriverRouteToPassenger -> {
            DriverRouteToPassengerScreen(
                repository = repository,
                trip = screen.trip,
                onBack = {
                    backStack.removeAt(backStack.lastIndex)
                },
                onTripCompleted = {
                    backStack.removeAt(backStack.lastIndex)
                }
            )
        }
        is Screen.DriverTripHistory -> {
            com.example.tuctucmap.ui.screens.driver.DriverTripHistoryScreen(
                repository = repository,
                onBack = {
                    backStack.removeAt(backStack.lastIndex)
                }
            )
        }
        is Screen.DriverReports -> {
            DriverReportsScreen(
                repository = repository,
                onBack = {
                    backStack.removeAt(backStack.lastIndex)
                }
            )
        }
        is Screen.DriverSubscriptionExpired -> {
            DriverSubscriptionExpiredScreen(
                repository = repository,
                onRenewed = {
                    navigate(Screen.DriverHome, clearStack = true)
                },
                onApprovalRevoked = {
                    navigate(Screen.DriverPending, clearStack = true)
                },
                onLogout = {
                    scope.launch {
                        clearRole()
                        com.example.tuctucmap.data.SecurePrefs.clearTokens(context)
                        repository.logout()
                        navigate(Screen.DriverLogin, clearStack = true)
                    }
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
                    backStack.removeAt(backStack.lastIndex)
                },
                onNavigateToRoute = { trip ->
                    navigate(Screen.DriverRouteToPassenger(trip))
                }
            )
        }
        is Screen.PassengerProfile -> {
            PassengerProfileScreen(
                repository = repository,
                onBack = {
                    backStack.removeAt(backStack.lastIndex)
                }
            )
        }
        is Screen.DriverProfile -> {
            DriverProfileScreen(
                repository = repository,
                onBack = {
                    backStack.removeAt(backStack.lastIndex)
                }
            )
        }
        is Screen.DriverSubscription -> {
            DriverSubscriptionScreen(
                repository = repository,
                onBack = {
                    backStack.removeAt(backStack.lastIndex)
                }
            )
        }
    }
}
