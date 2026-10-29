package com.example.tuctucmap.ui.screens.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tuctucmap.data.repository.AdminRepository
import com.example.tuctucmap.data.repository.SupabaseRepository
import com.example.tuctucmap.ui.components.AdminDrawerItem
import com.example.tuctucmap.ui.components.AdminDrawerSheet
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminMainScreen(
    adminRepo: AdminRepository,
    supabaseRepo: SupabaseRepository,
    onLogout: () -> Unit,
    onNavigateToTrips: () -> Unit,
    onNavigateToPassengers: () -> Unit,
    onExploreMapAsPassenger: () -> Unit,
    initialTab: Int = 0
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(initialTab) }
    var pendingDriversCount by remember { mutableStateOf(0) }
    var pendingPaymentRequestsCount by remember { mutableStateOf(0) }

    // Observar conteos pendientes reactivamente
    LaunchedEffect(Unit) {
        adminRepo.observePendingCounts().collectLatest { (drivers, requests) ->
            pendingDriversCount = drivers
            pendingPaymentRequestsCount = requests
        }
    }

    val selectedDrawerItem = when (selectedTab) {
        0 -> AdminDrawerItem.DRIVERS
        1 -> AdminDrawerItem.SUBSCRIPTIONS
        2 -> AdminDrawerItem.FLEET_MAP
        3 -> AdminDrawerItem.CONFIG
        else -> AdminDrawerItem.DRIVERS
    }

    val currentEmail = adminRepo.currentAdminEmail ?: "admin@tuctucmap.gt"

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AdminDrawerSheet(
                currentEmail = currentEmail,
                selectedItem = selectedDrawerItem,
                onSelectItem = { item ->
                    scope.launch { drawerState.close() }
                    when (item) {
                        AdminDrawerItem.DRIVERS -> selectedTab = 0
                        AdminDrawerItem.SUBSCRIPTIONS -> selectedTab = 1
                        AdminDrawerItem.FLEET_MAP -> selectedTab = 2
                        AdminDrawerItem.CONFIG -> selectedTab = 3
                        AdminDrawerItem.TRIPS -> onNavigateToTrips()
                        AdminDrawerItem.PASSENGERS -> onNavigateToPassengers()
                    }
                },
                onLogout = {
                    scope.launch {
                        drawerState.close()
                        onLogout()
                    }
                },
                onExploreMap = {
                    scope.launch {
                        drawerState.close()
                        onExploreMapAsPassenger()
                    }
                }
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        val title = when (selectedTab) {
                            0 -> "Conductores & Flota"
                            1 -> "Suscripciones & Pagos"
                            2 -> "Mapa de Flota en Vivo"
                            3 -> "Configuración Comercial"
                            else -> "TucTuc Map Admin"
                        }
                        Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 18.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            // Badge sobre el icono de menú si hay pendientes
                            val totalPending = pendingDriversCount + pendingPaymentRequestsCount
                            if (totalPending > 0) {
                                BadgedBox(
                                    badge = {
                                        Badge(
                                            containerColor = Color(0xFFEF4444),
                                            contentColor = Color.White
                                        ) {
                                            Text("$totalPending")
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Menu, contentDescription = "Menú", tint = Color.White)
                                }
                            } else {
                                Icon(Icons.Default.Menu, contentDescription = "Menú", tint = Color.White)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E293B))
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = Color(0xFF1E293B),
                    contentColor = Color(0xFFF59E0B)
                ) {
                    // Pestaña 0: Conductores
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = {
                            if (pendingDriversCount > 0) {
                                BadgedBox(
                                    badge = {
                                        Badge(
                                            containerColor = Color(0xFFEF4444),
                                            contentColor = Color.White
                                        ) {
                                            Text("$pendingDriversCount")
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.People, contentDescription = "Conductores")
                                }
                            } else {
                                Icon(Icons.Default.People, contentDescription = "Conductores")
                            }
                        },
                        label = { Text("Conductores", fontSize = 11.sp) },
                        colors = navigationBarItemColors()
                    )

                    // Pestaña 1: Suscripciones / Pagos
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = {
                            if (pendingPaymentRequestsCount > 0) {
                                BadgedBox(
                                    badge = {
                                        Badge(
                                            containerColor = Color(0xFFEF4444),
                                            contentColor = Color.White
                                        ) {
                                            Text("$pendingPaymentRequestsCount")
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = "Pagos")
                                }
                            } else {
                                Icon(Icons.Default.AccountBalanceWallet, contentDescription = "Pagos")
                            }
                        },
                        label = { Text("Pagos", fontSize = 11.sp) },
                        colors = navigationBarItemColors()
                    )

                    // Pestaña 2: Mapa
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.Map, contentDescription = "Mapa Flota") },
                        label = { Text("Mapa", fontSize = 11.sp) },
                        colors = navigationBarItemColors()
                    )

                    // Pestaña 3: Configuración
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Configuración") },
                        label = { Text("Config", fontSize = 11.sp) },
                        colors = navigationBarItemColors()
                    )
                }
            },
            containerColor = Color(0xFF0F172A)
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (selectedTab) {
                    0 -> AdminDriversTab(
                        adminRepo = adminRepo,
                        supabaseRepo = supabaseRepo,
                        modifier = Modifier.fillMaxSize()
                    )
                    1 -> AdminSubscriptionsTab(
                        adminRepo = adminRepo,
                        supabaseRepo = supabaseRepo,
                        modifier = Modifier.fillMaxSize()
                    )
                    2 -> AdminFleetMapTab(
                        supabaseRepo = supabaseRepo,
                        modifier = Modifier.fillMaxSize()
                    )
                    3 -> AdminBusinessConfigTab(
                        adminRepo = adminRepo,
                        supabaseRepo = supabaseRepo,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun navigationBarItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = Color(0xFF0F172A),
    selectedTextColor = Color(0xFFF59E0B),
    indicatorColor = Color(0xFFF59E0B),
    unselectedIconColor = Color(0xFF94A3B8),
    unselectedTextColor = Color(0xFF94A3B8)
)

