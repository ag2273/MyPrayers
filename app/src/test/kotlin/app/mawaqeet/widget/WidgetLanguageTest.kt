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
        assertEquals("بعد %s", arabic.getString(R.string.widget_countdown_format))
        assertEquals("الجمعة", arabic.getString(R.string.prayer_jumuah))
    }

    @Test
    fun `with English chosen the countdown reads in`() {
        assertEquals("in %s", localizedContext(context, Locale.ENGLISH).getString(R.string.widget_countdown_format))
    }
}
