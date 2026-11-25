package cz.skvor.windmeter.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Line chart whose values can be read by touching or dragging along it; the selected sample is
 * reported through [onSelect] so the screen can show its time and value in full.
 */
@Composable
fun InspectableChart(
    values: List<Double>,
    color: Color,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    formatAxis: (Double) -> String,
    formatTooltip: (Int) -> String,
    modifier: Modifier = Modifier,
    height: Dp = 260.dp,
) {
    val measurer = rememberTextMeasurer()
    val grid = MaterialTheme.colorScheme.outlineVariant
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val tooltipBackground = MaterialTheme.colorScheme.inverseSurface
    val tooltipText = MaterialTheme.colorScheme.inverseOnSurface
    val axisStyle = TextStyle(color = label, fontSize = 11.sp)
    val tooltipStyle = TextStyle(color = tooltipText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

    // Round the scale outwards so the grid lines land on readable numbers.
    val (low, high) = remember(values) {
        if (values.isEmpty()) 0.0 to 1.0 else {
            val min = values.min()
            val max = values.max()
            val span = (max - min).coerceAtLeast(0.5)
            val step = niceStep(span / 4)
            floor(min / step) * step to (ceil(max / step) * step).coerceAtLeast(floor(min / step) * step + step)
        }
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(values.size) {
                detectTapGestures { offset -> onSelect(indexAt(offset.x, 44.dp.toPx(), size.width.toFloat(), values.size)) }
            }
            .pointerInput(values.size) {
                detectHorizontalDragGestures(
                    onDragStart = { offset -> onSelect(indexAt(offset.x, 44.dp.toPx(), size.width.toFloat(), values.size)) },
                    onHorizontalDrag = { change, _ -> onSelect(indexAt(change.position.x, 44.dp.toPx(), size.width.toFloat(), values.size)) },
                )
            },
    ) {
        val leftPad = 44.dp.toPx()
        val topPad = 40.dp.toPx()
        val bottomPad = 8.dp.toPx()
        val plotWidth = size.width - leftPad
        val plotHeight = size.height - topPad - bottomPad
        fun yOf(v: Double) = topPad + (plotHeight * (1 - (v - low) / (high - low))).toFloat()
        fun xOf(i: Int) = leftPad + if (values.size <= 1) 0f else plotWidth * i / (values.size - 1)

        for (k in 0..4) {
            val v = low + (high - low) * k / 4
            val y = yOf(v)
            drawLine(grid, Offset(leftPad, y), Offset(size.width, y), strokeWidth = 1f)
            val text = measurer.measure(formatAxis(v), axisStyle)
            drawText(text, topLeft = Offset(leftPad - text.size.width - 6.dp.toPx(), y - text.size.height / 2))
        }
        if (values.size < 2) return@Canvas

        val line = Path().apply {
            moveTo(xOf(0), yOf(values[0]))
            for (i in 1 until values.size) lineTo(xOf(i), yOf(values[i]))
        }
        val area = Path().apply {
            moveTo(xOf(0), topPad + plotHeight)
            for (i in values.indices) lineTo(xOf(i), yOf(values[i]))
            lineTo(xOf(values.size - 1), topPad + plotHeight)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.3f), color.copy(alpha = 0f)), startY = topPad, endY = topPad + plotHeight))
        drawPath(line, color, style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round))

        selectedIndex?.takeIf { it in values.indices }?.let { i ->
            val x = xOf(i)
            val y = yOf(values[i])
            drawLine(label, Offset(x, topPad), Offset(x, topPad + plotHeight), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            drawCircle(color, radius = 6.dp.toPx(), center = Offset(x, y))
            drawCircle(tooltipText, radius = 2.5.dp.toPx(), center = Offset(x, y))

            val text = measurer.measure(formatTooltip(i), tooltipStyle)
            val padX = 10.dp.toPx()
            val padY = 6.dp.toPx()
            val boxWidth = text.size.width + padX * 2
            val boxHeight = text.size.height + padY * 2
            val boxLeft = (x - boxWidth / 2).coerceIn(leftPad, size.width - boxWidth)
            drawRoundRect(tooltipBackground, Offset(boxLeft, 0f), Size(boxWidth, boxHeight), CornerRadius(8.dp.toPx()))
            drawText(text, topLeft = Offset(boxLeft + padX, padY))
        }
    }
}

/** Maps a touch position to the nearest sample; the plot starts after the y-axis labels. */
private fun indexAt(x: Float, leftPad: Float, width: Float, count: Int): Int? {
    if (count == 0) return null
    val fraction = ((x - leftPad) / (width - leftPad)).coerceIn(0f, 1f)
    return (fraction * (count - 1)).roundToInt()
}

private fun niceStep(raw: Double): Double {
    val magnitude = Math.pow(10.0, floor(Math.log10(raw)))
    val normalized = raw / magnitude
    val nice = when {
        normalized <= 1 -> 1.0
        normalized <= 2 -> 2.0
        normalized <= 2.5 -> 2.5
        normalized <= 5 -> 5.0
        else -> 10.0
    }
    return nice * magnitude
}
