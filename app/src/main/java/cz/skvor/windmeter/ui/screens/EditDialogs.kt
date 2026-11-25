package cz.skvor.windmeter.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cz.skvor.windmeter.R
import cz.skvor.windmeter.data.AirDirection
import cz.skvor.windmeter.data.Outlet
import cz.skvor.windmeter.data.Project
import cz.skvor.windmeter.data.Room
import cz.skvor.windmeter.ui.components.DirectionBadge

/** Accepts both "60,5" and "60.5", since many keyboards offer the comma. */
fun parseDecimal(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

fun formatDecimal(value: Double): String =
    if (value % 1.0 == 0.0) "%.0f".format(value) else "%.1f".format(value)

@Composable
fun ProjectDialog(initial: Project?, onSave: (Project) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var address by remember { mutableStateOf(initial?.address.orEmpty()) }
    var unit by remember { mutableStateOf(initial?.unit.orEmpty()) }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.new_project else R.string.edit_project)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextInput(name, { name = it }, stringResource(R.string.project_name_hint))
                TextInput(address, { address = it }, stringResource(R.string.project_address_hint))
                TextInput(unit, { unit = it }, stringResource(R.string.project_unit_hint))
                TextInput(note, { note = it }, stringResource(R.string.note_hint), singleLine = false)
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    val base = initial ?: Project(name = name)
                    onSave(base.copy(name = name.trim(), address = address.trim(), unit = unit.trim(), note = note.trim()))
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Room with its design flow and number of outlets per direction; outlets are created from the counts. */
@Composable
fun RoomDialog(
    initial: Room?,
    onSave: (room: Room, counts: Map<AirDirection, Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    var number by remember { mutableStateOf(initial?.number.orEmpty()) }
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    val flows = remember {
        AirDirection.entries.associateWith { d -> mutableStateOf(initial?.designFlow(d)?.takeIf { it > 0 }?.let(::formatDecimal).orEmpty()) }
    }
    val counts = remember {
        AirDirection.entries.associateWith { d -> mutableStateOf(initial?.outlets(d)?.size?.takeIf { it > 0 }?.toString().orEmpty()) }
    }
    fun minCount(d: AirDirection) = initial?.outlets(d)?.count { it.measurements.isNotEmpty() } ?: 0
    fun countOf(d: AirDirection) = counts.getValue(d).value.trim().ifEmpty { "0" }.toIntOrNull()
    fun flowOf(d: AirDirection) = flows.getValue(d).value.trim().ifEmpty { "0" }.let(::parseDecimal)

    val valid = (number.isNotBlank() || name.isNotBlank()) && AirDirection.entries.all { d ->
        val c = countOf(d)
        val f = flowOf(d)
        c != null && c in minCount(d)..50 && f != null && f >= 0
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.new_room else R.string.edit_room)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextInput(number, { number = it }, stringResource(R.string.room_number_hint), keyboardType = KeyboardType.Ascii)
                TextInput(name, { name = it }, stringResource(R.string.room_name_hint))
                AirDirection.entries.forEach { d ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DirectionBadge(d, Modifier.padding(top = 6.dp))
                        TextInput(
                            flows.getValue(d).value, { flows.getValue(d).value = it },
                            "${stringResource(d.label)} [m³/h]",
                            keyboardType = KeyboardType.Decimal,
                            modifier = Modifier.weight(1.4f),
                        )
                        TextInput(
                            counts.getValue(d).value, { counts.getValue(d).value = it.filter(Char::isDigit) },
                            stringResource(R.string.room_count_hint),
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    val min = minCount(d)
                    if (min > 0 && (countOf(d) ?: 0) < min) {
                        Text(stringResource(R.string.room_count_min, min), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
                Text(
                    stringResource(R.string.room_flow_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = {
                    val base = initial ?: Room(number = number, name = name)
                    onSave(
                        base.copy(
                            number = number.trim(),
                            name = name.trim(),
                            supplyFlow = flowOf(AirDirection.Supply) ?: 0.0,
                            exhaustFlow = flowOf(AirDirection.Exhaust) ?: 0.0,
                        ),
                        AirDirection.entries.associateWith { countOf(it) ?: 0 },
                    )
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun OutletDialog(initial: Outlet?, suggestedLabel: String, onSave: (Outlet) -> Unit, onDismiss: () -> Unit) {
    var label by remember { mutableStateOf(initial?.label ?: suggestedLabel) }
    var direction by remember { mutableStateOf(initial?.direction ?: AirDirection.Supply) }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.new_outlet else R.string.edit_outlet)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    AirDirection.entries.forEachIndexed { index, d ->
                        SegmentedButton(
                            selected = d == direction,
                            onClick = { direction = d },
                            shape = SegmentedButtonDefaults.itemShape(index, AirDirection.entries.size),
                        ) { Text(stringResource(d.label)) }
                    }
                }
                TextInput(label, { label = it }, stringResource(R.string.outlet_label_hint))
                TextInput(note, { note = it }, stringResource(R.string.outlet_note_hint), singleLine = false)
            }
        },
        confirmButton = {
            Button(
                enabled = label.isNotBlank(),
                onClick = {
                    val base = initial ?: Outlet(label = label, direction = direction)
                    onSave(base.copy(label = label.trim(), direction = direction, note = note.trim()))
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun ConfirmDeleteDialog(title: String, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun TextInput(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, maxLines = 1) },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, keyboardType = keyboardType),
        modifier = modifier,
    )
}
