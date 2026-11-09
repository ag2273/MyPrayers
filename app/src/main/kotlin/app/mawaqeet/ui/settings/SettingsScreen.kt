package app.mawaqeet.ui.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.mawaqeet.R
import app.mawaqeet.core.AsrConvention
import app.mawaqeet.core.CalculationMethod
import app.mawaqeet.core.PrayerSettings

/** What the Settings screen can change. Later tickets add their own callbacks here. */
interface SettingsActions {
    fun setCalculationMethod(method: CalculationMethod)
    fun setAsrConvention(convention: AsrConvention)
}

/**
 * Settings, in the spec's six groups: Prayer Times, Alerts, Prayer Log, Language & display, Widgets, About.
 * A ticket that adds settings fills its group's composable below; each group is a [SettingsGroup].
 */
@Composable
fun SettingsScreen(settings: PrayerSettings, actions: SettingsActions, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                Text(
                    stringResource(R.string.settings),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
            }
            PrayerTimesGroup(settings, actions)
            SettingsGroup(R.string.settings_group_alerts) { ComingSoon() }
            SettingsGroup(R.string.settings_group_prayer_log) { ComingSoon() }
            SettingsGroup(R.string.settings_group_language_display) { ComingSoon() }
            SettingsGroup(R.string.settings_group_widgets) { ComingSoon() }
            SettingsGroup(R.string.settings_group_about) { ComingSoon() }
        }
    }
}

/** A titled group of settings rows. */
@Composable
fun SettingsGroup(@StringRes title: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        content()
        HorizontalDivider()
    }
}

@Composable
private fun ComingSoon() {
    Text(
        stringResource(R.string.settings_coming_soon),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PrayerTimesGroup(settings: PrayerSettings, actions: SettingsActions) {
    var choosingMethod by rememberSaveable { mutableStateOf(false) }
    SettingsGroup(R.string.settings_group_prayer_times) {
        SettingRow(
            title = stringResource(R.string.calculation_method),
            summary = stringResource(settings.method.label),
            onClick = { choosingMethod = true },
        )
        Text(stringResource(R.string.asr_convention), style = MaterialTheme.typography.titleSmall)
        Column(Modifier.selectableGroup()) {
            AsrConvention.entries.forEach { convention ->
                ChoiceRow(
                    title = stringResource(convention.label),
                    description = stringResource(convention.description),
                    selected = convention == settings.asrConvention,
                    onSelect = { actions.setAsrConvention(convention) },
                )
            }
        }
    }
    if (choosingMethod) {
        AlertDialog(
            onDismissRequest = { choosingMethod = false },
            title = { Text(stringResource(R.string.calculation_method)) },
            text = {
                Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                    CalculationMethod.entries.forEach { method ->
                        ChoiceRow(
                            title = stringResource(method.label),
                            description = stringResource(method.description),
                            selected = method == settings.method,
                            onSelect = {
                                actions.setCalculationMethod(method)
                                choosingMethod = false
                            },
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { choosingMethod = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/** A row that opens a chooser, showing the current choice beneath its title. */
@Composable
fun SettingRow(title: String, summary: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One option of a single choice: a radio button, its name and a one-line description. */
@Composable
fun ChoiceRow(title: String, description: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@get:StringRes
private val CalculationMethod.label: Int
    get() = when (this) {
        CalculationMethod.UMM_AL_QURA -> R.string.method_umm_al_qura
        CalculationMethod.EGYPTIAN -> R.string.method_egyptian
        CalculationMethod.MUSLIM_WORLD_LEAGUE -> R.string.method_muslim_world_league
        CalculationMethod.KARACHI -> R.string.method_karachi
        CalculationMethod.KUWAIT -> R.string.method_kuwait
        CalculationMethod.QATAR -> R.string.method_qatar
        CalculationMethod.DUBAI -> R.string.method_dubai
        CalculationMethod.TURKEY -> R.string.method_turkey
        CalculationMethod.SINGAPORE -> R.string.method_singapore
        CalculationMethod.MOON_SIGHTING_COMMITTEE -> R.string.method_moon_sighting_committee
        CalculationMethod.NORTH_AMERICA -> R.string.method_north_america
    }

@get:StringRes
private val CalculationMethod.description: Int
    get() = when (this) {
        CalculationMethod.UMM_AL_QURA -> R.string.method_umm_al_qura_description
        CalculationMethod.EGYPTIAN -> R.string.method_egyptian_description
        CalculationMethod.MUSLIM_WORLD_LEAGUE -> R.string.method_muslim_world_league_description
        CalculationMethod.KARACHI -> R.string.method_karachi_description
        CalculationMethod.KUWAIT -> R.string.method_kuwait_description
        CalculationMethod.QATAR -> R.string.method_qatar_description
        CalculationMethod.DUBAI -> R.string.method_dubai_description
        CalculationMethod.TURKEY -> R.string.method_turkey_description
        CalculationMethod.SINGAPORE -> R.string.method_singapore_description
        CalculationMethod.MOON_SIGHTING_COMMITTEE -> R.string.method_moon_sighting_committee_description
        CalculationMethod.NORTH_AMERICA -> R.string.method_north_america_description
    }

@get:StringRes
private val AsrConvention.label: Int
    get() = when (this) {
        AsrConvention.STANDARD -> R.string.asr_standard
        AsrConvention.HANAFI -> R.string.asr_hanafi
    }

@get:StringRes
private val AsrConvention.description: Int
    get() = when (this) {
        AsrConvention.STANDARD -> R.string.asr_standard_description
        AsrConvention.HANAFI -> R.string.asr_hanafi_description
    }
