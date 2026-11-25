package cz.skvor.windmeter.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cz.skvor.windmeter.R
import cz.skvor.windmeter.data.Measurement
import cz.skvor.windmeter.data.SavedSession
import cz.skvor.windmeter.data.TemperatureUnit
import cz.skvor.windmeter.data.WindUnit
import cz.skvor.windmeter.ui.components.InspectableChart
import cz.skvor.windmeter.ui.components.MetricColumn
import cz.skvor.windmeter.ui.components.Panel
import cz.skvor.windmeter.ui.components.SectionLabel
import cz.skvor.windmeter.ui.theme.AppTheme
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private enum class Series { Speed, Temperature, Humidity }

@Composable
fun RecordDetailScreen(
    session: SavedSession,
    samples: List<Measurement>,
    windUnit: WindUnit,
    temperatureUnit: TemperatureUnit,
    modifier: Modifier = Modifier,
) {
    var series by rememberSaveable { mutableStateOf(Series.Speed) }
    var selected by rememberSaveable(series) { mutableStateOf<Int?>(null) }
    val time = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
    val dateTime = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

    val colors = AppTheme.colors
    val (values, unit, decimals, color) = when (series) {
        Series.Speed -> SeriesData(samples.map { windUnit.convert(it.windMetersPerSecond) }, windUnit.label, windUnit.decimals, MaterialTheme.colorScheme.primary)
        Series.Temperature -> SeriesData(samples.map { temperatureUnit.fromCelsius(it.temperatureCelsius) }, temperatureUnit.label, 1, colors.supply)
        Series.Humidity -> SeriesData(samples.map { it.relativeHumidity }, "%", 1, colors.exhaust)
    }
    fun fmt(v: Double) = "%.${decimals}f".format(v)

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val duration = if (samples.size > 1) Duration.ofMillis(samples.last().timestampMillis - samples.first().timestampMillis) else Duration.ZERO
        Text(
            "${session.startedAt.format(dateTime)} · ${"%d:%02d".format(duration.toMinutes(), duration.toSecondsPart())} min · ${samples.size}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Series.entries.forEach { s ->
                FilterChip(
                    selected = s == series,
                    onClick = { series = s },
                    label = {
                        Text(
                            stringResource(
                                when (s) {
                                    Series.Speed -> R.string.metric_velocity
                                    Series.Temperature -> R.string.temperature
                                    Series.Humidity -> R.string.humidity
                                },
                            ),
                        )
                    },
                )
            }
        }

        Panel {
            val index = selected
            if (index != null && index in samples.indices) {
                Text(
                    "${fmt(values[index])} $unit",
                    style = MaterialTheme.typography.headlineMedium,
                    color = color,
                )
                Text(
                    time.format(Instant.ofEpochMilli(samples[index].timestampMillis)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(stringResource(R.string.chart_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))
            }
            Spacer(Modifier.height(8.dp))
            InspectableChart(
                values = values,
                color = color,
                selectedIndex = selected,
                onSelect = { selected = it },
                formatAxis = ::fmt,
                formatTooltip = { i -> "${fmt(values[i])} $unit · ${time.format(Instant.ofEpochMilli(samples[i].timestampMillis))}" },
            )
        }

        if (values.isNotEmpty()) {
            Panel {
                SectionLabel(unit)
                Spacer(Modifier.height(8.dp))
                Row {
                    MetricColumn(stringResource(R.string.stat_min), fmt(values.min()), "", Modifier.weight(1f))
                    MetricColumn(stringResource(R.string.stat_avg), fmt(values.average()), "", Modifier.weight(1f))
                    MetricColumn(stringResource(R.string.stat_max), fmt(values.max()), "", Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

private data class SeriesData(val values: List<Double>, val unit: String, val decimals: Int, val color: Color)
