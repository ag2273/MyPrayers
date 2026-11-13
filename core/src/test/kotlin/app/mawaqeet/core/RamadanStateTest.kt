package app.mawaqeet.core

import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Ramadan Mode (#90): `PrayerCore.ramadanState(instant)`. Ramadan 1448 (Umm al-Qura) runs from Monday 2027-02-08
 * (1 Ramadan) to Monday 2027-03-08 (29 Ramadan); 1 Shawwal is 2027-03-09.
 */
class RamadanStateTest {
    private val riyadh = ZoneId.of("Asia/Riyadh")

    private fun makkah(date: String, time: String) = ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), riyadh)

    private fun core(settings: PrayerSettings = PrayerSettings.Default) =
        PrayerCore(settings, Clock.fixed(makkah("2027-02-01", "12:00").toInstant(), riyadh), InMemoryPrayerLog())

    private fun settings(hijriOffsetDays: Int = 0, imsakMinutes: Int = RamadanSettings.DEFAULT_IMSAK_MINUTES) =
        PrayerSettings.Default.copy(
            hijriOffsetDays = hijriOffsetDays,
            alerts = AlertSettings(ramadan = RamadanSettings(imsakMinutes = imsakMinutes)),
        )

    private fun PrayerCore.timeOn(date: String, prayer: Prayer): ZonedDateTime =
        prayerDay(LocalDate.parse(date)).prayerTimes.single { it.prayer == prayer }.time

    private fun state(at: ZonedDateTime, settings: PrayerSettings = PrayerSettings.Default) =
        core(settings).ramadanState(at.toInstant())

    @Test
    fun `at noon on 15 Ramadan the user is fasting, counting to that day's Maghrib, with Imsak 10 minutes before Fajr`() {
        val core = core()

        val state = core.ramadanState(makkah("2027-02-22", "12:00").toInstant())

        assertEquals(
            RamadanState.Fasting(
                dayOfRamadan = 15,
                imsak = core.timeOn("2027-02-22", Prayer.FAJR).minusMinutes(10),
                maghrib = core.timeOn("2027-02-22", Prayer.MAGHRIB),
            ),
            state,
        )
    }

    @Test
    fun `before Imsak on a Fasting Day it is that day's suhoor, and from Imsak on the fast`() {
        val core = core()
        val imsak = core.timeOn("2027-02-22", Prayer.FAJR).minusMinutes(10)
        val maghrib = core.timeOn("2027-02-22", Prayer.MAGHRIB)

        assertEquals(RamadanState.Suhoor(15, imsak, maghrib), core.ramadanState(makkah("2027-02-22", "02:30").toInstant()))
        assertEquals(RamadanState.Suhoor(15, imsak, maghrib), core.ramadanState(imsak.minusSeconds(1).toInstant()))
        assertEquals(RamadanState.Fasting(15, imsak, maghrib), core.ramadanState(imsak.toInstant()))
    }

    @Test
    fun `from Maghrib it is the next Fasting Day's suhoor, counting to its Imsak`() {
        val core = core()
        val maghrib = core.timeOn("2027-02-22", Prayer.MAGHRIB)

        val expected = RamadanState.Suhoor(
            dayOfRamadan = 16,
            imsak = core.timeOn("2027-02-23", Prayer.FAJR).minusMinutes(10),
            maghrib = core.timeOn("2027-02-23", Prayer.MAGHRIB),
        )
        assertEquals(RamadanState.Fasting(15, core.timeOn("2027-02-22", Prayer.FAJR).minusMinutes(10), maghrib),
            core.ramadanState(maghrib.minusSeconds(1).toInstant()))
        assertEquals(expected, core.ramadanState(maghrib.toInstant()))
        assertEquals(expected, core.ramadanState(makkah("2027-02-22", "23:30").toInstant()))
        assertEquals(expected, core.ramadanState(makkah("2027-02-23", "01:00").toInstant()))
    }

    @Test
    fun `on the eve of 1 Ramadan suhoor starts at Maghrib, and before it nothing changes`() {
        val core = core()
        val maghrib = core.timeOn("2027-02-07", Prayer.MAGHRIB)

        assertEquals(RamadanState.None, core.ramadanState(makkah("2027-02-07", "12:00").toInstant()))
        assertEquals(RamadanState.None, core.ramadanState(maghrib.minusSeconds(1).toInstant()))
        assertEquals(
            RamadanState.Suhoor(1, core.timeOn("2027-02-08", Prayer.FAJR).minusMinutes(10), core.timeOn("2027-02-08", Prayer.MAGHRIB)),
            core.ramadanState(maghrib.toInstant()),
        )
    }

    @Test
    fun `after Maghrib on the last day there is no suhoor, and Eid morning is not a fast`() {
        val core = core()

        assertEquals(29, (core.ramadanState(makkah("2027-03-08", "12:00").toInstant()) as RamadanState.Fasting).dayOfRamadan)
        assertEquals(RamadanState.None, core.ramadanState(core.timeOn("2027-03-08", Prayer.MAGHRIB).toInstant()))
        assertEquals(RamadanState.None, core.ramadanState(makkah("2027-03-09", "03:00").toInstant()))
        assertEquals(RamadanState.None, core.ramadanState(makkah("2027-03-09", "12:00").toInstant()))
    }

    @Test
    fun `outside Ramadan the state is none`() {
        assertEquals(RamadanState.None, state(makkah("2026-11-12", "12:00")))
        assertEquals(RamadanState.None, state(makkah("2026-11-12", "03:00")))
        assertEquals(RamadanState.None, state(makkah("2026-11-12", "21:00")))
    }

    @Test
    fun `the Hijri offset moves Ramadan Mode with the user's day`() {
        // Offset +1: 2027-02-07 is already 1 Ramadan, and 2027-03-08 is 1 Shawwal.
        assertEquals(1, (state(makkah("2027-02-07", "12:00"), settings(hijriOffsetDays = 1)) as RamadanState.Fasting).dayOfRamadan)
        assertEquals(RamadanState.None, state(makkah("2027-03-08", "12:00"), settings(hijriOffsetDays = 1)))
        // Offset 0: as the calendar says.
        assertEquals(1, (state(makkah("2027-02-08", "12:00"), settings(hijriOffsetDays = 0)) as RamadanState.Fasting).dayOfRamadan)
        // Offset −1: 2027-02-08 is still 30 Sha'ban (suhoor only after its Maghrib), and 2027-03-09 is 29 Ramadan.
        assertEquals(RamadanState.None, state(makkah("2027-02-08", "12:00"), settings(hijriOffsetDays = -1)))
        assertEquals(1, (state(makkah("2027-02-08", "22:00"), settings(hijriOffsetDays = -1)) as RamadanState.Suhoor).dayOfRamadan)
        assertEquals(29, (state(makkah("2027-03-09", "12:00"), settings(hijriOffsetDays = -1)) as RamadanState.Fasting).dayOfRamadan)
    }

    @Test
    fun `Imsak is the chosen minutes before Fajr`() {
        val fajr = core().timeOn("2027-02-22", Prayer.FAJR)

        for (minutes in listOf(0, 10, 30)) {
            val state = state(makkah("2027-02-22", "12:00"), settings(imsakMinutes = minutes)) as RamadanState.Fasting
            assertEquals(Duration.ofMinutes(minutes.toLong()), Duration.between(state.imsak, fajr), "$minutes minutes")
        }
        // With 0 minutes the fast starts at Fajr itself.
        val atFajr = core(settings(imsakMinutes = 0)).ramadanState(fajr.toInstant())
        assertEquals(RamadanState.Fasting::class, atFajr::class)
    }

    @Test
    fun `Imsak minutes default to 10 and stay within 0 to 30`() {
        assertEquals(10, AlertSettings().ramadan.imsakMinutes)
        kotlin.test.assertFailsWith<IllegalArgumentException> { RamadanSettings(imsakMinutes = 31) }
        kotlin.test.assertFailsWith<IllegalArgumentException> { RamadanSettings(imsakMinutes = -1) }
    }

    @Test
    fun `each tap in Settings moves Imsak 5 minutes, stopping at 0 and 30`() {
        assertEquals(15, RamadanSettings().stepped(+1).imsakMinutes)
        assertEquals(0, RamadanSettings().stepped(-2).imsakMinutes)
        assertEquals(0, RamadanSettings(imsakMinutes = 0).stepped(-1).imsakMinutes)
        assertEquals(30, RamadanSettings(imsakMinutes = 25).stepped(+3).imsakMinutes)
    }
}
