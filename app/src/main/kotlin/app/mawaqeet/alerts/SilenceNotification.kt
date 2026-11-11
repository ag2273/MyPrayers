package app.mawaqeet.alerts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.mawaqeet.R
import app.mawaqeet.core.DigitStyle
import app.mawaqeet.core.DisplayFormatter
import app.mawaqeet.core.SilenceWindow
import app.mawaqeet.ui.appLocale
import app.mawaqeet.ui.localizedContext
import java.time.ZoneId

/**
 * The quiet ongoing notification while a Silence Window is active (#67): «الهاتف صامت حتى ٣:٢٤» on its own
 * low-importance channel, with «استعادة الصوت», which ends the window early through [SilenceReceiver.ACTION_RESTORE].
 * The time is the window's planned end in the phone's zone and clock style, in the app's language and digits.
 */
internal object SilenceNotification {
    const val CHANNEL = "prayer_silence"

    /** One window is active at a time, so one id; below the Silence Windows' alarm request codes. */
    private const val ID = 1_999_999

    fun post(context: Context, window: SilenceWindow, digits: DigitStyle, language: String) {
        if (!AdhanNotification.canPost(context)) return
        val locale = appLocale(context, language)
        val text = localizedContext(context, locale)
        ensureChannel(context, text)
        val end = window.end.atZone(ZoneId.systemDefault())
        val until = DisplayFormatter(locale, digits).clockTime(end, DateFormat.is24HourFormat(context))
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(text.getString(R.string.prayer_silence_notification, until))
            .setContentIntent(AlertScheduler.openAppIntent(context))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            // A safety net: gone at the planned end even if the end alarm never comes.
            .setTimeoutAfter((window.end.toEpochMilli() - System.currentTimeMillis()).coerceAtLeast(60_000))
            .addAction(
                NotificationCompat.Action(0, text.getString(R.string.prayer_silence_restore), SilenceReceiver.restoreIntent(context, window)),
            )
            .build()
        NotificationManagerCompat.from(context).notify(ID, notification)
    }

    fun cancel(context: Context) = NotificationManagerCompat.from(context).cancel(ID)

    private fun ensureChannel(context: Context, text: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, text.getString(R.string.prayer_silence_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
                description = text.getString(R.string.prayer_silence_channel_description)
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            },
        )
    }
}
