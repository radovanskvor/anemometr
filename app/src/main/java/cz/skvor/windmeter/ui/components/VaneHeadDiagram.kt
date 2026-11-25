package cz.skvor.windmeter.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Front view of the anemometer's vane head with dimension lines, so the user knows which two
 * diameters to measure: the inner diameter of the ring (head) and the solid centre (hub).
 */
@Composable
fun VaneHeadDiagram(headLabel: String, hubLabel: String, headColor: Color, hubColor: Color, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val housing = MaterialTheme.colorScheme.outline
    val blade = MaterialTheme.colorScheme.surfaceContainerHighest
    val bladeEdge = MaterialTheme.colorScheme.outlineVariant
    val hubFill = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(modifier.fillMaxWidth().height(250.dp)) {
        val labelSpace = 44.dp.toPx()
        val rHousing = (size.height - labelSpace * 2) / 2
        val rHead = rHousing * 0.86f
        val rHub = rHousing * 0.26f
        val c = Offset(size.width / 2, size.height / 2)

        // A stroke is centred on its radius, so centre the ring between rHead and rHousing to make
        // its inner edge exactly the measured head diameter.
        drawCircle(housing, radius = (rHousing + rHead) / 2, center = c, style = Stroke(rHousing - rHead))
        // Eight vanes between the hub and the ring, ending short of the ring like the real rotor.
        val rTip = rHead * 0.88f
        for (i in 0 until 8) {
            rotate(i * 45f, c) {
                val path = Path().apply {
                    moveTo(c.x - rHub * 0.35f, c.y - rHub * 0.9f)
                    quadraticTo(c.x - rTip * 0.45f, c.y - rTip * 0.55f, c.x - rTip * 0.28f, c.y - rTip * 0.96f)
                    lineTo(c.x + rTip * 0.1f, c.y - rTip * 0.99f)
                    quadraticTo(c.x - rTip * 0.05f, c.y - rTip * 0.5f, c.x + rHub * 0.35f, c.y - rHub * 0.9f)
                    close()
                }
                drawPath(path, blade)
                drawPath(path, bladeEdge, style = Stroke(1.dp.toPx()))
            }
        }
        drawCircle(hubFill, radius = rHub, center = c)
        // Outline the two measured edges in their dimension colors.
        drawCircle(headColor, radius = rHead, center = c, style = Stroke(2.dp.toPx()))
        drawCircle(hubColor, radius = rHub, center = c, style = Stroke(2.dp.toPx()))

        val headY = c.y - rHousing - 14.dp.toPx()
        dimension(c.x - rHead, c.x + rHead, c.y, headY, headColor)
        val headText = measurer.measure(headLabel, TextStyle(color = headColor, fontSize = 14.sp, fontWeight = FontWeight.Bold))
        drawText(headText, topLeft = Offset(c.x - headText.size.width / 2, headY - headText.size.height - 4.dp.toPx()))

        val hubY = c.y + rHousing + 14.dp.toPx()
        dimension(c.x - rHub, c.x + rHub, c.y, hubY, hubColor)
        val hubText = measurer.measure(hubLabel, TextStyle(color = hubColor, fontSize = 14.sp, fontWeight = FontWeight.Bold))
        drawText(hubText, topLeft = Offset(c.x - hubText.size.width / 2, hubY + 4.dp.toPx()))
    }
}

/** Engineering-style dimension: extension lines from the part, a dimension line and two arrows. */
private fun DrawScope.dimension(x1: Float, x2: Float, fromY: Float, atY: Float, color: Color) {
    val stroke = 1.5.dp.toPx()
    val overshoot = if (atY < fromY) -4.dp.toPx() else 4.dp.toPx()
    drawLine(color, Offset(x1, fromY), Offset(x1, atY + overshoot), strokeWidth = stroke)
    drawLine(color, Offset(x2, fromY), Offset(x2, atY + overshoot), strokeWidth = stroke)
    drawLine(color, Offset(x1, atY), Offset(x2, atY), strokeWidth = stroke)
    val a = 8.dp.toPx()
    val h = 4.dp.toPx()
    drawPath(Path().apply { moveTo(x1, atY); lineTo(x1 + a, atY - h); lineTo(x1 + a, atY + h); close() }, color)
    drawPath(Path().apply { moveTo(x2, atY); lineTo(x2 - a, atY - h); lineTo(x2 - a, atY + h); close() }, color)
}
