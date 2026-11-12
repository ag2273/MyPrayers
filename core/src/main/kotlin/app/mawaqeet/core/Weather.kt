package app.mawaqeet.core

import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

/**
 * The weather the Clock Widgets show beside the clock: the temperature now, the relative humidity now, and the
 * chance of rain today (the day's highest hourly chance). Fetched from Open-Meteo for the stored location, rounded to
 * a tenth of a degree (about 10 km) so the request never carries the exact place.
 */
data class Weather(
    val temperatureC: Double,
    val humidityPercent: Int,
    val rainChancePercent: Int,
    val fetchedAt: Instant,
    /** The rounded point it was fetched for; a moved location makes it stale at once. */
    val latitude: Double,
    val longitude: Double,
) {
    /** Whole degrees, as the widget shows them. */
    val temperature: Int get() = temperatureC.roundToInt()

    /** Shown while fetched for [location]'s rounded point within [SHOW_FOR]; older, the widget drops the weather. */
    fun isShownFor(location: Location, now: Instant): Boolean =
        isFor(location) && Duration.between(fetchedAt, now) in Duration.ZERO..SHOW_FOR

    /** Time to fetch again: [REFRESH_AFTER] has passed, or the location moved. */
    fun isDueFor(location: Location, now: Instant): Boolean =
        !isFor(location) || Duration.between(fetchedAt, now) !in Duration.ZERO..REFRESH_AFTER

    private fun isFor(location: Location) =
        latitude == OpenMeteo.rounded(location.latitude) && longitude == OpenMeteo.rounded(location.longitude)

    companion object {
        /** About hourly, as the design board says. */
        val REFRESH_AFTER: Duration = Duration.ofMinutes(55)

        /** Past this without a successful fetch (no internet), the weather goes and the widget is as before. */
        val SHOW_FOR: Duration = Duration.ofHours(3)
    }
}

/** Open-Meteo's free forecast API (no key, no account): the request, and reading its answer. */
object OpenMeteo {
    /** A tenth of a degree, about 10 km: enough for the weather, never the exact place. */
    fun rounded(degrees: Double): Double = (degrees * 10).roundToInt() / 10.0

    fun url(location: Location): String =
        "https://api.open-meteo.com/v1/forecast?latitude=${rounded(location.latitude)}&longitude=${rounded(location.longitude)}" +
            "&current=temperature_2m,relative_humidity_2m&daily=precipitation_probability_max&forecast_days=1&timezone=auto"

    /** Reads an answer to [url] for [location]; null if any of the three values is missing. */
    fun parse(json: String, location: Location, fetchedAt: Instant): Weather? {
        val current = section(json, "current") ?: return null
        val daily = section(json, "daily") ?: return null
        val temperature = number(current, "temperature_2m") ?: return null
        val humidity = number(current, "relative_humidity_2m") ?: return null
        // A list with one value per day; null when the model has no chance for the day.
        val rain = Regex(""""precipitation_probability_max"\s*:\s*\[\s*(-?[0-9.]+|null)""").find(daily)?.groupValues?.get(1)
        return Weather(
            temperatureC = temperature,
            humidityPercent = humidity.roundToInt().coerceIn(0, 100),
            rainChancePercent = rain?.toDoubleOrNull()?.roundToInt()?.coerceIn(0, 100) ?: 0,
            fetchedAt = fetchedAt,
            latitude = rounded(location.latitude),
            longitude = rounded(location.longitude),
        )
    }

    /** The `{…}` object under [key] (no nested objects in these sections). */
    private fun section(json: String, key: String): String? =
        Regex(""""$key"\s*:\s*\{([^{}]*)\}""").find(json)?.groupValues?.get(1)

    private fun number(section: String, key: String): Double? =
        Regex(""""$key"\s*:\s*(-?[0-9.]+)""").find(section)?.groupValues?.get(1)?.toDoubleOrNull()
}
