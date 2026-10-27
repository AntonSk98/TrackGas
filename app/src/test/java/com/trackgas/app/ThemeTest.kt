package com.trackgas.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.trackgas.app.ui.theme.TrackGasColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTest {
    @Test fun lightBackgroundAndWhiteCardsReplaceNearBlackSurfaces() {
        with(TrackGasColors) {
            assertTrue(background.luminance() > 0.8f)
            assertEquals(Color.White, surfaceContainer)
            assertTrue(primary.luminance() < 0.3f)
        }
    }

    @Test fun textRolesMeetNormalTextContrast() {
        with(TrackGasColors) {
            val pairs = listOf(
                onBackground to background,
                onSurface to surface,
                onSurface to surfaceContainer,
                onSurface to surfaceContainerHigh,
                onSurfaceVariant to surfaceContainer,
                onSurfaceVariant to surfaceContainerLow,
                onSurfaceVariant to surfaceContainerHigh,
                primary to surface,
                primary to surfaceContainer,
                onPrimary to primary,
                onSecondaryContainer to secondaryContainer,
                onPrimaryContainer to primaryContainer,
                onTertiaryContainer to tertiaryContainer,
                error to surfaceContainerHigh,
                onError to error,
                onErrorContainer to errorContainer,
            )
            pairs.forEach { (text, surface) ->
                assertTrue("Insufficient contrast for $text on $surface", contrast(text, surface) >= 4.5f)
            }
        }
    }

    private fun contrast(first: Color, second: Color): Float {
        val a = first.luminance()
        val b = second.luminance()
        return (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
    }
}
