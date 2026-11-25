package cz.skvor.windmeter.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cz.skvor.windmeter.R
import cz.skvor.windmeter.data.AirDirection
import cz.skvor.windmeter.data.Outlet
import cz.skvor.windmeter.data.Project
import cz.skvor.windmeter.data.Room
import cz.skvor.windmeter.data.flowStatus
import cz.skvor.windmeter.ui.components.DirectionBadge
import cz.skvor.windmeter.ui.components.FlowBar
import cz.skvor.windmeter.ui.components.Panel
import cz.skvor.windmeter.ui.components.SectionLabel
import cz.skvor.windmeter.ui.components.StatusPill
import cz.skvor.windmeter.ui.components.formatFlow
import cz.skvor.windmeter.ui.theme.AppTheme
import kotlin.math.abs

@Composable
fun ProjectDetailScreen(
    project: Project,
    tolerancePercent: Double,
    onOpenOutlet: (Outlet) -> Unit,
    onSaveRoom: (Room, Map<AirDirection, Int>) -> Unit,
    onDeleteRoom: (Room) -> Unit,
    onSaveOutlet: (Room, Outlet) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingRoom by remember { mutableStateOf<Room?>(null) }
    var deletingRoom by remember { mutableStateOf<Room?>(null) }
    var addingOutletTo by remember { mutableStateOf<Room?>(null) }

    LazyColumn(
        modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { BalancePanel(project, tolerancePercent) }

        if (project.rooms.isEmpty()) {
            item {
                Panel {
                    Text(stringResource(R.string.no_rooms_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.no_rooms_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        items(project.rooms, key = { it.id }) { room ->
            RoomPanel(
                room = room,
                tolerancePercent = tolerancePercent,
                onOpenOutlet = onOpenOutlet,
                onAddOutlet = { addingOutletTo = room },
                onEdit = { editingRoom = room },
                onDelete = { deletingRoom = room },
            )
        }
    }

    editingRoom?.let { room ->
        RoomDialog(room, onSave = { r, counts -> onSaveRoom(r, counts); editingRoom = null }, onDismiss = { editingRoom = null })
    }
    deletingRoom?.let { room ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_room_title),
            text = stringResource(R.string.delete_room_text, room.title, room.outlets.size),
            onConfirm = { onDeleteRoom(room); deletingRoom = null },
            onDismiss = { deletingRoom = null },
        )
    }
    addingOutletTo?.let { room ->
        val prefix = stringResource(AirDirection.Supply.shortLabel)
        OutletDialog(
            initial = null,
            suggestedLabel = "$prefix${room.outlets(AirDirection.Supply).size + 1}",
            onSave = { onSaveOutlet(room, it); addingOutletTo = null },
            onDismiss = { addingOutletTo = null },
        )
    }
}

@Composable
private fun BalancePanel(project: Project, tolerancePercent: Double) {
    val supply = project.totals(AirDirection.Supply)
    val exhaust = project.totals(AirDirection.Exhaust)
    Panel {
        if (project.address.isNotBlank() || project.unit.isNotBlank()) {
            Text(
                listOf(project.address, project.unit).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            listOf(AirDirection.Supply to supply, AirDirection.Exhaust to exhaust).forEach { (direction, totals) ->
                val color = AppTheme.colors.of(direction)
                Column(Modifier.weight(1f)) {
                    SectionLabel(stringResource(direction.label))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(formatFlow(totals.measured), style = MaterialTheme.typography.displaySmall, color = color)
                        Spacer(Modifier.width(4.dp))
                        Text("/ ${formatFlow(totals.design)}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                    }
                    Text(
                        stringResource(R.string.flow_outlets_summary, totals.measuredCount, totals.outletCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowBar(totals.measured.takeIf { totals.measuredCount > 0 }, totals.design, color, Modifier.fillMaxWidth().height(6.dp))
                }
            }
        }
        if (supply.measuredCount > 0 && exhaust.measuredCount > 0) {
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = AppTheme.colors.cardBorder)
            Spacer(Modifier.height(12.dp))
            val balance = supply.measured - exhaust.measured
            val reference = maxOf(supply.measured, exhaust.measured, 1.0)
            val balanceOk = abs(balance) / reference * 100 <= tolerancePercent
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.balance), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    "%+.0f m³/h".format(balance),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (balanceOk) AppTheme.colors.ok else AppTheme.colors.warning,
                )
            }
        }
    }
}

@Composable
private fun RoomPanel(
    room: Room,
    tolerancePercent: Double,
    onOpenOutlet: (Outlet) -> Unit,
    onAddOutlet: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Panel(contentPadding = PaddingValues(top = 6.dp, bottom = 6.dp)) {
        Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (room.number.isNotBlank()) {
                    Text(room.number, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Text(room.name.ifBlank { room.number }, style = MaterialTheme.typography.titleMedium)
            }
            Row {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.room_options)) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_edit)) }, onClick = { menuOpen = false; onEdit() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { menuOpen = false; onDelete() })
                }
            }
        }

        // Room total per direction: the sum of measured outlets against the room's design flow.
        AirDirection.entries.forEach { direction ->
            val totals = room.totals(direction)
            if (totals.design <= 0.0 && totals.outletCount == 0) return@forEach
            val color = AppTheme.colors.of(direction)
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.room_flow_line, stringResource(direction.label), formatFlow(totals.measured), formatFlow(totals.design)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = color,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    StatusPill(flowStatus(totals.deviationPercent, tolerancePercent), totals.deviationPercent)
                }
                Spacer(Modifier.height(6.dp))
                FlowBar(totals.measured.takeIf { totals.measuredCount > 0 }, totals.design, color, Modifier.fillMaxWidth().height(5.dp))
            }
        }

        room.outlets.sortedBy { it.direction }.forEach { outlet ->
            HorizontalDivider(color = AppTheme.colors.cardBorder)
            OutletRow(room, outlet, tolerancePercent, onClick = { onOpenOutlet(outlet) })
        }
        HorizontalDivider(color = AppTheme.colors.cardBorder)
        TextButton(onClick = onAddOutlet, modifier = Modifier.padding(start = 6.dp)) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.add_outlet))
        }
    }
}

@Composable
private fun OutletRow(room: Room, outlet: Outlet, tolerancePercent: Double, onClick: () -> Unit) {
    val deviation = room.deviationOf(outlet)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DirectionBadge(outlet.direction)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(outlet.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.design_share, formatFlow(room.designFlowOf(outlet))),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(outlet.measuredFlow?.let { "${formatFlow(it)} m³/h" } ?: "–", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            StatusPill(flowStatus(deviation, tolerancePercent), deviation)
        }
    }
}
