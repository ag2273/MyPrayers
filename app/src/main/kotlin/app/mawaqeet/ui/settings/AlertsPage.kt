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
import androidx.compose.material3.Slider
import androidx.compose.ui.semantics.stateDescription
import android.media.AudioManager
import kotlin.math.roundToInt
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
import app.mawaqeet.core.AdhanVolume
import app.mawaqeet.core.AlertSettings
import app.mawaqeet.core.AlertSound
import app.mawaqeet.core.Prayer
import app.mawaqeet.core.PrayerAlertSettings
import app.mawaqeet.core.PrayerSilence
import app.mawaqeet.core.SilenceMode
import app.mawaqeet.ui.components.Segment
import app.mawaqeet.ui.components.SegmentedChoice
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextAlign
import app.mawaqeet.ui.LocalDisplayFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lead times offered for a Prayer Reminder; the core rejects anything below [PrayerAlertSettings.MIN_REMINDER_MINUTES]. */
private val REMINDER_MINUTES = listOf(10, 15, 20, 30, 45, 60)

/**
 * Settings → Alerts & adhan: the All alerts master switch, then one row per Prayer with its Adhan Alert switch (the row opens the Prayer's own page), how
 * alerts behave (alarm-clock mode, play on silent, vibration), the adhan's volume (alarm or media), then the Custom
 * Adhan files.
 */
@Composable
internal fun AlertsPage(alerts: AlertSettings, actions: SettingsActions, onOpenPrayer: (Prayer) -> Unit) {
    // The master switch. Off, nothing below applies, so it is hidden until alerts are turned back on.
    Card {
        SwitchRow(
            title = stringResource(R.string.alerts_all),
            description = stringResource(if (alerts.enabled) R.string.alerts_all_on_description else R.string.alerts_all_off_description),
            checked = alerts.enabled,
            onChange = actions::setAlertsEnabled,
        )
    }
    PrayerSilenceRows(alerts, actions)
    if (!alerts.enabled) return
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
        if (alerts.alarmClockMode) LockScreenPermissionRow(actions)
        RowDivider()
        TryAlertRow(actions)
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
    AdhanVolumeRows(alerts, actions)
    CustomAdhanRows(alerts, actions)
}

/**
 * «الصامت وقت الصلاة» (#61), shown whatever the master switch says. The switch reads on only while Prayer Silence is
 * turned on AND Do Not Disturb access is granted (checked on every resume). Turning it on without access stores the
 * choice and opens the system page; until access is given (or after it is revoked) a Grant row explains why.
 */
