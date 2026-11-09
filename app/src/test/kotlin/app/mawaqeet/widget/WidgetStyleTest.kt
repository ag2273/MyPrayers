package app.mawaqeet.widget

import app.mawaqeet.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetStyleTest {
    private val android12 = 31
    private val android11 = 30

    @Test
    fun `by default widgets are solid, with wallpaper colours on Android 12 and later`() {
        val style = WidgetStyle.of(WidgetAppearance(), android12)

        assertEquals(255, style.backgroundAlpha)
        assertEquals(R.color.widget_dyn_background, style.background)
        assertEquals(R.color.widget_dyn_text, style.text)
        assertFalse(style.shadowedText)
        assertTrue(style.responsive)
    }

    @Test
    fun `before Android 12 widgets keep the app's colours and one layout, whatever the choice`() {
        val style = WidgetStyle.of(WidgetAppearance(colors = WidgetColors.WALLPAPER), android11)

        assertEquals(R.color.widget_background, style.background)
        assertEquals(R.color.widget_accent, style.accent)
        assertFalse(style.responsive)
    }

    @Test
    fun `choosing app colours keeps them on Android 12 too`() {
        val style = WidgetStyle.of(WidgetAppearance(colors = WidgetColors.APP), android12)

        assertEquals(R.color.widget_background, style.background)
        assertEquals(R.color.widget_text, style.text)
    }

    @Test
    fun `text turns light with a shadow from 60 percent transparency on`() {
        for (percent in listOf(0, 10, 50)) {
            val style = WidgetStyle.of(WidgetAppearance(percent), android11)
            assertFalse("$percent%", style.shadowedText)
            assertEquals(R.color.widget_text, style.text)
        }
        for (percent in listOf(60, 90, 100)) {
            val style = WidgetStyle.of(WidgetAppearance(percent), android11)
            assertTrue("$percent%", style.shadowedText)
            assertEquals(R.color.widget_over_text, style.text)
            assertEquals(R.color.widget_over_muted, style.muted)
        }
    }

    @Test
    fun `the background fades with the transparency`() {
        assertEquals(128, WidgetStyle.of(WidgetAppearance(50), android12).backgroundAlpha)
        assertEquals(0, WidgetStyle.of(WidgetAppearance(100), android12).backgroundAlpha)
    }

    @Test
    fun `transparency comes in steps of 10 between 0 and 100`() {
        assertThrows(IllegalArgumentException::class.java) { WidgetAppearance(35) }
        assertThrows(IllegalArgumentException::class.java) { WidgetAppearance(110) }
    }
}
