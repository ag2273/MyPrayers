package app.mawaqeet.widget

import app.mawaqeet.R
import app.mawaqeet.core.DigitStyle
import app.mawaqeet.core.DisplayFormatter
import app.mawaqeet.core.LogState
import app.mawaqeet.core.PastPlace
import app.mawaqeet.core.Prayer
import app.mawaqeet.core.PrayerCore
import app.mawaqeet.core.PrayerLogRepository
import app.mawaqeet.core.PrayerSettings
import app.mawaqeet.widget.WeekLogContent.Cell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/** Plain JVM tests of what the two Prayer Log widgets show, over a real core (Makkah, Umm al-Qura). */
class PrayerLogWidgetContentTest {
    private val riyadh = ZoneId.of("Asia/Riyadh")
    private val arabic = DisplayFormatter(Locale.forLanguageTag("ar"), DigitStyle.ARABIC_INDIC)
    private val english = DisplayFormatter(Locale.ENGLISH, DigitStyle.WESTERN)

    /** 2026-11-08 in Makkah: Fajr 05:09, Sunrise 06:27, Dhuhr 12:04, Asr 15:17, Maghrib 17:41, Isha 19:11. */
    private fun coreAt(dateTime: String, log: PrayerLogRepository) = PrayerCore(
        PrayerSettings.Default,
        Clock.fixed(LocalDateTime.parse(dateTime).atZone(riyadh).toInstant(), ZoneOffset.UTC),
        log,
    )

    private fun date(text: String) = LocalDate.parse(text)

    private fun todayAt(core: PrayerCore, formatter: DisplayFormatter = arabic): TodayLogContent {
        val day = core.displayDay()
        return TodayLogContent.of(day, core.prayerDay(day.date), formatter)
    }

    @Test
    fun `today's rows show each Prayer's time and state, with the next Prayer highlighted`() {
        val log = MemoryLog(firstUse = date("2026-10-01"))
        val core = coreAt("2026-11-08T13:00", log)
        core.mark(date("2026-11-08"), Prayer.FAJR)

        val content = todayAt(core)

        assertEquals(
            listOf(LogState.PRAYED, LogState.NOT_MARKED, LogState.NOT_YET_DUE, LogState.NOT_YET_DUE, LogState.NOT_YET_DUE),
            content.rows.map { it.state },
        )
        assertEquals(listOf(Prayer.ASR), content.rows.filter { it.isNext }.map { it.prayer })
        assertEquals("١٥:١٧", content.rows[2].time)
        assertEquals("٠٦:٢٧", content.sunrise)
        assertEquals(WidgetDate("الأحد ٨ نوفمبر", "٢٨ جمادى الأولى ١٤٤٨ هـ"), content.date)
    }

    @Test
    fun `a mark shows on the next render, and an unmark takes it off`() {
        val log = MemoryLog(firstUse = date("2026-10-01"))
        val core = coreAt("2026-11-08T20:00", log)

        core.mark(date("2026-11-08"), Prayer.ISHA)
        assertEquals(LogState.PRAYED, todayAt(core).rows.last().state)

        core.unmark(date("2026-11-08"), Prayer.ISHA)
        assertEquals(LogState.NOT_MARKED, todayAt(core).rows.last().state)
    }

    @Test
    fun `after midnight the widget shows the new date, all not yet due, and nothing highlighted until Fajr is next`() {
        val log = MemoryLog(firstUse = date("2026-10-01"))
        val core = coreAt("2026-11-09T00:30", log)
        core.mark(date("2026-11-08"), Prayer.ISHA)

        val content = todayAt(core, english)

        assertEquals("Mon 9 Nov", content.date.gregorian)
        assertTrue(content.rows.all { it.state == LogState.NOT_YET_DUE })
        assertEquals(listOf(Prayer.FAJR), content.rows.filter { it.isNext }.map { it.prayer })
    }

    @Test
    fun `on a Friday Dhuhr's row is Jumu'ah`() {
        val content = todayAt(coreAt("2026-11-13T10:00", MemoryLog(firstUse = date("2026-10-01"))))

        assertEquals(R.string.prayer_jumuah, content.rows[1].nameRes)
    }

    @Test
    fun `the week grid is the core's last 7 Prayer Days with the same total as the Prayer Log screen`() {
        val log = MemoryLog(firstUse = date("2026-10-01"))
        val core = coreAt("2026-11-08T13:00", log)
        core.mark(date("2026-11-08"), Prayer.FAJR)
        core.mark(date("2026-11-05"), Prayer.ISHA)
        core.mark(date("2026-11-02"), Prayer.FAJR)

        val week = core.week()
        val content = WeekLogContent.of(week, english)

        assertEquals(listOf("M", "T", "W", "T", "F", "S", "S"), content.weekdays)
        val fajr = content.rows.first { it.prayer == Prayer.FAJR }.cells
        assertEquals(Cell.PRAYED, fajr.first())
        assertEquals(Cell.PRAYED, fajr.last())
        assertEquals(Cell.NOT_MARKED, fajr[3])
        val isha = content.rows.first { it.prayer == Prayer.ISHA }.cells
        assertEquals(Cell.PRAYED, isha[3])
        assertEquals(Cell.NOT_YET_DUE, isha.last())
        // 6 full days and today's Fajr and Dhuhr: 32 possible, 3 prayed. The Prayer Log screen shows the same summary.
        assertEquals(week.summary.total.let { english.count(it.prayed, it.possible) }, content.total)
        assertEquals(english.count(3, 32), content.total)
    }

    @Test
    fun `days before first use show no dot and don't count`() {
        val log = MemoryLog(firstUse = date("2026-11-07"))
        val core = coreAt("2026-11-08T13:00", log)

        val content = WeekLogContent.of(core.week(), arabic)

        val dhuhr = content.rows.first { it.prayer == Prayer.DHUHR }.cells
        assertEquals(List(5) { Cell.BEFORE_FIRST_USE }, dhuhr.take(5))
        assertEquals(listOf(Cell.NOT_MARKED, Cell.NOT_MARKED), dhuhr.drop(5))
        assertEquals(arabic.count(0, 7), content.total)
    }

    @Test
    fun `the week's Dhuhr row keeps Dhuhr's name, even with a Friday in it`() {
        val content = WeekLogContent.of(coreAt("2026-11-13T13:00", MemoryLog(date("2026-10-01"))).week(), arabic)

        assertEquals(R.string.prayer_dhuhr, content.rows[1].nameRes)
        assertFalse(content.rows.any { it.nameRes == R.string.prayer_jumuah })
    }

    /** The core's repository contract, in memory. */
    private class MemoryLog(private var firstUse: LocalDate?) : PrayerLogRepository {
        private val entries = mutableSetOf<Pair<LocalDate, Prayer>>()
        private val places = mutableListOf<PastPlace>()

        override fun prayedOn(prayerDay: LocalDate) = entries.filter { it.first == prayerDay }.mapTo(mutableSetOf()) { it.second }
        override fun setPrayed(prayerDay: LocalDate, prayer: Prayer, prayed: Boolean) {
            if (prayed) entries += prayerDay to prayer else entries -= prayerDay to prayer
        }
        override fun firstUseDate() = firstUse
        override fun recordFirstUseIfAbsent(prayerDay: LocalDate) {
            if (firstUse == null) firstUse = prayerDay
        }
        override fun pastPlaces() = places.toList()
        override fun addPastPlace(place: PastPlace) {
            places += place
        }
        override fun deleteAllEntries(firstUseFrom: LocalDate) {
            entries.clear()
            firstUse = firstUseFrom
        }
    }
}
