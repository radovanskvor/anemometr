package cz.skvor.windmeter.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cz.skvor.windmeter.R
import cz.skvor.windmeter.ble.ConnectionState
import cz.skvor.windmeter.data.AppLanguage
import cz.skvor.windmeter.data.FunnelSettings
import cz.skvor.windmeter.data.TemperatureUnit
import cz.skvor.windmeter.data.WindUnit
import cz.skvor.windmeter.ui.components.Panel
import cz.skvor.windmeter.ui.components.SectionLabel
import cz.skvor.windmeter.ui.components.VaneHeadDiagram
import cz.skvor.windmeter.ui.theme.AppTheme

private val toleranceOptions = listOf(5.0, 10.0, 15.0, 20.0)
private val captureOptions = listOf(10, 15, 30, 60)

@Composable
fun SettingsScreen(
    windUnit: WindUnit,
    temperatureUnit: TemperatureUnit,
    funnel: FunnelSettings,
    tolerancePercent: Double,
    captureSeconds: Int,
    connection: ConnectionState,
    /** Null on Android versions without per-app languages; the picker is hidden then. */
    language: AppLanguage?,
    onWindUnitChange: (WindUnit) -> Unit,
    onTemperatureUnitChange: (TemperatureUnit) -> Unit,
    onFunnelChange: (FunnelSettings) -> Unit,
    onToleranceChange: (Double) -> Unit,
    onCaptureSecondsChange: (Int) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FunnelPanel(funnel, onFunnelChange)

        Panel {
            SectionLabel(stringResource(R.string.settings_evaluation))
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.settings_tolerance), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(6.dp))
            Segments(toleranceOptions, tolerancePercent, { "±%.0f %%".format(it) }, onToleranceChange)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.settings_capture), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(6.dp))
            Segments(captureOptions, captureSeconds, { "$it s" }, onCaptureSecondsChange)
        }

        Panel {
            SectionLabel(stringResource(R.string.settings_units))
            Spacer(Modifier.height(10.dp))
            Segments(WindUnit.entries, windUnit, { it.label }, onWindUnitChange)
            Spacer(Modifier.height(10.dp))
            Segments(TemperatureUnit.entries, temperatureUnit, { it.label }, onTemperatureUnitChange)
        }

        if (language != null) {
            Panel {
                SectionLabel(stringResource(R.string.settings_language))
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.entries.forEach { option ->
                        FilterChip(
                            selected = option == language,
                            onClick = { onLanguageChange(option) },
                            label = { Text(if (option == AppLanguage.System) stringResource(R.string.language_system) else option.label) },
                        )
                    }
                }
            }
        }

        Panel {
            SectionLabel(stringResource(R.string.settings_device))
            Spacer(Modifier.height(8.dp))
            val status = when (connection) {
                is ConnectionState.Connected -> stringResource(R.string.device_connected, connection.name)
                is ConnectionState.Connecting -> stringResource(R.string.device_connecting, connection.name)
                ConnectionState.Disconnected -> stringResource(R.string.device_disconnected)
            }
            Text(status, style = MaterialTheme.typography.bodyLarge)
            if (connection !is ConnectionState.Disconnected) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onDisconnect) { Text(stringResource(R.string.disconnect)) }
            }
        }
    }
}

@Composable
private fun FunnelPanel(funnel: FunnelSettings, onSave: (FunnelSettings) -> Unit) {
    var vane by remember(funnel) { mutableStateOf(formatDecimal(funnel.vaneDiameterMm)) }
    var hub by remember(funnel) { mutableStateOf(formatDecimal(funnel.hubDiameterMm)) }
    var factor by remember(funnel) { mutableStateOf("%.2f".format(funnel.correctionFactor)) }
    val draft = FunnelSettings(
        vaneDiameterMm = parseDecimal(vane) ?: 0.0,
        hubDiameterMm = parseDecimal(hub) ?: 0.0,
        correctionFactor = parseDecimal(factor) ?: 0.0,
    )
    val valid = draft.vaneDiameterMm > 0 && draft.hubDiameterMm >= 0 && draft.hubDiameterMm < draft.vaneDiameterMm && draft.correctionFactor > 0
    val defaults = FunnelSettings()
    val headColor = MaterialTheme.colorScheme.primary
    val hubColor = AppTheme.colors.warning

    Panel {
        SectionLabel(stringResource(R.string.settings_hood))
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.settings_hood_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        VaneHeadDiagram(
            headLabel = "${stringResource(R.string.hood_diagram_head)} = ${vane.ifBlank { "?" }} mm",
            hubLabel = "${stringResource(R.string.hood_diagram_hub)} = ${hub.ifBlank { "?" }} mm",
            headColor = headColor,
            hubColor = hubColor,
        )
        Text(
            stringResource(R.string.hood_diagram_caption),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(vane, { vane = it }, stringResource(R.string.hood_head_diameter), Modifier.weight(1f))
            NumberField(hub, { hub = it }, stringResource(R.string.hood_hub_diameter), Modifier.weight(1f))
            NumberField(factor, { factor = it }, stringResource(R.string.hood_factor), Modifier.weight(0.8f))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (valid) {
                stringResource(R.string.hood_area, "%.1f".format(draft.freeAreaSquareMeters * 10_000), "%.1f".format(draft.flowCubicMetersPerHour(1.0)))
            } else {
                stringResource(R.string.hood_invalid)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (valid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                enabled = draft.vaneDiameterMm != defaults.vaneDiameterMm || draft.hubDiameterMm != defaults.hubDiameterMm,
                onClick = { vane = formatDecimal(defaults.vaneDiameterMm); hub = formatDecimal(defaults.hubDiameterMm) },
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.hood_reset_wt87b), maxLines = 1) }
            Button(enabled = valid && draft != funnel, onClick = { onSave(draft) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.hood_save), maxLines = 1)
            }
        }
    }
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, maxLines = 1) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@Composable
private fun <T> Segments(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                icon = {},
            ) { Text(label(option), maxLines = 1) }
        }
    }
}
