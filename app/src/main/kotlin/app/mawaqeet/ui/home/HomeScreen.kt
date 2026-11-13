package app.mawaqeet.ui.home

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
        day, now, ramadan, prayerDay, locationName, alerts, onOpenLocation, locationHint, onLocationHint, tip, tools,
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

@Composable
private fun HomeContent(
    day: DisplayDay,
    now: Instant,
    ramadan: RamadanState,
    prayerDay: PrayerDay?,
    locationName: String,
    alerts: AlertSettings,
    onOpenLocation: () -> Unit,
    locationHint: String?,
    onLocationHint: () -> Unit,
    tip: (@Composable () -> Unit)?,
    tools: (@Composable () -> Unit)?,
    showLogHint: Boolean,
    onOpenCalendar: (() -> Unit)?,
    onToggle: (Prayer) -> Unit,
) {
    FillingPage { size ->
        Row(
            Modifier.semantics { todaySize = size },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DateLine(day, size, onOpenCalendar, Modifier.weight(1f))
            LocationChip(locationName, onOpenLocation)
        }
        if (locationHint != null) {
            TextButton(onClick = onLocationHint) { Text(locationHint) }
        }
        tip?.invoke()
        day.highLatitude?.let { HighLatitudeNote(it) }
        val cardSizes = NextPrayerCardSizes.of(size)
        // After Isha, until Fajr, the card names the start of the Last Third of the Night (#96).
        val afterIsha = day.next.prayer == Prayer.FAJR && !day.timeOf(Prayer.ISHA).toInstant().isAfter(now)
        val lastThird = day.lastThirdOfNight.takeIf { afterIsha }
        when (ramadan) {
            RamadanState.None -> NextPrayerCard(day, now, lastThird, cardSizes, Modifier.layoutId(Fill.TakesTheRest))
            else -> RamadanCard(ramadan, day.next, now, lastThird, cardSizes, Modifier.layoutId(Fill.TakesTheRest))
        }
        tools?.invoke()
        Card(Modifier.layoutId(Fill.Rows(count = day.prayerTimes.size + 1))) {
            SharedHeightColumn(Modifier.fillMaxHeight().padding(vertical = 4.dp)) {
                val isNextToday = day.next.time.toLocalDate() == day.date
                day.prayerTimes.forEach { prayerTime ->
                    val prayer = prayerTime.prayer
                    TimeRow(
                        name = prayerName(prayerTime),
                        time = prayerTime.time,
                        highlighted = isNextToday && prayerTime == day.next,
                        alert = alerts.effective(prayer),
                        prayer = prayer,
                        // Between midnight and Fajr the marks are still the previous Prayer Day's (late Isha).
                        log = prayerDay?.let { LogCell(it.stateOf(prayer), it.isMarkable(prayer)) },
                        onClick = { onToggle(prayer) },
                    )
                    if (prayer == Prayer.FAJR) {
                        // Sunrise is not a Prayer: never markable, and no alert.
                        TimeRow(name = stringResource(R.string.sunrise), time = day.sunrise, highlighted = false, muted = true)
                    }
                }
            }
        }
        if (showLogHint) {
            Text(
                stringResource(R.string.home_log_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
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

private val MAX_ROW_HEIGHT = 64.dp
private val PAGE_GAP = 10.dp
private val PAGE_GAP_COMPACT = 6.dp
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

/** The current location as a tappable chip: it opens the city search. */
@Composable
private fun LocationChip(name: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surfaceContainer)
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(24.dp))
            .clickable(onClickLabel = stringResource(R.string.location), onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(painterResource(R.drawable.ic_pin), contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
        Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 2)
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

/** The display day's Gregorian date, then its Hijri date (with the user's offset); tapping it opens [onOpen]. */
@Composable
private fun DateLine(day: DisplayDay, size: TodaySize, onOpen: (() -> Unit)?, modifier: Modifier = Modifier) {
    val format = LocalDisplayFormatter.current
    val style = when (size) {
        TodaySize.REGULAR -> MaterialTheme.typography.titleLarge
        TodaySize.COMPACT -> MaterialTheme.typography.titleMedium
    }
    val tappable = if (onOpen == null) {
        Modifier
    } else {
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = stringResource(R.string.calendar_open_description), role = Role.Button, onClick = onOpen)
            .heightIn(min = 48.dp)
    }
    Column(modifier.then(tappable), verticalArrangement = Arrangement.Center) {
        Text(format.gregorianDate(day.date), style = style)
        Text(
            format.hijriDate(day.hijriDate),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
 * The Next Prayer card's type sizes and spacing, shared by its usual and Ramadan forms so one size class sets both.
 * The regular sizes are the card's own design; the compact ones are for short screens.
 */
@Immutable
private data class NextPrayerCardSizes(
    /** «الصلاة القادمة», and the Ramadan card's top line. */
    val label: TextStyle,
    /** The Prayer's name and time; «الإفطار …» and «السحور». */
    val headline: TextStyle,
    val countdown: TextStyle,
    /** The bar's ends; the Ramadan card's next Prayer line. */
    val small: TextStyle,
    val paddingHorizontal: Dp,
    val paddingVertical: Dp,
    val barGap: Dp,
    val barHeight: Dp,
    /** The gap above the bar's ends and the Ramadan card's next Prayer line. */
    val smallGap: Dp,
    /** The label and the countdown each on their own line (regular), or sharing the label's line (compact). */
    val stacked: Boolean,
) {
    companion object {
        @Composable
        fun of(size: TodaySize) = when (size) {
            TodaySize.REGULAR -> regular()
            TodaySize.COMPACT -> compact()
        }

        @Composable
        fun regular() = MaterialTheme.typography.let {
            NextPrayerCardSizes(
                label = it.bodyMedium, headline = it.headlineLarge, countdown = it.titleLarge, small = it.bodySmall,
                paddingHorizontal = 22.dp, paddingVertical = 20.dp, barGap = 14.dp, barHeight = 6.dp, smallGap = 6.dp,
                stacked = true,
            )
        }

        @Composable
        fun compact() = MaterialTheme.typography.let {
            NextPrayerCardSizes(
                label = it.bodyMedium, headline = it.headlineSmall, countdown = it.titleMedium, small = it.bodySmall,
                paddingHorizontal = 20.dp, paddingVertical = 8.dp, barGap = 6.dp, barHeight = 6.dp, smallGap = 2.dp,
                stacked = false,
            )
        }
    }
}

/**
 * The next Prayer on a deep teal card: its name and time, the countdown, and how much of the time since the previous
 * Prayer has passed. After Isha the bar gives way to [lastThird], the start of the Last Third of the Night (#96), in
 * less height than the bar and its line, so the card never grows.
 */
@Composable
private fun NextPrayerCard(day: DisplayDay, now: Instant, lastThird: ZonedDateTime?, sizes: NextPrayerCardSizes, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val next = day.next
    val format = LocalDisplayFormatter.current
    val onCard = colors.onTertiaryContainer
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.tertiaryContainer, RoundedCornerShape(28.dp))
            .padding(horizontal = sizes.paddingHorizontal, vertical = sizes.paddingVertical)
            .semantics(mergeDescendants = true) {},
        // When the page gives it spare height, the content stays in the middle.
        verticalArrangement = Arrangement.Center,
    ) {
        val countdown = stringResource(R.string.countdown, format.countdown(Duration.between(now, next.time.toInstant())))
        // On a short screen the label and the countdown share a line (#87).
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.next_prayer), style = sizes.label, color = onCard, modifier = Modifier.weight(1f))
            if (!sizes.stacked) Text(countdown, style = sizes.countdown, color = colors.tertiary)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(prayerName(next), style = sizes.headline, color = onCard, modifier = Modifier.weight(1f))
            Text(format.time(next.time), style = sizes.headline, color = onCard)
        }
        if (sizes.stacked) Text(countdown, style = sizes.countdown, color = colors.tertiary)
        val previous = day.prayerTimes.lastOrNull { !it.time.toInstant().isAfter(now) }
        if (previous != null && next.time.toLocalDate() == day.date) {
            val total = Duration.between(previous.time, next.time).seconds.coerceAtLeast(1)
            val passed = Duration.between(previous.time.toInstant(), now).seconds.coerceIn(0, total)
            Spacer(Modifier.height(sizes.barGap))
            ProgressBar(passed.toFloat() / total, sizes.barHeight, track = onCard.copy(alpha = 0.2f), fill = colors.tertiary)
            Row(Modifier.fillMaxWidth().padding(top = sizes.smallGap).clearAndSetSemantics {}) {
                Text(stringResource(R.string.prayer_at, prayerName(previous), format.time(previous.time)), style = sizes.small, color = onCard, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.prayer_at, prayerName(next), format.time(next.time)), style = sizes.small, color = onCard)
            }
        } else if (lastThird != null) {
            Spacer(Modifier.height(sizes.barGap))
            LastThirdLine(lastThird, now, sizes, onCard)
        }
    }
}

/** «الثلث الأخير من الليل يبدأ ٢:٣٤ ص», or «… بدأ …» once it has begun (#96). */
@Composable
private fun LastThirdLine(start: ZonedDateTime, now: Instant, sizes: NextPrayerCardSizes, color: Color, modifier: Modifier = Modifier) {
    val text = if (start.toInstant().isAfter(now)) R.string.last_third_begins else R.string.last_third_began
    Text(stringResource(text, LocalDisplayFormatter.current.time(start)), style = sizes.small, color = color, modifier = modifier)
}

/**
 * The Next Prayer card's Ramadan form (#90), as tall as the usual card so Today still fits a small phone: the day of
 * Ramadan and Imsak on top; while fasting, Iftar's time with the countdown to it and a bar from Imsak to Maghrib; from
 * Maghrib, the countdown to the end of suhoor. The next Prayer stays as one line at the bottom (#83); after Isha the
 * suhoor card, which has no bar, names [lastThird], the start of the Last Third of the Night (#96), above it, or on a
 * short screen beside the next Prayer's name and time, so the card is no taller than the fasting one.
 */
@Composable
private fun RamadanCard(
    state: RamadanState,
    next: PrayerTime,
    now: Instant,
    lastThird: ZonedDateTime?,
    sizes: NextPrayerCardSizes,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val format = LocalDisplayFormatter.current
    val onCard = colors.onTertiaryContainer
    val (day, imsak) = when (state) {
        is RamadanState.Fasting -> state.dayOfRamadan to state.imsak
        is RamadanState.Suhoor -> state.dayOfRamadan to state.imsak
        RamadanState.None -> return
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.tertiaryContainer, RoundedCornerShape(28.dp))
            .padding(horizontal = sizes.paddingHorizontal, vertical = sizes.paddingVertical)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.ramadan_day, format.number(day)), style = sizes.label, color = onCard, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.ramadan_imsak_at, format.time(imsak)), style = sizes.label, color = onCard)
        }
        val (title, countdown) = when (state) {
            is RamadanState.Fasting -> stringResource(R.string.ramadan_iftar_at, format.time(state.maghrib)) to
                stringResource(R.string.countdown, format.countdown(Duration.between(now, state.maghrib.toInstant())))
            else -> stringResource(R.string.ramadan_suhoor) to
                stringResource(R.string.ramadan_suhoor_ends_in, format.countdown(Duration.between(now, imsak.toInstant())))
        }
        // As on the usual card: the countdown has its own line, or shares the title's on a short screen.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = sizes.headline, color = onCard, modifier = Modifier.weight(1f))
            if (!sizes.stacked) Text(countdown, style = sizes.countdown, color = colors.tertiary)
        }
        if (sizes.stacked) Text(countdown, style = sizes.countdown, color = colors.tertiary)
        if (state is RamadanState.Fasting) {
            val total = Duration.between(state.imsak, state.maghrib).seconds.coerceAtLeast(1)
            val passed = Duration.between(state.imsak.toInstant(), now).seconds.coerceIn(0, total)
            Spacer(Modifier.height(sizes.barGap))
            ProgressBar(passed.toFloat() / total, sizes.barHeight, track = onCard.copy(alpha = 0.2f), fill = colors.tertiary)
        }
        val nextPrayerLine = stringResource(
            R.string.ramadan_next_prayer,
            prayerName(next),
            format.time(next.time),
            format.hoursMinutes(Duration.between(now, next.time.toInstant())),
        )
        when {
            lastThird == null -> Text(nextPrayerLine, style = sizes.small, color = onCard, modifier = Modifier.padding(top = sizes.smallGap))
            // Suhoor has no bar: in its place the Last Third, above the next Prayer line.
            sizes.stacked -> {
                LastThirdLine(lastThird, now, sizes, onCard, Modifier.padding(top = sizes.smallGap))
                Text(nextPrayerLine, style = sizes.small, color = onCard, modifier = Modifier.padding(top = sizes.smallGap))
            }
            // On a short screen they share one line, as the usual card's bar ends do: «الثلث الأخير … · الفجر ٥:٠٠ ص».
            else -> Row(Modifier.fillMaxWidth().padding(top = sizes.smallGap), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LastThirdLine(lastThird, now, sizes, onCard, Modifier.weight(1f))
                Text(stringResource(R.string.prayer_at, prayerName(next), format.time(next.time)), style = sizes.small, color = onCard)
            }
        }
    }
}

