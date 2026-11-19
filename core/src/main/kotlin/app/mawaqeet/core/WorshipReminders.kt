package app.mawaqeet.core

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.random.Random

/**
 * Settings → Alerts → «تذكيرات العبادة»: the Worship Reminders, each off by default and switched on separately. None is
 * planned while the master switch ([AlertSettings.enabled]) is off. Each reminder is its own field.
 */
data class WorshipReminders(
    /** «ذكر الله والتسبيح»: a random [DhikrPhrase] every few hours between Sunrise and Isha (it replaced the salawat reminder). */
    val dhikr: DhikrReminder = DhikrReminder(),
    val kahf: KahfReminder = KahfReminder(),
    /** «أذكار الصباح» (#75): [AdhkarReminder.minutes] after Fajr; opens the morning set, skipped once it is complete. */
    val morningAdhkar: AdhkarReminder = AdhkarReminder(),
    /** «أذكار المساء» (#75): [AdhkarReminder.minutes] after Asr; opens the evening set, skipped once it is complete. */
    val eveningAdhkar: AdhkarReminder = AdhkarReminder(),
    /** «صيام السنة» (#95): a reminder the night before each Sunnah Fast switched on. */
    val fasts: FastReminders = FastReminders(),
    /** «السنن» (#96): the Duha and Qiyam reminders. */
    val sunnahPrayers: SunnahPrayerReminders = SunnahPrayerReminders(),
)

/** The morning or evening adhkar reminder: once a Prayer Day, [minutes] after Fajr (morning) or Asr (evening). */
data class AdhkarReminder(
    val enabled: Boolean = false,
    /** One of [MINUTES]; kept while the reminder is off. */
    val minutes: Int = DEFAULT_MINUTES,
) {
    init {
        require(minutes in MINUTES) { "Adhkar reminders come $MINUTES minutes after their Prayer, not $minutes" }
    }

    companion object {
        const val DEFAULT_MINUTES = 30
        val MINUTES = listOf(15, 30, 45, 60)
    }
}

/**
 * «ذكر الله والتسبيح»: a reminder every [intervalHours] hours from Sunrise + [intervalHours] until Isha, each showing one
 * [DhikrPhrase] picked at random. With [sound] on it says that dhikr aloud (the recording bundled with the app); with it off
 * it is a silent notification. It replaced the salawat reminder (1.4.8) and keeps its stored switch and interval.
 */
data class DhikrReminder(
    val enabled: Boolean = false,
    /** One of [INTERVAL_HOURS]; kept while the reminder is off. */
    val intervalHours: Int = DEFAULT_INTERVAL_HOURS,
    /** Say the dhikr aloud; kept while the reminder is off. */
    val sound: Boolean = true,
) {
    init {
        require(intervalHours in INTERVAL_HOURS) { "Dhikr reminders come every $INTERVAL_HOURS hours, not $intervalHours" }
    }

    val interval: Duration get() = Duration.ofHours(intervalHours.toLong())

    companion object {
        const val DEFAULT_INTERVAL_HOURS = 2
        val INTERVAL_HOURS = listOf(1, 2, 3)
    }
}

/** The adhkar a [DhikrReminder] picks from, one at random each time. */
enum class DhikrPhrase {
    SUBHAN_ALLAH,
    ASTAGHFIRULLAH,
    ALHAMDULILLAH,
    ALLAHU_AKBAR,
    SALAT_ALA_MUHAMMAD,
    SUBHAN_ALLAH_WA_BIHAMDIHI,
    LA_ILAHA_ILLALLAH,
    ;

    companion object {
        /** One of the adhkar, each as likely as the others. */
        fun pick(random: Random = Random.Default): DhikrPhrase = entries[random.nextInt(entries.size)]
    }
}

/**
 * «سورة الكهف» (#64): a reminder to read Surat Al-Kahf every Friday at Sunrise + [AFTER_SUNRISE]. The app shows no
 * Quran text and keeps no Kahf log: the reminder's «قرأتها» only dismisses it.
 */
data class KahfReminder(val enabled: Boolean = false) {
    companion object {
        val AFTER_SUNRISE: Duration = Duration.ofHours(1)
    }
}

/**
 * Which Worship Reminder a [PlannedWorshipReminder] is. Stored by name: add new kinds at the end. [adhkarSet] is the
 * Adhkar Set an adhkar reminder opens (and is skipped for once complete); null for the others.
 */
enum class WorshipReminderKind(val adhkarSet: AdhkarSetId? = null) {
    /** «ذكر الله والتسبيح»; stored as `SALAWAT` before 1.4.8, which [byName] still reads. Keep it first. */
    DHIKR,
    KAHF,
    MORNING_ADHKAR(AdhkarSetId.MORNING),
    EVENING_ADHKAR(AdhkarSetId.EVENING),

