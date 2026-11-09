package app.mawaqeet.core

import kotlin.math.abs

/**
 * How Fajr and Isha are bounded where the sun never gets low enough for their angles (Settings → Advanced).
 * Stored by name, so entries may be reordered or added but never renamed.
 */
enum class HighLatitudeRule {
    /** [SEVENTH_OF_THE_NIGHT] above [AUTOMATIC_LATITUDE] (north or south), else [MIDDLE_OF_THE_NIGHT]. */
    AUTOMATIC,

    /** Fajr is never earlier, and Isha never later, than the middle of the night. */
    MIDDLE_OF_THE_NIGHT,

    /** Fajr is never earlier than the last seventh of the night, and Isha never later than the first seventh. */
    SEVENTH_OF_THE_NIGHT,

    /** Like [SEVENTH_OF_THE_NIGHT], with the night portion set by the method's angle (angle ÷ 60). */
    TWILIGHT_ANGLE;

    /** The rule in effect at [latitude]: never [AUTOMATIC]. */
    fun resolve(latitude: Double): HighLatitudeRule = when {
        this != AUTOMATIC -> this
        abs(latitude) > AUTOMATIC_LATITUDE -> SEVENTH_OF_THE_NIGHT
        else -> MIDDLE_OF_THE_NIGHT
    }

    companion object {
        /** Above this latitude (degrees, north or south) [AUTOMATIC] uses the seventh of the night. */
        const val AUTOMATIC_LATITUDE = 48.0
    }
}

/**
 * Why a day's Fajr or Isha is not the one its Calculation Method's angle gives (Home shows a short note).
 *
 * [rule] (never [HighLatitudeRule.AUTOMATIC]) set Fajr, Isha or both. [polar] is true when the sun does not rise
 * or does not set at the location that day: every Prayer Time then comes from [POLAR_FALLBACK_LATITUDE] on the
 * same meridian (the nearest latitude where they can be calculated), with [rule] applied there.
 */
data class HighLatitudeAdjustment(val rule: HighLatitudeRule, val polar: Boolean) {
    companion object {
        /** The latitude (degrees, toward the equator from the location) used when the sun does not rise or set. */
        const val POLAR_FALLBACK_LATITUDE = 65.0
    }
}
