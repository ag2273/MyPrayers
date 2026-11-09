package app.mawaqeet.alerts

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.mawaqeet.MainActivity
import app.mawaqeet.core.AlertPlan
import app.mawaqeet.core.DeliveryMode
import app.mawaqeet.core.PlannedAlert
import java.time.Instant

/**
 * Turns the core's [AlertPlan] into alarms: one per planned alert (`setExactAndAllowWhileIdle`, or
 * `setAlarmClock` in alarm-clock mode) plus one display alarm ([AlertPlan.nextDisplayChange]: local midnight, or a Prayer
 * Time with no Adhan Alert). Applying a plan reconciles: alarms of the previously applied plan that the new one lacks
 * are cancelled, the rest are (re)set. The applied plan is stored in device-protected storage ([AlertPlanStore]) for
 * [rearmStoredPlan] after a locked boot; the stored copy keeps only midnight, so its display alarm is midnight.
 *
 * Callers normally go through [AlertRescheduler], which builds the plan and serialises calls.
 */
class AlertScheduler(context: Context) {
    private val context = context.applicationContext
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val store = AlertPlanStore(context)

    /** Schedules [plan] and stores it. Without the exact-alarm permission nothing is set (the Grant screen shows). */
    fun apply(plan: AlertPlan) {
        val previous = store.read()
        val keep = plan.alerts.map { it.requestCode }.toSet()
        previous?.alerts?.filter { it.requestCode !in keep }?.forEach { cancel(alertIntent(it, PendingIntent.FLAG_NO_CREATE)) }
        store.write(plan)
        arm(plan)
    }

    /** Re-arms the stored plan's alerts still after [now], with no recalculation (works before the first unlock). */
    fun rearmStoredPlan(now: Instant) {
        val stored = store.read() ?: return
        arm(stored.copy(alerts = stored.alerts.filter { it.instant.isAfter(now) }), now)
    }

    private fun arm(plan: AlertPlan, now: Instant? = null) {
        if (!canScheduleExact(alarmManager)) return
        for (alert in plan.alerts) {
            val operation = alertIntent(alert, PendingIntent.FLAG_UPDATE_CURRENT)!!
            val at = alert.instant.toEpochMilli()
            when (alert.deliveryMode) {
                DeliveryMode.EXACT -> alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
                DeliveryMode.ALARM_CLOCK -> alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(at, openAppIntent(context)),
                    operation,
                )
            }
        }
        // The display alarm: local midnight, or earlier at a Prayer Time no Adhan Alert announces (widgets move on).
        if (now == null || plan.nextDisplayChange.isAfter(now)) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                plan.nextDisplayChange.toEpochMilli(),
                midnightIntent(),
            )
        }
    }

    private fun cancel(operation: PendingIntent?) {
        operation ?: return
        alarmManager.cancel(operation)
        operation.cancel()
    }

    private fun alertIntent(alert: PlannedAlert, flag: Int): PendingIntent? = PendingIntent.getBroadcast(
        context,
        alert.requestCode,
        alarmIntent(alert),
        flag or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * The broadcast an alert's alarm delivers to [AlertReceiver]. Everything the receiver needs travels here,
     * since it may fire before the first unlock, when settings can't be read.
     */
    internal fun alarmIntent(alert: PlannedAlert): Intent = Intent(context, AlertReceiver::class.java)
        .setAction(AlertReceiver.ACTION_ALERT)
        .putExtra(AlertReceiver.EXTRA_PRAYER, alert.prayer.name)
        .putExtra(AlertReceiver.EXTRA_JUMUAH, alert.isJumuah)
        .putExtra(AlertReceiver.EXTRA_REQUEST_CODE, alert.requestCode)
        .putExtra(AlertReceiver.EXTRA_SOUND, encodeAlertSound(alert.sound))
        .putExtra(AlertReceiver.EXTRA_AT, alert.instant.toEpochMilli())
        .putExtra(AlertReceiver.EXTRA_KIND, alert.kind.name)
        .putExtra(AlertReceiver.EXTRA_PRAYER_TIME, alert.prayerTime.toEpochMilli())
        .putExtra(AlertReceiver.EXTRA_PLAY_ON_SILENT, alert.playOnSilent)
        .putExtra(AlertReceiver.EXTRA_VIBRATION, alert.vibration)
        .putExtra(AlertReceiver.EXTRA_PRAYER_DAY, alert.prayerDay.toString())

    private fun midnightIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        MIDNIGHT_REQUEST_CODE,
        Intent(context, AlertReceiver::class.java).setAction(AlertReceiver.ACTION_MIDNIGHT),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val MIDNIGHT_REQUEST_CODE = 0

        /**
         * Stable and distinct per (Prayer Day, Prayer, kind), so re-planning replaces the same alarm and a
         * stale one can be found to cancel. Also used as the alert's notification id.
         */
        internal val PlannedAlert.requestCode: Int
            get() = 1 + (prayerDay.toEpochDay().mod(100_000L)).toInt() * 16 + prayer.ordinal * 2 + kind.ordinal

        /** Exact alarms are always allowed before Android 12; from 12 the user can revoke them (14+: off by default). */
        fun canScheduleExact(alarmManager: AlarmManager): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

        internal fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
