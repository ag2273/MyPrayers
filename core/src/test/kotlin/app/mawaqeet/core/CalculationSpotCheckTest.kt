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

    @Test
    fun `London at 51 degrees north under the Moonsighting Committee matches its published timetable`() {
        // A high-latitude city: from about May to August the sun never gets 18° below the horizon here, so Fajr and
        // Isha come from the committee's seasonal high-latitude rule. Source: http://www.moonsighting.com/pray.php
        // (London, 2016, Hanafi Asr), as transcribed in the "source" field of batoulapps/adhan-testdata
        // Times/London-MoonsightingCommittee.json. Published 12-hour times converted to 24-hour; the transcription's
        // "+1"/"-1" marks (adhan's own ±1 minute) are dropped, as the tolerance covers them.
        val london = Location(51.507194, -0.116711, ZoneId.of("Europe/London"))
        val published = """
            2016-01-01 06:25 08:06 12:09 14:15 16:05 17:38
            2016-02-01 06:02 07:40 12:19 15:00 16:52 18:18
            2016-03-01 05:10 06:45 12:18 15:48 17:44 19:03
            2016-04-01 04:59 06:35 13:09 17:31 19:37 20:49
            2016-05-01 03:47 05:32 13:02 18:05 20:27 21:32
            2016-06-01 02:55 04:49 13:03 18:31 21:12 22:23
            2016-07-01 02:51 04:48 13:09 18:41 21:24 22:38
            2016-08-01 03:38 05:25 13:12 18:23 20:51 21:56
            2016-09-01 04:35 06:14 13:05 17:40 19:49 20:58
            2016-10-01 05:28 07:02 12:55 16:43 18:40 19:57
            2016-11-01 05:17 06:55 11:49 14:44 16:36 17:59
            2016-12-01 06:04 07:44 11:55 14:08 15:58 17:29
        """
        assertMatches(PrayerSettings(london, CalculationMethod.MOON_SIGHTING_COMMITTEE, AsrConvention.HANAFI), published)
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
