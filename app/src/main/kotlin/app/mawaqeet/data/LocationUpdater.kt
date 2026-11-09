package app.mawaqeet.data

import app.mawaqeet.core.CityDirectory
import app.mawaqeet.core.LocationChangeCheck
import app.mawaqeet.core.LocationSource
import app.mawaqeet.core.LocationUpdate
import app.mawaqeet.core.PrayerCore
import app.mawaqeet.core.PrayerLogRepository
import app.mawaqeet.core.SavedLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Clock

/** What a fresh device position led to. */
sealed interface LocationCheckResult {
    /** The automatic location moved: [saved] is stored; show the short "location updated" note. */
    data class Updated(val saved: SavedLocation) : LocationCheckResult

    /** The location is manual and the user seems to be elsewhere: ask; on yes, [LocationUpdater.change] it. */
    data class Ask(val proposed: SavedLocation) : LocationCheckResult

    data object Unchanged : LocationCheckResult
}

/**
 * The one way the stored location changes (city search, coordinates, device position), so every change first
 * fixes the place of the Prayer Days that have ended ([PrayerCore.recordLocationChange]). [cities] is read lazily
 * on [Dispatchers.IO].
 */
class LocationUpdater(
    private val settings: SettingsRepository,
    private val log: PrayerLogRepository,
    private val clock: Clock,
    private val cities: () -> CityDirectory,
) {
    /** Saves [saved] as the location, after fixing the past Prayer Days at the old one. */
    suspend fun change(saved: SavedLocation) {
        val old = settings.settings.first()
        if (old.location != saved.location) {
            withContext(Dispatchers.IO) { PrayerCore(old, clock, log).recordLocationChange() }
        }
        settings.setLocation(saved)
        settings.setLocationMaybeStale(false)
    }

    /** Applies the travel rule ([LocationChangeCheck]) to a device position read while the app is open. */
    suspend fun onDevicePosition(latitude: Double, longitude: Double): LocationCheckResult {
        val stored = settings.savedLocation.first()
        val update = LocationChangeCheck.check(stored, latitude, longitude)
        if (update == LocationUpdate.NOTHING) {
            settings.setLocationMaybeStale(false)
            return LocationCheckResult.Unchanged
        }
        val proposed = withContext(Dispatchers.IO) { cities().nearest(latitude, longitude) }
            .toSavedLocation(LocationSource.AUTOMATIC)
        return if (update == LocationUpdate.UPDATE_SILENTLY) {
            change(proposed)
            LocationCheckResult.Updated(proposed)
        } else {
            LocationCheckResult.Ask(proposed)
        }
    }
}
