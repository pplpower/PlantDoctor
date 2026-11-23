package com.plantdoctor.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.plantdoctor.app.data.HealthStatus

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8E6C9),
    onPrimaryContainer = Color(0xFF0B3D0F),
    secondary = Color(0xFF6D8B3A),
    secondaryContainer = Color(0xFFE3EDC8),
    tertiary = Color(0xFF8D6E63),
    background = Color(0xFFF7FBF2),
    surface = Color(0xFFF7FBF2),
    surfaceVariant = Color(0xFFE6EEDF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FD694),
    onPrimary = Color(0xFF0B3D0F),
    primaryContainer = Color(0xFF1F5A23),
    onPrimaryContainer = Color(0xFFC8E6C9),
    secondary = Color(0xFFB7CC8A),
    secondaryContainer = Color(0xFF3B4A20),
    tertiary = Color(0xFFD7B8AC),
    background = Color(0xFF111611),
    surface = Color(0xFF111611),
    surfaceVariant = Color(0xFF2A3328),
)

@Composable
fun PlantTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}

fun HealthStatus.color(): Color = when (this) {
    HealthStatus.HEALTHY -> Color(0xFF2E7D32)
    HealthStatus.ATTENTION -> Color(0xFFF9A825)
    HealthStatus.SICK -> Color(0xFFEF6C00)
    HealthStatus.CRITICAL -> Color(0xFFC62828)
    HealthStatus.UNKNOWN -> Color(0xFF757575)
}

fun severityColor(severity: String): Color = when {
    severity.contains("выс", ignoreCase = true) -> Color(0xFFC62828)
    severity.contains("сред", ignoreCase = true) -> Color(0xFFEF6C00)
    else -> Color(0xFFF9A825)
}
