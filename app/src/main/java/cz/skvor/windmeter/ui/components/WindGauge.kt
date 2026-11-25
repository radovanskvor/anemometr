package cz.skvor.windmeter.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import cz.skvor.windmeter.R
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import cz.skvor.windmeter.ui.theme.NumberStyle

private const val START_ANGLE = 135f
private const val SWEEP_ANGLE = 270f
private const val TICKS = 54

/**
 * Instrument-style arc gauge. [fraction] is 0..1 of the scale, [markerFraction] draws a fixed
 * notch (design flow or peak), and the arc color follows [color].
 */
@Composable
fun RingGauge(
    valueText: String,
    unitText: String,
    caption: String,
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    markerFraction: Float? = null,
    captionColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    /** When set, tapping the value or unit calls it (used to cycle units); a swap icon hints at it. */
    onUnitClick: (() -> Unit)? = null,
) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700), label = "gauge")
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val tick = MaterialTheme.colorScheme.outline
    val markerColor = MaterialTheme.colorScheme.onSurface

    Box(modifier.fillMaxWidth().aspectRatio(1.1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth(0.84f).aspectRatio(1f)) {
            val stroke = size.minDimension * 0.055f
            val inset = stroke / 2 + size.minDimension * 0.06f
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)

            // Fine scale ticks outside the arc, like the dial of an analog meter.
            val radius = size.minDimension / 2
            for (i in 0..TICKS) {
                val angle = START_ANGLE + SWEEP_ANGLE * i / TICKS
                val major = i % 9 == 0
                rotate(angle + 90f) {
                    drawLine(
                        if (animated > 0.001f && i <= animated * TICKS) color.copy(alpha = 0.9f) else tick.copy(alpha = 0.5f),
                        Offset(center.x, center.y - radius),
                        Offset(center.x, center.y - radius + if (major) stroke * 0.9f else stroke * 0.5f),
                        strokeWidth = if (major) 2.5f else 1.5f,
                    )
                }
            }

            drawArc(track, START_ANGLE, SWEEP_ANGLE, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            if (animated > 0.001f) {
                drawArc(
                    Brush.sweepGradient(listOf(color.copy(alpha = 0.35f), color, color.copy(alpha = 0.35f)), center),
                    START_ANGLE, SWEEP_ANGLE * animated, false, topLeft, arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            markerFraction?.coerceIn(0f, 1f)?.let { m ->
                rotate(START_ANGLE + SWEEP_ANGLE * m + 90f) {
                    drawLine(markerColor, Offset(center.x, inset - stroke * 0.9f), Offset(center.x, inset + stroke * 0.9f), strokeWidth = 4f, cap = StrokeCap.Round)
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val tappable = if (onUnitClick != null) {
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClickLabel = stringResource(R.string.change_unit), onClick = onUnitClick)
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            } else {
                Modifier
            }
            Column(tappable, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(valueText, style = MaterialTheme.typography.displayLarge.merge(NumberStyle), color = MaterialTheme.colorScheme.onSurface)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(unitText, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (onUnitClick != null) {
                        Icon(
                            Icons.Filled.SwapHoriz,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 6.dp).size(18.dp),
                        )
                    }
                }
            }
            Text(caption, style = MaterialTheme.typography.bodyMedium, color = captionColor, modifier = Modifier.padding(top = 10.dp))
        }
    }
}
