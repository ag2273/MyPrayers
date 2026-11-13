package app.mawaqeet

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mawaqeet.core.DigitStyle
import app.mawaqeet.core.Location
import app.mawaqeet.qibla.HeadingReading
import app.mawaqeet.qibla.HeadingSource
import app.mawaqeet.ui.ProvideDisplayFormatter
import app.mawaqeet.ui.qibla.QiblaScreen
import app.mawaqeet.ui.theme.MawaqeetTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.ZoneId

private val CAIRO = Location(30.0444, 31.2357, ZoneId.of("Africa/Cairo"))

/** A compass the test turns by hand. */
private class FakeHeadings(override val hasSensor: Boolean = true) : HeadingSource {
    val reading = MutableStateFlow<HeadingReading?>(null)

    override fun headings(latitude: Double, longitude: Double): Flow<HeadingReading> = reading.filterNotNull()

    fun face(degrees: Double, accurate: Boolean = true) {
        reading.value = HeadingReading(degrees, accurate)
    }
}

/**
 * The Qibla screen (#88) with a fake heading source. From Cairo the Qibla is at about 136° from true north.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class QiblaScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val headings = FakeHeadings()

    private var vibrations = 0

    private fun show(
        location: Location? = CAIRO,
        source: HeadingSource = headings,
        onSetLocation: () -> Unit = {},
        onMosques: () -> Unit = {},
    ) {
        compose.setContent {
            MawaqeetTheme {
                ProvideDisplayFormatter(DigitStyle.AUTOMATIC) {
                    QiblaScreen(location, source, onSetLocation = onSetLocation, onMosques = onMosques, onBack = {}, onFacingQibla = { vibrations++ })
                }
            }
        }
    }

    @Test
    @Config(qualifiers = "ar")
    fun `facing 120 degrees in Cairo the hint says to turn 16 degrees right`() {
        show()
        headings.face(120.0)

        compose.onNodeWithText("لفّ ١٦° يمينًا").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "ar")
    fun `facing past the Qibla the hint says to turn left`() {
        show()
        headings.face(150.0)

        compose.onNodeWithText("لفّ ١٤° يسارًا").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "ar")
    fun `within 3 degrees it says the phone faces the Qibla and vibrates once on entering`() {
        show()
        headings.face(120.0)
        compose.waitForIdle()
        assertEquals("not yet", 0, vibrations)

        headings.face(135.0)
        compose.onNodeWithText("أنت في اتجاه القبلة").assertIsDisplayed()
        headings.face(137.5)
        compose.waitForIdle()
        headings.face(134.0)
        compose.waitForIdle()
        assertEquals("one vibration while it stays on target", 1, vibrations)

        headings.face(100.0)
        compose.waitForIdle()
        headings.face(136.0)
        compose.waitForIdle()
        assertEquals("again after turning away and back", 2, vibrations)
    }

    @Test
    @Config(qualifiers = "ar")
    fun `it shows the bearing from true north and the distance to Makkah`() {
        show()
        headings.face(0.0)

        compose.onNodeWithText("القبلة ١٣٦° من الشمال الحقيقي").assertIsDisplayed()
        compose.onNodeWithText("١٢٨٧ كم إلى مكة المكرمة").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "ar")
    fun `a low-accuracy reading asks for a figure-8 calibration until it is accurate again`() {
        show()
        headings.face(120.0, accurate = false)
        compose.onNodeWithText("البوصلة تحتاج إلى معايرة").assertIsDisplayed()

        headings.face(120.0, accurate = true)
        compose.waitForIdle()
        compose.onAllNodesWithText("البوصلة تحتاج إلى معايرة").assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = "ar")
    fun `a phone without a heading sensor shows the bearing and its direction name only`() {
        show(source = FakeHeadings(hasSensor = false))

        compose.onNodeWithText("لا توجد بوصلة في هذا الهاتف").assertIsDisplayed()
        compose.onNodeWithText("نحو الجنوب الشرقي", substring = true).assertIsDisplayed()
        compose.onNodeWithText("القبلة ١٣٦° من الشمال الحقيقي").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("بوصلة القبلة").assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = "ar")
    fun `with no location set it offers to set one`() {
        var asked = false
        show(location = null, onSetLocation = { asked = true })

        compose.onNodeWithText("لم يُحدَّد موقعك بعد").assertIsDisplayed()
        compose.onNodeWithText("تحديد الموقع").performClick()
        assertTrue(asked)
    }

    @Test
    @Config(qualifiers = "ar")
    fun `nearby mosques under the compass says it opens in the maps app and opens it`() {
        var opened = 0
        show(onMosques = { opened++ })
        compose.onNodeWithText("تُفتح في تطبيق الخرائط عندك").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("المساجد القريبة").performClick()
        assertEquals(1, opened)
    }

    @Test
    @Config(qualifiers = "ar")
    fun `nearby mosques shows even with no location set, since the maps app finds its own`() {
        var opened = 0
        show(location = null, onMosques = { opened++ })
        compose.onNodeWithText("المساجد القريبة").performClick()
        assertEquals(1, opened)
    }

    @Test
    @Config(qualifiers = "en")
    fun `in English the hint, bearing and distance read naturally`() {
        show()
        headings.face(120.0)

        compose.onNodeWithText("Turn 16° right").assertIsDisplayed()
        compose.onNodeWithText("Qibla 136° from true north").assertIsDisplayed()
        compose.onNodeWithText("1287 km to Makkah").assertIsDisplayed()
        headings.face(136.0)
        compose.onNodeWithText("You are facing the Qibla").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "ar")
    fun `in Arabic the dial is not mirrored, east is right of west while facing north`() {
        show()
        headings.face(0.0)
        compose.waitForIdle()

        assertTrue("east right of west", center("ق").x > center("غ").x)
        assertTrue("north above south", center("ش").y < center("ج").y)
    }

    @Test
    @Config(qualifiers = "ar")
    fun `the dial turns with the phone, facing east puts east at the top`() {
        show()
        headings.face(90.0)
        compose.waitForIdle()

        assertTrue("east at the top", center("ق").y < center("ش").y && center("ق").y < center("ج").y)
        assertTrue("north on the left", center("ش").x < center("ج").x)
    }

    @Test
    @Config(qualifiers = "ar", fontScale = 2f)
    fun `at a large font nothing is cut off`() {
        show()
        headings.face(120.0, accurate = false)
        compose.waitForIdle()
        compose.assertAccessible("Qibla", compose.density.density)
    }

    private fun center(label: String) =
        compose.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
}
