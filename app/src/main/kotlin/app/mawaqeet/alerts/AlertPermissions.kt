package app.mawaqeet.alerts

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/** Which of the two permissions Adhan Alerts need are missing. Both must be granted to leave the Grant screen. */
data class AlertPermissions(val notifications: Boolean, val exactAlarms: Boolean) {
    val allGranted: Boolean get() = notifications && exactAlarms

    companion object {
        /**
         * Notifications: the runtime permission on 13+, the app's notification switch on all versions. Exact alarms:
         * granted on 8–11; pre-granted on 12–13 (so the step is skipped unless the user revoked it), off by default on 14+.
         */
        fun of(context: Context) = AlertPermissions(
            notifications = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            exactAlarms = AlertScheduler.canScheduleExact(context.getSystemService(AlarmManager::class.java)),
        )

        /** The app's notification settings page, for when the system dialog can no longer be shown. */
        fun notificationSettingsIntent(context: Context): Intent =
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

        /** The system "Alarms & reminders" page for this app (Android 12+). */
        fun exactAlarmSettingsIntent(context: Context): Intent =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            }
    }
}
