package app.mawaqeet.alerts

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import app.mawaqeet.core.DigitStyle
import app.mawaqeet.core.SilenceMode
import app.mawaqeet.core.SilenceWindow
import java.time.Instant

/**
 * Prayer Silence's thin adapter over the ringer. At a Silence Window's [start] it puts the phone on the window's mode
 * (silent or vibrate) only if the phone is on normal sound with Do Not Disturb off, and records the mode it replaced; at
 * the [end] it restores that mode only if the ringer is still what the app left (so the user's own change during the
 * window wins), keeping any Do Not Disturb change the user made meanwhile. A window that starts while the app still holds the phone silenced for an
 * earlier one extends that silence to the later end, keeping the first window's replaced mode, so the earlier end never
 * restores the sound early. A phone already silent, on vibrate or on Do Not Disturb is left alone and nothing is
 * restored, whichever mode is chosen. Without Do Not Disturb (notification policy) access Android forbids silencing, so nothing happens.
 *
 * While it holds the phone silenced it shows [SilenceNotification] (in the app's digits and language, from the
 * stored plan unless given), whose «استعادة الصوت»
 * calls [restore]. A ringer change by the user ([ringerChanged], via [watchRinger]) hands the window to them: the
 * notification goes and nothing is restored at the end.
 *
 * Many phones (Samsung among them) turn Do Not Disturb on themselves when an app silences the ringer, sometimes a moment
 * later. A Do Not Disturb change within [SIDE_EFFECT_MILLIS] of the start ([interruptionFilterChanged]) is taken as
 * that side effect, not the user's choice: the end restores the sound and turns that Do Not Disturb off again.
 *
 * A window whose Prayer has an adhan playing waits for it ([awaitAdhan]) and starts when the player stops
 * ([adhanStopped]), so the silence never cuts the adhan off; its end stays at the planned end. After a reboot
 * [catchUp] starts a window still waiting for its adhan (the adhan never replays), re-posts the notification of one in
 * progress and restores the sound at once for one whose planned end has passed.
 *
 * The record (the window, the mode it replaced, the mode it set, and a window waiting for its adhan) lives in
 * device-protected storage, so a window that ends before the first unlock still restores the sound.
 */
