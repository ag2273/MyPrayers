package app.mawaqeet.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

private fun day(date: String, offsetDays: Int = 0): CalendarDay = HijriCalendar(offsetDays).day(LocalDate.parse(date))

/**
 * Expected dates are the published Umm al-Qura calendar (ummulqura.org.sa), not recomputed: 1 Muharram 1448 =
 * 16 Jun 2026, 1 Ramadan 1448 = 8 Feb 2027, 1 Shawwal 1448 = 9 Mar 2027, 9 Dhu al-Hijjah 1448 = 15 May 2027,
 * 1 Muharram 1449 = 6 Jun 2027, 1 Ramadan 1449 = 28 Jan 2028.
 */
class HijriCalendarTest {

    @Test
    fun `1 Muharram 1448 is the Hijri New Year`() {
        val newYear = day("2026-06-16")
        assertEquals(HijriDate(1448, 1, 1), newYear.hijri)
        assertEquals(setOf(Occasion.HIJRI_NEW_YEAR), newYear.occasions)
    }

    @Test
    fun `every Occasion of 1448 falls on its Umm al-Qura day`() {
        assertEquals(setOf(Occasion.TASUA), day("2026-06-24").occasions)
        assertEquals(setOf(Occasion.ASHURA), day("2026-06-25").occasions)
        assertEquals(setOf(Occasion.MAWLID), day("2026-08-25").occasions)
        assertEquals(setOf(Occasion.ISRA_AND_MIRAJ), day("2027-01-05").occasions)
        assertEquals(setOf(Occasion.RAMADAN_START), day("2027-02-08").occasions)
        assertEquals(emptySet(), day("2027-02-27").occasions) // 20 Ramadan
        assertEquals(setOf(Occasion.LAST_TEN_NIGHTS), day("2027-02-28").occasions) // 21 Ramadan
        assertEquals(setOf(Occasion.LAST_TEN_NIGHTS), day("2027-03-08").occasions) // 29 Ramadan, its last day
        assertEquals(setOf(Occasion.EID_AL_FITR), day("2027-03-09").occasions)
        assertEquals(emptySet(), day("2027-05-06").occasions) // 30 Dhu al-Qa'dah
        assertEquals(setOf(Occasion.FIRST_TEN_OF_DHU_AL_HIJJAH), day("2027-05-07").occasions)
        assertEquals(setOf(Occasion.FIRST_TEN_OF_DHU_AL_HIJJAH, Occasion.ARAFAH), day("2027-05-15").occasions)
        assertEquals(setOf(Occasion.FIRST_TEN_OF_DHU_AL_HIJJAH, Occasion.EID_AL_ADHA), day("2027-05-16").occasions)
        assertEquals(setOf(Occasion.TASHREEQ), day("2027-05-17").occasions)
        assertEquals(setOf(Occasion.TASHREEQ), day("2027-05-19").occasions)
        assertEquals(emptySet(), day("2027-05-20").occasions) // 14 Dhu al-Hijjah
    }

    @Test
    fun `the Occasions of 1449 fall on their Umm al-Qura days`() {
        assertEquals(setOf(Occasion.HIJRI_NEW_YEAR), day("2027-06-06").occasions)
        assertEquals(setOf(Occasion.ASHURA), day("2027-06-15").occasions)
        assertEquals(setOf(Occasion.RAMADAN_START), day("2028-01-28").occasions)
        assertEquals(setOf(Occasion.EID_AL_FITR), day("2028-02-26").occasions)
        assertEquals(setOf(Occasion.FIRST_TEN_OF_DHU_AL_HIJJAH, Occasion.ARAFAH), day("2028-05-04").occasions)
        assertEquals(setOf(Occasion.FIRST_TEN_OF_DHU_AL_HIJJAH, Occasion.EID_AL_ADHA), day("2028-05-05").occasions)
    }

    @Test
    fun `Mondays and Thursdays are Sunnah Fasts, other weekdays are not`() {
        assertEquals(setOf(SunnahFast.MONDAY_THURSDAY), day("2026-11-16").fasts) // Monday 6 Jumada al-Akhirah 1448
        assertEquals(setOf(SunnahFast.MONDAY_THURSDAY), day("2026-11-19").fasts) // Thursday
        assertEquals(emptySet(), day("2026-11-17").fasts) // Tuesday
        assertEquals(emptySet(), day("2026-11-20").fasts) // Friday
    }