/** The Next Prayer card's bar, [height] tall, [fraction] of it filled. */
@Composable
private fun ProgressBar(fraction: Float, height: Dp, track: Color, fill: Color) {
    val shape = RoundedCornerShape(height / 2)
    Box(Modifier.fillMaxWidth().height(height).background(track, shape)) {
        Box(Modifier.fillMaxWidth(fraction).height(height).background(fill, shape))
    }
}

@Composable
private fun TimeRow(
    name: String,
    time: ZonedDateTime,
    highlighted: Boolean,
    alert: PrayerAlertSettings? = null,
    prayer: Prayer? = null,
    muted: Boolean = false,
    log: LogCell? = null,
    onClick: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val stateText = log?.let { stringResource(stateDescriptionOf(it)) }
    val textColor = when {
        highlighted -> colors.onPrimaryContainer
        muted -> colors.onSurfaceVariant
        else -> colors.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (highlighted) colors.primaryContainer else Color.Transparent)
            .then(
                if (log == null) {
                    Modifier
                } else {
                    // Stays focusable and tappable when not yet due so TalkBack reads the reason; the tap does nothing.
                    Modifier
                        .clickable(role = Role.Checkbox, onClick = onClick)
                        .semantics {
                            stateDescription = stateText.orEmpty()
                            toggleableState = ToggleableState(log.state == LogState.PRAYED)
                        }
                },
            )
            // A Prayer row is a 48dp tap target; Sunrise is never tapped, so it may be shorter.
            .heightIn(min = if (log == null) 0.dp else 48.dp)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val weight = if (muted) FontWeight.Normal else FontWeight.SemiBold
        Text(
            text = name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = weight,
            color = textColor,
        )
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            if (alert != null && prayer != null) AlertIcon(prayer, alert, if (highlighted) colors.onPrimaryContainer else colors.onSurfaceVariant)
        }
        Text(
            text = LocalDisplayFormatter.current.time(time),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = weight,
            color = textColor,
        )
        // Sunrise gets an empty slot so its time lines up with the Prayers'.
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            if (log != null) LogMark(log.state)
        }
    }
}

