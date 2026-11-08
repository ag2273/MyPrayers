package app.mawaqeet.core

import java.time.Clock
import java.time.LocalDate

/**
 * The prayer core: owns every rule about Prayer Times. Android code is a thin adapter around it.
 *
 * It is cheap to build: create a new one whenever [settings] change. The current instant always comes from
 * [clock], so tests pass a fixed or fake clock.
 */
class PrayerCore(
    private val settings: PrayerSettings,
    private val clock: Clock,
) {
    /** The calendar date to display now, at the location, with its Prayer Times and Sunrise. */
    fun displayDay(): DisplayDay {
        val now = clock.instant()
        val date = LocalDate.ofInstant(now, settings.location.zone)
        val day = calculateDay(settings, date)
        val next = day.prayerTimes.firstOrNull { it.time.toInstant().isAfter(now) }
            ?: calculateDay(settings, date.plusDays(1)).prayerTimes.first()
        return DisplayDay(date = date, prayerTimes = day.prayerTimes, sunrise = day.sunrise, next = next)
    }
}