class PrayerSilencer(
    private val context: Context,
    digits: DigitStyle? = null,
    language: String? = null,
) {
    /** Given, or else stored with the plan ([AlertPlanStore.readStyle]): a receiver never reads settings. */
    private val style by lazy {
        val stored = if (digits == null || language == null) AlertPlanStore(context).readStyle() else NotificationStyle.Default
        NotificationStyle(digits ?: stored.digits, language ?: stored.language)
    }

    private val audio = context.getSystemService(AudioManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val record = context.createDeviceProtectedStorageContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun start(window: SilenceWindow) {
        if (!hasAccess(notifications)) return
        if (extend(window)) return
        val doNotDisturb = isDoNotDisturb(notifications.currentInterruptionFilter)
        val previous = audio.ringerMode
        if (doNotDisturb || previous != AudioManager.RINGER_MODE_NORMAL) return
        val applied = ringerMode(window.mode)
        val changed = runCatching { audio.ringerMode = applied }.isSuccess
        if (!changed) return
        val filter = notifications.currentInterruptionFilter
        record.edit()
            .putString(KEY_WINDOW, encode(window))
            .putInt(KEY_PREVIOUS, previous)
            .putInt(KEY_APPLIED, applied)
            // What the app left, so the end can tell whether the user has since turned Do Not Disturb on or off.
            .putInt(KEY_FILTER, filter)
            // Do Not Disturb was off a moment ago, so if it is on now the phone turned it on along with the silence.
            .putBoolean(KEY_DND_BY_APP, isDoNotDisturb(filter))
            .putLong(KEY_STARTED_AT, SystemClock.elapsedRealtime())
            .apply()
        SilenceNotification.post(context, window, style.digits, style.language)
    }

    /**
     * [window] starts while the app still holds the phone silenced for another, overlapping one: the silence lasts until
     * the later of the two ends, with the mode it replaced kept from the first start. True when the app holds it.
     */
    private fun extend(window: SilenceWindow): Boolean {
        val held = active() ?: return false
        if (held.id == window.id || audio.ringerMode != record.getInt(KEY_APPLIED, -1)) return false
        if (window.end.isAfter(held.end)) hold(window)
        return true
    }

    /** [window] becomes the recorded one (same replaced mode), with its end alarm armed and the notification updated. */
    private fun hold(window: SilenceWindow) {
        record.edit().putString(KEY_WINDOW, encode(window)).apply()
        AlertScheduler(context).armSilenceEnd(window)
        SilenceNotification.post(context, window, style.digits, style.language)
    }

    /**
     * The window's planned end: the recorded mode comes back if the ringer is still what the app set (a ringer change by
     * the user during the window wins). A Do Not Disturb change by the user during the window is kept as they left it;
     * one the phone made along with the silence is undone.
     */
    fun end(window: SilenceWindow) {
        if (awaiting()?.id == window.id) record.edit().remove(KEY_AWAITING).apply()
        val held = active()
        // Another window holds the silence now (they overlapped): its own end restores the sound.
        if (held != null && held.id != window.id) return
        SilenceNotification.cancel(context)
        if (held == null) return
        val previous = record.getInt(KEY_PREVIOUS, AudioManager.RINGER_MODE_NORMAL)
        val applied = record.getInt(KEY_APPLIED, -1)
        // A record from before the filter was kept has none to compare.
        val filter = record.getInt(KEY_FILTER, notifications.currentInterruptionFilter)
        val dndByApp = record.getBoolean(KEY_DND_BY_APP, false)
        forget()
        // The user moved the ringer themselves: theirs to keep.
        if (audio.ringerMode != applied) return
        val now = notifications.currentInterruptionFilter
        runCatching { audio.ringerMode = previous }
        val after = notifications.currentInterruptionFilter
        when {
            // The user changed Do Not Disturb during the window: the ringer comes back but their setting stays, even
            // on phones where restoring the ringer turns Do Not Disturb off.
            now != filter -> if (after != now) runCatching { notifications.setInterruptionFilter(now) }
            // The phone turned it on along with the silence; restoring the ringer turns it off on some phones only.
            dndByApp && isDoNotDisturb(after) ->
                runCatching { notifications.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL) }
        }
    }

    /**
     * Do Not Disturb changed. Right after the start, with the ringer still as the app set it, that is the phone's own
     * side effect of the silence: it becomes part of what the app left, so the end still restores the sound (and turns
     * Do Not Disturb off). Later on it is the user's change, which [end] keeps.
     */
    fun interruptionFilterChanged() {
        if (!record.contains(KEY_WINDOW)) return
        if (audio.ringerMode != record.getInt(KEY_APPLIED, -1)) return
        val since = SystemClock.elapsedRealtime() - record.getLong(KEY_STARTED_AT, Long.MIN_VALUE / 2)
        if (since !in 0..SIDE_EFFECT_MILLIS) return
        val filter = notifications.currentInterruptionFilter
        record.edit().putInt(KEY_FILTER, filter).putBoolean(KEY_DND_BY_APP, isDoNotDisturb(filter)).apply()
    }

    /**
     * A new plan was applied ([AlertScheduler.apply]). The window the app holds silent ends now, under [end]'s usual
     * rules, if the plan no longer has it (Prayer Silence or its Prayer turned off) or now ends it before [now] (a
     * location change or an adjustment); one still planned with a new end or mode follows it, with its end alarm moved.
     * Without this the dropped end alarm would leave the phone silent indefinitely.
     */
    fun replan(planned: List<SilenceWindow>, now: Instant) {
        val held = active() ?: return
        val same = planned.firstOrNull { it.id == held.id }
        when {
            same == null || !same.end.isAfter(now) -> restore(held)
            same != held -> hold(same)
        }
    }

    /** «استعادة الصوت»: ends [window] now, as its planned end would, and drops its end alarm. */
    fun restore(window: SilenceWindow) {
        end(window)
        AlertScheduler(context).cancelSilenceEnd(window)
    }

    /**
     * The ringer changed. If it is no longer the mode the app set, the user changed it themselves: the window is theirs
     * now, so its end restores nothing and the notification goes.
     */
    fun ringerChanged() {
        if (!record.contains(KEY_WINDOW)) return
        if (audio.ringerMode == record.getInt(KEY_APPLIED, -1)) return
        forget()
        SilenceNotification.cancel(context)
    }

    /**
     * [window]'s adhan is about to play: the window starts when it stops ([adhanStopped]), not now. A window still
     * waiting for an earlier adhan (replaced by this one) starts at once.
     */
    fun awaitAdhan(window: SilenceWindow, now: Instant = Instant.now()) {
        adhanStopped(now)
        record.edit().putString(KEY_AWAITING, encode(window)).apply()
    }

    /** The adhan stopped (finished, Stop, or replaced): the window waiting for it starts, unless its end has passed. */
    fun adhanStopped(now: Instant = Instant.now()) {
        val waiting = awaiting() ?: return
        record.edit().remove(KEY_AWAITING).apply()
        if (waiting.end.isAfter(now)) start(waiting)
    }

    /**
     * After a reboot (or a clock change): a window still waiting for its adhan starts now, since the adhan never
     * replays; the window in progress gets its notification back, or, if its planned end has passed, its sound back.
     */
    fun catchUp(now: Instant) {
        adhanStopped(now)
        val window = active() ?: return
        if (window.end.isAfter(now)) SilenceNotification.post(context, window, style.digits, style.language) else end(window)
    }

    /** Whether the app holds the phone silenced for a Silence Window at [now]: one started and not yet at its end. */
    fun isActive(now: Instant = Instant.now()): Boolean = active()?.end?.isAfter(now) == true

    private fun active(): SilenceWindow? = record.getString(KEY_WINDOW, null)?.let(::decode)

    private fun awaiting(): SilenceWindow? = record.getString(KEY_AWAITING, null)?.let(::decode)

    private fun forget() {
        record.edit().remove(KEY_WINDOW).remove(KEY_PREVIOUS).remove(KEY_APPLIED).remove(KEY_FILTER)
            .remove(KEY_DND_BY_APP).remove(KEY_STARTED_AT).apply()
    }

    private fun ringerMode(mode: SilenceMode): Int = when (mode) {
        SilenceMode.SILENT -> AudioManager.RINGER_MODE_SILENT
        SilenceMode.VIBRATE -> AudioManager.RINGER_MODE_VIBRATE
    }

    companion object {
        private const val PREFS = "prayer_silence"
        private const val KEY_WINDOW = "window"
        private const val KEY_PREVIOUS = "previous_ringer_mode"
        private const val KEY_APPLIED = "applied_ringer_mode"
        private const val KEY_FILTER = "interruption_filter"
        private const val KEY_AWAITING = "awaiting_adhan"
        private const val KEY_DND_BY_APP = "dnd_by_app"
        private const val KEY_STARTED_AT = "started_at_elapsed"

        /** How long after the start a Do Not Disturb change counts as the phone's side effect of the silence. */
        const val SIDE_EFFECT_MILLIS = 60_000L

        private fun isDoNotDisturb(filter: Int): Boolean =
            filter != NotificationManager.INTERRUPTION_FILTER_ALL && filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN

        private val SilenceWindow.id: String get() = "$prayerDay/${prayer.name}"

        private fun encode(w: SilenceWindow): String = encodeSilenceWindow(w)

        /** Null for an unreadable record. */
        private fun decode(text: String): SilenceWindow? = runCatching { decodeSilenceWindow(text) }.getOrNull()

        /**
         * Listens for ringer and Do Not Disturb changes for as long as the process lives ([ringerChanged],
         * [interruptionFilterChanged]). Called from the Application, so
         * a process started later catches up at once: the ringer broadcast is sticky and arrives on registering.
         */
        fun watchRinger(context: Context) {
            val app = context.applicationContext
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = when (intent.action) {
                    NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED -> PrayerSilencer(app).interruptionFilterChanged()
                    else -> PrayerSilencer(app).ringerChanged()
                }
            }
            ContextCompat.registerReceiver(
                app,
                receiver,
                IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION).apply {
                    addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
                },
                // A protected system broadcast that no app can send. NOT_EXPORTED would add a signature permission on
                // Android 12 and older.
                ContextCompat.RECEIVER_EXPORTED,
            )
        }

        /** Do Not Disturb access (notification policy access), which Prayer Silence needs to change the ringer. */
        fun hasAccess(notifications: NotificationManager): Boolean = notifications.isNotificationPolicyAccessGranted

        fun hasAccess(context: Context): Boolean = hasAccess(context.getSystemService(NotificationManager::class.java))
    }
}
