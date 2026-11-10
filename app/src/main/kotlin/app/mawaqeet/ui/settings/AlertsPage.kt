package app.mawaqeet.ui.settings

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.mawaqeet.ui.components.Card
import app.mawaqeet.ui.components.NavRow
import app.mawaqeet.ui.components.RowDivider
import app.mawaqeet.ui.components.RowText
import app.mawaqeet.ui.components.SectionTitle
import app.mawaqeet.ui.components.SwitchRow
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.mawaqeet.R
import app.mawaqeet.alerts.AdhanPreview
import app.mawaqeet.alerts.AdhanSounds
import app.mawaqeet.alerts.CustomAdhanFiles
import app.mawaqeet.core.AdhanRecording
import app.mawaqeet.core.AdhanSoundChoice
import app.mawaqeet.core.AlertSettings
import app.mawaqeet.core.AlertSound
import app.mawaqeet.core.Prayer
import app.mawaqeet.core.PrayerAlertSettings
import app.mawaqeet.ui.LocalDisplayFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lead times offered for a Prayer Reminder; the core rejects anything below [PrayerAlertSettings.MIN_REMINDER_MINUTES]. */
private val REMINDER_MINUTES = listOf(10, 15, 20, 30, 45, 60)

/**
 * Settings → Alerts & adhan: one row per Prayer with its Adhan Alert switch (the row opens the Prayer's own page), how
 * alerts behave (alarm-clock mode, play on silent, vibration), then the Custom Adhan files.
 */
@Composable
internal fun AlertsPage(alerts: AlertSettings, actions: SettingsActions, onOpenPrayer: (Prayer) -> Unit) {
    SectionTitle(stringResource(R.string.alerts_each_prayer))
    Card {
        Prayer.entries.forEachIndexed { index, prayer ->
            if (index > 0) RowDivider()
            val alert = alerts.of(prayer)
            PrayerAlertRow(
                name = stringResource(prayer.nameRes),
                summary = summary(prayer, alert),
                on = alert.adhanAlert,
                onToggle = { on -> actions.updatePrayerAlert(prayer) { it.copy(adhanAlert = on) } },
                onOpen = { onOpenPrayer(prayer) },
            )
        }
    }
    SectionTitle(stringResource(R.string.alerts_behaviour))
    Card {
        SwitchRow(
            title = stringResource(R.string.alerts_alarm_clock),
            description = stringResource(R.string.alerts_alarm_clock_description),
            checked = alerts.alarmClockMode,
            onChange = actions::setAlarmClockMode,
        )
        RowDivider()
        SwitchRow(
            title = stringResource(R.string.alerts_play_on_silent),
            description = stringResource(R.string.alerts_play_on_silent_description),
            checked = alerts.playOnSilent,
            onChange = actions::setPlayOnSilent,
        )
        RowDivider()
        SwitchRow(
            title = stringResource(R.string.alerts_vibration),
            description = "",
            checked = alerts.vibration,
            onChange = actions::setVibration,
        )
    }
    CustomAdhanRows(alerts, actions)
}

/** A Prayer's row: tapping its name opens the Prayer's page; the switch beside it turns its Adhan Alert on or off. */
@Composable
private fun PrayerAlertRow(name: String, summary: String, on: Boolean, onToggle: (Boolean) -> Unit, onOpen: () -> Unit) {
    val switchLabel = stringResource(R.string.alerts_adhan_alert_of, name)
    Row(Modifier.fillMaxWidth().padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .clickable(role = Role.Button, onClick = onOpen)
                .heightIn(min = 60.dp)
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RowText(name, summary)
            Icon(painterResource(R.drawable.ic_chevron), contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
        }
        Switch(
            checked = on,
            onCheckedChange = onToggle,
            modifier = Modifier.semantics { contentDescription = switchLabel },
        )
    }
}

/** One Prayer's alerts on their own page: the Adhan Alert and its sound, then the Prayer Reminder and its lead time. */
@Composable
internal fun PrayerAlertPage(
    prayer: Prayer,
    alert: PrayerAlertSettings,
    onChange: ((PrayerAlertSettings) -> PrayerAlertSettings) -> Unit,
) {
    Card {
        SwitchRow(stringResource(R.string.alerts_adhan_alert), "", alert.adhanAlert) { on -> onChange { it.copy(adhanAlert = on) } }
    }
    if (alert.adhanAlert) {
        SectionTitle(stringResource(R.string.alerts_sound))
        Card {
            Column(Modifier.selectableGroup()) {
                AdhanSoundChoice.entries.forEach { sound ->
                    ChoiceRow(stringResource(soundLabel(prayer, sound)), "", sound == alert.sound) { onChange { it.copy(sound = sound) } }
                }
            }
        }
    }
    Card {
        SwitchRow(stringResource(R.string.alerts_reminder), stringResource(R.string.alerts_reminder_description), alert.reminder) { on ->
            onChange { it.copy(reminder = on) }
        }
        if (alert.reminder) {
            RowDivider()
            Column(Modifier.selectableGroup()) {
                REMINDER_MINUTES.forEach { minutes ->
                    ChoiceRow(minutesLabel(minutes), "", minutes == alert.reminderMinutes) {
                        onChange { it.copy(reminderMinutes = minutes) }
                    }
                }
            }
        }
    }
}

@Composable
private fun summary(prayer: Prayer, alert: PrayerAlertSettings): String {
    val adhan = if (alert.adhanAlert) stringResource(soundLabel(prayer, alert.sound)) else stringResource(R.string.alerts_adhan_off)
    return if (alert.reminder) stringResource(R.string.alerts_summary_with_reminder, adhan, minutesLabel(alert.reminderMinutes)) else adhan
}

