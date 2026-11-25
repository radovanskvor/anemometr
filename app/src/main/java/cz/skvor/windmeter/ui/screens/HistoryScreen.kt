package cz.skvor.windmeter.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cz.skvor.windmeter.R
import cz.skvor.windmeter.data.SavedSession
import cz.skvor.windmeter.data.WindUnit
import cz.skvor.windmeter.ui.components.Panel
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun HistoryScreen(
    sessions: List<SavedSession>,
    windUnit: WindUnit,
    onOpen: (SavedSession) -> Unit,
    onShare: (SavedSession) -> Unit,
    onDelete: (SavedSession) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDelete by remember { mutableStateOf<SavedSession?>(null) }
    val dateFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

    if (sessions.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.no_records_title),
            text = stringResource(R.string.no_records_text),
            modifier = modifier,
        )
    } else {
        LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(sessions, key = { it.file.name }) { session ->
                Panel(onClick = { onOpen(session) }, contentPadding = PaddingValues(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(session.title.ifEmpty { stringResource(R.string.record_default_title) }, style = MaterialTheme.typography.titleMedium)
                            Text(session.startedAt.format(dateFormat), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                stringResource(
                                    R.string.record_summary,
                                    session.sampleCount,
                                    windUnit.format(session.averageWindMetersPerSecond),
                                    windUnit.format(session.maxWindMetersPerSecond),
                                    windUnit.label,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(onClick = { onShare(session) }) { Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.share_csv)) }
                        IconButton(onClick = { pendingDelete = session }) { Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_delete)) }
                    }
                }
            }
        }
    }

    pendingDelete?.let { session ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_record_title),
            text = stringResource(R.string.delete_record_text, session.title.ifEmpty { stringResource(R.string.record_default_title) }),
            onConfirm = { onDelete(session); pendingDelete = null },
            onDismiss = { pendingDelete = null },
        )
    }
}
