package com.trackgas.app.tracking

import kotlin.math.ceil

/**
 * Shared presentation measurements for aligned consumption and temperature charts.
 * All dimensions are dp; [tickSpacing] is a bucket stride, not a filter on plotted data.
 * Insets describe axis space needs; inspection tooltips do not reserve plot space.
 */
internal data class ChartLayout(
    val tickSpacing: Int,
    val valueAxisWidth: Float,
    val edgePadding: Float,
    val consumptionHeight: Float,
    val temperatureHeight: Float,
    val topInset: Float,
    val consumptionBottomInset: Float,
    val temperatureBottomInset: Float,
)

/**
 * Fits chart axes to the available width without dropping any projected buckets.
 *
 * Callers supply finite, nonnegative text measurements in dp after localization and
 * system font scaling, and a positive [positionCount]. Both charts share the value-axis
 * width and edge padding so corresponding buckets stay horizontally aligned.
 * [titleThickness] and [titleLength] describe the axis title before rotation.
 *
 * The 180/80 dp host baselines keep plots compact after removing Vico's tooltip margins.
 * Heights grow for axis needs within fixed caps, with temperature always at least 48 dp
 * shorter. Keeping these calculations independent of rendering allows boundary testing.
 */
internal fun chartLayout(
    contentWidth: Float,
    positionCount: Int,
    dateLabelWidth: Float,
    dateLabelHeight: Float,
    valueLabelWidth: Float,
    valueLabelHeight: Float,
    titleThickness: Float,
    titleLength: Float,
): ChartLayout {
    require(positionCount > 0)
    require(
        listOf(
            contentWidth, dateLabelWidth, dateLabelHeight, valueLabelWidth,
            valueLabelHeight, titleThickness, titleLength,
        ).all { it.isFinite() && it >= 0f },
    )
    val axisWidth = ceil(valueLabelWidth + titleThickness + 4f)
    val edgePadding = ceil(dateLabelWidth / 2f + 4f)
    val plotWidth = (contentWidth - axisWidth - 2f * edgePadding).coerceAtLeast(1f)
    val spacing = ceil((positionCount - 1) * (dateLabelWidth + 8f) / plotWidth)
        .toInt().coerceIn(1, positionCount)
    // Inspection is a Compose overlay, not a Vico margin updater.
    val top = maxOf(3f, valueLabelHeight / 2f)
    val consumptionBottom = maxOf(dateLabelHeight + 1f, valueLabelHeight / 2f)
    val temperatureBottom = top
    val consumptionHeight = (
        maxOf(120f, titleLength) + top + consumptionBottom
    ).coerceIn(180f, 400f)
    val temperatureHeight = (
        maxOf(56f, titleLength) + top + temperatureBottom
    ).coerceIn(80f, minOf(260f, consumptionHeight - 48f))
    return ChartLayout(
        spacing, axisWidth, edgePadding, consumptionHeight, temperatureHeight,
        top, consumptionBottom, temperatureBottom,
    )
}

/**
 * Centers an inspection bubble on its bucket and clamps it to the chart's edges.
 * Inputs and result are pixels in the same chart-local coordinate space.
 * The caller bounds tooltip width to chart width; an oversized bubble is left-aligned.
 */
internal fun chartTooltipLeft(chartWidth: Int, tooltipWidth: Int, anchorX: Float): Int =
    (anchorX - tooltipWidth / 2f).toInt().coerceIn(0, (chartWidth - tooltipWidth).coerceAtLeast(0))
