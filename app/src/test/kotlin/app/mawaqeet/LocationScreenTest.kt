package app.mawaqeet

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mawaqeet.core.AsrConvention
import app.mawaqeet.core.CalculationMethod
import app.mawaqeet.core.LocationSource
import app.mawaqeet.core.PrayerSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class LocationScreenTest {
    @get:Rule(order = 0)
    val permissions = AlertPermissionsRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private val store get() = (compose.activity.application as MawaqeetApplication).settings
    private fun stored() = runBlocking { store.settings.first() }
    private fun saved() = runBlocking { store.savedLocation.first() }

    /** The app's DataStore outlives a test in this JVM, so put back what these tests change. */
    @After
    fun restoreDefaults() = runBlocking {
        store.clearLocation()
        store.setCalculationMethod(PrayerSettings.Default.method)
        store.setAsrConvention(PrayerSettings.Default.asrConvention)
    }

    private fun openLocation() {
        compose.onNodeWithText("الإعدادات").performClick()
        compose.onNodeWithText("الموقع").performScrollTo().performClick()
        // The city list loads off the main thread.
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("جارٍ تحميل قائمة المدن…").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    @Config(qualifiers = "ar")
    fun `searching an Arabic city name and accepting the suggestion stores the city and Egypt's defaults`() {
        openLocation()
        compose.onNode(hasSetTextAction() and hasText("ابحث عن مدينتك")).performTextInput("القاهرة")
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("القاهرة").fetchSemanticsNodes().size > 1
        }
        compose.onAllNodesWithText("القاهرة")[1].performClick()

        compose.onNodeWithText("نقترح الهيئة المصرية العامة للمساحة ووقت العصر الجمهور لأنك في مصر")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("اعتماد الاقتراح").performClick()

        compose.waitUntil(timeoutMillis = 5_000) { stored().method == CalculationMethod.EGYPTIAN }
        assertEquals(AsrConvention.STANDARD, stored().asrConvention)
        assertEquals(LocationSource.MANUAL, saved()?.source)
        assertEquals("Cairo", saved()?.city?.name)
        assertEquals(ZoneId.of("Africa/Cairo"), stored().location.zone)
    }

    @Test
    @Config(qualifiers = "en")
    fun `typed coordinates take the nearest city's zone and suggest Hanafi Asr in Pakistan`() {
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Location").performScrollTo().performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("Loading the city list…").fetchSemanticsNodes().isEmpty()
        }
        // A point a few kilometres from central Lahore.
        compose.onNode(hasSetTextAction() and hasText("Latitude (−90 to 90)")).performTextInput("31.52")
        compose.onNode(hasSetTextAction() and hasText("Longitude (−180 to 180)")).performTextInput("74.36")
        compose.onNodeWithText("Use these coordinates").performScrollTo().performClick()

        compose.waitUntil(timeoutMillis = 5_000) { saved() != null }
        assertEquals(31.52, stored().location.latitude, 0.0)
        assertEquals(ZoneId.of("Asia/Karachi"), stored().location.zone)
        assertEquals(true, saved()?.city?.isNear)
        compose.onNodeWithText("We suggest University of Islamic Sciences, Karachi with Hanafi Asr because you are in Pakistan")
            .performScrollTo().assertIsDisplayed()
    }
}
