package app.mawaqeet.alerts

import android.app.Activity
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import app.mawaqeet.R
import app.mawaqeet.ui.theme.MawaqeetTheme

/**
 * The Qiyam alarm (#97) over the lock screen, opened by [QiyamAlarmNotification]'s full-screen intent like
 * [AdhanAlarmActivity]: «قيام الليل», its text and Stop, which ends the sound with the notification. It closes by itself
 * when the alarm stops ringing (after a minute) or once the notification is gone. It reads nothing from storage, so it
 * works before the first unlock too.
 */
class QiyamAlarmActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        render(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        render(intent)
    }

    override fun onResume() {
        super.onResume()
        // Stopped from the notification (or timed out) while this was in the background.
        val id = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (getSystemService(NotificationManager::class.java).activeNotifications.none { it.id == id }) finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun render(intent: Intent) {
        val id = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
        handler.removeCallbacksAndMessages(null)
        val left = intent.getLongExtra(EXTRA_RINGS_UNTIL, 0) - System.currentTimeMillis()
        if (left <= 0) {
            finish()
            return
        }
        handler.postDelayed({ finish() }, left)
        setContent {
            MawaqeetTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    Column(
                        Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Spacer(Modifier.weight(1f))
                        Text(
                            title,
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                        )
                        Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = { stop(id) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        ) { Text(stringResource(R.string.adhan_stop)) }
                    }
                }
            }
        }
    }

    /** The sound is the notification's own: cancelling it stops the alarm. */
    private fun stop(id: Int) {
        NotificationManagerCompat.from(this).cancel(id)
        finish()
    }

    companion object {
        private const val EXTRA_NOTIFICATION_ID = "notification_id"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TEXT = "text"
        private const val EXTRA_RINGS_UNTIL = "rings_until"

        /** The full-screen intent of the alarm notification [id], ringing until [ringsUntil] (epoch millis). */
        internal fun pendingIntent(context: Context, id: Int, title: String, text: String, ringsUntil: Long): PendingIntent =
            PendingIntent.getActivity(
                context,
                id,
                Intent(context, QiyamAlarmActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                    .putExtra(EXTRA_NOTIFICATION_ID, id)
                    .putExtra(EXTRA_TITLE, title)
                    .putExtra(EXTRA_TEXT, text)
                    .putExtra(EXTRA_RINGS_UNTIL, ringsUntil),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}

/** Alarm-clock mode's window: over the lock screen, turning the screen on and keeping it on while shown. */
internal fun Activity.showOverLockScreen() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        setShowWhenLocked(true)
        setTurnScreenOn(true)
    } else {
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
    }
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
}
