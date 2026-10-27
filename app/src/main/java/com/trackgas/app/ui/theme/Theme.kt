package com.trackgas.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal val TrackGasColors = lightColorScheme(
    primary = Color(0xFF17685F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7EEE8),
    onPrimaryContainer = Color(0xFF0E4B43),
    secondary = Color(0xFF52645D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0EADF),
    onSecondaryContainer = Color(0xFF314239),
    tertiary = Color(0xFF356475),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD9ECF3),
    onTertiaryContainer = Color(0xFF163B48),
    background = Color(0xFFF7F6F2),
    onBackground = Color(0xFF1F2C27),
    surface = Color(0xFFF7F6F2),
    onSurface = Color(0xFF1F2C27),
    surfaceVariant = Color(0xFFE2E9E2),
    onSurfaceVariant = Color(0xFF53625C),
    surfaceTint = Color(0xFF17685F),
    inverseSurface = Color(0xFF2D3430),
    inverseOnSurface = Color(0xFFF1F2EC),
    inversePrimary = Color(0xFF9DD9C7),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF77857D),
    outlineVariant = Color(0xFFD9E1DB),
    scrim = Color.Black,
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFDDDCD6),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF3F4EE),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFEEF1EB),
    surfaceContainerHighest = Color(0xFFE4E9E2),
)

private val TrackGasShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
internal fun TrackGasTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TrackGasColors,
        typography = Typography(),
        shapes = TrackGasShapes,
        content = content,
    )
}
