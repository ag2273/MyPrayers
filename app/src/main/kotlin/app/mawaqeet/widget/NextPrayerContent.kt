package app.mawaqeet.widget

import androidx.annotation.StringRes
import app.mawaqeet.R
import app.mawaqeet.core.DisplayDay
import app.mawaqeet.core.DisplayFormatter
import app.mawaqeet.core.PrayerTime
import java.time.Duration
import java.time.Instant

/**
 * What the Next Prayer widget shows, decided from the core's display day and the shared formatter only. The Glance
 * layer ([NextPrayerWidget]) just lays it out; the countdown itself is a launcher-ticked `Chronometer` started at
 * [untilNext].
 */
data class NextPrayerContent(
    /** The next Prayer's name (Jumu'ah for Friday's Dhuhr). */
    @param:StringRes val nameRes: Int,
    /** Its time, in the user's language and digits. */
    val time: String,
    /** How long until it; never negative. */
    val untilNext: Duration,
) {
    companion object {
        fun of(day: DisplayDay, now: Instant, formatter: DisplayFormatter): NextPrayerContent = NextPrayerContent(
            nameRes = prayerNameRes(day.next),
            time = formatter.time(day.next.time),
            untilNext = Duration.between(now, day.next.time.toInstant()).coerceAtLeast(Duration.ZERO),
        )
    }
}

/** The string a Prayer Time is called by: Friday's Dhuhr is Jumu'ah. */
@StringRes
fun prayerNameRes(prayerTime: PrayerTime): Int =
    if (prayerTime.isJumuah) R.string.prayer_jumuah else prayerNameRes(prayerTime.prayer)
