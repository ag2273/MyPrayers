package app.mawaqeet.audio

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.mawaqeet.MawaqeetApplication
import app.mawaqeet.core.AdhkarAudioPack
import app.mawaqeet.core.AdhkarSetId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException

/** The listener's choices for Adhkar Audio (Settings → Adhkar audio), kept in their own small preferences file. */
data class AdhkarAudioPrefs(
    /** When a recording ends, the reader moves to the next dhikr and plays it. */
    val autoNext: Boolean = true,
    /** The download may use mobile data; otherwise it waits for Wi-Fi. */
    val mobileData: Boolean = true,
    /** «ليس الآن» on the download offer: the reader stops offering it (the play button still can). */
    val offerDismissed: Boolean = false,
)

/**
 * Listening to the adhkar (the recordings are downloaded once, never bundled): the [store] of recordings on this phone,
 * the [player], the listener's [prefs], and starting or stopping the download. One per process, from
 * [MawaqeetApplication.adhkarAudio]; null there when this build has no pack key (debug and CI builds without the
 * local key file), and then nothing about audio shows.
 */
class AdhkarAudio(context: Context, pack: AdhkarAudioPack, packKey: ByteArray, cipher: DeviceCipher, fetcher: Fetcher) {
    private val appContext = context.applicationContext
    val store = AdhkarAudioStore(pack, packKey, cipher, File(appContext.filesDir, DIR), fetcher)
    val player = AdhkarAudioPlayer(appContext) { store.load(it) }
    val totalBytes: Long = pack.totalBytes

    private val preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _prefs = MutableStateFlow(readPrefs())
    val prefs: StateFlow<AdhkarAudioPrefs> = _prefs.asStateFlow()

    private fun readPrefs() = AdhkarAudioPrefs(
        autoNext = preferences.getBoolean(KEY_AUTO_NEXT, true),
        mobileData = preferences.getBoolean(KEY_MOBILE_DATA, true),
        offerDismissed = preferences.getBoolean(KEY_OFFER_DISMISSED, false),
    )

    fun updatePrefs(change: (AdhkarAudioPrefs) -> AdhkarAudioPrefs) {
        val next = change(_prefs.value)
        preferences.edit()
            .putBoolean(KEY_AUTO_NEXT, next.autoNext)
            .putBoolean(KEY_MOBILE_DATA, next.mobileData)
            .putBoolean(KEY_OFFER_DISMISSED, next.offerDismissed)
            .apply()
        _prefs.value = next
    }

    fun recordingFor(set: AdhkarSetId, dhikr: String): String? = store.recordingFor(set, dhikr)

    /** Queues the download of what is missing; it runs when a network allowed by [AdhkarAudioPrefs.mobileData] is up. */
    fun startDownload() {
        val network = if (_prefs.value.mobileData) NetworkType.CONNECTED else NetworkType.UNMETERED
        val request = OneTimeWorkRequestBuilder<AdhkarAudioDownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build())
            .build()
        store.markWaiting()
        WorkManager.getInstance(appContext).enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, request)
    }

    /** Stops the download; what was downloaded stays, and a later start carries on from there. */
    fun stopDownload() {
        WorkManager.getInstance(appContext).cancelUniqueWork(WORK)
        store.markStopped(failed = false)
    }

    /** Settings → Adhkar audio → Delete: stops playing and downloading and removes every recording. */
    fun delete() {
        player.stop()
        WorkManager.getInstance(appContext).cancelUniqueWork(WORK)
        store.delete()
    }

    companion object {
        /** The pack key this build carries (a generated class, see app/build.gradle.kts), or null without one. */
        fun buildKey(): ByteArray? = AdhkarAudioKey.get()

        /** Under filesDir; kept out of backups (data_extraction_rules.xml): this phone's key opens nothing elsewhere. */
        const val DIR = "adhkar-audio"
        private const val PREFS = "adhkar_audio"
        private const val KEY_AUTO_NEXT = "auto_next"
        private const val KEY_MOBILE_DATA = "mobile_data"
        private const val KEY_OFFER_DISMISSED = "offer_dismissed"
        private const val WORK = "adhkar-audio-download"
    }
}

/** Runs [AdhkarAudioStore.download]: a network failure retries later (WorkManager's backoff), a few times at most. */
class AdhkarAudioDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val audio = (applicationContext as MawaqeetApplication).adhkarAudio ?: return Result.failure()
        return try {
            audio.store.download()
            Result.success()
        } catch (e: IOException) {
            retryOrFail(audio)
        } catch (e: SecurityException) {
            retryOrFail(audio)
        }
    }

    private fun retryOrFail(audio: AdhkarAudio): Result =
        if (runAttemptCount < 4) {
            audio.store.markWaiting()
            Result.retry()
        } else {
            audio.store.markStopped(failed = true)
            Result.failure()
        }
}
