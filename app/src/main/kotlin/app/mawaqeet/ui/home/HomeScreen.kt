package app.mawaqeet.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import app.mawaqeet.ui.theme.LocalSkin
import app.mawaqeet.ui.theme.NotoKufiArabic
import app.mawaqeet.ui.theme.SkyPhase
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.foundation.background
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import app.mawaqeet.core.AdhanSoundChoice
import app.mawaqeet.core.AlertSettings
import app.mawaqeet.core.PrayerAlertSettings
import app.mawaqeet.ui.components.Card
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.Constraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import app.mawaqeet.core.LogState
import app.mawaqeet.core.PrayerDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.Immutable
import app.mawaqeet.R
import app.mawaqeet.ui.LocalDisplayFormatter
import app.mawaqeet.core.DisplayDay
import app.mawaqeet.core.HighLatitudeAdjustment
import app.mawaqeet.ui.settings.label
import app.mawaqeet.core.Prayer
import app.mawaqeet.core.PrayerCore
import app.mawaqeet.core.PrayerTime
import app.mawaqeet.core.RamadanState
import kotlinx.coroutines.delay
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

/**
 * Today's Prayer Times and Sunrise, the next Prayer highlighted, and a live countdown to it. Tapping a Prayer marks
 * or unmarks it in the Prayer Log of the current Prayer Day.
 */
@Composable
fun HomeScreen(
    core: PrayerCore,
    clock: Clock,
    logChanges: StateFlow<Int>,
    locationName: String,
    /** The Adhan Alert of each Prayer, shown as a bell on its row. */
    alerts: AlertSettings = AlertSettings(),
    /** Opens the city search, from the location chip. */
    onOpenLocation: () -> Unit = {},
    /** A short hint under the location (e.g. the time zone changed), tapping it calls [onLocationHint]. */
    locationHint: String? = null,
    onLocationHint: () -> Unit = {},
    /** Shows the dismissible Fix alerts tip (#29) until the user taps Done. */
    showFixAlertsTip: Boolean = false,
    onOpenFixAlerts: () -> Unit = {},
    onFixAlertsDone: () -> Unit = {},
    /** The tools row under the Next Prayer card, or null for none. */
    tools: (@Composable () -> Unit)? = null,
    /** Opens the Hijri Calendar, from the date line; null leaves the date line plain. */
    onOpenCalendar: (() -> Unit)? = null,
) {
    // Re-query the core every second: the countdown ticks and the display day moves on at Fajr.
    var now by remember { mutableStateOf(clock.instant()) }
    LaunchedEffect(core, clock) {
        while (true) {
            now = clock.instant()
            delay(1000 - now.toEpochMilli() % 1000)
        }
    }
    val day = remember(core, now) { core.displayDay() }
    val ramadan = remember(core, now) { core.ramadanState(now) }
    // The log reads the database, so query it off the main thread, again whenever the log changes or a Prayer
    // Time arrives (the next Prayer moves on, so markability and the Prayer Day may have changed).
    val changes by logChanges.collectAsState()
    val prayerDay by produceState<PrayerDay?>(null, core, changes, day.next) {
        value = withContext(Dispatchers.IO) { core.currentPrayerDay() }
    }
    // Null until read, so the hint never flashes for a user who has marked before.
    val hasAnyMark by produceState<Boolean?>(null, core, changes) {
        value = withContext(Dispatchers.IO) { core.hasAnyMark() }
    }
    val scope = rememberCoroutineScope()
    val tip: (@Composable () -> Unit)? = if (showFixAlertsTip) {
        { FixAlertsTip(onOpenFixAlerts, onFixAlertsDone) }
    } else {
        null
    }
    HomeContent(
        day, now, ramadan, prayerDay, alerts, locationHint, onLocationHint, tip, tools,
        showLogHint = hasAnyMark == false,
        onOpenCalendar = onOpenCalendar,
    ) { prayer ->
        val logDay = prayerDay
        if (logDay != null && logDay.isMarkable(prayer)) {
            scope.launch(Dispatchers.IO) {
                if (logDay.stateOf(prayer) == LogState.PRAYED) {
                    core.unmark(logDay.date, prayer)
                } else {
                    core.mark(logDay.date, prayer)
                }
            }
        }
    }
}

