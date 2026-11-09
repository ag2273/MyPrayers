package app.mawaqeet.core

import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HighLatitudeTest {
    private val oslo = Location(59.9139, 10.7522, ZoneId.of("Europe/Oslo"))
    private val tromso = Location(69.6492, 18.9553, ZoneId.of("Europe/Oslo"))

    private fun displayDay(settings: PrayerSettings, date: String): DisplayDay {
        val noon = LocalDate.parse(date).atTime(12, 0).atZone(settings.location.zone)
        return PrayerCore(settings, Clock.fixed(noon.toInstant(), settings.location.zone), InMemoryPrayerLog()).displayDay()
    }

    private fun mwl(location: Location, rule: HighLatitudeRule = HighLatitudeRule.AUTOMATIC) =
        PrayerSettings(location, CalculationMethod.MUSLIM_WORLD_LEAGUE, AsrConvention.STANDARD, highLatitudeRule = rule)

    @Test
    fun `Makkah never needs a high-latitude rule`() {
        assertNull(displayDay(PrayerSettings.Default, "2026-06-21").highLatitude)
        assertNull(displayDay(PrayerSettings.Default, "2026-12-21").highLatitude)
    }

    @Test
    fun `in an Oslo summer Fajr and Isha follow the seventh of the night automatically, and it is reported`() {
        // At 60° N the sun never reaches 18° below the horizon in June.
        val day = displayDay(mwl(oslo), "2026-06-21")

        assertTrue(day.highLatitudeRuleApplied)
        assertEquals(HighLatitudeAdjustment(HighLatitudeRule.SEVENTH_OF_THE_NIGHT, polar = false), day.highLatitude)
        // Isha is the end of the first seventh of the night, Fajr the start of the last seventh.
        val tomorrow = displayDay(mwl(oslo), "2026-06-22")
        val night = Duration.between(day.timeOf(Prayer.MAGHRIB), tomorrow.sunrise)
        val ishaAfterMaghrib = Duration.between(day.timeOf(Prayer.MAGHRIB), day.timeOf(Prayer.ISHA))
        assertTrue((ishaAfterMaghrib - night.dividedBy(7)).abs() <= Duration.ofMinutes(2), "Isha $ishaAfterMaghrib after Maghrib, night $night")
    }

    @Test
    fun `in a London winter the angles fit inside the night and no rule is reported`() {
        val london = Location(51.5072, -0.1276, ZoneId.of("Europe/London"))
        assertNull(displayDay(mwl(london), "2026-12-21").highLatitude)
    }

    @Test
    fun `the user can choose another rule, which moves Fajr and Isha and is reported`() {
        val seventh = displayDay(mwl(oslo), "2026-06-21")
        val middle = displayDay(mwl(oslo, HighLatitudeRule.MIDDLE_OF_THE_NIGHT), "2026-06-21")
        val twilight = displayDay(mwl(oslo, HighLatitudeRule.TWILIGHT_ANGLE), "2026-06-21")

        assertEquals(HighLatitudeRule.MIDDLE_OF_THE_NIGHT, middle.highLatitude?.rule)
        assertEquals(HighLatitudeRule.TWILIGHT_ANGLE, twilight.highLatitude?.rule)
        // The seventh of the night keeps Fajr latest and Isha earliest.
        assertTrue(middle.timeOf(Prayer.FAJR).isBefore(seventh.timeOf(Prayer.FAJR)))
        assertTrue(middle.timeOf(Prayer.ISHA).isAfter(seventh.timeOf(Prayer.ISHA)))
        // Dhuhr, Asr and Maghrib never depend on the rule.
        assertEquals(seventh.timeOf(Prayer.DHUHR), middle.timeOf(Prayer.DHUHR))
        assertEquals(seventh.timeOf(Prayer.MAGHRIB), twilight.timeOf(Prayer.MAGHRIB))
    }

    @Test
    fun `under the midnight sun the core still gives five ordered Prayer Times, from the nearest workable latitude`() {
        listOf("2026-06-21", "2026-12-21").forEach { date ->
            val day = displayDay(mwl(tromso), date)

            assertEquals(HighLatitudeAdjustment(HighLatitudeRule.SEVENTH_OF_THE_NIGHT, polar = true), day.highLatitude, date)
            val times = listOf(day.timeOf(Prayer.FAJR), day.sunrise) + Prayer.entries.drop(1).map(day::timeOf)
            assertEquals(times.sortedBy { it.toInstant() }, times, "times in day order on $date")
            assertEquals(LocalDate.parse(date), day.timeOf(Prayer.DHUHR).toLocalDate())
        }
    }

    @Test
    fun `the polar fallback also keeps the alert plan and Prayer Days working`() {
        val june = LocalDate.parse("2026-06-21").atTime(12, 0).atZone(tromso.zone).toInstant()
        val core = PrayerCore(mwl(tromso), Clock.fixed(june, tromso.zone), InMemoryPrayerLog())

        assertTrue(core.alertPlan().alerts.isNotEmpty())
        assertEquals(LocalDate.parse("2026-06-21"), core.currentPrayerDay().date)
    }

    @Test
    fun `automatic means the seventh of the night above 48 degrees, north or south`() {
        assertEquals(HighLatitudeRule.SEVENTH_OF_THE_NIGHT, HighLatitudeRule.AUTOMATIC.resolve(59.9))
        assertEquals(HighLatitudeRule.SEVENTH_OF_THE_NIGHT, HighLatitudeRule.AUTOMATIC.resolve(-54.8))
        assertEquals(HighLatitudeRule.MIDDLE_OF_THE_NIGHT, HighLatitudeRule.AUTOMATIC.resolve(30.0))
        assertEquals(HighLatitudeRule.TWILIGHT_ANGLE, HighLatitudeRule.TWILIGHT_ANGLE.resolve(30.0))
    }
}
