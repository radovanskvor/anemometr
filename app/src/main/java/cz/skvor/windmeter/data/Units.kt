package cz.skvor.windmeter.data

enum class WindUnit(val label: String, val fromMetersPerSecond: Double, val decimals: Int, val gaugeMax: Double) {
    MetersPerSecond("m/s", 1.0, 2, 30.0),
    KilometersPerHour("km/h", 3.6, 1, 100.0),
    Knots("kn", 3600.0 / 1852.0, 1, 60.0),
    MilesPerHour("mph", 3600.0 / 1609.344, 1, 70.0),
    FeetPerMinute("ft/min", 196.850394, 0, 6000.0);

    fun convert(metersPerSecond: Double) = metersPerSecond * fromMetersPerSecond

    fun format(metersPerSecond: Double): String = "%.${decimals}f".format(convert(metersPerSecond))
}

enum class TemperatureUnit(val label: String) {
    Celsius("°C"),
    Fahrenheit("°F");

    fun fromCelsius(celsius: Double) = if (this == Celsius) celsius else celsius * 9.0 / 5.0 + 32.0
}

/** Languages the app is translated to; an empty tag means "follow the phone". */
enum class AppLanguage(val tag: String, val label: String) {
    System("", ""),
    Czech("cs", "Čeština"),
    English("en", "English"),
    German("de", "Deutsch"),
    Polish("pl", "Polski"),
}

object Beaufort {
    // Upper bounds in m/s of Beaufort 0..11; anything above is 12.
    private val upperBounds = doubleArrayOf(0.3, 1.6, 3.4, 5.5, 8.0, 10.8, 13.9, 17.2, 20.8, 24.5, 28.5, 32.7)

    /** Beaufort number 0..12; its name comes from the localized "beaufort" string array. */
    fun of(metersPerSecond: Double): Int =
        upperBounds.indexOfFirst { metersPerSecond < it }.let { if (it == -1) 12 else it }
}