/**
 * Today in the "A Day That Breathes" design (1.4): the hero (what comes next, its name and the arc that fills toward
 * it, the countdown inside), the dates as chips (the Hijri one opens the calendar), the Tools Row, then the day as a
 * timeline of stops with each Prayer's bell, time and mark. The page fits its screen exactly: the timeline's rows take
 * spare height first, then the arc grows.
 */
@Composable
private fun HomeContent(
    day: DisplayDay,
    now: Instant,
    ramadan: RamadanState,
    prayerDay: PrayerDay?,
    alerts: AlertSettings,
    locationHint: String?,
    onLocationHint: () -> Unit,
    tip: (@Composable () -> Unit)?,
    tools: (@Composable () -> Unit)?,
    showLogHint: Boolean,
    onOpenCalendar: (() -> Unit)?,
    onToggle: (Prayer) -> Unit,
) {
    FillingPage { size ->
        if (locationHint != null) {
            TextButton(onClick = onLocationHint) { Text(locationHint) }
        }
        tip?.invoke()
        day.highLatitude?.let { HighLatitudeNote(it) }
        // After Isha, until Fajr, the hero names the start of the Last Third of the Night (#96).
        val afterIsha = day.next.prayer == Prayer.FAJR && !day.timeOf(Prayer.ISHA).toInstant().isAfter(now)
        val lastThird = day.lastThirdOfNight.takeIf { afterIsha }
        Hero(
            HeroModel.of(day, now, ramadan, lastThird),
            now = now,
            night = SkyPhase.of(day, now) == SkyPhase.NIGHT,
            size = size,
            modifier = Modifier.layoutId(Fill.TakesTheRest).semantics { todaySize = size },
        )
        DateChips(day, ramadan, onOpenCalendar)
        tools?.invoke()
        val isNextToday = day.next.time.toLocalDate() == day.date
        // In Ramadan the hero counts to iftar or the end of suhoor: the next Prayer's own countdown sits on its stop.
        val stopCountdown = (ramadan as? RamadanState.Active)?.takeIf { isNextToday && it.countsTo != day.next.time }
            ?.let { LocalDisplayFormatter.current.countdown(Duration.between(now, day.next.time.toInstant())) }
        SharedHeightColumn(Modifier.layoutId(Fill.Rows(count = day.prayerTimes.size + 1)).fillMaxHeight()) {
            day.prayerTimes.forEach { prayerTime ->
                val prayer = prayerTime.prayer
                val current = isNextToday && prayerTime == day.next
                Stop(
                    name = prayerName(prayerTime),
                    time = prayerTime.time,
                    current = current,
                    past = !current && !prayerTime.time.toInstant().isAfter(now),
                    first = prayer == Prayer.FAJR,
                    last = prayer == Prayer.ISHA,
                    countdown = stopCountdown.takeIf { current },
                    alert = alerts.effective(prayer),
                    prayer = prayer,
                    // Between midnight and Fajr the marks are still the previous Prayer Day's (late Isha).
                    log = prayerDay?.let { LogCell(it.stateOf(prayer), it.isMarkable(prayer)) },
                    onClick = { onToggle(prayer) },
                )
                if (prayer == Prayer.FAJR) {
                    // Sunrise is not a Prayer: never markable, and no alert.
                    Stop(
                        name = stringResource(R.string.sunrise),
                        time = day.sunrise,
                        current = false,
                        past = !day.sunrise.toInstant().isAfter(now),
                        first = false,
                        last = false,
                    )
                }
            }
        }
        if (showLogHint) {
            Text(
                stringResource(R.string.home_log_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            )
        }
    }
}


/**
 * Today's size class (#101), chosen from the height Today has: [REGULAR] is the original design, kept on normal and big
 * phones; [COMPACT] tightens the header and the Next Prayer card so a short screen still fits without scrolling.
 */
enum class TodaySize { REGULAR, COMPACT }

/** The size class Today chose, on its header (for tests). */
val TodaySizeKey = SemanticsPropertyKey<TodaySize>("TodaySize")
private var SemanticsPropertyReceiver.todaySize by TodaySizeKey

/** How a child of [FillingPage] takes the page's spare height. Children without one keep their own height. */
private sealed interface Fill {
    /** The Prayer Time rows: they share the spare height evenly, up to [MAX_ROW_HEIGHT] each. */
    data class Rows(val count: Int) : Fill

    /** The Next Prayer card: whatever the rows leave. */
    data object TakesTheRest : Fill
}

private val MAX_ROW_HEIGHT = 50.dp
private val PAGE_GAP = 6.dp
private val PAGE_GAP_COMPACT = 4.dp
private val PAGE_PADDING_H = 16.dp
private val PAGE_PADDING_V = 8.dp

/**
 * Today's page: a column that fills its viewport exactly when its content fits. Spare height goes first to the
 * [Fill.Rows] child (each row up to [MAX_ROW_HEIGHT]), then to the [Fill.TakesTheRest] child.
 *
 * The content starts at [TodaySize.REGULAR] and switches to [TodaySize.COMPACT] when that is taller than the viewport;
 * it switches back once the regular content would fit again (a taller window, the log hint gone). When even the compact
 * content does not fit (a large font, a landscape phone), nothing grows and the page scrolls.
 */
@Composable
private fun FillingPage(content: @Composable (TodaySize) -> Unit) {
    var size by remember { mutableStateOf(TodaySize.REGULAR) }
    // What the last measures found, to know when the regular content fits again.
    val memory = remember { FitMemory() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewport = constraints.maxHeight
        // The size the children were composed at; the measure below must not read the state, which can be ahead of them.
        val composed = size
        Layout(
            { content(composed) },
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PAGE_PADDING_H, vertical = PAGE_PADDING_V)
                .onPlaced { memory.switchTo?.let { size = it } },
        ) { children, constraints ->
            val width = constraints.maxWidth
            val gap = (if (composed == TodaySize.REGULAR) PAGE_GAP else PAGE_GAP_COMPACT).roundToPx()
            val own = Constraints(maxWidth = width)
            val rows = children.firstOrNull { it.layoutId is Fill.Rows }
            val rest = children.firstOrNull { it.layoutId == Fill.TakesTheRest }
            val rowsMin = rows?.minIntrinsicHeight(width) ?: 0
            val restMin = rest?.minIntrinsicHeight(width) ?: 0
            val fixed = children.filter { it !== rows && it !== rest }.associateWith { it.measure(own) }
            val used = fixed.values.sumOf { it.height } + rowsMin + restMin + gap * (children.size - 1).coerceAtLeast(0)
            val target = if (viewport == Constraints.Infinity) 0 else viewport - PAGE_PADDING_V.roundToPx() * 2
            val switchTo = when {
                viewport == Constraints.Infinity -> null
                composed == TodaySize.REGULAR -> {
                    memory.regularHeight = used
                    memory.extra = null
                    if (used > target) TodaySize.COMPACT else null
                }
                else -> {
                    val extra = memory.extra ?: (memory.regularHeight - used).coerceAtLeast(0).also { memory.extra = it }
                    if (used + extra <= target) TodaySize.REGULAR else null
                }
            }
            // Switched once placed (a state write in the measure would loop): the next frame is at the other size.
            memory.switchTo = switchTo
            val spare = (target - used).coerceAtLeast(0)
            val rowsCap = (rows?.layoutId as? Fill.Rows)?.let { (MAX_ROW_HEIGHT.roundToPx() * it.count + 8.dp.roundToPx() - rowsMin).coerceAtLeast(0) } ?: 0
            val rowsExtra = if (rest == null) spare else spare.coerceAtMost(rowsCap)
            fun exactly(height: Int) = Constraints(minWidth = 0, maxWidth = width, minHeight = height, maxHeight = height)
            val placeables = children.map {
                when (it) {
                    rows -> it.measure(exactly(rowsMin + rowsExtra))
                    rest -> it.measure(exactly(restMin + spare - rowsExtra))
                    else -> fixed.getValue(it)
                }
            }
            val height = placeables.sumOf { it.height } + gap * (placeables.size - 1).coerceAtLeast(0)
            layout(width, height) {
                var y = 0
                placeables.forEach {
                    it.placeRelative(0, y)
                    y += it.height + gap
                }
            }
        }
    }
}

