package app.mawaqeet.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.semantics.Role
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
import app.mawaqeet.R
import app.mawaqeet.ui.LocalDisplayFormatter
import app.mawaqeet.core.DisplayDay
import app.mawaqeet.core.Prayer
import app.mawaqeet.core.PrayerCore
import app.mawaqeet.core.PrayerTime
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
    onOpenSettings: () -> Unit,
    /** A short hint under the location (e.g. the time zone changed), tapping it calls [onLocationHint]. */
    locationHint: String? = null,
    onLocationHint: () -> Unit = {},
    /** Shows the dismissible Fix alerts tip (#29) until the user taps Done. */
    showFixAlertsTip: Boolean = false,
    onOpenFixAlerts: () -> Unit = {},
    onFixAlertsDone: () -> Unit = {},
) {
    // Re-query the core every second: the countdown ticks and the display day switches at local midnight.
    var now by remember { mutableStateOf(clock.instant()) }
    LaunchedEffect(core, clock) {
        while (true) {
            now = clock.instant()
            delay(1000 - now.toEpochMilli() % 1000)
        }
    }
    val day = remember(core, now) { core.displayDay() }
    // The log reads the database, so query it off the main thread, again whenever the log changes or a Prayer
    // Time arrives (the next Prayer moves on, so markability and the Prayer Day may have changed).
    val changes by logChanges.collectAsState()
    val prayerDay by produceState<PrayerDay?>(null, core, changes, day.next) {
        value = withContext(Dispatchers.IO) { core.currentPrayerDay() }
    }
    val scope = rememberCoroutineScope()
    val tip: (@Composable () -> Unit)? = if (showFixAlertsTip) {
        { FixAlertsTip(onOpenFixAlerts, onFixAlertsDone) }
    } else {
        null
    }
    HomeContent(day, now, prayerDay, locationName, onOpenSettings, locationHint, onLocationHint, tip) { prayer ->
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
    prayerDay: PrayerDay?,
    locationName: String,
    onOpenSettings: () -> Unit,
    locationHint: String?,
    onLocationHint: () -> Unit,
    tip: (@Composable () -> Unit)?,
    onToggle: (Prayer) -> Unit,
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DateLine(day)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = locationName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.settings)) }
            }
            if (locationHint != null) {
                TextButton(onClick = onLocationHint) { Text(locationHint) }
            }
            tip?.invoke()
            NextPrayerCard(day.next, now)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val isNextToday = day.next.time.toLocalDate() == day.date
                day.prayerTimes.forEach { prayerTime ->
                    val prayer = prayerTime.prayer
                    TimeRow(
                        name = prayerName(prayerTime),
                        time = prayerTime.time,
                        highlighted = isNextToday && prayerTime == day.next,
                        // Between midnight and Fajr the marks are still the previous Prayer Day's (late Isha).
                        log = prayerDay?.let { LogCell(it.stateOf(prayer), it.isMarkable(prayer)) },
                        onClick = { onToggle(prayer) },
                    )
                    if (prayer == Prayer.FAJR) {
                        // Sunrise is not a Prayer: never markable.
                        TimeRow(name = stringResource(R.string.sunrise), time = day.sunrise, highlighted = false)
                    }
                }
            }
        }
    }
}

/** On alarm-killing phones: a short tip pointing to Fix alerts, until the user taps Done. */
@Composable
private fun FixAlertsTip(onOpen: () -> Unit, onDone: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            stringResource(R.string.fix_alerts_tip),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Row {
            TextButton(onClick = onOpen) { Text(stringResource(R.string.fix_alerts)) }
            TextButton(onClick = onDone) { Text(stringResource(R.string.done)) }
        }
    }
}

/** The display day's Gregorian date, then its Hijri date (with the user's offset). */
@Composable
private fun DateLine(day: DisplayDay) {
    val format = LocalDisplayFormatter.current
    Column {
        Text(format.gregorianDate(day.date), style = MaterialTheme.typography.titleMedium)
        Text(
            format.hijriDate(day.hijriDate),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** What a row shows of the Prayer Log. */
private data class LogCell(val state: LogState, val markable: Boolean)

@Composable
private fun NextPrayerCard(next: PrayerTime, now: Instant) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val onPrimary = MaterialTheme.colorScheme.onPrimary
        Text(stringResource(R.string.next_prayer), style = MaterialTheme.typography.bodyLarge, color = onPrimary)
        Text(prayerName(next), style = MaterialTheme.typography.headlineLarge, color = onPrimary)
        val format = LocalDisplayFormatter.current
        Text(format.time(next.time), style = MaterialTheme.typography.displayMedium, color = onPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.countdown, format.countdown(Duration.between(now, next.time.toInstant()))),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

@Composable
private fun TimeRow(
    name: String,
    time: ZonedDateTime,
    highlighted: Boolean,
    log: LogCell? = null,
    onClick: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val stateText = log?.let { stringResource(stateDescriptionOf(it)) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (highlighted) colors.primaryContainer else Color.Transparent, RoundedCornerShape(16.dp))
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
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val weight = if (highlighted) FontWeight.Bold else FontWeight.Normal
        Text(
            text = name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = weight,
            color = if (highlighted) colors.primary else colors.onSurface,
        )
        Text(
            text = LocalDisplayFormatter.current.time(time),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = weight,
            color = if (highlighted) colors.primary else colors.onSurface,
        )
        Spacer(Modifier.width(16.dp))
        // Sunrise gets an empty slot so its time lines up with the Prayers'.
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            if (log != null) LogMark(log.state)
        }
    }
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
            Text("✓", color = colors.onPrimary, style = MaterialTheme.typography.titleMedium)
        }
        LogState.NOT_MARKED -> Box(Modifier.size(28.dp).border(2.dp, colors.primary, CircleShape))
        LogState.NOT_YET_DUE -> Box(Modifier.size(28.dp).border(1.dp, colors.outlineVariant, CircleShape))
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
