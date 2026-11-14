package com.trackgas.app

import com.trackgas.app.tracking.ChartLayout
import com.trackgas.app.tracking.ChartInspectionMarker
import com.trackgas.app.tracking.chartLayout
import com.trackgas.app.tracking.chartTooltipLeft
import com.trackgas.app.tracking.TemperatureChartEntry
import com.trackgas.app.tracking.temperatureSegments
import com.trackgas.app.tracking.temperatureSeries
import com.patrykandpatrick.vico.compose.cartesian.CartesianMeasuringContext
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.ColumnCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.layer.CartesianLayerDimensions
import com.patrykandpatrick.vico.compose.cartesian.layer.CartesianLayerMargins
import java.lang.reflect.Proxy
import java.math.BigDecimal
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ChartLayoutPolicyTest {
    private fun layout(
        width: Float = 288f,
        positions: Int = 31,
        dateWidth: Float = 32f,
        dateHeight: Float = 22f,
        valueWidth: Float = 42f,
        valueHeight: Float = 22f,
        titleThickness: Float = 22f,
        titleLength: Float = 32f,
    ): ChartLayout = chartLayout(
        width, positions, dateWidth, dateHeight, valueWidth, valueHeight,
        titleThickness, titleLength,
    )

    @Test fun spacingChangesOnlyWhenAnotherFullLabelFits() {
        // 68 dp value axis, 40 dp total edge space; adjacent labels need 40 dp.
        assertEquals(2, layout(width = 347.9f, positions = 7).tickSpacing)
        assertEquals(1, layout(width = 348f, positions = 7).tickSpacing)
        assertEquals(1, layout(width = 348.1f, positions = 7).tickSpacing)
    }

    @Test fun actualContentWidthControlsDensityForEveryPeriod() {
        for (positions in listOf(7, 28, 29, 30, 31, 12)) {
            val narrow = layout(width = 288f, positions = positions)
            val wide = layout(width = 808f, positions = positions)
            assertTrue(narrow.tickSpacing >= wide.tickSpacing)
            for ((width, policy) in listOf(288f to narrow, 808f to wide)) {
                val plotWidth = width - policy.valueAxisWidth - 2f * policy.edgePadding
                assertTrue(policy.tickSpacing * plotWidth / (positions - 1) >= 40f)
            }
        }
    }

    @Test fun doubledFontMeasurementsIncreaseSpaceWithoutScalingDownText() {
        val normal = layout()
        val enlarged = layout(
            dateWidth = 64f, dateHeight = 44f, valueWidth = 84f,
            valueHeight = 44f, titleThickness = 44f, titleLength = 64f,
        )
        assertTrue(enlarged.tickSpacing > normal.tickSpacing)
        assertTrue(enlarged.valueAxisWidth > normal.valueAxisWidth)
        assertTrue(enlarged.edgePadding > normal.edgePadding)
        assertEquals(187f, enlarged.consumptionHeight, 0f)
        assertEquals(108f, enlarged.temperatureHeight, 0f)
        assertTrue(enlarged.temperatureHeight <= enlarged.consumptionHeight - 48f)
    }

    @Test fun widerLocalizedDateLabelsReduceTickDensity() {
        val short = layout(dateWidth = 24f)
        val long = layout(dateWidth = 58f)
        assertTrue(long.tickSpacing > short.tickSpacing)
        assertTrue(long.edgePadding >= 58f / 2f)
    }

    @Test fun longestValueAndRotatedTitleShareOneUnclippedAxisReservation() {
        val policy = layout(valueWidth = 96.25f, titleThickness = 30.5f)
        assertEquals(131f, policy.valueAxisWidth, 0f)
        assertTrue(policy.valueAxisWidth >= 96.25f + 30.5f + 4f)
        assertTrue(policy.tickSpacing >= layout().tickSpacing)
    }

    @Test fun longerDailyRateUnitTitlesFitWithoutChangingSharedDateGeometry() {
        for (fontScale in listOf(1f, 1.5f, 2f)) {
            val absolute = layout(titleLength = 32f * fontScale)
            val rateTitleLength = 72f * fontScale
            val daily = layout(titleLength = rateTitleLength)
            assertEquals(absolute.valueAxisWidth, daily.valueAxisWidth, 0f)
            assertEquals(absolute.edgePadding, daily.edgePadding, 0f)
            assertEquals(absolute.tickSpacing, daily.tickSpacing)
            assertTrue(daily.consumptionHeight - daily.topInset - daily.consumptionBottomInset >= rateTitleLength)
            assertTrue(daily.temperatureHeight - daily.topInset - daily.temperatureBottomInset >= 56f)
        }
    }

    @Test fun crampedAndSinglePositionChartsStillHavePositiveBoundedSpacing() {
        assertEquals(31, layout(width = 0f).tickSpacing)
        assertEquals(1, layout(width = 0f, positions = 1).tickSpacing)
        assertEquals(1, layout(width = 10000f).tickSpacing)
    }

    @Test fun ordinaryChartsRetainTheirCompactHeightHierarchy() {
        val policy = layout()
        assertEquals(180f, policy.consumptionHeight, 0f)
        assertEquals(80f, policy.temperatureHeight, 0f)
        assertTrue(policy.temperatureHeight < policy.consumptionHeight)
    }

    @Test fun compactSizingReducesTheActualPlotAreaNotOnlyTooltipSpace() {
        val policy = layout()
        val consumptionPlot = policy.consumptionHeight - policy.topInset - policy.consumptionBottomInset
        val temperaturePlot = policy.temperatureHeight - policy.topInset - policy.temperatureBottomInset
        assertEquals(40f, 220f - policy.topInset - policy.consumptionBottomInset - consumptionPlot, 0f)
        assertEquals(30f, 110f - policy.topInset - policy.temperatureBottomInset - temperaturePlot, 0f)
        assertTrue(consumptionPlot >= 120f)
        assertTrue(temperaturePlot >= 56f)
    }

    @Test fun axisFontSpaceLeavesUsefulPlotsThroughoutSupportedFontScales() {
        for (height in listOf(22f, 30f, 40f, 44f)) {
            for (positions in listOf(7, 12, 31)) {
                val policy = layout(
                    width = 256f, positions = positions,
                    dateHeight = height, valueHeight = height, titleLength = 56f,
                )
                assertTrue(policy.consumptionHeight - policy.topInset - policy.consumptionBottomInset >= 120f)
                assertTrue(policy.temperatureHeight - policy.topInset - policy.temperatureBottomInset >= 56f)
                assertTrue(policy.temperatureHeight <= policy.consumptionHeight - 48f)
            }
        }
    }

    @Test fun inspectionMarkerCannotReserveSpaceOrExhaustTheTemperatureCanvas() {
        val context = Proxy.newProxyInstance(
            CartesianMeasuringContext::class.java.classLoader,
            arrayOf(CartesianMeasuringContext::class.java),
        ) { _, _, _ -> error("An overlay marker must not measure or reserve tooltip space") }
            as CartesianMeasuringContext
        val dimensions = object : CartesianLayerDimensions {
            override val xSpacing = 1f
            override val scalableStartPadding = 0f
            override val scalableEndPadding = 0f
            override val unscalableStartPadding = 0f
            override val unscalableEndPadding = 0f
        }
        val model = CartesianChartModel(
            ColumnCartesianLayerModel(listOf(listOf(ColumnCartesianLayerModel.Entry(0, 0)))),
        )
        val marker = ChartInspectionMarker { _, _ -> error("Sizing must not inspect a target") }
        for (positions in listOf(7, 12, 31)) {
            val policy = layout(
                width = 256f, positions = positions, dateWidth = 72f, valueWidth = 84f,
                dateHeight = 44f, valueHeight = 44f, titleThickness = 44f,
            )
            val margins = CartesianLayerMargins().apply {
                ensureValuesAtLeast(top = policy.topInset, bottom = policy.temperatureBottomInset)
            }
            marker.updateLayerMargins(context, margins, dimensions, model)
            marker.updateHorizontalLayerMargins(context, margins, policy.temperatureHeight - margins.vertical, model)
            assertEquals(policy.topInset + policy.temperatureBottomInset, margins.vertical, 0f)
            assertTrue(policy.temperatureHeight - margins.vertical >= 56f)
            assertEquals(0f, margins.start, 0f)
            assertEquals(0f, margins.end, 0f)
        }
    }

    @Test fun compactTooltipTracksItsBucketAndStaysInsideChartEdges() {
        assertEquals(100, chartTooltipLeft(320, 80, 140f))
        assertEquals(0, chartTooltipLeft(320, 80, 0f))
        assertEquals(240, chartTooltipLeft(320, 80, 320f))
        assertEquals(0, chartTooltipLeft(320, 320, 200f))
    }

    @Test fun temperatureSegmentsKeepEveryGapAndAllEntries() {
        val entries = listOf(
            TemperatureChartEntry(0, BigDecimal("9.5")),
            TemperatureChartEntry(2, BigDecimal("11.5")),
            TemperatureChartEntry(3, BigDecimal("12.3")),
            TemperatureChartEntry(6, BigDecimal("15.5")),
        )
        assertEquals(
            listOf(listOf(entries[0]), listOf(entries[1], entries[2]), listOf(entries[3])),
            temperatureSegments(entries),
        )
    }

    @Test fun temperatureSegmentsRetainContiguousAndSinglePointRanges() {
        val single = listOf(TemperatureChartEntry(4, BigDecimal("-2.5")))
        val contiguous = listOf(
            TemperatureChartEntry(2, BigDecimal("0")),
            TemperatureChartEntry(3, BigDecimal("2.5")),
            TemperatureChartEntry(4, BigDecimal("4.5")),
        )
        assertEquals(listOf(single), temperatureSegments(single))
        assertEquals(listOf(contiguous), temperatureSegments(contiguous))
    }

    @Test fun temperatureSegmentsAreEmptyWhenThereAreNoEntries() {
        assertEquals(emptyList<List<TemperatureChartEntry>>(), temperatureSegments(emptyList()))
    }

    @Test fun temperatureSeriesTransactionsAcceptDataAndEmptyCoverage() = runTest {
        val producer = CartesianChartModelProducer()
        producer.runTransaction {
            temperatureSeries(listOf(TemperatureChartEntry(0, BigDecimal("12.3"))))
        }
        producer.runTransaction { temperatureSeries(emptyList()) }
    }

    @Test fun veryLargeMeasurementsStayBoundedWithoutInvertingHierarchy() {
        val policy = layout(
            dateHeight = 200f, valueHeight = 200f, titleLength = 900f,
        )
        assertEquals(400f, policy.consumptionHeight, 0f)
        assertEquals(260f, policy.temperatureHeight, 0f)
    }

    @Test fun fractionalLabelMeasurementsRoundReservationsUp() {
        val policy = layout(dateWidth = 33.25f, valueWidth = 42.1f, titleThickness = 22.1f)
        assertEquals(21f, policy.edgePadding, 0f)
        assertEquals(69f, policy.valueAxisWidth, 0f)
    }

    @Test fun invalidMeasurementsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { layout(positions = 0) }
        assertThrows(IllegalArgumentException::class.java) { layout(width = Float.POSITIVE_INFINITY) }
        assertThrows(IllegalArgumentException::class.java) { layout(dateWidth = -1f) }
        assertThrows(IllegalArgumentException::class.java) { layout(valueHeight = Float.NaN) }
    }
}
