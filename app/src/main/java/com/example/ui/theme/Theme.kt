package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val ExpressiveShapes = Shapes(
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(32.dp),
    extraLarge = RoundedCornerShape(48.dp)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006874),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF97F0FF),
    onPrimaryContainer = Color(0xFF001F24),
    secondary = Color(0xFF4A6267),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCDE7EC),
    onSecondaryContainer = Color(0xFF051F23),
    background = Color(0xFFFAFDFD),
    onBackground = Color(0xFF191C1D),
    surface = Color(0xFFFAFDFD),
    onSurface = Color(0xFF191C1D),
    surfaceVariant = Color(0xFFDBE4E6),
    onSurfaceVariant = Color(0xFF3F484A)
)

private val SlateDarkColorScheme = darkColorScheme(
    primary = Color(0xFF4FD8EB),
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF004F58),
    onPrimaryContainer = Color(0xFF97F0FF),
    secondary = Color(0xFFB1CBD0),
    onSecondary = Color(0xFF1C3438),
    secondaryContainer = Color(0xFF334B4F),
    onSecondaryContainer = Color(0xFFCDE7EC),
    background = Color(0xFF202324), // Lighter dark (less black)
    onBackground = Color(0xFFE1E3E3),
    surface = Color(0xFF202324),    // Lighter surface
    onSurface = Color(0xFFE1E3E3),
    surfaceVariant = Color(0xFF3F484A),
    onSurfaceVariant = Color(0xFFBFC8CA)
)

private val AmoledDarkColorScheme = SlateDarkColorScheme.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF111414)
)

@Composable
fun BinotTheme(
    themeMode: Int, // 0 = System, 1 = Light, 2 = Slate Dark, 3 = Amoled Dark
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when(themeMode) {
        1 -> false
        2, 3 -> true
        else -> isSystemDark
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        themeMode == 3 -> AmoledDarkColorScheme
        isDark -> SlateDarkColorScheme
        else -> LightColorScheme
    }
    
    // Override Amoled manually even if dynamic color is on, to ensure pure black
    val finalColorScheme = if (themeMode == 3) {
        colorScheme.copy(
            background = Color.Black,
            surface = Color.Black
        )
    } else {
        colorScheme
    }

    MaterialTheme(
        colorScheme = finalColorScheme,
        typography = Typography,
        shapes = ExpressiveShapes,
        content = content
    )
}
