package com.trackgas.app.tracking

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReadingFormLayoutUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun compactHeightAndDoubleFontsKeepFieldsErrorsAndActionsScrollReachable() {
        var saved = 0
        var cancelled = 0
        rule.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                screenHeightDp = 360
                screenWidthDp = 360
                fontScale = 2f
            }
            CompositionLocalProvider(
                LocalConfiguration provides configuration,
                LocalDensity provides Density(LocalDensity.current.density, 2f),
            ) {
                MaterialTheme {
                    ReadingFormLayout(
                        editing = false, displayedDate = "October 26, 2026", value = "100.0100",
                        errors = ReadingErrors(value = ReadingError.BELOW_PREVIOUS),
                        pending = false, failed = false, loadState = ReadingLoadState.READY,
                        onValueChange = {}, onChooseDate = {}, onRetryLoad = {},
                        onSave = { saved++ }, onDismiss = { cancelled++ },
                    )
                }
            }
        }
        rule.onNode(hasSetTextAction()).performScrollTo().performClick()
        rule.onNodeWithText("Save").performScrollTo().assertIsDisplayed().performClick()
        rule.onNodeWithText("Cancel").performScrollTo().assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(1, saved); assertEquals(1, cancelled) }
    }
}
