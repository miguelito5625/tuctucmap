package com.example.tuctucmap.ui.screens.passenger

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tuctucmap.data.model.Passenger
import com.example.tuctucmap.data.repository.SupabaseRepository
import com.example.tuctucmap.ui.components.PassengerDrawerItem
import com.example.tuctucmap.ui.components.PassengerDrawerSheet
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Deprecated("Depurado por arquitectura UX: Sustituido por PassengerMapScreen como pantalla núcleo interactiva")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassengerHomeScreen(
    repository: SupabaseRepository,
    onSearchTucTuc: () -> Unit,
    onViewHistory: () -> Unit = {},
    onViewProfile: () -> Unit = {},
    onLogout: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var passenger by remember { mutableStateOf<Passenger?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val authEmail = repository.currentUserEmail ?: ""

    val uid = repository.currentUserUid
    LaunchedEffect(uid) {
        if (!uid.isNullOrBlank()) {
            repository.observePassengerProfile(uid).collectLatest { profile ->
                passenger = profile
                isLoading = false
            }
        } else {
            isLoading = false
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            PassengerDrawerSheet(
                passenger = passenger,
                currentEmail = authEmail,
                selectedItem = PassengerDrawerItem.HOME,
                onSelectItem = { item ->
                    scope.launch { drawerState.close() }
                    when (item) {
                        PassengerDrawerItem.HOME -> { /* Ya en Home */ }
                        PassengerDrawerItem.MAP -> onSearchTucTuc()
                        PassengerDrawerItem.HISTORY -> onViewHistory()
                        PassengerDrawerItem.PROFILE -> onViewProfile()
                    }
                },
                onLogout = {
                    scope.launch {
                        drawerState.close()
                        repository.logout()
                        onLogout()
                    }
                }
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "TucTuc Map",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Menú lateral",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onViewProfile) {
                            val initial = (passenger?.nombre?.trim()?.firstOrNull() ?: 'P')
                                .uppercaseChar().toString()
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        brush = Brush.linearGradient(
                                            listOf(Color(0xFFF9D423), Color(0xFFFF9800))
                                        )
                                    )
                                    .border(1.5.dp, Color.White.copy(alpha = 0.8f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = initial,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F2027)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF0F2027)
                    )
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF0F2027),
                                Color(0xFF203A43),
                                Color(0xFF2C5364)
                            )
                        )
                    )
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color(0xFFF9D423))
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        // Tarjeta de bienvenida
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onViewProfile() },
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0x18FFFFFF))
                        ) {
                            Row(
                                modifier = Modifier.padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                val initial = (passenger?.nombre?.trim()?.firstOrNull() ?: 'P')
                                    .uppercaseChar().toString()
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(
                                            brush = Brush.linearGradient(
                                                listOf(Color(0xFFF9D423), Color(0xFFFF9800))
                                            )
                                        )
                                        .border(2.dp, Color.White.copy(alpha = 0.7f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = initial,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F2027)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "¡Hola ${passenger?.nombre?.ifBlank { "Pasajero" } ?: "Pasajero"}!",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Toca para ver o editar tu perfil",
                                        fontSize = 12.sp,
                                        color = Color(0xFFB0BEC5)
                                    )
                                }

                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = "Ver Perfil",
                                    tint = Color(0xFFF9D423)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(36.dp))

                        Text(
                            text = "¿A dónde quieres ir hoy?",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Encuentra conductores de mototaxi en tiempo real cerca de ti",
                            fontSize = 14.sp,
                            color = Color(0xFFB0BEC5),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(36.dp))

                        // Botón Buscar TucTuc
                        Button(
                            onClick = onSearchTucTuc,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFF9D423),
                                contentColor = Color(0xFF0F2027)
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Map, contentDescription = null)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Buscar TucTuc Cerca",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Botón Historial de Viajes
                        Button(
                            onClick = onViewHistory,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2C5364),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(imageVector = Icons.Default.History, contentDescription = null)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Historial de Viajes",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Botón Mi Perfil
                        OutlinedButton(
                            onClick = onViewProfile,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                Color.White.copy(alpha = 0.35f)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = Color(0xFFF9D423)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Mi Perfil y Contraseña",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
