package app.mawaqeet.core

import java.time.ZonedDateTime

/** Settings → Alerts → «تذكيرات العبادة» → «رمضان»: how Ramadan Mode reckons Imsak. */
data class RamadanSettings(
    /** Imsak is this many minutes before Fajr, within [IMSAK_MINUTES_RANGE]. */
    val imsakMinutes: Int = DEFAULT_IMSAK_MINUTES,
) {
    init {
        require(imsakMinutes in IMSAK_MINUTES_RANGE) { "Imsak is $IMSAK_MINUTES_RANGE minutes before Fajr, not $imsakMinutes" }
    }

    /** Imsak moved by [steps] taps of [IMSAK_MINUTES_STEP] minutes (negative: closer to Fajr), kept within range. */
    fun stepped(steps: Int): RamadanSettings =
        copy(imsakMinutes = (imsakMinutes + steps * IMSAK_MINUTES_STEP).coerceIn(IMSAK_MINUTES_RANGE))

    companion object {
        const val DEFAULT_IMSAK_MINUTES = 10

        /** One tap of the − or + button in Settings. */
        const val IMSAK_MINUTES_STEP = 5
        val IMSAK_MINUTES_RANGE = 0..30
    }
}

/**
 * Where an instant falls in Ramadan Mode. A Fasting Day is a Prayer Day whose Hijri date (with the user's offset) is in
 * Ramadan; its fast runs from Imsak (Fajr − [RamadanSettings.imsakMinutes]) to Maghrib (Iftar).
 */
sealed interface RamadanState {
    /** Not Ramadan Mode: the Next Prayer card and widgets show the next Prayer as usual. */
    data object None : RamadanState

    /** From Imsak to Maghrib on Fasting Day [dayOfRamadan]: counting to [maghrib] (Iftar). */
    data class Fasting(val dayOfRamadan: Int, val imsak: ZonedDateTime, val maghrib: ZonedDateTime) : RamadanState

    /**
     * Suhoor before Fasting Day [dayOfRamadan]: from the previous Maghrib (or midnight) to that day's [imsak], counting
     * to [imsak]. [maghrib] is that Fasting Day's Iftar. Never on the night before Eid.
     */
    data class Suhoor(val dayOfRamadan: Int, val imsak: ZonedDateTime, val maghrib: ZonedDateTime) : RamadanState
}

internal const val RAMADAN_MONTH = 9
