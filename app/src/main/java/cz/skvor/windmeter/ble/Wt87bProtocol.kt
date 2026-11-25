package cz.skvor.windmeter.ble

import java.util.UUID

/**
 * Wire format of the Wintact WT87B anemometer, learned from the vendor app for interoperability.
 *
 * Commands are ASCII "WH", a command byte and ";" written to [WRITE_CHARACTERISTIC].
 * Answers arrive as notifications on [NOTIFY_CHARACTERISTIC].
 */
object Wt87bProtocol {
    const val ADVERTISED_NAME = "Anemometer"
    val SERVICE: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
    val NOTIFY_CHARACTERISTIC: UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
    val WRITE_CHARACTERISTIC: UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")
    val CLIENT_CONFIG_DESCRIPTOR: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private const val LIVE_FRAME_LENGTH = 17
    private const val FRAME_START = 0xFF.toByte()

    /** The meter only reports a reading when asked, so the app sends this about once a second. */
    val REQUEST_LIVE_READING = command(0x01)

    private fun command(code: Int) = byteArrayOf('W'.code.toByte(), 'H'.code.toByte(), code.toByte(), ';'.code.toByte())

    /**
     * Decodes one live reading, or returns null for any other notification.
     *
     * Layout (byte index): 4-5 wind speed in 0.01 m/s, 6 unit shown on the meter's display,
     * 7-8 temperature in 0.1 degree, 9 temperature sign (1 = negative), 10 temperature unit
     * (0 = C, 1 = F), 11-12 relative humidity in 0.1 %, 15 wind-chill warning flag.
     */
    fun parseLiveReading(frame: ByteArray): RawReading? {
        if (frame.size != LIVE_FRAME_LENGTH || frame[0] != FRAME_START) return null

        val windMetersPerSecond = frame.u16(4) * 0.01
        val temperatureMagnitude = frame.u16(7) * 0.1
        val isNegative = frame[9].toInt() == 1
        val humidity = frame.u16(11) * 0.1

        return RawReading(
            windMetersPerSecond = windMetersPerSecond,
            temperature = if (isNegative) -temperatureMagnitude else temperatureMagnitude,
            isFahrenheit = frame[10].toInt() == 1,
            relativeHumidity = humidity,
            windChillWarning = frame[15].toInt() == 1,
        )
    }

    private fun ByteArray.u16(index: Int): Int =
        ((this[index].toInt() and 0xFF) shl 8) or (this[index + 1].toInt() and 0xFF)
}

data class RawReading(
    val windMetersPerSecond: Double,
    val temperature: Double,
    val isFahrenheit: Boolean,
    val relativeHumidity: Double,
    val windChillWarning: Boolean,
) {
    val temperatureCelsius: Double
        get() = if (isFahrenheit) (temperature - 32.0) * 5.0 / 9.0 else temperature
}
