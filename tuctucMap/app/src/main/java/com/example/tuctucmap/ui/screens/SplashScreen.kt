package com.example.tuctucmap.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tuctucmap.R
import com.example.tuctucmap.ui.theme.TuctucMapTheme

@Composable
fun SplashScreen(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFDFE4E8)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(0.9f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(80.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF233941),
                            Color(0xFF1B2D34)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(80.dp)
                )
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_tuc_tuc),
                    contentDescription = null,
                    modifier = Modifier
                        .size(240.dp)
                        .padding(bottom = 12.dp)
                )

                Text(
                    text = stringResource(id = R.string.app_name),
                    color = Color.White,
                    fontSize = 52.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                )
            }

            SparkleIcon(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(32.dp)
                    .size(36.dp)
            )
        }
    }
}

@Composable
fun SparkleIcon(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val path = Path().apply {
            moveTo(size.width / 2, 0f)
            quadraticTo(size.width / 2, size.height / 2, size.width, size.height / 2)
            quadraticTo(size.width / 2, size.height / 2, size.width / 2, size.height)
            quadraticTo(size.width / 2, size.height / 2, 0f, size.height / 2)
            quadraticTo(size.width / 2, size.height / 2, size.width / 2, 0f)
        }
        drawPath(path, color = Color.White.copy(alpha = 0.4f))
    }
}

@Preview(showBackground = true)
@Composable
fun SplashScreenPreview() {
    TuctucMapTheme {
        SplashScreen()
    }
}
