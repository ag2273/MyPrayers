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

private val RIYADH: ZoneId = ZoneId.of("Asia/Riyadh")

private fun makkahClockAt(dateTime: String): Clock =
    Clock.fixed(LocalDateTime.parse(dateTime).atZone(RIYADH).toInstant(), ZoneOffset.UTC)

private fun coreAt(
    dateTime: String,
    log: PrayerLogRepository = InMemoryPrayerLog(),
    settings: PrayerSettings = PrayerSettings.Default,
) = PrayerCore(settings, makkahClockAt(dateTime), log)

private fun date(text: String): LocalDate = LocalDate.parse(text)

class PrayerLogTest {

    @Test
    fun `Isha marked at 00 30 belongs to the previous Prayer Day`() {
        val log = InMemoryPrayerLog()
        val core = coreAt("2026-11-09T00:30", log)

        val day = core.currentPrayerDay()
        val result = core.mark(day.date, Prayer.ISHA)

        assertEquals(date("2026-11-08"), day.date)
        assertEquals(LogCommandResult.DONE, result)
        assertEquals(LogState.PRAYED, coreAt("2026-11-08T21:00", log).currentPrayerDay().stateOf(Prayer.ISHA))
    }

    @Test
    fun `from midnight until Fajr the Prayer Day is still the previous date`() {
        assertEquals(date("2026-11-08"), coreAt("2026-11-09T00:00").currentPrayerDay().date)
        // Makkah Fajr on 9 November 2026 is about 05:10 (Umm al-Qura).
        assertEquals(date("2026-11-08"), coreAt("2026-11-09T05:00").currentPrayerDay().date)
    }

    @Test
    fun `at Fajr exactly the new Prayer Day begins and its Fajr is markable`() {
        val fajr = coreAt("2026-11-09T12:00").prayerDay(date("2026-11-09")).prayerTimes.first().time.toInstant()
        val atFajr = PrayerCore(PrayerSettings.Default, Clock.fixed(fajr, ZoneOffset.UTC), InMemoryPrayerLog())

        val day = atFajr.currentPrayerDay()

        assertEquals(date("2026-11-09"), day.date)
        assertEquals(day, atFajr.prayerDayAt(fajr))
        assertEquals(date("2026-11-08"), atFajr.prayerDayAt(fajr.minusSeconds(1)).date)
        assertTrue(day.isMarkable(Prayer.FAJR))
        assertFalse(day.isMarkable(Prayer.DHUHR))
    }

    @Test
    fun `a Prayer whose time has not arrived cannot be marked`() {
        val log = InMemoryPrayerLog()
        val core = coreAt("2026-11-08T10:00", log)

        val result = core.mark(date("2026-11-08"), Prayer.DHUHR)

        assertEquals(LogCommandResult.NOT_YET_DUE, result)
        assertEquals(LogState.NOT_YET_DUE, core.currentPrayerDay().stateOf(Prayer.DHUHR))
        assertEquals(emptySet(), log.prayedOn(date("2026-11-08")))
    }

    @Test
    fun `a future Prayer Day cannot be marked`() {
        assertEquals(LogCommandResult.NOT_YET_DUE, coreAt("2026-11-08T22:00").mark(date("2026-11-09"), Prayer.FAJR))
    }

    @Test
    fun `tapping again unmarks a Prayer`() {
        val core = coreAt("2026-11-08T10:00")
        core.mark(date("2026-11-08"), Prayer.FAJR)

        val result = core.unmark(date("2026-11-08"), Prayer.FAJR)

        assertEquals(LogCommandResult.DONE, result)
        assertEquals(LogState.NOT_MARKED, core.currentPrayerDay().stateOf(Prayer.FAJR))
    }

    @Test
    fun `today and the 6 previous Prayer Days are editable, the 7th before is not`() {
        val log = InMemoryPrayerLog()
        val core = coreAt("2026-11-08T10:00", log)

        assertEquals(LogCommandResult.DONE, core.mark(date("2026-11-02"), Prayer.ISHA))
        assertTrue(core.prayerDay(date("2026-11-02")).isEditable)

        assertEquals(LogCommandResult.OUTSIDE_EDIT_WINDOW, core.mark(date("2026-11-01"), Prayer.ISHA))
        assertEquals(LogCommandResult.OUTSIDE_EDIT_WINDOW, core.unmark(date("2026-11-01"), Prayer.ISHA))
        val readOnly = core.prayerDay(date("2026-11-01"))
        assertFalse(readOnly.isEditable)
        assertFalse(readOnly.isMarkable(Prayer.ISHA))
        assertEquals(emptySet(), log.prayedOn(date("2026-11-01")))
    }

    @Test
    fun `before Fajr the edit window still counts from the previous Prayer Day`() {
        val core = coreAt("2026-11-09T03:00")

        assertEquals(LogCommandResult.DONE, core.mark(date("2026-11-02"), Prayer.FAJR))
        assertEquals(LogCommandResult.OUTSIDE_EDIT_WINDOW, core.mark(date("2026-11-01"), Prayer.FAJR))
    }

    @Test
    fun `older days stay readable with their marks`() {
        val log = InMemoryPrayerLog()
        coreAt("2026-11-01T22:00", log).mark(date("2026-11-01"), Prayer.MAGHRIB)

        val day = coreAt("2026-11-20T10:00", log).prayerDay(date("2026-11-01"))

        assertEquals(LogState.PRAYED, day.stateOf(Prayer.MAGHRIB))
        assertEquals(LogState.NOT_MARKED, day.stateOf(Prayer.ISHA))
    }

    @Test
    fun `a recalculation keeps existing marks`() {
        // Only Umm al-Qura exists until #16, so the recalculation here changes the Asr Convention,
        // which moves Asr by over an hour in Makkah.
        val log = InMemoryPrayerLog()
        coreAt("2026-11-08T16:00", log).mark(date("2026-11-08"), Prayer.ASR)
        val hanafi = PrayerSettings.Default.copy(asrConvention = AsrConvention.HANAFI)

        val day = coreAt("2026-11-08T17:00", log, hanafi).currentPrayerDay()

        assertEquals(LogState.PRAYED, day.stateOf(Prayer.ASR))
    }

    @Test
    fun `the first-use date is the Prayer Day of first launch and is recorded once`() {
        val log = InMemoryPrayerLog()

        coreAt("2026-11-09T02:00", log).recordFirstUse()
        coreAt("2026-11-12T10:00", log).recordFirstUse()

        assertEquals(date("2026-11-08"), log.firstUseDate())
    }
}