    /** «تذكير السحور» (#91), on a Fasting Day before Fajr; its Prayer Day is that Fasting Day's date. */
    SUHOOR,

    /** «تنبيه الإمساك» (#91), at a Fasting Day's Imsak; its Prayer Day is that Fasting Day's date. */
    IMSAK,

    /**
     * «صيام السنة» (#95), the night before a Sunnah Fast; its Prayer Day is the day before the fast, and
     * [PlannedWorshipReminder.fast] says which.
     */
    SUNNAH_FAST,

    /** «صلاة الضحى» (#96), after Sunrise; its Prayer Day is that Sunrise's. */
    DUHA,

    /**
     * «قيام الليل» (#96), at the start of the Last Third of the Night; its Prayer Day is that night's Maghrib's, so a
     * reminder after midnight still belongs to the day before. A notification, or an alarm in [QiyamStyle.ALARM] (#97).
     */
    QIYAM,
    ;

    companion object {
        /** The kind stored or sent under [name], including the salawat reminder's old name. */
        fun byName(name: String): WorshipReminderKind = if (name == "SALAWAT") DHIKR else valueOf(name)
    }
}

/**
 * One Worship Reminder the scheduler must raise at [instant]: a gentle notification, never an adhan. Identified by
 * [kind], [prayerDay] and [slot] (its place among that kind's reminders on that Prayer Day, from 0; always 0 for a
 * kind that comes once a day). [fast] is what a [WorshipReminderKind.SUNNAH_FAST] reminder announces, null for the others.
 * [alarm] marks a [WorshipReminderKind.QIYAM] reminder in [QiyamStyle.ALARM] (#97): it rings like an alarm, with the
 * phone's alarm sound, still never an adhan.
 */
data class PlannedWorshipReminder(
    val kind: WorshipReminderKind,
    val instant: Instant,
    val prayerDay: LocalDate,
    val slot: Int = 0,
    val fast: SunnahFastDay? = null,
    val alarm: Boolean = false,
    /** A [WorshipReminderKind.DHIKR] reminder with [DhikrReminder.sound] on: it says its dhikr aloud. */
    val sound: Boolean = false,
) {
    init {
        require(slot in 0 until MAX_SLOTS) { "A Worship Reminder's slot is below $MAX_SLOTS, not $slot" }
        require(!alarm || kind == WorshipReminderKind.QIYAM) { "Only the Qiyam reminder can be an alarm, not $kind" }
        require(!sound || kind == WorshipReminderKind.DHIKR) { "Only the dhikr reminder says a dhikr aloud, not $kind" }
    }

    companion object {
        /** At most this many reminders of one kind on one Prayer Day (hourly dhikr at the poles stays below it). */
        const val MAX_SLOTS = 32
    }
}

/** The dhikr reminders of one Prayer Day: Sunrise + n × interval for n ≥ 1, strictly before Isha. */
internal fun dhikrReminders(dhikr: DhikrReminder, prayerDay: LocalDate, sunrise: Instant, isha: Instant): List<PlannedWorshipReminder> {
    if (!dhikr.enabled) return emptyList()
    return generateSequence(sunrise.plus(dhikr.interval)) { it.plus(dhikr.interval) }
        .takeWhile { it.isBefore(isha) }
        .take(PlannedWorshipReminder.MAX_SLOTS)
        .mapIndexed { slot, instant -> PlannedWorshipReminder(WorshipReminderKind.DHIKR, instant, prayerDay, slot, sound = dhikr.sound) }
        .toList()
}

/** [kind]'s reminder of one Prayer Day: [AdhkarReminder.minutes] after [prayerTime] (Fajr or Asr), when it is on. */
internal fun adhkarReminder(
    reminder: AdhkarReminder,
    kind: WorshipReminderKind,
    prayerDay: LocalDate,
    prayerTime: Instant,
): PlannedWorshipReminder? =
    if (reminder.enabled) PlannedWorshipReminder(kind, prayerTime.plus(Duration.ofMinutes(reminder.minutes.toLong())), prayerDay) else null

/** The Kahf reminder of one Prayer Day: Sunrise + [KahfReminder.AFTER_SUNRISE], on a Friday only. */
internal fun kahfReminders(kahf: KahfReminder, prayerDay: LocalDate, sunrise: Instant): List<PlannedWorshipReminder> {
    if (!kahf.enabled || prayerDay.dayOfWeek != DayOfWeek.FRIDAY) return emptyList()
    return listOf(PlannedWorshipReminder(WorshipReminderKind.KAHF, sunrise.plus(KahfReminder.AFTER_SUNRISE), prayerDay))
}
