package cz.skvor.windmeter.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cz.skvor.windmeter.ble.AnemometerClient
import cz.skvor.windmeter.ble.ConnectionState
import cz.skvor.windmeter.ble.FoundDevice
import cz.skvor.windmeter.data.AirDirection
import cz.skvor.windmeter.data.FlowMeasurement
import cz.skvor.windmeter.data.FunnelSettings
import cz.skvor.windmeter.data.Measurement
import cz.skvor.windmeter.data.Outlet
import cz.skvor.windmeter.data.Project
import cz.skvor.windmeter.data.ProjectRepository
import cz.skvor.windmeter.data.Room
import cz.skvor.windmeter.data.SavedSession
import cz.skvor.windmeter.data.SessionStore
import cz.skvor.windmeter.data.TemperatureUnit
import cz.skvor.windmeter.data.WindUnit
import cz.skvor.windmeter.data.withOutletCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.sqrt

data class WindStats(val min: Double, val average: Double, val max: Double) {
    companion object {
        fun of(samples: List<Measurement>): WindStats? {
            if (samples.isEmpty()) return null
            val winds = samples.map { it.windMetersPerSecond }
            return WindStats(winds.min(), winds.average(), winds.max())
        }
    }
}

data class LiveState(
    val latest: Measurement? = null,
    val windChillWarning: Boolean = false,
    /** Rolling window shown in the chart; resets with the stats. */
    val recent: List<Measurement> = emptyList(),
    val stats: WindStats? = null,
    val isRecording: Boolean = false,
    val recorded: List<Measurement> = emptyList(),
)

