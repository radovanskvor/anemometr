package cz.skvor.windmeter.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cz.skvor.windmeter.R
import cz.skvor.windmeter.ble.FoundDevice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesSheet(
    devices: List<FoundDevice>,
    isScanning: Boolean,
    lastDeviceName: String?,
    onRescan: () -> Unit,
    onConnectLast: () -> Unit,
    onSelect: (FoundDevice) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.choose_meter), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onRescan, enabled = !isScanning) { Text(stringResource(R.string.scan_again)) }
            }
            Text(
                stringResource(R.string.devices_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            if (isScanning) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp))

            if (lastDeviceName != null) {
                ListItem(
                    headlineContent = { Text(lastDeviceName) },
                    supportingContent = { Text(stringResource(R.string.last_used)) },
                    leadingContent = { Icon(Icons.Filled.Air, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable(onClick = onConnectLast),
                )
            }

            LazyColumn {
                items(devices, key = { it.address }) { device ->
                    ListItem(
                        headlineContent = {
                            Text(
                                device.name,
                                fontWeight = if (device.isWt87b) FontWeight.Bold else null,
                                color = if (device.isWt87b) MaterialTheme.colorScheme.primary else Color.Unspecified,
                            )
                        },
                        supportingContent = {
                            Text(
                                when {
                                    device.isWt87b -> stringResource(R.string.wt87b_meter, device.rssi)
                                    device.looksLikeAnemometer -> stringResource(R.string.likely_meter, device.rssi)
                                    else -> "${device.address} · ${device.rssi} dBm"
                                },
                            )
                        },
                        colors = if (device.isWt87b) {
                            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                        } else {
                            ListItemDefaults.colors()
                        },
                        leadingContent = {
                            Icon(
                                if (device.looksLikeAnemometer) Icons.Filled.Air else Icons.Outlined.Bluetooth,
                                contentDescription = null,
                                tint = if (device.looksLikeAnemometer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        modifier = Modifier.clickable { onSelect(device) },
                    )
                }
            }
            if (!isScanning && devices.isEmpty()) {
                Text(
                    stringResource(R.string.nothing_found),
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
