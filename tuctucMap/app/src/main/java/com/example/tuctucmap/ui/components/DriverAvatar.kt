package com.example.tuctucmap.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

private val avatarMemoryCache = mutableMapOf<String, Bitmap>()

@Composable
fun DriverAvatar(
    photoUrl: String?,
    name: String,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(photoUrl) { mutableStateOf<Bitmap?>(photoUrl?.let { avatarMemoryCache[it] }) }

    LaunchedEffect(photoUrl) {
        if (!photoUrl.isNullOrBlank() && bitmap == null) {
            withContext(Dispatchers.IO) {
                try {
                    val conn = (URL(photoUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 8_000
                        readTimeout = 10_000
                        doInput = true
                    }
                    conn.connect()
                    val stream: InputStream = conn.inputStream
                    val decoded = BitmapFactory.decodeStream(stream)
                    stream.close()
                    conn.disconnect()
                    if (decoded != null) {
                        avatarMemoryCache[photoUrl] = decoded
                        withContext(Dispatchers.Main) {
                            bitmap = decoded
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    val initial = (name.trim().firstOrNull() ?: 'T').uppercaseChar().toString()

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFF59E0B), Color(0xFFD97706))
                )
            )
            .border(1.5.dp, Color(0xFFF9D423), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Foto de $name",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = initial,
                fontSize = (size.value * 0.42).sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
        }
    }
}

