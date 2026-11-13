package app.mawaqeet

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** «التقويم» on the Tools Row and the Hijri date on Today both open the Calendar screen (#93); back returns to Today. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class CalendarOpensTest {
    @get:Rule(order = 0)
    val permissions = AlertPermissionsRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private fun assertCalendarOpenThenBack() {
        compose.onNodeWithText("القادم").assertIsDisplayed()
        compose.onNodeWithContentDescription("رجوع").performClick()
        compose.onNodeWithText("الصلاة القادمة").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "ar")
    fun `the Calendar tool on Today opens the Calendar screen`() {
        compose.onNode(hasText("التقويم") and hasClickAction()).performClick()

        assertCalendarOpenThenBack()
    }

    @Test
    @Config(qualifiers = "ar")
    fun `the Hijri date on Today opens the Calendar screen`() {
        compose.onNode(hasText(" هـ", substring = true) and hasClickAction()).performClick()

        assertCalendarOpenThenBack()
    }
}
