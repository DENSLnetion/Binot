package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

@Composable
fun AudioWaveform(
    amplitude: Float,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    
    // Spring animation untuk fluiditas gerakan batang, mencegah jitter
    val animatedAmplitude by animateFloatAsState(
        targetValue = amplitude,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "smoothAmplitude"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val centerY = canvasHeight / 2f

        // Hardcode ke 5 batang ekspresif
        val numBars = 5
        val barWidth = 36.dp.toPx() 
        val gap = 12.dp.toPx()
        
        // Kalkulasi posisi sentral
        val totalWaveWidth = (numBars * barWidth) + ((numBars - 1) * gap)
        val startX = (canvasWidth - totalWaveWidth) / 2f

        val gradient = Brush.linearGradient(
            colors = listOf(primary, tertiary)
        )

        // Bobot distribusi amplitudo (tengah bereaksi paling ekstrem)
        val weightMultipliers = listOf(0.3f, 0.7f, 1.0f, 0.7f, 0.3f)

        for (i in 0 until numBars) {
            val x = startX + (i * (barWidth + gap))
            
            // Base height ketika diam
            val baseHeight = 16.dp.toPx() 
            
            val dynamicHeight = if (animatedAmplitude > 0f) {
                baseHeight + (animatedAmplitude * (canvasHeight - baseHeight) * weightMultipliers[i])
            } else {
                baseHeight 
            }
            
            val finalHeight = dynamicHeight.coerceIn(baseHeight, canvasHeight)
            val yOffset = centerY - (finalHeight / 2f)

            drawRoundRect(
                brush = gradient,
                topLeft = Offset(x, yOffset),
                size = Size(barWidth, finalHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
