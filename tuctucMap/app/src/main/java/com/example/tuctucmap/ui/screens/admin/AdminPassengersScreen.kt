package com.example.tuctucmap.ui.screens.admin

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
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
import com.example.tuctucmap.data.model.AdminPassenger
import com.example.tuctucmap.data.repository.AdminRepository
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPassengersScreen(
    adminRepo: AdminRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var passengers by remember { mutableStateOf<List<AdminPassenger>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    fun loadPassengers() {
        scope.launch {
            isLoading = true
            val res = adminRepo.getPassengers()
            if (res.isSuccess) {
                passengers = res.getOrDefault(emptyList())
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadPassengers()
    }

    val filteredPassengers = remember(passengers, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) passengers
        else {
            passengers.filter { p ->
                p.nombre.lowercase().contains(q) ||
                        p.telefono.contains(q) ||
                        p.email.lowercase().contains(q)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Directorio de Pasajeros", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E293B))
            )
        },
        containerColor = Color(0xFF0F172A)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Buscador
            Box(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar por nombre, teléfono o correo...", color = Color(0xFF94A3B8), fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFFF59E0B)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Limpiar", tint = Color(0xFF94A3B8))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF1E293B),
                        unfocusedContainerColor = Color(0xFF1E293B),
                        focusedBorderColor = Color(0xFFF59E0B),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFFF59E0B))
                }
            } else if (filteredPassengers.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No se encontraron pasajeros registrados", color = Color(0xFF94A3B8), fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text(
                            text = "${passengers.size} Pasajeros Registrados en Supabase",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    items(filteredPassengers, key = { it.id }) { passenger ->
                        PassengerCard(
                            passenger = passenger,
                            onWhatsApp = {
                                openWhatsApp(context, passenger.telefono, passenger.nombre)
                            },
                            onCall = {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${passenger.telefono}"))
                                context.startActivity(intent)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PassengerCard(
    passenger: AdminPassenger,
    onWhatsApp: () -> Unit,
    onCall: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Inicial
            val initial = (passenger.nombre.trim().firstOrNull() ?: 'P').uppercaseChar().toString()
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF334155)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF38BDF8)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = passenger.nombre.ifBlank { "Sin nombre" },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "📞 ${passenger.telefono.ifBlank { "Sin teléfono" }}",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )
                Text(
                    text = "Viajes solicitados: ${passenger.tripsCount} • Desde: ${formatDateShort(passenger.createdAtEpoch)}",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )
            }

            // Botones de acción
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (passenger.telefono.isNotBlank()) {
                    IconButton(
                        onClick = onWhatsApp,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0x2210B981), CircleShape)
                    ) {
                        Icon(Icons.Default.Chat, contentDescription = "WhatsApp", tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                    }

                    IconButton(
                        onClick = onCall,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0x2238BDF8), CircleShape)
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = "Llamar", tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

private fun formatDateShort(epochMs: Long): String {
    if (epochMs <= 0L) return "N/D"
    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.US)
    return sdf.format(Date(epochMs))
}

private fun openWhatsApp(context: Context, rawPhone: String, name: String) {
    try {
        val phone = rawPhone.replace(Regex("[^0-9]"), "")
        val formatted = if (phone.startsWith("502")) phone else "502$phone"
        val msg = "Hola $name, te contactamos de la administración de TucTuc Map."
        val encoded = URLEncoder.encode(msg, "UTF-8")
        val uri = Uri.parse("https://wa.me/$formatted?text=$encoded")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "No se pudo abrir WhatsApp", Toast.LENGTH_SHORT).show()
    }
}