@Composable
private fun minutesLabel(minutes: Int): String =
    stringResource(R.string.alerts_reminder_minutes, LocalDisplayFormatter.current.number(minutes))

@StringRes
private fun soundLabel(prayer: Prayer, sound: AdhanSoundChoice): Int = when (sound) {
    AdhanSoundChoice.SHORT -> R.string.alerts_sound_short
    AdhanSoundChoice.FULL -> if (prayer == Prayer.FAJR) R.string.alerts_sound_full_fajr else R.string.alerts_sound_full
    AdhanSoundChoice.SILENT -> R.string.alerts_sound_silent
}

/**
 * Custom Adhan (#28): one row per [AdhanRecording], each chosen, previewed and cleared on its own. The Full Adhan's
 * never changes Fajr, which keeps its own adhan with «الصلاة خير من النوم» unless a Custom Adhan is chosen for Fajr.
 */
@Composable
private fun CustomAdhanRows(alerts: AlertSettings, actions: SettingsActions) {
    val context = LocalContext.current
    var editing by rememberSaveable { mutableStateOf<AdhanRecording?>(null) }
    // Each chosen file's name, or null once it can't be read; checked again on every resume (it may be deleted meanwhile).
    var names by remember { mutableStateOf<Map<AdhanRecording, String?>>(emptyMap()) }
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumes++
        onPauseOrDispose { }
    }
    LaunchedEffect(alerts.customAdhans, resumes) {
        names = withContext(Dispatchers.IO) {
            alerts.customAdhans.mapValues { (_, reference) -> CustomAdhanFiles.readableName(context, reference) }
        }
    }
    fun state(recording: AdhanRecording): CustomAdhanState {
        val reference = alerts.customAdhan(recording)
        val checked = reference != null && recording in names
        return CustomAdhanState(reference, names[recording], unreadable = checked && names[recording] == null)
    }

    SectionTitle(stringResource(R.string.alerts_custom_adhan))
    Text(
        stringResource(R.string.alerts_custom_adhan_description),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 6.dp),
    )
    Card {
        AdhanRecording.entries.forEachIndexed { index, recording ->
            if (index > 0) RowDivider()
            val current = state(recording)
            NavRow(
                title = stringResource(recording.labelRes),
                summary = current.summary(),
                onClick = { editing = recording },
            )
            if (current.unreadable) Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { UnreadableNotice() }
        }
    }
    editing?.let { recording ->
        CustomAdhanDialog(recording, state(recording), actions, onDismiss = { editing = null })
    }
}

/** A recording's Custom Adhan as Settings shows it: [name] is null until checked, or when [unreadable]. */
private data class CustomAdhanState(val reference: String?, val name: String?, val unreadable: Boolean)

@Composable
private fun CustomAdhanState.summary(): String = when {
    reference == null -> stringResource(R.string.alerts_custom_bundled)
    name != null -> name
    unreadable -> stringResource(R.string.alerts_custom_bundled)
    else -> stringResource(R.string.alerts_custom_chosen)
}

@Composable
private fun UnreadableNotice() {
    Text(
        stringResource(R.string.alerts_custom_unreadable),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun CustomAdhanDialog(recording: AdhanRecording, current: CustomAdhanState, actions: SettingsActions, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val preview = remember { AdhanPreview(context) }
    var playing by remember { mutableStateOf(false) }
    DisposableEffect(preview) { onDispose { preview.stop() } }
    fun stopPreview() {
        preview.stop()
        playing = false
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            stopPreview()
            actions.setCustomAdhan(recording, uri)
        }
    }
    // What the Adhan Alert would play now: the chosen file, or the bundled recording when none is chosen or it's unreadable.
    val previewUri = current.reference?.takeIf { !current.unreadable }?.let(Uri::parse)
        ?: AdhanSounds.bundled(context, AlertSound.bundled(recording))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(recording.labelRes)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (recording == AdhanRecording.FULL) Text(
                    stringResource(R.string.alerts_custom_full_not_fajr),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(current.summary(), style = MaterialTheme.typography.bodyLarge)
                if (current.unreadable) UnreadableNotice()
                TextButton(onClick = { picker.launch(CustomAdhanFiles.MIME_TYPES) }) {
                    Text(stringResource(R.string.alerts_custom_choose))
                }
                TextButton(
                    onClick = {
                        if (playing) {
                            stopPreview()
                        } else {
                            playing = true
                            preview.play(previewUri) { playing = false }
                        }
                    },
                ) {
                    Text(stringResource(if (playing) R.string.alerts_custom_preview_stop else R.string.alerts_custom_preview))
                }
                if (current.reference != null) TextButton(
                    onClick = {
                        stopPreview()
                        actions.setCustomAdhan(recording, null)
                    },
                ) {
                    Text(stringResource(R.string.alerts_custom_clear))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}

@get:StringRes
private val AdhanRecording.labelRes: Int
    get() = when (this) {
        AdhanRecording.SHORT -> R.string.alerts_sound_short
        AdhanRecording.FULL -> R.string.alerts_custom_full
        AdhanRecording.FAJR_FULL -> R.string.alerts_custom_fajr_full
    }

@get:StringRes
internal val Prayer.nameRes: Int
    get() = when (this) {
        Prayer.FAJR -> R.string.prayer_fajr
        Prayer.DHUHR -> R.string.prayer_dhuhr
        Prayer.ASR -> R.string.prayer_asr
        Prayer.MAGHRIB -> R.string.prayer_maghrib
        Prayer.ISHA -> R.string.prayer_isha
    }
