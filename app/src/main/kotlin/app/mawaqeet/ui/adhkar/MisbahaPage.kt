package app.mawaqeet.ui.adhkar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mawaqeet.R
import app.mawaqeet.core.Adhkar
import app.mawaqeet.ui.LocalDisplayFormatter
import app.mawaqeet.ui.components.SubPage
import app.mawaqeet.ui.theme.LocalSkin
import app.mawaqeet.ui.theme.NotoKufiArabic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** Settings → Language and display → Tasbeeh (1.4): the hand that counts, so the string hangs under its thumb. */
enum class TasbeehHand { RIGHT, LEFT }

/** What each round of 33 is for, as after the Prayer: 33 «سبحان الله», 33 «الحمد لله», 33 «الله أكبر». */
private val ROUNDS = listOf(R.string.misbaha_round_subhan, R.string.misbaha_round_hamd, R.string.misbaha_round_akbar)

/**
 * The Misbaha (1.5): the free Tasbeeh as a coke-wood string of beads. Each tap, or a pull of the thumb down the
 * string, moves one bead; a divider passes after every 33, and the chips name the round (33 «سبحان الله» · 33
 * «الحمد لله» · 33 «الله أكبر», then again). The string hangs on the side of the counting [hand] and today's total sits
 * on the other side. A wooden «تك» plays as the bead lands ([sound], switched from the title row); every bead
 * vibrates, the divider more. The total is the stored one ([Adhkar.tasbeeh]): this page counts ahead of the write.
 */
@Composable
internal fun MisbahaPage(
    adhkar: Adhkar,
    changes: StateFlow<Int>,
    hand: TasbeehHand,
    onChangeHand: () -> Unit,
    sound: Boolean,
    onSound: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val version by changes.collectAsState()
    val stored by produceState<Int?>(null, adhkar, version) { value = withContext(Dispatchers.IO) { adhkar.tasbeeh() } }
    // The bead moves on the tap; the write follows off the main thread and the stored total then confirms it.
    var pending by remember { mutableIntStateOf(0) }
    val total = stored?.let { (it + pending).coerceAtLeast(0) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val count = {
        pending += 1
        val next = (total ?: 0) + 1
        haptics.performHapticFeedback(if (next % BeadString.BEADS_PER_ROUND == 0) HapticFeedbackType.Confirm else HapticFeedbackType.SegmentTick)
        scope.launch(Dispatchers.IO) { adhkar.countTasbeeh(); withContext(Dispatchers.Main) { pending -= 1 } }
        Unit
    }
    val undo = {
        if ((total ?: 0) > 0) {
            pending -= 1
            scope.launch(Dispatchers.IO) { adhkar.uncountTasbeeh(); withContext(Dispatchers.Main) { pending += 1 } }
        }
    }
    val reset = { scope.launch(Dispatchers.IO) { adhkar.resetTasbeeh() }; Unit }
    val skin = LocalSkin.current
    SubPage(
        stringResource(R.string.tasbeeh_section),
        onBack,
        scrolls = false,
        actions = {
            IconToggleButton(
                checked = sound,
                onCheckedChange = onSound,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(skin.surface)
                    .border(1.dp, skin.line, RoundedCornerShape(14.dp)),
            ) {
                Icon(
                    painterResource(if (sound) R.drawable.ic_sound_on else R.drawable.ic_sound_off),
                    contentDescription = stringResource(R.string.misbaha_sound),
                    modifier = Modifier.size(20.dp),
                )
            }
        },
    ) {
        val shown = total ?: return@SubPage
        RoundChips(shown)
        Misbaha(shown, hand, sound, count, undo, Modifier.weight(1f).fillMaxWidth())
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(if (hand == TasbeehHand.RIGHT) R.string.tasbeeh_set_right else R.string.tasbeeh_set_left),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onChangeHand) { Text(stringResource(R.string.tasbeeh_hand_change)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = undo, enabled = shown > 0, border = BorderStroke(1.dp, skin.line), modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(painterResource(R.drawable.ic_undo), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.misbaha_undo), modifier = Modifier.padding(start = 6.dp))
                }
                OutlinedButton(onClick = reset, enabled = shown > 0, border = BorderStroke(1.dp, skin.line), modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.tasbeeh_reset))
                }
            }
        }
    }
}

