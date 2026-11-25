package cz.skvor.windmeter.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cz.skvor.windmeter.R
import cz.skvor.windmeter.ble.ConnectionState
import cz.skvor.windmeter.data.AirDirection
import cz.skvor.windmeter.data.FlowStatus
import cz.skvor.windmeter.ui.theme.AppTheme
import java.util.Locale

/** Flat card with a hairline border; reads as an instrument panel rather than a floating sheet. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    val border = BorderStroke(1.dp, AppTheme.colors.cardBorder)
    val color = MaterialTheme.colorScheme.surfaceContainer
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, color = color, border = border) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    } else {
        Surface(modifier = modifier.fillMaxWidth(), shape = shape, color = color, border = border) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
fun DirectionBadge(direction: AirDirection, modifier: Modifier = Modifier, long: Boolean = false) {
    val color = AppTheme.colors.of(direction)
    Box(
        modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
            .padding(horizontal = if (long) 10.dp else 0.dp)
            .then(if (long) Modifier else Modifier.size(28.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(if (long) direction.label else direction.shortLabel),
            color = color,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = if (long) Modifier.padding(vertical = 4.dp) else Modifier,
        )
    }
}

@Composable
fun StatusPill(status: FlowStatus, deviation: Double?, modifier: Modifier = Modifier) {
    val color by animateColorAsState(AppTheme.colors.of(status), label = "status")
    val text = when {
        deviation == null -> stringResource(R.string.status_not_measured)
        else -> "%+.0f %%".format(deviation)
    }
    Row(
        modifier.background(color.copy(alpha = 0.14f), CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(text, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** Live connection indicator for the top bar; tapping it opens the device picker. */
@Composable
fun ConnectionChip(connection: ConnectionState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val (color, text) = when (connection) {
        is ConnectionState.Connected -> colors.ok to stringResource(R.string.conn_connected)
        is ConnectionState.Connecting -> colors.warning to stringResource(R.string.conn_connecting)
        ConnectionState.Disconnected -> colors.muted to stringResource(R.string.conn_connect)
    }
    val pulse = rememberInfiniteTransition(label = "pulse")
    val alpha by pulse.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "alpha")
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = color.copy(alpha = 0.14f),
        modifier = modifier,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(8.dp)
                    .alpha(if (connection is ConnectionState.Disconnected) 1f else alpha)
                    .background(color, CircleShape),
            )
            Spacer(Modifier.width(8.dp))
            Text(text, color = color, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun MetricColumn(label: String, value: String, unit: String, modifier: Modifier = Modifier, valueColor: Color = Color.Unspecified) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SectionLabel(label)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.headlineSmall, color = valueColor)
            if (unit.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                Text(unit, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}

/** Thin horizontal bar of measured vs design flow; the marker at the design value stays fixed. */
@Composable
fun FlowBar(measured: Double?, design: Double, color: Color, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val marker = MaterialTheme.colorScheme.onSurfaceVariant
    androidx.compose.foundation.Canvas(modifier) {
        val scaleMax = maxOf(design * 1.5, measured ?: 0.0, 1.0)
        val h = size.height
        drawRoundRect(track, cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2))
        if (measured != null) {
            val w = (size.width * (measured / scaleMax)).toFloat().coerceIn(h, size.width)
            drawRoundRect(color, size = androidx.compose.ui.geometry.Size(w, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2))
        }
        val x = (size.width * (design / scaleMax)).toFloat()
        drawRect(marker, topLeft = androidx.compose.ui.geometry.Offset(x - 1.dp.toPx(), -3.dp.toPx()), size = androidx.compose.ui.geometry.Size(2.dp.toPx(), h + 6.dp.toPx()))
    }
}

fun formatFlow(value: Double): String = "%.0f".format(value)
