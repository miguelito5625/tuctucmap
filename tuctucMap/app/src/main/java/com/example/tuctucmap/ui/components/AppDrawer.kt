package com.example.tuctucmap.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tuctucmap.data.model.Driver
import com.example.tuctucmap.data.model.Passenger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class PassengerDrawerItem {
    HOME,
    MAP,
    HISTORY,
    PROFILE
}

enum class DriverDrawerItem {
    HOME,
    MAP,
    HISTORY,
    SUBSCRIPTIONS,
    PROFILE
}

@Composable
fun PassengerDrawerSheet(
    passenger: Passenger?,
    currentEmail: String,
    selectedItem: PassengerDrawerItem,
    onSelectItem: (PassengerDrawerItem) -> Unit,
    onLogout: () -> Unit,
    onNavigateToLogin: () -> Unit = {},
    onNavigateToDriver: () -> Unit = {},
    isGuest: Boolean = false,
    modifier: Modifier = Modifier
) {
    ModalDrawerSheet(
        modifier = modifier.width(320.dp),
        drawerContainerColor = Color(0xFF0F2027),
        drawerContentColor = Color.White,
        drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0F2027),
                            Color(0xFF1B323D),
                            Color(0xFF2C5364)
                        )
                    )
                )
        ) {
            // ── Header de Usuario ───────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color(0xFF1E3A47), Color(0xFF2C5364))
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 26.dp)
            ) {
                if (isGuest) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(58.dp)
                                    .clip(CircleShape)
                                    .background(
                                        brush = Brush.linearGradient(
                                            colors = listOf(Color(0xFF38BDF8), Color(0xFF0284C7))
                                        )
                                    )
                                    .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Modo Pasajero",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0x33F9D423),
                                    border = BorderStroke(1.dp, Color(0x66F9D423))
                                ) {
                                    Text(
                                        text = "Invitado",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFF9D423),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Inicia sesión para guardar tu historial de viajes y preferencias.",
                            fontSize = 12.sp,
                            color = Color(0xFFCFD8DC),
                            lineHeight = 16.sp
                        )
                    }
                } else {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Avatar circular con inicial
                            val initial = (passenger?.nombre?.trim()?.firstOrNull() ?: 'P').uppercaseChar().toString()
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(
                                        brush = Brush.linearGradient(
                                            colors = listOf(Color(0xFFF9D423), Color(0xFFFF9800))
                                        )
                                    )
                                    .border(2.dp, Color.White.copy(alpha = 0.6f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = initial,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF0F2027)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = passenger?.nombre?.ifBlank { "Pasajero" } ?: "Pasajero",
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0x3300C9FF),
                                    border = BorderStroke(1.dp, Color(0x6600C9FF))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            tint = Color(0xFF80D8FF),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Pasajero",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF80D8FF)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        val displayEmail = if (!passenger?.email.isNullOrBlank()) passenger!!.email else currentEmail
                        if (displayEmail.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = null,
                                    tint = Color(0xFFB0BEC5),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = displayEmail,
                                    fontSize = 12.sp,
                                    color = Color(0xFFCFD8DC),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (!passenger?.telefono.isNullOrBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = null,
                                    tint = Color(0xFFB0BEC5),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = passenger!!.telefono,
                                    fontSize = 12.sp,
                                    color = Color(0xFFCFD8DC)
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.12f), thickness = 1.dp)

            // ── Opciones de Navegación ──────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DrawerNavigationItem(
                    label = "Mapa en Vivo",
                    icon = Icons.Default.Map,
                    isSelected = selectedItem == PassengerDrawerItem.MAP || selectedItem == PassengerDrawerItem.HOME,
                    onClick = { onSelectItem(PassengerDrawerItem.MAP) }
                )

                if (isGuest) {
                    DrawerNavigationItem(
                        label = "Acceder a mi Cuenta",
                        icon = Icons.Default.AccountCircle,
                        isSelected = false,
                        onClick = onNavigateToLogin
                    )
                } else {
                    DrawerNavigationItem(
                        label = "Historial de Viajes",
                        icon = Icons.Default.History,
                        isSelected = selectedItem == PassengerDrawerItem.HISTORY,
                        onClick = { onSelectItem(PassengerDrawerItem.HISTORY) }
                    )

                    DrawerNavigationItem(
                        label = "Mi Perfil y Contraseña",
                        icon = Icons.Default.AccountCircle,
                        isSelected = selectedItem == PassengerDrawerItem.PROFILE,
                        onClick = { onSelectItem(PassengerDrawerItem.PROFILE) }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(4.dp))

                DrawerNavigationItem(
                    label = "Soy Conductor",
                    icon = Icons.Default.ElectricRickshaw,
                    isSelected = false,
                    onClick = onNavigateToDriver
                )
            }

            // ── Footer / Cerrar Sesión ──────────────────────────────────────
            HorizontalDivider(color = Color.White.copy(alpha = 0.12f), thickness = 1.dp)

            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                if (!isGuest) {
                    NavigationDrawerItem(
                        label = {
                            Text(
                                text = "Cerrar Sesión",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFFF5252)
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = "Cerrar Sesión",
                                tint = Color(0xFFFF5252)
                            )
                        },
                        selected = false,
                        onClick = onLogout,
                        shape = RoundedCornerShape(12.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedContainerColor = Color(0x1AFF5252)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                }

                Text(
                    text = "TucTuc Map • v1.0",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }
    }
}

