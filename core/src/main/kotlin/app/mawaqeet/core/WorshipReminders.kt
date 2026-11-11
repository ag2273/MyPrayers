package app.mawaqeet.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * Settings → Alerts → «تذكيرات العبادة»: the Worship Reminders, each off by default and switched on separately. None is
 * planned while the master switch ([AlertSettings.enabled]) is off. Later reminders (morning and evening adhkar,
 * Surat Al-Kahf) join here as their own fields.
 */
data class WorshipReminders(
    val salawat: SalawatReminder = SalawatReminder(),
)

/** «الصلاة على النبي ﷺ»: a reminder every [intervalHours] hours from Sunrise + [intervalHours] until Isha. */
data class SalawatReminder(
    val enabled: Boolean = false,
    /** One of [INTERVAL_HOURS]; kept while the reminder is off. */
    val intervalHours: Int = DEFAULT_INTERVAL_HOURS,
) {
    init {
        require(intervalHours in INTERVAL_HOURS) { "Salawat reminders come every $INTERVAL_HOURS hours, not $intervalHours" }
    }

    val interval: Duration get() = Duration.ofHours(intervalHours.toLong())

    companion object {
        const val DEFAULT_INTERVAL_HOURS = 2
        val INTERVAL_HOURS = listOf(1, 2, 3)
    }
}

/** Which Worship Reminder a [PlannedWorshipReminder] is. Stored by name: add new kinds at the end. */
enum class WorshipReminderKind { SALAWAT }

/**
 * One Worship Reminder the scheduler must raise: a gentle notification at [instant], never an adhan. Identified by
 * [kind], [prayerDay] and [slot] (its place among that kind's reminders on that Prayer Day, from 0; always 0 for a
 * kind that comes once a day).
 */
data class PlannedWorshipReminder(
    val kind: WorshipReminderKind,
    val instant: Instant,
    val prayerDay: LocalDate,
    val slot: Int = 0,
) {
    init {
        require(slot in 0 until MAX_SLOTS) { "A Worship Reminder's slot is below $MAX_SLOTS, not $slot" }
    }

    companion object {
        /** At most this many reminders of one kind on one Prayer Day (hourly salawat at the poles stays below it). */
        const val MAX_SLOTS = 32
    }
}

/** The salawat reminders of one Prayer Day: Sunrise + n × interval for n ≥ 1, strictly before Isha. */
internal fun salawatReminders(salawat: SalawatReminder, prayerDay: LocalDate, sunrise: Instant, isha: Instant): List<PlannedWorshipReminder> {
    if (!salawat.enabled) return emptyList()
    return generateSequence(sunrise.plus(salawat.interval)) { it.plus(salawat.interval) }
        .takeWhile { it.isBefore(isha) }
        .take(PlannedWorshipReminder.MAX_SLOTS)
        .mapIndexed { slot, instant -> PlannedWorshipReminder(WorshipReminderKind.SALAWAT, instant, prayerDay, slot) }
        .toList()
}
