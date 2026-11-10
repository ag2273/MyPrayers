package app.mawaqeet.ui.home

import androidx.compose.foundation.background
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import app.mawaqeet.core.AdhanSoundChoice
import app.mawaqeet.core.AlertSettings
import app.mawaqeet.core.PrayerAlertSettings
import app.mawaqeet.ui.components.Card
import app.mawaqeet.ui.components.TabPage
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
import app.mawaqeet.R
import app.mawaqeet.ui.LocalDisplayFormatter
import app.mawaqeet.core.DisplayDay
import app.mawaqeet.core.HighLatitudeAdjustment
import app.mawaqeet.ui.settings.label
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
    HomeContent(day, now, prayerDay, locationName, alerts, onOpenLocation, locationHint, onLocationHint, tip) { prayer ->
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
    alerts: AlertSettings,
    onOpenLocation: () -> Unit,
    locationHint: String?,
    onLocationHint: () -> Unit,
    tip: (@Composable () -> Unit)?,
    onToggle: (Prayer) -> Unit,
) {
    TabPage(title = null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DateLine(day, Modifier.weight(1f))
            LocationChip(locationName, onOpenLocation)
        }
        if (locationHint != null) {
            TextButton(onClick = onLocationHint) { Text(locationHint) }
        }
        tip?.invoke()
        day.highLatitude?.let { HighLatitudeNote(it) }
        NextPrayerCard(day, now)
        Card(Modifier.padding(vertical = 0.dp)) {
            Column(Modifier.padding(vertical = 4.dp)) {
                val isNextToday = day.next.time.toLocalDate() == day.date
                day.prayerTimes.forEach { prayerTime ->
                    val prayer = prayerTime.prayer
                    TimeRow(
                        name = prayerName(prayerTime),
                        time = prayerTime.time,
                        highlighted = isNextToday && prayerTime == day.next,
                        alert = alerts.of(prayer),
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
        Text(
            stringResource(R.string.home_log_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
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

/** The display day's Gregorian date, then its Hijri date (with the user's offset). */
@Composable
private fun DateLine(day: DisplayDay, modifier: Modifier = Modifier) {
    val format = LocalDisplayFormatter.current
    Column(modifier) {
        Text(format.gregorianDate(day.date), style = MaterialTheme.typography.titleLarge)
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
 * The next Prayer on a deep teal card: its name and time, the countdown, and how much of the time since the previous
 * Prayer has passed (hidden before Fajr, when the previous Prayer was yesterday's).
 */
@Composable
private fun NextPrayerCard(day: DisplayDay, now: Instant) {
    val colors = MaterialTheme.colorScheme
    val next = day.next
    val format = LocalDisplayFormatter.current
    val onCard = colors.onTertiaryContainer
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.tertiaryContainer, RoundedCornerShape(28.dp))
            .padding(horizontal = 22.dp, vertical = 20.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(stringResource(R.string.next_prayer), style = MaterialTheme.typography.bodyMedium, color = onCard)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(prayerName(next), style = MaterialTheme.typography.headlineLarge, color = onCard, modifier = Modifier.weight(1f))
            Text(format.time(next.time), style = MaterialTheme.typography.headlineLarge, color = onCard)
        }
        Text(
            text = stringResource(R.string.countdown, format.countdown(Duration.between(now, next.time.toInstant()))),
            style = MaterialTheme.typography.titleLarge,
            color = colors.tertiary,
        )
        val previous = day.prayerTimes.lastOrNull { !it.time.toInstant().isAfter(now) }
        if (previous != null && next.time.toLocalDate() == day.date) {
            val total = Duration.between(previous.time, next.time).seconds.coerceAtLeast(1)
            val passed = Duration.between(previous.time.toInstant(), now).seconds.coerceIn(0, total)
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).background(onCard.copy(alpha = 0.2f), RoundedCornerShape(3.dp))) {
                Box(
                    Modifier
                        .fillMaxWidth(passed.toFloat() / total)
                        .height(6.dp)
                        .background(colors.tertiary, RoundedCornerShape(3.dp)),
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp).clearAndSetSemantics {}) {
                Text("${prayerName(previous)} ${format.time(previous.time)}", style = MaterialTheme.typography.bodySmall, color = onCard, modifier = Modifier.weight(1f))
                Text("${prayerName(next)} ${format.time(next.time)}", style = MaterialTheme.typography.bodySmall, color = onCard)
            }
        }
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
            .heightIn(min = 54.dp)
            .padding(horizontal = 10.dp, vertical = 6.dp),
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
