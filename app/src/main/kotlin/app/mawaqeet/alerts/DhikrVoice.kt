package app.mawaqeet.alerts

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.core.app.NotificationManagerCompat
import app.mawaqeet.R
import app.mawaqeet.core.DhikrPhrase
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * «بصوت الذكر»: the dhikr reminder says its [DhikrPhrase] aloud with the recording bundled for it, like a notification
 * sound: on the notification stream, and only when the phone would make a notification sound (ringer on, Do Not
 * Disturb off, the «ذكر الله والتسبيح» channel not silenced). Music is ducked while it speaks. Each recording is under
 * 3 seconds, so it is played inside the receiver's broadcast.
 */
internal object DhikrVoice {
    /** The text of [phrase] in the notification. */
    @StringRes
    fun text(phrase: DhikrPhrase): Int = when (phrase) {
        DhikrPhrase.SUBHAN_ALLAH -> R.string.dhikr_subhan_allah
        DhikrPhrase.ASTAGHFIRULLAH -> R.string.dhikr_astaghfirullah
        DhikrPhrase.ALHAMDULILLAH -> R.string.dhikr_alhamdulillah
        DhikrPhrase.ALLAHU_AKBAR -> R.string.dhikr_allahu_akbar
        DhikrPhrase.SALAT_ALA_MUHAMMAD -> R.string.dhikr_salat_ala_muhammad
        DhikrPhrase.SUBHAN_ALLAH_WA_BIHAMDIHI -> R.string.dhikr_subhan_allah_wa_bihamdihi
        DhikrPhrase.LA_ILAHA_ILLALLAH -> R.string.dhikr_la_ilaha_illallah
    }

    /** The recording of [phrase]. */
    @RawRes
    fun recording(phrase: DhikrPhrase): Int = when (phrase) {
        DhikrPhrase.SUBHAN_ALLAH -> R.raw.dhikr_subhan_allah
        DhikrPhrase.ASTAGHFIRULLAH -> R.raw.dhikr_astaghfirullah
        DhikrPhrase.ALHAMDULILLAH -> R.raw.dhikr_alhamdulillah
        DhikrPhrase.ALLAHU_AKBAR -> R.raw.dhikr_allahu_akbar
        DhikrPhrase.SALAT_ALA_MUHAMMAD -> R.raw.dhikr_salat_ala_muhammad
        DhikrPhrase.SUBHAN_ALLAH_WA_BIHAMDIHI -> R.raw.dhikr_subhan_allah_wa_bihamdihi
        DhikrPhrase.LA_ILAHA_ILLALLAH -> R.raw.dhikr_la_ilaha_illallah
    }

    /** Plays a recording and returns once it ends; tests replace it to hear what would be said. */
    @Volatile
    internal var player: suspend (Context, Int) -> Unit = ::playToEnd

    /** Says [phrase] aloud when the phone would make a notification sound now. */
    suspend fun say(context: Context, phrase: DhikrPhrase) {
        if (!mayMakeSound(context)) return
        runCatching { player(context, recording(phrase)) }
    }

    /** The ringer is on, Do Not Disturb lets every notification through, and the dhikr channel still has its sound. */
    internal fun mayMakeSound(context: Context): Boolean {
        val audio = context.getSystemService(AudioManager::class.java)
        if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return false
        val notifications = context.getSystemService(NotificationManager::class.java)
        if (notifications.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL) return false
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        val channel = notifications.getNotificationChannel(WorshipReminderNotification.DHIKR_CHANNEL) ?: return true
        return channel.importance >= NotificationManager.IMPORTANCE_DEFAULT
    }

    private val attributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    /** Each recording is under 3 seconds; never hold the broadcast longer than this. */
    private const val LONGEST_MS = 8_000L

    private suspend fun playToEnd(context: Context, @RawRes recording: Int) {
        val audio = context.getSystemService(AudioManager::class.java)
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { }
            .build()
        val player = MediaPlayer.create(context, recording, attributes, audio.generateAudioSessionId()) ?: return
        audio.requestAudioFocus(focus)
        try {
            withTimeoutOrNull(LONGEST_MS) {
                suspendCancellableCoroutine { done ->
                    player.setOnCompletionListener { if (done.isActive) done.resume(Unit) }
                    player.setOnErrorListener { _, _, _ -> if (done.isActive) done.resume(Unit); true }
                    player.start()
                }
            }
        } finally {
            player.release()
            audio.abandonAudioFocusRequest(focus)
        }
    }
}
