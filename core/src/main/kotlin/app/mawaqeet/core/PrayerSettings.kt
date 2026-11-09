package app.mawaqeet.core

import java.time.ZoneId

/** A place Prayer Times are calculated for, with the time zone its calendar days and wall-clock times follow. */
data class Location(
    val latitude: Double,
    val longitude: Double,
    val zone: ZoneId,
)

/**
 * The named convention of sun angles and rules used to derive Prayer Times, in the order Settings lists them.
 * Stored by name, so entries may be reordered or added but never renamed.
 */
enum class CalculationMethod {
    UMM_AL_QURA,
    EGYPTIAN,
    MUSLIM_WORLD_LEAGUE,
    KARACHI,
    KUWAIT,
    QATAR,
    DUBAI,
    TURKEY,
    SINGAPORE,
    MOON_SIGHTING_COMMITTEE,
    NORTH_AMERICA,
}

/** Which shadow-length rule sets the Asr Prayer Time. Stored by name, like [CalculationMethod]. */
enum class AsrConvention { STANDARD, HANAFI }

/** Everything the user chooses that affects the prayer core's answers. */
data class PrayerSettings(
    val location: Location,
    val method: CalculationMethod,
    val asrConvention: AsrConvention,
) {
    companion object {
        /** Before the user picks a location: Makkah (the Kaaba), Umm al-Qura, Standard Asr. */
        val Default = PrayerSettings(
            location = Location(21.4225, 39.8262, ZoneId.of("Asia/Riyadh")),
            method = CalculationMethod.UMM_AL_QURA,
            asrConvention = AsrConvention.STANDARD,
        )
    }
}
