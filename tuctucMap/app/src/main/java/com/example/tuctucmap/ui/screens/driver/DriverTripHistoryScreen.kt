package com.example.tuctucmap.ui.screens.driver

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tuctucmap.data.repository.SupabaseRepository
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverTripHistoryScreen(
    repository: SupabaseRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var trips by remember { mutableStateOf<List<SupabaseRepository.CompletedTripResponseDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedTrip by remember { mutableStateOf<SupabaseRepository.CompletedTripResponseDto?>(null) }

    LaunchedEffect(Unit) {
        repository.getDriverTripHistory().onSuccess {
            trips = it.sortedByDescending { trip -> trip.created_at }
        }
        isLoading = false
    }

    val dateFormat = remember {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }
    }

    fun parseDate(dateStr: String): String {
        return try {
            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val date = isoFormat.parse(dateStr)
            if (date != null) dateFormat.format(date) else dateStr
        } catch (e: Exception) {
            dateStr
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Viajes Realizados", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F2027))
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF2C5364))
                .padding(innerPadding)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color(0xFFF9D423))
            } else if (trips.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(imageVector = Icons.Default.History, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No tienes viajes en tu historial", color = Color.White, fontSize = 18.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(trips) { trip ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedTrip = trip },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF203A43)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(trip.passenger_name, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Mensaje:", color = Color.LightGray, fontSize = 12.sp)
                                Text("${trip.message}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Fecha: ${parseDate(trip.created_at)}", color = Color.LightGray, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Estado: ", color = Color.LightGray, fontSize = 14.sp)
                                    Text(
                                        text = if (trip.status == "completed") "Completado" else trip.status,
                                        color = if (trip.status == "completed") Color(0xFF4CAF50) else Color(0xFFF9D423),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (selectedTrip != null) {
                AlertDialog(
                    onDismissRequest = { selectedTrip = null },
                    title = { Text("Opciones de Viaje", fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            Text("¿Qué deseas hacer con el viaje de ${selectedTrip!!.passenger_name}?")
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    try {
                                        val uri = android.net.Uri.parse("google.navigation:q=${selectedTrip!!.passenger_lat},${selectedTrip!!.passenger_lon}")
                                        val mapIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                                        mapIntent.setPackage("com.google.android.apps.maps")
                                        if (mapIntent.resolveActivity(context.packageManager) != null) {
                                            context.startActivity(mapIntent)
                                        } else {
                                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                    selectedTrip = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A5F))
                            ) {
                                Text("Trazar Ruta (Google Maps)")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    try {
                                        val phone = selectedTrip!!.passenger_phone
                                        if (phone.isNotBlank()) {
                                            val url = "https://api.whatsapp.com/send?phone=$phone"
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                                            intent.data = android.net.Uri.parse(url)
                                            context.startActivity(intent)
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                    selectedTrip = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                            ) {
                                Text("Contactar por WhatsApp", color = Color.White)
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { selectedTrip = null }) {
                            Text("Cancelar", color = Color.Gray)
                        }
                    }
                )
            }
        }
    }
}
