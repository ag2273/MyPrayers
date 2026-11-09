package app.mawaqeet.widget

import androidx.core.os.LocaleListCompat
import app.mawaqeet.ui.appLocale
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/**
 * On Android 8–12 a fresh widget or receiver process has no per-app language until an activity runs, so widgets fall
 * back to the language stored in settings.
 */
class AppLocaleTest {
    private val french = Locale.FRENCH

    @Test
    fun `the per-app language wins when this process knows it`() {
        assertEquals("en", appLocale(LocaleListCompat.forLanguageTags("en"), storedTag = "ar", system = french).language)
    }

    @Test
    fun `in a fresh process with no per-app language the stored language is used`() {
        assertEquals("ar", appLocale(LocaleListCompat.getEmptyLocaleList(), storedTag = "ar", system = french).language)
    }

    @Test
    fun `with neither, the system language is used`() {
        assertEquals("fr", appLocale(LocaleListCompat.getEmptyLocaleList(), storedTag = "", system = french).language)
    }
}
