package com.example.tuctucmap.ui.screens.admin

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.tuctucmap.data.model.DriverLocation
import com.example.tuctucmap.data.repository.SupabaseRepository
import com.example.tuctucmap.ui.util.AppTileSource
import com.example.tuctucmap.ui.util.MarkerIconUtils
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.net.URLEncoder
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminFleetMapTab(
    supabaseRepo: SupabaseRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var liveDrivers by remember { mutableStateOf<List<DriverLocation>>(emptyList()) }
    var selectedDriver by remember { mutableStateOf<DriverLocation?>(null) }
    var showDriversSheet by remember { mutableStateOf(false) }
    var hasCenteredInitialCamera by remember { mutableStateOf(false) }

    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }
    val driverMarkers = remember { mutableMapOf<String, Marker>() }

    // Íconos cacheados
    val tucTucIcon = remember(context) { MarkerIconUtils.getTucTucIcon(context) }
    val pausedTucTucIcon = remember(context) { MarkerIconUtils.getPausedTucTucIcon(context) }

    // Suscripción en tiempo real a Firebase Realtime Database sin límite de radio
    DisposableEffect(Unit) {
        val ref = Firebase.database.getReference("driver_locations")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<DriverLocation>()
                for (child in snapshot.children) {
                    val loc = supabaseRepo.parseDriverLocationSnapshot(child)
                    if (loc != null && loc.active && loc.isRecentlyActive()) {
                        list.add(loc)
                    }
                }
                liveDrivers = list
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        onDispose {
            ref.removeEventListener(listener)
        }
    }

    // Ciclo de vida estricto para OSMDroid
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapViewInstance?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapViewInstance?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            driverMarkers.clear()
            mapViewInstance?.onPause()
            mapViewInstance?.onDetach()
        }
    }

    val availableCount = remember(liveDrivers) { liveDrivers.count { it.isAvailable } }
    val occupiedCount = remember(liveDrivers) { liveDrivers.count { !it.isAvailable } }

    LaunchedEffect(liveDrivers) {
        if (!hasCenteredInitialCamera && liveDrivers.isNotEmpty()) {
            val first = liveDrivers.first()
            mapViewInstance?.controller?.animateTo(GeoPoint(first.lat, first.lon))
            mapViewInstance?.controller?.setZoom(15.0)
            hasCenteredInitialCamera = true
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // ── MapView OSMDroid ─────────────────────────────────────────────────
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(AppTileSource)
                    setMultiTouchControls(true)
                    isTilesScaledToDpi = true
                    controller.setZoom(14.5)
                    // Centrar inicialmente en el primer conductor o en Guatemala por defecto
                    controller.setCenter(GeoPoint(14.6349, -90.5069))
                    mapViewInstance = this
                }
            },
            update = { mapView ->
                mapViewInstance = mapView

                // Remover marcadores de conductores desconectados
                val activeUids = liveDrivers.map { it.uid }.toSet()
                val toRemove = driverMarkers.keys.filter { it !in activeUids }
                toRemove.forEach { uid ->
                    driverMarkers[uid]?.let { mapView.overlays.remove(it) }
                    driverMarkers.remove(uid)
                }

                // Actualizar o crear marcadores para conductores activos
                for (driver in liveDrivers) {
                    val geoPoint = GeoPoint(driver.lat, driver.lon)
                    val isAvailable = driver.isAvailable
                    val icon = if (isAvailable) tucTucIcon else pausedTucTucIcon

                    var marker = driverMarkers[driver.uid]
                    if (marker == null) {
                        marker = Marker(mapView).apply {
                            this.position = geoPoint
                            this.icon = icon
                            this.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            this.title = driver.nombre
                            this.snippet = "${driver.displayIdentifier} • ${if (isAvailable) "Disponible" else "En Viaje"}"
                            this.setOnMarkerClickListener { m, _ ->
                                selectedDriver = driver
                                m.showInfoWindow()
                                true
                            }
                        }
                        driverMarkers[driver.uid] = marker
                        mapView.overlays.add(marker)
                    } else {
                        marker.position = geoPoint
                        marker.icon = icon
                        marker.title = driver.nombre
                        marker.snippet = "${driver.displayIdentifier} • ${if (isAvailable) "Disponible" else "En Viaje"}"
                    }
                }
                mapView.invalidate()
            },
            modifier = Modifier.fillMaxSize()
        )

        // ── Píldora Superior de Estadísticas en Vivo ─────────────────────────
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xDD0F172A)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$availableCount Disp.",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF59E0B))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$occupiedCount Ocupados",
                        color = Color(0xFFF59E0B),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "• ${liveDrivers.size} Total",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
            }
        }

        // ── Tarjeta Inferior del Conductor Seleccionado ──────────────────────
        selectedDriver?.let { driver ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xF01E293B)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = 80.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = driver.nombre.ifBlank { "TucTuc" },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val isAvail = driver.isAvailable
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isAvail) Color(0x2210B981) else Color(0x22F59E0B)
                            ) {
                                Text(
                                    text = if (isAvail) "Disponible" else "En Viaje",
                                    color = if (isAvail) Color(0xFF10B981) else Color(0xFFF59E0B),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${driver.displayIdentifier} • 📞 ${driver.telefono}",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    // Botón WhatsApp
                    IconButton(
                        onClick = {
                            openWhatsAppDirect(context, driver.telefono, driver.nombre)
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFF10B981), CircleShape)
                    ) {
                        Icon(Icons.Default.Chat, contentDescription = "WhatsApp", tint = Color.White, modifier = Modifier.size(20.dp))
                    }

                    // Botón Cerrar selección
                    IconButton(onClick = { selectedDriver = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color(0xFF94A3B8))
                    }
                }
            }
        }

        // ── Botón Flotante para abrir Lista de Conectados ────────────────────
        FloatingActionButton(
            onClick = { showDriversSheet = true },
            containerColor = Color(0xFFF59E0B),
            contentColor = Color(0xFF0F172A),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.FormatListBulleted, contentDescription = "Ver Lista")
        }
    }

    // ── BottomSheet con Lista de Conductores Conectados ──────────────────────
    if (showDriversSheet) {
        ModalBottomSheet(
            onDismissRequest = { showDriversSheet = false },
            containerColor = Color(0xFF1E293B)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Flota en Vivo (${liveDrivers.size})",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Toca un conductor para centrar la cámara del mapa",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (liveDrivers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 30.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No hay conductores activos en los últimos 3 minutos", color = Color(0xFF94A3B8), fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(liveDrivers, key = { it.uid }) { driver ->
                            val isAvail = driver.isAvailable
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedDriver = driver
                                        showDriversSheet = false
                                        mapViewInstance?.controller?.animateTo(GeoPoint(driver.lat, driver.lon))
                                        mapViewInstance?.controller?.setZoom(16.0)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = driver.nombre.ifBlank { "TucTuc" },
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "${driver.displayIdentifier} • ${driver.telefono}",
                                            fontSize = 12.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isAvail) Color(0x2210B981) else Color(0x22F59E0B)
                                    ) {
                                        Text(
                                            text = if (isAvail) "Disponible" else "En Viaje",
                                            color = if (isAvail) Color(0xFF10B981) else Color(0xFFF59E0B),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

private fun openWhatsAppDirect(context: Context, phoneRaw: String, name: String) {
    try {
        val phone = phoneRaw.replace(Regex("[^0-9]"), "")
        val formattedPhone = if (phone.startsWith("502")) phone else "502$phone"
        val msg = "Hola $name, te contactamos de la administración de TucTuc Map."
        val encoded = URLEncoder.encode(msg, "UTF-8")
        val uri = Uri.parse("https://wa.me/$formattedPhone?text=$encoded")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "No se pudo abrir WhatsApp", Toast.LENGTH_SHORT).show()
    }
}

