package com.trackgas.app.tracking

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.point
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.common.insets
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import com.patrykandpatrick.vico.core.cartesian.Zoom
import com.patrykandpatrick.vico.core.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.core.cartesian.axis.BaseAxis
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.ColumnCartesianLayerModel
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.core.common.DrawingContext
import com.patrykandpatrick.vico.core.common.Fill
import com.patrykandpatrick.vico.core.common.component.LineComponent
import com.patrykandpatrick.vico.core.common.data.ExtraStore
import com.patrykandpatrick.vico.core.common.shape.CorneredShape
import com.trackgas.app.R
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun ConsumptionChart(
    period: CalendarPeriod,
    dashboard: Dashboard,
    temperatures: Map<java.time.LocalDate, BigDecimal>,
    locale: Locale,
    averageTemperature: BigDecimal?,
    modifier: Modifier = Modifier,
) {
    val projection = chartProjection(period, dashboard, temperatures)
    if (projection.entries.isEmpty()) return

    val colors = MaterialTheme.colorScheme
    ProvideVicoTheme(
        rememberM3VicoTheme(
            columnCartesianLayerColors = listOf(colors.primary),
            lineColor = colors.outlineVariant,
            textColor = colors.onSurfaceVariant,
        ),
    ) {
        // A snapshot owns its producer/host. While the new transaction is being collected,
        // no frame may show an old model under the new dates or decimal scale.
        key(projection) {
            Column(modifier) {
                ProjectedConsumptionChart(projection, locale)
                if (projection.temperatures.isNotEmpty()) {
                    averageTemperature?.let { mean ->
                        Text(
                            stringResource(R.string.temperature_mean, numberText(mean, locale)),
                            modifier = Modifier.padding(vertical = 4.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ProjectedTemperatureChart(projection, locale)
                }
            }
        }
    }
}

@Composable
private fun ProjectedConsumptionChart(
    projection: ChartProjection,
    locale: Locale,
) {
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(producer, projection) {
        producer.runTransaction {
            if (projection.entries.isNotEmpty()) {
                columnSeries {
                    series(
                        x = projection.entries.map { it.offset },
                        y = projection.entries.map { it.normalizedY },
                    )
                }
            }
        }
    }
    val bar = rememberLineComponent(fill(MaterialTheme.colorScheme.primary), thickness = 8.dp)
    val zeroFill = fill(MaterialTheme.colorScheme.primary)
    val columns = remember(projection, bar, zeroFill) {
        val zero = BaselineZeroComponent(zeroFill, bar.thicknessDp)
        val zeroOffsets = projection.entries.filter { it.isZero }.map { it.offset }.toSet()
        object : ColumnCartesianLayer.ColumnProvider {
            override fun getColumn(
                entry: ColumnCartesianLayerModel.Entry,
                seriesIndex: Int,
                extraStore: ExtraStore,
            ): LineComponent = if (entry.x.toInt() in zeroOffsets) zero else bar

            override fun getWidestSeriesColumn(seriesIndex: Int, extraStore: ExtraStore) = bar
        }
    }
    val range = remember(projection.positionCount) {
        CartesianLayerRangeProvider.fixed(
            minX = 0.0,
            maxX = (projection.positionCount - 1).toDouble(),
            minY = 0.0,
            maxY = 1.0,
        )
    }
    val dateFormatter = remember(projection.period, locale) {
        CartesianValueFormatter { _, value, _ ->
            val offset = value.roundToInt().coerceIn(0, projection.positionCount - 1)
            val date = if (projection.period.type == PeriodType.YEAR) {
                projection.period.start.plusMonths(offset.toLong())
            } else {
                projection.period.start.plusDays(offset.toLong())
            }
            bucketText(date, projection.period.type, locale)
        }
    }
    val amountFormatter = remember(projection, locale) {
        CartesianValueFormatter { _, value, _ ->
            chartAxisText(projection, value, locale)
        }
    }
    val spacing = when (projection.period.type) {
        PeriodType.WEEK -> 3
        PeriodType.MONTH -> 10
        PeriodType.YEAR -> 3
    }
    val resources = LocalContext.current.resources
    val inspection = rememberInspection(
        chartMarker(projection, locale, DefaultCartesianMarker.LabelPosition.Top, MaterialTheme.colorScheme.primary) { offset ->
            projection.entries.firstOrNull { it.offset == offset }
                ?.let { resources.getString(R.string.volume, numberText(it.cubicMetres, locale)) }
        },
    )
    val startAxis = VerticalAxis.rememberStart(
        valueFormatter = amountFormatter,
        itemPlacer = remember(projection.scale) {
            VerticalAxis.ItemPlacer.count(count = { if (projection.scale.signum() == 0) 1 else 3 })
        },
        size = Y_AXIS_SIZE,
        title = stringResource(R.string.volume, "").trim(),
        titleComponent = rememberAxisLabelComponent(),
        tick = null,
        line = null,
    )
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                columnProvider = columns,
                columnCollectionSpacing = 4.dp,
                rangeProvider = range,
            ),
            startAxis = startAxis,
            bottomAxis = HorizontalAxis.rememberBottom(
                valueFormatter = dateFormatter,
                itemPlacer = remember(spacing) {
                    HorizontalAxis.ItemPlacer.aligned(spacing = { spacing })
                },
                guideline = null,
                tick = null,
            ),
            marker = inspection.marker,
            getXStep = { 1.0 },
        ),
        modelProducer = producer,
        modifier = Modifier.height(220.dp).then(inspection.modifier),
        scrollState = rememberVicoScrollState(scrollEnabled = false),
        zoomState = rememberVicoZoomState(
            zoomEnabled = false,
            initialZoom = Zoom.Content,
            minZoom = Zoom.Content,
            maxZoom = Zoom.Content,
        ),
        animationSpec = null,
        animateIn = false,
    )
}

