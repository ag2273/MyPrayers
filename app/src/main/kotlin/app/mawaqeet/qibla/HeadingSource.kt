package app.mawaqeet.qibla

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/** Which way the top of the phone points, in degrees clockwise from true north, and whether the sensor trusts it. */
data class HeadingReading(val degrees: Double, val accurate: Boolean)

/** The phone's heading for the Qibla screen. A fake one in tests. */
interface HeadingSource {
    /** False on a phone with no compass: the screen then shows the bearing only. */
    val hasSensor: Boolean

    /**
     * Headings from true north at [latitude], [longitude] (for the magnetic declination there). The sensors are
     * listened to only while this flow is collected.
     */
    fun headings(latitude: Double, longitude: Double): Flow<HeadingReading>
}

/**
 * The heading from the rotation-vector sensor, or the accelerometer and magnetometer where there is none, corrected
 * from magnetic to true north with the platform's geomagnetic model.
 */
class SensorHeadings(private val context: Context) : HeadingSource {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val rotation: Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometer: Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer: Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    override val hasSensor: Boolean = rotation != null || (accelerometer != null && magnetometer != null)

    override fun headings(latitude: Double, longitude: Double): Flow<HeadingReading> = callbackFlow {
        val manager = sensors ?: run { close(); return@callbackFlow }
        val declination = GeomagneticField(latitude.toFloat(), longitude.toFloat(), 0f, System.currentTimeMillis()).declination
        val matrix = FloatArray(9)
        val remapped = FloatArray(9)
        val orientation = FloatArray(3)
        val gravity = FloatArray(3)
        val field = FloatArray(3)
        var haveGravity = false
        var haveField = false
        // The magnetometer's accuracy decides whether to ask for a figure-8 calibration.
        var accurate = true

        fun emitFrom(rotationMatrix: FloatArray) {
            val (x, y) = when (displayRotation()) {
                Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
            }
            SensorManager.remapCoordinateSystem(rotationMatrix, x, y, remapped)
            SensorManager.getOrientation(remapped, orientation)
            val magnetic = Math.toDegrees(orientation[0].toDouble())
            trySend(HeadingReading(((magnetic + declination) % 360.0 + 360.0) % 360.0, accurate))
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(matrix, event.values)
                        emitFrom(matrix)
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        event.values.copyInto(gravity, endIndex = 3)
                        haveGravity = true
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        event.values.copyInto(field, endIndex = 3)
                        haveField = true
                        accurate = event.accuracy.isAccurate()
                        if (haveGravity && SensorManager.getRotationMatrix(matrix, null, gravity, field)) emitFrom(matrix)
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD || sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    accurate = accuracy.isAccurate()
                }
            }
        }
        if (rotation != null) {
            manager.registerListener(listener, rotation, SensorManager.SENSOR_DELAY_UI)
            // Only for its accuracy: the rotation vector reports it poorly on many phones.
            magnetometer?.let { manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        } else {
            manager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
            manager.registerListener(listener, magnetometer, SensorManager.SENSOR_DELAY_UI)
        }
        awaitClose { manager.unregisterListener(listener) }
    }.conflate()

    @Suppress("DEPRECATION")
    private fun displayRotation(): Int = context.getSystemService(WindowManager::class.java)?.defaultDisplay?.rotation ?: Surface.ROTATION_0

    private fun Int.isAccurate() = this != SensorManager.SENSOR_STATUS_UNRELIABLE && this != SensorManager.SENSOR_STATUS_ACCURACY_LOW
}
