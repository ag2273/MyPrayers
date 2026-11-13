package app.mawaqeet

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mawaqeet.core.DigitStyle
import app.mawaqeet.core.HijriCalendar
import app.mawaqeet.ui.ProvideDisplayFormatter
import app.mawaqeet.ui.calendar.CalendarScreen
import app.mawaqeet.ui.theme.MawaqeetTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * The «الشهر» grid (#94) on small and big phones and at large text: no text is cut off, the seven columns fit the
 * width, and on a big phone the grid uses the width rather than a small phone's. Arabic, on 13 November 2026, and
 * Dhu al-Hijjah 1448 (six weeks, Occasions on most cells).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class CalendarMonthFitTest {
    @get:Rule
    val compose = createComposeRule()

    private var density = 1f

    private fun showMonth() {
        compose.setContent {
            density = LocalDensity.current.density
            MawaqeetTheme {
                ProvideDisplayFormatter(DigitStyle.AUTOMATIC) {
                    CalendarScreen(HijriCalendar(), today = LocalDate.of(2026, 11, 13), onBack = {})
                }
            }
        }
        compose.onNodeWithText("الشهر").performClick()
        compose.waitForIdle()
    }

    private fun bounds(description: String) = compose.onNodeWithContentDescription(description).getUnclippedBoundsInRoot()

    /** Nothing cut off (a real-height window scrolls, so only text overflowing its own box counts), and the columns fit. */
    private fun assertFits(tallWindow: Boolean) {
        val width = compose.onRoot().getUnclippedBoundsInRoot().right
        listOf(false, true).forEach { dhuAlHijjah ->
            if (dhuAlHijjah) repeat(6) { compose.onNodeWithContentDescription("الشهر التالي").performClick() }
            compose.waitForIdle()
            assertEquals(emptyList<String>(), compose.accessibilityProblems(density, parentClips = tallWindow))
            val saturday = bounds("السبت")
            val friday = bounds("الجمعة")
            assertTrue("Saturday is inside the screen", saturday.right <= width)
            assertTrue("Friday is inside the screen", friday.left.value >= 0f)
            // The grid takes the width: seven columns across at least 80% of it.
            assertTrue("grid spans the width", (saturday.right - friday.left) / width >= 0.8f)
            compose.onAllNodesWithContentDescription("ذو الحجة ١٤٤٨ هـ،", substring = true).fetchSemanticsNodes().forEach {
                assertTrue(it.boundsInRoot.right / density <= width.value + 0.5f)
            }
        }
    }

    @Test
    @Config(qualifiers = "ar-w360dp-h640dp", fontScale = 1.3f)
    fun `a 360x640 phone at 130% text`() {
        showMonth()
        assertFits(tallWindow = false)
    }

    @Test
    @Config(qualifiers = "ar-w360dp-h640dp", fontScale = 1.5f)
    fun `a 360x640 phone at 150% text`() {
        showMonth()
        assertFits(tallWindow = false)
    }

    @Test
    @Config(qualifiers = "ar-w360dp-h6000dp", fontScale = 2.0f)
    fun `a 360dp phone at 200% text, the whole page`() {
        showMonth()
        assertFits(tallWindow = true)
    }

    @Test
    @Config(qualifiers = "ar-w412dp-h915dp", fontScale = 1.0f)
    fun `a big 412x915 phone`() {
        showMonth()
        assertFits(tallWindow = false)
    }

    @Test
    @Config(qualifiers = "ar-w412dp-h6000dp", fontScale = 1.5f)
    fun `a big phone at 150% text, the whole page`() {
        showMonth()
        assertFits(tallWindow = true)
    }

    @Test
    @Config(qualifiers = "en-w480dp-h6000dp", fontScale = 1.3f)
    fun `a very big phone in English at 130% text`() {
        compose.setContent {
            density = LocalDensity.current.density
            MawaqeetTheme {
                ProvideDisplayFormatter(DigitStyle.AUTOMATIC) {
                    CalendarScreen(HijriCalendar(), today = LocalDate.of(2026, 11, 13), onBack = {})
                }
            }
        }
        compose.onNodeWithText("Month").performClick()
        compose.waitForIdle()
        assertEquals(emptyList<String>(), compose.accessibilityProblems(density))
        val width = compose.onRoot().getUnclippedBoundsInRoot().right
        assertTrue((bounds("Friday").right - bounds("Saturday").left) / width >= 0.8f)
    }
}
