package app.mawaqeet.core

import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.Madhab
import com.batoulapps.adhan2.PrayerAdjustments
import com.batoulapps.adhan2.PrayerTimes
import com.batoulapps.adhan2.CalculationParameters
import com.batoulapps.adhan2.data.DateComponents
import com.batoulapps.adhan2.model.Rounding
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.sign
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import com.batoulapps.adhan2.CalculationMethod as AdhanMethod
import com.batoulapps.adhan2.HighLatitudeRule as AdhanHighLatitudeRule

/** One calendar day's raw Prayer Times and Sunrise at a location: the only place adhan-kotlin is used. */
internal class CalculatedDay(
    val prayerTimes: List<PrayerTime>,
    val sunrise: ZonedDateTime,
    findHighLatitude: () -> HighLatitudeAdjustment?,
) {
    /** Non-null when a high-latitude rule, not the method's angle, set Fajr or Isha. Worked out only when asked. */
    val highLatitude: HighLatitudeAdjustment? by lazy(findHighLatitude)
}

/**
 * The Prayer Times of [date], with the core's rules around adhan-kotlin: the user's per-Prayer adjustments, the
 * high-latitude rule (and the polar fallback when adhan cannot calculate the day at all) and Umm al-Qura's Ramadan
 * Isha.
 */
internal fun calculateDay(settings: PrayerSettings, date: LocalDate): CalculatedDay {
    val location = settings.location
    val base = settings.method.toAdhan().parameters
    val params = base.copy(
        madhab = settings.asrConvention.toAdhan(),
        ishaInterval = if (settings.method == CalculationMethod.UMM_AL_QURA && isRamadan(date, settings)) {
            UMM_AL_QURA_RAMADAN_ISHA_MINUTES
        } else {
            base.ishaInterval
        },
        prayerAdjustments = PrayerAdjustments(
            fajr = settings.adjustmentOf(Prayer.FAJR),
            dhuhr = settings.adjustmentOf(Prayer.DHUHR),
            asr = settings.adjustmentOf(Prayer.ASR),
            maghrib = settings.adjustmentOf(Prayer.MAGHRIB),
            isha = settings.adjustmentOf(Prayer.ISHA),
        ),
    )
    // Near the poles the sun may not rise or set at all, and adhan-kotlin then throws: fall back to the nearest
    // latitude toward the equator where the day can be calculated.
    val latitudes = sequenceOf(location.latitude) + polarFallbackLatitudes(location.latitude)
    val (latitude, times) = latitudes.firstNotNullOf { latitude ->
        val coordinates = Coordinates(latitude, location.longitude)
        val rule = settings.highLatitudeRule.resolve(latitude)
        prayerTimesOrNull(coordinates, date, params.copy(highLatitudeRule = rule.toAdhan()))?.let { latitude to it }
    }
    val rule = settings.highLatitudeRule.resolve(latitude)
    val polar = latitude != location.latitude
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
    ) {
        val ruleUsed = polar ||
            ruleSetFajrOrIsha(Coordinates(latitude, location.longitude), date, params.copy(highLatitudeRule = rule.toAdhan()))
        if (ruleUsed) HighLatitudeAdjustment(rule, polar) else null
    }
}

/** Umm al-Qura's Isha is 90 minutes after Maghrib, and 120 minutes during Ramadan. */
private const val UMM_AL_QURA_RAMADAN_ISHA_MINUTES = 120

/** Ramadan follows the Hijri date the user sees (their moon-sighting offset included). */
private fun isRamadan(date: LocalDate, settings: PrayerSettings): Boolean =
    HijriDate.of(date, settings.hijriOffsetDays).month == 9

/** From [HighLatitudeAdjustment.POLAR_FALLBACK_LATITUDE] toward the equator, on [latitude]'s side of it. */
private fun polarFallbackLatitudes(latitude: Double): Sequence<Double> {
    val fallback = HighLatitudeAdjustment.POLAR_FALLBACK_LATITUDE
    if (abs(latitude) <= fallback) return emptySequence()
    val side = sign(latitude)
    // A few degrees lower too, in case some time (e.g. Hanafi Asr) still cannot be calculated at 65°.
    return generateSequence(fallback) { it - 1.0 }.take(11).map { it * side }
}

private fun prayerTimesOrNull(coordinates: Coordinates, date: LocalDate, params: CalculationParameters): PrayerTimes? =
    try {
        PrayerTimes(coordinates, date.toAdhan(), params)
    } catch (_: IllegalStateException) {
        null
    }

/**
 * Whether the high-latitude bound, not the angle, set Fajr or Isha: adhan-kotlin does not say, so this rebuilds the
 * bound from an unrounded, unadjusted calculation the way adhan-kotlin does and compares.
 */
private fun ruleSetFajrOrIsha(coordinates: Coordinates, date: LocalDate, params: CalculationParameters): Boolean {
    val probe = params.copy(
        prayerAdjustments = PrayerAdjustments(),
        methodAdjustments = PrayerAdjustments(),
        rounding = Rounding.NONE,
    )
    val today = prayerTimesOrNull(coordinates, date, probe) ?: return true
    val tomorrowSunrise = prayerTimesOrNull(coordinates, date.plusDays(1), probe)?.sunrise ?: return true
    // Unadjusted, Maghrib is sunset.
    val nightMillis = (tomorrowSunrise - today.maghrib).inWholeMilliseconds
    val portions = if (params.method == AdhanMethod.MOON_SIGHTING_COMMITTEE) {
        // The committee's own seasonal rule is its normal method; above 55° adhan-kotlin uses the seventh of the night.
        if (coordinates.latitude < 55.0) return false
        CalculationParameters.NightPortions(1.0 / 7.0, 1.0 / 7.0)
    } else {
        probe.nightPortions(coordinates)
    }
    fun bound(portion: Double) = (portion * nightMillis / 1000).toLong().seconds
    fun near(a: Instant, b: Instant) = (a - b).absoluteValue <= 1000.milliseconds
    val fajrBounded = near(today.fajr, today.sunrise - bound(portions.fajr))
    val ishaBounded = probe.ishaInterval == 0 && near(today.isha, today.maghrib + bound(portions.isha))
    return fajrBounded || ishaBounded
}

private fun LocalDate.toAdhan() = DateComponents(year, monthValue, dayOfMonth)

private fun HighLatitudeRule.toAdhan(): AdhanHighLatitudeRule = when (this) {
    // calculateDay always resolves AUTOMATIC first; adhan's own default ignores southern latitudes.
    HighLatitudeRule.AUTOMATIC, HighLatitudeRule.MIDDLE_OF_THE_NIGHT -> AdhanHighLatitudeRule.MIDDLE_OF_THE_NIGHT
    HighLatitudeRule.SEVENTH_OF_THE_NIGHT -> AdhanHighLatitudeRule.SEVENTH_OF_THE_NIGHT
    HighLatitudeRule.TWILIGHT_ANGLE -> AdhanHighLatitudeRule.TWILIGHT_ANGLE
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
