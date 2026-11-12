package app.mawaqeet.core

import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val RIYADH: ZoneId = ZoneId.of("Asia/Riyadh")

private val catalog = AdhkarCatalog.parse(
    """
    # mawaqeet-adhkar-v1
    morning	first	1	رواه مسلم	النص الأول
    """.trimIndent(),
)

/** The Adhkar at a Makkah wall-clock time (Umm al-Qura: Fajr on 9 November 2026 is about 05:10). */
private fun adhkarAt(dateTime: String, repo: AdhkarRepository) = Adhkar(
    catalog,
    PrayerCore(
        PrayerSettings.Default,
        Clock.fixed(LocalDateTime.parse(dateTime).atZone(RIYADH).toInstant(), ZoneOffset.UTC),
        InMemoryPrayerLog(),
    ),
    repo,
)

class TasbeehTest {
    private val repo = InMemoryAdhkarRepository()

    @Test
    fun `the Tasbeeh starts at zero`() {
        assertEquals(0, adhkarAt("2026-11-09T07:00", repo).tasbeeh())
    }

    @Test
    fun `each tap adds one to the total`() {
        val adhkar = adhkarAt("2026-11-09T07:00", repo)

        assertEquals(1, adhkar.countTasbeeh())
        assertEquals(2, adhkar.countTasbeeh())
        assertEquals(3, adhkar.countTasbeeh())
        assertEquals(3, adhkar.tasbeeh())
    }

    @Test
    fun `the total lasts the whole Prayer Day, even after midnight`() {
        repeat(33) { adhkarAt("2026-11-09T07:00", repo).countTasbeeh() }

        assertEquals(33, adhkarAt("2026-11-09T22:00", repo).tasbeeh())
        assertEquals(33, adhkarAt("2026-11-10T03:00", repo).tasbeeh())
    }

    @Test
    fun `the next Prayer Day starts again at zero`() {
        repeat(10) { adhkarAt("2026-11-09T07:00", repo).countTasbeeh() }

        val nextDay = adhkarAt("2026-11-10T06:30", repo)
        assertEquals(0, nextDay.tasbeeh())
        assertEquals(1, nextDay.countTasbeeh())
    }

    @Test
    fun `reset puts the total back to zero`() {
        val adhkar = adhkarAt("2026-11-09T07:00", repo)
        repeat(5) { adhkar.countTasbeeh() }

        assertEquals(0, adhkar.resetTasbeeh())
        assertEquals(0, adhkar.tasbeeh())
        assertEquals(1, adhkar.countTasbeeh())
    }

    @Test
    fun `the Tasbeeh never appears in the Adhkar Record`() {
        val adhkar = adhkarAt("2026-11-09T07:00", repo)
        repeat(100) { adhkar.countTasbeeh() }

        val week = adhkar.week()
        assertTrue(week.days.all { day -> day.marks.values.none { it == AdhkarMark.COMPLETED } })
        assertEquals(AdhkarMark.NOT_COMPLETED, week.days.last().of(AdhkarSlot.MORNING))
        assertTrue(AdhkarSlot.all.none { adhkar.isComplete(it) })
    }

    @Test
    fun `the Tasbeeh leaves the sets' progress alone`() {
        val adhkar = adhkarAt("2026-11-09T07:00", repo)
        repeat(4) { adhkar.countTasbeeh() }

        assertEquals(0, adhkar.progress(AdhkarSetId.MORNING).currentIndex)
        adhkar.restart(AdhkarSetId.MORNING)
        assertEquals(4, adhkar.tasbeeh())
    }
}
