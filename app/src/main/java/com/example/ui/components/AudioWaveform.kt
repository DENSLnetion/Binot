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
import kotlin.math.sin

@Composable
fun AudioWaveform(
    amplitude: Float,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    
    // BOOST SENSITIVITAS: Dikali 2.5 biar mode accurate yang suaranya kecil tetep ngangkat, dilimit mentok di 1f
    val boostedAmplitude = (amplitude * 2.5f).coerceIn(0f, 1f)

    // Spring animation untuk fluiditas
    val animatedAmplitude by animateFloatAsState(
        targetValue = boostedAmplitude,
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

        // 5 batang ekspresif
        val numBars = 5
        val barWidth = 36.dp.toPx() 
        val gap = 12.dp.toPx()
        
        // Kalkulasi posisi sentral
        val totalWaveWidth = (numBars * barWidth) + ((numBars - 1) * gap)
        val startX = (canvasWidth - totalWaveWidth) / 2f

        val gradient = Brush.linearGradient(
            colors = listOf(primary, tertiary)
        )

        // Bobot asimetris biar gak kaku numpuk persis di tengah
        val weightMultipliers = listOf(0.5f, 0.9f, 1.0f, 0.8f, 0.6f)

        for (i in 0 until numBars) {
            val x = startX + (i * (barWidth + gap))
            val baseHeight = 16.dp.toPx() 
            
            // VARIASI ORGANIK: Pake fungsi sin biar masing-masing batang punya goyangan unik waktu ada suara
            val variation = if (animatedAmplitude > 0.05f) {
                (sin(i * 1.5f + animatedAmplitude * 10f) * 0.15f) + 0.85f // Hasilin angka sekitar 0.7 - 1.0
            } else {
                1f
            }
            
            val dynamicHeight = if (animatedAmplitude > 0f) {
                baseHeight + (animatedAmplitude * (canvasHeight - baseHeight) * weightMultipliers[i] * variation.toFloat())
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
