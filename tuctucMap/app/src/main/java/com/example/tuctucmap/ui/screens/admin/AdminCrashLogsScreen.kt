package com.example.tuctucmap.ui.screens.admin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tuctucmap.data.model.AdminCrashLog
import com.example.tuctucmap.data.repository.AdminRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminCrashLogsScreen(
    adminRepo: AdminRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var crashes by remember { mutableStateOf<List<AdminCrashLog>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("all") } // "all", "pending", "resolved"

    var selectedCrashForDetail by remember { mutableStateOf<AdminCrashLog?>(null) }
    var crashToDelete by remember { mutableStateOf<AdminCrashLog?>(null) }

    fun loadCrashes() {
        scope.launch {
            isLoading = true
            val res = adminRepo.getCrashLogs()
            if (res.isSuccess) {
                crashes = res.getOrDefault(emptyList())
            } else {
                Toast.makeText(context, "Error al cargar reportes de fallos", Toast.LENGTH_SHORT).show()
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadCrashes()
    }

    val totalCount = crashes.size
    val pendingCount = crashes.count { !it.resolved }
    val resolvedCount = crashes.count { it.resolved }

    val filteredCrashes = remember(crashes, searchQuery, selectedFilter) {
        val q = searchQuery.trim().lowercase()
        crashes.filter { c ->
            val matchesFilter = when (selectedFilter) {
                "pending" -> !c.resolved
                "resolved" -> c.resolved
                else -> true
            }
            val matchesSearch = if (q.isEmpty()) true else {
                c.displayDevice.lowercase().contains(q) ||
                        c.androidVersion.lowercase().contains(q) ||
                        c.errorType.lowercase().contains(q) ||
                        (c.errorMessage ?: "").lowercase().contains(q) ||
                        (c.userEmail ?: "").lowercase().contains(q)
            }
            matchesFilter && matchesSearch
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Reportes de Errores Móvil",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Telemetría y fallos no controlados",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Atrás",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { loadCrashes() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Actualizar",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E293B)
                )
            )
        },
        containerColor = Color(0xFF0F172A)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            // ─── Tarjetas Resumen / KPIs ───────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                KpiMiniCard(
                    title = "Total",
                    count = totalCount,
                    accentColor = Color(0xFF38BDF8),
                    modifier = Modifier.weight(1f)
                )
                KpiMiniCard(
                    title = "Pendientes",
                    count = pendingCount,
                    accentColor = Color(0xFFEF4444),
                    modifier = Modifier.weight(1f)
                )
                KpiMiniCard(
                    title = "Resueltos",
                    count = resolvedCount,
                    accentColor = Color(0xFF10B981),
                    modifier = Modifier.weight(1f)
                )
            }

            // ─── Buscador ─────────────────────────────────────────────────
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text("Buscar por dispositivo, SO, error o usuario...", fontSize = 13.sp, color = Color(0xFF64748B))
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF94A3B8))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Limpiar", tint = Color(0xFF94A3B8))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xFF1E293B),
                    unfocusedContainerColor = Color(0xFF1E293B),
                    focusedBorderColor = Color(0xFF38BDF8),
                    unfocusedBorderColor = Color(0xFF334155)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            // ─── Chips de Filtro ──────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == "all",
                    onClick = { selectedFilter = "all" },
                    label = { Text("Todos ($totalCount)", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color(0xFF1E293B),
                        labelColor = Color(0xFF94A3B8),
                        selectedContainerColor = Color(0xFF334155),
                        selectedLabelColor = Color.White
                    )
                )
                FilterChip(
                    selected = selectedFilter == "pending",
                    onClick = { selectedFilter = "pending" },
                    label = { Text("Solo Pendientes ($pendingCount)", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color(0xFF1E293B),
                        labelColor = Color(0xFF94A3B8),
                        selectedContainerColor = Color(0x33EF4444),
                        selectedLabelColor = Color(0xFFF87171)
                    )
                )
                FilterChip(
                    selected = selectedFilter == "resolved",
                    onClick = { selectedFilter = "resolved" },
                    label = { Text("Solo Resueltos ($resolvedCount)", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color(0xFF1E293B),
                        labelColor = Color(0xFF94A3B8),
                        selectedContainerColor = Color(0x3310B981),
                        selectedLabelColor = Color(0xFF34D399)
                    )
                )
            }

            // ─── Lista de Errores ─────────────────────────────────────────
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF38BDF8))
                }
            } else if (filteredCrashes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(56.dp)
                        )
                        Text(
                            text = if (searchQuery.isNotEmpty() || selectedFilter != "all")
                                "No hay reportes que coincidan con el filtro"
                            else
                                "¡Excelente! No hay fallos pendientes registrados",
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF94A3B8),
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredCrashes, key = { it.id }) { crash ->
                        CrashLogCard(
                            crash = crash,
                            onViewDetail = { selectedCrashForDetail = crash },
                            onToggleResolved = {
                                scope.launch {
                                    val nextStatus = !crash.resolved
                                    val res = adminRepo.setCrashLogResolved(crash.id, nextStatus)
                                    if (res.isSuccess) {
                                        loadCrashes()
                                        Toast.makeText(
                                            context,
                                            if (nextStatus) "Incidencia marcada como resuelta" else "Incidencia reabierta",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        Toast.makeText(context, "Error al actualizar estado", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onDelete = { crashToDelete = crash }
                        )
                    }
                }
            }
        }
    }

    // ─── Modal BottomSheet para Stack Trace y Detalle ─────────────────────
    if (selectedCrashForDetail != null) {
        val detail = selectedCrashForDetail!!
        ModalBottomSheet(
            onDismissRequest = { selectedCrashForDetail = null },
            containerColor = Color(0xFF0F172A),
            contentColor = Color.White,
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF64748B)) }
        ) {
            CrashDetailContent(
                crash = detail,
                onCopyTrace = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Stack Trace", detail.stackTrace)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Stack trace copiado al portapapeles", Toast.LENGTH_SHORT).show()
                },
                onToggleResolved = {
                    scope.launch {
                        val nextStatus = !detail.resolved
                        val res = adminRepo.setCrashLogResolved(detail.id, nextStatus)
                        if (res.isSuccess) {
                            selectedCrashForDetail = detail.copy(resolved = nextStatus)
                            loadCrashes()
                            Toast.makeText(
                                context,
                                if (nextStatus) "Marcado como resuelto" else "Reabierto como pendiente",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                onClose = { selectedCrashForDetail = null }
            )
        }
    }

    // ─── Diálogo de Confirmación para Eliminar ────────────────────────────
    if (crashToDelete != null) {
        val item = crashToDelete!!
        AlertDialog(
            onDismissRequest = { crashToDelete = null },
            title = { Text("¿Eliminar reporte de error?", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Text(
                    "Se eliminará permanentemente el reporte '${item.errorType}' del dispositivo ${item.displayDevice}.",
                    color = Color(0xFFCBD5E1),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val res = adminRepo.deleteCrashLog(item.id)
                            if (res.isSuccess) {
                                loadCrashes()
                                Toast.makeText(context, "Reporte eliminado", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Error al eliminar", Toast.LENGTH_SHORT).show()
                            }
                            crashToDelete = null
                        }
                    }
                ) {
                    Text("Eliminar", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { crashToDelete = null }) {
                    Text("Cancelar", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }
}

// ─── Tarjeta KPI Mini ────────────────────────────────────────────────────────
@Composable
private fun KpiMiniCard(
    title: String,
    count: Int,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1E293B),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x1AFFFFFF)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$count",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF94A3B8)
            )
        }
    }
}

