package com.trackgas.app.tracking

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TrackerPagerUiTest {
    @get:Rule val rule = createComposeRule()
    private var tab by mutableStateOf(TrackerTab.DASHBOARD)

    @Test fun tapsPartialReversedAndBoundarySwipesKeepSettledTabAndIndependentScroll() {
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            MaterialTheme {
                TrackerPager(tab, { tab = it }, {}) { page, scroll ->
                    LazyColumn(Modifier.fillMaxSize().testTag(page.name), state = scroll) {
                        items(100) { Text("${page.name} row $it") }
                    }
                }
            }
        }
        rule.onNodeWithTag("DASHBOARD").performScrollToIndex(30)
        rule.onNodeWithText("History").performClick()
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(TrackerTab.HISTORY, tab) }
        rule.onNodeWithTag("HISTORY").performScrollToIndex(60)
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("HISTORY row 60").assertIsDisplayed()
        rule.onNodeWithTag("HISTORY").performTouchInput { swipeRight() }
        rule.runOnIdle { assertEquals(TrackerTab.DASHBOARD, tab) }
        rule.onNodeWithText("DASHBOARD row 30").assertIsDisplayed()
        rule.onNodeWithTag("DASHBOARD").performTouchInput {
            down(center)
            moveTo(center + Offset(-width * .15f, 0f), 400)
            moveTo(center, 400)
            up()
        }
        rule.runOnIdle { assertEquals(TrackerTab.DASHBOARD, tab) }
        rule.onNodeWithTag("DASHBOARD").performTouchInput { swipeRight() }
        rule.runOnIdle { assertEquals(TrackerTab.DASHBOARD, tab) }
        repeat(3) {
            rule.onNodeWithText("History").performClick()
            rule.onNodeWithText("Dashboard").performClick()
        }
        rule.runOnIdle { assertEquals(TrackerTab.DASHBOARD, tab) }
        rule.onNodeWithTag("DASHBOARD").performTouchInput { swipeLeft() }
        rule.runOnIdle { assertEquals(TrackerTab.HISTORY, tab) }
        rule.onNodeWithText("HISTORY row 60").assertIsDisplayed()
        // A fresh session wins over the restored pager's last History page.
        rule.runOnIdle { tab = TrackerTab.DASHBOARD }
        restoration.emulateSavedInstanceStateRestore()
        rule.runOnIdle { assertEquals(TrackerTab.DASHBOARD, tab) }
        rule.onNodeWithTag("DASHBOARD").assertIsDisplayed()
    }
}