/** What [FillingPage] measured, kept across passes (not state: changing it must not relayout). */
private class FitMemory {
    /** The height the content last needed at the regular sizes. */
    var regularHeight = 0

    /** How much taller the regular content is than the compact one, measured once after each switch. */
    var extra: Int? = null

    /** The size the last measure found the page should be at, if not the one it was composed at. */
    var switchTo: TodaySize? = null
}

/**
 * A column that gives each child its own minimum height plus an even share of whatever height is left over, so taller
 * rows (a wrapped name at a large font) are never squeezed to fit the others.
 */
@Composable
private fun SharedHeightColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { children, constraints ->
        val width = constraints.maxWidth
        val mins = children.map { it.minIntrinsicHeight(width) }
        val spare = if (constraints.hasBoundedHeight) (constraints.maxHeight - mins.sum()).coerceAtLeast(0) else 0
        val share = if (children.isEmpty()) 0 else spare / children.size
        val placeables = children.mapIndexed { i, child ->
            val h = mins[i] + share + if (i < spare - share * children.size) 1 else 0
            child.measure(Constraints(minWidth = width, maxWidth = width, minHeight = h, maxHeight = h))
        }
        val height = placeables.sumOf { it.height }.coerceAtLeast(constraints.minHeight)
        layout(width, height) {
            var y = 0
            placeables.forEach {
                it.placeRelative(0, y)
                y += it.height
            }
        }
    }
}

