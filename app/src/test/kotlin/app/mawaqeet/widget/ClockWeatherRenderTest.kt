package app.mawaqeet.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.glance.appwidget.GlanceAppWidget
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mawaqeet.MawaqeetApplication
import app.mawaqeet.core.Weather
import app.mawaqeet.weather.WeatherStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * The weather on the Clock Widgets (design board option «أ»): at 4×2 the temperature, humidity and chance of rain
 * beside (Big Clock, Gold Clock) or under (Clock & Prayer) the clock; at 4×1 the temperature alone; nothing clipped.
 * Without a fetch in the last three hours the widgets are as before.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "ar")
class ClockWeatherRenderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val settings get() = (context as MawaqeetApplication).settings
    private val now = Instant.parse("2026-11-12T06:30:20Z")
    private val clock: Clock = Clock.fixed(now, ZoneId.systemDefault())

    private val widgets: List<Pair<String, GlanceAppWidget>> =
        listOf("big" to BigClockWidget(clock), "gold" to GoldClockWidget(clock), "split" to ClockPrayerWidget(clock))

    private fun storeWeather(fetchedAt: Instant = now.minus(Duration.ofMinutes(20))) = runBlocking {
        val location = settings.settings.first().location
        WeatherStore(context).write(
            Weather(28.6, 38, 5, fetchedAt, app.mawaqeet.core.OpenMeteo.rounded(location.latitude), app.mawaqeet.core.OpenMeteo.rounded(location.longitude)),
        )
    }

    @After
    fun clear() {
        context.getSharedPreferences("weather", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun texts(view: View) = widgetTexts(view).map { it.text.toString() }

    /** Kept for a look while developing: set MAWAQEET_SHOTS to a folder. */
    private fun shot(view: View, name: String) {
        val dir = System.getenv("MAWAQEET_SHOTS") ?: return
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun `at 4x2 every Clock shows the temperature, the humidity and the chance of rain, nothing clipped`() {
        storeWeather()
        for ((name, widget) in widgets) {
            val view = renderWidget(context, widget, 300, 160)
            shot(view, "$name-4x2")
            val shown = texts(view)
            assertTrue("$name: $shown", "٢٩°" in shown && "٣٨٪" in shown && "٥٪" in shown)
            assertNothingClipped(view, isHijriDate)
        }
    }

    @Test
    fun `at 4x1 every Clock shows the temperature alone`() {
        storeWeather()
        for ((name, widget) in widgets) {
            val view = renderWidget(context, widget, 300, 80)
            shot(view, "$name-4x1")
            val shown = texts(view)
            assertTrue("$name: $shown", "٢٩°" in shown)
            assertFalse("$name: $shown", "٣٨٪" in shown || "٥٪" in shown)
            assertNothingClipped(view, isHijriDate)
        }
    }

    @Test
    fun `without a fetch in the last three hours, no weather shows`() {
        storeWeather(fetchedAt = now.minus(Duration.ofHours(4)))
        for ((name, widget) in widgets) {
            val shown = texts(renderWidget(context, widget, 300, 160))
            assertFalse("$name: $shown", shown.any { it.endsWith("°") || it.endsWith("٪") })
        }
    }
}
