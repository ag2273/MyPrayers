package app.mawaqeet.core

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** One-dhikr sets, so a single tap completes each. */
private val catalog = AdhkarCatalog.parse(
    """
    # mawaqeet-adhkar-v1
    morning	m	1	رواه مسلم	ذكر الصباح
    evening	e	1	رواه مسلم	ذكر المساء
    after-prayer	a	1	رواه مسلم	ذكر بعد الصلاة
    """.trimIndent(),
)

private val NOV_9: LocalDate = LocalDate.parse("2026-11-09")
private val NOV_10: LocalDate = LocalDate.parse("2026-11-10")

private fun prayersAt(instant: Instant) = PrayerCore(PrayerSettings.Default, Clock.fixed(instant, ZoneOffset.UTC), InMemoryPrayerLog())

/** The Prayer Time of [prayer] on the Prayer Day [date] in Makkah (the default location). */
private fun timeOf(date: LocalDate, prayer: Prayer): Instant =
    prayersAt(Instant.EPOCH).prayerDay(date).prayerTimes.single { it.prayer == prayer }.time.toInstant()

/**
 * The Adhkar Sets' times (#70): morning counts from Fajr to Dhuhr, evening from Asr to the next Fajr, after-prayer
 * per Prayer against the most recent Prayer whose time has arrived.
 */
class AdhkarTimesTest {
    private val repo = InMemoryAdhkarRepository()

    private fun adhkarAt(instant: Instant) = Adhkar(catalog, prayersAt(instant), repo)

    @Test
    fun `morning adhkar completed exactly at Fajr count for that Prayer Day`() {
        val adhkar = adhkarAt(timeOf(NOV_9, Prayer.FAJR))

        val done = adhkar.count(AdhkarSlot.MORNING)

        assertTrue(done.isInTime)
        assertTrue(done.isComplete)
        assertTrue(adhkar.isComplete(AdhkarSetId.MORNING, NOV_9))
    }

    @Test
    fun `morning adhkar completed a moment before Dhuhr count, and from Dhuhr on they do not`() {
        val beforeDhuhr = adhkarAt(timeOf(NOV_9, Prayer.DHUHR).minusSeconds(1))
        assertTrue(beforeDhuhr.count(AdhkarSlot.MORNING).isComplete)

        val atDhuhr = adhkarAt(timeOf(NOV_10, Prayer.DHUHR))
        val read = atDhuhr.count(AdhkarSlot.MORNING)

        // Still readable at any time, but it isn't recorded for the morning.
        assertFalse(read.isInTime)
        assertEquals(null, read.currentIndex)
        assertFalse(read.isComplete)
        assertFalse(atDhuhr.isComplete(AdhkarSetId.MORNING, NOV_10))
    }

    @Test
    fun `evening adhkar do not count before Asr and count from exactly Asr`() {
        val beforeAsr = adhkarAt(timeOf(NOV_9, Prayer.ASR).minusSeconds(1))
        assertFalse(beforeAsr.progress(AdhkarSlot.EVENING).isInTime)
        assertFalse(beforeAsr.count(AdhkarSlot.EVENING).isComplete)

        val atAsr = adhkarAt(timeOf(NOV_10, Prayer.ASR))
        assertTrue(atAsr.count(AdhkarSlot.EVENING).isComplete)
        assertTrue(atAsr.isComplete(AdhkarSetId.EVENING, NOV_10))
    }

    @Test
    fun `evening adhkar read after midnight count for the evening of the Prayer Day before`() {
        val afterMidnight = adhkarAt(timeOf(NOV_10, Prayer.FAJR).minusSeconds(3 * 3600))

        val done = afterMidnight.count(AdhkarSlot.EVENING)

        assertEquals(NOV_9, done.prayerDay)
        assertTrue(done.isComplete)
        assertTrue(afterMidnight.isComplete(AdhkarSetId.EVENING, NOV_9))
        // Morning adhkar at that hour are outside the morning, of either day.
        assertFalse(afterMidnight.progress(AdhkarSlot.MORNING).isInTime)
    }

    @Test
    fun `at the next Fajr the evening is over and a new morning begins`() {
        val atFajr = adhkarAt(timeOf(NOV_10, Prayer.FAJR))

        assertEquals(NOV_10, atFajr.progress(AdhkarSlot.EVENING).prayerDay)
        assertFalse(atFajr.progress(AdhkarSlot.EVENING).isInTime)
        assertTrue(atFajr.progress(AdhkarSlot.MORNING).isInTime)
    }

    @Test
    fun `after-prayer adhkar are recorded for the Prayer they were read after, and each Prayer separately`() {
        val adhkar = adhkarAt(timeOf(NOV_9, Prayer.MAGHRIB).plusSeconds(600))

        assertTrue(adhkar.count(AdhkarSlot.afterPrayer(Prayer.ASR)).isComplete)

        assertTrue(adhkar.isComplete(AdhkarSlot.afterPrayer(Prayer.ASR), NOV_9))
        assertFalse(adhkar.isComplete(AdhkarSlot.afterPrayer(Prayer.MAGHRIB), NOV_9))
        assertEquals(0, adhkar.progress(AdhkarSlot.afterPrayer(Prayer.MAGHRIB)).currentIndex)
        assertTrue(adhkar.count(AdhkarSlot.afterPrayer(Prayer.MAGHRIB)).isComplete)
        assertEquals(
            listOf(false, false, true, true, false),
            Prayer.entries.map { adhkar.isComplete(AdhkarSlot.afterPrayer(it), NOV_9) },
        )
    }

    @Test
    fun `after-prayer adhkar for a Prayer whose time has not come can be read but are not recorded`() {
        val adhkar = adhkarAt(timeOf(NOV_9, Prayer.DHUHR).plusSeconds(600))

        val isha = adhkar.count(AdhkarSlot.afterPrayer(Prayer.ISHA))

        assertFalse(isha.isInTime)
        assertFalse(isha.isComplete)
    }

    @Test
    fun `the default Prayer is the most recent one whose time has arrived`() {
        assertEquals(Prayer.FAJR, adhkarAt(timeOf(NOV_9, Prayer.FAJR)).defaultAfterPrayer())
        assertEquals(Prayer.FAJR, adhkarAt(timeOf(NOV_9, Prayer.DHUHR).minusSeconds(1)).defaultAfterPrayer())
        assertEquals(Prayer.DHUHR, adhkarAt(timeOf(NOV_9, Prayer.DHUHR)).defaultAfterPrayer())
        assertEquals(Prayer.ASR, adhkarAt(timeOf(NOV_9, Prayer.ASR).plusSeconds(60)).defaultAfterPrayer())
        assertEquals(Prayer.ISHA, adhkarAt(timeOf(NOV_9, Prayer.ISHA)).defaultAfterPrayer())
        // After midnight it is still the previous Prayer Day's Isha.
        assertEquals(Prayer.ISHA, adhkarAt(timeOf(NOV_10, Prayer.FAJR).minusSeconds(3 * 3600)).defaultAfterPrayer())
    }

    @Test
    fun `the list holds the morning, the evening and the five after-prayer slots`() {
        val slots = adhkarAt(timeOf(NOV_9, Prayer.FAJR)).slots

        assertEquals(
            listOf(
                "morning", "evening",
                "after-prayer:fajr", "after-prayer:dhuhr", "after-prayer:asr", "after-prayer:maghrib", "after-prayer:isha",
            ),
            slots.map { it.key },
        )
    }
}
