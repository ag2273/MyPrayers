package app.mawaqeet.alerts

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log

/**
 * The Android side of a Custom Adhan (#28): the file the user picks with the system file picker
 * (`ACTION_OPEN_DOCUMENT`, audio files only). The core only ever sees its content URI, as an opaque reference.
 */
object CustomAdhanFiles {
    private const val TAG = "CustomAdhan"

    /** MIME types the picker offers. */
    val MIME_TYPES = arrayOf("audio/*")

    /** Keeps read access to [uri] across restarts and reboots. False if the provider refuses a persistable grant. */
    fun keep(context: Context, uri: Uri): Boolean = try {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        true
    } catch (e: SecurityException) {
        Log.w(TAG, "No persistable permission for $uri", e)
        false
    }

    /** Gives back read access to [references] no recording uses any more. */
    fun release(context: Context, references: Collection<String>) {
        for (reference in references) {
            try {
                context.contentResolver.releasePersistableUriPermission(Uri.parse(reference), Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                Log.w(TAG, "Permission for $reference was already gone", e)
            }
        }
    }

    /**
     * The file's name, as the provider shows it, or null if it can't be read any more (deleted, permission lost).
     * Opens the file, so call it off the main thread.
     */
    fun readableName(context: Context, reference: String): String? {
        val uri = Uri.parse(reference)
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { } ?: return null
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: uri.lastPathSegment ?: reference
        } catch (e: Exception) {
            // SecurityException, FileNotFoundException, IllegalArgumentException from providers.
            Log.w(TAG, "Custom Adhan unreadable: $reference", e)
            null
        }
    }
}

/**
 * Plays an adhan once from Settings so the user can hear their choice: on the alarm stream at the alarm volume,
 * like a real Adhan Alert. One at a time; call [stop] when the screen leaves.
 */
class AdhanPreview(private val context: Context) {
    private var player: MediaPlayer? = null

    val isPlaying: Boolean get() = player != null

    /** Starts [uri], replacing any preview still playing; [onEnd] runs when it finishes or fails. */
    fun play(uri: Uri, onEnd: () -> Unit) {
        stop()
        val mp = MediaPlayer()
        player = mp
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            mp.setDataSource(context, uri)
        } catch (e: Exception) {
            Log.w("AdhanPreview", "Can't open $uri", e)
            stop()
            onEnd()
            return
        }
        mp.setOnPreparedListener { it.start() }
        mp.setOnCompletionListener {
            stop()
            onEnd()
        }
        mp.setOnErrorListener { _, _, _ ->
            stop()
            onEnd()
            true
        }
        mp.prepareAsync()
    }

    fun stop() {
        player?.release()
        player = null
    }
}
