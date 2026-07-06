package com.example.tuctucmap.ui.screens.driver

import android.Manifest
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.tuctucmap.data.model.DriverLocation
import com.example.tuctucmap.data.repository.SupabaseRepository
import kotlinx.coroutines.flow.collectLatest
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import android.animation.ValueAnimator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.AccelerateInterpolator
/**
 * Pantalla de mapa en pantalla completa.
 * Muestra todos los conductores activos en tiempo real.
 * El botón de regreso (← o hardware back) regresa a la pantalla anterior.
 *
 * [repository]  repositorio Firebase para escuchar ubicaciones activas
 * [onBack]      callback para volver a la pantalla anterior
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverMapFullscreenScreen(
    repository: SupabaseRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // Colectar las ubicaciones activas en tiempo real
    val driverLocations = remember { mutableStateListOf<DriverLocation>() }
    LaunchedEffect(Unit) {
        repository.observeActiveLocations().collectLatest { locations ->
            driverLocations.clear()
            driverLocations.addAll(locations)
        }
    }

    // Ícono del Tuc Tuc con fondo transparente
    val tucTucIcon = remember(context) {
        val drawable = ContextCompat.getDrawable(context, com.example.tuctucmap.R.drawable.ic_tuc_tuc)
        if (drawable is android.graphics.drawable.BitmapDrawable) {
            val originalBitmap = drawable.bitmap
            val size = 144
            val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(originalBitmap, size, size, true)
            val pixels = IntArray(scaledBitmap.width * scaledBitmap.height)
            scaledBitmap.getPixels(pixels, 0, scaledBitmap.width, 0, 0, scaledBitmap.width, scaledBitmap.height)
            for (i in pixels.indices) {
                val r = (pixels[i] shr 16) and 0xff
                val g = (pixels[i] shr 8) and 0xff
                val b = pixels[i] and 0xff
                if (r > 230 && g > 230 && b > 230) pixels[i] = 0x00000000
            }
            val bmp = android.graphics.Bitmap.createBitmap(
                scaledBitmap.width, scaledBitmap.height, android.graphics.Bitmap.Config.ARGB_8888
            )
            bmp.setPixels(pixels, 0, scaledBitmap.width, 0, 0, scaledBitmap.width, scaledBitmap.height)
            android.graphics.drawable.BitmapDrawable(context.resources, bmp)
        } else drawable
    }

    // Permiso de ubicación
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasLocationPermission = (perms[Manifest.permission.ACCESS_FINE_LOCATION] ?: false)
                || (perms[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false)
    }
    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    var selectedDriver by remember { mutableStateOf<DriverLocation?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }
    var myLocationOverlayInstance by remember { mutableStateOf<MyLocationNewOverlay?>(null) }
    val driverMarkers = remember { mutableMapOf<String, Marker>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "TucTucs Disponibles",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "${driverLocations.size} conductor${if (driverLocations.size != 1) "es" else ""} en línea",
                            color = Color(0xFF4CAF50),
                            fontSize = 13.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Regresar",
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
                        myLocationOverlayInstance?.myLocation?.let { loc ->
                            mapViewInstance?.controller?.animateTo(loc)
                        }
                    },
                    containerColor = Color(0xFFF9D423),
                    contentColor = Color(0xFF0F2027)
                ) {
                    Icon(imageVector = Icons.Default.MyLocation, contentDescription = "Mi ubicación")
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
            // ── Mapa ─────────────────────────────────────────────────────────
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(16.0)
                        controller.setCenter(GeoPoint(14.6349, -90.5069))

                        if (hasLocationPermission) {
                            val myLocOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this).apply {
                                enableMyLocation()
                                runOnFirstFix {
                                    myLocation?.let { loc -> post { controller.animateTo(loc) } }
                                }
                            }
                            overlays.add(myLocOverlay)
                            myLocationOverlayInstance = myLocOverlay
                        }
                        mapViewInstance = this
                    }
                },
                update = { mapView ->
                    val currentUids = driverLocations.map { it.uid }.toSet()
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
                            marker.snippet = "Placas: ${driverLoc.placas}"
                        }
                    }
                    mapView.invalidate()
                },
                modifier = Modifier.fillMaxSize()
            )

            // ── Mensaje si no hay conductores activos ─────────────────────────
            if (driverLocations.isEmpty()) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 90.dp, start = 16.dp, end = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xCC0F2027))
                ) {
                    Text(
                        text = "🛺  Sin conductores activos en este momento",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                }
            }

            // ── Bottom Sheet: detalles del conductor seleccionado ─────────────
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
                            Text(text = "🛺", fontSize = 40.sp)
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
                            Button(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${driver.telefono}"))
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.weight(1f).height(54.dp),
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

                            Button(
                                onClick = {
                                    var phone = driver.telefono.replace(" ", "").replace("-", "")
                                    if (phone.length == 8) phone = "502$phone"
                                    val msg = "Hola, vi tu TucTuc en el mapa de la app. ¿Estás disponible para un viaje?"
                                    val url = "https://api.whatsapp.com/send?phone=$phone&text=${Uri.encode(msg)}"
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                },
                                modifier = Modifier.weight(1f).height(54.dp),
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
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}
