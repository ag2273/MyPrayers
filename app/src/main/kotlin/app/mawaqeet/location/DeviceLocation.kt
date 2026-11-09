package app.mawaqeet.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * The device's approximate position from the platform location manager (no Google Play services): the fused
 * provider if present (API 31+), then network, then GPS. Only `ACCESS_COARSE_LOCATION` is ever requested, and the
 * position is read only while the app is open. It never leaves the phone: it is only turned into a location for
 * Prayer Times.
 */
object DeviceLocation {
    const val PERMISSION = Manifest.permission.ACCESS_COARSE_LOCATION

    private const val TIMEOUT_MS = 10_000L

    /** A cached fix younger than this is used without asking for a fresh one. */
    private const val FRESH_ENOUGH_MS = 10 * 60_000L

    fun isGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** A recent cached fix, else a fresh one within 10 s, else any cached fix; null without permission or provider. */
    @SuppressLint("MissingPermission") // Checked by isGranted.
    suspend fun current(context: Context): Location? {
        if (!isGranted(context)) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        if (!LocationManagerCompat.isLocationEnabled(manager)) return null
        val provider = provider(manager) ?: return null
        val cached = runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        if (cached != null && ageMs(cached) < FRESH_ENOUGH_MS) return cached
        val fresh = withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                LocationManagerCompat.getCurrentLocation(
                    manager, provider, signal, ContextCompat.getMainExecutor(context),
                ) { continuation.resume(it) }
            }
        }
        return fresh ?: cached
    }

    private fun provider(manager: LocationManager): String? {
        val order = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
        }
        return order.firstOrNull { LocationManagerCompat.hasProvider(manager, it) && manager.isProviderEnabled(it) }
    }

    private fun ageMs(location: Location): Long =
        (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000
}
