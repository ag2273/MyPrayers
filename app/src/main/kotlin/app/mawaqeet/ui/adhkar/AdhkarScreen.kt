package app.mawaqeet.ui.adhkar

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import app.mawaqeet.R
import app.mawaqeet.core.Adhkar
import app.mawaqeet.core.AdhkarSetId
import app.mawaqeet.core.AdhkarSlot
import app.mawaqeet.core.Prayer
import app.mawaqeet.core.DhikrProgress
import app.mawaqeet.core.SetProgress
import app.mawaqeet.ui.LocalDisplayFormatter
import app.mawaqeet.ui.settings.nameRes
import app.mawaqeet.ui.components.Card
import app.mawaqeet.ui.components.NavRow
import app.mawaqeet.ui.components.RowDivider
import app.mawaqeet.ui.components.SectionTitle
import app.mawaqeet.ui.components.Segment
import app.mawaqeet.ui.components.SegmentedChoice
import app.mawaqeet.ui.components.SubPage
import app.mawaqeet.ui.components.TabPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The set's name in the UI language. */
@get:StringRes
val AdhkarSetId.nameRes: Int
    get() = when (this) {
        AdhkarSetId.MORNING -> R.string.adhkar_morning
        AdhkarSetId.EVENING -> R.string.adhkar_evening
        AdhkarSetId.AFTER_PRAYER -> R.string.adhkar_after_prayer
    }

/** The slot's name: the set's, or «أذكار بعد صلاة الفجر» for one Prayer's after-prayer adhkar. */
@Composable
private fun slotName(slot: AdhkarSlot): String =
    slot.prayer?.let { stringResource(R.string.adhkar_after_prayer_name, stringResource(it.nameRes)) }
        ?: stringResource(slot.set.nameRes)

/**
 * The «الأذكار» tab (#62, #70, #71, #74): the morning and evening sets and the five after-prayer slots with today's
 * state; one opens in the reader. The Adhkar Record's week grid opens from the end of the list, and the free Tasbeeh closes it. [adhkar] is null while the bundled catalog loads. [changes] signals every write to the
 * Adhkar repository. [openSlot] (an adhkar reminder's tap, #75) opens that set in the reader, then [onOpened] is called.
 */
@Composable
fun AdhkarScreen(
    adhkar: Adhkar?,
    changes: StateFlow<Int>,
    openSlot: AdhkarSlot? = null,
    onOpened: () -> Unit = {},
    /** Which hand counts the Tasbeeh (Settings → Language and display), and how to change it. */
    tasbeehHand: TasbeehHand = TasbeehHand.RIGHT,
    onChangeHand: () -> Unit = {},
) {
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var record by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(openSlot) {
        if (openSlot == null) return@LaunchedEffect
        record = false
        open = openSlot.key
        onOpened()
    }
    val reading = open?.let(AdhkarSlot::ofKey)
    when {
        adhkar != null && reading != null ->
            SetReader(adhkar, reading, changes, onSwitch = { open = it.key }, onBack = { open = null })
        adhkar != null && record -> AdhkarRecordPage(adhkar, changes, onBack = { record = false })
        else -> SetList(adhkar, changes, tasbeehHand, onChangeHand, onOpen = { open = it.key }, onOpenRecord = { record = true })
    }
}

/** What the list shows now: every slot's progress, the Prayer after-prayer adhkar default to, and the Tasbeeh total. */
private data class AdhkarToday(val slots: List<SetProgress>, val defaultPrayer: Prayer, val tasbeeh: Int)

