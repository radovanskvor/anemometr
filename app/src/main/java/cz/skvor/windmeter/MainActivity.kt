package cz.skvor.windmeter

import android.Manifest
import android.app.LocaleManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.skvor.windmeter.ble.ConnectionState
import cz.skvor.windmeter.data.AirDirection
import cz.skvor.windmeter.data.AppLanguage
import cz.skvor.windmeter.ui.MainViewModel
import cz.skvor.windmeter.ui.components.ConnectionChip
import cz.skvor.windmeter.ui.screens.ConfirmDeleteDialog
import cz.skvor.windmeter.ui.screens.DevicesSheet
import cz.skvor.windmeter.ui.screens.HistoryScreen
import cz.skvor.windmeter.ui.screens.LiveScreen
import cz.skvor.windmeter.ui.screens.OutletDialog
import cz.skvor.windmeter.ui.screens.OutletMeasureScreen
import cz.skvor.windmeter.ui.screens.ProjectDetailScreen
import cz.skvor.windmeter.ui.screens.ProjectDialog
import cz.skvor.windmeter.ui.screens.ProjectsScreen
import cz.skvor.windmeter.ui.screens.RecordDetailScreen
import cz.skvor.windmeter.ui.screens.RoomDialog
import cz.skvor.windmeter.ui.screens.SettingsScreen
import cz.skvor.windmeter.ui.theme.WindMeterTheme
import java.io.File

private enum class Tab(val title: Int, val selectedIcon: ImageVector, val icon: ImageVector) {
    Projects(R.string.tab_projects, Icons.Filled.Folder, Icons.Outlined.Folder),
    Live(R.string.tab_live, Icons.Filled.Air, Icons.Outlined.Air),
    History(R.string.tab_records, Icons.Filled.History, Icons.Outlined.History),
    Settings(R.string.tab_settings, Icons.Filled.Settings, Icons.Outlined.Settings),
}

private val bluetoothPermissions: Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WindMeterTheme { WindMeterApp(viewModel) }
        }
    }
}

/** Per-app language exists from Android 13; older phones simply follow the system language. */
private fun currentLanguage(context: Context): AppLanguage? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
    if (locales.isEmpty) return AppLanguage.System
    val tag = locales[0].language
    return AppLanguage.entries.firstOrNull { it.tag == tag } ?: AppLanguage.System
}

