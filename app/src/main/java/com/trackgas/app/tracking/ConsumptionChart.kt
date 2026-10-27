package com.trackgas.app.tracking

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
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
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.ColumnCartesianLayerModel
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
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
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    val projection = chartProjection(period, dashboard)
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
            ProjectedConsumptionChart(projection, locale, modifier)
        }
    }
}

@Composable
private fun ProjectedConsumptionChart(
    projection: ChartProjection,
    locale: Locale,
    modifier: Modifier,
) {
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(producer, projection) {
        producer.runTransaction {
            columnSeries {
                series(
                    x = projection.entries.map { it.offset },
                    y = projection.entries.map { it.normalizedY },
                )
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
    val marker = consumptionMarker(projection, locale)
    var inspecting by remember { mutableStateOf(false) }
    val inspectionMarker = remember(marker) {
        object : CartesianMarker by marker {
            override fun drawOverLayers(context: CartesianDrawingContext, targets: List<CartesianMarker.Target>) {
                if (inspecting) marker.drawOverLayers(context, targets)
            }
        }
    }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                columnProvider = columns,
                columnCollectionSpacing = 4.dp,
                rangeProvider = range,
            ),
            startAxis = VerticalAxis.rememberStart(
                valueFormatter = amountFormatter,
                itemPlacer = remember(projection.scale) {
                    VerticalAxis.ItemPlacer.count(count = { if (projection.scale.signum() == 0) 1 else 3 })
                },
                title = stringResource(R.string.volume, "").trim(),
                titleComponent = rememberAxisLabelComponent(),
                tick = null,
                line = null,
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                valueFormatter = dateFormatter,
                itemPlacer = remember(spacing) {
                    HorizontalAxis.ItemPlacer.aligned(spacing = { spacing })
                },
                guideline = null,
                tick = null,
            ),
            marker = inspectionMarker,
            getXStep = { 1.0 },
        ),
        modelProducer = producer,
        modifier = modifier.height(220.dp).pointerInput(Unit) {
            detectDragGesturesAfterLongPress(
                onDragStart = { inspecting = true },
                onDragEnd = { inspecting = false },
                onDragCancel = { inspecting = false },
                onDrag = { change, _ -> change.consume() },
            )
        },
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

@Composable
private fun consumptionMarker(projection: ChartProjection, locale: Locale): DefaultCartesianMarker {
    val colors = MaterialTheme.colorScheme
    val resources = LocalContext.current.resources
    val valueFormatter = remember(projection, locale, resources) {
        DefaultCartesianMarker.ValueFormatter { _, targets ->
            val entry = projection.entries.single { it.offset.toDouble() == targets.single().x }
            resources.getString(
                R.string.chart_marker,
                bucketText(entry.range.start, projection.period.type, locale, padDay = true),
                resources.getString(R.string.volume, numberText(entry.cubicMetres, locale)),
            )
        }
    }
    return rememberDefaultCartesianMarker(
        label = rememberTextComponent(
            color = colors.onPrimary,
            textSize = MaterialTheme.typography.labelLarge.fontSize,
            lineCount = 2,
            padding = insets(horizontal = 12.dp, vertical = 8.dp),
            background = rememberShapeComponent(
                fill = fill(colors.primary),
                shape = CorneredShape.rounded(8f),
            ),
        ),
        valueFormatter = valueFormatter,
        labelPosition = DefaultCartesianMarker.LabelPosition.Top,
        guideline = rememberLineComponent(fill(colors.primary.copy(alpha = 0.35f)), thickness = 1.dp),
    )
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