    @Test
    fun `the 13th, 14th and 15th of a Hijri month are the white days, on top of the weekday`() {
        assertEquals(emptySet(), day("2026-11-22").fasts) // Sunday 12 Jumada al-Akhirah 1448
        assertEquals(setOf(SunnahFast.MONDAY_THURSDAY, SunnahFast.WHITE_DAYS), day("2026-11-23").fasts) // Monday 13
        assertEquals(setOf(SunnahFast.WHITE_DAYS), day("2026-11-24").fasts) // 14
        assertEquals(setOf(SunnahFast.WHITE_DAYS), day("2026-11-25").fasts) // 15
        assertEquals(emptySet(), day("2026-11-27").fasts) // Friday 17
    }

    @Test
    fun `Arafah, Tasu'a and Ashura, and the six of Shawwal after Eid are Sunnah Fasts`() {
        assertEquals(setOf(SunnahFast.ARAFAH), day("2027-05-15").fasts) // Saturday 9 Dhu al-Hijjah 1448
        assertEquals(setOf(SunnahFast.ASHURA), day("2026-06-24").fasts) // Wednesday 9 Muharram 1448
        assertEquals(setOf(SunnahFast.ASHURA, SunnahFast.MONDAY_THURSDAY), day("2026-06-25").fasts) // Thursday 10
        assertEquals(setOf(SunnahFast.SIX_OF_SHAWWAL), day("2027-03-10").fasts) // Wednesday 2 Shawwal 1448
        assertEquals(setOf(SunnahFast.SIX_OF_SHAWWAL, SunnahFast.MONDAY_THURSDAY), day("2027-03-15").fasts) // Monday 7
        assertEquals(emptySet(), day("2027-03-16").fasts) // Tuesday 8 Shawwal
    }

    @Test
    fun `the two Eids and the days of Tashreeq are Forbidden Fast Days with no Sunnah Fast`() {
        listOf("2027-03-09", "2027-05-16", "2027-05-17", "2027-05-18", "2027-05-19").forEach {
            val forbidden = day(it)
            assertEquals(true, forbidden.fastingForbidden, it)
            assertEquals(emptySet(), forbidden.fasts, it)
        }
        // 17 May 2027, a Monday of Tashreeq, is no Monday fast.
        assertEquals(java.time.DayOfWeek.MONDAY, LocalDate.parse("2027-05-17").dayOfWeek)
        assertEquals(false, day("2027-05-15").fastingForbidden) // Arafah
        assertEquals(false, day("2027-03-10").fastingForbidden) // 2 Shawwal
    }

    @Test
    fun `13 Dhu al-Hijjah is not a white day, the 14th and 15th are`() {
        assertEquals(emptySet(), day("2027-05-19").fasts) // Wednesday 13 Dhu al-Hijjah 1448
        assertEquals(setOf(SunnahFast.WHITE_DAYS, SunnahFast.MONDAY_THURSDAY), day("2027-05-20").fasts) // Thursday 14
        assertEquals(setOf(SunnahFast.WHITE_DAYS), day("2027-05-21").fasts) // 15
    }

    @Test
    fun `in Ramadan neither Mondays and Thursdays nor the white days are Sunnah Fasts`() {
        assertEquals(emptySet(), day("2027-02-08").fasts) // Monday 1 Ramadan 1448
        assertEquals(emptySet(), day("2027-02-20").fasts) // 13 Ramadan
        assertEquals(false, day("2027-02-08").fastingForbidden)
    }

    @Test
    fun `the Hijri offset moves every Occasion and Forbidden Fast Day by a day`() {
        assertEquals(setOf(Occasion.RAMADAN_START), day("2027-02-07", offsetDays = 1).occasions)
        assertEquals(setOf(Occasion.RAMADAN_START), day("2027-02-08", offsetDays = 0).occasions)
        assertEquals(setOf(Occasion.RAMADAN_START), day("2027-02-09", offsetDays = -1).occasions)

        assertEquals(true, day("2027-03-08", offsetDays = 1).fastingForbidden) // Eid al-Fitr a day early
        assertEquals(false, day("2027-03-09", offsetDays = -1).fastingForbidden) // still 29 Ramadan
        assertEquals(setOf(Occasion.EID_AL_FITR), day("2027-03-10", offsetDays = -1).occasions)

        assertEquals(setOf(SunnahFast.ARAFAH), day("2027-05-14", offsetDays = 1).fasts)
        assertEquals(setOf(SunnahFast.ARAFAH), day("2027-05-16", offsetDays = -1).fasts) // a Sunday, not Eid
    }