/** A bell for an audible Adhan Alert, a struck-out bell for a silent one or none; its description names the sound. */
@Composable
private fun AlertIcon(prayer: Prayer, alert: PrayerAlertSettings, tint: Color) {
    val audible = alert.adhanAlert && alert.sound != AdhanSoundChoice.SILENT
    val description = stringResource(
        when {
            !alert.adhanAlert -> R.string.alerts_adhan_off
            alert.sound == AdhanSoundChoice.SHORT -> R.string.alerts_sound_short
            alert.sound == AdhanSoundChoice.FULL -> if (prayer == Prayer.FAJR) R.string.alerts_sound_full_fajr else R.string.alerts_sound_full
            else -> R.string.alerts_sound_silent
        },
    )
    Icon(
        painterResource(if (audible) R.drawable.ic_bell else R.drawable.ic_bell_off),
        contentDescription = description,
        tint = if (audible) tint else tint.copy(alpha = 0.6f),
        modifier = Modifier.size(18.dp),
    )
}

/** Prayed: a filled circle with a tick. Not marked: an outlined circle. Not yet due: a faint outline. */
@Composable
private fun LogMark(state: LogState) {
    val colors = MaterialTheme.colorScheme
    when (state) {
        LogState.PRAYED -> Box(
            Modifier.size(28.dp).background(colors.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            // The row's state already says "Prayed"; TalkBack shouldn't also read "check mark".
            Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(16.dp))
        }
        LogState.NOT_MARKED -> Box(Modifier.size(28.dp).border(2.dp, colors.primary, CircleShape))
        LogState.NOT_YET_DUE -> Box(Modifier.size(28.dp).border(1.5.dp, colors.outline.copy(alpha = 0.5f), CircleShape))
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
