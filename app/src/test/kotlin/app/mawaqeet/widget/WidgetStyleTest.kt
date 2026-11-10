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
    fun `by default widgets are solid, in the design's own colours`() {
        val style = WidgetStyle.of(WidgetAppearance(), android12)

        assertEquals(255, style.backgroundAlpha)
        assertEquals(R.color.widget_background, style.background)
        assertEquals(R.color.widget_text, style.text)
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

    @Test
    fun `Next Prayer's deep surface keeps the design's teal and gold, even with wallpaper colours chosen`() {
        val style = WidgetStyle.of(WidgetAppearance(colors = WidgetColors.WALLPAPER), android12, deep = true)

        assertEquals(R.color.widget_deep_background, style.background)
        assertEquals(R.color.widget_deep_gold, style.accent)
    }

    @Test
    fun `the Next Prayer look puts every widget on the deep surface, with the star, and off leaves them as they were`() {
        val on = WidgetStyle.of(WidgetAppearance(nextPrayerLook = true), android12)
        assertEquals(R.color.widget_deep_background, on.background)
        assertEquals(R.color.widget_deep_gold, on.accent)
        assertEquals(R.color.widget_deep_fill, on.cellFill)
        assertTrue(on.deep && on.star)

        // The Next Prayer widgets draw their own star, so they don't get a second one.
        val nextPrayer = WidgetStyle.of(WidgetAppearance(nextPrayerLook = true), android12, deep = true)
        assertEquals(WidgetStyle.of(WidgetAppearance(), android12, deep = true), nextPrayer.copy(star = false))
        assertFalse(nextPrayer.star)

        val off = WidgetStyle.of(WidgetAppearance(), android12)
        assertEquals(R.color.widget_background, off.background)
        assertFalse(off.deep || off.star)
    }
}
