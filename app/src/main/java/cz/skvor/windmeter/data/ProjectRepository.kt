package cz.skvor.windmeter.data

import android.content.Context
import cz.skvor.windmeter.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Stores all projects in one JSON file; a household-sized project is a few kilobytes. */
class ProjectRepository(private val context: Context) {
    private val file = File(context.filesDir, "projects.json")
    private val exportDirectory = File(context.filesDir, "exports").apply { mkdirs() }
    private val mutex = Mutex()

    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            _projects.value = if (file.exists()) {
                runCatching { decodeProjects(JSONArray(file.readText())) }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
        }
    }

    suspend fun update(transform: (List<Project>) -> List<Project>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = transform(_projects.value)
            _projects.value = updated
            // Write to a temp file first so a crash mid-write never destroys the measurements.
            val temp = File(file.parentFile, "projects.json.tmp")
            temp.writeText(encodeProjects(updated).toString())
            temp.renameTo(file) || run { file.delete(); temp.renameTo(file) }
        }
    }

    /**
     * Measurement report for Excel in the user's language. Where the decimal separator is a comma
     * (Czech, Polish, German), Excel expects semicolons between columns, otherwise commas.
     */
    fun exportCsv(project: Project, tolerancePercent: Double): File {
        val locale = Locale.getDefault()
        val symbols = DecimalFormatSymbols(locale)
        val separator = if (symbols.decimalSeparator == ',') ";" else ","
        val one = DecimalFormat("0.0", symbols)
        val two = DecimalFormat("0.00", symbols)
        val time = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale).withZone(ZoneId.systemDefault())
        fun s(id: Int) = context.getString(id)
        fun cell(text: String) = if (text.contains(separator) || text.contains('"')) "\"${text.replace("\"", "\"\"")}\"" else text
        fun row(vararg cells: String) = cells.joinToString(separator) { cell(it) } + "\n"

        val safeName = project.name.replace(Regex("""[\\/:*?"<>|;,]"""), "_")
        val out = File(exportDirectory, "protocol_$safeName.csv")
        out.bufferedWriter(Charsets.UTF_8).use { w ->
            w.write("﻿")
            w.write(row(s(R.string.csv_title), project.name))
            if (project.address.isNotBlank()) w.write(row(s(R.string.csv_address), project.address))
            if (project.unit.isNotBlank()) w.write(row(s(R.string.csv_unit), project.unit))
            w.write(row(s(R.string.csv_tolerance), "±${one.format(tolerancePercent)} %"))
            w.write("\n")
            w.write(row(*s(R.string.csv_header).split(';').toTypedArray()))
            for (room in project.rooms) {
                for (direction in AirDirection.entries) {
                    val outlets = room.outlets(direction)
                    if (outlets.isEmpty() && room.designFlow(direction) == 0.0) continue
                    for (outlet in outlets) {
                        val last = outlet.lastMeasurement
                        val deviation = room.deviationOf(outlet)
                        w.write(
                            row(
                                room.title,
                                outlet.label,
                                s(direction.label),
                                one.format(room.designFlowOf(outlet)),
                                last?.let { one.format(it.flowCubicMetersPerHour) } ?: "",
                                deviation?.let { one.format(it) } ?: "",
                                statusText(flowStatus(deviation, tolerancePercent)),
                                last?.let { two.format(it.averageVelocity) } ?: "",
                                last?.let { time.format(Instant.ofEpochMilli(it.timestampMillis)) } ?: "",
                                outlet.note,
                            ),
                        )
                    }
                    val totals = room.totals(direction)
                    w.write(
                        row(
                            room.title,
                            s(R.string.csv_room_total),
                            s(direction.label),
                            one.format(totals.design),
                            if (totals.measuredCount > 0) one.format(totals.measured) else "",
                            totals.deviationPercent?.let { one.format(it) } ?: "",
                            statusText(flowStatus(totals.deviationPercent, tolerancePercent)),
                            "", "", "",
                        ),
                    )
                }
            }
            w.write("\n")
            for (direction in AirDirection.entries) {
                val totals = project.totals(direction)
                w.write(row(s(R.string.csv_total), "", s(direction.label), one.format(totals.design), one.format(totals.measured)))
            }
        }
        return out
    }

    private fun statusText(status: FlowStatus) = context.getString(
        when (status) {
            FlowStatus.NotMeasured -> R.string.status_not_measured
            FlowStatus.Ok -> R.string.status_ok
            FlowStatus.Warning -> R.string.status_warning
            FlowStatus.Bad -> R.string.status_bad
        },
    )

    private fun encodeProjects(projects: List<Project>) = JSONArray().apply {
        projects.forEach { p ->
            put(
                JSONObject()
                    .put("id", p.id).put("name", p.name).put("address", p.address).put("unit", p.unit)
                    .put("note", p.note).put("createdAt", p.createdAtMillis)
                    .put("rooms", JSONArray().apply { p.rooms.forEach { put(encodeRoom(it)) } }),
            )
        }
    }

    private fun encodeRoom(room: Room) = JSONObject()
        .put("id", room.id).put("number", room.number).put("name", room.name)
        .put("supplyFlow", room.supplyFlow).put("exhaustFlow", room.exhaustFlow)
        .put(
            "outlets",
            JSONArray().apply {
                room.outlets.forEach { o ->
                    put(
                        JSONObject()
                            .put("id", o.id).put("label", o.label).put("direction", o.direction.name).put("note", o.note)
                            .put(
                                "measurements",
                                JSONArray().apply {
                                    o.measurements.forEach { m ->
                                        put(
                                            JSONObject()
                                                .put("t", m.timestampMillis).put("flow", m.flowCubicMetersPerHour)
                                                .put("velocity", m.averageVelocity).put("duration", m.durationSeconds)
                                                .putOpt("temperature", m.temperatureCelsius).putOpt("humidity", m.relativeHumidity),
                                        )
                                    }
                                },
                            ),
                    )
                }
            },
        )

    private fun decodeProjects(array: JSONArray): List<Project> = array.objects().map { p ->
        Project(
            id = p.getString("id"),
            name = p.getString("name"),
            address = p.optString("address"),
            unit = p.optString("unit"),
            note = p.optString("note"),
            createdAtMillis = p.optLong("createdAt"),
            rooms = p.optJSONArray("rooms")?.objects()?.map(::decodeRoom).orEmpty(),
        )
    }

    private fun decodeRoom(r: JSONObject): Room {
        val outletJson = r.optJSONArray("outlets")?.objects().orEmpty()
        val outlets = outletJson.map { o ->
            Outlet(
                id = o.getString("id"),
                label = o.optString("label"),
                direction = AirDirection.valueOf(o.getString("direction")),
                note = o.optString("note"),
                measurements = o.optJSONArray("measurements")?.objects()?.map { m ->
                    FlowMeasurement(
                        timestampMillis = m.getLong("t"),
                        flowCubicMetersPerHour = m.getDouble("flow"),
                        averageVelocity = m.optDouble("velocity", 0.0),
                        durationSeconds = m.optInt("duration"),
                        temperatureCelsius = m.optDoubleOrNull("temperature"),
                        relativeHumidity = m.optDoubleOrNull("humidity"),
                    )
                }.orEmpty(),
            )
        }
        // Version 2.0 stored the design flow per outlet; the room flow is their sum.
        fun legacyFlow(direction: AirDirection) =
            outletJson.filter { it.optString("direction") == direction.name }.sumOf { it.optDouble("designFlow", 0.0) }
        return Room(
            id = r.getString("id"),
            number = r.optString("number"),
            name = r.optString("name"),
            supplyFlow = r.optDoubleOrNull("supplyFlow") ?: legacyFlow(AirDirection.Supply),
            exhaustFlow = r.optDoubleOrNull("exhaustFlow") ?: legacyFlow(AirDirection.Exhaust),
            outlets = outlets,
        )
    }
}

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

private fun JSONObject.optDoubleOrNull(key: String): Double? = if (has(key) && !isNull(key)) getDouble(key) else null