/** On alarm-killing phones: a short tip pointing to Fix alerts, until the user taps Done. */
@Composable
private fun FixAlertsTip(onOpen: () -> Unit, onDone: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.secondaryContainer, RoundedCornerShape(18.dp))
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(painterResource(R.drawable.ic_warning), contentDescription = null, tint = colors.onSecondaryContainer, modifier = Modifier.size(20.dp))
            Text(
                stringResource(R.string.fix_alerts_tip),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSecondaryContainer,
            )
        }
        Row(Modifier.align(Alignment.End)) {
            TextButton(onClick = onDone) { Text(stringResource(R.string.done), color = colors.onSecondaryContainer) }
            TextButton(onClick = onOpen) { Text(stringResource(R.string.fix_alerts), color = colors.onSecondaryContainer, fontWeight = FontWeight.Bold) }
        }
    }
}

/** Says that a high-latitude rule, not the method's angle, set today's Fajr or Isha (#20). */
@Composable
private fun HighLatitudeNote(adjustment: HighLatitudeAdjustment) {
    val rule = stringResource(adjustment.rule.label)
    Text(
        stringResource(if (adjustment.polar) R.string.high_latitude_polar_note else R.string.high_latitude_note, rule),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}


/** What a row shows of the Prayer Log. */
private data class LogCell(val state: LogState, val markable: Boolean)

/**
 * What the hero says: [eyebrow] over the big [title] (the next Prayer, or in Ramadan «الإفطار» / «السحور»), the
 * countdown to [target] from [start] (the arc's span) with its [label], and under it [at]: the target's time, or after
 * Isha the Last Third of the Night.
 */
@Immutable
private data class HeroModel(
    val eyebrow: String,
    val title: String,
    val start: Instant,
    val target: Instant,
    val label: String,
    val at: String,
) {
    companion object {
        @Composable
        fun of(day: DisplayDay, now: Instant, ramadan: RamadanState, lastThird: ZonedDateTime?): HeroModel {
            val format = LocalDisplayFormatter.current
            val lastThirdText = lastThird?.let {
                stringResource(if (it.toInstant().isAfter(now)) R.string.home_last_third else R.string.home_last_third_began, format.time(it))
            }
            return when (ramadan) {
                is RamadanState.Fasting -> HeroModel(
                    eyebrow = stringResource(R.string.ramadan_day, format.number(ramadan.fastingDayNumber)),
                    title = stringResource(R.string.home_iftar),
                    start = ramadan.imsak.toInstant(),
                    target = ramadan.maghrib.toInstant(),
                    label = stringResource(R.string.home_until_iftar),
                    at = format.time(ramadan.maghrib),
                )
                is RamadanState.Suhoor -> {
                    val maghrib = ramadan.maghrib.toInstant().takeUnless { it.isAfter(now) } ?: day.timeOf(Prayer.MAGHRIB).toInstant()
                    HeroModel(
                        eyebrow = stringResource(R.string.ramadan_day, format.number(ramadan.fastingDayNumber)),
                        title = stringResource(R.string.ramadan_suhoor),
                        start = maghrib,
                        target = ramadan.imsak.toInstant(),
                        label = stringResource(R.string.home_until_suhoor_end),
                        at = lastThirdText ?: format.time(ramadan.imsak),
                    )
                }
                RamadanState.None -> {
                    val next = day.next
                    val previous = day.prayerTimes.lastOrNull { !it.time.toInstant().isAfter(now) } ?: day.previousIsha
                    HeroModel(
                        eyebrow = stringResource(R.string.next_prayer),
                        title = prayerName(next),
                        start = previous.time.toInstant(),
                        target = next.time.toInstant(),
                        label = stringResource(R.string.home_until_adhan),
                        at = lastThirdText ?: format.time(next.time),
                    )
                }
            }
        }
    }
}

/** The hero's type sizes: the design's on normal and big phones, a step smaller on short screens. */
@Composable
private fun heroTitleStyle(size: TodaySize) = when (size) {
    TodaySize.REGULAR -> MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold)
    TodaySize.COMPACT -> MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
}

