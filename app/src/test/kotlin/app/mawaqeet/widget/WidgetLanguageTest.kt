package app.mawaqeet.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mawaqeet.R
import app.mawaqeet.ui.localizedContext
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import app.mawaqeet.core.DigitStyle
import app.mawaqeet.core.DisplayFormatter
import java.time.Duration
import java.util.Locale

/** A widget process whose own configuration is in another language still renders the chosen one (Android 8–12). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en")
class WidgetLanguageTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `with Arabic chosen in an English process the widget's words are Arabic`() {
        val arabic = localizedContext(context, Locale.forLanguageTag("ar"))

        assertEquals("الصلاة القادمة", arabic.getString(R.string.widget_next_prayer))
        assertEquals("بعد %s دقيقة", arabic.getString(R.string.widget_countdown_minutes))
        assertEquals("الجمعة", arabic.getString(R.string.prayer_jumuah))
    }

    @Test
    fun `with English chosen the countdown reads in`() {
        assertEquals("in %s min", localizedContext(context, Locale.ENGLISH).getString(R.string.widget_countdown_minutes))
    }

    @Test
    fun `the countdown names its unit, hours from an hour on and minutes under it`() {
        val arabic = localizedContext(context, Locale.forLanguageTag("ar"))
        val text = WidgetText(DisplayFormatter(Locale.forLanguageTag("ar"), DigitStyle.ARABIC_INDIC), arabic, rtl = true)
        fun format(until: Duration) = NextPrayerView.of(NextPrayerContent(R.string.prayer_isha, "١٨:٢٢", until), text).countdownFormat

        assertEquals("بعد %s دقيقة", format(Duration.ofMinutes(21).plusSeconds(18)))
        assertEquals("بعد %s ساعة", format(Duration.ofHours(1)))
        assertEquals("بعد %s ساعة", format(Duration.ofHours(3)))
    }

    @Test
    fun `in Ramadan Mode the label names the day of Ramadan and the place, and the iftar line runs from Imsak`() {
        val arabic = localizedContext(context, Locale.forLanguageTag("ar"))
        val text = WidgetText(DisplayFormatter(Locale.forLanguageTag("ar"), DigitStyle.ARABIC_INDIC), arabic, rtl = true, place = "مكة")
        val iftar = NextPrayerContent(
            R.string.widget_iftar, "١٧:٤١", Duration.ofHours(4),
            previousNameRes = R.string.ramadan_imsak, previousTime = "٠٤:٥٩", fastingDayNumber = 15,
        )

        val view = NextPrayerView.of(iftar, text)

        assertEquals("رمضان · اليوم ١٥ · مكة", view.label)
        assertEquals("رمضان", view.shortLabel)
        assertEquals("الإفطار", view.name)
        assertEquals("الإمساك ٠٤:٥٩", view.from)
        assertEquals("الإفطار ١٧:٤١", view.to)
    }

    @Test
    fun `in English the suhoor widget reads Suhoor ends`() {
        val english = localizedContext(context, Locale.ENGLISH)
        val text = WidgetText(DisplayFormatter(Locale.ENGLISH, DigitStyle.WESTERN), english, rtl = false)
        val suhoor = NextPrayerContent(R.string.widget_suhoor_ends, "04:59", Duration.ofHours(8), fastingDayNumber = 15)

        val view = NextPrayerView.of(suhoor, text)

        assertEquals("Ramadan · Day 15", view.label)
        assertEquals("Suhoor ends", view.name)
    }
}