// ─── Tarjeta de Cada Incidencia ──────────────────────────────────────────────
@Composable
private fun CrashLogCard(
    crash: AdminCrashLog,
    onViewDetail: () -> Unit,
    onToggleResolved: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (crash.resolved) Color(0x3310B981) else Color(0x33EF4444)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header con Status Badge y Fecha
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (crash.resolved) Color(0x2610B981) else Color(0x26EF4444)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (crash.resolved) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (crash.resolved) Color(0xFF34D399) else Color(0xFFF87171),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (crash.resolved) "RESUELTO" else "PENDIENTE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (crash.resolved) Color(0xFF34D399) else Color(0xFFF87171)
                        )
                    }
                }

                Text(
                    text = formatCrashDate(crash.createdAt),
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Dispositivo y Versión SO
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = crash.displayDevice,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFF38BDF8)
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF0F172A)
                ) {
                    Text(
                        text = "Android ${crash.androidVersion} (API ${crash.sdkInt})",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Versión de la app
            Text(
                text = "App: v${crash.appVersion} (build ${crash.buildNumber} • ${crash.buildType})",
                fontSize = 11.sp,
                color = Color(0xFF64748B)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Tipo de error y mensaje
            Text(
                text = crash.errorType,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFF87171),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (!crash.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = crash.errorMessage,
                    fontSize = 12.sp,
                    color = Color(0xFFCBD5E1),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Usuario / Rol
            Text(
                text = "Usuario: ${crash.userEmail ?: "Anónimo"} (${crash.role ?: "no logueado"})",
                fontSize = 11.sp,
                color = Color(0xFF94A3B8)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Botones de acción
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onViewDetail,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF38BDF8))
                ) {
                    Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ver Traza", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                IconButton(onClick = onToggleResolved) {
                    Icon(
                        imageVector = if (crash.resolved) Icons.Default.Replay else Icons.Default.Check,
                        contentDescription = if (crash.resolved) "Reabrir" else "Resolver",
                        tint = if (crash.resolved) Color(0xFFF59E0B) else Color(0xFF10B981)
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Eliminar",
                        tint = Color(0xFFEF4444)
                    )
                }
            }
        }
    }
}

