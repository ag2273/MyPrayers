package app.mawaqeet

import android.content.Intent
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo

/**
 * «المساجد» on Today hands off to the maps app (#89): a `geo:` search for mosques with no coordinates from the app,
 * so the maps app uses its own location.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class NearbyMosquesTest {
    @get:Rule(order = 0)
    val permissions = AlertPermissionsRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private fun startedActivity(): Intent =
        shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).nextStartedActivity

    @Test
    @Config(qualifiers = "ar")
    fun `the Mosques tool on Today opens the maps app on a search for mosques in Arabic`() {
        compose.onNode(hasText("المساجد") and hasClickAction()).performClick()

        val intent = startedActivity()
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("geo", intent.data?.scheme)
        assertEquals("0,0?q=مسجد", intent.data?.schemeSpecificPart)
    }

    @Test
    @Config(qualifiers = "ar")
    fun `nearby mosques on the Qibla screen opens the same maps search`() {
        compose.onNode(hasText("القبلة") and hasClickAction()).performClick()
        compose.onNodeWithText("المساجد القريبة").performScrollTo().performClick()

        assertEquals("geo:0,0?q=%D9%85%D8%B3%D8%AC%D8%AF", startedActivity().dataString)
    }

    @Test
    @Config(qualifiers = "en")
    fun `in English the maps search is for mosque`() {
        compose.onNode(hasText("Mosques") and hasClickAction()).performClick()

        assertEquals("geo:0,0?q=mosque", startedActivity().dataString)
    }

    @Test
    @Config(qualifiers = "ar")
    fun `with no maps app a short message shows instead of a crash`() {
        shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).checkActivities(true)

        compose.onNode(hasText("المساجد") and hasClickAction()).performClick()

        assertEquals("لا يوجد تطبيق خرائط على هذا الهاتف", ShadowToast.getTextOfLatestToast())
        compose.onNodeWithText("حتى الأذان").assertIsDisplayed()
    }
}
