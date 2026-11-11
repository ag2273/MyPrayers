package app.mawaqeet.core

import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** One dhikr said once in each set, so a single count completes it. */
private val catalog = AdhkarCatalog.parse(
    """
    # mawaqeet-adhkar-v1
    morning	m	1	رواه مسلم	ذكر الصباح
    evening	e	1	رواه مسلم	ذكر المساء
    after-prayer	a	1	رواه مسلم	ذكر بعد الصلاة
    """.trimIndent(),
)

/** The Adhkar at a Makkah wall-clock time (Umm al-Qura, 9 November 2026: Fajr 05:10, Dhuhr 12:04, Asr 15:17). */
private fun adhkarAt(dateTime: String, repo: AdhkarRepository) = Adhkar(
    catalog,
    PrayerCore(
        PrayerSettings.Default,
        Clock.fixed(LocalDateTime.parse(dateTime).atZone(ZoneId.of("Asia/Riyadh")).toInstant(), ZoneOffset.UTC),
        InMemoryPrayerLog(),
    ),
    repo,
)

private fun date(text: String) = LocalDate.parse(text)

private fun AdhkarRecordDay.marks(): List<AdhkarMark> = AdhkarSlot.all.map(::of)

private val C = AdhkarMark.COMPLETED
private val N = AdhkarMark.NOT_COMPLETED
private val Y = AdhkarMark.NOT_YET_DUE
private val B = AdhkarMark.BEFORE_FIRST_USE

/** The Adhkar Record week grid (#74): morning, evening and five after-prayer marks per Prayer Day; no streaks. */
class AdhkarRecordTest {
    private val repo = InMemoryAdhkarRepository()

    @Test
    fun `the week grid shows each slot completed, not completed or not yet due, oldest day first`() {
        adhkarAt("2026-11-05T20:00", repo).count(AdhkarSlot.afterPrayer(Prayer.ISHA))
        adhkarAt("2026-11-09T07:00", repo).count(AdhkarSetId.MORNING)

        val week = adhkarAt("2026-11-09T13:00", repo).week()

        assertEquals((3..9).map { date("2026-11-0$it") }, week.days.map { it.date })
        // Today after Dhuhr: morning read; evening, Asr, Maghrib and Isha not yet due.
        assertEquals(listOf(C, Y, N, N, Y, Y, Y), week.days.last().marks())
        assertEquals(listOf(N, N, N, N, N, N, C), week.days[2].marks())
    }

    @Test
    fun `days before the first count are before first use, not missed`() {
        adhkarAt("2026-11-05T20:00", repo).count(AdhkarSlot.afterPrayer(Prayer.ISHA))

        val week = adhkarAt("2026-11-09T13:00", repo).week()

        assertEquals(date("2026-11-05"), week.countedFrom)
        assertEquals(List(7) { B }, week.days[0].marks())
        assertEquals(List(7) { B }, week.days[1].marks())
        assertEquals(N, week.days[3].of(AdhkarSlot.MORNING))
    }

    @Test
    fun `before anything is counted only the current Prayer Day counts`() {
        val week = adhkarAt("2026-11-09T13:00", repo).week()

        assertEquals(date("2026-11-09"), week.countedFrom)
        assertTrue(week.days.dropLast(1).all { day -> day.marks().all { it == B } })
        assertEquals(listOf(N, Y, N, N, Y, Y, Y), week.days.last().marks())
    }

    @Test
    fun `older weeks are browsable by their last Prayer Day`() {
        adhkarAt("2026-10-20T07:00", repo).count(AdhkarSetId.MORNING)
        adhkarAt("2026-10-30T07:00", repo).count(AdhkarSetId.MORNING)

        val week = adhkarAt("2026-11-09T13:00", repo).week(date("2026-11-02"))

        assertEquals((0L..6L).map { date("2026-10-27").plusDays(it) }, week.days.map { it.date })
        assertEquals(C, week.days[3].of(AdhkarSlot.MORNING))
        // A past day's evening was due: not completed, not "not yet due".
        assertEquals(N, week.days.last().of(AdhkarSlot.EVENING))
    }

    @Test
    fun `after midnight the week still ends with the Prayer Day that began at Fajr`() {
        val week = adhkarAt("2026-11-10T02:00", repo).week()

        assertEquals(date("2026-11-09"), week.days.last().date)
        assertEquals(N, week.days.last().of(AdhkarSlot.EVENING))
    }

    @Test
    fun `each day carries its Hijri date for the row label`() {
        val week = adhkarAt("2026-11-09T13:00", repo).week()

        assertEquals(HijriDate.of(date("2026-11-09")), week.days.last().hijriDate)
    }

    @Test
    fun `deleting the Adhkar Record wipes every completed set and counts from today again`() {
        adhkarAt("2026-11-05T07:00", repo).count(AdhkarSetId.MORNING)
        val today = adhkarAt("2026-11-09T07:00", repo)
        today.count(AdhkarSetId.MORNING)

        today.deleteRecord()

        val week = today.week()
        assertEquals(date("2026-11-09"), week.countedFrom)
        assertEquals(B, week.days[2].of(AdhkarSlot.MORNING))
        assertEquals(N, week.days.last().of(AdhkarSlot.MORNING))
        assertEquals(0, today.progress(AdhkarSetId.MORNING).currentIndex)
    }
}
