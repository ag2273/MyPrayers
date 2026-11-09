package app.mawaqeet.core

import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.Madhab
import com.batoulapps.adhan2.PrayerTimes
import com.batoulapps.adhan2.data.DateComponents
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import com.batoulapps.adhan2.CalculationMethod as AdhanMethod

/** One calendar day's raw Prayer Times and Sunrise at a location: the only place adhan-kotlin is used. */
internal class CalculatedDay(
    val prayerTimes: List<PrayerTime>,
    val sunrise: ZonedDateTime,
)

internal fun calculateDay(settings: PrayerSettings, date: LocalDate): CalculatedDay {
    val location = settings.location
    val params = settings.method.toAdhan().parameters.copy(madhab = settings.asrConvention.toAdhan())
    val times = PrayerTimes(
        Coordinates(location.latitude, location.longitude),
        DateComponents(date.year, date.monthValue, date.dayOfMonth),
        params,
    )
    fun local(instant: Instant): ZonedDateTime = instant.toJavaInstant().atZone(location.zone)
    return CalculatedDay(
        prayerTimes = listOf(
            PrayerTime(Prayer.FAJR, local(times.fajr)),
            PrayerTime(Prayer.DHUHR, local(times.dhuhr)),
            PrayerTime(Prayer.ASR, local(times.asr)),
            PrayerTime(Prayer.MAGHRIB, local(times.maghrib)),
            PrayerTime(Prayer.ISHA, local(times.isha)),
        ),
        sunrise = local(times.sunrise),
    )
}

private fun CalculationMethod.toAdhan(): AdhanMethod = when (this) {
    CalculationMethod.UMM_AL_QURA -> AdhanMethod.UMM_AL_QURA
    CalculationMethod.EGYPTIAN -> AdhanMethod.EGYPTIAN
    CalculationMethod.MUSLIM_WORLD_LEAGUE -> AdhanMethod.MUSLIM_WORLD_LEAGUE
    CalculationMethod.KARACHI -> AdhanMethod.KARACHI
    CalculationMethod.KUWAIT -> AdhanMethod.KUWAIT
    CalculationMethod.QATAR -> AdhanMethod.QATAR
    CalculationMethod.DUBAI -> AdhanMethod.DUBAI
    CalculationMethod.TURKEY -> AdhanMethod.TURKEY
    CalculationMethod.SINGAPORE -> AdhanMethod.SINGAPORE
    CalculationMethod.MOON_SIGHTING_COMMITTEE -> AdhanMethod.MOON_SIGHTING_COMMITTEE
    CalculationMethod.NORTH_AMERICA -> AdhanMethod.NORTH_AMERICA
}

private fun AsrConvention.toAdhan(): Madhab = when (this) {
    AsrConvention.STANDARD -> Madhab.SHAFI
    AsrConvention.HANAFI -> Madhab.HANAFI
}
