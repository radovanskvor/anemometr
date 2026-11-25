package cz.skvor.windmeter.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cz.skvor.windmeter.R
import cz.skvor.windmeter.data.AirDirection
import cz.skvor.windmeter.data.Project
import cz.skvor.windmeter.ui.components.FlowBar
import cz.skvor.windmeter.ui.components.Panel
import cz.skvor.windmeter.ui.components.SectionLabel
import cz.skvor.windmeter.ui.components.formatFlow
import cz.skvor.windmeter.ui.theme.AppTheme

@Composable
fun ProjectsScreen(projects: List<Project>, onOpen: (Project) -> Unit, modifier: Modifier = Modifier) {
    if (projects.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.empty_projects_title),
            text = stringResource(R.string.empty_projects_text),
            modifier = modifier,
        )
        return
    }
    LazyColumn(
        modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(projects.sortedByDescending { it.createdAtMillis }, key = { it.id }) { project ->
            ProjectCard(project, onClick = { onOpen(project) })
        }
    }
}

@Composable
private fun ProjectCard(project: Project, onClick: () -> Unit) {
    Panel(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(project.name, style = MaterialTheme.typography.titleLarge)
                val subtitle = listOf(project.address, project.unit).filter { it.isNotBlank() }.joinToString(" · ")
                if (subtitle.isNotEmpty()) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AirDirection.entries.forEach { direction ->
                val totals = project.totals(direction)
                val color = AppTheme.colors.of(direction)
                Column(Modifier.weight(1f)) {
                    SectionLabel(stringResource(direction.label))
                    Text(
                        "${formatFlow(totals.measured)} / ${formatFlow(totals.design)} m³/h",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (totals.outletCount == 0) MaterialTheme.colorScheme.onSurfaceVariant else color,
                    )
                    Spacer(Modifier.height(6.dp))
                    FlowBar(
                        measured = totals.measured.takeIf { totals.measuredCount > 0 },
                        design = totals.design,
                        color = color,
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.measured_x_of_y, totals.measuredCount, totals.outletCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyState(title: String, text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.Air, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.width(1.dp))
        }
    }
}
