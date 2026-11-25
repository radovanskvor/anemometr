package cz.skvor.windmeter.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cz.skvor.windmeter.R
import cz.skvor.windmeter.ble.ConnectionState
import cz.skvor.windmeter.data.Beaufort
import cz.skvor.windmeter.data.FunnelSettings
import cz.skvor.windmeter.data.TemperatureUnit
import cz.skvor.windmeter.data.WindUnit
import cz.skvor.windmeter.ui.LiveState
import cz.skvor.windmeter.ui.components.LiveChart
import cz.skvor.windmeter.ui.components.MetricColumn
import cz.skvor.windmeter.ui.components.Panel
import cz.skvor.windmeter.ui.components.RingGauge
import cz.skvor.windmeter.ui.components.SectionLabel
import cz.skvor.windmeter.ui.components.formatFlow

@Composable
fun LiveScreen(
    live: LiveState,
    connection: ConnectionState,
    windUnit: WindUnit,
    /** Gauge, chart and statistics show flow through the hood (m³/h) instead of speed. */
    showsFlow: Boolean,
    temperatureUnit: TemperatureUnit,
    funnel: FunnelSettings,
    onConnectClick: () -> Unit,
    onWindUnitChange: (WindUnit) -> Unit,
    onShowFlow: () -> Unit,
    onResetStats: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: (title: String) -> Unit,
    onDiscardRecording: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSaveDialog by remember { mutableStateOf(false) }
    val latest = live.latest
    val connected = connection is ConnectionState.Connected

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (connection is ConnectionState.Disconnected) {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.meter_not_connected), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    FilledTonalButton(onClick = onConnectClick) { Text(stringResource(R.string.conn_connect)) }
                }
            }
        }

        val wind = latest?.windMetersPerSecond ?: 0.0
        val beaufort = Beaufort.of(wind)
        val beaufortNames = stringArrayResource(R.array.beaufort)
        val display = if (showsFlow) {
            LiveDisplay("m³/h", FLOW_GAUGE_MAX, funnel::flowCubicMetersPerHour) { formatFlow(funnel.flowCubicMetersPerHour(it)) }
        } else {
            LiveDisplay(windUnit.label, windUnit.gaugeMax, windUnit::convert, windUnit::format)
        }
        RingGauge(
            valueText = if (latest == null) "–" else display.format(wind),
            unitText = display.label,
            caption = if (latest == null) stringResource(R.string.waiting_for_data) else stringResource(R.string.beaufort_caption, beaufort, beaufortNames[beaufort]),
            fraction = (display.convert(wind) / display.gaugeMax).toFloat(),
            markerFraction = live.stats?.let { (display.convert(it.max) / display.gaugeMax).toFloat() },
            color = MaterialTheme.colorScheme.primary,
            captionColor = MaterialTheme.colorScheme.primary,
            onUnitClick = {
                // Cycle through all speed units, then flow, then back to the first speed unit.
                when {
                    showsFlow -> onWindUnitChange(WindUnit.entries.first())
                    windUnit == WindUnit.entries.last() -> onShowFlow()
                    else -> onWindUnitChange(WindUnit.entries[windUnit.ordinal + 1])
                }
            },
        )

        Panel {
            Row {
                // The first metric shows whichever quantity the gauge is not showing.
                if (showsFlow) {
                    // Always m/s: the cycle reaches flow from ft/min, which nobody wants to read here.
                    val speedUnit = WindUnit.MetersPerSecond
                    MetricColumn(
                        stringResource(R.string.metric_velocity),
                        latest?.let { speedUnit.format(it.windMetersPerSecond) } ?: "–",
                        speedUnit.label,
                        Modifier.weight(1.3f),
                    )
                } else {
                    MetricColumn(
                        stringResource(R.string.flow_through_hood),
                        latest?.let { formatFlow(funnel.flowCubicMetersPerHour(it.windMetersPerSecond)) } ?: "–",
                        "m³/h",
                        Modifier.weight(1.3f),
                    )
                }
                MetricColumn(
                    stringResource(R.string.temperature),
                    latest?.let { "%.1f".format(temperatureUnit.fromCelsius(it.temperatureCelsius)) } ?: "–",
                    temperatureUnit.label,
                    Modifier.weight(1f),
                )
                MetricColumn(stringResource(R.string.humidity), latest?.let { "%.0f".format(it.relativeHumidity) } ?: "–", "%", Modifier.weight(0.8f))
            }
        }
        if (live.windChillWarning) {
            Text(stringResource(R.string.wind_chill), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(stringResource(if (showsFlow) R.string.flow_trend else R.string.speed_trend), Modifier.weight(1f))
                TextButton(onClick = onResetStats) {
                    Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.reset))
                }
            }
            LiveChart(live.recent.map { display.convert(it.windMetersPerSecond) })
            Spacer(Modifier.height(12.dp))
            Row {
                MetricColumn(stringResource(R.string.stat_min), live.stats?.min?.let(display.format) ?: "–", "", Modifier.weight(1f))
                MetricColumn(stringResource(R.string.stat_avg), live.stats?.average?.let(display.format) ?: "–", "", Modifier.weight(1f))
                MetricColumn(stringResource(R.string.stat_max), live.stats?.max?.let(display.format) ?: "–", display.label, Modifier.weight(1f))
            }
        }

        if (live.isRecording) {
            Button(
                onClick = { showSaveDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Filled.Stop, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.stop_recording, live.recorded.size))
            }
        } else {
            Button(onClick = onStartRecording, enabled = connected, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Filled.FiberManualRecord, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.start_recording))
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (showSaveDialog) {
        SaveRecordingDialog(
            sampleCount = live.recorded.size,
            onSave = { title -> showSaveDialog = false; onStopRecording(title) },
            onDiscard = { showSaveDialog = false; onDiscardRecording() },
            onDismiss = { showSaveDialog = false },
        )
    }
}

/** Full scale of the gauge in flow mode; residential outlets rarely exceed 150 m³/h. */
private const val FLOW_GAUGE_MAX = 200.0

/** What the gauge, chart and statistics show: a speed unit or flow, both computed from m/s. */
private class LiveDisplay(
    val label: String,
    val gaugeMax: Double,
    val convert: (metersPerSecond: Double) -> Double,
    val format: (metersPerSecond: Double) -> String,
)

@Composable
private fun SaveRecordingDialog(sampleCount: Int, onSave: (String) -> Unit, onDiscard: () -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.save_recording)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.recorded_values, sampleCount))
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(stringResource(R.string.record_name_hint)) }, singleLine = true)
            }
        },
        confirmButton = { Button(onClick = { onSave(title) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDiscard) { Text(stringResource(R.string.action_discard)) } },
    )
}
