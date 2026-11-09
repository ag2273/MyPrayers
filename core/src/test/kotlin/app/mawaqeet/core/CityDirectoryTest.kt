package app.mawaqeet.core

import java.io.File
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * City search and nearest city against the bundled dataset (app/src/main/assets/cities.tsv). Expected values are
 * well-known facts about the places (names, countries, IANA zones), not read back from the file.
 */
class CityDirectoryTest {

    private val cities = bundled

    @Test
    fun `an English name finds the city, largest first`() {
        val cairo = cities.search("cairo").first()
        assertEquals("Cairo", cairo.name)
        assertEquals("EG", cairo.countryCode)
        assertEquals(ZoneId.of("Africa/Cairo"), cairo.zone)
    }

    @Test
    fun `an Arabic name finds the city`() {
        val riyadh = cities.search("الرياض").first()
        assertEquals("Riyadh", riyadh.name)
        assertEquals("الرياض", riyadh.displayName(arabic = true))
        assertEquals(ZoneId.of("Asia/Riyadh"), riyadh.zone)
    }

    @Test
    fun `Arabic search ignores hamza forms, ta marbuta and the article`() {
        assertEquals("Alexandria", cities.search("الاسكندريه").first().name)
        assertEquals("Makkah", cities.search("مكة المكرمه").first().name)
        assertEquals("Riyadh", cities.search("رياض").first().name)
    }

    @Test
    fun `English search ignores case and diacritics and matches the start of any word`() {
        assertEquals("Jeddah", cities.search("JED").first().name)
        assertEquals("Şanlıurfa", cities.search("sanliurfa").first().name)
        assertEquals("Ho Chi Minh City", cities.search("chi minh").first().name)
    }

    @Test
    fun `a blank query finds nothing`() {
        assertEquals(emptyList(), cities.search("  "))
    }

    @Test
    fun `a city is looked up by its GeoNames id`() {
        val cairo = cities.search("cairo").first()
        assertEquals(cairo, cities.byId(cairo.id))
    }

    @Test
    fun `the nearest city gives a point its country and time zone`() {
        // A point in Giza, a few kilometres west of central Cairo.
        val nearest = cities.nearest(30.01, 31.21)
        assertEquals("EG", nearest.city.countryCode)
        assertEquals(Location(30.01, 31.21, ZoneId.of("Africa/Cairo")), nearest.location)
        assertEquals(CalculationMethod.EGYPTIAN, CountryDefaults.suggestionFor(nearest.city.countryCode).method)
    }

    @Test
    fun `manual coordinates yield the official Umm al-Qura times for Makkah`() {
        // Same published row as CalculationSpotCheckTest (ummulqura.org.sa, 5 January 2016), entered as coordinates.
        val location = cities.nearest(21.427009, 39.828685).location
        val settings = PrayerSettings(location, CalculationMethod.UMM_AL_QURA, AsrConvention.STANDARD)
        val date = java.time.LocalDate.of(2016, 1, 5)
        val noon = date.atTime(12, 0).atZone(location.zone).toInstant()
        val day = PrayerCore(settings, java.time.Clock.fixed(noon, location.zone), InMemoryPrayerLog()).prayerDay(date)
        val times = day.prayerTimes.map { it.time.toLocalTime().withSecond(0).withNano(0).toString() }
        // Fajr, Dhuhr, Asr, Maghrib, Isha, within a minute of 05:39 12:26 15:32 17:53 19:23.
        listOf("05:39", "12:26", "15:32", "17:53", "19:23").zip(times).forEach { (published, core) ->
            val diff = java.time.Duration.between(java.time.LocalTime.parse(published), java.time.LocalTime.parse(core)).abs()
            kotlin.test.assertTrue(diff.toMinutes() <= 1, "published $published, core $core")
        }
    }

    companion object {
        val bundled: CityDirectory by lazy {
            File("../app/src/main/assets/cities.tsv").inputStream().use(CityDirectory::read)
        }
    }
}