@Composable
private fun SetList(
    adhkar: Adhkar?,
    changes: StateFlow<Int>,
    hand: TasbeehHand,
    onChangeHand: () -> Unit,
    onOpen: (AdhkarSlot) -> Unit,
    onOpenRecord: () -> Unit,
) {
    val version by changes.collectAsState()
    val today by produceState<AdhkarToday?>(null, adhkar, version) {
        value = adhkar?.let { a ->
            withContext(Dispatchers.IO) { AdhkarToday(a.slots.map(a::progress), a.defaultAfterPrayer(), a.tasbeeh()) }
        }
    }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    TabPage(stringResource(R.string.tab_adhkar)) {
        val shown = today ?: return@TabPage
        val (daily, afterPrayer) = shown.slots.partition { it.set != AdhkarSetId.AFTER_PRAYER }
        if (daily.isNotEmpty()) {
            Card {
                daily.forEachIndexed { index, set ->
                    if (index > 0) RowDivider()
                    NavRow(stringResource(set.set.nameRes), summary(set), icon = R.drawable.ic_adhkar) { onOpen(set.slot) }
                }
            }
        }
        if (afterPrayer.isNotEmpty()) {
            Card {
                // Opens on the most recent Prayer whose time has arrived; the reader can switch before starting.
                NavRow(
                    stringResource(R.string.adhkar_after_prayer),
                    stringResource(R.string.adhkar_after_prayer_slot, stringResource(shown.defaultPrayer.nameRes)),
                    icon = R.drawable.ic_adhkar,
                ) { onOpen(AdhkarSlot.afterPrayer(shown.defaultPrayer)) }
                afterPrayer.forEach { set ->
                    val prayer = checkNotNull(set.slot.prayer)
                    RowDivider()
                    NavRow(stringResource(R.string.adhkar_after_prayer_slot, stringResource(prayer.nameRes)), summary(set)) {
                        onOpen(set.slot)
                    }
                }
            }
        }
        Card {
            NavRow(stringResource(R.string.adhkar_record), stringResource(R.string.adhkar_record_summary), icon = R.drawable.ic_log, onClick = onOpenRecord)
        }
        // The Tasbeeh closes the list, as on the design: the round button under the counting hand.
        if (adhkar != null) {
            SectionTitle(stringResource(R.string.tasbeeh))
            // Each write changes the repository, which re-reads [today]: the total shown is always the stored one.
            TasbeehPad(
                total = shown.tasbeeh,
                hand = hand,
                onChangeHand = onChangeHand,
                onCount = {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    scope.launch(Dispatchers.IO) { adhkar.countTasbeeh() }
                },
                onReset = { scope.launch(Dispatchers.IO) { adhkar.resetTasbeeh() } },
            )
        }
    }
}

/** A slot's state today, under its name in the list. */
@Composable
private fun summary(set: SetProgress): String {
    val format = LocalDisplayFormatter.current
    val current = set.currentIndex
    val started = set.items.any { it.counted > 0 }
    return when {
        set.isComplete -> stringResource(R.string.adhkar_done_today)
        current == null && started -> stringResource(R.string.adhkar_read_outside_time)
        !started && !set.isInTime -> stringResource(
            when (set.set) {
                AdhkarSetId.MORNING -> R.string.adhkar_time_morning
                AdhkarSetId.EVENING -> R.string.adhkar_time_evening
                AdhkarSetId.AFTER_PRAYER -> R.string.adhkar_not_yet_time
            },
        )
        current == null || !started -> stringResource(R.string.adhkar_not_started)
        else -> stringResource(R.string.adhkar_resume, format.number(current + 1), format.number(set.items.size))
    }
}

/** Why finishing [set] now won't go in the Adhkar Record; it can still be read. */
@Composable
private fun outsideTimeNote(set: SetProgress): String = when (set.set) {
    AdhkarSetId.MORNING -> stringResource(R.string.adhkar_outside_morning)
    AdhkarSetId.EVENING -> stringResource(R.string.adhkar_outside_evening)
    AdhkarSetId.AFTER_PRAYER -> stringResource(R.string.adhkar_outside_after_prayer, stringResource(checkNotNull(set.slot.prayer).nameRes))
}

/**
 * One set, one dhikr card at a time. Tapping anywhere on the card counts one with a light vibration; at the
 * dhikr's count the next one not yet said shows. «السابق» and «التالي» move a whole dhikr back or on without
 * counting; a skipped dhikr stays unsaid, so the set completes only once it is said too. Progress is the core's, per
 * Prayer Day, so leaving and returning resumes at the first dhikr not yet said.
 */
