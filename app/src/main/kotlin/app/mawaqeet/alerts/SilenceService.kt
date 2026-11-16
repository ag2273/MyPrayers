package app.mawaqeet.alerts

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.mawaqeet.core.SilenceWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Runs Prayer Silence's ringer changes ([PrayerSilencer]) in a foreground service for the second they take.
 *
 * From Android 17 the system silently ignores `setRingerMode` and `setStreamVolume` from an app with no visible activity
 * and no foreground service ("background audio hardening"), whatever its Do Not Disturb access. A window's start and end
 * come from alarms, with the app in the background, so a Samsung on Android 17 went to vibrate (the start ran inside the
 * adhan player's foreground service) but never came back (the end ran from the alarm alone). The alarm may still start
 * a foreground service, and inside it the ringer change goes through.
 *
 * Before Android 17 the change runs in the alarm's receiver as it always has.
 */
class SilenceService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            SilenceNotification.working(this),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
        )
        val action = intent?.action
        val window = intent?.silenceWindow()
        scope.launch {
            try {
                perform(this@SilenceService, action, window)
            } finally {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val TAG = "SilenceService"
        private const val NOTIFICATION_ID = 1_999_998
        const val ACTION_START = "app.mawaqeet.action.SILENCE_SERVICE_START"
        const val ACTION_END = "app.mawaqeet.action.SILENCE_SERVICE_END"
        const val ACTION_RESTORE = "app.mawaqeet.action.SILENCE_SERVICE_RESTORE"

        /** Whether ringer changes need a foreground service: Android 17 and later. Tests switch it. */
        @androidx.annotation.VisibleForTesting
        internal var foregroundNeeded: () -> Boolean = { Build.VERSION.SDK_INT >= 37 }

        /**
         * Does [action] for [window]: in this service where Android needs one, else right here ([inline] runs the work
         * off the receiver, as it always ran). A phone that refuses the service start still gets the direct try.
         */
        fun run(context: Context, action: String, window: SilenceWindow, inline: (() -> Unit) -> Unit) {
            if (foregroundNeeded()) {
                val intent = Intent(context, SilenceService::class.java).setAction(action).putSilenceWindow(window)
                val started = runCatching { ContextCompat.startForegroundService(context, intent) }
                    .onFailure { Log.w(TAG, "Can't start the silence service", it) }
                    .isSuccess
                if (started) return
            }
            inline { perform(context, action, window) }
        }

        internal fun perform(context: Context, action: String?, window: SilenceWindow?) {
            val silencer = PrayerSilencer(context)
            when (action) {
                ACTION_START -> window?.let(silencer::start)
                ACTION_END -> window?.let(silencer::end)
                ACTION_RESTORE -> window?.let(silencer::restore)
            }
        }
    }
}
