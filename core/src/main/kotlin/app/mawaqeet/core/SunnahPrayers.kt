package app.mawaqeet.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * «السنن» under «تذكيرات العبادة» (#96): the Sunnah Prayer Reminders, each off by default and switched on separately.
 * Like every Worship Reminder they are planned only with the master switch on.
 */
data class SunnahPrayerReminders(
    val duha: DuhaReminder = DuhaReminder(),
    val qiyam: QiyamReminder = QiyamReminder(),
)

/** «صلاة الضحى»: once a Prayer Day, [minutes] after Sunrise, never later than [LATEST_BEFORE_DHUHR] before Dhuhr. */
data class DuhaReminder(
    val enabled: Boolean = false,
    /** One of [MINUTES]; kept while the reminder is off. */
    val minutes: Int = DEFAULT_MINUTES,
) {
    init {
        require(minutes in MINUTES) { "The Duha reminder comes $MINUTES minutes after Sunrise, not $minutes" }
    }

    companion object {
        const val DEFAULT_MINUTES = 60
        val MINUTES = listOf(20, 30, 45, 60, 90)

        /** Duha ends shortly before Dhuhr: the reminder never comes later than this before it. */
        val LATEST_BEFORE_DHUHR: Duration = Duration.ofMinutes(10)
    }
}

/**
 * «قيام الليل»: a reminder at the start of the Last Third of the Night ([lastThirdOfNight]), in its [style]: a normal
 * notification by default, or (#97) an alarm over the lock screen. The style is kept while the reminder is off.
 */
data class QiyamReminder(val enabled: Boolean = false, val style: QiyamStyle = QiyamStyle.NOTIFICATION)

/**
 * How the Qiyam reminder comes (#97). Stored by name.
 * - [NOTIFICATION] «إشعار»: a gentle Worship Reminder notification, like the others.
 * - [ALARM] «منبّه»: like alarm-clock mode, over the lock screen with the phone's default alarm sound (never an adhan)
 *   and Stop, stopping by itself after [ALARM_RINGS_FOR]; a notification where the phone allows no full-screen alarm.
 */
enum class QiyamStyle {
    NOTIFICATION,
    ALARM,
    ;

    companion object {
        /** An alarm-style Qiyam reminder rings this long unless stopped. */
        val ALARM_RINGS_FOR: Duration = Duration.ofMinutes(1)
    }
}

/**
 * The start of the Last Third of the Night: Maghrib + ⅔ × (next Fajr − Maghrib), on real (instant) time, so a change
 * of clocks in the night does not move it. The night belongs to the Prayer Day of its [maghrib].
 */
fun lastThirdOfNight(maghrib: ZonedDateTime, nextFajr: ZonedDateTime): ZonedDateTime =
    maghrib.plus(Duration.between(maghrib, nextFajr).multipliedBy(2).dividedBy(3))

/**
 * The Duha reminder of one Prayer Day: [DuhaReminder.minutes] after [sunrise], clamped to [dhuhr] −
 * [DuhaReminder.LATEST_BEFORE_DHUHR]; none when it is off, or when that leaves no time after Sunrise.
 */
internal fun duhaReminder(duha: DuhaReminder, prayerDay: LocalDate, sunrise: Instant, dhuhr: Instant): PlannedWorshipReminder? {
    if (!duha.enabled) return null
    val latest = dhuhr.minus(DuhaReminder.LATEST_BEFORE_DHUHR)
    val at = minOf(sunrise.plus(Duration.ofMinutes(duha.minutes.toLong())), latest)
    if (!at.isAfter(sunrise)) return null
    return PlannedWorshipReminder(WorshipReminderKind.DUHA, at, prayerDay)
}

/** The Qiyam reminder of the night after [prayerDay]'s Maghrib, when it is on; an alarm in [QiyamStyle.ALARM]. */
internal fun qiyamReminder(qiyam: QiyamReminder, prayerDay: LocalDate, maghrib: ZonedDateTime, nextFajr: ZonedDateTime): PlannedWorshipReminder? =
    if (qiyam.enabled) {
        PlannedWorshipReminder(
            WorshipReminderKind.QIYAM,
            lastThirdOfNight(maghrib, nextFajr).toInstant(),
            prayerDay,
            alarm = qiyam.style == QiyamStyle.ALARM,
        )
    } else {
        null
    }