private val ARC_MIN_REGULAR = 150.dp
private val ARC_MIN_COMPACT = 120.dp
private val ARC_MAX = 260.dp

/**
 * The hero: the eyebrow and title, then the arc, which takes whatever height the page gives the hero (between its
 * minimum and [ARC_MAX], and never wider than 64% of the width). TalkBack reads it as one item.
 */
@Composable
private fun Hero(model: HeroModel, now: Instant, night: Boolean, size: TodaySize, modifier: Modifier = Modifier) {
    val format = LocalDisplayFormatter.current
    val countdown = format.countdown(Duration.between(now, model.target).coerceAtLeast(Duration.ZERO))
    val minArc = if (size == TodaySize.REGULAR) ARC_MIN_REGULAR else ARC_MIN_COMPACT
    Layout(
        content = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // A short screen drops the eyebrow and moves the title into the ring, as the design does under 800px.
                if (size == TodaySize.REGULAR) {
                    Text(model.eyebrow, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(model.title, style = heroTitleStyle(size), textAlign = TextAlign.Center)
                }
            }
            Arc(model, now, countdown, night, titleInside = size == TodaySize.COMPACT)
        },
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        measurePolicy = object : MeasurePolicy {
            override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
                val width = constraints.maxWidth
                val header = measurables[0].measure(Constraints(maxWidth = width))
                val gap = 6.dp.roundToPx()
                val room = if (constraints.hasBoundedHeight) constraints.maxHeight - header.height - gap else Int.MAX_VALUE
                val largest = minOf(ARC_MAX.roundToPx(), (width * 0.64f).toInt())
                val arc = room.coerceIn(minOf(minArc.roundToPx(), largest), largest)
                val arcPlaceable = measurables[1].measure(Constraints.fixed(arc, arc))
                val height = maxOf(constraints.minHeight, header.height + gap + arc)
                // Centred together in the height the page gave.
                val top = (height - header.height - gap - arc) / 2
                return layout(width, height) {
                    header.placeRelative((width - header.width) / 2, top)
                    arcPlaceable.placeRelative((width - arc) / 2, top + header.height + gap)
                }
            }

            override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
                measurables[0].minIntrinsicHeight(width) + 6.dp.roundToPx() + minOf(minArc.roundToPx(), (width * 0.64f).toInt())

            override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
                minIntrinsicHeight(measurables, width)
        },
    )
}

