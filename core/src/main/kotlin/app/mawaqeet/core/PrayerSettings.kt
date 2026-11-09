package app.mawaqeet.core

import java.time.ZoneId

/** A place Prayer Times are calculated for, with the time zone its calendar days and wall-clock times follow. */
data class Location(
    val latitude: Double,
    val longitude: Double,
    val zone: ZoneId,
)

/** How the stored location was set. Stored by name. A [MANUAL] location never changes by itself (no travel check). */
enum class LocationSource {
    /** From the device's approximate location (#24). */
    AUTOMATIC,

    /** Picked from the city list or typed as coordinates. */
    MANUAL,
}

/**
 * The location the user set, as stored: [location] feeds [PrayerSettings.location]; [city] names it without
 * loading the city list (null if no bundled city is known for it).
 */
data class SavedLocation(
    val location: Location,
    val source: LocationSource,
    val city: CityRef?,
)

/**
 * A bundled city as stored with a location: the city picked, or for typed coordinates the nearest one, whose
 * time zone and country the point uses ([isNear] true: shown as "near <city>").
 */
data class CityRef(
    val id: Long,
    val name: String,
    val arabicName: String?,
    val countryCode: String,
    val isNear: Boolean,
) {
    fun displayName(arabic: Boolean): String = if (arabic) arabicName ?: name else name
}

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
    /** Days added to the Umm al-Qura Hijri date to match local moon sighting, within [HIJRI_OFFSET_RANGE]. */
    val hijriOffsetDays: Int = 0,
    /** Settings → Alerts: per-Prayer Adhan Alerts and Prayer Reminders, delivery and playback. */
    val alerts: AlertSettings = AlertSettings(),
    /**
     * Settings → Prayer Times: minutes added to each Prayer Time to match the local mosque (negative is earlier),
     * each within [ADJUSTMENT_RANGE]. A missing Prayer is not adjusted. Sunrise is never adjusted.
     */
    val adjustments: Map<Prayer, Int> = emptyMap(),
    /** Settings → Advanced: how Fajr and Isha are bounded at high latitudes. */
    val highLatitudeRule: HighLatitudeRule = HighLatitudeRule.AUTOMATIC,
) {
    init {
        require(hijriOffsetDays in HIJRI_OFFSET_RANGE) { "Hijri offset $hijriOffsetDays is outside $HIJRI_OFFSET_RANGE" }
        adjustments.forEach { (prayer, minutes) ->
            require(minutes in ADJUSTMENT_RANGE) { "$prayer adjustment $minutes is outside $ADJUSTMENT_RANGE" }
        }
    }

    /** The minutes added to [prayer]'s time (0 when not adjusted). */
    fun adjustmentOf(prayer: Prayer): Int = adjustments[prayer] ?: 0

    companion object {
        /** The Hijri day offsets the user may choose. */
        val HIJRI_OFFSET_RANGE = -2..2

        /** The per-Prayer adjustments the user may choose, in minutes. */
        val ADJUSTMENT_RANGE = -30..30

        /** Before the user picks a location: Makkah (the Kaaba), Umm al-Qura, Standard Asr. */
        val Default = PrayerSettings(
            location = Location(21.4225, 39.8262, ZoneId.of("Asia/Riyadh")),
            method = CalculationMethod.UMM_AL_QURA,
            asrConvention = AsrConvention.STANDARD,
        )
    }
}