@Composable
private fun SetReader(
    adhkar: Adhkar,
    set: AdhkarSlot,
    changes: StateFlow<Int>,
    onSwitch: (AdhkarSlot) -> Unit,
    onBack: () -> Unit,
) {
    val version by changes.collectAsState()
    var progress by remember(adhkar, set) { mutableStateOf<SetProgress?>(null) }
    // The dhikr shown, when moved with «السابق» or «التالي»; null follows the first dhikr not yet said.
    var moved by rememberSaveable(set.key) { mutableStateOf<Int?>(null) }
    LaunchedEffect(adhkar, set, version) { progress = withContext(Dispatchers.IO) { adhkar.progress(set) } }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val format = LocalDisplayFormatter.current
    val name = slotName(set)
    SubPage(stringResource(set.set.nameRes), onBack) {
        val shown = progress ?: return@SubPage
        val index = moved?.takeIf { it in shown.items.indices } ?: shown.currentIndex
        val prayer = set.prayer
        // After-prayer adhkar: which Prayer they are for can change until the first count.
        if (prayer != null && shown.items.none { it.counted > 0 }) {
            Text(stringResource(R.string.adhkar_after_which_prayer), style = MaterialTheme.typography.titleSmall)
            SegmentedChoice(
                Prayer.entries.map { p ->
                    Segment(stringResource(p.nameRes), selected = p == prayer, onSelect = { onSwitch(AdhkarSlot.afterPrayer(p)) })
                },
            )
        }
        if (!shown.isInTime && !shown.isComplete) {
            Text(
                outsideTimeNote(shown),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (index == null) {
            if (shown.isComplete) Complete(name) else ReadOutsideTime(name)
        } else {
            Text(
                stringResource(R.string.adhkar_position, format.number(index + 1), format.number(shown.items.size)),
                style = MaterialTheme.typography.titleMedium,
            )
            if (shown.isComplete) {
                Text(
                    stringResource(R.string.adhkar_done_today),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            DhikrCard(shown.items[index]) {
                if (shown.items[index].isDone) return@DhikrCard
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                scope.launch {
                    val after = withContext(Dispatchers.IO) { adhkar.count(set, index) }
                    progress = after
                    // At its count, on to the next dhikr not yet said.
                    if (after.items[index].isDone) moved = after.nextUndone(index)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { moved = index - 1 },
                    enabled = index > 0,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.adhkar_previous)) }
                OutlinedButton(
                    onClick = { moved = index + 1 },
                    enabled = index < shown.items.lastIndex,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.adhkar_next)) }
            }
            Text(
                stringResource(R.string.adhkar_tap_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(
            onClick = {
                moved = null
                scope.launch { progress = withContext(Dispatchers.IO) { adhkar.restart(set) } }
            },
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.adhkar_restart))
        }
    }
}

/** The dhikr's Arabic text, its source and the count; the whole card is one tap target and one TalkBack item. */
@Composable
private fun DhikrCard(item: DhikrProgress, onCount: () -> Unit) {
    val format = LocalDisplayFormatter.current
    val state = stringResource(
        R.string.adhkar_card_state,
        format.number(item.counted),
        format.number(item.dhikr.count),
        format.number(item.left),
    )
    Card(
        Modifier
            .clickable(onClickLabel = stringResource(R.string.adhkar_count_one), onClick = onCount)
            .semantics(mergeDescendants = true) { stateDescription = state },
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = 240.dp).padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Always Arabic, right to left, even in the English UI.
            val arabic = TextStyle(textDirection = TextDirection.Rtl, textAlign = TextAlign.Center)
            // A long dhikr at a large font scrolls inside the card, so its source and count stay in view (#72).
            val textHeight = (LocalConfiguration.current.screenHeightDp * 0.4f).dp
            Box(Modifier.fillMaxWidth().heightIn(max = textHeight).verticalScroll(rememberScrollState())) {
                Text(item.dhikr.text, style = MaterialTheme.typography.headlineSmall.merge(arabic), modifier = Modifier.fillMaxWidth())
            }
            Text(
                item.dhikr.source,
                style = MaterialTheme.typography.bodyMedium.merge(arabic),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                format.count(item.counted, item.dhikr.count),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Every dhikr said, outside the set's time: nothing is recorded, and that is said plainly. */
@Composable
private fun ReadOutsideTime(name: String) {
    Card {
        Text(
            stringResource(R.string.adhkar_set_read, name),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth().padding(20.dp),
        )
    }
}

@Composable
private fun Complete(name: String) {
    Card {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.adhkar_set_complete, name), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.adhkar_set_complete_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
