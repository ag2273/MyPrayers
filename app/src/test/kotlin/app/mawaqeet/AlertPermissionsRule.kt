package app.mawaqeet

import org.junit.rules.ExternalResource
import org.robolectric.shadows.ShadowAlarmManager

/**
 * Sets whether the exact-alarm permission is granted before the activity starts (Robolectric denies it by default,
 * which would open the app to the Grant screen). Use with a lower `order` than the compose rule.
 */
class AlertPermissionsRule(private val exactAlarms: Boolean = true) : ExternalResource() {
    override fun before() = ShadowAlarmManager.setCanScheduleExactAlarms(exactAlarms)
}
