package com.trackgas.app.tracking

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.trackgas.app.R

@Composable
internal fun ReadingLoadContent(
    state: ReadingLoadState,
    onRetry: () -> Unit,
    inline: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (state == ReadingLoadState.READY) return
    if (!inline) {
        Box(
            modifier = modifier.fillMaxWidth().semantics {
                liveRegion = LiveRegionMode.Polite
                if (state == ReadingLoadState.LOADING) {
                    progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                }
            },
            contentAlignment = Alignment.Center,
        ) {
            if (state == ReadingLoadState.LOADING) {
                GasLoadingIndicator()
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.load_storage_error), color = MaterialTheme.colorScheme.onSurface)
                    Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
            }
        }
    } else if (state == ReadingLoadState.LOADING) {
        Box(
            modifier = modifier.fillMaxWidth().semantics {
                liveRegion = LiveRegionMode.Polite
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
            },
            contentAlignment = Alignment.CenterStart,
        ) {
            GasLoadingIndicator(compact = true)
        }
    } else {
        Column(
            modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(0.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(stringResource(R.string.load_storage_error), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}

@Composable
private fun GasLoadingIndicator(compact: Boolean = false) {
    val sizeScale = if (compact) 0.41f else 1f
    val flameAnimation = rememberInfiniteTransition(label = "gas-flame")
    val flameScale by flameAnimation.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(420), RepeatMode.Reverse),
        label = "flame-scale",
    )
    val flameTilt by flameAnimation.animateFloat(
        initialValue = -14f,
        targetValue = 14f,
        animationSpec = infiniteRepeatable(tween(360), RepeatMode.Reverse),
        label = "flame-tilt",
    )
    val meterLift by flameAnimation.animateFloat(
        initialValue = 0f,
        targetValue = -8f * sizeScale,
        animationSpec = infiniteRepeatable(tween(680), RepeatMode.Reverse),
        label = "meter-lift",
    )
    val haloAlpha by flameAnimation.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(680), RepeatMode.Reverse),
        label = "halo-pulse",
    )

    Box(Modifier.size(176.dp * sizeScale), contentAlignment = Alignment.BottomCenter) {
        Box(
            Modifier.align(Alignment.BottomCenter)
                .padding(bottom = 8.dp * sizeScale)
                .size(136.dp * sizeScale)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = haloAlpha), CircleShape),
        )
        Icon(
            painter = painterResource(R.drawable.ic_brand_mark),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(bottom = 22.dp * sizeScale)
                .size(104.dp * sizeScale)
                .graphicsLayer { translationY = meterLift },
        )
        Canvas(
            Modifier.align(Alignment.TopCenter)
                .size(72.dp * sizeScale)
                .graphicsLayer {
                    scaleX = flameScale
                    scaleY = flameScale
                    rotationZ = flameTilt
                },
        ) {
            val outerFlame = Path().apply {
                moveTo(size.width * 0.5f, size.height * 0.04f)
                cubicTo(
                    size.width * 0.66f, size.height * 0.28f,
                    size.width * 0.94f, size.height * 0.4f,
                    size.width * 0.82f, size.height * 0.68f,
                )
                cubicTo(
                    size.width * 0.68f, size.height * 0.98f,
                    size.width * 0.24f, size.height * 0.96f,
                    size.width * 0.16f, size.height * 0.68f,
                )
                cubicTo(
                    size.width * 0.08f, size.height * 0.42f,
                    size.width * 0.38f, size.height * 0.2f,
                    size.width * 0.5f, size.height * 0.04f,
                )
                close()
            }
            val innerFlame = Path().apply {
                moveTo(size.width * 0.5f, size.height * 0.42f)
                cubicTo(
                    size.width * 0.68f, size.height * 0.6f,
                    size.width * 0.66f, size.height * 0.75f,
                    size.width * 0.54f, size.height * 0.84f,
                )
                cubicTo(
                    size.width * 0.38f, size.height * 0.94f,
                    size.width * 0.32f, size.height * 0.76f,
                    size.width * 0.4f, size.height * 0.64f,
                )
                cubicTo(
                    size.width * 0.44f, size.height * 0.55f,
                    size.width * 0.48f, size.height * 0.48f,
                    size.width * 0.5f, size.height * 0.42f,
                )
                close()
            }
            drawPath(outerFlame, Color(0xFFF06B32))
            drawPath(innerFlame, Color(0xFFFFC857))
        }
    }
}