/**
 * The ring: a faint track, the amber arc filled from the top as the span passes, and the sun (a pale moon at night) at
 * its tip. Inside: the countdown, what it counts to, and [HeroModel.at]. The type scales with the ring.
 */
@Composable
private fun Arc(model: HeroModel, now: Instant, countdown: String, night: Boolean, titleInside: Boolean) {
    val skin = LocalSkin.current
    val span = Duration.between(model.start, model.target).toMillis().coerceAtLeast(1_000)
    val fraction = (Duration.between(model.start, now).toMillis().toFloat() / span).coerceIn(0f, 1f)
    val dot = if (night) Color(0xFFCFE1DC) else skin.amber
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.03f
            val radius = size.minDimension / 2 - stroke * 2.8f
            val center = Offset(size.width / 2, size.height / 2)
            val topLeft = Offset(center.x - radius, center.y - radius)
            val box = Size(radius * 2, radius * 2)
            drawArc(skin.track, 0f, 360f, useCenter = false, topLeft = topLeft, size = box, style = Stroke(stroke))
            drawArc(skin.amber, -90f, 360f * fraction, useCenter = false, topLeft = topLeft, size = box, style = Stroke(stroke, cap = StrokeCap.Round))
            val angle = Math.toRadians(-90.0 + 360.0 * fraction)
            val tip = Offset(center.x + radius * cos(angle).toFloat(), center.y + radius * sin(angle).toFloat())
            drawCircle(Brush.radialGradient(listOf(dot.copy(alpha = 0.55f), Color.Transparent), tip, stroke * 2.6f), stroke * 2.6f, tip)
            drawCircle(dot, stroke * 1.0f, tip)
        }
        // The type grows with the font size up to 130%, then stays: past that it would no longer fit the ring.
        val scale = LocalDensity.current.run { minOf(fontScale, 1.3f) / fontScale }
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val ring = maxWidth.value
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (titleInside) {
                    val title = ((ring * 0.12f).coerceIn(14f, 22f) * scale).sp
                    Text(model.title, fontFamily = NotoKufiArabic, fontWeight = FontWeight.Bold, fontSize = title, lineHeight = title * 1.3f, maxLines = 1)
                }
                Text(
                    countdown,
                    fontFamily = NotoKufiArabic,
                    fontWeight = FontWeight.Bold,
                    fontSize = ((ring * 0.135f).coerceIn(20f, 36f) * scale).sp,
                    lineHeight = ((ring * 0.16f).coerceIn(24f, 44f) * scale).sp,
                    maxLines = 1,
                    style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                )
                val small = ((ring * 0.056f).coerceIn(10f, 14f) * scale).sp
                // In the small ring the title takes the label's line.
                if (!titleInside) Text(model.label, fontSize = small, lineHeight = small * 1.3f, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Text(
                    model.at,
                    fontFamily = NotoKufiArabic,
                    fontSize = small,
                    lineHeight = small * 1.4f,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** The dates as chips under the hero: Imsak in Ramadan, the Gregorian date, and the Hijri one, which opens the calendar. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DateChips(day: DisplayDay, ramadan: RamadanState, onOpenCalendar: (() -> Unit)?) {
    val format = LocalDisplayFormatter.current
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (ramadan is RamadanState.Active) {
            Chip(stringResource(R.string.ramadan_imsak_at, format.time(ramadan.imsak)), highlighted = true)
        }
        // In Ramadan the Imsak chip joins the row, so the date drops its weekday to keep one line.
        val arabic = LocalConfiguration.current.locales[0].language == "ar"
        Chip(if (ramadan is RamadanState.Active) format.dayMonth(day.date) else "${format.weekday(day.date)}${if (arabic) "، " else ", "}${format.dayMonth(day.date)}")
        Chip(
            // With the Imsak chip in the row the Hijri date drops its year too: «١٥ رمضان».
            if (ramadan is RamadanState.Active) format.hijriDayMonth(day.hijriDate) else format.hijriDate(day.hijriDate),
            icon = R.drawable.ic_calendar.takeIf { onOpenCalendar != null },
            onClick = onOpenCalendar,
            clickLabel = stringResource(R.string.calendar_open_description),
        )
    }
}

@Composable
private fun Chip(text: String, highlighted: Boolean = false, icon: Int? = null, onClick: (() -> Unit)? = null, clickLabel: String? = null) {
    val skin = LocalSkin.current
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .heightIn(min = if (onClick != null) 48.dp else 36.dp)
            .padding(vertical = if (onClick != null) 6.dp else 0.dp)
            .clip(shape)
            .background(skin.surface)
            .border(1.dp, if (highlighted) skin.amber.copy(alpha = 0.6f) else skin.line, shape)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Normal),
            color = if (highlighted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val StopShape = RoundedCornerShape(18.dp)

/**
 * One stop of the day's timeline: the dot on the line, the name (in Ramadan the current one also counts down to its
 * adhan), the bell, the time, and the Prayer Log mark. The next Prayer's stop is lifted on a near-solid card with an
 * amber dot; passed stops are dimmed. A Prayer's whole stop toggles its mark (a 48dp target); Sunrise has none.
 */
@Composable
private fun Stop(
    name: String,
    time: ZonedDateTime,
    current: Boolean,
    past: Boolean,
    first: Boolean,
    last: Boolean,
    countdown: String? = null,
    alert: PrayerAlertSettings? = null,
    prayer: Prayer? = null,
    log: LogCell? = null,
    onClick: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val skin = LocalSkin.current
    val stateText = log?.let { stringResource(stateDescriptionOf(it)) }
    val dim = if (past) 0.6f else 1f
    val prayed = log?.state == LogState.PRAYED
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (current) {
                    Modifier
                        .shadow(14.dp, StopShape, ambientColor = Color(0x29134A44), spotColor = Color(0x29134A44))
                        .clip(StopShape)
                        .background(skin.surfaceStrong)
                        .border(1.dp, skin.line, StopShape)
                } else {
                    Modifier.clip(StopShape)
                },
            )
            .then(
                if (log == null) {
                    Modifier
                } else {
                    // Stays focusable and tappable when not yet due so TalkBack reads the reason; the tap does nothing.
                    Modifier
                        .clickable(role = Role.Checkbox, onClick = onClick)
                        .semantics {
                            stateDescription = stateText.orEmpty()
                            toggleableState = ToggleableState(prayed)
                        }
                },
            )
            // A Prayer's stop is a 48dp tap target; Sunrise is never tapped, so it may be shorter.
            .heightIn(min = if (log == null) 36.dp else 48.dp)
            .padding(start = 0.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TimelineDot(current = current, done = prayed, first = first, last = last)
        Column(Modifier.weight(1f).alpha(dim)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = NotoKufiArabic, fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal),
            )
            if (countdown != null) {
                Text(stringResource(R.string.countdown, countdown), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
        }
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            if (alert != null && prayer != null) AlertIcon(prayer, alert, if (current) colors.onSurface else colors.onSurfaceVariant)
        }
        Text(
            text = LocalDisplayFormatter.current.time(time),
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = NotoKufiArabic, fontFeatureSettings = "tnum"),
            color = if (current) colors.onSurface else colors.onSurfaceVariant,
            modifier = Modifier.alpha(dim),
        )
        // Sunrise gets an empty slot so its time lines up with the Prayers'.
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            if (log != null) LogMark(log.state)
        }
    }
}

