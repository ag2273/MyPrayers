package app.mawaqeet.widget

import androidx.annotation.StringRes
import app.mawaqeet.R
import app.mawaqeet.core.AdhanSoundChoice
import app.mawaqeet.core.AlertSettings
import app.mawaqeet.core.DisplayDay
import app.mawaqeet.core.DisplayFormatter
import app.mawaqeet.core.PrayerTime
import java.time.Duration
import java.time.Instant

/**
 * What the Next Prayer widget shows, decided from the core's display day and the shared formatter only. The Glance
 * layer ([NextPrayerWidget]) just lays it out; the countdown itself is a launcher-ticked `Chronometer` started at
 * [untilNext]. The progress line runs from the previous Prayer ([previousNameRes] at [previousTime]) to the next.
 */
data class NextPrayerContent(
    /** The next Prayer's name (Jumu'ah for Friday's Dhuhr). */
    @param:StringRes val nameRes: Int,
    /** Its time, in the user's language and digits. */
    val time: String,
    /** How long until it; never negative. */
    val untilNext: Duration,
    /** Whether it is tomorrow's Fajr (after Isha). */
    val isTomorrow: Boolean = false,
    @param:StringRes val previousNameRes: Int = nameRes,
    val previousTime: String = time,
    /** From the previous Prayer to the next: 0 at the previous one, 1 at the next. */
    val progress: Float = 0f,
    /** The kind of alert the next Prayer has, for the 4×2's chip: «أذان قصير», «صامت»… */
    @param:StringRes val alertRes: Int = R.string.alerts_sound_short,
    /** Whether that alert makes a sound (a bell, else a struck-out bell). */
    val audible: Boolean = true,
) {
    companion object {
        fun of(day: DisplayDay, now: Instant, formatter: DisplayFormatter, alerts: AlertSettings = AlertSettings()): NextPrayerContent {
            val next = day.next
            val index = day.prayerTimes.indexOf(next)
            // Before Fajr the previous Prayer is yesterday's Isha: today's, a day earlier, to within minutes.
            val previous: PrayerTime = when {
                index > 0 -> day.prayerTimes[index - 1]
                index == 0 -> day.prayerTimes.last().let { it.copy(time = it.time.minusDays(1)) }
                else -> day.prayerTimes.last()
            }
            val span = Duration.between(previous.time.toInstant(), next.time.toInstant()).toMillis().coerceAtLeast(1)
            val done = Duration.between(previous.time.toInstant(), now).toMillis()
            val alert = alerts.effective(next.prayer)
            return NextPrayerContent(
                nameRes = prayerNameRes(next),
                time = formatter.time(next.time),
                untilNext = Duration.between(now, next.time.toInstant()).coerceAtLeast(Duration.ZERO),
                isTomorrow = next.time.toLocalDate() != day.date,
                previousNameRes = prayerNameRes(previous),
                previousTime = formatter.time(previous.time),
                progress = (done.toFloat() / span).coerceIn(0f, 1f),
                alertRes = when {
                    !alert.adhanAlert -> R.string.widget_chip_off
                    alert.sound == AdhanSoundChoice.SHORT -> R.string.alerts_sound_short
                    alert.sound == AdhanSoundChoice.FULL -> R.string.alerts_sound_full
                    else -> R.string.widget_chip_silent
                },
                audible = alert.adhanAlert && alert.sound != AdhanSoundChoice.SILENT,
            )
        }
    }
}

/** The string a Prayer Time is called by: Friday's Dhuhr is Jumu'ah. */
@StringRes
fun prayerNameRes(prayerTime: PrayerTime): Int =
    if (prayerTime.isJumuah) R.string.prayer_jumuah else prayerNameRes(prayerTime.prayer)
