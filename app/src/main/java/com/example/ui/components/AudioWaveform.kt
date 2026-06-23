package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun AudioWaveform(
    amplitude: Float,
    style: Int = 0,
    primaryColor: Color,
    tertiaryColor: Color,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth().height(100.dp)) {
        when (style) {
            0 -> LiquidStringWaveform(amplitude, primaryColor, tertiaryColor)
            1 -> AiBlobWaveform(amplitude, primaryColor, tertiaryColor)
            2 -> PixelBarsWaveform(amplitude, primaryColor, tertiaryColor)
        }
    }
}

// ---------------- STYLE 0: LIQUID STRING (Konsep Matematika Lama) ----------------
@Composable
private fun LiquidStringWaveform(amplitude: Float, primaryColor: Color, tertiaryColor: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "liquid_transition")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val centerY = height / 2
        
        // Pake logika core lu: amplitude * height * 0.8f
        val dynamicHeight = if (amplitude > 0f) {
            (amplitude * height * 0.8f).coerceIn(0f, height / 2f)
        } else {
            0f
        }

        val gradient = Brush.linearGradient(listOf(primaryColor, tertiaryColor))

        for (i in 0..2) {
            val path = Path()
            path.moveTo(0f, centerY)

            val waveFrequency = 1.5f + (i * 0.5f) 
            val wavePhase = phase + (i * (PI / 3).toFloat())
            
            val stepSize = 10f
            var x = 0f
            while (x <= width) {
                val normalizedX = x / width
                val edgeEasing = sin(normalizedX * PI).toFloat()
                
                val yOffset = sin((normalizedX * PI * waveFrequency) + wavePhase).toFloat() * (4f + dynamicHeight * (1f - i * 0.2f)) * edgeEasing
                
                path.lineTo(x, centerY + yOffset)
                x += stepSize
            }
            
            drawPath(
                path = path,
                brush = gradient,
                alpha = 0.3f + (0.35f * (2 - i)),
                style = Stroke(width = 4f + (i * 1.5f))
            )
        }
    }
}

// ---------------- STYLE 1: AI BLOB (Konsep Matematika Lama) ----------------
@Composable
private fun AiBlobWaveform(amplitude: Float, primaryColor: Color, tertiaryColor: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "blob_transition")
    val breathing by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathing"
    )
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height / 2)
        val height = size.height
        val baseRadius = 24f
        
        // Pake logika core lu: amplitude * height * 0.8f
        val dynamicBoost = if (amplitude > 0f) {
            (amplitude * height * 0.8f).coerceIn(0f, height)
        } else {
            0f
        }
        
        val gradient = Brush.linearGradient(listOf(primaryColor, tertiaryColor))

        for (i in 0..2) {
            val pulseRadius = (baseRadius + dynamicBoost * (1f - (i * 0.2f))) * breathing
            drawCircle(
                brush = gradient,
                radius = pulseRadius + (i * 12f),
                center = center,
                alpha = 0.15f + (0.1f * i)
            )
        }
        
        drawCircle(
            brush = gradient,
            radius = (baseRadius + dynamicBoost) * breathing,
            center = center
        )
    }
}

// ---------------- STYLE 2: PIXEL BARS (KODINGAN ASLI LU 100%) ----------------
@Composable
private fun PixelBarsWaveform(amplitude: Float, primaryColor: Color, tertiaryColor: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val centerY = height / 2

        val barWidth = 12f
        val gap = 16f
        val numBars = (width / (barWidth + gap)).toInt()

        val gradient = Brush.linearGradient(
            colors = listOf(primaryColor, tertiaryColor)
        )

        for (i in 0 until numBars) {
            val x = i * (barWidth + gap)
            
            // INI EXACTLY RUMUS LAMA LU YANG JALAN
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
