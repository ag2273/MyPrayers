package app.mawaqeet.core

import java.time.ZonedDateTime

/**
 * Settings → Alerts → «تذكيرات العبادة» → «رمضان»: how Ramadan Mode reckons Imsak, and the two Ramadan reminders (#91),
 * each off by default and planned only on Fasting Days while the master switch is on.
 */
data class RamadanSettings(
    /** Imsak is this many minutes before Fajr, within [IMSAK_MINUTES_RANGE]. */
    val imsakMinutes: Int = DEFAULT_IMSAK_MINUTES,
    /** «تذكير السحور»: [SuhoorReminder.minutes] before Fajr. */
    val suhoorReminder: SuhoorReminder = SuhoorReminder(),
    /** «تنبيه الإمساك»: at Imsak. */
    val imsakAlert: Boolean = false,
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

/** «تذكير السحور» (#91): a gentle reminder [minutes] before Fajr on each Fasting Day, to wake for suhoor. */
data class SuhoorReminder(
    val enabled: Boolean = false,
    /** One of [MINUTES]; kept while the reminder is off. */
    val minutes: Int = DEFAULT_MINUTES,
) {
    init {
        require(minutes in MINUTES) { "The suhoor reminder comes $MINUTES minutes before Fajr, not $minutes" }
    }

    companion object {
        const val DEFAULT_MINUTES = 60
        val MINUTES = listOf(30, 45, 60, 90, 120)
    }
}

/**
 * Where an instant falls in Ramadan Mode. A Fasting Day is a Prayer Day whose Hijri date (with the user's offset) is in
 * Ramadan; its fast runs from Imsak (Fajr − [RamadanSettings.imsakMinutes]) to Maghrib (Iftar).
 */
sealed interface RamadanState {
    /** Not Ramadan Mode: the Next Prayer card and widgets show the next Prayer as usual. */
    data object None : RamadanState

    /**
     * Ramadan Mode is on, for the [fastingDayNumber]th Fasting Day of Ramadan (its Hijri day), whose fast runs from
     * [imsak] to [maghrib]. What the Today card and the Next Prayer widget count to and draw is decided here, once.
     */
    sealed interface Active : RamadanState {
        val fastingDayNumber: Int
        val imsak: ZonedDateTime
        val maghrib: ZonedDateTime

        /** The moment the countdown runs to: Iftar while fasting, Imsak (the end of suhoor) before it. */
        val countsTo: ZonedDateTime

        /** Where the fast's bar starts (Imsak) while fasting; null during suhoor, which has no fast to show yet. */
        val fastFrom: ZonedDateTime?
    }

    /** From Imsak to Maghrib on the Fasting Day: counting to [maghrib] (Iftar). */
    data class Fasting(
        override val fastingDayNumber: Int,
        override val imsak: ZonedDateTime,
        override val maghrib: ZonedDateTime,
    ) : Active {
        override val countsTo: ZonedDateTime get() = maghrib
        override val fastFrom: ZonedDateTime get() = imsak
    }

    /**
     * Suhoor before the Fasting Day: from the previous Maghrib (or midnight) to that day's [imsak], counting to [imsak].
     * [maghrib] is that Fasting Day's Iftar. Never on the night before Eid.
     */
    data class Suhoor(
        override val fastingDayNumber: Int,
        override val imsak: ZonedDateTime,
        override val maghrib: ZonedDateTime,
    ) : Active {
        override val countsTo: ZonedDateTime get() = imsak
        override val fastFrom: ZonedDateTime? get() = null
    }
}

internal const val RAMADAN_MONTH = 9
