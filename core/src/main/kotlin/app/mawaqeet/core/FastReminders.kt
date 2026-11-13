package app.mawaqeet.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * «صيام السنة» under «تذكيرات العبادة» (#95): the Sunnah Fasts whose reminder is switched on, none by default. Each comes
 * the night before the fast, [AFTER_ISHA] after Isha of the Prayer Day before it.
 */
data class FastReminders(val on: Set<SunnahFast> = emptySet()) {
    fun isOn(fast: SunnahFast): Boolean = fast in on

    /** These switches with [fast]'s turned [enabled]. */
    fun with(fast: SunnahFast, enabled: Boolean): FastReminders = copy(on = if (enabled) on + fast else on - fast)

    companion object {
        val AFTER_ISHA: Duration = Duration.ofMinutes(30)
    }
}

/**
 * What a [WorshipReminderKind.SUNNAH_FAST] reminder announces: tomorrow's [hijri] date and the Sunnah Fasts switched on
 * that it is a day of (never empty). Tomorrow is the reminder's Prayer Day + 1, so its weekday is known too.
 */
data class SunnahFastDay(val hijri: HijriDate, val kinds: Set<SunnahFast>) {
    init {
        require(kinds.isNotEmpty()) { "A fast reminder names at least one Sunnah Fast" }
    }
}

/**
 * The Sunnah Fast reminder of the Prayer Day [prayerDay]: [FastReminders.AFTER_ISHA] after its [isha], when tomorrow
 * is a day of a Sunnah Fast switched on in [fasts]. One reminder however many kinds coincide. The six of Shawwal is
 * announced once, the night before 2 Shawwal. [calendar] already leaves out Forbidden Fast Days and, in Ramadan,
 * Mondays, Thursdays and the white days.
 */
internal fun sunnahFastReminder(
    fasts: FastReminders,
    calendar: HijriCalendar,
    prayerDay: LocalDate,
    isha: Instant,
): PlannedWorshipReminder? {
    if (fasts.on.isEmpty()) return null
    val tomorrow = calendar.day(prayerDay.plusDays(1))
    val kinds = tomorrow.fasts.filterTo(mutableSetOf()) { fasts.isOn(it) }
    if (tomorrow.hijri.day != FIRST_OF_SIX_OF_SHAWWAL) kinds -= SunnahFast.SIX_OF_SHAWWAL
    if (kinds.isEmpty()) return null
    return PlannedWorshipReminder(
        kind = WorshipReminderKind.SUNNAH_FAST,
        instant = isha.plus(FastReminders.AFTER_ISHA),
        prayerDay = prayerDay,
        fast = SunnahFastDay(tomorrow.hijri, kinds),
    )
}

/** 2 Shawwal, the first of the six days after Eid. */
private const val FIRST_OF_SIX_OF_SHAWWAL = 2
