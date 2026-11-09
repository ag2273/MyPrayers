package app.mawaqeet.core

import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The core's Prayer Times against official published timetables, within [TOLERANCE].
 *
 * Each row is one published day: date, then Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha in 24-hour local time.
 */
class CalculationSpotCheckTest {

    @Test
    fun `Makkah under Umm al-Qura matches the official Umm al-Qura timetable`() {
        // Source: the Umm al-Qura calendar, http://www.ummulqura.org.sa/Index.aspx (Mecca, 2016), as transcribed
        // in the "source" field of batoulapps/adhan-testdata Times/Makkah-UmmAlQura.json (also shipped as
        // batoulapps/adhan-kotlin Shared/Times/Makkah-UmmAlQura.json). These are the published values, not
        // adhan's adjusted ones; the transcription's first row reads "1/5/15", a typo for 5 January 2016
        // (25/3/1437 AH). None of these days is in Ramadan.
        val makkah = Location(21.427009, 39.828685, ZoneId.of("Asia/Riyadh"))
        val published = """
            2016-01-05 05:39 06:59 12:26 15:32 17:53 19:23
            2016-02-04 05:39 06:57 12:35 15:49 18:13 19:43
            2016-03-05 05:22 06:37 12:33 15:55 18:27 19:57
            2016-04-03 04:55 06:11 12:24 15:50 18:37 20:07
            2016-05-02 04:27 05:48 12:18 15:39 18:47 20:17
            2016-06-04 04:11 05:37 12:20 15:37 19:01 20:31
            2016-07-08 04:18 05:44 12:26 15:43 19:07 20:37
            2016-08-02 04:32 05:54 12:27 15:46 19:00 20:30
            2016-09-04 04:48 06:04 12:20 15:45 18:34 20:04
            2016-10-06 04:58 06:13 12:09 15:32 18:04 19:34
            2016-11-05 05:09 06:26 12:05 15:19 17:43 19:13
            2016-12-04 05:24 06:44 12:12 15:17 17:38 19:08
        """
        assertMatches(PrayerSettings(makkah, CalculationMethod.UMM_AL_QURA, AsrConvention.STANDARD), published)
    }

    private fun assertMatches(settings: PrayerSettings, published: String) {
        val names = listOf("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha")
        published.trim().lines().map { it.trim().split(" ") }.forEach { row ->
            val date = LocalDate.parse(row[0])
            val expected = row.drop(1).map(LocalTime::parse)
            val noon = date.atTime(12, 0).atZone(settings.location.zone).toInstant()
            val day = PrayerCore(settings, Clock.fixed(noon, settings.location.zone), InMemoryPrayerLog())
                .prayerDay(date)
            val actual = listOf(day.prayerTimes[0].time, day.sunrise) + day.prayerTimes.drop(1).map { it.time }
            names.indices.forEach { i ->
                val off = Duration.between(date.atTime(expected[i]), actual[i].toLocalDateTime()).abs()
                assertTrue(
                    off <= TOLERANCE,
                    "${names[i]} on $date: published ${expected[i]}, core ${actual[i].toLocalTime()}",
                )
            }
        }
    }

    private companion object {
        /** The official timetables publish whole minutes; adhan's own fixture allows the same ±1 minute. */
        val TOLERANCE: Duration = Duration.ofMinutes(1)
    }
}
