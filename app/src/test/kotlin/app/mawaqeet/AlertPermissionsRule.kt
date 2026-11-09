package app.mawaqeet

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource
import org.robolectric.shadows.ShadowAlarmManager

/**
 * Sets whether the exact-alarm permission is granted before the activity starts (Robolectric denies it by default,
 * which would open the app to the Grant screen), and whether onboarding (#29) is already done, so the app opens to
 * Home. Use with a lower `order` than the compose rule.
 */
class AlertPermissionsRule(private val exactAlarms: Boolean = true, private val onboarded: Boolean = true) : ExternalResource() {
    override fun before() {
        ShadowAlarmManager.setCanScheduleExactAlarms(exactAlarms)
        val app = ApplicationProvider.getApplicationContext<MawaqeetApplication>()
        runBlocking { app.settings.setOnboardingDone(onboarded) }
    }
}
