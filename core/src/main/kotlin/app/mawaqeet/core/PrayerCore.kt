package app.mawaqeet.core

import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/**
 * The prayer core: owns every rule about Prayer Times and the Prayer Log. Android code is a thin adapter around it.
 *
 * It is cheap to build: create a new one whenever [settings] change. The current instant always comes from
 * [clock], so tests pass a fixed or fake clock. Prayer Log entries are read and written through [log].
 */
class PrayerCore(
    private val settings: PrayerSettings,
    private val clock: Clock,
    private val log: PrayerLogRepository,
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

    /** The Prayer Day now falls in. */
    fun currentPrayerDay(): PrayerDay = prayerDay(prayerDayDateAt(clock.instant()))

    /** The Prayer Day [instant] belongs to: from its Fajr (inclusive) to the next Fajr. */
    fun prayerDayAt(instant: Instant): PrayerDay = prayerDay(prayerDayDateAt(instant))

    /** The Prayer Day identified by [date], with each Prayer's state as of now. */
    fun prayerDay(date: LocalDate): PrayerDay {
        val now = clock.instant()
        val day = calculateDay(settings, date)
        val prayed = log.prayedOn(date)
        val states = day.prayerTimes.associate { (prayer, time) ->
            prayer to when {
                time.toInstant().isAfter(now) -> LogState.NOT_YET_DUE
                prayer in prayed -> LogState.PRAYED
                else -> LogState.NOT_MARKED
            }
        }
        val today = prayerDayDateAt(now)
        return PrayerDay(
            date = date,
            prayerTimes = day.prayerTimes,
            sunrise = day.sunrise,
            states = states,
            isEditable = date <= today && date >= today.minusDays(EDIT_WINDOW_DAYS - 1),
        )
    }

    /** Marks [prayer] on the Prayer Day [date] as prayed, unless it isn't markable yet or the day is read-only. */
    fun mark(date: LocalDate, prayer: Prayer): LogCommandResult = setPrayed(date, prayer, true)

    /** Removes the mark from [prayer] on the Prayer Day [date], under the same rules as [mark]. */
    fun unmark(date: LocalDate, prayer: Prayer): LogCommandResult = setPrayed(date, prayer, false)

    /**
     * Every Adhan Alert due after now (exclusive, so an alarm firing now is not planned again) and within the
     * next [AlertPlan.WINDOW]. Sunrise never alerts. Until per-Prayer alert settings exist (#23), every Prayer
     * has a Short Adhan Adhan Alert delivered as an exact alarm.
     */
    fun alertPlan(): AlertPlan {
        val now = clock.instant()
        val end = now.plus(AlertPlan.WINDOW)
        val zone = settings.location.zone
        val today = LocalDate.ofInstant(now, zone)
        // Yesterday too: a Prayer Day is a calendar date's Fajr onwards, and the window spans at most 3 dates.
        val alerts = (-1L..2L).flatMap { offset ->
            val date = today.plusDays(offset)
            calculateDay(settings, date).prayerTimes.map { time ->
                PlannedAlert(
                    instant = time.time.toInstant(),
                    prayer = time.prayer,
                    kind = AlertKind.ADHAN_ALERT,
                    sound = AlertSound.ShortAdhan,
                    deliveryMode = DeliveryMode.EXACT,
                    prayerDay = date,
                    isJumuah = time.isJumuah,
                )
            }
        }.filter { it.instant.isAfter(now) && !it.instant.isAfter(end) }.sortedBy { it.instant }
        val midnight = today.plusDays(1).atStartOfDay(zone).toInstant()
        return AlertPlan(alerts, midnight)
    }

    /** Records the current Prayer Day as the first-use date, once; later calls change nothing. */
    fun recordFirstUse() = log.recordFirstUseIfAbsent(prayerDayDateAt(clock.instant()))

    private fun setPrayed(date: LocalDate, prayer: Prayer, prayed: Boolean): LogCommandResult {
        val day = prayerDay(date)
        return when {
            // A future Prayer Day is outside the window too, but "not yet due" is the truer reason.
            day.stateOf(prayer) == LogState.NOT_YET_DUE -> LogCommandResult.NOT_YET_DUE
            !day.isEditable -> LogCommandResult.OUTSIDE_EDIT_WINDOW
            else -> {
                log.setPrayed(date, prayer, prayed)
                LogCommandResult.DONE
            }
        }
    }

    /** The local date at the location, stepped back a day before that date's Fajr. */
    private fun prayerDayDateAt(instant: Instant): LocalDate {
        val date = LocalDate.ofInstant(instant, settings.location.zone)
        val fajr = calculateDay(settings, date).prayerTimes.first().time.toInstant()
        return if (instant.isBefore(fajr)) date.minusDays(1) else date
    }

    private companion object {
        /** Today plus the 6 previous Prayer Days. */
        const val EDIT_WINDOW_DAYS = 7L
    }
}