private fun setLanguage(context: Context, language: AppLanguage) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    context.getSystemService(LocaleManager::class.java).applicationLocales =
        if (language == AppLanguage.System) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(language.tag)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WindMeterApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val live by viewModel.live.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val windUnit by viewModel.windUnit.collectAsStateWithLifecycle()
    val liveShowsFlow by viewModel.liveShowsFlow.collectAsStateWithLifecycle()
    val temperatureUnit by viewModel.temperatureUnit.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val funnel by viewModel.funnel.collectAsStateWithLifecycle()
    val tolerance by viewModel.tolerancePercent.collectAsStateWithLifecycle()
    val captureSeconds by viewModel.captureSeconds.collectAsStateWithLifecycle()
    val capture by viewModel.capture.collectAsStateWithLifecycle()
    val openRecord by viewModel.openRecord.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(Tab.Projects) }
    var openProjectId by rememberSaveable { mutableStateOf<String?>(null) }
    var openOutletId by rememberSaveable { mutableStateOf<String?>(null) }
    var showDevices by rememberSaveable { mutableStateOf(false) }
    var language by remember { mutableStateOf(currentLanguage(context)) }

    // Dialog state lives here because the top bar and FAB open the dialogs.
    var creatingProject by remember { mutableStateOf(false) }
    var editingProject by remember { mutableStateOf(false) }
    var deletingProject by remember { mutableStateOf(false) }
    var addingRoom by remember { mutableStateOf(false) }
    var editingOutlet by remember { mutableStateOf(false) }
    var deletingOutlet by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    val project = projects.firstOrNull { it.id == openProjectId }
    val roomAndOutlet = openOutletId?.let { id -> project?.findOutlet(id) }
    val labelPrefixes = AirDirection.entries.associateWith { stringResource(it.shortLabel) }

    fun closeOutlet() {
        viewModel.cancelCapture()
        openOutletId = null
    }

    BackHandler(enabled = openOutletId != null || openProjectId != null || openRecord != null) {
        when {
            openRecord != null -> viewModel.closeRecord()
            openOutletId != null -> closeOutlet()
            else -> openProjectId = null
        }
    }

    fun hasPermissions() = bluetoothPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    val enableBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (viewModel.isBluetoothEnabled) {
            showDevices = true
            viewModel.startScan()
        }
    }

    fun openDevicePicker() {
        if (!viewModel.isBluetoothEnabled) {
            enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }
        showDevices = true
        viewModel.startScan()
    }

    val requestPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) {
            openDevicePicker()
        } else {
            Toast.makeText(context, context.getString(R.string.permission_denied), Toast.LENGTH_LONG).show()
        }
    }

    fun onConnectClick() {
        if (connection !is ConnectionState.Disconnected) {
            tab = Tab.Settings
            openOutletId = null
            openProjectId = null
            viewModel.closeRecord()
            return
        }
        if (hasPermissions()) openDevicePicker() else requestPermissions.launch(bluetoothPermissions)
    }

    // Reconnect to the last meter on start so measuring is one tap away.
    LaunchedEffect(Unit) {
        if (hasPermissions() && viewModel.isBluetoothEnabled && connection is ConnectionState.Disconnected) {
            viewModel.connectToLastDevice()
        }
    }

    val record = openRecord
    val title = when {
        record != null -> record.first.title.ifEmpty { stringResource(R.string.record_detail) }
        roomAndOutlet != null -> roomAndOutlet.second.label
        project != null -> project.name
        tab == Tab.Projects -> stringResource(R.string.title_projects)
        else -> stringResource(tab.title)
    }
    val isTopLevel = openProjectId == null && record == null

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = {
                    if (!isTopLevel) {
                        IconButton(onClick = {
                            when {
                                record != null -> viewModel.closeRecord()
                                openOutletId != null -> closeOutlet()
                                else -> openProjectId = null
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                },
                actions = {
                    ConnectionChip(connection, onClick = ::onConnectClick)
                    when {
                        record != null -> IconButton(onClick = {
                            shareFile(context, record.first.file, context.getString(R.string.share_subject_record, record.first.title))
                        }) { Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.share_csv)) }
                        project != null -> {
                            if (roomAndOutlet == null) {
                                IconButton(onClick = {
                                    shareFile(context, viewModel.exportProject(project), context.getString(R.string.share_subject_protocol, project.name))
                                }) { Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.action_share_protocol)) }
                            }
                            Row {
                                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.action_more)) }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    if (roomAndOutlet != null) {
                                        DropdownMenuItem(text = { Text(stringResource(R.string.edit_outlet)) }, onClick = { menuOpen = false; editingOutlet = true })
                                        DropdownMenuItem(text = { Text(stringResource(R.string.delete_outlet)) }, onClick = { menuOpen = false; deletingOutlet = true })
                                    } else {
                                        DropdownMenuItem(text = { Text(stringResource(R.string.edit_project)) }, onClick = { menuOpen = false; editingProject = true })
                                        DropdownMenuItem(text = { Text(stringResource(R.string.delete_project)) }, onClick = { menuOpen = false; deletingProject = true })
                                    }
                                }
                            }
                        }
                        else -> Spacer(Modifier.width(12.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            when {
                isTopLevel && tab == Tab.Projects -> ExtendedFloatingActionButton(
                    onClick = { creatingProject = true },
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.fab_project)) },
                )
                record == null && project != null && roomAndOutlet == null -> ExtendedFloatingActionButton(
                    onClick = { addingRoom = true },
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.fab_room)) },
                )
            }
        },
        bottomBar = {
            if (isTopLevel) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Tab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = { Icon(if (tab == item) item.selectedIcon else item.icon, contentDescription = null) },
                            label = { Text(stringResource(item.title), maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
                        )
                    }
                }
            }
        },
    ) { padding ->
        val contentModifier = Modifier.padding(padding)
        when {
            record != null -> RecordDetailScreen(
                session = record.first,
                samples = record.second,
                windUnit = windUnit,
                temperatureUnit = temperatureUnit,
                modifier = contentModifier,
            )
            project != null && roomAndOutlet != null -> OutletMeasureScreen(
                room = roomAndOutlet.first,
                outlet = roomAndOutlet.second,
                live = live,
                connection = connection,
                funnel = funnel,
                tolerancePercent = tolerance,
                captureSeconds = captureSeconds,
                capture = capture?.takeIf { it.outletId == roomAndOutlet.second.id },
                onConnectClick = ::onConnectClick,
                onStartCapture = { viewModel.startCapture(project.id, roomAndOutlet.second.id) },
                onCancelCapture = viewModel::cancelCapture,
                onSaveCapture = viewModel::saveCapture,
                onDeleteMeasurement = { viewModel.deleteMeasurement(project.id, roomAndOutlet.second.id, it.timestampMillis) },
                modifier = contentModifier,
            )
            project != null -> ProjectDetailScreen(
                project = project,
                tolerancePercent = tolerance,
                onOpenOutlet = { openOutletId = it.id },
                onSaveRoom = { room, counts -> viewModel.saveRoom(project.id, room, counts, labelPrefixes) },
                onDeleteRoom = { viewModel.deleteRoom(project.id, it.id) },
                onSaveOutlet = { room, outlet -> viewModel.saveOutlet(project.id, room.id, outlet) },
                modifier = contentModifier,
            )
            else -> when (tab) {
                Tab.Projects -> ProjectsScreen(projects, onOpen = { openProjectId = it.id }, modifier = contentModifier)
                Tab.Live -> LiveScreen(
                    live = live,
                    connection = connection,
                    windUnit = windUnit,
                    showsFlow = liveShowsFlow,
                    temperatureUnit = temperatureUnit,
                    funnel = funnel,
                    onConnectClick = ::onConnectClick,
                    onWindUnitChange = viewModel::setWindUnit,
                    onShowFlow = viewModel::showLiveFlow,
                    onResetStats = viewModel::resetStats,
                    onStartRecording = viewModel::startRecording,
                    onStopRecording = viewModel::stopRecording,
                    onDiscardRecording = viewModel::discardRecording,
                    modifier = contentModifier,
                )
                Tab.History -> HistoryScreen(
                    sessions = sessions,
                    windUnit = windUnit,
                    onOpen = viewModel::openRecord,
                    onShare = { shareFile(context, it.file, context.getString(R.string.share_subject_record, it.title)) },
                    onDelete = viewModel::deleteSession,
                    modifier = contentModifier,
                )
                Tab.Settings -> SettingsScreen(
                    windUnit = windUnit,
                    temperatureUnit = temperatureUnit,
                    funnel = funnel,
                    tolerancePercent = tolerance,
                    captureSeconds = captureSeconds,
                    connection = connection,
                    language = language,
                    onWindUnitChange = viewModel::setWindUnit,
                    onTemperatureUnitChange = viewModel::setTemperatureUnit,
                    onFunnelChange = {
                        viewModel.setFunnel(it)
                        Toast.makeText(context, context.getString(R.string.hood_saved), Toast.LENGTH_SHORT).show()
                    },
                    onToleranceChange = viewModel::setTolerance,
                    onCaptureSecondsChange = viewModel::setCaptureSeconds,
                    onLanguageChange = {
                        language = it
                        setLanguage(context, it)
                    },
                    onDisconnect = viewModel::disconnect,
                    modifier = contentModifier,
                )
            }
        }
    }

    if (creatingProject) {
        ProjectDialog(null, onSave = { viewModel.saveProject(it); creatingProject = false; openProjectId = it.id }, onDismiss = { creatingProject = false })
    }
    if (editingProject && project != null) {
        ProjectDialog(project, onSave = { viewModel.saveProject(it); editingProject = false }, onDismiss = { editingProject = false })
    }
    if (deletingProject && project != null) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_project_title),
            text = stringResource(R.string.delete_project_text, project.name),
            onConfirm = { viewModel.deleteProject(project.id); deletingProject = false; openProjectId = null },
            onDismiss = { deletingProject = false },
        )
    }
    if (addingRoom && project != null) {
        RoomDialog(
            null,
            onSave = { room, counts -> viewModel.saveRoom(project.id, room, counts, labelPrefixes); addingRoom = false },
            onDismiss = { addingRoom = false },
        )
    }
    if (editingOutlet && project != null && roomAndOutlet != null) {
        OutletDialog(
            initial = roomAndOutlet.second,
            suggestedLabel = roomAndOutlet.second.label,
            onSave = { viewModel.saveOutlet(project.id, roomAndOutlet.first.id, it); editingOutlet = false },
            onDismiss = { editingOutlet = false },
        )
    }
    if (deletingOutlet && project != null && roomAndOutlet != null) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_outlet_title),
            text = stringResource(R.string.delete_outlet_text, roomAndOutlet.second.label, roomAndOutlet.second.measurements.size),
            onConfirm = { viewModel.deleteOutlet(project.id, roomAndOutlet.second.id); deletingOutlet = false; closeOutlet() },
            onDismiss = { deletingOutlet = false },
        )
    }

    if (showDevices) {
        DevicesSheet(
            devices = devices,
            isScanning = isScanning,
            lastDeviceName = viewModel.lastDeviceName,
            onRescan = viewModel::startScan,
            onConnectLast = {
                showDevices = false
                viewModel.connectToLastDevice()
            },
            onSelect = {
                showDevices = false
                viewModel.connect(it)
            },
            onDismiss = {
                showDevices = false
                viewModel.stopScan()
            },
        )
    }
}

private fun shareFile(context: Context, file: File, subject: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, subject)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.share_chooser)))
}
