package app.mawaqeet.ui.qibla

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mawaqeet.R
import app.mawaqeet.core.CompassPoint
import app.mawaqeet.core.Location
import app.mawaqeet.core.Qibla
import app.mawaqeet.core.TurnSide
import app.mawaqeet.qibla.HeadingReading
import app.mawaqeet.qibla.HeadingSource
import app.mawaqeet.ui.LocalDisplayFormatter
import app.mawaqeet.ui.components.SubPage
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The Qibla from the stored [location] (never a new permission): a compass dial turning with the phone, the bearing
 * from true north, the distance to Makkah and a turn hint. Facing it (within 3°) says so and calls [onFacingQibla]
 * once on entering, a light vibration by default. Without a location it offers [onSetLocation]; without a heading
 * sensor it shows the bearing only. [headings] is collected only while the screen is visible. «المساجد القريبة»
 * calls [onMosques] in every case, since the maps app finds the user itself (#89).
 */
@Composable
fun QiblaScreen(
    location: Location?,
    headings: HeadingSource,
    onSetLocation: () -> Unit,
    onMosques: () -> Unit,
    onBack: () -> Unit,
    onFacingQibla: () -> Unit = rememberLightVibration(),
) {
    SubPage(stringResource(R.string.tool_qibla), onBack) {
        when {
            location == null -> NoLocationCard(onSetLocation)
            !headings.hasSensor -> NoSensorCard(Qibla.from(location.latitude, location.longitude))
            else -> Compass(location, headings, onFacingQibla)
        }
        NearbyMosquesButton(onMosques)
    }
}

/** «المساجد القريبة» and the line saying it opens in the maps app. */
@Composable
private fun NearbyMosquesButton(onMosques: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(onClick = onMosques, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(painterResource(R.drawable.ic_pin), contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.mosques_nearby), modifier = Modifier.padding(start = 8.dp))
        }
        Text(
            stringResource(R.string.mosques_opens_in_maps),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Compass(location: Location, source: HeadingSource, onFacingQibla: () -> Unit) {
    val qibla = remember(location) { Qibla.from(location.latitude, location.longitude) }
    val flow = remember(location, source) { source.headings(location.latitude, location.longitude) }
    val reading: HeadingReading? by flow.collectAsStateWithLifecycle(null)
    val turn = reading?.let { qibla.turn(it.degrees) }
    val onTarget = turn?.side == TurnSide.ON_TARGET
    val facing by rememberUpdatedState(onFacingQibla)
    LaunchedEffect(onTarget) { if (onTarget) facing() }
    val format = LocalDisplayFormatter.current
    val colors = MaterialTheme.colorScheme

    if (reading?.accurate == false) CalibrationCard()
    Dial(qibla.bearing, reading?.degrees ?: 0.0, onTarget)
    Text(
        when (turn?.side) {
            null -> stringResource(R.string.qibla_waiting)
            TurnSide.ON_TARGET -> stringResource(R.string.qibla_on_target)
            TurnSide.RIGHT -> stringResource(R.string.qibla_turn_right, format.number(turn.degrees))
            TurnSide.LEFT -> stringResource(R.string.qibla_turn_left, format.number(turn.degrees))
        },
        style = MaterialTheme.typography.headlineSmall,
        color = if (onTarget) colors.primary else colors.onSurface,
        fontWeight = if (onTarget) FontWeight.Bold else null,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    BearingAndDistance(qibla)
}

/** The bearing from true north and the distance to Makkah, under the dial or on the no-sensor card. */
@Composable
private fun BearingAndDistance(qibla: Qibla) {
    val format = LocalDisplayFormatter.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(R.string.qibla_bearing, format.number(qibla.bearing.roundToInt() % 360)),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.qibla_distance, format.number(qibla.distanceKm.roundToInt())),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** The four cardinal labels, at their bearings on the dial. */
private val CARDINALS = listOf(
    0.0 to R.string.qibla_dial_north,
    90.0 to R.string.qibla_dial_east,
    180.0 to R.string.qibla_dial_south,
    270.0 to R.string.qibla_dial_west,
)

/**
 * A compass dial turned so that the top of the phone is up: ticks, N/E/S/W, the Kaaba marker at the Qibla's bearing
 * and a fixed needle along the phone. A compass is never mirrored: east is clockwise from north in every language, so
 * the labels are placed in absolute coordinates, not start/end.
 */
@Composable
private fun Dial(bearing: Double, heading: Double, onTarget: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val description = stringResource(R.string.qibla_dial_description)
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, 300.dp)
        Box(Modifier.size(side).semantics(mergeDescendants = true) { contentDescription = description }) {
            val ring = colors.outlineVariant
            val tick = colors.onSurfaceVariant
            val needle = if (onTarget) colors.primary else colors.tertiary
            Canvas(Modifier.size(side)) {
                val radius = size.minDimension / 2
                val c = center
                drawCircle(ring, radius - 2.dp.toPx(), style = Stroke(2.dp.toPx()))
                rotate(-heading.toFloat(), c) {
                    for (deg in 0 until 360 step 15) {
                        val long = deg % 90 == 0
                        val inner = radius - (if (long) 20.dp else 12.dp).toPx()
                        rotate(deg.toFloat(), c) {
                            drawLine(tick, Offset(c.x, c.y - radius + 6.dp.toPx()), Offset(c.x, c.y - inner), strokeWidth = (if (long) 2.5.dp else 1.dp).toPx())
                        }
                    }
                }
                // The needle along the phone, from the centre to the top.
                val path = Path().apply {
                    moveTo(c.x, c.y - radius * 0.62f)
                    lineTo(c.x - 9.dp.toPx(), c.y)
                    lineTo(c.x + 9.dp.toPx(), c.y)
                    close()
                }
                drawPath(path, needle)
                drawCircle(needle, 6.dp.toPx(), c)
            }
            DialMarks(side.value, heading, kaabaAt = bearing) {
                CARDINALS.forEach { (_, label) ->
                    Text(
                        stringResource(label),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (label == R.string.qibla_dial_north) colors.primary else colors.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Box(
                    Modifier
                        .size(40.dp)
                        .background(if (onTarget) colors.primary else colors.tertiaryContainer, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_qibla),
                        contentDescription = stringResource(R.string.qibla_kaaba),
                        tint = if (onTarget) colors.onPrimary else colors.onTertiaryContainer,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

/**
 * Places its children around the dial's centre at their bearings (the cardinals, then the Kaaba marker at
 * [kaabaAt]), turned by -[heading], in absolute coordinates so a right-to-left layout never mirrors east and west.
 */
@Composable
private fun DialMarks(sideDp: Float, heading: Double, kaabaAt: Double, content: @Composable () -> Unit) {
    Layout(content, Modifier.size(sideDp.dp)) { children, constraints ->
        val placeables = children.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val side = sideDp.dp.roundToPx()
        layout(side, side) {
            placeables.forEachIndexed { i, p ->
                val (at, r) = if (i < CARDINALS.size) {
                    CARDINALS[i].first to side / 2f - 40.dp.toPx()
                } else {
                    kaabaAt to side / 2f - 72.dp.toPx()
                }
                val angle = Math.toRadians(at - heading)
                val x = side / 2f + r * sin(angle).toFloat() - p.width / 2f
                val y = side / 2f - r * cos(angle).toFloat() - p.height / 2f
                // place, not placeRelative: never mirrored.
                p.place(x.roundToInt(), y.roundToInt())
            }
        }
    }
}

@Composable
private fun InfoCard(@StringRes title: Int, body: String, action: (@Composable () -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.secondaryContainer, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, color = colors.onSecondaryContainer)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.onSecondaryContainer)
        if (action != null) Row(Modifier.padding(top = 6.dp)) { action() }
    }
}

@Composable
private fun CalibrationCard() = InfoCard(R.string.qibla_calibrate_title, stringResource(R.string.qibla_calibrate))

@Composable
private fun NoLocationCard(onSetLocation: () -> Unit) = InfoCard(R.string.qibla_no_location_title, stringResource(R.string.qibla_no_location)) {
    Button(onClick = onSetLocation, modifier = Modifier.widthIn(min = 48.dp)) { Text(stringResource(R.string.qibla_set_location)) }
}

@Composable
private fun NoSensorCard(qibla: Qibla) {
    val format = LocalDisplayFormatter.current
    InfoCard(
        R.string.qibla_no_sensor_title,
        stringResource(R.string.qibla_no_sensor, format.number(qibla.bearing.roundToInt() % 360), stringResource(qibla.direction.label)),
    )
    BearingAndDistance(qibla)
}

/** The direction's name: «الجنوب الشرقي», "south-east". */
@get:StringRes
private val CompassPoint.label: Int
    get() = when (this) {
        CompassPoint.NORTH -> R.string.compass_north
        CompassPoint.NORTH_EAST -> R.string.compass_north_east
        CompassPoint.EAST -> R.string.compass_east
        CompassPoint.SOUTH_EAST -> R.string.compass_south_east
        CompassPoint.SOUTH -> R.string.compass_south
        CompassPoint.SOUTH_WEST -> R.string.compass_south_west
        CompassPoint.WEST -> R.string.compass_west
        CompassPoint.NORTH_WEST -> R.string.compass_north_west
    }

/** One light tick, for facing the Qibla. Does nothing on a phone without a vibrator. */
@Composable
fun rememberLightVibration(): () -> Unit {
    val context = LocalContext.current
    return remember(context) { { vibrateLightly(context) } }
}

private fun vibrateLightly(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    } ?: return
    if (!vibrator.hasVibrator()) return
    val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
    } else {
        VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE)
    }
    vibrator.vibrate(effect)
}
