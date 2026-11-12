package app.mawaqeet.weather

import android.content.Context
import android.content.SharedPreferences
import app.mawaqeet.MawaqeetApplication
import app.mawaqeet.core.Location
import app.mawaqeet.core.OpenMeteo
import app.mawaqeet.core.Weather
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/**
 * The last weather fetched for the Clock Widgets, kept in the app's own storage: nothing but the three values, when,
 * and the rounded point. Read by the widgets on every render, written by [WeatherUpdater].
 */
internal class WeatherStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(): Weather? {
        if (!prefs.contains(KEY_AT)) return null
        return Weather(
            temperatureC = prefs.getFloat(KEY_TEMPERATURE, 0f).toDouble(),
            humidityPercent = prefs.getInt(KEY_HUMIDITY, 0),
            rainChancePercent = prefs.getInt(KEY_RAIN, 0),
            fetchedAt = Instant.ofEpochMilli(prefs.getLong(KEY_AT, 0)),
            latitude = prefs.getFloat(KEY_LATITUDE, 0f).toDouble().let(OpenMeteo::rounded),
            longitude = prefs.getFloat(KEY_LONGITUDE, 0f).toDouble().let(OpenMeteo::rounded),
        )
    }

    /** The weather to show for [location] at [now], or null (none yet, too old, or for another place). */
    fun shownFor(location: Location, now: Instant = Instant.now()): Weather? = read()?.takeIf { it.isShownFor(location, now) }

    fun write(weather: Weather) {
        prefs.edit()
            .putFloat(KEY_TEMPERATURE, weather.temperatureC.toFloat())
            .putInt(KEY_HUMIDITY, weather.humidityPercent)
            .putInt(KEY_RAIN, weather.rainChancePercent)
            .putLong(KEY_AT, weather.fetchedAt.toEpochMilli())
            .putFloat(KEY_LATITUDE, weather.latitude.toFloat())
            .putFloat(KEY_LONGITUDE, weather.longitude.toFloat())
            .commit()
    }

    private companion object {
        const val PREFS = "weather"
        const val KEY_TEMPERATURE = "temperature_c"
        const val KEY_HUMIDITY = "humidity"
        const val KEY_RAIN = "rain_chance"
        const val KEY_AT = "fetched_at"
        const val KEY_LATITUDE = "latitude"
        const val KEY_LONGITUDE = "longitude"
    }
}

/**
 * Fetches the weather when it is due ([Weather.isDueFor]: about hourly, or the location moved). Called from the Clock
 * Widgets' minute re-render, so it only runs while a Clock widget is on the home screen and the screen is on. Without a
 * connection nothing changes, and after [Weather.SHOW_FOR] the widgets drop the weather.
 */
internal object WeatherUpdater {
    /** The network fetch; tests replace it. Returns the answer's body, or null on any failure. */
    @Volatile
    var fetch: (String) -> String? = ::get

    /** True when a new weather was stored. */
    suspend fun updateIfDue(context: Context, now: Instant = Instant.now()): Boolean = withContext(Dispatchers.IO) {
        val app = context.applicationContext as? MawaqeetApplication ?: return@withContext false
        val location = app.settings.settings.first().location
        val store = WeatherStore(app)
        if (store.read()?.isDueFor(location, now) == false) return@withContext false
        val weather = fetch(OpenMeteo.url(location))?.let { OpenMeteo.parse(it, location, now) } ?: return@withContext false
        store.write(weather)
        true
    }

    private fun get(url: String): String? = runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private const val TIMEOUT_MS = 8_000
}
