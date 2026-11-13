package app.mawaqeet.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

private fun upcoming(today: String, offsetDays: Int = 0): List<Upcoming> =
    HijriCalendar(offsetDays).upcoming(LocalDate.parse(today))

/** What an entry is, for comparing lists: an Occasion or a Sunnah Fast. */
private val Upcoming.what: Any
    get() = when (this) {
        is Upcoming.OccasionDays -> occasion
        is Upcoming.FastDays -> fast
    }

/** Dates: the published Umm al-Qura calendar, as in [HijriCalendarTest]. 13 Nov 2026 is Friday 3 Jumada al-Akhirah 1448. */
class UpcomingTest {

    @Test
    fun `the coming twelve months list the Occasions and the next weekly fasts in date order`() {
        val list = upcoming("2026-11-13")

        assertEquals(
            listOf(
                SunnahFast.MONDAY_THURSDAY to "2026-11-16",
                SunnahFast.WHITE_DAYS to "2026-11-23",
                Occasion.ISRA_AND_MIRAJ to "2027-01-05",
                Occasion.RAMADAN_START to "2027-02-08",
                Occasion.LAST_TEN_NIGHTS to "2027-02-28",
                Occasion.EID_AL_FITR to "2027-03-09",
                Occasion.FIRST_TEN_OF_DHU_AL_HIJJAH to "2027-05-07",
                Occasion.ARAFAH to "2027-05-15",
                Occasion.EID_AL_ADHA to "2027-05-16",
                Occasion.TASHREEQ to "2027-05-17",
                Occasion.HIJRI_NEW_YEAR to "2027-06-06",
                Occasion.TASUA to "2027-06-14",
                Occasion.ASHURA to "2027-06-15",
                Occasion.MAWLID to "2027-08-14",
            ),
            list.map { it.what to it.first.date.toString() },
        )
    }

    @Test
    fun `each entry says how many days away it is and where a run of days ends`() {
        val list = upcoming("2026-11-13")

        assertEquals(3, list[0].daysAway) // Monday 16 November
        assertEquals(10, list[1].daysAway) // the white days, from Monday 23 November
        assertEquals(LocalDate.parse("2026-11-25"), list[1].last.date)
        assertEquals(HijriDate(1448, 6, 13), list[1].first.hijri)

        val lastTen = list.single { it.what == Occasion.LAST_TEN_NIGHTS }
        assertEquals(LocalDate.parse("2027-03-08"), lastTen.last.date) // 29 Ramadan: a 29-day Ramadan
    }

    @Test
    fun `Mondays, Thursdays and the white days are shown once, not every week`() {
        val list = upcoming("2026-11-13")

        assertEquals(1, list.count { it.what == SunnahFast.MONDAY_THURSDAY })
        assertEquals(1, list.count { it.what == SunnahFast.WHITE_DAYS })
    }

    @Test
    fun `an Occasion already under way today comes first, today, and the weekly fasts wait until after Ramadan and Eid`() {
        val list = upcoming("2027-03-02") // Tuesday 23 Ramadan 1448

        val first = list.first()
        assertEquals(Occasion.LAST_TEN_NIGHTS, first.what)
        assertEquals(0, first.daysAway)
        assertEquals(LocalDate.parse("2027-02-28"), first.first.date)

        assertEquals(LocalDate.parse("2027-03-11"), list.single { it.what == SunnahFast.MONDAY_THURSDAY }.first.date) // Thursday 3 Shawwal
        assertEquals(LocalDate.parse("2027-03-21"), list.single { it.what == SunnahFast.WHITE_DAYS }.first.date) // 13 Shawwal
    }

    @Test
    fun `the list follows the Hijri offset`() {
        val list = upcoming("2026-11-13", offsetDays = 1)

        assertEquals(LocalDate.parse("2027-02-07"), list.single { it.what == Occasion.RAMADAN_START }.first.date)
    }
}
