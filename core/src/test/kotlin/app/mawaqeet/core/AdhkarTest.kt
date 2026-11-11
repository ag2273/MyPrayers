package app.mawaqeet.core

import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val RIYADH: ZoneId = ZoneId.of("Asia/Riyadh")

/** A three-dhikr morning set: one, three, then one repetition. */
private val catalog = AdhkarCatalog.parse(
    """
    # mawaqeet-adhkar-v1
    morning	first	1	رواه مسلم	النص الأول
    morning	second	3	رواه أبو داود	النص الثاني\nسطر ثانٍ
    morning	third	1	رواه البخاري	النص الثالث
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

private fun Adhkar.countTimes(n: Int, set: AdhkarSetId = AdhkarSetId.MORNING): SetProgress {
    var progress = progress(set)
    repeat(n) { progress = count(set) }
    return progress
}

class AdhkarTest {
    private val repo = InMemoryAdhkarRepository()

    @Test
    fun `a new set starts at its first dhikr with nothing counted`() {
        val progress = adhkarAt("2026-11-09T07:00", repo).progress(AdhkarSetId.MORNING)

        assertEquals(LocalDate.parse("2026-11-09"), progress.prayerDay)
        assertEquals(3, progress.items.size)
        assertEquals(0, progress.currentIndex)
        assertEquals("first", progress.current?.dhikr?.id)
        assertEquals(listOf(0, 0, 0), progress.items.map { it.counted })
        assertFalse(progress.isComplete)
    }

    @Test
    fun `counting a dhikr up to its count moves on to the next dhikr`() {
        val adhkar = adhkarAt("2026-11-09T07:00", repo)

        val afterFirst = adhkar.count(AdhkarSetId.MORNING)
        assertEquals(1, afterFirst.currentIndex)

        val oneOfThree = adhkar.count(AdhkarSetId.MORNING)
        assertEquals(1, oneOfThree.currentIndex)
        assertEquals(1, oneOfThree.current?.counted)
        assertEquals(2, oneOfThree.current?.left)

        val afterSecond = adhkar.countTimes(2)
        assertEquals(2, afterSecond.currentIndex)
        assertEquals(listOf(1, 3, 0), afterSecond.items.map { it.counted })
        assertFalse(afterSecond.isComplete)
    }

    @Test
    fun `counting every dhikr completes the set for the Prayer Day`() {
        val adhkar = adhkarAt("2026-11-09T07:00", repo)

        val done = adhkar.countTimes(5)

        assertTrue(done.isComplete)
        assertNull(done.currentIndex)
        assertTrue(adhkar.isComplete(AdhkarSetId.MORNING))
        // A tap on a finished set counts nothing more.
        assertEquals(listOf(1, 3, 1), adhkar.count(AdhkarSetId.MORNING).items.map { it.counted })
    }

    @Test
    fun `restarting begins again at the first dhikr and the day stays complete`() {
        val adhkar = adhkarAt("2026-11-09T07:00", repo)
        adhkar.countTimes(5)

        val restarted = adhkar.restart(AdhkarSetId.MORNING)

        assertEquals(0, restarted.currentIndex)
        assertEquals(listOf(0, 0, 0), restarted.items.map { it.counted })
        assertTrue(restarted.isComplete)
    }

    @Test
    fun `restarting a half-read set clears it without completing it`() {
        val adhkar = adhkarAt("2026-11-09T07:00", repo)
        adhkar.countTimes(2)

        val restarted = adhkar.restart(AdhkarSetId.MORNING)

        assertEquals(0, restarted.currentIndex)
        assertFalse(restarted.isComplete)
    }

    @Test
    fun `leaving halfway and returning later in the Prayer Day resumes, even after midnight`() {
        adhkarAt("2026-11-09T07:00", repo).countTimes(2)

        assertEquals(1, adhkarAt("2026-11-09T11:30", repo).progress(AdhkarSetId.MORNING).currentIndex)
        val afterMidnight = adhkarAt("2026-11-10T03:00", repo).progress(AdhkarSetId.MORNING)
        assertEquals(LocalDate.parse("2026-11-09"), afterMidnight.prayerDay)
        assertEquals(listOf(1, 1, 0), afterMidnight.items.map { it.counted })
    }

    @Test
    fun `the next Prayer Day starts fresh and is not complete`() {
        adhkarAt("2026-11-09T07:00", repo).countTimes(5)

        val nextDay = adhkarAt("2026-11-10T06:30", repo)
        val progress = nextDay.progress(AdhkarSetId.MORNING)

        assertEquals(LocalDate.parse("2026-11-10"), progress.prayerDay)
        assertEquals(0, progress.currentIndex)
        assertEquals(listOf(0, 0, 0), progress.items.map { it.counted })
        assertFalse(progress.isComplete)
        // The finished day stays in the Adhkar Record.
        assertTrue(nextDay.isComplete(AdhkarSetId.MORNING, LocalDate.parse("2026-11-09")))
    }

    @Test
    fun `counting on a new Prayer Day drops older half-read progress`() {
        adhkarAt("2026-11-09T07:00", repo).countTimes(2)

        adhkarAt("2026-11-10T06:30", repo).count(AdhkarSetId.MORNING)

        assertEquals(emptyMap(), repo.counts(LocalDate.parse("2026-11-09"), AdhkarSetId.MORNING.key))
    }
}

class AdhkarCatalogTest {
    @Test
    fun `the catalog keeps each set's adhkar in order with their counts, sources and line breaks`() {
        val morning = catalog.set(AdhkarSetId.MORNING)

        assertEquals(listOf("first", "second", "third"), morning.adhkar.map { it.id })
        assertEquals(listOf(1, 3, 1), morning.adhkar.map { it.count })
        assertEquals("رواه أبو داود", morning.adhkar[1].source)
        assertEquals("النص الثاني\nسطر ثانٍ", morning.adhkar[1].text)
    }

    @Test
    fun `the bundled morning set has real adhkar with counts and sources`() {
        val morning = java.io.File("../app/src/main/assets/adhkar.tsv").inputStream().use(AdhkarCatalog::read)
            .set(AdhkarSetId.MORNING)

        assertTrue(morning.adhkar.size >= 5)
        assertEquals(morning.adhkar.size, morning.adhkar.map { it.id }.toSet().size)
        assertTrue(morning.adhkar.all { it.count >= 1 && it.source.isNotBlank() && it.text.isNotBlank() })
        // «سبحان الله وبحمده» a hundred times (Muslim).
        val subhanallah = morning.adhkar.single { it.text == "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ" }
        assertEquals(100, subhanallah.count)
        assertEquals("رواه مسلم", subhanallah.source)
    }
}
