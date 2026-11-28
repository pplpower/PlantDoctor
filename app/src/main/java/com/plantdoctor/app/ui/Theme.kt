package com.plantdoctor.app.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.plantdoctor.app.data.HealthStatus
import com.plantdoctor.app.data.Severity

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8E6C9),
    onPrimaryContainer = Color(0xFF0B3D0F),
    secondary = Color(0xFF55624C),
    secondaryContainer = Color(0xFFD9E7CB),
    onSecondaryContainer = Color(0xFF131F0D),
    tertiary = Color(0xFF386667),
    tertiaryContainer = Color(0xFFBCEBEC),
    background = Color(0xFFF7FBF2),
    surface = Color(0xFFF7FBF2),
    surfaceVariant = Color(0xFFDFE4D7),
    surfaceContainer = Color(0xFFECEFE6),
    surfaceContainerHigh = Color(0xFFE6E9E1),
    surfaceContainerLow = Color(0xFFF1F5EC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FD694),
    onPrimary = Color(0xFF0B3D0F),
    primaryContainer = Color(0xFF1F5A23),
    onPrimaryContainer = Color(0xFFC8E6C9),
    secondary = Color(0xFFBDCBB0),
    secondaryContainer = Color(0xFF3E4A36),
    onSecondaryContainer = Color(0xFFD9E7CB),
    tertiary = Color(0xFFA0CFD0),
    tertiaryContainer = Color(0xFF1E4E4F),
    background = Color(0xFF10140F),
    surface = Color(0xFF10140F),
    surfaceVariant = Color(0xFF43483F),
    surfaceContainer = Color(0xFF1C211B),
    surfaceContainerHigh = Color(0xFF272B25),
    surfaceContainerLow = Color(0xFF181D17),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
)

/** Material You colors from the wallpaper on Android 12+, our green palette otherwise. */
@Composable
fun PlantTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, shapes = AppShapes, content = content)
}

fun HealthStatus.color(): Color = when (this) {
    HealthStatus.HEALTHY -> Color(0xFF2E9D46)
    HealthStatus.ATTENTION -> Color(0xFFE5A100)
    HealthStatus.SICK -> Color(0xFFEF6C00)
    HealthStatus.CRITICAL -> Color(0xFFD32F2F)
    HealthStatus.UNKNOWN -> Color(0xFF8A8F87)
}

fun Severity.color(): Color = when (this) {
    Severity.HIGH -> Color(0xFFD32F2F)
    Severity.MEDIUM -> Color(0xFFEF6C00)
    Severity.LOW -> Color(0xFFE5A100)
}
