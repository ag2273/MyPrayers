package app.mawaqeet.core

import java.time.ZoneId

/** A place Prayer Times are calculated for, with the time zone its calendar days and wall-clock times follow. */
data class Location(
    val latitude: Double,
    val longitude: Double,
    val zone: ZoneId,
)

/** The named convention of sun angles and rules used to derive Prayer Times. More methods arrive with #16. */
enum class CalculationMethod { UMM_AL_QURA }

/** Which shadow-length rule sets the Asr Prayer Time. */
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
