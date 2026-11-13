package app.mawaqeet.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Expected bearings and distances are the published values of common Qibla calculators (great circle to the Kaaba
 * at 21.4225°N, 39.8262°E), not recomputed here. Bearings within 0.5°, distances within 1%.
 */
class QiblaTest {
    private fun assertQibla(latitude: Double, longitude: Double, bearing: Double, distanceKm: Double) {
        val qibla = Qibla.from(latitude, longitude)
        // Compared round the circle, so 359.9° is 0.1° from 0°.
        val off = ((qibla.bearing - bearing) % 360.0 + 540.0) % 360.0 - 180.0
        assertTrue(abs(off) <= 0.5, "bearing ${qibla.bearing}, expected $bearing")
        assertEquals(distanceKm, qibla.distanceKm, distanceKm * 0.01, "distance")
    }

    @Test
    fun `from Cairo the Qibla is south-east, about 136 degrees from true north and 1290 km away`() {
        assertQibla(30.0444, 31.2357, bearing = 136.1, distanceKm = 1_290.0)
    }

    @Test
    fun `from Jakarta the Qibla is west-north-west, about 295 degrees and 7920 km away`() {
        assertQibla(-6.2088, 106.8456, bearing = 295.2, distanceKm = 7_920.0)
    }

    @Test
    fun `from London the Qibla is about 119 degrees and 4790 km away`() {
        assertQibla(51.5074, -0.1278, bearing = 119.0, distanceKm = 4_790.0)
    }

    @Test
    fun `from New York the Qibla is north-east, about 58 degrees, not south-east as on a flat map`() {
        assertQibla(40.7128, -74.0060, bearing = 58.5, distanceKm = 10_300.0)
    }

    @Test
    fun `a tenth of a degree east of the Kaaba the Qibla is due west, about 10 km away`() {
        // 0.1° of longitude at 21.42°N is 111.32 km × cos(21.42°) × 0.1 ≈ 10.36 km.
        assertQibla(21.4225, 39.9262, bearing = 270.0, distanceKm = 10.36)
    }

    @Test
    fun `at the Kaaba's antipode every direction is the Qibla, half the Earth's circumference away`() {
        val qibla = Qibla.from(-21.4225, 39.8262 - 180.0)
        assertTrue(qibla.bearing in 0.0..<360.0, "a usable bearing, not NaN: ${qibla.bearing}")
        // Half of the mean circumference, 40 030 km.
        assertEquals(20_015.0, qibla.distanceKm, 200.0)
    }

    @Test
    fun `just off the antipode the Qibla still points the short way round`() {
        // A little north of the antipode, the short way to Makkah is north.
        assertQibla(-21.0, 39.8262 - 180.0, bearing = 0.0, distanceKm = 19_968.0)
    }

    private val cairo = Qibla(bearing = 136.0, distanceKm = 1_290.0)

    @Test
    fun `facing 120 degrees from Cairo the hint is to turn 16 degrees right`() {
        assertEquals(QiblaTurn(16, TurnSide.RIGHT), cairo.turn(heading = 120.0))
    }

    @Test
    fun `facing past the Qibla the hint is to turn left`() {
        assertEquals(QiblaTurn(14, TurnSide.LEFT), cairo.turn(heading = 150.0))
    }

    @Test
    fun `the hint takes the short way round through north`() {
        val north = Qibla(bearing = 10.0, distanceKm = 1.0)
        assertEquals(QiblaTurn(20, TurnSide.RIGHT), north.turn(heading = 350.0))
        assertEquals(QiblaTurn(30, TurnSide.LEFT), north.turn(heading = 40.0))
    }

    @Test
    fun `facing directly away the hint is half a turn`() {
        assertEquals(180, cairo.turn(heading = 316.0).degrees)
    }

    @Test
    fun `within 3 degrees either side the phone faces the Qibla`() {
        assertEquals(QiblaTurn(0, TurnSide.ON_TARGET), cairo.turn(heading = 133.0))
        assertEquals(QiblaTurn(0, TurnSide.ON_TARGET), cairo.turn(heading = 139.0))
        assertEquals(QiblaTurn(4, TurnSide.LEFT), cairo.turn(heading = 140.0))
        assertEquals(QiblaTurn(4, TurnSide.RIGHT), cairo.turn(heading = 132.0))
    }

    @Test
    fun `the Qibla is named by the nearest of the eight compass points`() {
        fun point(bearing: Double) = Qibla(bearing, 1.0).direction
        assertEquals(CompassPoint.SOUTH_EAST, Qibla.from(30.0444, 31.2357).direction) // Cairo
        assertEquals(CompassPoint.NORTH_WEST, Qibla.from(-6.2088, 106.8456).direction) // Jakarta, 295°: past 292.5°
        assertEquals(CompassPoint.NORTH_EAST, Qibla.from(40.7128, -74.0060).direction) // New York
        assertEquals(CompassPoint.NORTH, point(359.0))
        assertEquals(CompassPoint.NORTH, point(22.4))
        assertEquals(CompassPoint.NORTH_EAST, point(22.6))
        assertEquals(CompassPoint.EAST, point(90.0))
        assertEquals(CompassPoint.SOUTH, point(180.0))
        assertEquals(CompassPoint.SOUTH_WEST, point(225.0))
        assertEquals(CompassPoint.WEST, point(292.0))
        assertEquals(CompassPoint.NORTH_WEST, point(320.0))
    }
}