    @Test
    fun `a date range gives each of its days in order, both ends included`() {
        val days = HijriCalendar().days(LocalDate.parse("2027-02-27"), LocalDate.parse("2027-03-10"))
        assertEquals(12, days.size)
        assertEquals(LocalDate.parse("2027-02-27"), days.first().date)
        assertEquals(HijriDate(1448, 10, 2), days.last().hijri)
        // Ramadan 1448 has 29 days: 8 March is its last, 9 March is Eid.
        assertEquals(HijriDate(1448, 9, 29), days[9].hijri)
        assertEquals(HijriDate(1448, 10, 1), days[10].hijri)
    }

    @Test
    fun `a Hijri month gives its days from the 1st, as many as Umm al-Qura gives it`() {
        val jumada = HijriCalendar().month(HijriMonth(1448, 6))
        assertEquals(LocalDate.parse("2026-11-11"), jumada.first().date) // a Wednesday
        assertEquals(HijriDate(1448, 6, 1), jumada.first().hijri)
        assertEquals(29, jumada.size) // 1 Rajab is 10 December
        assertEquals(LocalDate.parse("2026-12-09"), jumada.last().date)

        val dhuAlHijjah = HijriCalendar().month(HijriMonth(1448, 12))
        assertEquals(LocalDate.parse("2027-05-07"), dhuAlHijjah.first().date)
        assertEquals(30, dhuAlHijjah.size) // 1 Muharram 1449 is 6 June 2027
    }

    @Test
    fun `the Hijri offset moves a month's days with it`() {
        assertEquals(LocalDate.parse("2026-11-10"), HijriCalendar(offsetDays = 1).month(HijriMonth(1448, 6)).first().date)
        assertEquals(LocalDate.parse("2026-11-12"), HijriCalendar(offsetDays = -1).month(HijriMonth(1448, 6)).first().date)
    }

    @Test
    fun `the month after Dhu al-Hijjah is Muharram of the next year, and back`() {
        assertEquals(HijriMonth(1449, 1), HijriMonth(1448, 12).next())
        assertEquals(HijriMonth(1448, 12), HijriMonth(1449, 1).previous())
        assertEquals(HijriMonth(1448, 7), HijriMonth(1448, 6).next())
        assertEquals(HijriMonth(1448, 6), HijriMonth.of(HijriDate(1448, 6, 3)))
    }

    @Test
    fun `in this month lists the month's Occasions and fasts in date order, without the weekly fast`() {
        fun entries(month: HijriMonth) = HijriCalendar().inMonth(month).map {
            when (it) {
                is MonthEntry.OccasionDays -> it.occasion.name
                is MonthEntry.FastDays -> it.fast.name
            } + " " + it.first.date + ".." + it.last.date
        }

        assertEquals(
            listOf(
                "FIRST_TEN_OF_DHU_AL_HIJJAH 2027-05-07..2027-05-16",
                "ARAFAH 2027-05-15..2027-05-15",
                "EID_AL_ADHA 2027-05-16..2027-05-16",
                "TASHREEQ 2027-05-17..2027-05-19",
                "WHITE_DAYS 2027-05-20..2027-05-21",
            ),
            entries(HijriMonth(1448, 12)),
        )
        assertEquals(
            listOf(
                "EID_AL_FITR 2027-03-09..2027-03-09",
                "SIX_OF_SHAWWAL 2027-03-10..2027-03-15",
                "WHITE_DAYS 2027-03-21..2027-03-23",
            ),
            entries(HijriMonth(1448, 10)),
        )
        assertEquals(listOf("WHITE_DAYS 2026-11-23..2026-11-25"), entries(HijriMonth(1448, 6)))
    }
}
