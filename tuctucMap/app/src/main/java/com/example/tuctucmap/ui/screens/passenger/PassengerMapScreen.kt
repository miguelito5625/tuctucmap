package com.example.tuctucmap.ui.screens.passenger

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    onBackToOnboarding: () -> Unit
) {
    val context = LocalContext.current
    val driverLocations by repository.observeActiveLocations().collectAsState(initial = emptyList())

    // Cargar y procesar el icono de Tuc Tuc (escalarlo a 96x96 y hacer transparente el fondo blanco)
    val tucTucIcon = remember(context) {
        val drawable = ContextCompat.getDrawable(context, com.example.tuctucmap.R.drawable.ic_tuc_tuc)
        if (drawable is android.graphics.drawable.BitmapDrawable) {
            val originalBitmap = drawable.bitmap
            val size = 144 // 144x144 píxeles para que sea más visible en el mapa
            val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(originalBitmap, size, size, true)
            val width = scaledBitmap.width
            val height = scaledBitmap.height
            val pixels = IntArray(width * height)
            scaledBitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            for (i in pixels.indices) {
                val pixel = pixels[i]
                val r = (pixel shr 16) and 0xff
                val g = (pixel shr 8) and 0xff
                val b = pixel and 0xff
                if (r > 230 && g > 230 && b > 230) {
                    pixels[i] = 0x00000000 // Transparente
                }
            }
            val transparentBitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            transparentBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            android.graphics.drawable.BitmapDrawable(context.resources, transparentBitmap)
        } else {
            drawable
        }
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

    var selectedDriver by remember { mutableStateOf<DriverLocation?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }

    var tripMessage by remember { mutableStateOf("") }
    var isRequestingTrip by remember { mutableStateOf(false) }
    var activeTripId by remember { mutableStateOf<String?>(null) }
    var activeTrip by remember { mutableStateOf<SupabaseRepository.TripInfo?>(null) }

    // Observe active trip status
    LaunchedEffect(activeTripId) {
        activeTripId?.let { tid ->
            repository.observeTrip(tid).collect { trip ->
                activeTrip = trip
            }
        }
    }

    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }
    var myLocationOverlayInstance by remember { mutableStateOf<MyLocationNewOverlay?>(null) }
    val driverMarkers = remember { mutableMapOf<String, Marker>() }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TucTucs Disponibles", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBackToOnboarding) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Volver",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F2027)
                )
            )
        },
        floatingActionButton = {
            if (hasLocationPermission) {
                FloatingActionButton(
                    onClick = {
                        myLocationOverlayInstance?.let { overlay ->
                            val myLoc = overlay.myLocation
                            if (myLoc != null) {
                                mapViewInstance?.controller?.animateTo(myLoc)
                            }
                        }
                    },
                    containerColor = Color(0xFFF9D423),
                    contentColor = Color(0xFF0F2027)
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Mi ubicación"
                    )
                }
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
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            
                            controller.setZoom(16.0)
                            // Centro inicial por defecto
                            val startPoint = GeoPoint(14.6349, -90.5069)
                            controller.setCenter(startPoint)

                            val myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this).apply {
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
                        val currentUids = driverLocations.map { it.uid }.toSet()
                        
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
                        driverLocations.forEach { driverLoc ->
                            val newGeoPoint = GeoPoint(driverLoc.latitud, driverLoc.longitud)
                            var marker = driverMarkers[driverLoc.uid]

                            if (marker == null) {
                                val startLat = newGeoPoint.latitude + 0.002
                                marker = Marker(mapView).apply {
                                    position = GeoPoint(startLat, newGeoPoint.longitude)
                                    setAlpha(0f)
                                    title = driverLoc.nombre
                                    snippet = "Placas: ${driverLoc.placas}"
                                    icon = tucTucIcon
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
                                        marker.setAlpha(fraction.coerceIn(0f, 1f))
                                        mapView.invalidate()
                                    }
                                }
                                entranceAnimator.start()
                            } else {
                                val oldGeoPoint = marker.position
                                if (oldGeoPoint.latitude != newGeoPoint.latitude || oldGeoPoint.longitude != newGeoPoint.longitude) {
                                    val animator = ValueAnimator.ofFloat(0f, 1f)
                                    animator.duration = 4000L // Interpolar durante 4s
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
                                // Actualizar info si cambió
                                marker.title = driverLoc.nombre
                                marker.snippet = "Placas: ${driverLoc.placas}"
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

            // Bottom Sheet de Detalles del Conductor
            if (showBottomSheet && selectedDriver != null) {
                val driver = selectedDriver!!
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
                            Column {
                                Text(
                                    text = driver.nombre,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Placas del Torito: ${driver.placas}",
                                    fontSize = 16.sp,
                                    color = Color(0xFFF9D423),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = "🛺",
                                fontSize = 40.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "Ponte en contacto con el conductor:",
                            fontSize = 14.sp,
                            color = Color.LightGray,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
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
                                    .height(54.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF4CAF50),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(imageVector = Icons.Default.Call, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Llamar", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }

                            // Botón de WhatsApp
                            Button(
                                onClick = {
                                    var formattedPhone = driver.telefono.replace(" ", "").replace("-", "")
                                    if (formattedPhone.length == 8) {
                                        formattedPhone = "502$formattedPhone"
                                    }
                                    val message = "Hola, vi tu TucTuc en el mapa de la app. ¿Estás disponible para un viaje?"
                                    val waUrl = "https://api.whatsapp.com/send?phone=$formattedPhone&text=${Uri.encode(message)}"
                                    val waIntent = Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(waUrl)
                                    )
                                    context.startActivity(waIntent)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(54.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF25D366),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(imageVector = Icons.Default.Chat, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("WhatsApp", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                        
                        Text(
                            text = "O puedes solicitar un viaje rápido:",
                            fontSize = 14.sp,
                            color = Color.LightGray,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        OutlinedTextField(
                            value = tripMessage,
                            onValueChange = { tripMessage = it },
                            placeholder = { Text("Ej: Voy al mercado central...", color = Color.Gray) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFF9D423),
                                unfocusedBorderColor = Color.Gray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        if (activeTrip != null) {
                            val statusText = when (activeTrip!!.status) {
                                "requested" -> "Esperando respuesta del conductor..."
                                "accepted" -> "¡Viaje aceptado! El conductor ha recibido tu solicitud."
                                "cancelled" -> "El conductor rechazó el viaje."
                                "timeout" -> "El conductor no respondió a tiempo."
                                else -> "Estado desconocido"
                            }
                            val statusColor = when (activeTrip!!.status) {
                                "accepted" -> Color(0xFF4CAF50)
                                "cancelled", "timeout" -> Color(0xFFFF5252)
                                else -> Color(0xFFF9D423)
                            }
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0x33000000))
                            ) {
                                Text(
                                    text = statusText,
                                    modifier = Modifier.padding(16.dp),
                                    color = statusColor,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                            
                            if (activeTrip!!.status == "accepted" || activeTrip!!.status == "cancelled" || activeTrip!!.status == "timeout") {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { 
                                        coroutineScope.launch { repository.deleteTrip(activeTripId!!) }
                                        activeTripId = null
                                        activeTrip = null
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                                ) {
                                    Text("Cerrar")
                                }
                            }
                        } else {
                            Button(
                                onClick = {
                                    val myLoc = myLocationOverlayInstance?.myLocation
                                    if (myLoc != null) {
                                        isRequestingTrip = true
                                        coroutineScope.launch {
                                            val result = repository.requestTrip(
                                                driverId = driver.uid,
                                                lat = myLoc.latitude,
                                                lon = myLoc.longitude,
                                                message = tripMessage.ifBlank { "Solicitud rápida" }
                                            )
                                            isRequestingTrip = false
                                            result.onSuccess { tripId ->
                                                activeTripId = tripId
                                                tripMessage = ""
                                                
                                                coroutineScope.launch {
                                                    kotlinx.coroutines.delay(60000)
                                                    if (activeTripId == tripId && activeTrip?.status == "requested") {
                                                        repository.updateTripStatus(tripId, "timeout")
                                                        kotlinx.coroutines.delay(10000)
                                                        repository.deleteTrip(tripId)
                                                    }
                                                }
                                            }.onFailure {
                                                // Manejar error (Toast)
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isRequestingTrip,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFF9D423),
                                    contentColor = Color(0xFF0F2027)
                                )
                            ) {
                                if (isRequestingTrip) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color(0xFF0F2027))
                                } else {
                                    Text("Solicitar Viaje", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}
