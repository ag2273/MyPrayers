package app.mawaqeet.alerts

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import app.mawaqeet.core.SilenceMode
import app.mawaqeet.core.SilenceWindow

/**
 * Prayer Silence's thin adapter over the ringer. At a Silence Window's [start] it puts the phone on the window's mode
 * (silent or vibrate) only if the phone is on normal sound with Do Not Disturb off, and records the mode it replaced; at
 * the [end] it restores that mode only if the ringer is still the one the app set (so the user's own change during the
 * window wins). A phone already silent, on vibrate or on Do Not Disturb is left alone and nothing is restored, whichever
 * mode is chosen. Without Do Not Disturb (notification policy)
 * access Android forbids silencing, so nothing happens.
 *
 * The record lives in device-protected storage, so a window that ends before the first unlock still restores the sound.
 */
class PrayerSilencer(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val record = context.createDeviceProtectedStorageContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun start(window: SilenceWindow) {
        if (!hasAccess(notifications)) return
        val doNotDisturb = notifications.currentInterruptionFilter.let {
            it != NotificationManager.INTERRUPTION_FILTER_ALL && it != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        }
        val previous = audio.ringerMode
        if (doNotDisturb || previous != AudioManager.RINGER_MODE_NORMAL) return
        val applied = ringerMode(window.mode)
        val changed = runCatching { audio.ringerMode = applied }.isSuccess
        if (!changed) return
        record.edit()
            .putString(KEY_WINDOW, window.id)
            .putInt(KEY_PREVIOUS, previous)
            .putInt(KEY_APPLIED, applied)
            .apply()
    }

    fun end(window: SilenceWindow) {
        if (record.getString(KEY_WINDOW, null) != window.id) return
        val previous = record.getInt(KEY_PREVIOUS, AudioManager.RINGER_MODE_NORMAL)
        val applied = record.getInt(KEY_APPLIED, -1)
        record.edit().clear().apply()
        if (audio.ringerMode == applied) runCatching { audio.ringerMode = previous }
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

        private val SilenceWindow.id: String get() = "$prayerDay/${prayer.name}"

        /** Do Not Disturb access (notification policy access), which Prayer Silence needs to change the ringer. */
        fun hasAccess(notifications: NotificationManager): Boolean = notifications.isNotificationPolicyAccessGranted

        fun hasAccess(context: Context): Boolean = hasAccess(context.getSystemService(NotificationManager::class.java))
    }
}
