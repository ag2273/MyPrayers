package app.mawaqeet.widget

import androidx.annotation.StringRes
import app.mawaqeet.R
import app.mawaqeet.core.AdhanSoundChoice
import app.mawaqeet.core.AlertSettings
import app.mawaqeet.core.DisplayDay
import app.mawaqeet.core.DisplayFormatter
import app.mawaqeet.core.Prayer
import app.mawaqeet.core.PrayerTime
import app.mawaqeet.core.RamadanState
import java.time.Duration
import java.time.Instant

/**
 * What the Next Prayer widget shows, decided from the core's display day and the shared formatter only. The Glance
 * layer ([NextPrayerWidget]) just lays it out; the countdown itself is a launcher-ticked `Chronometer` started at
 * [untilNext]. The progress line runs from the previous Prayer ([previousNameRes] at [previousTime]) to the next.
 *
 * In Ramadan Mode (#92) it shows what the Today card's main line shows: «الإفطار» at Maghrib during the fast (the line
 * from Imsak), and «السحور ينتهي» at Imsak from Maghrib on (the line from the previous Prayer).
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
    /** The day of Ramadan while Ramadan Mode counts to iftar or the end of suhoor; null otherwise. */
    val ramadanDay: Int? = null,
) {
    companion object {
        fun of(
            day: DisplayDay,
            now: Instant,
            formatter: DisplayFormatter,
            alerts: AlertSettings = AlertSettings(),
            ramadan: RamadanState = RamadanState.None,
        ): NextPrayerContent = when (ramadan) {
            RamadanState.None -> prayer(day, now, formatter, alerts)
            is RamadanState.Fasting -> prayer(day, now, formatter, alerts).copy(
                nameRes = R.string.widget_iftar,
                time = formatter.time(ramadan.maghrib),
                untilNext = until(now, ramadan.maghrib.toInstant()),
                isTomorrow = false,
                previousNameRes = R.string.ramadan_imsak,
                previousTime = formatter.time(ramadan.imsak),
                progress = progress(ramadan.imsak.toInstant(), ramadan.maghrib.toInstant(), now),
                ramadanDay = ramadan.dayOfRamadan,
            ).withAlertOf(alerts, Prayer.MAGHRIB)
            // The line keeps the previous Prayer's end (Maghrib, Isha, or yesterday's Isha after midnight).
            is RamadanState.Suhoor -> prayer(day, now, formatter, alerts).copy(
                nameRes = R.string.widget_suhoor_ends,
                time = formatter.time(ramadan.imsak),
                untilNext = until(now, ramadan.imsak.toInstant()),
                isTomorrow = false,
                progress = progress(day.previous().time.toInstant(), ramadan.imsak.toInstant(), now),
                alertRes = if (imsakAlert(alerts)) R.string.widget_chip_imsak else R.string.widget_chip_imsak_off,
                audible = imsakAlert(alerts),
                ramadanDay = ramadan.dayOfRamadan,
            )
        }

        /** Whether «تنبيه الإمساك» will sound: its own switch and the master switch. */
        private fun imsakAlert(alerts: AlertSettings) = alerts.enabled && alerts.ramadan.imsakAlert

        /** The Prayer Time before [DisplayDay.next]: before Fajr, yesterday's Isha (from yesterday's own Prayer Times). */
        private fun DisplayDay.previous(): PrayerTime {
            val index = prayerTimes.indexOf(next)
            return when {
                index > 0 -> prayerTimes[index - 1]
                index == 0 -> previousIsha
                else -> prayerTimes.last()
            }
        }

        private fun until(now: Instant, at: Instant) = Duration.between(now, at).coerceAtLeast(Duration.ZERO)

        private fun progress(from: Instant, to: Instant, now: Instant): Float {
            val span = Duration.between(from, to).toMillis().coerceAtLeast(1)
            return (Duration.between(from, now).toMillis().toFloat() / span).coerceIn(0f, 1f)
        }

        private fun NextPrayerContent.withAlertOf(alerts: AlertSettings, prayer: Prayer): NextPrayerContent {
            val alert = alerts.effective(prayer)
            return copy(
                alertRes = when {
                    !alert.adhanAlert -> R.string.widget_chip_off
                    alert.sound == AdhanSoundChoice.SHORT -> R.string.alerts_sound_short
                    alert.sound == AdhanSoundChoice.FULL -> R.string.alerts_sound_full
                    else -> R.string.widget_chip_silent
                },
                audible = alert.adhanAlert && alert.sound != AdhanSoundChoice.SILENT,
            )
        }

        private fun prayer(day: DisplayDay, now: Instant, formatter: DisplayFormatter, alerts: AlertSettings): NextPrayerContent {
            val next = day.next
            val previous = day.previous()
            return NextPrayerContent(
                nameRes = prayerNameRes(next),
                time = formatter.time(next.time),
                untilNext = until(now, next.time.toInstant()),
                isTomorrow = next.time.toLocalDate() != day.date,
                previousNameRes = prayerNameRes(previous),
                previousTime = formatter.time(previous.time),
                progress = progress(previous.time.toInstant(), next.time.toInstant(), now),
            ).withAlertOf(alerts, next.prayer)
        }
    }
}

/** The string a Prayer Time is called by: Friday's Dhuhr is Jumu'ah. */
@StringRes
fun prayerNameRes(prayerTime: PrayerTime): Int =
    if (prayerTime.isJumuah) R.string.prayer_jumuah else prayerNameRes(prayerTime.prayer)
