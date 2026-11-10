package com.example.tuctucmap.ui.screens.passenger

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Route
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.tuctucmap.data.PassengerDestinationPreferences
import com.example.tuctucmap.data.PassengerLocationPreferences
import com.example.tuctucmap.data.model.DriverLocation
import com.example.tuctucmap.data.model.Driver
import com.example.tuctucmap.data.model.ServerClock
import com.example.tuctucmap.data.repository.SupabaseRepository
import com.example.tuctucmap.data.service.OsrmRoutingService
import com.example.tuctucmap.ui.components.DriverAvatar
import com.example.tuctucmap.ui.components.Vehicle360Viewer
import com.example.tuctucmap.ui.util.AppTileSource
import com.example.tuctucmap.ui.util.PassengerLocationOverlay
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.animation.ValueAnimator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.AccelerateInterpolator
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

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
    onNavigateToSettings: () -> Unit = {},
    onLogout: () -> Unit = {},
    onNavigateToAdmin: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    var rawDriverLocations by remember { mutableStateOf<List<DriverLocation>>(emptyList()) }
    LaunchedEffect(repository, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            repository.observeActiveLocations().collect { locations ->
                rawDriverLocations = locations
            }
        }
    }

    // Obtener los iconos de Tuc Tuc (activo, en pausa y asignado a mi viaje) con caché optimizada en memoria
    val tucTucIcon = remember(context) {
        com.example.tuctucmap.ui.util.MarkerIconUtils.getTucTucIcon(context)
    }
    val pausedTucTucIcon = remember(context) {
        com.example.tuctucmap.ui.util.MarkerIconUtils.getPausedTucTucIcon(context)
    }
    val assignedTucTucIcon = remember(context) {
        com.example.tuctucmap.ui.util.MarkerIconUtils.getAssignedDriverTucTucIcon(context)
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
        repository.getAppConfig()
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
    var showBroadcastDialog by remember { mutableStateOf(false) }

    var tripMessage by remember { mutableStateOf("") }
    var isRequestingTrip by remember { mutableStateOf(false) }
    var isCancellingTrip by remember { mutableStateOf(false) }
    var isFinishingCompletedTrip by remember { mutableStateOf(false) }
    var isClearingCancelledTrip by remember { mutableStateOf(false) }

    val sharedPrefs = remember(context) { context.getSharedPreferences("tuctuc_prefs", Context.MODE_PRIVATE) }
    var activeTripId by remember { mutableStateOf(sharedPrefs.getString("active_passenger_trip_id", null)) }
    var activeTrip by remember { mutableStateOf<SupabaseRepository.TripInfo?>(null) }

    var guestName by remember { mutableStateOf(sharedPrefs.getString("guest_passenger_name", "") ?: "") }
    var guestPhone by remember { mutableStateOf(sharedPrefs.getString("guest_passenger_phone", "") ?: "") }

    var recentDestinations by remember {
        mutableStateOf(PassengerDestinationPreferences.getRecentDestinations(context))
    }

    LaunchedEffect(showBroadcastDialog) {
        if (showBroadcastDialog) {
            recentDestinations = PassengerDestinationPreferences.getRecentDestinations(context)
        }
    }

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
    val driverAnimators = remember { mutableMapOf<String, ValueAnimator>() }
    var lastMapRenderSignature by remember { mutableStateOf<Any?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Cerrar el drawer lateral al presionar Atrás en lugar de salir de la aplicación (MED-01)
    BackHandler(enabled = drawerState.isOpen) {
        coroutineScope.launch { drawerState.close() }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    mapViewInstance?.onResume()
                    if (hasLocationPermission) {
                        myLocationOverlayInstance?.enableMyLocation()
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    myLocationOverlayInstance?.disableMyLocation()
                    mapViewInstance?.onPause()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            driverAnimators.values.forEach { it.cancel() }
            driverAnimators.clear()
            driverMarkers.clear()
            myLocationOverlayInstance?.disableMyLocation()
            mapViewInstance?.onPause()
            mapViewInstance?.onDetach()
        }
    }

    // ── Estado de la ruta OSRM y polilíneas de alto contraste en tiempo real ──
    var passengerRouteSummary by remember { mutableStateOf<OsrmRoutingService.RouteResult?>(null) }
    var routePointsToPassenger by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
    var routeCasingPolyline by remember { mutableStateOf<Polyline?>(null) }
    var routePolyline by remember { mutableStateOf<Polyline?>(null) }
    var hasZoomedToTripRoute by remember { mutableStateOf(false) }
    var isTrackingCardExpanded by remember { mutableStateOf(false) }

    // Observe active trip status
    LaunchedEffect(activeTripId) {
        activeTripId?.let { tid ->
            repository.observeTrip(tid).collect { trip ->
                val now = ServerClock.nowMillis()
                val isExpiredRequested = trip != null && trip.status == "requested" && (now - trip.timestamp > 60_000L)
                if (trip == null || trip.status in listOf("completed", "cancelled", "timeout") || isExpiredRequested) {
                    if (isExpiredRequested) {
                        try {
                            repository.updateTripStatus(tid, "timeout")
                            repository.deleteTrip(tid)
                        } catch (_: Exception) {}
                    }
                    if (trip?.status == "completed") {
                        com.example.tuctucmap.ui.util.InAppReviewHelper.recordSuccessfulTripAndRequestReview(context)
                    }
                    activeTrip = null
                    activeTripId = null
                    sharedPrefs.edit().remove("active_passenger_trip_id").apply()
                } else {
                    activeTrip = trip
                    sharedPrefs.edit().putString("active_passenger_trip_id", tid).apply()
                }
            }
        }
    }

    // Cálculo reactivo optimizado de ruta hacia el pasajero:
    // Throttle inteligente (30s) y fallback de proyección matemática local para no saturar OSRM
    val assignedDriverLocation = remember(rawDriverLocations, activeTrip?.driverId) {
        if (activeTrip != null && activeTrip?.status == "accepted" && activeTrip?.driverId?.isNotBlank() == true) {
            rawDriverLocations.find { it.uid == activeTrip?.driverId }
        } else null
    }

    // Perfil completo del conductor asignado (foto de rostro y fotos 360 del vehículo)
    var assignedDriverProfile by remember { mutableStateOf<Driver?>(null) }
    var showVehicle360Dialog by remember { mutableStateOf(false) }

    LaunchedEffect(activeTrip?.driverId, activeTrip?.status) {
        val dId = activeTrip?.driverId
        if (activeTrip?.status == "accepted" && !dId.isNullOrBlank()) {
            assignedDriverProfile = repository.fetchDriverDetails(dId)
        } else {
            assignedDriverProfile = null
            showVehicle360Dialog = false
        }
    }

    var lastPassengerOsrmCalcTime by remember { mutableLongStateOf(0L) }
    var lastObservedDriverTripId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(activeTrip?.driverId, activeTrip?.status, assignedDriverLocation?.latitud, assignedDriverLocation?.longitud) {
        val trip = activeTrip
        val driverLoc = assignedDriverLocation
        if (trip != null && trip.status == "accepted" && driverLoc != null) {
            val passengerLat = if (trip.passengerLat != 0.0) trip.passengerLat else (myLocationOverlayInstance?.myLocation?.latitude ?: 0.0)
            val passengerLon = if (trip.passengerLon != 0.0) trip.passengerLon else (myLocationOverlayInstance?.myLocation?.longitude ?: 0.0)

            if (passengerLat != 0.0 && passengerLon != 0.0 && driverLoc.latitud != 0.0 && driverLoc.longitud != 0.0) {
                val now = System.currentTimeMillis()
                val isNewTrip = trip.tripId != lastObservedDriverTripId
                val shouldQueryOsrm = isNewTrip || (now - lastPassengerOsrmCalcTime >= 30_000L) || routePointsToPassenger.isEmpty()

                if (shouldQueryOsrm) {
                    lastObservedDriverTripId = trip.tripId
                    lastPassengerOsrmCalcTime = now
                    val routeResult = withContext(Dispatchers.IO) {
                        OsrmRoutingService.getRoute(
                            startLat = driverLoc.latitud,
                            startLon = driverLoc.longitud,
                            endLat = passengerLat,
                            endLon = passengerLon
                        )
                    }
                    if (routeResult.isSuccess) {
                        passengerRouteSummary = routeResult
                        routePointsToPassenger = routeResult.points
                    } else {
                        // Proyección matemática local Haversine con factor vial a costo $0
                        val targetLoc = android.location.Location("").apply {
                            latitude = passengerLat
                            longitude = passengerLon
                        }
                        val dLoc = android.location.Location("").apply {
                            latitude = driverLoc.latitud
                            longitude = driverLoc.longitud
                        }
                        val roadMeters = (dLoc.distanceTo(targetLoc) * 1.28).toDouble()
                        val estSeconds = roadMeters / 5.55
                        passengerRouteSummary = OsrmRoutingService.RouteResult(
                            points = routePointsToPassenger,
                            distanceMeters = roadMeters,
                            durationSeconds = estSeconds,
                            isSuccess = true
                        )
                    }
                } else {
                    // Actualización local continua entre ciclos de OSRM (0 llamadas HTTP)
                    val targetLoc = android.location.Location("").apply {
                        latitude = passengerLat
                        longitude = passengerLon
                    }
                    val dLoc = android.location.Location("").apply {
                        latitude = driverLoc.latitud
                        longitude = driverLoc.longitud
                    }
                    val roadMeters = (dLoc.distanceTo(targetLoc) * 1.28).toDouble()
                    val estSeconds = roadMeters / 5.55
                    passengerRouteSummary = OsrmRoutingService.RouteResult(
                        points = routePointsToPassenger,
                        distanceMeters = roadMeters,
                        durationSeconds = estSeconds,
                        isSuccess = true
                    )
                }
            }
        } else {
            passengerRouteSummary = null
            routePointsToPassenger = emptyList()
            hasZoomedToTripRoute = false
            lastObservedDriverTripId = null
            lastPassengerOsrmCalcTime = 0L
        }
    }

    // Encuadre inteligente de cámara con compensación para que la ruta siempre sea visible en la mitad superior
    fun zoomToFitPassengerRoute() {
        val mv = mapViewInstance ?: return
        val points = mutableListOf<GeoPoint>()
        if (routePointsToPassenger.isNotEmpty()) {
            points.addAll(routePointsToPassenger)
        } else {
            assignedDriverLocation?.let { points.add(GeoPoint(it.latitud, it.longitud)) }
            val pLat = activeTrip?.passengerLat ?: 0.0
            val pLon = activeTrip?.passengerLon ?: 0.0
            if (pLat != 0.0 && pLon != 0.0) {
                points.add(GeoPoint(pLat, pLon))
            } else {
                myLocationOverlayInstance?.myLocation?.let { points.add(it) }
            }
        }

        if (points.isNotEmpty()) {
            var north = -90.0
            var south = 90.0
            var east = -180.0
            var west = 180.0
            for (p in points) {
                if (p.latitude > north) north = p.latitude
                if (p.latitude < south) south = p.latitude
                if (p.longitude > east) east = p.longitude
                if (p.longitude < west) west = p.longitude
            }
            val latSpan = (north - south).coerceAtLeast(0.002)
            val lonSpan = (east - west).coerceAtLeast(0.002)

            // Desplazar la vista hacia el sur para que la ruta quede en la zona visible superior libre de tarjetas
            val bottomPaddingFactor = if (isTrackingCardExpanded) 1.2 else 0.65
            val adjustedBbox = BoundingBox(
                north + (latSpan * 0.25),
                east + (lonSpan * 0.25),
                south - (latSpan * bottomPaddingFactor),
                west - (lonSpan * 0.25)
            )
            try {
                mv.zoomToBoundingBox(adjustedBbox, true, 80)
            } catch (_: Exception) {
                points.firstOrNull()?.let {
                    mv.controller.animateTo(it)
                    mv.controller.setZoom(16.0)
                }
            }
        }
    }

    // Auto-encuadre inicial cuando la ruta esté disponible
    LaunchedEffect(routePointsToPassenger) {
        if (routePointsToPassenger.isNotEmpty() && !hasZoomedToTripRoute && mapViewInstance != null) {
            hasZoomedToTripRoute = true
            zoomToFitPassengerRoute()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mapViewInstance?.let { mv ->
                routeCasingPolyline?.let { mv.overlays.remove(it) }
                routePolyline?.let { mv.overlays.remove(it) }
            }
        }
    }

    // Ubicación actual del pasajero (con fallback inmediato a la última ubicación guardada en el dispositivo)
    val myLocGeo = myLocationOverlayInstance?.myLocation ?: remember(context) {
        PassengerLocationPreferences.getLastSavedLocation(context)
    }
    val appConfig by repository.appConfigFlow.collectAsState()
    val maxRadiusMeters = remember(appConfig.maxSearchRadiusKm) { appConfig.maxSearchRadiusKm * 1000.0 }

    // Separar conductores disponibles vs en pausa/ocupados dentro de la geocerca del municipio
    val nearbyDriverLocations = remember(rawDriverLocations, myLocGeo?.latitude, myLocGeo?.longitude) {
        if (myLocGeo == null || (myLocGeo.latitude == 0.0 && myLocGeo.longitude == 0.0)) {
            rawDriverLocations
        } else {
            rawDriverLocations.filter {
                it.distanceMetersTo(myLocGeo.latitude, myLocGeo.longitude) <= maxRadiusMeters
            }
        }
    }

    val availableDrivers = remember(nearbyDriverLocations) {
        nearbyDriverLocations.filter { it.isAvailable }
    }
    val pausedDrivers = remember(nearbyDriverLocations) {
        nearbyDriverLocations.filter { !it.isAvailable }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            val isAdminUser = remember(context) { com.example.tuctucmap.data.SecurePrefs.getUserRole(context) == "admin" }
            PassengerDrawerSheet(
                passenger = passengerProfile,
                currentEmail = repository.currentUserEmail ?: "",
                selectedItem = PassengerDrawerItem.MAP,
                isGuest = !isUserLoggedIn,
                isAdmin = isAdminUser,
                onNavigateToAdmin = {
                    coroutineScope.launch { drawerState.close() }
                    onNavigateToAdmin()
                },
                onSelectItem = { item ->
                    coroutineScope.launch { drawerState.close() }
                    when (item) {
                        PassengerDrawerItem.HOME, PassengerDrawerItem.MAP -> { /* Ya en mapa */ }
                        PassengerDrawerItem.HISTORY -> onNavigateToPassengerHistory()
                        PassengerDrawerItem.PROFILE -> onNavigateToPassengerProfile()
                        PassengerDrawerItem.INFORMATION -> onNavigateToSettings()
                    }
                },
                onShareApp = {
                    coroutineScope.launch { drawerState.close() }
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
                                rawDriverLocations.isEmpty() -> "Buscando tuctucs..."
                                pausedDrivers.isEmpty() -> "${availableDrivers.size} tuctuc${if (availableDrivers.size != 1) "s" else ""} disponible${if (availableDrivers.size != 1) "s" else ""}"
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
                            "PEDIR TUCTUC CERCANO",
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
                            val startPoint = PassengerLocationPreferences.getInitialMapLocation(ctx)
                            controller.setCenter(startPoint)

                            val myLocationOverlay = PassengerLocationOverlay(GpsMyLocationProvider(ctx), this, ctx).apply {
                                enableMyLocation()
                                runOnFirstFix {
                                    val myLoc = myLocation
                                    if (myLoc != null) {
                                        PassengerLocationPreferences.saveLastLocation(ctx, myLoc.latitude, myLoc.longitude)
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
                        // Función auxiliar para garantizar que el marcador del pasajero siempre se dibuje
                        // en la capa superior absoluta (último en overlays), por encima de conductores y rutas.
                        fun bringPassengerOverlayToFront() {
                            val pOverlay = myLocationOverlayInstance ?: mapView.overlays.firstOrNull { it is PassengerLocationOverlay }
                            if (pOverlay != null && mapView.overlays.contains(pOverlay)) {
                                val lastIndex = mapView.overlays.size - 1
                                val currentIndex = mapView.overlays.indexOf(pOverlay)
                                if (currentIndex in 0 until lastIndex) {
                                    mapView.overlays.removeAt(currentIndex)
                                    mapView.overlays.add(pOverlay)
                                }
                            }
                        }

                        // Evitar re-ejecutar el bucle de marcadores e invalidar el mapa cuando el usuario
                        // escribe texto en los formularios (tripMessage, guestName, guestPhone).
                        val currentSignature = Triple(
                            rawDriverLocations.toList(),
                            routePointsToPassenger,
                            Triple(activeTrip?.tripId, activeTrip?.status, activeTrip?.driverId)
                        )
                        val shouldSkip = (currentSignature == lastMapRenderSignature) &&
                            (rawDriverLocations.isEmpty() || driverMarkers.isNotEmpty())
                        if (shouldSkip) {
                            bringPassengerOverlayToFront()
                            return@AndroidView
                        }
                        lastMapRenderSignature = currentSignature

                        // Sincronizar trazado de ruta por calles con doble contorno de alto contraste
                        if (routePointsToPassenger.isNotEmpty()) {
                            if (routeCasingPolyline == null) {
                                val casing = Polyline(mapView).apply {
                                    outlinePaint.apply {
                                        color = android.graphics.Color.parseColor("#0B192C") // Borde exterior oscuro
                                        strokeWidth = 18f
                                        strokeCap = android.graphics.Paint.Cap.ROUND
                                        strokeJoin = android.graphics.Paint.Join.ROUND
                                        isAntiAlias = true
                                    }
                                }
                                routeCasingPolyline = casing
                            }
                            if (!mapView.overlays.contains(routeCasingPolyline)) {
                                // Insertar en la base (debajo de conductores y pasajero)
                                mapView.overlays.add(0, routeCasingPolyline)
                            }
                            routeCasingPolyline?.setPoints(routePointsToPassenger)

                            if (routePolyline == null) {
                                val mainLine = Polyline(mapView).apply {
                                    outlinePaint.apply {
                                        color = android.graphics.Color.parseColor("#00E5FF") // Cian neón brillante
                                        strokeWidth = 11f
                                        strokeCap = android.graphics.Paint.Cap.ROUND
                                        strokeJoin = android.graphics.Paint.Join.ROUND
                                        isAntiAlias = true
                                    }
                                }
                                routePolyline = mainLine
                            }
                            if (!mapView.overlays.contains(routePolyline)) {
                                val casingIdx = mapView.overlays.indexOf(routeCasingPolyline)
                                val targetIdx = if (casingIdx >= 0) casingIdx + 1 else 0
                                mapView.overlays.add(targetIdx, routePolyline)
                            }
                            routePolyline?.setPoints(routePointsToPassenger)
                        } else {
                            routeCasingPolyline?.let {
                                mapView.overlays.remove(it)
                                routeCasingPolyline = null
                            }
                            routePolyline?.let {
                                mapView.overlays.remove(it)
                                routePolyline = null
                            }
                        }

                        val currentUids = rawDriverLocations.map { it.uid }.toSet()

                        // Remover marcadores de conductores desconectados
                        val removedUids = driverMarkers.keys.filter { it !in currentUids }
                        for (uid in removedUids) {
                            driverAnimators.remove(uid)?.cancel()
                            val marker = driverMarkers.remove(uid)
                            if (marker != null) {
                                val startLat = marker.position.latitude
                                val exitAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
                                    duration = 400L
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
                            val isAssignedToMyTrip = activeTrip != null && activeTrip?.status == "accepted" && activeTrip?.driverId == driverLoc.uid
                            val isAvail = driverLoc.isAvailable

                            // Para el pasajero que solicitó el viaje se destaca a su conductor; para otros pasajeros se mantiene el estado ocupado/en pausa
                            val iconStateKey = when {
                                isAssignedToMyTrip -> 2
                                isAvail -> 1
                                else -> 0
                            }
                            fun createTargetIcon() = when (iconStateKey) {
                                2 -> com.example.tuctucmap.ui.util.MarkerIconUtils.getAssignedDriverTucTucIcon(context)
                                1 -> com.example.tuctucmap.ui.util.MarkerIconUtils.getTucTucIcon(context)
                                else -> com.example.tuctucmap.ui.util.MarkerIconUtils.getPausedTucTucIcon(context)
                            }
                            val targetAlpha = if (isAssignedToMyTrip || isAvail) 1.0f else 0.65f

                            if (marker == null) {
                                val startLat = newGeoPoint.latitude + 0.002
                                marker = Marker(mapView).apply {
                                    position = GeoPoint(startLat, newGeoPoint.longitude)
                                    setAlpha(0f)
                                    icon = createTargetIcon()
                                    relatedObject = iconStateKey
                                    infoWindow = null
                                    setOnMarkerClickListener { _, _ -> true }
                                }
                                driverMarkers[driverLoc.uid] = marker

                                // Insertar el marcador del conductor ANTES del overlay del pasajero para mantener al pasajero siempre encima
                                val passengerOverlay = myLocationOverlayInstance ?: mapView.overlays.firstOrNull { it is PassengerLocationOverlay }
                                val passengerIdx = passengerOverlay?.let { mapView.overlays.indexOf(it) } ?: -1
                                if (passengerIdx >= 0) {
                                    mapView.overlays.add(passengerIdx, marker)
                                } else {
                                    mapView.overlays.add(marker)
                                }

                                val entranceAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                                    duration = 800L
                                    interpolator = OvershootInterpolator()
                                    addUpdateListener { animation ->
                                        val fraction = animation.animatedValue as Float
                                        val currentLat = startLat + (newGeoPoint.latitude - startLat) * fraction
                                        marker.position = GeoPoint(currentLat, newGeoPoint.longitude)
                                        marker.setAlpha((fraction * targetAlpha).coerceIn(0f, targetAlpha))
                                        mapView.invalidate()
                                    }
                                }
                                driverAnimators[driverLoc.uid]?.cancel()
                                driverAnimators[driverLoc.uid] = entranceAnimator
                                entranceAnimator.start()
                            } else {
                                val oldGeoPoint = marker.position
                                val latDiff = kotlin.math.abs(oldGeoPoint.latitude - newGeoPoint.latitude)
                                val lonDiff = kotlin.math.abs(oldGeoPoint.longitude - newGeoPoint.longitude)

                                // Optimización de renderizado para gama baja:
                                // Solo interpolar si el desplazamiento supera 2.5 metros (filtro de ruido GPS).
                                // Cancelar proactivamente cualquier animación previa para evitar saturar el hilo de dibujo.
                                if (latDiff > 0.000025 || lonDiff > 0.000025) {
                                    driverAnimators[driverLoc.uid]?.cancel()
                                    val startLat = oldGeoPoint.latitude
                                    val startLon = oldGeoPoint.longitude
                                    val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                                        duration = 3500L
                                        interpolator = LinearInterpolator()
                                        addUpdateListener { animation ->
                                            val fraction = animation.animatedFraction
                                            val lat = startLat + (newGeoPoint.latitude - startLat) * fraction
                                            val lon = startLon + (newGeoPoint.longitude - startLon) * fraction
                                            marker.position = GeoPoint(lat, lon)
                                            mapView.invalidate()
                                        }
                                        addListener(object : AnimatorListenerAdapter() {
                                            override fun onAnimationEnd(animation: Animator) {
                                                driverAnimators.remove(driverLoc.uid)
                                            }
                                        })
                                    }
                                    driverAnimators[driverLoc.uid] = animator
                                    animator.start()
                                } else if (oldGeoPoint != newGeoPoint) {
                                    marker.position = newGeoPoint
                                }
                                marker.infoWindow = null
                                marker.setOnMarkerClickListener { _, _ -> true }
                                if (marker.relatedObject != iconStateKey) {
                                    marker.icon = createTargetIcon()
                                    marker.relatedObject = iconStateKey
                                }
                                marker.setAlpha(targetAlpha)
                            }
                        }

                        // Asegurar de manera estricta que el marcador animado del pasajero
                        // siempre permanezca en la capa superior absoluta (último en overlays)
                        bringPassengerOverlayToFront()

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
            // Chip de Estado Vacío: Sin tuctucs activos en el mapa (Fase 1 - Acción 3)
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
                                text = "Sin tuctucs activos en este momento",
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
                                    PassengerLocationPreferences.saveLastLocation(context, myLoc.latitude, myLoc.longitude)
                                    mapViewInstance?.controller?.animateTo(myLoc)
                                } else {
                                    PassengerLocationPreferences.getLastSavedLocation(context)?.let { savedLoc ->
                                        mapViewInstance?.controller?.animateTo(savedLoc)
                                    }
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
                            contentDescription = "Centrar mi ubicación en el mapa",
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Botón flotante para centrar en la ruta del viaje activo
                    if (activeTrip?.status == "accepted") {
                        FloatingActionButton(
                            onClick = { zoomToFitPassengerRoute() },
                            containerColor = Color(0xEE00E5FF),
                            contentColor = Color(0xFF0F2027),
                            shape = CircleShape,
                            modifier = Modifier.size(48.dp),
                            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Route,
                                contentDescription = "Centrar en la ruta",
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }



            // ══════════════════════════════════════════════════════════════════════
            // Bottom Sheet: Solicitar TucTuc a Todos los Conductores Cercanos
            // ══════════════════════════════════════════════════════════════════════
            if (showBroadcastDialog) {
                val broadcastScrollState = rememberScrollState()
                val broadcastSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(
                    onDismissRequest = { showBroadcastDialog = false },
                    sheetState = broadcastSheetState,
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
                                        text = "Pedir TucTuc Cercano",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = if (availableDrivers.isNotEmpty())
                                            "${availableDrivers.size} tuctuc${if (availableDrivers.size != 1) "s" else ""} disponible${if (availableDrivers.size != 1) "s" else ""} en la zona"
                                        else if (pausedDrivers.isNotEmpty())
                                            "Todos los tuctucs (${pausedDrivers.size}) están en pausa u ocupados"
                                        else "Sin tuctucs activos en este momento",
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

                        // Formulario Unificado: Datos de Pasajero y Destino
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0x33F9D423))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                if (!repository.isUserLoggedIn) {
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
                                        onValueChange = { guestName = it.replace("\r", " ").replace("\n", " ").take(50) },
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
                                        onValueChange = { guestPhone = it.filter { ch -> ch.isDigit() || ch == '-' || ch == '+' || ch == ' ' }.take(15) },
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

                                    Spacer(modifier = Modifier.height(10.dp))
                                } else {
                                    // Usuario autenticado: Resumen rápido de quién pide
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Person,
                                            contentDescription = null,
                                            tint = Color(0xFFF9D423),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Pasajero: ${passengerProfile?.nombre ?: "Mi Cuenta"} • Tel: ${passengerProfile?.telefono ?: "Sin número"}",
                                            color = Color(0xFFE2E8F0),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                }

                                // Campo de Destino dentro del mismo formulario
                                OutlinedTextField(
                                    value = tripMessage,
                                    onValueChange = { input -> tripMessage = input.replace("\r", " ").replace("\n", " ").take(PassengerDestinationPreferences.MAX_DESTINATION_LENGTH) },
                                    supportingText = {
                                        val maxDestLen = PassengerDestinationPreferences.MAX_DESTINATION_LENGTH
                                        Text(
                                            text = "${tripMessage.length}/$maxDestLen caracteres máx.",
                                            fontSize = 11.sp,
                                            color = if (tripMessage.length >= maxDestLen) Color(0xFFF9D423) else Color(0xFF94A3B8),
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.End
                                        )
                                    },
                                    label = { Text("Destino", color = Color.LightGray) },
                                    placeholder = { Text("¿Hacia dónde vas? (Ej: Parque Central)", color = Color.Gray) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Place,
                                            contentDescription = null,
                                            tint = Color(0xFFF9D423),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFFF9D423),
                                        unfocusedBorderColor = Color.Gray,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    singleLine = true
                                )

                                // Sugerencias de los últimos 5 destinos guardados localmente (sin datos de ejemplo si es primera vez)
                                if (recentDestinations.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            Icons.Default.History,
                                            contentDescription = null,
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Destinos recientes:",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        recentDestinations.forEach { dest ->
                                            SuggestionChip(
                                                onClick = { tripMessage = PassengerDestinationPreferences.sanitizeDestination(dest) },
                                                label = { Text(dest, fontSize = 11.sp, color = Color.White) },
                                                colors = SuggestionChipDefaults.suggestionChipColors(
                                                    containerColor = Color(0xFF334155)
                                                ),
                                                border = BorderStroke(1.dp, Color(0x33F9D423))
                                            )
                                        }
                                    }
                                }

                                if (!repository.isUserLoggedIn) {
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
                        }

                        Spacer(modifier = Modifier.height(28.dp))

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
                        val isValidGuestPhone = guestPhone.replace(Regex("[^0-9]"), "").length >= 8
                        val canRequest = if (repository.isUserLoggedIn) {
                            !isRequestingTrip && myLocGeo != null && hasAvailableDrivers
                        } else {
                            !isRequestingTrip && myLocGeo != null && guestName.trim().length >= 2 && isValidGuestPhone && hasAvailableDrivers
                        }

                        // Alerta si todos los tuctucs están en pausa
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
                                val myLoc = myLocationOverlayInstance?.myLocation ?: PassengerLocationPreferences.getLastSavedLocation(context)
                                if (myLoc != null) {
                                    isRequestingTrip = true
                                    PassengerLocationPreferences.saveLastLocation(context, myLoc.latitude, myLoc.longitude)
                                    if (!isUserLoggedIn) {
                                        sharedPrefs.edit()
                                            .putString("guest_passenger_name", guestName.trim())
                                            .putString("guest_passenger_phone", guestPhone.trim())
                                            .apply()
                                    }
                                    if (tripMessage.isNotBlank()) {
                                        PassengerDestinationPreferences.saveDestination(context, tripMessage)
                                        recentDestinations = PassengerDestinationPreferences.getRecentDestinations(context)
                                    }
                                    coroutineScope.launch {
                                        try {
                                            val result = repository.requestBroadcastTrip(
                                                lat = myLoc.latitude,
                                                lon = myLoc.longitude,
                                                message = tripMessage.ifBlank { "Pasajero esperando tuctuc" },
                                                passengerName = effectivePassengerName.ifBlank { "Pasajero" },
                                                passengerPhone = effectivePassengerPhone
                                            )
                                            result.onSuccess { tripId ->
                                                activeTripId = tripId
                                                sharedPrefs.edit().putString("active_passenger_trip_id", tripId).apply()
                                                tripMessage = ""
                                                showBroadcastDialog = false
                                                coroutineScope.launch {
                                                    kotlinx.coroutines.delay(60000)
                                                    if (activeTripId == tripId && activeTrip?.status == "requested") {
                                                        repository.updateTripStatus(tripId, "timeout")
                                                        kotlinx.coroutines.delay(10000)
                                                        repository.deleteTrip(tripId)
                                                        sharedPrefs.edit().remove("active_passenger_trip_id").apply()
                                                    }
                                                }
                                            }
                                        } finally {
                                            isRequestingTrip = false
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
                                        !hasAvailableDrivers && pausedDrivers.isNotEmpty() -> "No hay tuctucs disponibles (en pausa)"
                                        !hasAvailableDrivers -> "Sin tuctucs cercanos disponibles"
                                        !repository.isUserLoggedIn && (guestName.trim().length < 2 || !isValidGuestPhone) -> "Ingresa nombre y teléfono (8 dígitos)"
                                        else -> "Pedir TucTuc Cercano"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(28.dp))
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
                                            text = "Buscando TucTucs Cercanos...",
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 16.sp
                                        )
                                        Text(
                                            text = "Notificando a ${availableDrivers.size} tuctucs cercanos. El primero en aceptar vendrá hacia ti.",
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
                                        val tripId = activeTripId ?: return@OutlinedButton
                                        isCancellingTrip = true
                                        coroutineScope.launch {
                                            try {
                                                repository.deleteTrip(tripId)
                                                activeTripId = null
                                                activeTrip = null
                                            } finally {
                                                isCancellingTrip = false
                                            }
                                        }
                                    },
                                    enabled = !isCancellingTrip,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252))
                                ) {
                                    if (isCancellingTrip) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = Color(0xFFFF5252),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Text("Cancelar Solicitud")
                                    }
                                }
                            }
                            "accepted" -> {
                                // ── Tarjeta Compacta y Colapsable de Viaje Activo (Deja el 80% del mapa libre) ──
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { isTrackingCardExpanded = !isTrackingCardExpanded },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        DriverAvatar(
                                            photoUrl = assignedDriverProfile?.driverPhotoUrl,
                                            name = activeTrip!!.driverName.ifBlank { "Conductor" },
                                            size = 44.dp
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = "Tu TucTuc viene en camino",
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFF4ADE80),
                                                fontSize = 15.sp
                                            )
                                            Text(
                                                text = "${activeTrip!!.driverName.ifBlank { "Conductor" }} • ${activeTrip!!.driverPlate.ifBlank { "" }}",
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    // Indicador de ETA y botón colapsar/expandir
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            color = Color(0x3300E5FF),
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, Color(0xFF00E5FF))
                                        ) {
                                            Text(
                                                text = if (passengerRouteSummary != null)
                                                    "${passengerRouteSummary?.formattedDistance} • ~${passengerRouteSummary?.formattedDuration}"
                                                else
                                                    "En camino",
                                                color = Color(0xFF00E5FF),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        IconButton(
                                            onClick = { isTrackingCardExpanded = !isTrackingCardExpanded },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isTrackingCardExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                                contentDescription = if (isTrackingCardExpanded) "Colapsar" else "Expandir",
                                                tint = Color.LightGray
                                            )
                                        }
                                    }
                                }

                                // Botonera rápida de acción (Llamar, WhatsApp y Centrar Ruta)
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = {
                                            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${activeTrip!!.driverPhone}"))
                                            context.startActivity(dialIntent)
                                        },
                                        modifier = Modifier.weight(1f).height(44.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                                    ) {
                                        Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Llamar", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                                        modifier = Modifier.weight(1f).height(44.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                                    ) {
                                        Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("WhatsApp", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }

                                    FilledIconButton(
                                        onClick = { zoomToFitPassengerRoute() },
                                        modifier = Modifier.size(44.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = Color(0xFF00E5FF),
                                            contentColor = Color(0xFF0F2027)
                                        )
                                    ) {
                                        Icon(Icons.Default.Route, contentDescription = "Ver ruta completa", modifier = Modifier.size(20.dp))
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Botón para ver fotos 360° interactivas del vehículo
                                OutlinedButton(
                                    onClick = { showVehicle360Dialog = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(42.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color(0xFF00E5FF)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00E5FF))
                                ) {
                                    Text("🛺 Ver Vehículo 360°", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }

                                // Panel de detalles colapsable
                                AnimatedVisibility(visible = isTrackingCardExpanded) {
                                    Column(modifier = Modifier.padding(top = 10.dp)) {
                                        Surface(
                                            color = Color(0xFF1E293B),
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, Color(0x3300E5FF)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(8.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0xFF00E5FF))
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "ESTADO: EN CAMINO A TU UBICACIÓN",
                                                        color = Color(0xFF00E5FF),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.ExtraBold
                                                    )
                                                }
                                                if (passengerRouteSummary != null) {
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = "Ruta por calles: ${passengerRouteSummary?.formattedDistance} • Llegada estimada en ~${passengerRouteSummary?.formattedDuration}",
                                                        color = Color.White,
                                                        fontSize = 12.sp
                                                    )
                                                }
                                                if (activeTrip!!.message.isNotBlank()) {
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = "Nota del viaje: \"${activeTrip!!.message}\"",
                                                        color = Color(0xFFF9D423),
                                                        fontSize = 12.sp
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        Button(
                                            onClick = {
                                                activeTripId = null
                                                activeTrip = null
                                            },
                                            modifier = Modifier.fillMaxWidth().height(40.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Ocultar Tarjeta de Seguimiento", fontSize = 12.sp)
                                        }
                                    }
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
                                        isFinishingCompletedTrip = true
                                        coroutineScope.launch {
                                            try {
                                                val tid = activeTripId
                                                activeTripId = null
                                                activeTrip = null
                                                if (tid != null) {
                                                    repository.deleteTrip(tid)
                                                }
                                            } finally {
                                                isFinishingCompletedTrip = false
                                            }
                                        }
                                    },
                                    enabled = !isFinishingCompletedTrip,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFF9D423),
                                        contentColor = Color(0xFF0F2027)
                                    )
                                ) {
                                    if (isFinishingCompletedTrip) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = Color(0xFF0F2027),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Listo / Pedir Otro TucTuc",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                            "cancelled", "timeout" -> {
                                Text(
                                    text = if (activeTrip!!.status == "timeout") "Ningún tuctuc disponible respondió a tiempo." else "El viaje no pudo ser completado.",
                                    color = Color(0xFFFF5252),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Puedes intentar pedir nuevamente o llamar directamente a un tuctuc desde el mapa.",
                                    color = Color.LightGray,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        isClearingCancelledTrip = true
                                        coroutineScope.launch {
                                            try {
                                                val tid = activeTripId
                                                activeTripId = null
                                                activeTrip = null
                                                if (tid != null) {
                                                    repository.deleteTrip(tid)
                                                }
                                            } finally {
                                                isClearingCancelledTrip = false
                                            }
                                        }
                                    },
                                    enabled = !isClearingCancelledTrip,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF9D423), contentColor = Color(0xFF0F2027))
                                ) {
                                    if (isClearingCancelledTrip) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = Color(0xFF0F2027),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Text("Aceptar", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Diálogo para Visualización 360° Interactiva del TucTuc para el Pasajero
        if (showVehicle360Dialog && activeTrip != null) {
            val photos360 = assignedDriverProfile?.vehiclePhotos360 ?: emptyList()
            Dialog(
                onDismissRequest = { showVehicle360Dialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .wrapContentHeight(),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF1E293B),
                    tonalElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                DriverAvatar(
                                    photoUrl = assignedDriverProfile?.driverPhotoUrl,
                                    name = activeTrip!!.driverName,
                                    size = 46.dp
                                )
                                Column {
                                    Text(
                                        text = activeTrip!!.driverName.ifBlank { "Conductor" },
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = "Placas: ${activeTrip!!.driverPlate.ifBlank { "Sin placa" }}",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            IconButton(onClick = { showVehicle360Dialog = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.LightGray)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Arrastra con el dedo para ver el vehículo en 360°:",
                            color = Color(0xFF00E5FF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Vehicle360Viewer(
                            imageUrls = photos360,
                            autoSpinOnStart = true,
                            modifier = Modifier.height(240.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { showVehicle360Dialog = false },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                        ) {
                            Text("Entendido", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
}
