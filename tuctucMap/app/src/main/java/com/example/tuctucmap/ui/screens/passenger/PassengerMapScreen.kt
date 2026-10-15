package com.example.tuctucmap.ui.screens.passenger

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ElectricRickshaw
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import com.example.tuctucmap.ui.components.PassengerDrawerItem
import com.example.tuctucmap.ui.components.PassengerDrawerSheet
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.tuctucmap.data.model.DriverLocation
import com.example.tuctucmap.data.repository.SupabaseRepository
import com.example.tuctucmap.ui.util.AppTileSource
import com.example.tuctucmap.ui.util.PassengerLocationOverlay
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import kotlinx.coroutines.launch
import android.animation.ValueAnimator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.AccelerateInterpolator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassengerMapScreen(
    repository: SupabaseRepository,
    onBack: (() -> Unit)? = null,
    onNavigateToPassengerLogin: () -> Unit = {},
    onNavigateToPassengerRegister: () -> Unit = {},
    onNavigateToDriverLogin: () -> Unit = {},
    onNavigateToDriverRegister: () -> Unit = {},
    onNavigateToPassengerProfile: () -> Unit = {},
    onNavigateToPassengerHistory: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    val rawDriverLocations by repository.observeActiveLocations().collectAsState(initial = emptyList())

    // Obtener los iconos de Tuc Tuc (activo y en pausa) con caché optimizada en memoria
    val tucTucIcon = remember(context) {
        com.example.tuctucmap.ui.util.MarkerIconUtils.getTucTucIcon(context)
    }
    val pausedTucTucIcon = remember(context) {
        com.example.tuctucmap.ui.util.MarkerIconUtils.getPausedTucTucIcon(context)
    }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        hasLocationPermission = fineGranted || coarseGranted
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var selectedDriver by remember { mutableStateOf<DriverLocation?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var showBroadcastDialog by remember { mutableStateOf(false) }

    var tripMessage by remember { mutableStateOf("") }
    var isRequestingTrip by remember { mutableStateOf(false) }
    var activeTripId by remember { mutableStateOf<String?>(null) }
    var activeTrip by remember { mutableStateOf<SupabaseRepository.TripInfo?>(null) }

    val sharedPrefs = remember(context) { context.getSharedPreferences("tuctuc_prefs", Context.MODE_PRIVATE) }
    var guestName by remember { mutableStateOf(sharedPrefs.getString("guest_passenger_name", "") ?: "") }
    var guestPhone by remember { mutableStateOf(sharedPrefs.getString("guest_passenger_phone", "") ?: "") }

    val sessionStatus by repository.sessionStatus.collectAsState(
        initial = repository.currentSessionStatus
    )
    val isUserLoggedIn = (sessionStatus is io.github.jan.supabase.auth.status.SessionStatus.Authenticated) || repository.isUserLoggedIn
    val currentUid = (sessionStatus as? io.github.jan.supabase.auth.status.SessionStatus.Authenticated)?.session?.user?.id
        ?: repository.currentUserUid
        ?: ""

    var passengerProfile by remember { mutableStateOf<com.example.tuctucmap.data.model.Passenger?>(null) }
    LaunchedEffect(currentUid) {
        if (currentUid.isNotBlank()) {
            repository.observePassengerProfile(currentUid).collect { p ->
                passengerProfile = p
            }
        } else {
            passengerProfile = null
        }
    }

    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }
    var myLocationOverlayInstance by remember { mutableStateOf<MyLocationNewOverlay?>(null) }
    val driverMarkers = remember { mutableMapOf<String, Marker>() }
    val coroutineScope = rememberCoroutineScope()

    // Observe active trip status
    LaunchedEffect(activeTripId) {
        activeTripId?.let { tid ->
            repository.observeTrip(tid).collect { trip ->
                if (trip == null) {
                    activeTrip = null
                    activeTripId = null
                } else {
                    activeTrip = trip
                }
            }
        }
    }

    // Ubicación actual del pasajero
    val myLocGeo = myLocationOverlayInstance?.myLocation

    // Separar conductores disponibles vs en pausa/ocupados
    val availableDrivers = remember(rawDriverLocations) {
        rawDriverLocations.filter { it.isAvailable }
    }
    val pausedDrivers = remember(rawDriverLocations) {
        rawDriverLocations.filter { !it.isAvailable }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = activeTrip == null,
        drawerContent = {
            PassengerDrawerSheet(
                passenger = passengerProfile,
                currentEmail = repository.currentUserEmail ?: "",
                selectedItem = PassengerDrawerItem.MAP,
                isGuest = !isUserLoggedIn,
                onSelectItem = { item ->
                    coroutineScope.launch { drawerState.close() }
                    when (item) {
                        PassengerDrawerItem.HOME, PassengerDrawerItem.MAP -> { /* Ya en mapa */ }
                        PassengerDrawerItem.HISTORY -> onNavigateToPassengerHistory()
                        PassengerDrawerItem.PROFILE -> onNavigateToPassengerProfile()
                    }
                },
                onNavigateToLogin = {
                    coroutineScope.launch { drawerState.close() }
                    onNavigateToPassengerLogin()
                },
                onNavigateToDriver = {
                    coroutineScope.launch { drawerState.close() }
                    onNavigateToDriverLogin()
                },
                onLogout = {
                    coroutineScope.launch {
                        drawerState.close()
                        passengerProfile = null
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
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🛺 TucTuc Map", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                            }
                            val statusSubtitle = when {
                                rawDriverLocations.isEmpty() -> "Buscando toritos..."
                                pausedDrivers.isEmpty() -> "${availableDrivers.size} torito${if (availableDrivers.size != 1) "s" else ""} disponible${if (availableDrivers.size != 1) "s" else ""}"
                                availableDrivers.isEmpty() -> "0 disponibles (${pausedDrivers.size} en pausa)"
                                else -> "${availableDrivers.size} disponible${if (availableDrivers.size != 1) "s" else ""} • ${pausedDrivers.size} en pausa"
                            }
                            Text(
                                text = statusSubtitle,
                                color = if (availableDrivers.isNotEmpty()) Color(0xFFF9D423) else Color(0xFFFFB74D),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Volver",
                                    tint = Color.White
                                )
                            }
                        } else {
                            IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Menú lateral",
                                    tint = Color.White
                                )
                            }
                        }
                    },
                    actions = {
                        if (isUserLoggedIn) {
                            IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFFF9D423),
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        val initial = passengerProfile?.nombre?.firstOrNull()?.uppercaseChar()?.toString()
                                            ?: repository.currentUserEmail?.firstOrNull()?.uppercaseChar()?.toString()
                                            ?: "P"
                                        Text(
                                            text = initial,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0F2027),
                                            fontSize = 16.sp
                                        )
                                    }
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF0F2027)
                    )
                )
            },
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            if (hasLocationPermission && activeTrip == null) {
                ExtendedFloatingActionButton(
                    onClick = { showBroadcastDialog = true },
                    containerColor = Color(0xFFF9D423),
                    contentColor = Color(0xFF0F2027),
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.ElectricRickshaw,
                            contentDescription = null,
                            tint = Color(0xFF0F2027),
                            modifier = Modifier.size(28.dp)
                        )
                    },
                    text = {
                        Text(
                            "PEDIR TORITO CERCANO",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF2C5364))
        ) {
            if (hasLocationPermission) {
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(AppTileSource)
                            setMultiTouchControls(true)
                            controller.setZoom(16.0)
                            val startPoint = GeoPoint(14.6349, -90.5069)
                            controller.setCenter(startPoint)

                            val myLocationOverlay = PassengerLocationOverlay(GpsMyLocationProvider(ctx), this, ctx).apply {
                                enableMyLocation()
                                runOnFirstFix {
                                    val myLoc = myLocation
                                    if (myLoc != null) {
                                        post {
                                            controller.animateTo(myLoc)
                                        }
                                    }
                                }
                            }
                            overlays.add(myLocationOverlay)
                            myLocationOverlayInstance = myLocationOverlay
                            mapViewInstance = this
                        }
                    },
                    update = { mapView ->
                        val currentUids = rawDriverLocations.map { it.uid }.toSet()

                        // Remover marcadores de conductores desconectados
                        val removedUids = driverMarkers.keys.filter { it !in currentUids }
                        for (uid in removedUids) {
                            val marker = driverMarkers.remove(uid)
                            if (marker != null) {
                                val startLat = marker.position.latitude
                                val exitAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
                                    duration = 500L
                                    interpolator = AccelerateInterpolator()
                                    addUpdateListener { animation ->
                                        val fraction = animation.animatedValue as Float
                                        marker.setAlpha(fraction)
                                        val currentLat = startLat + 0.002 * (1f - fraction)
                                        marker.position = GeoPoint(currentLat, marker.position.longitude)
                                        mapView.invalidate()
                                    }
                                    addListener(object : AnimatorListenerAdapter() {
                                        override fun onAnimationEnd(animation: Animator) {
                                            mapView.overlays.remove(marker)
                                            mapView.invalidate()
                                        }
                                    })
                                }
                                exitAnimator.start()
                            }
                        }

                        // Actualizar o crear marcadores para conductores en línea
                        rawDriverLocations.forEach { driverLoc ->
                            val newGeoPoint = GeoPoint(driverLoc.latitud, driverLoc.longitud)
                            var marker = driverMarkers[driverLoc.uid]
                            val isAvail = driverLoc.isAvailable
                            val targetIcon = if (isAvail) tucTucIcon else pausedTucTucIcon
                            val targetAlpha = if (isAvail) 1.0f else 0.65f
                            val statusTag = if (isAvail) "🟢 DISPONIBLE" else "🟠 EN PAUSA"

                            if (marker == null) {
                                val startLat = newGeoPoint.latitude + 0.002
                                marker = Marker(mapView).apply {
                                    position = GeoPoint(startLat, newGeoPoint.longitude)
                                    setAlpha(0f)
                                    title = driverLoc.nombre
                                    snippet = "Placas: ${driverLoc.placas} • $statusTag"
                                    icon = targetIcon
                                    setOnMarkerClickListener { _, _ ->
                                        selectedDriver = driverLoc
                                        showBottomSheet = true
                                        true
                                    }
                                }
                                driverMarkers[driverLoc.uid] = marker
                                mapView.overlays.add(marker)

                                val entranceAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                                    duration = 1000L
                                    interpolator = OvershootInterpolator()
                                    addUpdateListener { animation ->
                                        val fraction = animation.animatedValue as Float
                                        val currentLat = startLat + (newGeoPoint.latitude - startLat) * fraction
                                        marker.position = GeoPoint(currentLat, newGeoPoint.longitude)
                                        marker.setAlpha((fraction * targetAlpha).coerceIn(0f, targetAlpha))
                                        mapView.invalidate()
                                    }
                                }
                                entranceAnimator.start()
                            } else {
                                val oldGeoPoint = marker.position
                                if (oldGeoPoint.latitude != newGeoPoint.latitude || oldGeoPoint.longitude != newGeoPoint.longitude) {
                                    val animator = ValueAnimator.ofFloat(0f, 1f)
                                    animator.duration = 4000L
                                    animator.interpolator = LinearInterpolator()
                                    animator.addUpdateListener { animation ->
                                        val fraction = animation.animatedFraction
                                        val lat = oldGeoPoint.latitude + (newGeoPoint.latitude - oldGeoPoint.latitude) * fraction
                                        val lon = oldGeoPoint.longitude + (newGeoPoint.longitude - oldGeoPoint.longitude) * fraction
                                        marker?.position = GeoPoint(lat, lon)
                                        mapView.invalidate()
                                    }
                                    animator.start()
                                }
                                marker.title = driverLoc.nombre
                                marker.snippet = "Placas: ${driverLoc.placas} • $statusTag"
                                marker.icon = targetIcon
                                marker.setAlpha(targetAlpha)
                            }
                        }
                        mapView.invalidate()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Permiso de Ubicación Requerido",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Necesitamos acceso a tu ubicación para centrar el mapa y mostrar los TucTucs cercanos.",
                        fontSize = 14.sp,
                        color = Color.LightGray,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF9D423),
                            contentColor = Color(0xFF0F2027)
                        )
                    ) {
                        Text("Conceder Permisos", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ══════════════════════════════════════════════════════════════════
            // Chip de Estado Vacío: Sin toritos activos en el mapa (Fase 1 - Acción 3)
            // ══════════════════════════════════════════════════════════════════
            if (rawDriverLocations.isEmpty() && hasLocationPermission) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(
                            top = 12.dp,
                            start = 16.dp,
                            end = 76.dp
                        ),
                    colors = CardDefaults.cardColors(containerColor = Color(0xEE0F172A)),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color(0x66F9D423)),
                    elevation = CardDefaults.cardElevation(6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📡", fontSize = 17.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Sin toritos activos en este momento",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Los conductores se conectan continuamente. Puedes esperar unos instantes.",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // ══════════════════════════════════════════════════════════════════
            // Controles Flotantes del Mapa (Estándar Google Maps / Waze: Superior Derecho)
            // ══════════════════════════════════════════════════════════════════
            if (hasLocationPermission) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(
                            top = 16.dp,
                            end = 16.dp
                        ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    // Botón para centrar en mi ubicación
                    FloatingActionButton(
                        onClick = {
                            myLocationOverlayInstance?.let { overlay ->
                                val myLoc = overlay.myLocation
                                if (myLoc != null) {
                                    mapViewInstance?.controller?.animateTo(myLoc)
                                }
                            }
                        },
                        containerColor = Color(0xEE0F2027),
                        contentColor = Color(0xFFF9D423),
                        shape = CircleShape,
                        modifier = Modifier.size(48.dp),
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "Mi ubicación",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // ══════════════════════════════════════════════════════════════════════
            // Bottom Sheet: Detalle de Conductor Seleccionado (Marcador)
            // ══════════════════════════════════════════════════════════════════════
            if (showBottomSheet && selectedDriver != null) {
                // Sincronizar reactivamente con el estado en tiempo real de Firebase
                val driver = rawDriverLocations.find { it.uid == selectedDriver?.uid } ?: selectedDriver!!
                val isDriverAvail = driver.isAvailable

                ModalBottomSheet(
                    onDismissRequest = {
                        showBottomSheet = false
                        selectedDriver = null
                    },
                    containerColor = Color(0xFF203A43),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = driver.nombre,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Placas: ${driver.placas}",
                                        fontSize = 15.sp,
                                        color = Color(0xFFF9D423),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    // Badge de distancia y ETA si está disponible
                                    if (myLocGeo != null) {
                                        val dist = driver.formattedDistance(myLocGeo.latitude, myLocGeo.longitude)
                                        val eta = driver.estimatedArrivalMinutes(myLocGeo.latitude, myLocGeo.longitude)
                                        Surface(
                                            color = Color(0x334CAF50),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "$dist • ~$eta min",
                                                color = Color(0xFF81C784),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Badge distintivo de Disponibilidad vs Pausa
                                Surface(
                                    color = if (isDriverAvail) Color(0x334CAF50) else Color(0x33EF6C00),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, if (isDriverAvail) Color(0xFF4CAF50) else Color(0xFFEF6C00))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(if (isDriverAvail) Color(0xFF4ADE80) else Color(0xFFFFB74D))
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isDriverAvail) "🟢 DISPONIBLE" else "🟠 EN PAUSA / OCUPADO",
                                            color = if (isDriverAvail) Color(0xFF81C784) else Color(0xFFFFB74D),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "🛺",
                                fontSize = 38.sp
                            )
                        }

                        // Banner de alerta informativa si el conductor está pausado
                        if (!isDriverAvail) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0x33EF6C00)),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0x66EF6C00))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("⏸️", fontSize = 18.sp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Conductor en pausa temporal",
                                            color = Color(0xFFFFB74D),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Este torito pausó la recepción de solicitudes o está en viaje. Puedes llamarlo directamente o solicitar otro torito disponible.",
                                            color = Color(0xFFCBD5E1),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Contacta al conductor directamente:",
                            fontSize = 13.sp,
                            color = Color.LightGray,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Botón de llamada estándar
                            Button(
                                onClick = {
                                    val dialIntent = Intent(
                                        Intent.ACTION_DIAL,
                                        Uri.parse("tel:${driver.telefono}")
                                    )
                                    context.startActivity(dialIntent)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF4CAF50),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(imageVector = Icons.Default.Call, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Llamar", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }

                            // Botón de WhatsApp con ENLACE GPS INTELIGENTE
                            Button(
                                onClick = {
                                    var formattedPhone = driver.telefono.replace(" ", "").replace("-", "")
                                    if (formattedPhone.length == 8) {
                                        formattedPhone = "502$formattedPhone"
                                    }
                                    val mapsLink = if (myLocGeo != null) {
                                        "https://maps.google.com/?q=${myLocGeo.latitude},${myLocGeo.longitude}"
                                    } else {
                                        "mi ubicación actual"
                                    }
                                    val message = "¡Hola! Vi tu Torito en TucTucMap (Placas: ${driver.placas}). ¿Estás disponible? Te espero aquí: $mapsLink"
                                    val waUrl = "https://api.whatsapp.com/send?phone=$formattedPhone&text=${Uri.encode(message)}"
                                    val waIntent = Intent(Intent.ACTION_VIEW, Uri.parse(waUrl))
                                    context.startActivity(waIntent)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF25D366),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(imageVector = Icons.Default.Chat, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("WhatsApp", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = {
                                showBottomSheet = false
                                showBroadcastDialog = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDriverAvail) Color(0xFFF9D423) else Color(0xFF38BDF8),
                                contentColor = Color(0xFF0F2027)
                            )
                        ) {
                            Icon(Icons.Default.ElectricRickshaw, contentDescription = null, tint = Color(0xFF0F2027))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isDriverAvail) "Solicitar Torito Cercano a Todos" else "Buscar otro Torito Disponible",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }

            // ══════════════════════════════════════════════════════════════════════
            // Bottom Sheet: Solicitar Torito a Todos los Conductores Cercanos
            // ══════════════════════════════════════════════════════════════════════
            if (showBroadcastDialog) {
                val broadcastScrollState = rememberScrollState()
                ModalBottomSheet(
                    onDismissRequest = { showBroadcastDialog = false },
                    containerColor = Color(0xFF0F172A),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .imePadding()
                            .verticalScroll(broadcastScrollState)
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🛺", fontSize = 32.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Pedir Torito Cercano",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = if (availableDrivers.isNotEmpty())
                                            "${availableDrivers.size} torito${if (availableDrivers.size != 1) "s" else ""} disponible${if (availableDrivers.size != 1) "s" else ""} en la zona"
                                        else if (pausedDrivers.isNotEmpty())
                                            "Todos los toritos (${pausedDrivers.size}) están en pausa u ocupados"
                                        else "Sin toritos activos en este momento",
                                        fontSize = 13.sp,
                                        color = if (availableDrivers.isNotEmpty()) Color(0xFFF9D423) else Color(0xFFFFB74D)
                                    )
                                }
                            }
                            IconButton(onClick = { showBroadcastDialog = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.LightGray)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Guest Checkout: Campos de Nombre y Teléfono si no ha iniciado sesión
                        if (!repository.isUserLoggedIn) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color(0x33F9D423))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("👤", fontSize = 16.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Datos de contacto para el conductor",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color.White
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "El conductor te llamará a este número al llegar a recogerte.",
                                        fontSize = 12.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))

                                    OutlinedTextField(
                                        value = guestName,
                                        onValueChange = { guestName = it },
                                        label = { Text("Tu Nombre", color = Color.LightGray) },
                                        placeholder = { Text("Ej: Juan Pérez", color = Color.Gray) },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFFF9D423),
                                            unfocusedBorderColor = Color.Gray,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        ),
                                        singleLine = true
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    OutlinedTextField(
                                        value = guestPhone,
                                        onValueChange = { guestPhone = it },
                                        label = { Text("Tu Teléfono", color = Color.LightGray) },
                                        placeholder = { Text("Ej: 5555-1234", color = Color.Gray) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFFF9D423),
                                            unfocusedBorderColor = Color.Gray,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        ),
                                        singleLine = true
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(
                                            onClick = {
                                                showBroadcastDialog = false
                                                onNavigateToPassengerLogin()
                                            },
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "¿Tienes cuenta? Inicia sesión aquí",
                                                color = Color(0xFFF9D423),
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        } else {
                            // Usuario autenticado: Resumen rápido de quién pide
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF1E293B),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFFF9D423), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Pasajero: ${passengerProfile?.nombre ?: "Mi Cuenta"} • Tel: ${passengerProfile?.telefono ?: "Sin número"}",
                                        color = Color(0xFFE2E8F0),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        // Rumbo y Referencia (Unificación Recogida + Rumbo para el Conductor)
                        Text(
                            text = "¿Hacia dónde vas o referencia de recogida?",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFF9D423)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "El conductor escuchará esto por voz para ubicarte de inmediato.",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        val quickChips = listOf(
                            "📍 Al Mercado Municipal",
                            "📍 Al Parque Central",
                            "📍 A la Terminal",
                            "📍 Al Hospital / Salud",
                            "📍 Al Cementerio"
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            quickChips.take(2).forEach { chip ->
                                SuggestionChip(
                                    onClick = {
                                        val cleanChip = chip.replace("📍 ", "").trim()
                                        tripMessage = if (tripMessage.isBlank() || tripMessage.startsWith("Esperando aquí, voy rumbo")) {
                                            "Esperando aquí, voy rumbo a $cleanChip"
                                        } else {
                                            "${tripMessage.trim()}, voy rumbo a $cleanChip"
                                        }
                                    },
                                    label = { Text(chip, fontSize = 11.sp, color = Color.White) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Color(0xFF334155))
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            quickChips.drop(2).forEach { chip ->
                                SuggestionChip(
                                    onClick = {
                                        val cleanChip = chip.replace("📍 ", "").trim()
                                        tripMessage = if (tripMessage.isBlank() || tripMessage.startsWith("Esperando aquí, voy rumbo")) {
                                            "Esperando aquí, voy rumbo a $cleanChip"
                                        } else {
                                            "${tripMessage.trim()}, voy rumbo a $cleanChip"
                                        }
                                    },
                                    label = { Text(chip, fontSize = 11.sp, color = Color.White) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Color(0xFF334155))
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = tripMessage,
                            onValueChange = { tripMessage = it },
                            label = { Text("Nota de recogida y rumbo", color = Color.LightGray) },
                            placeholder = { Text("Ej: En Farmacia Galeno, voy al Cementerio", color = Color.Gray) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFF9D423),
                                unfocusedBorderColor = Color.Gray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        val effectivePassengerName = if (repository.isUserLoggedIn) {
                            passengerProfile?.nombre ?: "Pasajero"
                        } else {
                            guestName.trim()
                        }
                        val effectivePassengerPhone = if (repository.isUserLoggedIn) {
                            passengerProfile?.telefono ?: ""
                        } else {
                            guestPhone.trim()
                        }

                        val hasAvailableDrivers = availableDrivers.isNotEmpty()
                        val canRequest = if (repository.isUserLoggedIn) {
                            !isRequestingTrip && myLocGeo != null && hasAvailableDrivers
                        } else {
                            !isRequestingTrip && myLocGeo != null && guestName.isNotBlank() && guestPhone.isNotBlank() && hasAvailableDrivers
                        }

                        // Alerta si todos los toritos están en pausa
                        if (availableDrivers.isEmpty() && pausedDrivers.isNotEmpty()) {
                            Surface(
                                color = Color(0x33EF6C00),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0x66EF6C00)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("⏸️", fontSize = 16.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Todos los conductores están en pausa u ocupados. Puedes llamarlos directamente desde el mapa o esperar a que se liberen.",
                                        color = Color(0xFFFFB74D),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                val myLoc = myLocationOverlayInstance?.myLocation
                                if (myLoc != null) {
                                    isRequestingTrip = true
                                    if (!isUserLoggedIn) {
                                        sharedPrefs.edit()
                                            .putString("guest_passenger_name", guestName.trim())
                                            .putString("guest_passenger_phone", guestPhone.trim())
                                            .apply()
                                    }
                                    coroutineScope.launch {
                                        val result = repository.requestBroadcastTrip(
                                            lat = myLoc.latitude,
                                            lon = myLoc.longitude,
                                            message = tripMessage.ifBlank { "Pasajero esperando torito" },
                                            passengerName = effectivePassengerName.ifBlank { "Pasajero" },
                                            passengerPhone = effectivePassengerPhone
                                        )
                                        isRequestingTrip = false
                                        result.onSuccess { tripId ->
                                            activeTripId = tripId
                                            tripMessage = ""
                                            showBroadcastDialog = false
                                            coroutineScope.launch {
                                                kotlinx.coroutines.delay(60000)
                                                if (activeTripId == tripId && activeTrip?.status == "requested") {
                                                    repository.updateTripStatus(tripId, "timeout")
                                                    kotlinx.coroutines.delay(10000)
                                                    repository.deleteTrip(tripId)
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(12.dp),
                            enabled = canRequest,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (hasAvailableDrivers) Color(0xFFF9D423) else Color(0xFF64748B),
                                contentColor = Color(0xFF0F2027)
                            )
                        ) {
                            if (isRequestingTrip) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color(0xFF0F2027))
                            } else {
                                Icon(Icons.Default.ElectricRickshaw, contentDescription = null, tint = Color(0xFF0F2027))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = when {
                                        !hasAvailableDrivers && pausedDrivers.isNotEmpty() -> "No hay toritos disponibles (en pausa)"
                                        !hasAvailableDrivers -> "Sin toritos activos en la red"
                                        !repository.isUserLoggedIn && (guestName.isBlank() || guestPhone.isBlank()) -> "Ingresa tu nombre y teléfono"
                                        else -> "Pedir Torito Cercano"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }

            // ══════════════════════════════════════════════════════════════════════
            // Overlay Flotante de Estado de Viaje Activo
            // ══════════════════════════════════════════════════════════════════════
            if (activeTrip != null) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(10.dp),
                    border = BorderStroke(
                        1.5.dp,
                        if (activeTrip!!.status in listOf("accepted", "completed")) Color(0xFF4CAF50) else Color(0xFFF9D423)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        when (activeTrip!!.status) {
                            "requested" -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = Color(0xFFF9D423),
                                        strokeWidth = 3.dp
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Buscando Toritos Cercanos...",
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 16.sp
                                        )
                                        Text(
                                            text = "Notificando a ${rawDriverLocations.size} toritos. El primero en aceptar vendrá hacia ti.",
                                            color = Color.LightGray,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                                if (activeTrip!!.message.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Referencia: \"${activeTrip!!.message}\"",
                                        color = Color(0xFFF9D423),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                OutlinedButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            repository.deleteTrip(activeTripId!!)
                                            activeTripId = null
                                            activeTrip = null
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252))
                                ) {
                                    Text("Cancelar Solicitud")
                                }
                            }
                            "accepted" -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "🎉 ¡Torito en Camino!",
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF4CAF50),
                                            fontSize = 18.sp
                                        )
                                        Text(
                                            text = activeTrip!!.driverName.ifBlank { "Conductor Confirmado" },
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Placas: ${activeTrip!!.driverPlate.ifBlank { "..." }}",
                                            color = Color(0xFFF9D423),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Text(text = "🛺", fontSize = 38.sp)
                                }

                                val assignedDriverLoc = rawDriverLocations.find { it.uid == activeTrip!!.driverId }
                                if (assignedDriverLoc != null && myLocGeo != null) {
                                    val dist = assignedDriverLoc.formattedDistance(myLocGeo.latitude, myLocGeo.longitude)
                                    val eta = assignedDriverLoc.estimatedArrivalMinutes(myLocGeo.latitude, myLocGeo.longitude)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Surface(
                                        color = Color(0x334CAF50),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "Distancia: $dist • Llegada en ~$eta min",
                                            color = Color(0xFF81C784),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${activeTrip!!.driverPhone}"))
                                            context.startActivity(dialIntent)
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                                    ) {
                                        Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Llamar", fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = {
                                            var formattedPhone = activeTrip!!.driverPhone.replace(" ", "").replace("-", "")
                                            if (formattedPhone.length == 8) formattedPhone = "502$formattedPhone"
                                            val mapsLink = if (myLocGeo != null) "https://maps.google.com/?q=${myLocGeo.latitude},${myLocGeo.longitude}" else "mi ubicación"
                                            val msg = "¡Hola! Vi que aceptaste mi viaje en TucTucMap. Te espero aquí: $mapsLink"
                                            val waUrl = "https://api.whatsapp.com/send?phone=$formattedPhone&text=${Uri.encode(msg)}"
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(waUrl)))
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                                    ) {
                                        Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("WhatsApp", fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            val current = activeTrip
                                            val tid = activeTripId
                                            activeTripId = null
                                            activeTrip = null
                                            if (current != null) {
                                                repository.saveTripRecord(current, "completed")
                                            }
                                            if (tid != null) {
                                                repository.deleteTrip(tid)
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                                ) {
                                    Text("Finalizar / Cerrar Viaje")
                                }
                            }
                            "completed" -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .background(Color(0x334ADE80)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = "✅", fontSize = 24.sp)
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "¡Viaje Finalizado!",
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF4ADE80),
                                            fontSize = 17.sp
                                        )
                                        Text(
                                            text = "¡Has llegado a tu destino! Esperamos que hayas tenido un excelente viaje.",
                                            color = Color.White,
                                            fontSize = 13.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            val tid = activeTripId
                                            activeTripId = null
                                            activeTrip = null
                                            if (tid != null) {
                                                repository.deleteTrip(tid)
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFF9D423),
                                        contentColor = Color(0xFF0F2027)
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Listo / Pedir Otro Torito",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                            "cancelled", "timeout" -> {
                                Text(
                                    text = if (activeTrip!!.status == "timeout") "Ningún torito disponible respondió a tiempo." else "El viaje no pudo ser completado.",
                                    color = Color(0xFFFF5252),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Puedes intentar pedir nuevamente o llamar directamente a un torito desde el mapa.",
                                    color = Color.LightGray,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            val tid = activeTripId
                                            activeTripId = null
                                            activeTrip = null
                                            if (tid != null) {
                                                repository.deleteTrip(tid)
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF9D423), contentColor = Color(0xFF0F2027))
                                ) {
                                    Text("Aceptar", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}
