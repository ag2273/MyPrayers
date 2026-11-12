package app.mawaqeet.core

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The Qibla from one place: the initial great-circle [bearing] to the Kaaba in degrees clockwise from true north
 * (0 until 360), and the great-circle [distanceKm] to it.
 */
data class Qibla(val bearing: Double, val distanceKm: Double) {
    /** The nearest of the eight compass points to [bearing], for a phone without a heading sensor. */
    val direction: CompassPoint get() = CompassPoint.entries[((bearing + 22.5) / 45.0).toInt() % 8]

    /**
     * Which way to turn a phone facing [heading] (degrees from true north) to face the Qibla, the short way round.
     * Within [ON_TARGET_DEGREES] either side it already faces it.
     */
    fun turn(heading: Double): QiblaTurn {
        // -180 until 180: positive is clockwise, to the right.
        val delta = ((bearing - heading) % 360.0 + 540.0) % 360.0 - 180.0
        return when {
            abs(delta) <= ON_TARGET_DEGREES -> QiblaTurn(0, TurnSide.ON_TARGET)
            delta > 0 -> QiblaTurn(delta.roundToInt(), TurnSide.RIGHT)
            else -> QiblaTurn((-delta).roundToInt(), TurnSide.LEFT)
        }
    }

    companion object {
        /** How far off the Qibla a phone may point and still face it. */
        const val ON_TARGET_DEGREES = 3.0

        /** The Kaaba, Makkah. */
        const val KAABA_LATITUDE = 21.4225
        const val KAABA_LONGITUDE = 39.8262

        /** The mean Earth radius (IUGG). */
        private const val EARTH_RADIUS_KM = 6_371.0088

        /** The Qibla from [latitude], [longitude] in degrees. */
        fun from(latitude: Double, longitude: Double): Qibla {
            val p1 = Math.toRadians(latitude)
            val p2 = Math.toRadians(KAABA_LATITUDE)
            val dl = Math.toRadians(KAABA_LONGITUDE - longitude)
            val y = sin(dl) * cos(p2)
            val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
            val bearing = (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
            val h = sin((p2 - p1) / 2).let { it * it } + cos(p1) * cos(p2) * sin(dl / 2).let { it * it }
            val distance = 2 * EARTH_RADIUS_KM * asin(sqrt(h.coerceIn(0.0, 1.0)))
            return Qibla(bearing, distance)
        }
    }
}

/** The turn hint: «لفّ ١٦° يمينًا». [degrees] is 0 when [side] is [TurnSide.ON_TARGET]. */
data class QiblaTurn(val degrees: Int, val side: TurnSide)

enum class TurnSide { RIGHT, LEFT, ON_TARGET }

/** The eight compass points, clockwise from north. */
enum class CompassPoint { NORTH, NORTH_EAST, EAST, SOUTH_EAST, SOUTH, SOUTH_WEST, WEST, NORTH_WEST }
