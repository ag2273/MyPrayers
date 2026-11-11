package app.mawaqeet.ui.adhkar

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import app.mawaqeet.R
import app.mawaqeet.core.Adhkar
import app.mawaqeet.core.AdhkarSetId
import app.mawaqeet.core.DhikrProgress
import app.mawaqeet.core.SetProgress
import app.mawaqeet.ui.LocalDisplayFormatter
import app.mawaqeet.ui.components.Card
import app.mawaqeet.ui.components.NavRow
import app.mawaqeet.ui.components.RowDivider
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
    }

/**
 * The «الأذكار» tab (#62): the Adhkar Sets with today's state; one opens in the reader. [adhkar] is null while the
 * bundled catalog loads. [changes] signals every write to the Adhkar repository.
 */
@Composable
fun AdhkarScreen(adhkar: Adhkar?, changes: StateFlow<Int>) {
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val openSet = open?.let(AdhkarSetId::ofKey)
    if (adhkar != null && openSet != null) {
        SetReader(adhkar, openSet, changes, onBack = { open = null })
    } else {
        SetList(adhkar, changes, onOpen = { open = it.key })
    }
}

@Composable
private fun SetList(adhkar: Adhkar?, changes: StateFlow<Int>, onOpen: (AdhkarSetId) -> Unit) {
    val version by changes.collectAsState()
    val progress by produceState<List<SetProgress>>(emptyList(), adhkar, version) {
        value = adhkar?.let { a -> withContext(Dispatchers.IO) { a.sets.map { a.progress(it.id) } } }.orEmpty()
    }
    val format = LocalDisplayFormatter.current
    TabPage(stringResource(R.string.tab_adhkar)) {
        if (progress.isEmpty()) return@TabPage
        Card {
            progress.forEachIndexed { index, set ->
                if (index > 0) RowDivider()
                val current = set.currentIndex
                val summary = when {
                    set.isComplete -> stringResource(R.string.adhkar_done_today)
                    current == null || set.items.none { it.counted > 0 } -> stringResource(R.string.adhkar_not_started)
                    else -> stringResource(R.string.adhkar_resume, format.number(current + 1), format.number(set.items.size))
                }
                NavRow(stringResource(set.set.nameRes), summary, icon = R.drawable.ic_adhkar) { onOpen(set.set) }
            }
        }
    }
}

/**
 * One set, one dhikr card at a time. Tapping anywhere on the card counts one with a light vibration; at the
 * dhikr's count the next one shows. Progress is the core's, per Prayer Day, so leaving and returning resumes.
 */
@Composable
private fun SetReader(adhkar: Adhkar, set: AdhkarSetId, changes: StateFlow<Int>, onBack: () -> Unit) {
    val version by changes.collectAsState()
    var progress by remember(adhkar, set) { mutableStateOf<SetProgress?>(null) }
    LaunchedEffect(adhkar, set, version) { progress = withContext(Dispatchers.IO) { adhkar.progress(set) } }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val format = LocalDisplayFormatter.current
    val name = stringResource(set.nameRes)
    SubPage(name, onBack) {
        val shown = progress ?: return@SubPage
        val index = shown.currentIndex
        if (index == null) {
            Complete(name)
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
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                scope.launch { progress = withContext(Dispatchers.IO) { adhkar.count(set) } }
            }
            Text(
                stringResource(R.string.adhkar_tap_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(
            onClick = { scope.launch { progress = withContext(Dispatchers.IO) { adhkar.restart(set) } } },
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
            Text(item.dhikr.text, style = MaterialTheme.typography.headlineSmall.merge(arabic), modifier = Modifier.fillMaxWidth())
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
