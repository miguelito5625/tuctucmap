package com.example.tuctucmap.ui.screens.admin

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tuctucmap.data.model.AppConfig
import com.example.tuctucmap.data.repository.AdminRepository
import com.example.tuctucmap.data.repository.SupabaseRepository
import kotlinx.coroutines.launch

@Composable
fun AdminBusinessConfigTab(
    adminRepo: AdminRepository,
    supabaseRepo: SupabaseRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var config by remember { mutableStateOf(AppConfig()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    // Campos editables
    var whatsappPhone by remember { mutableStateOf("") }
    var bank1Name by remember { mutableStateOf("") }
    var bank1Account by remember { mutableStateOf("") }
    var bank2Name by remember { mutableStateOf("") }
    var bank2Account by remember { mutableStateOf("") }
    var accountHolder by remember { mutableStateOf("") }
    var currencySymbol by remember { mutableStateOf("Q") }
    var priceDaily by remember { mutableStateOf("10.0") }
    var priceWeekly by remember { mutableStateOf("25.0") }
    var priceQuincenal by remember { mutableStateOf("45.0") }
    var priceMonthly by remember { mutableStateOf("80.0") }
    var searchRadiusKm by remember { mutableStateOf("5.0") }

    fun loadConfig() {
        scope.launch {
            isLoading = true
            val cfg = supabaseRepo.getAppConfig(force = true)
            config = cfg
            whatsappPhone = cfg.adminWhatsapp
            bank1Name = cfg.bank1Name
            bank1Account = cfg.bank1Account
            bank2Name = cfg.bank2Name
            bank2Account = cfg.bank2Account
            accountHolder = cfg.accountHolderName
            currencySymbol = cfg.currencySymbol
            priceDaily = cfg.priceDaily.toString()
            priceWeekly = cfg.priceSemanal.toString()
            priceQuincenal = cfg.priceQuincenal.toString()
            priceMonthly = cfg.priceMensual.toString()
            searchRadiusKm = cfg.searchRadiusKm.toString()
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadConfig()
    }

    if (isLoading) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFFF59E0B))
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A))
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Configuración Comercial",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Estos datos se sincronizan en tiempo real con la app de todos los conductores para cobros y geocerca.",
                fontSize = 13.sp,
                color = Color(0xFF94A3B8)
            )

            // ── WhatsApp de Atención Administrativa ─────────────────────────
            ConfigSectionTitle("WhatsApp Administrativo")
            OutlinedTextField(
                value = whatsappPhone,
                onValueChange = { whatsappPhone = it },
                label = { Text("Número con código de país (ej. 50230000000)") },
                leadingIcon = { Icon(Icons.Default.Chat, contentDescription = null, tint = Color(0xFF10B981)) },
                trailingIcon = {
                    TextButton(onClick = {
                        testWhatsAppNumber(context, whatsappPhone)
                    }) {
                        Text("Probar", color = Color(0xFF10B981), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = configFieldColors()
            )

            // ── Cuentas Bancarias ───────────────────────────────────────────
            ConfigSectionTitle("Cuentas Bancarias para Depósito")
            OutlinedTextField(
                value = bank1Name,
                onValueChange = { bank1Name = it },
                label = { Text("Banco 1 - Nombre y Tipo") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = configFieldColors()
            )
            OutlinedTextField(
                value = bank1Account,
                onValueChange = { bank1Account = it },
                label = { Text("Banco 1 - Número de Cuenta") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = configFieldColors()
            )

            Spacer(modifier = Modifier.height(4.dp))

            OutlinedTextField(
                value = bank2Name,
                onValueChange = { bank2Name = it },
                label = { Text("Banco 2 - Nombre y Tipo") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = configFieldColors()
            )
            OutlinedTextField(
                value = bank2Account,
                onValueChange = { bank2Account = it },
                label = { Text("Banco 2 - Número de Cuenta") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = configFieldColors()
            )

            OutlinedTextField(
                value = accountHolder,
                onValueChange = { accountHolder = it },
                label = { Text("Nombre del Titular de las Cuentas") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFFF59E0B)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = configFieldColors()
            )

            // ── Tarifas y Precios de Membresía ──────────────────────────────
            ConfigSectionTitle("Precios de Suscripción ($currencySymbol)")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = priceDaily,
                    onValueChange = { priceDaily = it },
                    label = { Text("Diario") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = configFieldColors()
                )
                OutlinedTextField(
                    value = priceWeekly,
                    onValueChange = { priceWeekly = it },
                    label = { Text("Semanal") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = configFieldColors()
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = priceQuincenal,
                    onValueChange = { priceQuincenal = it },
                    label = { Text("Quincenal") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = configFieldColors()
                )
                OutlinedTextField(
                    value = priceMonthly,
                    onValueChange = { priceMonthly = it },
                    label = { Text("Mensual") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = configFieldColors()
                )
            }

            // ── Geocerca ───────────────────────────────────────────────────
            ConfigSectionTitle("Radio de Búsqueda / Geocerca (Km)")
            OutlinedTextField(
                value = searchRadiusKm,
                onValueChange = { searchRadiusKm = it },
                label = { Text("Radio de cobertura en Kilómetros (ej. 5.0 o 15.0)") },
                leadingIcon = { Icon(Icons.Default.Radar, contentDescription = null, tint = Color(0xFF38BDF8)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = configFieldColors()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // ── Botón Guardar Cambios ───────────────────────────────────────
            Button(
                onClick = {
                    isSaving = true
                    scope.launch {
                        val newCfg = config.copy(
                            adminWhatsapp = whatsappPhone.trim(),
                            adminWhatsappPhone = whatsappPhone.trim(),
                            bank1Name = bank1Name.trim(),
                            bank1Account = bank1Account.trim(),
                            banruralAccount = bank1Account.trim(),
                            bank2Name = bank2Name.trim(),
                            bank2Account = bank2Account.trim(),
                            biAccount = bank2Account.trim(),
                            accountHolderName = accountHolder.trim(),
                            currencySymbol = currencySymbol.trim(),
                            priceDaily = priceDaily.toDoubleOrNull() ?: 10.0,
                            priceWeekly = priceWeekly.toDoubleOrNull() ?: 25.0,
                            priceSemanal = priceWeekly.toDoubleOrNull() ?: 25.0,
                            priceQuincenal = priceQuincenal.toDoubleOrNull() ?: 45.0,
                            priceMonthly = priceMonthly.toDoubleOrNull() ?: 80.0,
                            priceMensual = priceMonthly.toDoubleOrNull() ?: 80.0,
                            searchRadiusKm = searchRadiusKm.toDoubleOrNull() ?: 5.0,
                            maxSearchRadiusKm = searchRadiusKm.toDoubleOrNull() ?: 5.0
                        )
                        val res = adminRepo.updateAppConfig(newCfg)
                        isSaving = false
                        if (res.isSuccess) {
                            Toast.makeText(context, "Configuración actualizada en vivo", Toast.LENGTH_LONG).show()
                            loadConfig()
                        } else {
                            Toast.makeText(context, "Error al guardar configuración", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color(0xFF0F172A), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Save, contentDescription = null, tint = Color(0xFF0F172A))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Guardar Configuración Comercial",
                        color = Color(0xFF0F172A),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ConfigSectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFFF59E0B)
    )
}

@Composable
private fun configFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color(0xFF1E293B),
    unfocusedContainerColor = Color(0xFF1E293B),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = Color(0xFFF59E0B),
    unfocusedBorderColor = Color(0xFF334155),
    focusedLabelColor = Color(0xFFF59E0B),
    unfocusedLabelColor = Color(0xFF94A3B8)
)

private fun testWhatsAppNumber(context: Context, rawPhone: String) {
    try {
        val phone = rawPhone.replace(Regex("[^0-9]"), "")
        val formatted = if (phone.startsWith("502")) phone else "502$phone"
        val uri = Uri.parse("https://wa.me/$formatted?text=Hola,%20mensaje%20de%20prueba%20TucTuc%20Map")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "No se pudo abrir WhatsApp", Toast.LENGTH_SHORT).show()
    }
}

