package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import kotlin.math.sin

@Composable
fun AudioWaveform(
    amplitude: Float,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    
    Canvas(modifier = modifier
        .fillMaxWidth()
        .height(100.dp)) {
        val width = size.width
        val height = size.height
        val centerY = height / 2

        val barWidth = 12f
        val gap = 16f
        val numBars = (width / (barWidth + gap)).toInt()

        val gradient = Brush.linearGradient(
            colors = listOf(primary, tertiary)
        )

        for (i in 0 until numBars) {
            val x = i * (barWidth + gap)
            
            val dynamicHeight = if (amplitude > 0f) {
                val baseHeight = (sin(i.toFloat() * 0.5f) * 10f) + 15f
                (baseHeight + (amplitude * height * 0.8f * Math.random().toFloat())).coerceIn(10f, height)
            } else {
                4f 
            }
            
            val yOffset = centerY - (dynamicHeight / 2)

            drawRoundRect(
                brush = gradient,
                topLeft = Offset(x, yOffset),
                size = Size(barWidth, dynamicHeight),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
            )
        }
    }
}

