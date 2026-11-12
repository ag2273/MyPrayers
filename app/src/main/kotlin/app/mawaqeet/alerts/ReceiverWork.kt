package app.mawaqeet.alerts

import android.content.BroadcastReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * A receiver's work off the main thread: `goAsync` keeps the broadcast alive while [run]'s block runs on
 * [Dispatchers.IO], so preferences, `AudioManager`, files and the database are never touched on the main thread. A
 * broadcast can `goAsync` only once, so a receiver runs all its work in one block.
 */
internal object ReceiverWork {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var last: Job? = null

    fun run(receiver: BroadcastReceiver, block: suspend () -> Unit) {
        // Null when onReceive is called directly (tests): there is no broadcast to keep alive.
        val pending: BroadcastReceiver.PendingResult? = receiver.goAsync()
        last = scope.launch {
            try {
                block()
            } finally {
                pending?.finish()
            }
        }
    }

    /** Waits for the last receiver's work (tests). */
    suspend fun idle() {
        last?.join()
    }
}