/** Builds a long-press tooltip with the bucket date and [details] for the touched x offset. */
@Composable
private fun chartMarker(
    projection: ChartProjection,
    locale: Locale,
    labelPosition: DefaultCartesianMarker.LabelPosition,
    accent: Color,
    details: (Int) -> String?,
): DefaultCartesianMarker {
    val colors = MaterialTheme.colorScheme
    val resources = LocalContext.current.resources
    val valueFormatter = remember(projection, locale, resources) {
        DefaultCartesianMarker.ValueFormatter { _, targets ->
            val offset = targets.firstOrNull()?.x?.roundToInt()
            if (offset == null) {
                ""
            } else {
                val date = if (projection.period.type == PeriodType.YEAR) {
                    projection.period.start.plusMonths(offset.toLong())
                } else {
                    projection.period.start.plusDays(offset.toLong())
                }
                resources.getString(
                    R.string.chart_marker,
                    bucketText(date, projection.period.type, locale, padDay = true),
                    details(offset).orEmpty(),
                )
            }
        }
    }
    return rememberDefaultCartesianMarker(
        label = rememberTextComponent(
            color = MaterialTheme.colorScheme.onPrimary,
            textSize = MaterialTheme.typography.labelLarge.fontSize,
            lineCount = 2,
            padding = insets(horizontal = 12.dp, vertical = 8.dp),
            background = rememberShapeComponent(
                fill = fill(accent),
                shape = CorneredShape.rounded(8f),
            ),
        ),
        valueFormatter = valueFormatter,
        labelPosition = labelPosition,
        guideline = rememberLineComponent(fill(accent.copy(alpha = 0.35f)), thickness = 1.dp),
    )
}

/** A marker that only draws while a long press is held, plus the gesture modifier driving it. */
private class Inspection(val marker: CartesianMarker, val modifier: Modifier)

@Composable
private fun rememberInspection(marker: DefaultCartesianMarker): Inspection {
    var inspecting by remember { mutableStateOf(false) }
    val inspectionMarker = remember(marker) {
        object : CartesianMarker by marker {
            override fun drawOverLayers(context: CartesianDrawingContext, targets: List<CartesianMarker.Target>) {
                if (inspecting) marker.drawOverLayers(context, targets)
            }
        }
    }
    val gesture = Modifier.pointerInput(Unit) {
        detectDragGesturesAfterLongPress(
            onDragStart = { inspecting = true },
            onDragEnd = { inspecting = false },
            onDragCancel = { inspecting = false },
            onDrag = { change, _ -> change.consume() },
        )
    }
    return Inspection(inspectionMarker, gesture)
}

