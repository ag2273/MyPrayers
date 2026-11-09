package app.mawaqeet.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.mawaqeet.R
import app.mawaqeet.core.AdhanSoundChoice
import app.mawaqeet.core.AlertSettings
import app.mawaqeet.core.Prayer
import app.mawaqeet.core.PrayerAlertSettings
import app.mawaqeet.ui.LocalDisplayFormatter

/** Lead times offered for a Prayer Reminder; the core rejects anything below [PrayerAlertSettings.MIN_REMINDER_MINUTES]. */
private val REMINDER_MINUTES = listOf(10, 15, 20, 30, 45, 60)

/** Settings → Alerts: one row per Prayer (opens its own dialog), then alarm-clock mode, play on silent, vibration. */
@Composable
internal fun AlertsGroup(alerts: AlertSettings, actions: SettingsActions) {
    var editing by rememberSaveable { mutableStateOf<Prayer?>(null) }
    SettingsGroup(R.string.settings_group_alerts) {
        Prayer.entries.forEach { prayer ->
            SettingRow(
                title = stringResource(prayer.nameRes),
                summary = summary(prayer, alerts.of(prayer)),
                onClick = { editing = prayer },
            )
        }
        SwitchRow(
            title = stringResource(R.string.alerts_alarm_clock),
            description = stringResource(R.string.alerts_alarm_clock_description),
            checked = alerts.alarmClockMode,
            onChange = actions::setAlarmClockMode,
        )
        SwitchRow(
            title = stringResource(R.string.alerts_play_on_silent),
            description = stringResource(R.string.alerts_play_on_silent_description),
            checked = alerts.playOnSilent,
            onChange = actions::setPlayOnSilent,
        )
        SwitchRow(
            title = stringResource(R.string.alerts_vibration),
            description = "",
            checked = alerts.vibration,
            onChange = actions::setVibration,
        )
    }
    editing?.let { prayer ->
        PrayerAlertDialog(prayer, alerts.of(prayer), { change -> actions.updatePrayerAlert(prayer, change) }, onDismiss = { editing = null })
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

@Composable
private fun PrayerAlertDialog(
    prayer: Prayer,
    alert: PrayerAlertSettings,
    onChange: ((PrayerAlertSettings) -> PrayerAlertSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(prayer.nameRes)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SwitchRow(stringResource(R.string.alerts_adhan_alert), "", alert.adhanAlert) { on -> onChange { it.copy(adhanAlert = on) } }
                if (alert.adhanAlert) {
                    Text(stringResource(R.string.alerts_sound), style = MaterialTheme.typography.titleSmall)
                    Column(Modifier.selectableGroup()) {
                        AdhanSoundChoice.entries.forEach { sound ->
                            ChoiceRow(stringResource(soundLabel(prayer, sound)), "", sound == alert.sound) { onChange { it.copy(sound = sound) } }
                        }
                    }
                }
                SwitchRow(stringResource(R.string.alerts_reminder), "", alert.reminder) { on -> onChange { it.copy(reminder = on) } }
                if (alert.reminder) {
                    Column(Modifier.selectableGroup()) {
                        REMINDER_MINUTES.forEach { minutes ->
                            ChoiceRow(minutesLabel(minutes), "", minutes == alert.reminderMinutes) {
                                onChange { it.copy(reminderMinutes = minutes) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}

/** An on/off setting: its name, an optional one-line description and a switch. */
@Composable
fun SwitchRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description.isNotEmpty()) Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@get:StringRes
private val Prayer.nameRes: Int
    get() = when (this) {
        Prayer.FAJR -> R.string.prayer_fajr
        Prayer.DHUHR -> R.string.prayer_dhuhr
        Prayer.ASR -> R.string.prayer_asr
        Prayer.MAGHRIB -> R.string.prayer_maghrib
        Prayer.ISHA -> R.string.prayer_isha
    }
