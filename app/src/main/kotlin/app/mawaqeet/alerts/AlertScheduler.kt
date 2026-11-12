package app.mawaqeet.alerts

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.mawaqeet.MainActivity
import app.mawaqeet.core.AdhanVolume
import app.mawaqeet.core.AlertKind
import app.mawaqeet.core.AlertPlan
import app.mawaqeet.core.AlertSound
import app.mawaqeet.core.DeliveryMode
import app.mawaqeet.core.PlannedAlert
import app.mawaqeet.core.PlannedWorshipReminder
import app.mawaqeet.core.Prayer
import app.mawaqeet.core.SilenceWindow
import java.time.Instant

/**
 * Turns the core's [AlertPlan] into alarms: one per planned alert (`setExactAndAllowWhileIdle`, or
 * `setAlarmClock` in alarm-clock mode) plus one display alarm ([AlertPlan.nextDisplayChange]: local midnight, or a Prayer
 * Time with no Adhan Alert), plus a start and an end alarm per Silence Window ([SilenceReceiver]; the start only while
 * it is still ahead, so re-planning during a window never silences twice, and only when no Adhan Alert opens it), plus
 * one exact alarm per Worship Reminder ([WorshipReminderReceiver]). Applying a plan reconciles: alarms of the previously
 * applied plan that the new one lacks
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
    fun apply(plan: AlertPlan, now: Instant = Instant.now()) {
        val previous = store.read()
        val keep = plan.alerts.map { it.requestCode }.toSet()
        previous?.alerts?.filter { it.requestCode !in keep }?.forEach { cancel(alertIntent(it, PendingIntent.FLAG_NO_CREATE)) }
        val keepSilence = plan.silenceWindows.map { it.startRequestCode }.toSet()
        previous?.silenceWindows?.filter { it.startRequestCode !in keepSilence }?.forEach {
            cancel(silenceIntent(it, start = true, PendingIntent.FLAG_NO_CREATE))
            cancel(silenceIntent(it, start = false, PendingIntent.FLAG_NO_CREATE))
        }
        val keepWorship = plan.worshipReminders.map { it.requestCode }.toSet()
        previous?.worshipReminders?.filter { it.requestCode !in keepWorship }?.forEach {
            cancel(worshipIntent(it, PendingIntent.FLAG_NO_CREATE))
        }
        store.write(plan)
        arm(plan)
        armSilence(plan, now)
        armWorship(plan.worshipReminders, now)
    }

    /** Re-arms the stored plan's alerts and Silence Windows still after [now], with no recalculation (works before the first unlock). */
    fun rearmStoredPlan(now: Instant) {
        val stored = store.read() ?: return
        arm(stored.copy(alerts = stored.alerts.filter { it.instant.isAfter(now) }), now)
        armSilence(stored, now)
        armWorship(stored.worshipReminders, now)
    }

    /** Each Worship Reminder still after [now], as an exact alarm (never an alarm clock: it is not an alarm). */
    private fun armWorship(reminders: List<PlannedWorshipReminder>, now: Instant) {
        if (!canScheduleExact(alarmManager)) return
        for (reminder in reminders) {
            if (!reminder.instant.isAfter(now)) continue
            val operation = worshipIntent(reminder, PendingIntent.FLAG_UPDATE_CURRENT)!!
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.instant.toEpochMilli(), operation)
        }
    }

    private fun worshipIntent(reminder: PlannedWorshipReminder, flag: Int): PendingIntent? = PendingIntent.getBroadcast(
        context,
        reminder.requestCode,
        WorshipReminderReceiver.intent(context, reminder),
        flag or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Sets (or moves) [window]'s end alarm, e.g. when an overlapping window extends the silence to it. */
    fun armSilenceEnd(window: SilenceWindow) {
        if (!canScheduleExact(alarmManager)) return
        val operation = silenceIntent(window, start = false, PendingIntent.FLAG_UPDATE_CURRENT)!!
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, window.end.toEpochMilli(), operation)
    }

    /** Drops [window]'s end alarm (it was ended early with «استعادة الصوت»). */
    fun cancelSilenceEnd(window: SilenceWindow) = cancel(silenceIntent(window, start = false, PendingIntent.FLAG_NO_CREATE))

    /**
     * Each Silence Window's start alarm while the start is still after [now], and its end alarm while the end is. A
     * window whose Prayer has an Adhan Alert gets no start alarm: [AlertReceiver] starts it once the adhan stops (a
     * start alarm at the same instant could silence the phone before the adhan, and so stop it playing).
     */
    private fun armSilence(plan: AlertPlan, now: Instant) {
        if (!canScheduleExact(alarmManager)) return
        val announced = plan.alerts.mapNotNull { plan.silenceOpenedBy(it) }.toSet()
        for (window in plan.silenceWindows) {
            val byAdhanAlert = window in announced
            // An Adhan Alert turned on since the last plan: drop the start alarm set while it was off.
            if (byAdhanAlert) cancel(silenceIntent(window, start = true, PendingIntent.FLAG_NO_CREATE))
            for ((start, at) in listOf(true to window.start, false to window.end)) {
                if (!at.isAfter(now) || (start && byAdhanAlert)) continue
                val operation = silenceIntent(window, start, PendingIntent.FLAG_UPDATE_CURRENT)!!
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), operation)
            }
        }
    }

    private fun silenceIntent(window: SilenceWindow, start: Boolean, flag: Int): PendingIntent? = PendingIntent.getBroadcast(
        context,
        if (start) window.startRequestCode else window.startRequestCode + 1,
        SilenceReceiver.intent(context, window, start),
        flag or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun arm(plan: AlertPlan, now: Instant? = null) {
        if (!canScheduleExact(alarmManager)) return
        for (alert in plan.alerts) {
            val operation = alertIntent(alert, PendingIntent.FLAG_UPDATE_CURRENT, plan.silenceOpenedBy(alert))!!
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

    private fun alertIntent(alert: PlannedAlert, flag: Int, silence: SilenceWindow? = null): PendingIntent? = PendingIntent.getBroadcast(
        context,
        alert.requestCode,
        alarmIntent(alert, silence),
        flag or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * The broadcast an alert's alarm delivers to [AlertReceiver]. Everything the receiver needs travels here,
     * since it may fire before the first unlock, when settings can't be read. That includes the Silence Window
     * [silence] the alert opens.
     */
    internal fun alarmIntent(alert: PlannedAlert, silence: SilenceWindow? = null): Intent = Intent(context, AlertReceiver::class.java)
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
        .putExtra(AlertReceiver.EXTRA_ADHAN_VOLUME, alert.adhanVolume.name)
        .putExtra(AlertReceiver.EXTRA_PRAYER_DAY, alert.prayerDay.toString())
        .putExtra(AlertReceiver.EXTRA_FULL_SCREEN, alert.deliveryMode == DeliveryMode.ALARM_CLOCK)
        .apply {
            if (silence != null) {
                putExtra(AlertReceiver.EXTRA_SILENCE_START, silence.start.toEpochMilli())
                putExtra(AlertReceiver.EXTRA_SILENCE_END, silence.end.toEpochMilli())
                putExtra(AlertReceiver.EXTRA_SILENCE_MODE, silence.mode.name)
            }
        }

    /**
     * Settings → Alerts → Try it: an Adhan Alert for [prayer] in [delaySeconds], delivered like a real one in the
     * current mode (in alarm-clock mode as an alarm, so the status bar's alarm icon and the lock-screen alarm show),
     * with nothing to mark. Returns false when exact alarms aren't allowed.
     */
    fun scheduleTest(
        prayer: Prayer,
        isJumuah: Boolean,
        alarmClockMode: Boolean,
        sound: AlertSound,
        adhanVolume: AdhanVolume = AdhanVolume.ALARM,
        delaySeconds: Long = 10,
    ): Boolean {
        if (!canScheduleExact(alarmManager)) return false
        val at = System.currentTimeMillis() + delaySeconds * 1000
        val intent = Intent(context, AlertReceiver::class.java)
            .setAction(AlertReceiver.ACTION_ALERT)
            .putExtra(AlertReceiver.EXTRA_PRAYER, prayer.name)
            .putExtra(AlertReceiver.EXTRA_JUMUAH, isJumuah)
            .putExtra(AlertReceiver.EXTRA_REQUEST_CODE, TEST_REQUEST_CODE)
            .putExtra(AlertReceiver.EXTRA_SOUND, encodeAlertSound(sound))
            .putExtra(AlertReceiver.EXTRA_AT, at)
            .putExtra(AlertReceiver.EXTRA_KIND, AlertKind.ADHAN_ALERT.name)
            .putExtra(AlertReceiver.EXTRA_PRAYER_TIME, at)
            .putExtra(AlertReceiver.EXTRA_PLAY_ON_SILENT, false)
            .putExtra(AlertReceiver.EXTRA_VIBRATION, true)
            .putExtra(AlertReceiver.EXTRA_ADHAN_VOLUME, adhanVolume.name)
            .putExtra(AlertReceiver.EXTRA_FULL_SCREEN, alarmClockMode)
        val operation = PendingIntent.getBroadcast(context, TEST_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (alarmClockMode) {
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(at, openAppIntent(context)), operation)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        }
        return true
    }

    private fun midnightIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        MIDNIGHT_REQUEST_CODE,
        Intent(context, AlertReceiver::class.java).setAction(AlertReceiver.ACTION_MIDNIGHT),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val MIDNIGHT_REQUEST_CODE = 0

        /** The Try it alert's request code and notification id; planned alerts' codes are all positive. */
        const val TEST_REQUEST_CODE = -1

        /**
         * Stable and distinct per (Prayer Day, Prayer, kind), so re-planning replaces the same alarm and a
         * stale one can be found to cancel. Also used as the alert's notification id.
         */
        internal val PlannedAlert.requestCode: Int
            get() = 1 + (prayerDay.toEpochDay().mod(100_000L)).toInt() * 16 + prayer.ordinal * 2 + kind.ordinal

        /** The Silence Window [alert] opens: an Adhan Alert's, for the same Prayer and Prayer Day. */
        private fun AlertPlan.silenceOpenedBy(alert: PlannedAlert): SilenceWindow? =
            if (alert.kind != AlertKind.ADHAN_ALERT) null
            else silenceWindows.firstOrNull { it.prayer == alert.prayer && it.prayerDay == alert.prayerDay }

        /** Silence Windows' request codes sit above every alert's: [startRequestCode] for the start, one more for the end. */
        private const val SILENCE_REQUEST_CODE_BASE = 2_000_000

        internal val SilenceWindow.startRequestCode: Int
            get() = SILENCE_REQUEST_CODE_BASE + (prayerDay.toEpochDay().mod(100_000L)).toInt() * 16 + prayer.ordinal * 2

        /** Worship Reminders' request codes sit above every Silence Window's. */
        private const val WORSHIP_REQUEST_CODE_BASE = 4_000_000

        /** Stable and distinct per (Prayer Day, kind, slot): 128 codes a day, [PlannedWorshipReminder.MAX_SLOTS] per kind. */
        internal val PlannedWorshipReminder.requestCode: Int
            get() = WORSHIP_REQUEST_CODE_BASE + (prayerDay.toEpochDay().mod(100_000L)).toInt() * 128 +
                kind.ordinal * PlannedWorshipReminder.MAX_SLOTS + slot

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
