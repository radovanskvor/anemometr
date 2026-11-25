package cz.skvor.windmeter.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Simple area chart of the last values; the vertical scale adapts to the highest value. */
@Composable
fun LiveChart(values: List<Double>, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier.fillMaxWidth().height(140.dp)) {
        for (i in 0..3) {
            val y = size.height * i / 3f
            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        if (values.size < 2) return@Canvas

        val top = (values.max() * 1.15).coerceAtLeast(1.0)
        val stepX = size.width / (values.size - 1)
        val points = values.mapIndexed { i, v -> Offset(i * stepX, (size.height * (1 - v / top)).toFloat()) }

        val linePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        val areaPath = Path().apply {
            moveTo(points.first().x, size.height)
            points.forEach { lineTo(it.x, it.y) }
            lineTo(points.last().x, size.height)
            lineTo(points.first().x, size.height)
            close()
        }
        drawPath(areaPath, Brush.verticalGradient(listOf(line.copy(alpha = 0.35f), line.copy(alpha = 0f))))
        drawPath(linePath, line, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
