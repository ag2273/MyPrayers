package app.mawaqeet

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Home shows the Gregorian date, then the Hijri date written with our own month names. Formats: core tests. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class HomeDateLineTest {
    @get:Rule(order = 0)
    val permissions = AlertPermissionsRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    @Config(qualifiers = "ar")
    fun `Arabic Home shows the Hijri date with its Arabic era`() {
        compose.onNodeWithText(" هـ", substring = true).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en")
    fun `English Home shows the Hijri date with its English era`() {
        compose.onNodeWithText(" AH", substring = true).assertIsDisplayed()
    }
}
