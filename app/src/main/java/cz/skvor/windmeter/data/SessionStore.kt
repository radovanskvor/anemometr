package cz.skvor.windmeter.data

import android.content.Context
import cz.skvor.windmeter.R
import java.io.File
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class Measurement(
    val timestampMillis: Long,
    val windMetersPerSecond: Double,
    val temperatureCelsius: Double,
    val relativeHumidity: Double,
)

data class SavedSession(
    val file: File,
    val title: String,
    val startedAt: LocalDateTime,
    val sampleCount: Int,
    val maxWindMetersPerSecond: Double,
    val averageWindMetersPerSecond: Double,
)

/**
 * Keeps recorded sessions as CSV files in app storage, formatted for Excel in the user's language
 * (semicolons where the decimal separator is a comma) with a UTF-8 BOM so diacritics show correctly.
 */
class SessionStore(private val context: Context) {
    private val directory = File(context.filesDir, "sessions").apply { mkdirs() }
    private val fileNameTime = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
    private val cellTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun save(title: String, samples: List<Measurement>): File {
        val startedAt = samples.firstOrNull()?.timestampMillis?.toLocalDateTime() ?: LocalDateTime.now()
        val safeTitle = title.trim().ifEmpty { context.getString(R.string.record_default_title) }
            .replace(Regex("""[\\/:*?"<>|;,]"""), "_")
        val symbols = DecimalFormatSymbols(Locale.getDefault())
        val number = DecimalFormat("0.00", symbols)
        val separator = if (symbols.decimalSeparator == ',') ";" else ","
        val file = File(directory, "${startedAt.format(fileNameTime)}_$safeTitle.csv")
        file.bufferedWriter(Charsets.UTF_8).use { out ->
            out.write("﻿")
            out.write(context.getString(R.string.csv_record_header).replace(";", separator) + "\n")
            for (sample in samples) {
                out.write(
                    listOf(
                        sample.timestampMillis.toLocalDateTime().format(cellTime),
                        number.format(sample.windMetersPerSecond),
                        number.format(sample.temperatureCelsius),
                        number.format(sample.relativeHumidity),
                    ).joinToString(separator, postfix = "\n"),
                )
            }
        }
        return file
    }

    fun list(): List<SavedSession> =
        directory.listFiles { f -> f.extension == "csv" }.orEmpty()
            .mapNotNull { runCatching { summarize(it) }.getOrNull() }
            .sortedByDescending { it.startedAt }

    /** Reads all samples of a record back for the detail chart. */
    fun load(session: SavedSession): List<Measurement> {
        val lines = session.file.readLines(Charsets.UTF_8)
        val separator = if (lines.firstOrNull()?.contains(';') == true) ';' else ','
        // Version 1.0 wrote Czech dates; newer files use ISO-like dates.
        val formats = listOf(cellTime, DateTimeFormatter.ofPattern("d.M.yyyy HH:mm:ss"))
        return lines.drop(1).mapNotNull { line ->
            val cells = line.split(separator)
            if (cells.size < 4) return@mapNotNull null
            val time = formats.firstNotNullOfOrNull { f -> runCatching { LocalDateTime.parse(cells[0], f) }.getOrNull() } ?: return@mapNotNull null
            fun number(i: Int) = cells[i].replace(',', '.').toDoubleOrNull()
            Measurement(
                timestampMillis = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                windMetersPerSecond = number(1) ?: return@mapNotNull null,
                temperatureCelsius = number(2) ?: 0.0,
                relativeHumidity = number(3) ?: 0.0,
            )
        }
    }

    fun delete(session: SavedSession) {
        session.file.delete()
    }

    private fun summarize(file: File): SavedSession {
        val stem = file.nameWithoutExtension
        val startedAt = LocalDateTime.parse(stem.take(19), fileNameTime)
        val lines = file.readLines(Charsets.UTF_8)
        // Files may come from an earlier language setting, so detect the separator from the header.
        val separator = if (lines.firstOrNull()?.contains(';') == true) ';' else ','
        val winds = lines.drop(1)
            .mapNotNull { line -> line.split(separator).getOrNull(1)?.replace(',', '.')?.toDoubleOrNull() }
        return SavedSession(
            file = file,
            title = stem.drop(20),
            startedAt = startedAt,
            sampleCount = winds.size,
            maxWindMetersPerSecond = winds.maxOrNull() ?: 0.0,
            averageWindMetersPerSecond = if (winds.isEmpty()) 0.0 else winds.average(),
        )
    }

    private fun Long.toLocalDateTime(): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault())
}