/** The timeline's thread through the stop and its dot: amber with a halo on the next Prayer, filled once prayed. */
@Composable
private fun TimelineDot(current: Boolean, done: Boolean, first: Boolean, last: Boolean) {
    val skin = LocalSkin.current
    val teal = MaterialTheme.colorScheme.primary
    Canvas(Modifier.width(34.dp).fillMaxHeight().heightIn(min = 40.dp)) {
        val x = size.width / 2 + 2.dp.toPx()
        val cy = size.height / 2
        val line = 1.dp.toPx()
        if (!first) drawLine(skin.line, Offset(x, 0f), Offset(x, cy), line)
        if (!last) drawLine(skin.line, Offset(x, cy), Offset(x, size.height), line)
        val r = 5.5.dp.toPx()
        when {
            current -> {
                drawCircle(skin.amber.copy(alpha = 0.18f), r + 5.dp.toPx(), Offset(x, cy))
                drawCircle(skin.amber, r, Offset(x, cy))
            }
            done -> drawCircle(teal, r, Offset(x, cy))
            else -> {
                drawCircle(skin.bar, r, Offset(x, cy))
                drawCircle(teal, r - 1.dp.toPx(), Offset(x, cy), style = Stroke(2.dp.toPx()))
            }
        }
    }
}

