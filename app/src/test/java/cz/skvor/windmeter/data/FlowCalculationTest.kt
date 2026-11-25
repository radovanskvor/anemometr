package cz.skvor.windmeter.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FlowCalculationTest {
    @Test
    fun flowIsVelocityTimesFreeAreaTimesFactor() {
        val funnel = FunnelSettings(vaneDiameterMm = 100.0, hubDiameterMm = 0.0, correctionFactor = 1.0)
        // A = pi/4 * 0.1^2 = 0.007854 m2; 2 m/s -> 0.015708 m3/s -> 56.55 m3/h
        assertEquals(56.55, funnel.flowCubicMetersPerHour(2.0), 0.01)
    }

    @Test
    fun hubAndCorrectionReduceFlow() {
        val funnel = FunnelSettings(vaneDiameterMm = 100.0, hubDiameterMm = 50.0, correctionFactor = 0.9)
        assertEquals(56.55 * 0.75 * 0.9, funnel.flowCubicMetersPerHour(2.0), 0.01)
    }

    @Test
    fun defaultsAreTheMeasuredWt87bHead() {
        // pi/4 * (0.0645^2 - 0.0267^2) = 0.0027076 m2 -> 1 m/s = 9.75 m3/h
        val funnel = FunnelSettings()
        assertEquals(27.08, funnel.freeAreaSquareMeters * 10_000, 0.01)
        assertEquals(9.75, funnel.flowCubicMetersPerHour(1.0), 0.01)
    }

    @Test
    fun statusFollowsTolerance() {
        assertEquals(FlowStatus.Ok, flowStatus(deviationPercent(55.0, 60.0), 10.0))
        assertEquals(FlowStatus.Warning, flowStatus(deviationPercent(48.0, 60.0), 10.0))
        assertEquals(FlowStatus.Bad, flowStatus(deviationPercent(40.0, 60.0), 10.0))
        assertEquals(FlowStatus.NotMeasured, flowStatus(null, 10.0))
    }

    @Test
    fun roomFlowIsSharedByOutletsAndSummed() {
        val p1 = Outlet(label = "P1", direction = AirDirection.Supply, measurements = listOf(
            FlowMeasurement(1, 20.0, 1.0, 15, null, null),
            FlowMeasurement(2, 29.0, 1.4, 15, null, null),
        ))
        val p2 = Outlet(label = "P2", direction = AirDirection.Supply, measurements = listOf(FlowMeasurement(3, 33.0, 1.6, 15, null, null)))
        val room = Room(number = "1.05", name = "Living", supplyFlow = 60.0, outlets = listOf(p1, p2))

        assertEquals(30.0, room.designFlowOf(p1), 1e-9)
        val totals = room.totals(AirDirection.Supply)
        assertEquals(62.0, totals.measured, 1e-9)
        assertEquals(60.0, totals.design, 1e-9)
        assertEquals(0, room.totals(AirDirection.Exhaust).outletCount)

        val project = Project(name = "Test", rooms = listOf(room))
        assertEquals(62.0, project.totals(AirDirection.Supply).measured, 1e-9)
    }

    @Test
    fun outletCountAddsAndKeepsMeasuredOutlets() {
        val measured = Outlet(label = "P1", direction = AirDirection.Supply, measurements = listOf(FlowMeasurement(1, 20.0, 1.0, 15, null, null)))
        val room = Room(number = "1", name = "", supplyFlow = 90.0, outlets = listOf(measured))

        val three = room.withOutletCount(AirDirection.Supply, 3, "P")
        assertEquals(listOf("P1", "P2", "P3"), three.outlets.map { it.label })

        // Reducing to zero removes only the unmeasured ones.
        val reduced = three.withOutletCount(AirDirection.Supply, 0, "P")
        assertEquals(listOf("P1"), reduced.outlets.map { it.label })
    }
}
