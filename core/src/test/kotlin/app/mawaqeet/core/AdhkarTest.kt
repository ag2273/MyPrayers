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
    fun `a dhikr skipped with Next can be counted later, and the set completes once every dhikr is said`() {
        val adhkar = adhkarAt("2026-11-09T07:00", repo)
        val slot = AdhkarSlot.of(AdhkarSetId.MORNING)

        // Skip the first dhikr and say the second and third.
        repeat(3) { adhkar.count(slot, index = 1) }
        val afterSecond = adhkar.progress(slot)
        assertEquals(listOf(0, 3, 0), afterSecond.items.map { it.counted })
        assertEquals(2, afterSecond.nextUndone(1))
        val afterThird = adhkar.count(slot, index = 2)
        assertFalse(afterThird.isComplete)
        assertEquals(0, afterThird.nextUndone(2))

        val done = adhkar.count(slot, index = 0)
        assertTrue(done.isComplete)
        assertNull(done.nextUndone(0))
    }

    @Test
    fun `a dhikr already said in full is not counted again`() {
        val adhkar = adhkarAt("2026-11-09T07:00", repo)
        val slot = AdhkarSlot.of(AdhkarSetId.MORNING)
        adhkar.count(slot)

        val again = adhkar.count(slot, index = 0)

        assertEquals(listOf(1, 0, 0), again.items.map { it.counted })
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

private val TASBEEH = setOf("سُبْحَانَ اللَّهِ", "الْحَمْدُ لِلَّهِ", "اللَّهُ أَكْبَرُ")

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

    @Test
    fun `the bundled evening and after-prayer sets have real adhkar with counts and sources`() {
        val bundled = java.io.File("../app/src/main/assets/adhkar.tsv").inputStream().use(AdhkarCatalog::read)

        for (id in listOf(AdhkarSetId.EVENING, AdhkarSetId.AFTER_PRAYER)) {
            val set = bundled.set(id)
            assertTrue(set.adhkar.size >= 4, "${id.key} has ${set.adhkar.size}")
            assertEquals(set.adhkar.size, set.adhkar.map { it.id }.toSet().size)
            assertTrue(set.adhkar.all { it.count >= 1 && it.source.isNotBlank() && it.text.isNotBlank() })
        }
        // The evening's own opening, «أمسينا وأمسى الملك لله» (Muslim).
        assertTrue(bundled.set(AdhkarSetId.EVENING).adhkar.first().text.startsWith("أَمْسَيْنَا"))
        // After the Prayer: «أستغفر الله» three times (Muslim), then the tasbeeh thirty-three times each.
        val afterPrayer = bundled.set(AdhkarSetId.AFTER_PRAYER).adhkar
        assertEquals(3, afterPrayer.first().count)
        assertEquals(listOf(33, 33, 33), afterPrayer.filter { it.text in TASBEEH }.map { it.count })
    }

    private val full = java.io.File("../app/src/main/assets/adhkar.tsv").inputStream().use(AdhkarCatalog::read)

    private fun AdhkarSet.byId(id: String) = adhkar.singleOrNull { it.id == id } ?: error("${this.id.key} has no $id")

    @Test
    fun `every bundled dhikr has its text, count and source`() {
        for (set in AdhkarSetId.entries) {
            val adhkar = full.set(set).adhkar
            assertTrue(adhkar.isNotEmpty(), "${set.key} is empty")
            for (d in adhkar) {
                assertTrue(d.text.isNotBlank() && d.source.isNotBlank() && d.count >= 1, "${set.key}/${d.id}")
                // Sources are short lines naming the hadith collection, not virtue texts.
                assertTrue(d.source.startsWith("رواه") || d.source == "متفق عليه", "${set.key}/${d.id}: ${d.source}")
                assertTrue(d.source.length <= 60, "${set.key}/${d.id} source is long")
            }
        }
    }

    @Test
    fun `the bundled sets are the full Hisn al-Muslim adhkar without Quran text`() {
        val morning = full.set(AdhkarSetId.MORNING)
        val evening = full.set(AdhkarSetId.EVENING)
        val afterPrayer = full.set(AdhkarSetId.AFTER_PRAYER)
        assertEquals(20, morning.adhkar.size)
        assertEquals(17, evening.adhkar.size)
        assertEquals(8, afterPrayer.adhkar.size)
        // Repeat counts as in Hisn al-Muslim.
        for (set in listOf(morning, evening)) {
            assertEquals(4, set.byId("ushhiduka").count)
            assertEquals(7, set.byId("hasbiyallah").count)
            assertEquals(3, set.byId("raditu-billah").count)
            assertEquals(10, set.byId("la-ilaha-illallah-10").count)
            assertEquals(10, set.byId("salat-ala-annabi").count)
            assertEquals("رواه الطبراني", set.byId("salat-ala-annabi").source)
        }
        // Said in the morning only: the hundred tahlil, «عدد خلقه» three times, and «علمًا نافعًا».
        assertEquals(100, morning.byId("la-ilaha-illallah-100").count)
        assertEquals(3, morning.byId("adada-khalqih").count)
        assertTrue(evening.adhkar.none { it.id in setOf("la-ilaha-illallah-100", "adada-khalqih", "ilman-nafian") })
        // Said in the evening only: «أعوذ بكلمات الله التامات» three times (Muslim).
        assertEquals(3, evening.byId("audhu-bikalimatillah").count)
        assertTrue(morning.adhkar.none { it.id == "audhu-bikalimatillah" })
        // Each evening dhikr speaks of the evening.
        assertTrue(evening.byId("ushhiduka").text.startsWith("اللَّهُمَّ إِنِّي أَمْسَيْتُ"))
        assertTrue(evening.byId("ma-asbaha-bi").text.startsWith("اللَّهُمَّ مَا أَمْسَى بِي"))
        assertTrue(evening.adhkar.none { "أَصْبَحْتُ" in it.text || "هَذَا الْيَوْمِ" in it.text })
        assertTrue(morning.adhkar.none { "أَمْسَيْتُ" in it.text || "اللَّيْلَةِ" in it.text })
        // After the Prayer: the full «لا إله إلا الله ... ولو كره الكافرون» (Muslim) before the tasbeeh.
        assertEquals("رواه مسلم", afterPrayer.byId("la-hawla-wala-quwwata").source)
        // Quran text is out of scope (#58): no ayat in the catalog.
        assertTrue(listOf(morning, evening, afterPrayer).flatMap { it.adhkar }.none { '﴿' in it.text || "الْقَيُّومُ لَا تَأْخُذُهُ" in it.text })
    }
}
