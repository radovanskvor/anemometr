package cz.skvor.windmeter.data

import androidx.annotation.StringRes
import cz.skvor.windmeter.R
import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs

enum class AirDirection(@StringRes val label: Int, @StringRes val shortLabel: Int) {
    Supply(R.string.direction_supply, R.string.direction_supply_short),
    Exhaust(R.string.direction_exhaust, R.string.direction_exhaust_short),
}

data class FlowMeasurement(
    val timestampMillis: Long,
    val flowCubicMetersPerHour: Double,
    val averageVelocity: Double,
    val durationSeconds: Int,
    val temperatureCelsius: Double?,
    val relativeHumidity: Double?,
)

data class Outlet(
    val id: String = newId(),
    val label: String,
    val direction: AirDirection,
    /** Free text such as valve position after balancing. */
    val note: String = "",
    val measurements: List<FlowMeasurement> = emptyList(),
) {
    val lastMeasurement: FlowMeasurement? get() = measurements.maxByOrNull { it.timestampMillis }
    val measuredFlow: Double? get() = lastMeasurement?.flowCubicMetersPerHour
}

/**
 * The design flow belongs to the room, as in ventilation drawings; each outlet's target is an
 * equal share of the room flow for its direction.
 */
data class Room(
    val id: String = newId(),
    val number: String,
    val name: String,
    val supplyFlow: Double = 0.0,
    val exhaustFlow: Double = 0.0,
    val outlets: List<Outlet> = emptyList(),
) {
    val title: String get() = listOf(number, name).filter { it.isNotBlank() }.joinToString(" ")

    fun designFlow(direction: AirDirection) = if (direction == AirDirection.Supply) supplyFlow else exhaustFlow

    fun outlets(direction: AirDirection) = outlets.filter { it.direction == direction }

    fun designFlowOf(outlet: Outlet): Double {
        val count = outlets(outlet.direction).size
        return if (count == 0) 0.0 else designFlow(outlet.direction) / count
    }

    fun deviationOf(outlet: Outlet): Double? = outlet.measuredFlow?.let { deviationPercent(it, designFlowOf(outlet)) }

    fun totals(direction: AirDirection): FlowTotals {
        val list = outlets(direction)
        return FlowTotals(
            design = designFlow(direction),
            measured = list.sumOf { it.measuredFlow ?: 0.0 },
            outletCount = list.size,
            measuredCount = list.count { it.measuredFlow != null },
        )
    }
}

data class Project(
    val id: String = newId(),
    val name: String,
    val address: String = "",
    val unit: String = "",
    val note: String = "",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val rooms: List<Room> = emptyList(),
) {
    val outlets: List<Outlet> get() = rooms.flatMap { it.outlets }

    fun totals(direction: AirDirection): FlowTotals {
        val parts = rooms.map { it.totals(direction) }
        return FlowTotals(
            design = parts.sumOf { it.design },
            measured = parts.sumOf { it.measured },
            outletCount = parts.sumOf { it.outletCount },
            measuredCount = parts.sumOf { it.measuredCount },
        )
    }

    fun findOutlet(outletId: String): Pair<Room, Outlet>? =
        rooms.firstNotNullOfOrNull { room -> room.outlets.firstOrNull { it.id == outletId }?.let { room to it } }
}

data class FlowTotals(val design: Double, val measured: Double, val outletCount: Int, val measuredCount: Int) {
    val isComplete: Boolean get() = outletCount > 0 && measuredCount == outletCount
    val deviationPercent: Double? get() = if (measuredCount == 0) null else deviationPercent(measured, design)
}

enum class FlowStatus { NotMeasured, Ok, Warning, Bad }

fun deviationPercent(measured: Double, design: Double): Double? =
    if (design <= 0.0) null else (measured - design) / design * 100.0

fun flowStatus(deviation: Double?, tolerancePercent: Double): FlowStatus = when {
    deviation == null -> FlowStatus.NotMeasured
    abs(deviation) <= tolerancePercent -> FlowStatus.Ok
    abs(deviation) <= tolerancePercent * 2 -> FlowStatus.Warning
    else -> FlowStatus.Bad
}

/**
 * Brings the outlets of one direction to [count]: new ones are appended with numbered labels,
 * surplus ones are removed from the end, but measured outlets are never removed here.
 */
fun Room.withOutletCount(direction: AirDirection, count: Int, labelPrefix: String): Room {
    val current = outlets(direction)
    val others = outlets.filter { it.direction != direction }
    val adjusted = when {
        count > current.size -> current + (current.size + 1..count).map { Outlet(label = "$labelPrefix$it", direction = direction) }
        count < current.size -> {
            val removable = current.filter { it.measurements.isEmpty() }.takeLast(current.size - count).toSet()
            current.filterNot { it in removable }
        }
        else -> current
    }
    return copy(outlets = others + adjusted)
}

/**
 * Geometry of the measuring hood. The hood channels the whole outlet flow through the vane head,
 * so flow = mean velocity in the head x free area of the head x correction factor.
 */
data class FunnelSettings(
    val vaneDiameterMm: Double = WT87B_HEAD_DIAMETER_MM,
    val hubDiameterMm: Double = WT87B_HUB_DIAMETER_MM,
    val correctionFactor: Double = 1.0,
) {
    companion object {
        // Defaults for the WT87B; its vane dimensions are not published, so they were measured
        // with a caliper on a real meter. Still editable in Settings for other units.
        const val WT87B_HEAD_DIAMETER_MM = 64.5
        const val WT87B_HUB_DIAMETER_MM = 26.7
    }

    val freeAreaSquareMeters: Double
        get() {
            val outer = vaneDiameterMm / 1000.0
            val hub = hubDiameterMm / 1000.0
            return PI / 4.0 * (outer * outer - hub * hub)
        }

    fun flowCubicMetersPerHour(velocityMetersPerSecond: Double): Double =
        velocityMetersPerSecond * freeAreaSquareMeters * correctionFactor * 3600.0
}

private fun newId() = UUID.randomUUID().toString()
