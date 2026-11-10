package com.trackgas.app.tracking

import android.graphics.RectF
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.point
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
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
import com.patrykandpatrick.vico.core.cartesian.layer.CartesianLayerPadding
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.core.common.DrawingContext
import com.patrykandpatrick.vico.core.common.Fill
import com.patrykandpatrick.vico.core.common.MeasuringContext
import com.patrykandpatrick.vico.core.common.component.LineComponent
import com.patrykandpatrick.vico.core.common.component.TextComponent
import com.patrykandpatrick.vico.core.common.data.CacheStore
import com.patrykandpatrick.vico.core.common.data.ExtraStore
import com.patrykandpatrick.vico.core.common.data.MutableExtraStore
import com.patrykandpatrick.vico.core.common.shape.CorneredShape
import com.trackgas.app.R
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.roundToInt

private const val CONSUMPTION_BAR_WIDTH = 8f
private const val TEMPERATURE_POINT_SIZE = 6f

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
            BoxWithConstraints(modifier.fillMaxWidth()) {
                val label = rememberAxisLabelComponent(
                    lineCount = 1,
                    margins = insets(horizontal = 4.dp, vertical = 4.dp),
                )
                val presentation = rememberChartPresentation(projection, locale, maxWidth.value, label)
                Column {
                    ProjectedConsumptionChart(projection, locale, presentation, label)
                    if (projection.temperatures.isNotEmpty()) {
                        averageTemperature?.let { mean ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    stringResource(R.string.temperature_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    stringResource(R.string.temperature, numberText(mean, locale)),
                                    modifier = Modifier.padding(bottom = 4.dp),
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                        }
                        ProjectedTemperatureChart(projection, locale, presentation, label)
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberChartPresentation(
    projection: ChartProjection,
    locale: Locale,
    width: Float,
    label: TextComponent,
): ChartLayout {
    val composeDensity = LocalDensity.current
    val layoutIsLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val context = remember(composeDensity, layoutIsLtr) {
        object : MeasuringContext {
            override val canvasBounds = RectF()
            override val density = composeDensity.density
            override val extraStore = MutableExtraStore()
            override val isLtr = layoutIsLtr
            override val cacheStore = CacheStore()
            override fun spToPx(sp: Float) = with(composeDensity) { sp.sp.toPx() }
        }
    }
    val resources = LocalContext.current.resources
    val volumeTitle = stringResource(R.string.volume, "").trim()
    val temperatureTitle = stringResource(R.string.temperature_axis, "").trim()
    return remember(projection, locale, width, label, context, resources, volumeTitle, temperatureTitle) {
        val dates = (0 until projection.positionCount).map { offset ->
            val date = if (projection.period.type == PeriodType.YEAR) {
                projection.period.start.plusMonths(offset.toLong())
            } else {
                projection.period.start.plusDays(offset.toLong())
            }
            bucketText(date, projection.period.type, locale)
        }
        val values = listOf(0.0, 0.5, 1.0).map { chartAxisText(projection, it, locale) } +
            temperatureRange(projection)?.let { range ->
                listOf(range.start, 0.0, range.endInclusive).map {
                    resources.getString(R.string.temperature_axis, numberText(BigDecimal.valueOf(it), locale))
                }
            }.orEmpty()
        val titles = if (projection.temperatures.isEmpty()) listOf(volumeTitle)
            else listOf(volumeTitle, temperatureTitle)
        fun textWidth(text: String) = label.getWidth(context, text) / context.density
        fun textHeight(text: String) = label.getHeight(context, text, pad = false) / context.density
        val dateWidth = dates.maxOf(::textWidth)
        val valueWidth = values.maxOf(::textWidth)
        val titleThickness = titles.maxOf(::textHeight)
        val dateHeight = dates.maxOf(::textHeight)
        val valueHeight = values.maxOf(::textHeight)
        val titleLength = titles.maxOf(::textWidth)
        chartLayout(
            contentWidth = width,
            positionCount = projection.positionCount,
            dateLabelWidth = dateWidth,
            dateLabelHeight = dateHeight,
            valueLabelWidth = valueWidth,
            valueLabelHeight = valueHeight,
            titleThickness = titleThickness,
            titleLength = titleLength,
        )
    }
}

@Composable
private fun ProjectedConsumptionChart(
    projection: ChartProjection,
    locale: Locale,
    presentation: ChartLayout,
    label: TextComponent,
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
    val bar = rememberLineComponent(
        fill(MaterialTheme.colorScheme.primary),
        thickness = CONSUMPTION_BAR_WIDTH.dp,
    )
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
    val layout = presentation
    val spacing = layout.tickSpacing
    val resources = LocalContext.current.resources
    val inspection = rememberInspection(projection, locale, MaterialTheme.colorScheme.primary) { offset ->
        projection.entries.firstOrNull { it.offset == offset }
            ?.let { resources.getString(R.string.volume, numberText(it.cubicMetres, locale)) }
    }
    val startAxis = VerticalAxis.rememberStart(
        valueFormatter = amountFormatter,
        itemPlacer = remember(projection.scale) {
            VerticalAxis.ItemPlacer.count(count = { if (projection.scale.signum() == 0) 1 else 3 })
        },
        label = label,
        size = BaseAxis.Size.Auto(layout.valueAxisWidth, layout.valueAxisWidth),
        title = stringResource(R.string.volume, "").trim(),
        titleComponent = label,
        tick = null,
        tickLength = 0.dp,
        line = null,
    )
    Box {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberColumnCartesianLayer(
                    columnProvider = columns,
                    columnCollectionSpacing = 4.dp,
                    rangeProvider = range,
                ),
                startAxis = startAxis,
                bottomAxis = HorizontalAxis.rememberBottom(
                    label = label,
                    valueFormatter = dateFormatter,
                    itemPlacer = remember(spacing) {
                        HorizontalAxis.ItemPlacer.aligned(spacing = { spacing }, addExtremeLabelPadding = false)
                    },
                    guideline = null,
                    tick = null,
                    tickLength = 0.dp,
                ),
                marker = inspection.marker,
                // Cancel the column's intrinsic scalable half-width; both plots use fixed edge space.
                layerPadding = {
                    CartesianLayerPadding(
                        scalableStartDp = -CONSUMPTION_BAR_WIDTH / 2f,
                        scalableEndDp = -CONSUMPTION_BAR_WIDTH / 2f,
                        unscalableStartDp = layout.edgePadding, unscalableEndDp = layout.edgePadding,
                    )
                },
                getXStep = { 1.0 },
            ),
            modelProducer = producer,
            modifier = Modifier.fillMaxWidth().height(layout.consumptionHeight.dp).then(inspection.modifier),
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
        ChartInspectionOverlay(inspection, layout.consumptionHeight.dp, MaterialTheme.colorScheme.primary)
    }
}

/** Reports inspection targets without ever reserving space around Vico's plot. */
internal class ChartInspectionMarker(
    private val guideline: LineComponent? = null,
    private val onTarget: (Int, Float) -> Unit,
) : CartesianMarker {
    override fun drawOverLayers(context: CartesianDrawingContext, targets: List<CartesianMarker.Target>) {
        targets.forEach { target ->
            guideline?.drawVertical(context, target.canvasX, context.layerBounds.top, context.layerBounds.bottom)
        }
        targets.firstOrNull()?.let { onTarget(it.x.roundToInt(), it.canvasX) }
    }
}

/** A marker that only draws while a long press is held, plus the gesture modifier driving it. */
private class Inspection(val marker: CartesianMarker, val modifier: Modifier, val text: String?, val anchorX: Float)

@Composable
private fun rememberInspection(
    projection: ChartProjection,
    locale: Locale,
    accent: Color,
    details: (Int) -> String?,
): Inspection {
    var inspecting by remember { mutableStateOf(false) }
    var inspectedText by remember { mutableStateOf<String?>(null) }
    var anchorX by remember { mutableStateOf(0f) }
    val resources = LocalContext.current.resources
    val currentDetails by rememberUpdatedState(details)
    val guideline = rememberLineComponent(fill(accent.copy(alpha = 0.35f)), thickness = 1.dp)
    val marker = remember(projection, locale, resources, guideline) {
        ChartInspectionMarker(guideline) { offset, canvasX ->
            anchorX = canvasX
            val date = if (projection.period.type == PeriodType.YEAR) {
                projection.period.start.plusMonths(offset.toLong())
            } else {
                projection.period.start.plusDays(offset.toLong())
            }
            inspectedText = resources.getString(
                R.string.chart_marker,
                bucketText(date, projection.period.type, locale, padDay = true),
                currentDetails(offset).orEmpty(),
            )
        }
    }
    val inspectionMarker = remember(marker) {
        object : CartesianMarker by marker {
            override fun drawOverLayers(context: CartesianDrawingContext, targets: List<CartesianMarker.Target>) {
                if (inspecting) marker.drawOverLayers(context, targets)
            }
        }
    }
    val gesture = Modifier.pointerInput(Unit) {
        detectDragGesturesAfterLongPress(
            onDragStart = {
                inspectedText = null
                inspecting = true
            },
            onDragEnd = {
                inspecting = false
                inspectedText = null
            },
            onDragCancel = {
                inspecting = false
                inspectedText = null
            },
            onDrag = { change, _ -> change.consume() },
        )
    }
    return Inspection(inspectionMarker, gesture, inspectedText, anchorX)
}

@Composable
private fun ChartInspectionOverlay(inspection: Inspection, height: Dp, accent: Color) {
    inspection.text?.let { text ->
        Layout(content = {
            Surface(
                modifier = Modifier.padding(4.dp).heightIn(max = height - 8.dp),
                shape = RoundedCornerShape(8.dp),
                color = accent,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                key(text) {
                    Text(
                        text,
                        modifier = Modifier.verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }) { measurables, constraints ->
            val tooltip = measurables.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
            layout(constraints.maxWidth, tooltip.height) {
                tooltip.place(chartTooltipLeft(constraints.maxWidth, tooltip.width, inspection.anchorX), 0)
            }
        }
    }
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

internal fun CartesianChartModelProducer.Transaction.temperatureSeries(entries: List<TemperatureChartEntry>) {
    val segments = temperatureSegments(entries)
    if (segments.isEmpty()) return
    // One model for the one line layer; separate series preserve gaps without dropping later segments.
    lineSeries {
        segments.forEach { segment ->
            series(x = segment.map { it.offset }, y = segment.map { it.celsius })
        }
    }
}

/** Separate, shorter chart so the temperature scale is never visually compared with consumption bars. */
@Composable
private fun ProjectedTemperatureChart(
    projection: ChartProjection,
    locale: Locale,
    presentation: ChartLayout,
    label: TextComponent,
) {
    val layout = presentation
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(producer, projection) {
        producer.runTransaction {
            temperatureSeries(projection.temperatures)
        }
    }
    val color = MaterialTheme.colorScheme.tertiary
    val line = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(fill(color)),
        pointProvider = LineCartesianLayer.PointProvider.single(
            LineCartesianLayer.point(
                rememberShapeComponent(fill(color), CorneredShape.Pill),
                size = TEMPERATURE_POINT_SIZE.dp,
            ),
        ),
    )
    val context = LocalContext.current
    val formatter = remember(locale, context) {
        CartesianValueFormatter { _, value, _ ->
            context.resources.getString(R.string.temperature_axis, numberText(BigDecimal.valueOf(value), locale))
        }
    }
    val range = remember(projection) {
        val bounds = requireNotNull(temperatureRange(projection))
        CartesianLayerRangeProvider.fixed(
            minX = 0.0,
            maxX = (projection.positionCount - 1).toDouble(),
            minY = bounds.start,
            maxY = bounds.endInclusive,
        )
    }
    val inspection = rememberInspection(projection, locale, color) { offset ->
        projection.temperatures.firstOrNull { it.offset == offset }
            ?.let { context.resources.getString(R.string.temperature, numberText(it.celsius, locale)) }
    }
    Box {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(
                    lineProvider = LineCartesianLayer.LineProvider.series(line),
                    rangeProvider = range,
                ),
                startAxis = VerticalAxis.rememberStart(
                    label = label,
                    valueFormatter = formatter,
                    itemPlacer = VerticalAxis.ItemPlacer.count(count = { 2 }),
                    size = BaseAxis.Size.Auto(layout.valueAxisWidth, layout.valueAxisWidth),
                    title = stringResource(R.string.temperature_axis, "").trim(),
                    titleComponent = label,
                    tick = null,
                    tickLength = 0.dp,
                    line = null,
                ),
                marker = inspection.marker,
                // Line layers already reserve the point's 3 dp radius.
                layerPadding = {
                    CartesianLayerPadding(
                        unscalableStartDp = layout.edgePadding - TEMPERATURE_POINT_SIZE / 2f,
                        unscalableEndDp = layout.edgePadding - TEMPERATURE_POINT_SIZE / 2f,
                    )
                },
                getXStep = { 1.0 },
            ),
            modelProducer = producer,
            modifier = Modifier.fillMaxWidth().height(layout.temperatureHeight.dp).then(inspection.modifier),
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
        ChartInspectionOverlay(inspection, layout.temperatureHeight.dp, color)
    }
}

private fun temperatureRange(projection: ChartProjection): ClosedFloatingPointRange<Double>? {
    val values = projection.temperatures.map { it.celsius.toDouble() }
    if (values.isEmpty()) return null
    val min = values.min()
    val max = values.max()
    val padding = if (min == max) 1.0 else (max - min) * 0.1
    return (min - padding)..(max + padding)
}

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
