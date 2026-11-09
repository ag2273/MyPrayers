package app.mawaqeet.core

/** What to do with a fresh device position, given the stored location. */
enum class LocationUpdate {
    /** Automatic location (or none yet): save the new one and show a short "location updated" note. */
    UPDATE_SILENTLY,

    /** Manual location: ask before changing it; if the user agrees, the new one is automatic. */
    ASK_FIRST,

    /** The user has not moved more than [LocationChangeCheck.THRESHOLD_KM]. */
    NOTHING,
}

/** The travel rule: a device position counts as a move only beyond [THRESHOLD_KM] from the stored location. */
object LocationChangeCheck {
    /** About 25 to 35 seconds of Prayer Time error at most (docs/research/location.md). */
    const val THRESHOLD_KM = 10.0

    fun check(stored: SavedLocation?, latitude: Double, longitude: Double): LocationUpdate {
        if (stored == null) return LocationUpdate.UPDATE_SILENTLY
        val moved = distanceKm(stored.location.latitude, stored.location.longitude, latitude, longitude) > THRESHOLD_KM
        return when {
            !moved -> LocationUpdate.NOTHING
            stored.source == LocationSource.AUTOMATIC -> LocationUpdate.UPDATE_SILENTLY
            else -> LocationUpdate.ASK_FIRST
        }
    }
}