@Composable
private fun PrayerSilenceRows(alerts: AlertSettings, actions: SettingsActions) {
    var access by remember { mutableStateOf(actions.hasDoNotDisturbAccess()) }
    LifecycleResumeEffect(Unit) {
        access = actions.hasDoNotDisturbAccess()
        onPauseOrDispose { }
    }
    val wanted = alerts.prayerSilence.enabled
    Card {
        SwitchRow(
            title = stringResource(R.string.prayer_silence),
            description = stringResource(R.string.prayer_silence_description),
            checked = wanted && access,
            onChange = { on ->
                actions.setPrayerSilenceEnabled(on)
                if (on && !access) actions.openDoNotDisturbAccess()
            },
        )
        if (wanted && !access) {
            RowDivider()
            NavRow(
                stringResource(R.string.prayer_silence_access),
                stringResource(R.string.prayer_silence_access_summary),
                icon = R.drawable.ic_warning,
                trailing = { Text(stringResource(R.string.grant_button), color = MaterialTheme.colorScheme.primary) },
                onClick = actions::openDoNotDisturbAccess,
            )
        }
        if (wanted && access) {
            RowDivider()
            val mode = alerts.prayerSilence.mode
            SegmentedChoice(
                listOf(
                    Segment(stringResource(R.string.prayer_silence_mode_silent), mode == SilenceMode.SILENT) {
                        actions.setPrayerSilenceMode(SilenceMode.SILENT)
                    },
                    Segment(stringResource(R.string.prayer_silence_mode_vibrate), mode == SilenceMode.VIBRATE) {
                        actions.setPrayerSilenceMode(SilenceMode.VIBRATE)
                    },
                ),
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
    if (!wanted || !access) return
    val silence = alerts.prayerSilence
    SectionTitle(stringResource(R.string.prayer_silence_prayers))
    Card {
        Prayer.entries.forEachIndexed { index, prayer ->
            if (index > 0) RowDivider()
            SwitchRow(stringResource(prayer.nameRes), "", silence.isOn(prayer)) { on -> actions.setPrayerSilencePrayer(prayer, on) }
        }
    }
    Card {
        SilenceDurationRow(
            title = stringResource(R.string.prayer_silence_duration),
            summary = stringResource(R.string.prayer_silence_duration_summary),
            minutes = silence.minutes,
            range = PrayerSilence.MINUTES_RANGE,
            shorter = R.string.prayer_silence_shorter,
            longer = R.string.prayer_silence_longer,
        ) { step -> actions.stepPrayerSilenceMinutes(jumuah = false, step = step) }
        RowDivider()
        SilenceDurationRow(
            title = stringResource(R.string.prayer_silence_jumuah),
            summary = stringResource(R.string.prayer_silence_jumuah_summary),
            minutes = silence.jumuahMinutes,
            range = PrayerSilence.JUMUAH_MINUTES_RANGE,
            shorter = R.string.prayer_silence_jumuah_shorter,
            longer = R.string.prayer_silence_jumuah_longer,
        ) { step -> actions.stepPrayerSilenceMinutes(jumuah = true, step = step) }
    }
}

/** A Silence Window's duration: its title and summary, then −, the minutes and +, [PrayerSilence.MINUTES_STEP] per tap. */
@Composable
private fun SilenceDurationRow(
    title: String,
    summary: String,
    minutes: Int,
    range: IntRange,
    @StringRes shorter: Int,
    @StringRes longer: Int,
    onStep: (Int) -> Unit,
) {
    val format = LocalDisplayFormatter.current
    val step = format.number(PrayerSilence.MINUTES_STEP)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RowText(title, summary)
        StepButton(R.drawable.ic_minus, stringResource(shorter, step), enabled = minutes > range.first) { onStep(-1) }
        Text(
            pluralStringResource(R.plurals.prayer_silence_minutes, minutes, format.number(minutes)),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 80.dp),
        )
        StepButton(R.drawable.ic_plus, stringResource(longer, step), enabled = minutes < range.last) { onStep(+1) }
    }
}

/**
 * Adhan volume: whether the adhan follows the alarm volume or the media volume, a slider that sets that volume right
 * here (it is the phone's own volume, so it also changes alarms or media), and a button to hear the Short Adhan at it.
 */
@Composable
private fun AdhanVolumeRows(alerts: AlertSettings, actions: SettingsActions) {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    val volume = alerts.adhanVolume
    val stream = AdhanSounds.stream(volume)
    val max = remember(stream) { audio.getStreamMaxVolume(stream).coerceAtLeast(1) }
    val min = remember(stream) { if (android.os.Build.VERSION.SDK_INT >= 28) audio.getStreamMinVolume(stream) else 0 }
    var level by remember(stream) { mutableStateOf(audio.getStreamVolume(stream).toFloat()) }
    // The volume keys or another app may change it meanwhile.
    LifecycleResumeEffect(stream) {
        level = audio.getStreamVolume(stream).toFloat()
        onPauseOrDispose { }
    }
    val preview = remember { AdhanPreview(context) }
    var playing by remember { mutableStateOf(false) }
    DisposableEffect(preview) { onDispose { preview.stop() } }
    LaunchedEffect(volume) {
        preview.stop()
        playing = false
    }

    SectionTitle(stringResource(R.string.alerts_volume))
    Card {
        Column(Modifier.selectableGroup()) {
            ChoiceRow(
                stringResource(R.string.alerts_volume_alarm),
                stringResource(R.string.alerts_volume_alarm_description),
                volume == AdhanVolume.ALARM,
            ) { actions.setAdhanVolume(AdhanVolume.ALARM) }
            ChoiceRow(
                stringResource(R.string.alerts_volume_media),
                stringResource(R.string.alerts_volume_media_description),
                volume == AdhanVolume.MEDIA,
            ) { actions.setAdhanVolume(AdhanVolume.MEDIA) }
        }
        RowDivider()
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            val percent = ((level - min) / (max - min).coerceAtLeast(1) * 100).roundToInt().coerceIn(0, 100)
            val label = stringResource(R.string.alerts_volume_level_value, LocalDisplayFormatter.current.number(percent))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(if (volume == AdhanVolume.ALARM) R.string.alerts_volume_level_alarm else R.string.alerts_volume_level_media),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = level,
                onValueChange = { new ->
                    level = new.roundToInt().toFloat()
                    // Do Not Disturb can forbid changing a stream's volume: keep what the phone allows.
                    runCatching { audio.setStreamVolume(stream, level.toInt(), 0) }
                },
                onValueChangeFinished = { level = audio.getStreamVolume(stream).toFloat() },
                valueRange = min.toFloat()..max.toFloat(),
                steps = (max - min - 1).coerceAtLeast(0),
                modifier = Modifier.semantics { stateDescription = label },
            )
            TextButton(
                onClick = {
                    if (playing) {
                        preview.stop()
                        playing = false
                    } else {
                        playing = true
                        val sound = AlertSound.of(AdhanRecording.SHORT, alerts)
                        preview.play(AdhanSounds.uri(context, sound), volume) { playing = false }
                    }
                },
            ) {
                Text(stringResource(if (playing) R.string.alerts_custom_preview_stop else R.string.alerts_volume_test))
            }
        }
    }
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
    /** Recordings replaced by a Custom Adhan: their choice names no reciter. */
    customised: Set<AdhanRecording> = emptySet(),
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
                    ChoiceRow(stringResource(soundLabel(prayer, sound)), soundReciter(prayer, sound, customised)?.let { stringResource(it) } ?: "", sound == alert.sound) { onChange { it.copy(sound = sound) } }
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