@Composable
fun DriverDrawerSheet(
    driver: Driver?,
    currentEmail: String,
    selectedItem: DriverDrawerItem,
    onSelectItem: (DriverDrawerItem) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalDrawerSheet(
        modifier = modifier.width(320.dp),
        drawerContainerColor = Color(0xFF0F2027),
        drawerContentColor = Color.White,
        drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0F2027),
                            Color(0xFF1B323D),
                            Color(0xFF2C5364)
                        )
                    )
                )
        ) {
            // ── Header de Conductor ─────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color(0xFF1E3A47), Color(0xFF2C5364))
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 26.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Avatar circular con inicial
                        val initial = (driver?.nombre?.trim()?.firstOrNull() ?: 'C').uppercaseChar().toString()
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(Color(0xFFF9D423), Color(0xFFFFB300))
                                    )
                                )
                                .border(2.dp, Color.White.copy(alpha = 0.6f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initial,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF0F2027)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = driver?.nombre?.ifBlank { "Conductor" } ?: "Conductor",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFF9D423)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DirectionsCar,
                                        contentDescription = null,
                                        tint = Color(0xFF0F2027),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Conductor TucTuc",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F2027)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Placas
                    if (!driver?.placas.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsCar,
                                contentDescription = null,
                                tint = Color(0xFFF9D423),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Placas: ${driver!!.placas}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }

                    // Suscripción estado
                    val isExpired = driver?.isSubscriptionExpired == true
                    val expiryDateFormatted = remember(driver?.fechaVencimientoSuscripcion) {
                        formatDate(driver?.fechaVencimientoSuscripcion)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isExpired) Color(0x33FF5252) else Color(0x334CAF50),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isExpired) Color(0x66FF5252) else Color(0x664CAF50)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (isExpired) Color(0xFFFF5252) else Color(0xFF4CAF50))
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isExpired) "Suscripción Vencida" else "Suscripción Activa ($expiryDateFormatted)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isExpired) Color(0xFFFF8A80) else Color(0xFFA5D6A7)
                                )
                            }
                        }
                    }

                    val displayEmail = if (!driver?.email.isNullOrBlank()) driver!!.email else currentEmail
                    if (displayEmail.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = null,
                                tint = Color(0xFFB0BEC5),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = displayEmail,
                                fontSize = 12.sp,
                                color = Color(0xFFCFD8DC),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (!driver?.telefono.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = null,
                                tint = Color(0xFFB0BEC5),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = driver!!.telefono,
                                fontSize = 12.sp,
                                color = Color(0xFFCFD8DC)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.12f), thickness = 1.dp)

            // ── Opciones de Navegación ──────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DrawerNavigationItem(
                    label = "Panel de Conductor",
                    icon = Icons.Default.Dashboard,
                    isSelected = selectedItem == DriverDrawerItem.HOME,
                    onClick = { onSelectItem(DriverDrawerItem.HOME) }
                )

                DrawerNavigationItem(
                    label = "Ver Mapa en Vivo",
                    icon = Icons.Default.Map,
                    isSelected = selectedItem == DriverDrawerItem.MAP,
                    onClick = { onSelectItem(DriverDrawerItem.MAP) }
                )

                DrawerNavigationItem(
                    label = "Historial de Viajes",
                    icon = Icons.Default.History,
                    isSelected = selectedItem == DriverDrawerItem.HISTORY,
                    onClick = { onSelectItem(DriverDrawerItem.HISTORY) }
                )

                DrawerNavigationItem(
                    label = "Mi Suscripción",
                    icon = Icons.Default.CardMembership,
                    isSelected = selectedItem == DriverDrawerItem.SUBSCRIPTIONS,
                    onClick = { onSelectItem(DriverDrawerItem.SUBSCRIPTIONS) }
                )

                DrawerNavigationItem(
                    label = "Mi Perfil y Contraseña",
                    icon = Icons.Default.AccountCircle,
                    isSelected = selectedItem == DriverDrawerItem.PROFILE,
                    onClick = { onSelectItem(DriverDrawerItem.PROFILE) }
                )
            }

            // ── Footer / Cerrar Sesión ──────────────────────────────────────
            HorizontalDivider(color = Color.White.copy(alpha = 0.12f), thickness = 1.dp)

            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                NavigationDrawerItem(
                    label = {
                        Text(
                            text = "Cerrar Sesión",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFFF5252)
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Cerrar Sesión",
                            tint = Color(0xFFFF5252)
                        )
                    },
                    selected = false,
                    onClick = onLogout,
                    shape = RoundedCornerShape(12.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color(0x1AFF5252)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "TucTuc Map • v1.0",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun DrawerNavigationItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        label = {
            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color(0xFFF9D423) else Color.White
            )
        },
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) Color(0xFFF9D423) else Color(0xFFB0BEC5),
                modifier = Modifier.size(22.dp)
            )
        },
        selected = isSelected,
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = Color(0x33F9D423),
            unselectedContainerColor = Color.Transparent
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

private fun formatDate(dateStr: String?): String {
    if (dateStr.isNullOrBlank()) return "Sin fecha"
    val ts = com.example.tuctucmap.data.model.parseIsoTimestamp(dateStr)
    if (ts <= 0L) return "Sin fecha"
    val sdf = SimpleDateFormat("dd MMM yyyy", Locale("es", "GT"))
    return sdf.format(Date(ts))
}
