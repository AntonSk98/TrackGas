package com.trackgas.app.tracking

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import java.time.LocalDate
import java.util.Locale
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

    @Test fun replacementAt360DpAndDoubleFontsKeepsReadOnlyDateStorageErrorAndActionsReachable() {
        var confirmed = 0
        var cancelled = 0
        val date = LocalDate.of(2026, 10, 28)
        val reading = Reading("final", date, "1105.0000".toBigDecimal())
        rule.setContent {
            val base = LocalConfiguration.current
            val configuration = Configuration(base).apply { screenWidthDp = 360; fontScale = 2f }
            val density = LocalDensity.current.density * base.screenWidthDp / 360f
            CompositionLocalProvider(
                LocalConfiguration provides configuration,
                LocalDensity provides Density(density, 2f),
            ) {
                MaterialTheme {
                    ReplacementForm(
                        dialog = ReadingDialog.Replacing(
                            "replacement", ReplacementTarget(date, reading.installationId, reading),
                            ReplacementPhase.Failed(ReadingFailure(ReadingFailureCategory.STORAGE_ACCESS, ReadingAction.REPLACE)),
                        ),
                        locale = Locale.US, loadState = ReadingLoadState.READY,
                        onRetryLoad = {},
                        onConfirm = { confirmed++ }, onAddFinal = {},
                        onDismiss = { cancelled++ },
                    )
                }
            }
        }
        rule.onNode(hasText("Date") and hasText("Oct 28, 2026")).performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("Choose date: Oct 28, 2026").assertDoesNotExist()
        rule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        rule.onNodeWithText("1105.0000", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("New meter starts at 0 m³ today.").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Could not replace meter. Try again.")
            .performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Confirm replacement").performScrollTo().assertIsDisplayed().performClick()
        rule.onNodeWithText("Cancel").performScrollTo().assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(1, confirmed); assertEquals(1, cancelled) }
    }
}