/** Who recites the bundled recording [sound] plays for [prayer]; null for silence or a [customised] recording. */
@StringRes
private fun soundReciter(prayer: Prayer, sound: AdhanSoundChoice, customised: Set<AdhanRecording>): Int? =
    if (AdhanRecording.of(prayer, sound) in customised) null else when (sound) {
    AdhanSoundChoice.SHORT -> R.string.alerts_reciter_short
    AdhanSoundChoice.FULL -> if (prayer == Prayer.FAJR) R.string.alerts_reciter_fajr else R.string.alerts_reciter_full
    AdhanSoundChoice.SILENT -> null
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
        CustomAdhanDialog(recording, state(recording), alerts.adhanVolume, actions, onDismiss = { editing = null })
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
private fun CustomAdhanDialog(
    recording: AdhanRecording,
    current: CustomAdhanState,
    volume: AdhanVolume,
    actions: SettingsActions,
    onDismiss: () -> Unit,
) {
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
                            preview.play(previewUri, volume) { playing = false }
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

/**
 * In alarm-clock mode on Android 14+, where the user can stop an app showing over the lock screen: a row to allow it,
 * shown only while it's off. Checked again each time the page comes back.
 */
@Composable
private fun LockScreenPermissionRow(actions: SettingsActions) {
    var allowed by remember { mutableStateOf(actions.canShowOverLockScreen()) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) allowed = actions.canShowOverLockScreen()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    if (allowed) return
    RowDivider()
    NavRow(
        stringResource(R.string.alerts_lock_screen_permission),
        stringResource(R.string.alerts_lock_screen_permission_summary),
        icon = R.drawable.ic_warning,
        onClick = actions::openLockScreenPermission,
    )
}

/** Try it: an Adhan Alert for the next Prayer in 10 seconds, so the person can lock the phone and see how it arrives. */
@Composable
private fun TryAlertRow(actions: SettingsActions) {
    var state by rememberSaveable { mutableIntStateOf(0) } // 0 idle, 1 scheduled, 2 not allowed
    NavRow(
        stringResource(R.string.alerts_try),
        stringResource(
            when (state) {
                1 -> R.string.alerts_try_scheduled
                2 -> R.string.alerts_try_not_allowed
                else -> R.string.alerts_try_summary
            },
        ),
        icon = R.drawable.ic_bell,
        onClick = { state = if (actions.tryAdhanAlert()) 1 else 2 },
    )
}
