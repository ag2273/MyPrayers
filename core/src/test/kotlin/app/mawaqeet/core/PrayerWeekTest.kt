package app.mawaqeet.core

import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrayerWeekTest {
    private val riyadh = ZoneId.of("Asia/Riyadh")

    // Makkah, Umm al-Qura, 2026-11-08: Fajr 05:09, Dhuhr 12:04, Asr 15:17, Maghrib 17:41, Isha 19:11.
    private fun coreAt(dateTime: String, log: PrayerLogRepository) = PrayerCore(
        PrayerSettings.Default,
        Clock.fixed(LocalDateTime.parse(dateTime).atZone(riyadh).toInstant(), ZoneOffset.UTC),
        log,
    )

    private fun date(text: String) = LocalDate.parse(text)

    private fun logSince(firstUse: String) = InMemoryPrayerLog().apply { recordFirstUseIfAbsent(date(firstUse)) }

    @Test
    fun `the week grid is the last 7 Prayer Days, oldest first, with prayed, not marked and not yet due`() {
        val log = logSince("2026-10-01")
        val core = coreAt("2026-11-08T13:00", log)
        core.mark(date("2026-11-08"), Prayer.FAJR)
        core.mark(date("2026-11-05"), Prayer.ISHA)

        val week = core.week()

        assertEquals((2..8).map { date("2026-11-0$it") }, week.days.map { it.date })
        val today = week.days.last()
        assertEquals(LogState.PRAYED, today.stateOf(Prayer.FAJR))
        assertEquals(LogState.NOT_MARKED, today.stateOf(Prayer.DHUHR))
        assertEquals(LogState.NOT_YET_DUE, today.stateOf(Prayer.ASR))
        assertEquals(LogState.PRAYED, week.days[3].stateOf(Prayer.ISHA))
        assertEquals(LogState.NOT_MARKED, week.days[3].stateOf(Prayer.FAJR))
    }

    @Test
    fun `between midnight and Fajr the current week still ends with the previous Prayer Day`() {
        val week = coreAt("2026-11-09T02:00", logSince("2026-10-01")).week()

        assertEquals(date("2026-11-08"), week.days.last().date)
    }

    @Test
    fun `the summary counts each Prayer and the total, leaving out Prayers not yet due`() {
        val log = logSince("2026-10-01")
        val core = coreAt("2026-11-08T13:00", log)
        (2..8).forEach { core.mark(date("2026-11-0$it"), Prayer.FAJR) }
        (2..6).forEach { core.mark(date("2026-11-0$it"), Prayer.ISHA) }

        val summary = core.week().summary

        assertEquals(PrayerCount(7, 7), summary.of(Prayer.FAJR))
        assertEquals(PrayerCount(0, 7), summary.of(Prayer.DHUHR))
        // Today's Asr, Maghrib and Isha are not yet due: not yet missed either.
        assertEquals(PrayerCount(0, 6), summary.of(Prayer.ASR))
        assertEquals(PrayerCount(5, 6), summary.of(Prayer.ISHA))
        assertEquals(PrayerCount(12, 7 + 7 + 6 + 6 + 6), summary.total)
    }

    @Test
    fun `days before the first-use date never count as missed`() {
        val log = logSince("2026-11-06")
        val core = coreAt("2026-11-08T22:00", log)
        core.mark(date("2026-11-07"), Prayer.MAGHRIB)

        val week = core.week()

        assertEquals(date("2026-11-06"), week.countedFrom)
        assertTrue(week.isBeforeFirstUse(week.days.first()))
        assertFalse(week.isBeforeFirstUse(week.days.last()))
        assertEquals(PrayerCount(1, 3), week.summary.of(Prayer.MAGHRIB))
        assertEquals(PrayerCount(1, 15), week.summary.total)
    }

    @Test
    fun `before any first use is recorded only the current Prayer Day counts`() {
        val summary = coreAt("2026-11-08T22:00", InMemoryPrayerLog()).week().summary

        assertEquals(PrayerCount(0, 5), summary.total)
    }

    @Test
    fun `only today and the 6 previous Prayer Days are editable, older weeks are read-only`() {
        val core = coreAt("2026-11-08T22:00", logSince("2026-10-01"))

        assertTrue(core.week().days.all { it.isEditable })
        val previous = core.week(date("2026-11-01"))
        assertEquals(date("2026-10-26"), previous.days.first().date)
        assertTrue(previous.days.none { it.isEditable })
        assertEquals(LogCommandResult.OUTSIDE_EDIT_WINDOW, core.mark(date("2026-11-01"), Prayer.FAJR))
    }

    @Test
    fun `each Prayer Day carries its Hijri date with the user's offset`() {
        val settings = PrayerSettings.Default.copy(hijriOffsetDays = 1)
        val core = PrayerCore(
            settings,
            Clock.fixed(LocalDateTime.parse("2026-11-08T22:00").atZone(riyadh).toInstant(), ZoneOffset.UTC),
            logSince("2026-10-01"),
        )

        assertEquals(HijriDate.of(date("2026-11-09")), core.week().days.last().hijriDate)
    }

    @Test
    fun `deleting the Prayer Log leaves no entries and the summary starts again from today`() {
        val log = logSince("2026-10-01")
        val core = coreAt("2026-11-08T22:00", log)
        core.mark(date("2026-11-08"), Prayer.FAJR)
        core.mark(date("2026-11-03"), Prayer.ASR)

        core.deletePrayerLog()

        val week = core.week()
        assertTrue(week.days.all { day -> Prayer.entries.none { day.stateOf(it) == LogState.PRAYED } })
        assertEquals(date("2026-11-08"), log.firstUseDate())
        assertEquals(PrayerCount(0, 5), week.summary.total)
    }
}
