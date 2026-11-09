package app.mawaqeet.core

import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

private val RIYADH_CENTRE = Location(24.7136, 46.6753, ZoneId.of("Asia/Riyadh"))

private fun stored(source: LocationSource) = SavedLocation(RIYADH_CENTRE, source, city = null)

class LocationChangeCheckTest {

    // 0.1 degree of latitude is about 11.1 km anywhere on Earth.

    @Test
    fun `an automatic location more than 10 km away updates silently`() {
        val update = LocationChangeCheck.check(stored(LocationSource.AUTOMATIC), 24.7136 + 0.1, 46.6753)
        assertEquals(LocationUpdate.UPDATE_SILENTLY, update)
    }

    @Test
    fun `a manual location more than 10 km away asks first`() {
        val update = LocationChangeCheck.check(stored(LocationSource.MANUAL), 24.7136 + 0.1, 46.6753)
        assertEquals(LocationUpdate.ASK_FIRST, update)
    }

    @Test
    fun `within 10 km nothing happens in either mode`() {
        // About 8.9 km north.
        for (source in LocationSource.entries) {
            assertEquals(LocationUpdate.NOTHING, LocationChangeCheck.check(stored(source), 24.7136 + 0.08, 46.6753))
        }
    }

    @Test
    fun `with no stored location the device position is used silently`() {
        assertEquals(LocationUpdate.UPDATE_SILENTLY, LocationChangeCheck.check(null, 30.0444, 31.2357))
    }
}
