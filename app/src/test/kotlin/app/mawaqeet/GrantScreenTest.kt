package app.mawaqeet

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "ar")
class GrantScreenTest {
    @get:Rule(order = 0)
    val permissions = AlertPermissionsRule(exactAlarms = false)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun `without the exact-alarm permission the app opens to the Grant screen, and granting it returns to Home`() {
        compose.onNodeWithText("التنبيهات تحتاج إلى إذنك").assertIsDisplayed()
        compose.onNodeWithText("المنبّهات الدقيقة").assertIsDisplayed()
        compose.onNodeWithText("منح الإذن").assertIsDisplayed()

        // The user grants it in system settings and comes back to the app.
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()

        compose.onNodeWithText("الإعدادات").assertIsDisplayed()
    }
}
