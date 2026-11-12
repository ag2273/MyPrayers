package app.mawaqeet.core

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WeatherTest {
    private val tanta = Location(30.7865, 31.0004, ZoneId.of("Africa/Cairo"))
    private val now = Instant.parse("2026-11-12T13:00:00Z")

    /** An answer as Open-Meteo gives it, units and all. */
    private val answer = """
        {"latitude":30.8,"longitude":31.0,"generationtime_ms":0.05,"utc_offset_seconds":7200,"timezone":"Africa/Cairo",
        "current_units":{"time":"iso8601","interval":"seconds","temperature_2m":"°C","relative_humidity_2m":"%"},
        "current":{"time":"2026-11-12T15:00","interval":900,"temperature_2m":28.6,"relative_humidity_2m":38},
        "daily_units":{"time":"iso8601","precipitation_probability_max":"%"},
        "daily":{"time":["2026-11-12"],"precipitation_probability_max":[5]}}
    """.trimIndent()

    @Test
    fun `the request carries the location rounded to a tenth of a degree, never the exact place`() {
        val url = OpenMeteo.url(tanta)

        assertTrue(url.startsWith("https://api.open-meteo.com/v1/forecast?latitude=30.8&longitude=31.0&"), url)
        assertTrue("current=temperature_2m,relative_humidity_2m" in url)
        assertTrue("daily=precipitation_probability_max" in url)
        assertFalse("30.78" in url || "31.0004" in url)
    }

    @Test
    fun `an answer gives the temperature and humidity now and the chance of rain today`() {
        val weather = OpenMeteo.parse(answer, tanta, now)!!

        assertEquals(29, weather.temperature)
        assertEquals(38, weather.humidityPercent)
        assertEquals(5, weather.rainChancePercent)
        assertEquals(30.8, weather.latitude)
        assertEquals(31.0, weather.longitude)
    }

    @Test
    fun `below zero reads as a negative temperature, and a day with no rain chance as zero`() {
        val cold = answer.replace("28.6", "-3.4").replace("[5]", "[null]")

        val weather = OpenMeteo.parse(cold, tanta, now)!!

        assertEquals(-3, weather.temperature)
        assertEquals(0, weather.rainChancePercent)
    }

    @Test
    fun `an answer without the current weather is not read`() {
        assertNull(OpenMeteo.parse("""{"error":true,"reason":"Too many requests"}""", tanta, now))
    }

    @Test
    fun `weather is fetched again after about an hour, and goes from the widget after three hours without a fetch`() {
        val weather = OpenMeteo.parse(answer, tanta, now)!!

        assertFalse(weather.isDueFor(tanta, now.plus(Duration.ofMinutes(30))))
        assertTrue(weather.isDueFor(tanta, now.plus(Duration.ofMinutes(56))))
        assertTrue(weather.isShownFor(tanta, now.plus(Duration.ofHours(2))))
        assertFalse(weather.isShownFor(tanta, now.plus(Duration.ofHours(3)).plusSeconds(1)))
    }

    @Test
    fun `a new location makes the weather stale at once`() {
        val weather = OpenMeteo.parse(answer, tanta, now)!!
        val cairo = Location(30.0444, 31.2357, ZoneId.of("Africa/Cairo"))

        assertTrue(weather.isDueFor(cairo, now))
        assertFalse(weather.isShownFor(cairo, now))
        // A few hundred metres away rounds to the same point.
        assertFalse(weather.isDueFor(tanta.copy(latitude = 30.79), now))
    }
}