// ─── Contenido del BottomSheet de Detalle de Traza ───────────────────────────
@Composable
private fun CrashDetailContent(
    crash: AdminCrashLog,
    onCopyTrace: () -> Unit,
    onToggleResolved: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = crash.errorType,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFF87171),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color(0xFF94A3B8))
            }
        }

        if (!crash.errorMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0x1AEF4444),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33EF4444)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = crash.errorMessage,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFFCA5A5),
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Grid de Metadatos
        Text(
            text = "INFORMACIÓN DEL SISTEMA",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF64748B)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "• Dispositivo: ${crash.displayDevice} (${crash.deviceBrand})\n" +
                    "• SO: Android ${crash.androidVersion} (SDK API ${crash.sdkInt})\n" +
                    "• App: v${crash.appVersion} (Build ${crash.buildNumber} • ${crash.buildType})\n" +
                    "• Hilo: ${crash.threadName ?: "main"}\n" +
                    "• Usuario: ${crash.userEmail ?: "Anónimo"} (${crash.role ?: "no logueado"})\n" +
                    "• Fecha: ${crash.createdAt}",
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = Color(0xFFCBD5E1)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Pila de llamadas (Stack Trace)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PILA DE LLAMADAS (STACK TRACE)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF64748B)
            )

            Button(
                onClick = onCopyTrace,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Copiar", fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp)
                .background(Color(0xFF020617), RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                .padding(10.dp)
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
        ) {
            Text(
                text = crash.stackTrace.ifBlank { "Sin traza disponible" },
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFF87171),
                lineHeight = 15.sp
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Acciones al pie del modal
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onToggleResolved,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (crash.resolved) Color(0xFFD97706) else Color(0xFF059669)
                ),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (crash.resolved) Icons.Default.Replay else Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (crash.resolved) "Reabrir Incidencia" else "Marcar como Resuelto",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedButton(
                onClick = onClose,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF475569))
            ) {
                Text("Cerrar", fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

private fun formatCrashDate(isoString: String): String {
    if (isoString.isBlank()) return "Sin fecha"
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val date = parser.parse(isoString.substringBefore("."))
        val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        if (date != null) formatter.format(date) else isoString
    } catch (_: Exception) {
        isoString
    }
}

