package app.mawaqeet

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mawaqeet.ui.theme.DarkColors
import app.mawaqeet.ui.theme.LightColors
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Text keeps WCAG AA contrast (4.5:1) on what it is drawn over, in light and dark (#33): the app's colour roles and
 * the widgets' default colours. (Light widget text over a see-through background relies on its shadow instead.)
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ContrastTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun ratio(a: Color, b: Color): Float {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05f) / (lo + 0.05f)
    }

    private fun assertReadable(name: String, text: Color, background: Color) {
        val ratio = ratio(text, background)
        assertTrue("$name: %.2f:1".format(ratio), ratio >= 4.5f)
    }

    private fun assertScheme(theme: String, scheme: ColorScheme) = with(scheme) {
        assertReadable("$theme text", onBackground, background)
        assertReadable("$theme secondary text", onSurfaceVariant, surface)
        assertReadable("$theme group titles and links", primary, background)
        assertReadable("$theme Next Prayer card", onPrimary, primary)
        assertReadable("$theme countdown on the card", tertiary, primary)
        assertReadable("$theme highlighted row", onPrimaryContainer, primaryContainer)
    }

    @Test
    fun `the app's text colours are readable in light and dark`() {
        assertScheme("light", LightColors)
        assertScheme("dark", DarkColors)
    }

    private fun color(id: Int) = Color(context.getColor(id))

    private fun assertWidgetColours(theme: String) {
        val background = color(R.color.widget_background)
        assertReadable("$theme widget text", color(R.color.widget_text), background)
        assertReadable("$theme widget muted text", color(R.color.widget_text_muted), background)
        assertReadable("$theme widget accent", color(R.color.widget_accent), background)
        assertReadable("$theme widget text on the accent", color(R.color.widget_on_accent), color(R.color.widget_accent))
        assertReadable("$theme widget next row", color(R.color.widget_text), color(R.color.widget_accent_soft))
        assertReadable("$theme widget accent on the next row", color(R.color.widget_accent), color(R.color.widget_accent_soft))
    }

    @Test
    @Config(qualifiers = "notnight")
    fun `the widgets' colours are readable in light`() = assertWidgetColours("light")

    @Test
    @Config(qualifiers = "night")
    fun `the widgets' colours are readable in dark`() = assertWidgetColours("dark")
}