internal fun temperatureSegments(
    entries: List<TemperatureChartEntry>,
): List<List<TemperatureChartEntry>> {
    val segments = mutableListOf<MutableList<TemperatureChartEntry>>()
    entries.forEach { entry ->
        val current = segments.lastOrNull()
        if (current == null || current.last().offset + 1 != entry.offset) {
            segments += mutableListOf(entry)
        } else {
            current += entry
        }
    }
    return segments
}

/** Separate, shorter chart so the temperature scale is never visually compared with consumption bars. */
@Composable
private fun ProjectedTemperatureChart(projection: ChartProjection, locale: Locale) {
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(producer, projection) {
        producer.runTransaction {
            temperatureSegments(projection.temperatures).forEach { segment ->
                lineSeries {
                    series(x = segment.map { it.offset }, y = segment.map { it.celsius })
                }
            }
        }
    }
    val color = MaterialTheme.colorScheme.tertiary
    val line = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(fill(color)),
        pointProvider = LineCartesianLayer.PointProvider.single(
            LineCartesianLayer.point(rememberShapeComponent(fill(color), CorneredShape.Pill), size = 6.dp),
        ),
    )
    val context = LocalContext.current
    val formatter = remember(locale, context) {
        CartesianValueFormatter { _, value, _ ->
            context.resources.getString(R.string.temperature_axis, numberText(BigDecimal.valueOf(value), locale))
        }
    }
    val values = projection.temperatures.map { it.celsius.toDouble() }
    val min = values.min()
    val max = values.max()
    val padding = if (min == max) 1.0 else (max - min) * 0.1
    val range = remember(projection) {
        CartesianLayerRangeProvider.fixed(
            minX = 0.0,
            maxX = (projection.positionCount - 1).toDouble(),
            minY = min - padding,
            maxY = max + padding,
        )
    }
    val inspection = rememberInspection(
        chartMarker(projection, locale, DefaultCartesianMarker.LabelPosition.Top, color) { offset ->
            projection.temperatures.firstOrNull { it.offset == offset }
                ?.let { context.resources.getString(R.string.temperature, numberText(it.celsius, locale)) }
        },
    )
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(line),
                rangeProvider = range,
            ),
            startAxis = VerticalAxis.rememberStart(
                valueFormatter = formatter,
                itemPlacer = VerticalAxis.ItemPlacer.count(count = { 2 }),
                size = Y_AXIS_SIZE,
                title = stringResource(R.string.temperature_axis, "").trim(),
                titleComponent = rememberAxisLabelComponent(),
                tick = null,
                line = null,
            ),
            marker = inspection.marker,
            getXStep = { 1.0 },
        ),
        modelProducer = producer,
        modifier = Modifier.height(110.dp).then(inspection.modifier),
        scrollState = rememberVicoScrollState(scrollEnabled = false),
        zoomState = rememberVicoZoomState(
            zoomEnabled = false,
            initialZoom = Zoom.Content,
            minZoom = Zoom.Content,
            maxZoom = Zoom.Content,
        ),
        animationSpec = null,
        animateIn = false,
    )
}

/** Same width for both charts' value axes so their plot areas line up. */
private val Y_AXIS_SIZE = BaseAxis.Size.Fixed(56f)

/** A horizontal baseline dash, not a fictitious positive-height consumption bar. */
private class BaselineZeroComponent(fill: Fill, thicknessDp: Float) : LineComponent(fill, thicknessDp) {
    override fun drawVertical(
        context: DrawingContext,
        x: Float,
        top: Float,
        bottom: Float,
        thicknessFactor: Float,
    ) {
        val halfWidth = with(context) { thicknessDp.pixels } * thicknessFactor / 2
        val stroke = with(context) { 2f.pixels }
        draw(context, x - halfWidth, bottom - stroke, x + halfWidth, bottom)
    }
}

/** Keep extreme axis labels compact; exact localized values remain in the caller's list. */
internal fun chartAxisText(projection: ChartProjection, value: Double, locale: Locale): String =
    chartAmountText(projection.cubicMetresAt(value.coerceIn(0.0, 1.0)), locale)

internal fun chartAmountText(value: BigDecimal, locale: Locale): String =
    if (value.signum() != 0 && (value.abs() >= BigDecimal("1000000") || value.abs() < BigDecimal("0.001"))) {
        DecimalFormat("0.##E0", DecimalFormatSymbols.getInstance(locale)).format(value)
    } else {
        numberText(value, locale)
    }