/** The three rounds of 33; the current one stands out and the finished ones take the accent. */
@Composable
private fun RoundChips(total: Int) {
    val skin = LocalSkin.current
    val colors = MaterialTheme.colorScheme
    val round = total / BeadString.BEADS_PER_ROUND % ROUNDS.size
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ROUNDS.forEachIndexed { i, name ->
            val on = i == round
            val shape = RoundedCornerShape(50)
            Box(
                Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(if (on) skin.accentSoft else skin.surface)
                    .border(1.dp, if (on) colors.primary.copy(alpha = .45f) else skin.line, shape)
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                // The name only: the count beside the string says how far into the 33 it is.
                Text(
                    stringResource(name),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Medium),
                    color = when {
                        on -> colors.onSurface
                        i < round -> colors.primary
                        else -> colors.onSurfaceVariant
                    },
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** The string on the hand's side and the count on the other, laid out left to right whatever the language. */
@Composable
private fun Misbaha(total: Int, hand: TasbeehHand, sound: Boolean, onCount: () -> Unit, onUndo: () -> Unit, modifier: Modifier) {
    val format = LocalDisplayFormatter.current
    val contentDirection = LocalLayoutDirection.current
    val state = stringResource(R.string.tasbeeh_total, format.number(total))
    val countLabel = stringResource(R.string.adhkar_count_one)
    val undoLabel = stringResource(R.string.misbaha_undo)
    val hint = stringResource(R.string.tasbeeh_tap_hint)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            val string = @Composable {
                BeadStringView(
                    total, sound, onCount,
                    Modifier
                        .fillMaxHeight()
                        .weight(.42f)
                        .semantics(mergeDescendants = true) {
                            role = Role.Button
                            contentDescription = hint
                            stateDescription = state
                            liveRegion = LiveRegionMode.Polite
                            onClick(countLabel) { onCount(); true }
                            customActions = listOf(CustomAccessibilityAction(undoLabel) { onUndo(); true })
                        },
                )
            }
            val count = @Composable {
                CompositionLocalProvider(LocalLayoutDirection provides contentDirection) {
                    CountColumn(total, Modifier.weight(.58f))
                }
            }
            if (hand == TasbeehHand.RIGHT) { count(); string() } else { string(); count() }
        }
    }
}

@Composable
private fun CountColumn(total: Int, modifier: Modifier) {
    val format = LocalDisplayFormatter.current
    val colors = MaterialTheme.colorScheme
    val skin = LocalSkin.current
    val inRound = total % BeadString.BEADS_PER_ROUND
    val round = total / BeadString.BEADS_PER_ROUND % ROUNDS.size
    Column(
        modifier.clearAndSetSemantics {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            format.number(total),
            fontFamily = NotoKufiArabic,
            fontWeight = FontWeight.Medium,
            fontSize = 72.sp,
            maxLines = 1,
            // Kufi's tall line box would push the label away; trim it to the digits.
            style = LocalTextStyle.current.copy(
                lineHeight = 80.sp,
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            ),
        )
        Text(stringResource(R.string.tasbeeh_today), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        Text(stringResource(ROUNDS[round]), fontFamily = NotoKufiArabic, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = colors.primary)
        Text(
            stringResource(R.string.misbaha_of, format.number(inRound), format.number(BeadString.BEADS_PER_ROUND)),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in ROUNDS.indices) {
                val filled = when {
                    i < round -> 1f
                    i == round -> inRound / BeadString.BEADS_PER_ROUND.toFloat()
                    else -> 0f
                }
                Box(Modifier.width(22.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(skin.track)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(filled).background(colors.primary))
                }
            }
        }
    }
}

/**
 * The string itself: draws [BeadString] and runs its springs frame by frame. A tap counts; a pull down the string
 * moves the bead with the thumb and counts when it is let go far or fast enough.
 */
@Composable
private fun BeadStringView(total: Int, sound: Boolean, onCount: () -> Unit, modifier: Modifier) {
    val dark = LocalSkin.current.dark
    val context = LocalContext.current
    val click = remember { WoodClick(context.applicationContext) }
    DisposableEffect(click) { onDispose { click.close() } }
    val soundOn by rememberUpdatedState(sound)
    val count by rememberUpdatedState(onCount)
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val radius = with(density) { min(maxWidth.toPx() * .3f, 34.dp.toPx()) }
        val height = with(density) { maxHeight.toPx() }
        val string = remember(radius, height) { BeadString(radius, height, total) }
        SideEffect { string.onStrike = { divider -> if (soundOn) click.play(divider) } }
        var frame by remember { mutableLongStateOf(0L) }
        var kick by remember { mutableIntStateOf(0) }
        LaunchedEffect(string, total, kick) {
            var last = withFrameMillis { it }
            if (string.count != total) string.count(total, last)
            while (true) {
                val now = withFrameMillis { it }
                val busy = string.step(now, (now - last) / 1000f)
                last = now
                frame++
                if (!busy) break
            }
        }
        val size = (radius * 2).roundToInt()
        val art by produceState<BeadArt?>(null, size, dark) {
            value = withContext(Dispatchers.Default) {
                BeadArt(
                    List(WoodBeadArt.WOOD_VARIANTS) { WoodBeadArt.bead(size, it, dark) },
                    List(3) { WoodBeadArt.divider((radius * 2 * BeadString.DIVIDER_LENGTH).roundToInt(), it, dark) },
                )
            }
        }
        val cord = if (dark) Color(0xFF2A1A10) to Color(0xFF8A6A4C) else Color(0xFF3B2516) to Color(0xFF9B7653)
        val glow = LocalSkin.current.amber
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .pointerInput(string) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        var moved = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val dy = change.position.y - down.position.y
                            if (!moved && abs(dy) > viewConfiguration.touchSlop) moved = true
                            if (moved) {
                                change.consume()
                                string.drag(dy, tracker.calculateVelocity().y)
                                kick++
                            }
                            if (!change.pressed) break
                        }
                        if (!moved) count()
                        else if (string.release(0L)) count() else kick++
                    }
                },
        ) {
            if (frame < 0) return@Canvas // read so each spring step redraws
            val cx = this.size.width / 2
            drawLine(cord.first, Offset(cx, 0f), Offset(cx, this.size.height), strokeWidth = 3.2.dp.toPx(), cap = StrokeCap.Round)
            drawLine(
                cord.second, Offset(cx, 0f), Offset(cx, this.size.height), strokeWidth = 1.4.dp.toPx(), alpha = .8f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.2.dp.toPx(), 2.6.dp.toPx())),
            )
            drawCircle(
                Brush.radialGradient(listOf(glow.copy(alpha = if (dark) .26f else .3f), Color.Transparent), Offset(cx, string.thumbY), radius * 2.3f),
                radius * 2.3f, Offset(cx, string.thumbY),
            )
            art?.let { a ->
                for (b in string.beads) {
                    val image = if (b.isDivider) a.dividers[b.position % a.dividers.size] else a.beads[b.position % a.beads.size]
                    // the image carries a margin for its shadow round the bead
                    val side = radius * 2 * (if (b.isDivider) BeadString.DIVIDER_LENGTH else 1f) * (1 + 2 * WoodBeadArt.SHADOW_PAD)
                    val dst = side.roundToInt()
                    drawImage(
                        image,
                        dstOffset = IntOffset((cx - side / 2).roundToInt(), (b.y - side / 2).roundToInt()),
                        dstSize = IntSize(dst, dst),
                        filterQuality = FilterQuality.Medium,
                    )
                }
            }
            // The string fades out at both ends of the view.
            drawRect(
                Brush.verticalGradient(0f to Color.Transparent, .13f to Color.Black, .87f to Color.Black, 1f to Color.Transparent),
                blendMode = BlendMode.DstIn,
            )
        }
    }
}

private class BeadArt(val beads: List<ImageBitmap>, val dividers: List<ImageBitmap>)
