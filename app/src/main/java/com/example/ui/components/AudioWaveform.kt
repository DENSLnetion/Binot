package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay

@Composable
fun AudioWaveform(
    amplitude: Float,
    style: Int = 0, // Diterima dari Setting via RecordScreen (0=Liquid, 1=Blob, 2=Bars)
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    // Smoother inersia fisik untuk menangani lonjakan mic yang kasar
    val smoothedAmp by animateFloatAsState(
        targetValue = (amplitude / 32767f).coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 150f),
        label = "smoothedAmp"
    )

    Box(modifier = modifier.fillMaxWidth().height(100.dp)) {
        when (style) {
            0 -> LiquidStringWaveform(smoothedAmp, color)
            1 -> AiBlobWaveform(smoothedAmp, color)
            2 -> PixelBarsWaveform(smoothedAmp, color)
        }
    }
}

// ---------------- STYLE 0: LIQUID STRING (Garis Gelombang Organik) ----------------
@Composable
private fun LiquidStringWaveform(amplitude: Float, color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "liquid_transition")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val centerY = size.height / 2
        val width = size.width
        val baseHeight = 4f
        val maxAmpHeight = size.height / 2f

        // Menggambar 3 lapisan garis dengan kecepatan dan ketinggian berbeda
        for (i in 0..2) {
            val path = Path()
            path.moveTo(0f, centerY)

            val waveFrequency = 1.5f + (i * 0.5f) 
            val wavePhase = phase + (i * (PI / 3).toFloat())
            val ampMultiplier = 1f - (i * 0.25f)
            
            val stepSize = 10f
            var x = 0f
            while (x <= width) {
                val normalizedX = x / width
                // Efek "easing" di tepi biar kurva halus ke tengah
                val edgeEasing = sin(normalizedX * PI).toFloat()
                
                val yOffset = sin((normalizedX * PI * waveFrequency) + wavePhase).toFloat() * (baseHeight + (amplitude * maxAmpHeight * ampMultiplier)) * edgeEasing
                
                path.lineTo(x, centerY + yOffset)
                x += stepSize
            }
            
            drawPath(
                path = path,
                color = color.copy(alpha = 0.3f + (0.35f * (2 - i))),
                style = Stroke(width = 4f + (i * 1.5f))
            )
        }
    }
}

// ---------------- STYLE 1: AI BLOB (Lingkaran Morphing Dinamis) ----------------
@Composable
private fun AiBlobWaveform(amplitude: Float, color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "blob_transition")
    val breathing by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathing"
    )
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height / 2)
        val baseRadius = 24f
        
        // Menggambar lapisan aura di belakang inti (Blob berlapis)
        for (i in 0..2) {
            // Reaksi radius dari amplitude yang memantul dan bernapas
            val pulseRadius = baseRadius + (amplitude * 80f * (3 - i)) * breathing
            drawCircle(
                color = color.copy(alpha = 0.15f + (0.1f * i)),
                radius = pulseRadius + (i * 12f),
                center = center
            )
        }
        
        // Inti bola energi yang reaktif kuat terhadap suara
        drawCircle(
            color = color,
            radius = baseRadius + (amplitude * 24f) * breathing,
            center = center
        )
    }
}

// ---------------- STYLE 2: PIXEL BARS (Batang Antrean Fisik ala Pixel) ----------------
@Composable
private fun PixelBarsWaveform(amplitude: Float, color: Color) {
    val barCount = 42
    var history by remember { mutableStateOf(List(barCount) { 0f }) }
    
    // rememberUpdatedState menjaga agar while-loop mendapatkan nilai amplitude terbaru tanpa me-restart LaunchedEffect
    val currentAmp by rememberUpdatedState(amplitude)
    
    LaunchedEffect(Unit) {
        while (true) {
            // Geser array ke kanan (history queue) setiap frame waktu tertentu
            history = listOf(currentAmp) + history.take(barCount - 1)
            delay(40) // Memberikan FPS halus dan *decay* organik
        }
    }
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        val barWidth = size.width / (barCount * 1.6f)
        val gap = barWidth * 0.6f
        val centerY = size.height / 2
        
        history.forEachIndexed { index, amp ->
            // Menghitung dari tengah atau dari kanan ke kiri
            val x = size.width - (index * (barWidth + gap)) - barWidth
            if (x < 0) return@forEachIndexed
            
            // Batas minimal ketinggian (idle statis)
            val height = 8f + (amp * size.height * 0.85f)
            
            drawRoundRect(
                color = color.copy(alpha = 1f - (index.toFloat() / barCount)),
                topLeft = Offset(x, centerY - height / 2),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2)
            )
        }
    }
}
