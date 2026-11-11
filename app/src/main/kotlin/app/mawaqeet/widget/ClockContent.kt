package app.mawaqeet.widget

import androidx.annotation.StringRes
import app.mawaqeet.R
import app.mawaqeet.core.DisplayDay
import app.mawaqeet.core.DisplayFormatter
import app.mawaqeet.core.Prayer
import app.mawaqeet.core.PrayerTime
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** Morning or evening, for the Clock's own «ص»/«م» marker in 12-hour time. */
enum class Meridiem { AM, PM }

/** One end of the Clock's interval: a Prayer or Sunrise. */
data class Stop(
    /** Its name: a Prayer (Jumu'ah for Friday's Dhuhr) or Sunrise. */
    @param:StringRes val nameRes: Int,
    /** Its time, `HH:mm` in the user's digits. */
    val time: String,
    val at: ZonedDateTime,
    /** The Prayer, or null for Sunrise (never a Prayer: no alert, never logged). */
    val prayer: Prayer?,
)

/**
 * What every Clock Widget shows (Big Clock, Gold Clock, Clock & Prayer, at 4×2 and 4×1), decided from the core's
 * display day, now and the shared formatter only. The Stops are the five Prayers plus Sunrise; before Fajr the
 * interval starts at yesterday's Isha, after Isha it runs to tomorrow's Fajr. Each widget only lays this out.
 */
data class ClockContent(
    val previous: Stop,
    val next: Stop,
    /** The share of the interval gone: 0 at [previous], towards 1 at [next]. */
    val elapsed: Float,
    /** Time left to [next], rounded up to the whole minute so it never reads zero before the Stop. */
    val untilNext: Duration,
    /** [untilNext] in hours and minutes: «١ س ١٠ د» / `1h 10m`. */
    val countdown: String,
    /** The time now, as the clock face shows it (no marker): «٣:٠٥» / `15:05`. */
    val clockTime: String,
    /** In 12-hour time, morning or evening; null in 24-hour time. */
    val meridiem: Meridiem?,
    /** The Hijri date with the user's offset. */
    val hijriDate: String,
    /** The Gregorian weekday, day and month. */
    val gregorianDate: String,
) {
    /** The date line: Hijri, then Gregorian. */
    val dateLine: String get() = "$hijriDate · $gregorianDate"

    /** When this content goes stale after [now]: the next whole minute (clock and countdown), or [next] if sooner. */
    fun nextChange(now: Instant): Instant = minOf(now.truncatedTo(ChronoUnit.MINUTES).plus(Duration.ofMinutes(1)), next.at.toInstant())

    companion object {
        /**
         * [now] is in the phone's time zone (the clock face shows the phone's time); the Stops keep the location's.
         * [twentyFourHour] is the phone's 12/24-hour setting.
         */
        fun of(day: DisplayDay, now: ZonedDateTime, formatter: DisplayFormatter, twentyFourHour: Boolean): ClockContent {
            val stops = stopsAround(day)
            val instant = now.toInstant()
            val nextIndex = stops.indexOfFirst { it.at.toInstant().isAfter(instant) }.let { if (it < 1) stops.lastIndex else it }
            val previous = stops[nextIndex - 1]
            val next = stops[nextIndex]
            val span = Duration.between(previous.at.toInstant(), next.at.toInstant()).toMillis().coerceAtLeast(1)
            val done = Duration.between(previous.at.toInstant(), instant).toMillis()
            val left = Duration.between(instant, next.at.toInstant()).coerceAtLeast(Duration.ZERO)
            val untilNext = Duration.ofMinutes((left.toMillis() + 59_999) / 60_000)
            fun Stop.formatted() = copy(time = formatter.time(at))
            return ClockContent(
                previous = previous.formatted(),
                next = next.formatted(),
                elapsed = (done.toFloat() / span).coerceIn(0f, 1f),
                untilNext = untilNext,
                countdown = formatter.hoursMinutes(untilNext),
                clockTime = formatter.clockTime(now, twentyFourHour),
                meridiem = if (twentyFourHour) null else if (now.hour < 12) Meridiem.AM else Meridiem.PM,
                hijriDate = formatter.hijriDate(day.hijriDate),
                gregorianDate = formatter.shortGregorianDate(day.date),
            )
        }

        /** Yesterday's Isha, the day's Prayers with Sunrise after Fajr, and the core's next Prayer when it's tomorrow. */
        private fun stopsAround(day: DisplayDay): List<Stop> {
            fun stop(time: PrayerTime) = Stop(prayerNameRes(time), "", time.time, time.prayer)
            // Yesterday's Isha is today's, a day earlier: within a minute or two, as the Next Prayer widget does.
            val isha = day.prayerTimes.last()
            val today = day.prayerTimes.flatMap { time ->
                if (time.prayer == Prayer.FAJR) listOf(stop(time), Stop(R.string.sunrise, "", day.sunrise, null)) else listOf(stop(time))
            }
            val tomorrow = day.next.takeIf { it.time.isAfter(isha.time) }?.let(::stop)
            return listOf(stop(isha.copy(time = isha.time.minusDays(1)))) + today + listOfNotNull(tomorrow)
        }
    }
}
