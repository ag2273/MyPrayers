package app.mawaqeet

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Tools Row's «القبلة» on Today opens the Qibla screen (#88); back returns to Today. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class QiblaOpensTest {
    @get:Rule(order = 0)
    val permissions = AlertPermissionsRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    @Config(qualifiers = "ar")
    fun `the Qibla tool on Today opens the Qibla screen and back returns to Today`() {
        compose.onNode(hasText("القبلة") and hasClickAction()).performClick()

        // Robolectric's phone has no compass, and the tests' DataStore has no location: one of the two cards shows.
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("لم يُحدَّد موقعك بعد").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("لا توجد بوصلة في هذا الهاتف").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("رجوع").performClick()
        compose.onNodeWithText("حتى الأذان").assertIsDisplayed()
    }
}
