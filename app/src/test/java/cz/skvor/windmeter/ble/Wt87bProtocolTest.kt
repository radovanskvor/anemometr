package cz.skvor.windmeter.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Wt87bProtocolTest {
    private fun frame(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun parsesLiveReading() {
        // 1.24 m/s, 21.8 °C, 54.4 %RH (values from the vendor app screenshot).
        val reading = Wt87bProtocol.parseLiveReading(
            frame(0xFF, 0, 0, 0, 0x00, 0x7C, 0, 0x00, 0xDA, 0, 0, 0x02, 0x20, 0, 0, 0, 0),
        )!!
        assertEquals(1.24, reading.windMetersPerSecond, 1e-9)
        assertEquals(21.8, reading.temperatureCelsius, 1e-9)
        assertEquals(54.4, reading.relativeHumidity, 1e-9)
        assertFalse(reading.windChillWarning)
    }

    @Test
    fun appliesNegativeTemperatureSign() {
        val reading = Wt87bProtocol.parseLiveReading(
            frame(0xFF, 0, 0, 0, 0, 0, 0, 0x00, 0x32, 1, 0, 0, 0, 0, 0, 1, 0),
        )!!
        assertEquals(-5.0, reading.temperature, 1e-9)
        assertTrue(reading.windChillWarning)
    }

    @Test
    fun convertsFahrenheitToCelsius() {
        val reading = Wt87bProtocol.parseLiveReading(
            frame(0xFF, 0, 0, 0, 0, 0, 0, 0x02, 0x3A, 0, 1, 0, 0, 0, 0, 0, 0),
        )!!
        assertEquals(57.0, reading.temperature, 1e-9)
        assertEquals(13.888, reading.temperatureCelsius, 1e-3)
    }

    @Test
    fun ignoresOtherFrames() {
        assertNull(Wt87bProtocol.parseLiveReading(frame(0xFF, 1, 2)))
        assertNull(Wt87bProtocol.parseLiveReading(ByteArray(17)))
    }

    @Test
    fun liveRequestMatchesMeterCommand() {
        assertEquals(listOf(0x57, 0x48, 0x01, 0x3B), Wt87bProtocol.REQUEST_LIVE_READING.map { it.toInt() })
    }
}
