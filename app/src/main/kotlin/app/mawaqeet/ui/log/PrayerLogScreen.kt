package app.mawaqeet.ui.log

import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import app.mawaqeet.ui.theme.LocalSkin
import app.mawaqeet.ui.theme.NotoKufiArabic
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import app.mawaqeet.ui.components.Card
import app.mawaqeet.ui.components.RowDivider
import app.mawaqeet.ui.components.TabPage
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.mawaqeet.R
import app.mawaqeet.core.LogState
import app.mawaqeet.core.Prayer
import app.mawaqeet.core.PrayerCore
import app.mawaqeet.core.PrayerDay
import app.mawaqeet.core.PrayerWeek
import app.mawaqeet.ui.LocalDisplayFormatter
import app.mawaqeet.ui.settings.nameRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Prayer Log (#25) in the "A Day That Breathes" design (1.4): the week as seven day columns (oldest first) with a
 * dot per Prayer, a legend, the week's total and share (counts only: no streaks, badges or scores), then the chosen
 * day's Prayers to mark. The current week opens on today; older weeks are browsable and read-only. Tapping a Prayer of
 * an editable day marks or unmarks it through the core, under the same rules as Home.
 */
@Composable
fun PrayerLogScreen(core: PrayerCore, logChanges: StateFlow<Int>) {
    // How many weeks back from the current one; saved so rotation keeps the week.
    var weeksBack by rememberSaveable { mutableStateOf(0) }
    // The chosen day, counted back from the week's newest (0 = today on the current week).
    var dayBack by rememberSaveable { mutableStateOf(0) }
    val changes by logChanges.collectAsState()
    val week by produceState<PrayerWeek?>(null, core, changes, weeksBack) {
        value = withContext(Dispatchers.IO) {
            val current = core.currentPrayerDay().date
            core.week(current.minusDays(7L * weeksBack))
        }
    }
    val scope = rememberCoroutineScope()
    val format = LocalDisplayFormatter.current
    TabPage(stringResource(R.string.prayer_log)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            WeekButton(R.string.log_previous_week, mirrored = false, enabled = true) { weeksBack += 1; dayBack = 0 }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(if (weeksBack == 0) R.string.log_this_week else R.string.log_earlier_week),
                    style = MaterialTheme.typography.titleMedium,
                )
                week?.let {
                    Text(
                        "${format.shortGregorianDate(it.days.first().date)} – ${format.shortGregorianDate(it.days.last().date)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            WeekButton(R.string.log_next_week, mirrored = true, enabled = weeksBack > 0) { weeksBack -= 1; dayBack = 0 }
        }
        val shown = week ?: return@TabPage
        val selected = shown.days[(shown.days.size - 1 - dayBack).coerceIn(0, shown.days.size - 1)]
        DayStrip(shown, selected) { day -> dayBack = shown.days.size - 1 - shown.days.indexOf(day) }
        Legend()
        Totals(shown)
        if (shown.days.none { it.isEditable }) {
            Text(
                stringResource(R.string.log_week_read_only),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // One date a line: a middle dot between two dates reads as an Arabic zero («٢٠٢٦ · ٣» → «٢٠٢٦٠٣»).
        val selectedDate = "${format.gregorianDate(selected.date)}\n${format.hijriDate(selected.hijriDate)}"
        Text(
            if (selected.isEditable) stringResource(R.string.log_day_hint, selectedDate) else selectedDate,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (shown.isBeforeFirstUse(selected)) {
            Text(
                stringResource(R.string.log_before_first_use),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Prayer.entries.forEach { prayer ->
            PrayerRow(selected, prayer) {
                if (selected.isMarkable(prayer)) {
                    scope.launch(Dispatchers.IO) {
                        if (selected.stateOf(prayer) == LogState.PRAYED) core.unmark(selected.date, prayer) else core.mark(selected.date, prayer)
                    }
                }
            }
        }
    }
}

/** An outlined round arrow button for the previous or next week; [label] is read by TalkBack. Also the Adhkar Record's and the Calendar month's. */
@Composable
internal fun WeekButton(label: Int, mirrored: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val skin = LocalSkin.current
    OutlinedIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(48.dp),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, skin.line),
        colors = androidx.compose.material3.IconButtonDefaults.outlinedIconButtonColors(
            containerColor = skin.surface,
            disabledContainerColor = skin.surface.copy(alpha = skin.surface.alpha * 0.4f),
        ),
    ) {
        // ic_back points to the start (right in Arabic): the previous week; turned around, the next one.
        Icon(
            painterResource(R.drawable.ic_back),
            contentDescription = stringResource(label),
            modifier = Modifier.size(20.dp).graphicsLayer { if (mirrored) rotationZ = 180f },
        )
    }
}

/**
 * The week as seven columns, oldest at the start: the weekday, the date and a dot per Prayer (filled: prayed; ring:
 * missed; faint: not yet due or before first use). The chosen day is outlined; each column is a tab TalkBack reads
 * with its count.
 */
@Composable
private fun DayStrip(week: PrayerWeek, selected: PrayerDay, onSelect: (PrayerDay) -> Unit) {
    val format = LocalDisplayFormatter.current
    val skin = LocalSkin.current
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        week.days.forEach { day ->
            val on = day == selected
            val prayed = Prayer.entries.count { day.stateOf(it) == LogState.PRAYED }
            val description = stringResource(R.string.log_day_description, "${format.gregorianDate(day.date)}، ${format.hijriDate(day.hijriDate)}", format.count(prayed, Prayer.entries.size))
            val shape = RoundedCornerShape(16.dp)
            Column(
                Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(skin.surface)
                    .border(if (on) 2.dp else 1.dp, if (on) colors.primary else skin.line, shape)
                    .selectable(selected = on, role = Role.Tab) { onSelect(day) }
                    .clearAndSetSemantics {
                        contentDescription = description
                        this.selected = on
                        role = Role.Tab
                    }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // At the largest text sizes a short weekday no longer fits a seventh of the width: one letter does.
                val weekday = if (LocalDensity.current.fontScale >= 1.5f) format.narrowWeekday(day.date) else shortWeekday(day.date)
                Text(weekday, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                Text(format.number(day.date.dayOfMonth), style = MaterialTheme.typography.titleSmall)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Prayer.entries.forEach { prayer -> Dot(if (week.isBeforeFirstUse(day)) LogState.NOT_YET_DUE else day.stateOf(prayer)) }
                }
            }
        }
    }
}

/** A Prayer's dot in the week strip and the legend. */
@Composable
private fun Dot(state: LogState) {
    val skin = LocalSkin.current
    when (state) {
        LogState.PRAYED -> Box(Modifier.size(9.dp).background(skin.mark, CircleShape))
        LogState.NOT_MARKED -> Box(Modifier.size(9.dp).border(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
        LogState.NOT_YET_DUE -> Box(Modifier.size(9.dp).background(skin.track, CircleShape))
    }
}

/** «● صلّيت  ○ فاتت  ● لم يحن». TalkBack skips it: each day already says its count. */
@Composable
private fun Legend() {
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(
            LogState.PRAYED to R.string.log_legend_prayed,
            LogState.NOT_MARKED to R.string.log_legend_missed,
            LogState.NOT_YET_DUE to R.string.log_legend_not_yet,
        ).forEach { (state, label) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Dot(state)
                Text(stringResource(label), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The week's total («٢٦ من ٣٣ · المجموع») and share («٧٩٪ · ملخص الأسبوع»), side by side. */
@Composable
private fun Totals(week: PrayerWeek) {
    val format = LocalDisplayFormatter.current
    val total = week.summary.total
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Stat(
            Modifier.weight(1f),
            value = format.number(total.prayed),
            suffix = stringResource(R.string.log_total_of, format.number(total.possible)),
            label = stringResource(R.string.log_total),
        )
        val percent = if (total.possible == 0) 0 else (total.prayed * 100 + total.possible / 2) / total.possible
        Stat(
            Modifier.weight(1f),
            value = format.number(percent) + if (androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language == "ar") "٪" else "%",
            suffix = null,
            label = stringResource(R.string.log_summary),
        )
    }
}

@Composable
private fun Stat(modifier: Modifier, value: String, suffix: String?, label: String) {
    Card(modifier) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp).semantics(mergeDescendants = true) {}) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(value, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
                if (suffix != null) {
                    Text(suffix, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 4.dp))
                }
            }
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** One Prayer of the chosen day as its own card: the mark, the name, and its state with the Prayer Time. */
@Composable
private fun PrayerRow(day: PrayerDay, prayer: Prayer, onToggle: () -> Unit) {
    val format = LocalDisplayFormatter.current
    val skin = LocalSkin.current
    val state = day.stateOf(prayer)
    val time = day.prayerTimes.firstOrNull { it.prayer == prayer }
    val name = stringResource(if (time?.isJumuah == true) R.string.prayer_jumuah else prayer.nameRes)
    val stateText = stringResource(
        when {
            state == LogState.NOT_YET_DUE -> R.string.log_not_yet_due
            !day.isEditable -> if (state == LogState.PRAYED) R.string.log_read_only_prayed else R.string.log_read_only
            state == LogState.PRAYED -> R.string.log_prayed
            else -> R.string.log_not_marked
        },
    )
    val short = stringResource(
        when (state) {
            LogState.PRAYED -> R.string.log_status_prayed
            LogState.NOT_MARKED -> R.string.log_status_not_marked
            LogState.NOT_YET_DUE -> R.string.log_status_not_yet
        },
    )
    val shape = RoundedCornerShape(20.dp)
    val faint = state == LogState.NOT_YET_DUE
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (faint) skin.surface.copy(alpha = skin.surface.alpha * 0.5f) else skin.surface)
            .border(1.dp, skin.line, shape)
            .clickable(role = Role.Checkbox, enabled = day.isMarkable(prayer), onClick = onToggle)
            .semantics(mergeDescendants = true) {
                contentDescription = name
                stateDescription = stateText
            }
            .heightIn(min = 60.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Mark(state)
        Text(
            name,
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = NotoKufiArabic),
            color = if (faint) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).clearAndSetSemantics {},
        )
        Text(
            if (time != null) stringResource(R.string.log_status_line, short, format.time(time.time)) else short,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/** The same marks as Home: prayed is a filled circle with a tick, not marked an outline, not yet due a faint one. */
@Composable
internal fun Mark(state: LogState, size: androidx.compose.ui.unit.Dp = 28.dp) {
    val colors = MaterialTheme.colorScheme
    val skin = LocalSkin.current
    when (state) {
        LogState.PRAYED -> Box(Modifier.size(size).background(skin.mark, CircleShape), contentAlignment = Alignment.Center) {
            // The cell's state already says "Prayed"; TalkBack shouldn't also read "check mark".
            Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = skin.onMark, modifier = Modifier.size(size * 4 / 7))
        }
        LogState.NOT_MARKED -> Box(Modifier.size(size).border(2.dp, colors.primary, CircleShape))
        LogState.NOT_YET_DUE -> Box(Modifier.size(size).border(1.5.dp, skin.track, CircleShape))
    }
}

/** The week strip's day names: «سبت» … «جمعة» in Arabic (the design's), "Sat" … "Fri" otherwise. */
@Composable
private fun shortWeekday(date: java.time.LocalDate): String {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    if (locale.language != "ar") return date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, locale)
    return when (date.dayOfWeek) {
        java.time.DayOfWeek.SATURDAY -> "سبت"
        java.time.DayOfWeek.SUNDAY -> "أحد"
        java.time.DayOfWeek.MONDAY -> "إثنين"
        java.time.DayOfWeek.TUESDAY -> "ثلاثاء"
        java.time.DayOfWeek.WEDNESDAY -> "أربعاء"
        java.time.DayOfWeek.THURSDAY -> "خميس"
        java.time.DayOfWeek.FRIDAY -> "جمعة"
        null -> ""
    }
}
