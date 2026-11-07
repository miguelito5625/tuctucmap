package com.example.tuctucmap.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun CompleteDriverGoogleRegistrationDialog(
    initialName: String,
    googleEmail: String,
    onDismiss: () -> Unit,
    onConfirm: (nombre: String, telefono: String, placas: String, numeroUnidad: String) -> Unit,
    isLoading: Boolean = false,
    errorMessage: String? = null
) {
    var nombre by remember { mutableStateOf(initialName) }
    var telefono by remember { mutableStateOf("") }
    var placas by remember { mutableStateOf("") }
    var numeroUnidad by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .imePadding(),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1E293B),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🛺 Completa tu Registro",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF9D423),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Cuenta de Google conectada:\n$googleEmail",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Nombre
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it; localError = null },
                    label = { Text("Nombre Completo") },
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF94A3B8))
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFF9D423),
                        unfocusedBorderColor = Color(0xFF475569),
                        focusedLabelColor = Color(0xFFF9D423),
                        unfocusedLabelColor = Color(0xFF94A3B8)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Teléfono
                OutlinedTextField(
                    value = telefono,
                    onValueChange = { telefono = it; localError = null },
                    label = { Text("Teléfono / WhatsApp *") },
                    leadingIcon = {
                        Icon(Icons.Default.Phone, contentDescription = null, tint = Color(0xFF94A3B8))
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFF9D423),
                        unfocusedBorderColor = Color(0xFF475569),
                        focusedLabelColor = Color(0xFFF9D423),
                        unfocusedLabelColor = Color(0xFF94A3B8)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Placas
                OutlinedTextField(
                    value = placas,
                    onValueChange = { placas = it.uppercase(); localError = null },
                    label = { Text("Placa del Tuctuct * (Ej: M-123XYZ)") },
                    leadingIcon = {
                        Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = Color(0xFF94A3B8))
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFF9D423),
                        unfocusedBorderColor = Color(0xFF475569),
                        focusedLabelColor = Color(0xFFF9D423),
                        unfocusedLabelColor = Color(0xFF94A3B8)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Número de Unidad (Opcional)
                OutlinedTextField(
                    value = numeroUnidad,
                    onValueChange = { numeroUnidad = it },
                    label = { Text("Número de Unidad (Opcional)") },
                    leadingIcon = {
                        Icon(Icons.Default.Numbers, contentDescription = null, tint = Color(0xFF94A3B8))
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFF9D423),
                        unfocusedBorderColor = Color(0xFF475569),
                        focusedLabelColor = Color(0xFFF9D423),
                        unfocusedLabelColor = Color(0xFF94A3B8)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                val displayError = localError ?: errorMessage
                if (!displayError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = displayError,
                        color = Color(0xFFFF6B6B),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Acciones
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isLoading,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8))
                    ) {
                        Text("Cancelar")
                    }

                    Button(
                        onClick = {
                            if (nombre.isBlank() || telefono.isBlank() || placas.isBlank()) {
                                localError = "Completa tu nombre, teléfono y número de placa"
                                return@Button
                            }
                            onConfirm(nombre.trim(), telefono.trim(), placas.trim(), numeroUnidad.trim())
                        },
                        enabled = !isLoading,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF9D423),
                            contentColor = Color(0xFF0F2027)
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF0F2027)
                            )
                        } else {
                            Text("Guardar", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

