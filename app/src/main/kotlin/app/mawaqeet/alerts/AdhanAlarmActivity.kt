package app.mawaqeet.alerts

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import app.mawaqeet.R
import app.mawaqeet.core.Prayer
import app.mawaqeet.ui.theme.MawaqeetTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The Adhan Alert as an alarm, in alarm-clock mode: shown over the lock screen (and turning the screen on) through the
 * notification's full-screen intent, like a clock app's alarm. It shows the Prayer and its time, with Stop and, for a
 * real alert, Mark as prayed. It reads nothing from storage, so it works before the first unlock too.
 */
class AdhanAlarmActivity : AppCompatActivity() {
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

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun render(intent: Intent) {
        val prayer = intent.getStringExtra(AlertReceiver.EXTRA_PRAYER)?.let { n -> Prayer.entries.firstOrNull { it.name == n } }
        if (prayer == null) {
            finish()
            return
        }
        val jumuah = intent.getBooleanExtra(AlertReceiver.EXTRA_JUMUAH, false)
        val iftar = intent.getBooleanExtra(AlertReceiver.EXTRA_IFTAR, false)
        val id = intent.getIntExtra(AlertReceiver.EXTRA_REQUEST_CODE, -1)
        val prayerTime = intent.getLongExtra(AlertReceiver.EXTRA_PRAYER_TIME, System.currentTimeMillis())
        val day = AlertReceiver.prayerDay(intent)
        val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
            .format(Instant.ofEpochMilli(prayerTime).atZone(ZoneId.systemDefault()))
        setContent {
            MawaqeetTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    Column(
                        Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Spacer(Modifier.weight(1f))
                        Text(stringResource(if (iftar) R.string.adhan_alert_iftar_title else R.string.adhan_alarm_label), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            stringResource(if (jumuah) R.string.prayer_jumuah else nameRes(prayer)),
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                        )
                        Text(time, style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.weight(1f))
                        if (day != null) {
                            Button(
                                onClick = { send(MarkPrayedReceiver.intent(this@AdhanAlarmActivity, prayer, day, id)); finish() },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                            ) { Text(stringResource(R.string.mark_as_prayed)) }
                        }
                        OutlinedButton(
                            onClick = { stop(id); finish() },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        ) { Text(stringResource(R.string.adhan_stop)) }
                    }
                }
            }
        }
    }

    /** Stops the adhan if this alert's is playing; the notification stays, as after the notification's own Stop. */
    private fun stop(id: Int) {
        if (AdhanPlayerService.playingId == id) {
            send(AdhanPlayerService.stopIntent(this))
        }
        // A test alert has nothing to mark: Stop also clears its notification.
        if (id == AlertScheduler.TEST_REQUEST_CODE) NotificationManagerCompat.from(this).cancel(id)
    }

    private fun send(pending: PendingIntent) = runCatching { pending.send() }

    companion object {
        private fun nameRes(prayer: Prayer) = when (prayer) {
            Prayer.FAJR -> R.string.prayer_fajr
            Prayer.DHUHR -> R.string.prayer_dhuhr
            Prayer.ASR -> R.string.prayer_asr
            Prayer.MAGHRIB -> R.string.prayer_maghrib
            Prayer.ISHA -> R.string.prayer_isha
        }

        /** The full-screen intent for [n]'s notification: one per alert id, so a newer alert replaces it. */
        fun pendingIntent(context: Context, n: AdhanNotification): PendingIntent = PendingIntent.getActivity(
            context,
            n.id,
            Intent(context, AdhanAlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                .putExtra(AlertReceiver.EXTRA_PRAYER, n.prayer.name)
                .putExtra(AlertReceiver.EXTRA_JUMUAH, n.isJumuah)
                .putExtra(AlertReceiver.EXTRA_IFTAR, n.isIftar)
                .putExtra(AlertReceiver.EXTRA_REQUEST_CODE, n.id)
                .putExtra(AlertReceiver.EXTRA_PRAYER_TIME, n.prayerTime)
                .putExtra(AlertReceiver.EXTRA_PRAYER_DAY, n.prayerDay?.toString()),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
