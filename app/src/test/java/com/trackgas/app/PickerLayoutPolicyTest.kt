package com.trackgas.app

import com.trackgas.app.tracking.TimePickerPresentation
import com.trackgas.app.tracking.timePickerPresentation
import com.trackgas.app.tracking.formatTimePickerInput
import com.trackgas.app.tracking.parseTimePickerInput
import com.trackgas.app.tracking.timePickerInputAfterSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

class PickerLayoutPolicyTest {
    @Test fun verticalClockFitsAtItsInnerBoundary() {
        assertEquals(TimePickerPresentation.VERTICAL, timePickerPresentation(256f, 384f, 1f))
        assertEquals(TimePickerPresentation.INPUT, timePickerPresentation(255f, 384f, 1f))
    }

    @Test fun horizontalClockUsesCappedInnerWidthNotLandscapeWindowWidth() {
        assertEquals(TimePickerPresentation.HORIZONTAL, timePickerPresentation(496f, 256f, 1f))
        assertEquals(TimePickerPresentation.INPUT, timePickerPresentation(495f, 256f, 1f))
        assertEquals(TimePickerPresentation.INPUT, timePickerPresentation(496f, 255f, 1f))
    }

    @Test fun keyboardAndEnlargedFontsUseInputEvenOnWideWindows() {
        assertEquals(TimePickerPresentation.INPUT, timePickerPresentation(512f, 80f, 1f))
        assertEquals(TimePickerPresentation.INPUT, timePickerPresentation(512f, 800f, 2f))
        assertEquals(TimePickerPresentation.INPUT, timePickerPresentation(0f, -100f, 2f))
    }

    @Test fun resizeChoosesEachPresentationFromItsInnerBounds() {
        assertEquals(TimePickerPresentation.VERTICAL, timePickerPresentation(512f, 500f, 1f))
        assertEquals(TimePickerPresentation.HORIZONTAL, timePickerPresentation(512f, 300f, 1f))
        assertEquals(TimePickerPresentation.INPUT, timePickerPresentation(256f, 300f, 1f))
    }

    @Test fun timeInputParsesStrict24HourTimes() {
        assertEquals(LocalTime.MIDNIGHT, parseTimePickerInput("00:00"))
        assertEquals(LocalTime.of(9, 7), parseTimePickerInput("09:07"))
        assertEquals(LocalTime.of(23, 59), parseTimePickerInput("23:59"))
        assertEquals("09:07", formatTimePickerInput(LocalTime.of(9, 7)))
    }

    @Test fun incompleteAndInvalidTimeInputDoesNotNormalizeIntoAnotherTime() {
        listOf("", "09:", "9:07", "24:00", "12:60", "-1:00", "ab:cd", "09:07:00", " 09:07").forEach {
            assertNull(it, parseTimePickerInput(it))
        }
    }

    @Test fun unchangedPickerSelectionPreservesInvalidDraftAcrossPresentations() {
        val selection = LocalTime.of(9, 7)
        listOf("", "09:", "24:00", "not a time").forEach {
            assertEquals(it, timePickerInputAfterSelection(it, "09:07", selection))
        }
    }

    @Test fun validInputAndActualClockChangesStaySynchronized() {
        val typed = requireNotNull(parseTimePickerInput("23:59"))
        assertEquals("23:59", timePickerInputAfterSelection("23:59", "23:59", typed))
        assertEquals("00:05", timePickerInputAfterSelection("23:59", "23:59", LocalTime.of(0, 5)))
        assertEquals("00:05", timePickerInputAfterSelection("invalid", "23:59", LocalTime.of(0, 5)))
    }
}
