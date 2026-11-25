package cz.skvor.windmeter.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cz.skvor.windmeter.R
import cz.skvor.windmeter.ble.ConnectionState
import cz.skvor.windmeter.data.FlowMeasurement
import cz.skvor.windmeter.data.FunnelSettings
import cz.skvor.windmeter.data.Outlet
import cz.skvor.windmeter.data.Room
import cz.skvor.windmeter.data.deviationPercent
import cz.skvor.windmeter.data.flowStatus
import cz.skvor.windmeter.ui.FlowCapture
import cz.skvor.windmeter.ui.LiveState
import cz.skvor.windmeter.ui.components.DirectionBadge
import cz.skvor.windmeter.ui.components.FlowBar
import cz.skvor.windmeter.ui.components.MetricColumn
import cz.skvor.windmeter.ui.components.Panel
import cz.skvor.windmeter.ui.components.RingGauge
import cz.skvor.windmeter.ui.components.SectionLabel
import cz.skvor.windmeter.ui.components.StatusPill
import cz.skvor.windmeter.ui.components.formatFlow
import cz.skvor.windmeter.ui.theme.AppTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun OutletMeasureScreen(
    room: Room,
    outlet: Outlet,
    live: LiveState,
    connection: ConnectionState,
    funnel: FunnelSettings,
    tolerancePercent: Double,
    captureSeconds: Int,
    capture: FlowCapture?,
    onConnectClick: () -> Unit,
    onStartCapture: () -> Unit,
    onCancelCapture: () -> Unit,
    onSaveCapture: () -> Unit,
    onDeleteMeasurement: (FlowMeasurement) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val connected = connection is ConnectionState.Connected
    val target = room.designFlowOf(outlet)
    val liveVelocity = live.latest?.windMetersPerSecond
    val liveFlow = liveVelocity?.let(funnel::flowCubicMetersPerHour)
    val liveDeviation = liveFlow?.let { deviationPercent(it, target) }
    val liveStatus = flowStatus(liveDeviation, tolerancePercent)
    // Scale up to 150 % of the target so the target sits at two thirds of the dial.
    val scaleMax = maxOf(target * 1.5, 1.0)
    val timeFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DirectionBadge(outlet.direction, long = true)
            Spacer(Modifier.width(10.dp))
            Text(room.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            if (liveDeviation != null) StatusPill(liveStatus, liveDeviation)
        }

        RingGauge(
            valueText = liveFlow?.let(::formatFlow) ?: "–",
            unitText = "m³/h",
            caption = when {
                liveVelocity != null -> stringResource(R.string.gauge_caption_flow, formatFlow(target), "%.2f".format(liveVelocity))
                connected -> stringResource(R.string.waiting_for_data)
                else -> stringResource(R.string.meter_not_connected)
            },
            fraction = ((liveFlow ?: 0.0) / scaleMax).toFloat(),
            markerFraction = (target / scaleMax).toFloat(),
            color = if (liveFlow == null) colors.of(outlet.direction) else colors.of(liveStatus),
        )

        AnimatedContent(targetState = capture?.isFinished, label = "capture") { finished ->
            when {
                !connected -> Button(onClick = onConnectClick, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.connect_meter)) }
                capture == null -> Button(onClick = onStartCapture, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Icon(Icons.Outlined.Timer, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.measure_average, captureSeconds))
                }
                finished == true -> CaptureResult(capture, target, funnel, tolerancePercent, onSaveCapture, onStartCapture, onCancelCapture)
                else -> CaptureProgress(capture, onCancelCapture)
            }
        }

        Text(stringResource(R.string.hood_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        // How far the whole room is: the outlets together have to reach the room's design flow.
        val totals = room.totals(outlet.direction)
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.room_progress, room.title, formatFlow(totals.measured), formatFlow(totals.design)),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                StatusPill(flowStatus(totals.deviationPercent, tolerancePercent), totals.deviationPercent)
            }
            Spacer(Modifier.height(8.dp))
            FlowBar(totals.measured.takeIf { totals.measuredCount > 0 }, totals.design, colors.of(outlet.direction), Modifier.fillMaxWidth().height(6.dp))
        }

        if (outlet.note.isNotBlank()) {
            Panel {
                SectionLabel(stringResource(R.string.note_label))
                Spacer(Modifier.height(4.dp))
                Text(outlet.note, style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (outlet.measurements.isNotEmpty()) {
            Panel(contentPadding = PaddingValues(vertical = 8.dp)) {
                SectionLabel(stringResource(R.string.history_label), Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                outlet.measurements.sortedByDescending { it.timestampMillis }.forEachIndexed { index, m ->
                    if (index > 0) HorizontalDivider(color = colors.cardBorder)
                    val deviation = deviationPercent(m.flowCubicMetersPerHour, target)
                    Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${formatFlow(m.flowCubicMetersPerHour)} m³/h", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${timeFormat.format(Instant.ofEpochMilli(m.timestampMillis))} · %.2f m/s · ${m.durationSeconds} s".format(m.averageVelocity),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        StatusPill(flowStatus(deviation, tolerancePercent), deviation)
                        IconButton(onClick = { onDeleteMeasurement(m) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.delete_measurement), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun CaptureProgress(capture: FlowCapture, onCancel: () -> Unit) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionLabel(stringResource(R.string.measuring_average))
                Text(
                    stringResource(R.string.measuring_remaining, capture.durationSeconds - capture.elapsedSeconds, capture.samples.size),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            IconButton(onClick = onCancel) { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.cancel_measurement)) }
        }
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { capture.elapsedSeconds / capture.durationSeconds.toFloat() },
            strokeCap = StrokeCap.Round,
            modifier = Modifier.fillMaxWidth().height(8.dp),
        )
    }
}

@Composable
private fun CaptureResult(
    capture: FlowCapture,
    target: Double,
    funnel: FunnelSettings,
    tolerancePercent: Double,
    onSave: () -> Unit,
    onRepeat: () -> Unit,
    onDiscard: () -> Unit,
) {
    val colors = AppTheme.colors
    val velocity = capture.averageVelocity
    Panel {
        if (velocity == null) {
            Text(stringResource(R.string.no_data_during_capture), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDiscard) { Text(stringResource(R.string.action_close)) }
                Button(onClick = onRepeat) { Text(stringResource(R.string.action_repeat)) }
            }
            return@Panel
        }
        val flow = funnel.flowCubicMetersPerHour(velocity)
        val deviation = deviationPercent(flow, target)
        val status = flowStatus(deviation, tolerancePercent)

        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(stringResource(R.string.result), Modifier.weight(1f))
            StatusPill(status, deviation)
        }
        Spacer(Modifier.height(8.dp))
        Row {
            MetricColumn(stringResource(R.string.metric_flow), formatFlow(flow), "m³/h", Modifier.weight(1f), valueColor = colors.of(status))
            MetricColumn(stringResource(R.string.metric_target), formatFlow(target), "m³/h", Modifier.weight(1f))
            MetricColumn(stringResource(R.string.metric_velocity), "%.2f".format(velocity), "m/s", Modifier.weight(1f))
        }
        capture.variationPercent?.let { variation ->
            if (variation > 15) {
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = onRepeat) {
                    Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = colors.warning, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.unstable_values, "%.0f".format(variation)), color = colors.warning)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onDiscard) { Text(stringResource(R.string.action_discard)) }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onRepeat) { Text(stringResource(R.string.action_repeat)) }
            Button(onClick = onSave) { Text(stringResource(R.string.action_save)) }
        }
    }
}