/** A bell for each kind of Adhan Alert (full, short, silent, off); its description names the sound. */
@Composable
private fun AlertIcon(prayer: Prayer, alert: PrayerAlertSettings, tint: Color) {
    val description = stringResource(
        when {
            !alert.adhanAlert -> R.string.alerts_adhan_off
            alert.sound == AdhanSoundChoice.SHORT -> R.string.alerts_sound_short
            alert.sound == AdhanSoundChoice.FULL -> if (prayer == Prayer.FAJR) R.string.alerts_sound_full_fajr else R.string.alerts_sound_full
            else -> R.string.alerts_sound_silent
        },
    )
    val icon = when {
        !alert.adhanAlert -> R.drawable.ic_bell_off
        alert.sound == AdhanSoundChoice.FULL -> R.drawable.ic_bell_full
        alert.sound == AdhanSoundChoice.SHORT -> R.drawable.ic_bell
        else -> R.drawable.ic_bell_silent
    }
    Icon(
        painterResource(icon),
        contentDescription = description,
        tint = if (alert.adhanAlert) tint else tint.copy(alpha = 0.6f),
        modifier = Modifier.size(18.dp),
    )
}

/** Prayed: a filled circle with a tick. Due and not marked: an accent ring. Not yet due: a faint dashed ring. */
@Composable
private fun LogMark(state: LogState) {
    val skin = LocalSkin.current
    val accent = MaterialTheme.colorScheme.primary
    when (state) {
        LogState.PRAYED -> Box(Modifier.size(26.dp).background(skin.mark, CircleShape), contentAlignment = Alignment.Center) {
            // The row's state already says "Prayed"; TalkBack shouldn't also read "check mark".
            Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = skin.onMark, modifier = Modifier.size(15.dp))
        }
        LogState.NOT_MARKED -> Box(Modifier.size(26.dp).border(2.dp, accent, CircleShape))
        LogState.NOT_YET_DUE -> Canvas(Modifier.size(26.dp)) {
            val w = 1.5.dp.toPx()
            drawCircle(
                skin.track,
                radius = size.minDimension / 2 - w / 2,
                style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))),
            )
        }
    }
}

private fun stateDescriptionOf(cell: LogCell): Int = when {
    cell.state == LogState.NOT_YET_DUE -> R.string.log_not_yet_due
    !cell.markable -> R.string.log_read_only
    cell.state == LogState.PRAYED -> R.string.log_prayed
    else -> R.string.log_not_marked
}

@Composable
private fun prayerName(prayerTime: PrayerTime): String = stringResource(
    when {
        prayerTime.isJumuah -> R.string.prayer_jumuah
        else -> when (prayerTime.prayer) {
            Prayer.FAJR -> R.string.prayer_fajr
            Prayer.DHUHR -> R.string.prayer_dhuhr
            Prayer.ASR -> R.string.prayer_asr
            Prayer.MAGHRIB -> R.string.prayer_maghrib
            Prayer.ISHA -> R.string.prayer_isha
        }
    },
)