/** One averaged flow measurement at an outlet, running or finished. */
data class FlowCapture(
    val projectId: String,
    val outletId: String,
    val durationSeconds: Int,
    val samples: List<Measurement> = emptyList(),
    val elapsedSeconds: Int = 0,
    val isFinished: Boolean = false,
) {
    val averageVelocity: Double? get() = samples.takeIf { it.isNotEmpty() }?.map { it.windMetersPerSecond }?.average()

    /** Spread of the readings relative to their mean; a high value means a gusty, unreliable reading. */
    val variationPercent: Double?
        get() {
            val mean = averageVelocity ?: return null
            if (mean <= 0.0 || samples.size < 2) return null
            val variance = samples.sumOf { (it.windMetersPerSecond - mean).let { d -> d * d } } / (samples.size - 1)
            return sqrt(variance) / mean * 100.0
        }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val client = AnemometerClient(application, viewModelScope)
    private val store = SessionStore(application)
    private val repository = ProjectRepository(application)

    val devices: StateFlow<List<FoundDevice>> = client.devices
    val isScanning: StateFlow<Boolean> = client.isScanning
    val connection: StateFlow<ConnectionState> = client.connection
    val projects: StateFlow<List<Project>> = repository.projects

    private val _live = MutableStateFlow(LiveState())
    val live: StateFlow<LiveState> = _live.asStateFlow()

    private val _windUnit = MutableStateFlow(enumPref("windUnit", WindUnit.MetersPerSecond))
    val windUnit: StateFlow<WindUnit> = _windUnit.asStateFlow()

    /** Live screen shows flow through the hood (m³/h) instead of speed; a view choice, not a speed unit. */
    private val _liveShowsFlow = MutableStateFlow(prefs.getBoolean("liveShowsFlow", false))
    val liveShowsFlow: StateFlow<Boolean> = _liveShowsFlow.asStateFlow()

    private val _temperatureUnit = MutableStateFlow(enumPref("temperatureUnit", TemperatureUnit.Celsius))
    val temperatureUnit: StateFlow<TemperatureUnit> = _temperatureUnit.asStateFlow()

    private val _funnel = MutableStateFlow(loadFunnel())
    val funnel: StateFlow<FunnelSettings> = _funnel.asStateFlow()

    private val _tolerancePercent = MutableStateFlow(prefs.getFloat("tolerancePercent", 10f).toDouble())
    val tolerancePercent: StateFlow<Double> = _tolerancePercent.asStateFlow()

    private val _captureSeconds = MutableStateFlow(prefs.getInt("captureSeconds", 15))
    val captureSeconds: StateFlow<Int> = _captureSeconds.asStateFlow()

    private val _capture = MutableStateFlow<FlowCapture?>(null)
    val capture: StateFlow<FlowCapture?> = _capture.asStateFlow()
    private var captureJob: Job? = null

    private val _sessions = MutableStateFlow<List<SavedSession>>(emptyList())
    val sessions: StateFlow<List<SavedSession>> = _sessions.asStateFlow()

    val lastDeviceName: String? get() = prefs.getString("lastDeviceName", null)
    val isBluetoothEnabled: Boolean get() = client.isBluetoothEnabled

    init {
        viewModelScope.launch {
            client.readings.collect { raw ->
                val sample = Measurement(
                    timestampMillis = System.currentTimeMillis(),
                    windMetersPerSecond = raw.windMetersPerSecond,
                    temperatureCelsius = raw.temperatureCelsius,
                    relativeHumidity = raw.relativeHumidity,
                )
                _live.update { state ->
                    val recent = (state.recent + sample).takeLast(CHART_WINDOW)
                    state.copy(
                        latest = sample,
                        windChillWarning = raw.windChillWarning,
                        recent = recent,
                        stats = WindStats.of(recent),
                        recorded = if (state.isRecording) state.recorded + sample else state.recorded,
                    )
                }
                _capture.update { c -> if (c != null && !c.isFinished) c.copy(samples = c.samples + sample) else c }
            }
        }
        viewModelScope.launch { repository.load() }
        refreshSessions()
    }

    // region Connection
    fun startScan() = client.startScan()

    fun stopScan() = client.stopScan()

    fun connect(device: FoundDevice) {
        prefs.edit().putString("lastDeviceAddress", device.address).putString("lastDeviceName", device.name).apply()
        client.connect(device.address, device.name)
    }

    /** Reconnects to the meter used last time, so the user doesn't have to search again. */
    fun connectToLastDevice(): Boolean {
        val address = prefs.getString("lastDeviceAddress", null) ?: return false
        client.connect(address, lastDeviceName ?: address)
        return true
    }

    fun disconnect() = client.disconnect()
    // endregion

    // region Quick measuring and recordings
    fun resetStats() = _live.update { it.copy(recent = emptyList(), stats = null) }

    fun startRecording() = _live.update { it.copy(isRecording = true, recorded = emptyList()) }

    fun stopRecording(title: String) {
        val samples = _live.value.recorded
        _live.update { it.copy(isRecording = false, recorded = emptyList()) }
        if (samples.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.save(title, samples) }
            refreshSessions()
        }
    }

    fun discardRecording() = _live.update { it.copy(isRecording = false, recorded = emptyList()) }

    fun deleteSession(session: SavedSession) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.delete(session) }
            refreshSessions()
        }
    }

    private val _openRecord = MutableStateFlow<Pair<SavedSession, List<Measurement>>?>(null)
    val openRecord: StateFlow<Pair<SavedSession, List<Measurement>>?> = _openRecord.asStateFlow()

    fun openRecord(session: SavedSession) {
        viewModelScope.launch {
            _openRecord.value = session to withContext(Dispatchers.IO) { store.load(session) }
        }
    }

    fun closeRecord() {
        _openRecord.value = null
    }

    private fun refreshSessions() {
        viewModelScope.launch { _sessions.value = withContext(Dispatchers.IO) { store.list() } }
    }
    // endregion

    // region Projects
    fun saveProject(project: Project) = launchUpdate { list ->
        if (list.any { it.id == project.id }) list.map { if (it.id == project.id) project else it } else list + project
    }

    fun deleteProject(projectId: String) = launchUpdate { list -> list.filterNot { it.id == projectId } }

    /** Saves room data and creates or removes outlets so each direction has the requested count. */
    fun saveRoom(projectId: String, room: Room, counts: Map<AirDirection, Int>, labelPrefixes: Map<AirDirection, String>) =
        updateProject(projectId) { p ->
            val existing = p.rooms.firstOrNull { it.id == room.id }
            var updated = room.copy(outlets = existing?.outlets ?: emptyList())
            for (direction in AirDirection.entries) {
                updated = updated.withOutletCount(direction, counts[direction] ?: 0, labelPrefixes[direction].orEmpty())
            }
            val rooms = if (existing != null) p.rooms.map { if (it.id == room.id) updated else it } else p.rooms + updated
            p.copy(rooms = rooms)
        }

    fun deleteRoom(projectId: String, roomId: String) = updateProject(projectId) { p -> p.copy(rooms = p.rooms.filterNot { it.id == roomId }) }

    fun saveOutlet(projectId: String, roomId: String, outlet: Outlet) = updateProject(projectId) { p ->
        p.copy(
            rooms = p.rooms.map { room ->
                if (room.id != roomId) return@map room
                val outlets = if (room.outlets.any { it.id == outlet.id }) {
                    room.outlets.map { if (it.id == outlet.id) outlet.copy(measurements = it.measurements) else it }
                } else {
                    room.outlets + outlet
                }
                room.copy(outlets = outlets)
            },
        )
    }

    fun deleteOutlet(projectId: String, outletId: String) = updateProject(projectId) { p ->
        p.copy(rooms = p.rooms.map { r -> r.copy(outlets = r.outlets.filterNot { it.id == outletId }) })
    }

    fun deleteMeasurement(projectId: String, outletId: String, timestampMillis: Long) = updateOutlet(projectId, outletId) { o ->
        o.copy(measurements = o.measurements.filterNot { it.timestampMillis == timestampMillis })
    }

    fun exportProject(project: Project): File = repository.exportCsv(project, _tolerancePercent.value)

    private fun updateProject(projectId: String, transform: (Project) -> Project) = launchUpdate { list ->
        list.map { if (it.id == projectId) transform(it) else it }
    }

    private fun updateOutlet(projectId: String, outletId: String, transform: (Outlet) -> Outlet) = updateProject(projectId) { p ->
        p.copy(rooms = p.rooms.map { r -> r.copy(outlets = r.outlets.map { if (it.id == outletId) transform(it) else it }) })
    }

    private fun launchUpdate(transform: (List<Project>) -> List<Project>) {
        viewModelScope.launch { repository.update(transform) }
    }
    // endregion

    // region Flow capture
    fun startCapture(projectId: String, outletId: String) {
        captureJob?.cancel()
        val seconds = _captureSeconds.value
        _capture.value = FlowCapture(projectId, outletId, seconds)
        captureJob = viewModelScope.launch {
            for (elapsed in 1..seconds) {
                delay(1_000)
                _capture.update { it?.copy(elapsedSeconds = elapsed) }
            }
            _capture.update { it?.copy(isFinished = true) }
        }
    }

    fun cancelCapture() {
        captureJob?.cancel()
        _capture.value = null
    }

    fun saveCapture() {
        val capture = _capture.value ?: return
        val velocity = capture.averageVelocity ?: return
        val samples = capture.samples
        val measurement = FlowMeasurement(
            timestampMillis = System.currentTimeMillis(),
            flowCubicMetersPerHour = _funnel.value.flowCubicMetersPerHour(velocity),
            averageVelocity = velocity,
            durationSeconds = capture.durationSeconds,
            temperatureCelsius = samples.map { it.temperatureCelsius }.average(),
            relativeHumidity = samples.map { it.relativeHumidity }.average(),
        )
        updateOutlet(capture.projectId, capture.outletId) { it.copy(measurements = it.measurements + measurement) }
        _capture.value = null
    }
    // endregion

    // region Settings
    fun setWindUnit(unit: WindUnit) {
        _windUnit.value = unit
        _liveShowsFlow.value = false
        prefs.edit().putString("windUnit", unit.name).putBoolean("liveShowsFlow", false).apply()
    }

    fun showLiveFlow() {
        _liveShowsFlow.value = true
        prefs.edit().putBoolean("liveShowsFlow", true).apply()
    }

    fun setTemperatureUnit(unit: TemperatureUnit) {
        _temperatureUnit.value = unit
        prefs.edit().putString("temperatureUnit", unit.name).apply()
    }

    fun setFunnel(settings: FunnelSettings) {
        _funnel.value = settings
        prefs.edit()
            .putFloat("funnelHeadMm", settings.vaneDiameterMm.toFloat())
            .putFloat("funnelHubMm", settings.hubDiameterMm.toFloat())
            .putFloat("funnelK", settings.correctionFactor.toFloat())
            // Old keys held the guessed 70 mm default; drop them so they can never come back.
            .remove("funnelVane")
            .remove("funnelHub")
            .remove("funnelConfirmed")
            .apply()
    }

    fun setTolerance(percent: Double) {
        _tolerancePercent.value = percent
        prefs.edit().putFloat("tolerancePercent", percent.toFloat()).apply()
    }

    fun setCaptureSeconds(seconds: Int) {
        _captureSeconds.value = seconds
        prefs.edit().putInt("captureSeconds", seconds).apply()
    }

    private fun loadFunnel(): FunnelSettings {
        val defaults = FunnelSettings()
        // New keys on purpose: values saved under the old keys were based on a guess, so
        // existing installs start from the measured WT87B dimensions.
        return FunnelSettings(
            vaneDiameterMm = prefs.getFloat("funnelHeadMm", defaults.vaneDiameterMm.toFloat()).toRounded(),
            hubDiameterMm = prefs.getFloat("funnelHubMm", defaults.hubDiameterMm.toFloat()).toRounded(),
            correctionFactor = prefs.getFloat("funnelK", defaults.correctionFactor.toFloat()).toRounded(),
        )
    }

    // Prefs store floats, so 26.7 comes back as 26.700000762; round it back to what the user typed.
    private fun Float.toRounded(): Double = Math.round(this * 1000.0) / 1000.0
    // endregion

    private inline fun <reified T : Enum<T>> enumPref(key: String, default: T): T =
        prefs.getString(key, null)?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default

    override fun onCleared() {
        client.disconnect()
    }

    private companion object {
        const val CHART_WINDOW = 120
    }
}
