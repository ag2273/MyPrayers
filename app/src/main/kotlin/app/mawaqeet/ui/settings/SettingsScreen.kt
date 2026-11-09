package app.mawaqeet.ui.settings

import android.net.Uri
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.mawaqeet.R
import app.mawaqeet.core.AdhanRecording
import app.mawaqeet.core.AsrConvention
import app.mawaqeet.core.CalculationMethod
import app.mawaqeet.core.DigitStyle
import app.mawaqeet.core.HighLatitudeRule
import app.mawaqeet.core.HijriDate
import app.mawaqeet.core.Prayer
import app.mawaqeet.core.PrayerAlertSettings
import app.mawaqeet.core.PrayerSettings
import app.mawaqeet.ui.LocalDisplayFormatter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.core.text.BidiFormatter
import androidx.core.text.TextDirectionHeuristicsCompat
import java.time.LocalDate

/** What the Settings screen can change. Later tickets add their own callbacks here. */
interface SettingsActions {
    fun setCalculationMethod(method: CalculationMethod)
    fun setAsrConvention(convention: AsrConvention)

    /** Switches the whole app to [languageTag] (`ar` or `en`) through the per-app language API. */
    fun setLanguage(languageTag: String)
    fun setDigitStyle(style: DigitStyle)
    fun setHijriOffsetDays(days: Int)

    /** Settings → Prayer Times (#20): [minutes] within [PrayerSettings.ADJUSTMENT_RANGE] added to [prayer]'s time. */
    fun setAdjustment(prayer: Prayer, minutes: Int)

    /** Settings → Advanced (#20). */
    fun setHighLatitudeRule(rule: HighLatitudeRule)

    /** Opens the Location screen (city search or coordinates, #21). */
    fun openLocation()

    /** Settings → Alerts (#23); each change reschedules the alarms through the settings flow. */
    fun updatePrayerAlert(prayer: Prayer, change: (PrayerAlertSettings) -> PrayerAlertSettings)
    fun setAlarmClockMode(on: Boolean)
    fun setPlayOnSilent(on: Boolean)
    fun setVibration(on: Boolean)

    /**
     * Custom Adhan (#28): [uri] is the file the user picked with the system file picker for [recording] only, or null
     * to go back to the bundled recording.
     */
    fun setCustomAdhan(recording: AdhanRecording, uri: Uri?)
}

/**
 * Settings, in the spec's six groups (Prayer Times, Alerts, Prayer Log, Language & display, Widgets, About), then
 * Advanced. A ticket that adds settings fills its group's composable below; each group is a [SettingsGroup].
 */
@Composable
fun SettingsScreen(
    settings: PrayerSettings,
    locationName: String,
    digitStyle: DigitStyle,
    today: LocalDate,
    actions: SettingsActions,
    onBack: () -> Unit,
) {
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
            PrayerTimesGroup(settings, locationName, actions)
            AlertsGroup(settings.alerts, actions)
            SettingsGroup(R.string.settings_group_prayer_log) { ComingSoon() }
            LanguageDisplayGroup(settings, digitStyle, today, actions)
            SettingsGroup(R.string.settings_group_widgets) { ComingSoon() }
            AdvancedGroup(settings, actions)
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
private fun PrayerTimesGroup(settings: PrayerSettings, locationName: String, actions: SettingsActions) {
    var choosingMethod by rememberSaveable { mutableStateOf(false) }
    var adjusting by rememberSaveable { mutableStateOf(false) }
    val format = LocalDisplayFormatter.current
    val resources = LocalResources.current
    SettingsGroup(R.string.settings_group_prayer_times) {
        SettingRow(
            title = stringResource(R.string.location),
            summary = locationName,
            onClick = actions::openLocation,
        )
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
        val adjusted = Prayer.entries.filter { settings.adjustmentOf(it) != 0 }
        SettingRow(
            title = stringResource(R.string.adjustments),
            summary = if (adjusted.isEmpty()) {
                stringResource(R.string.adjustments_none)
            } else {
                adjusted.joinToString(resources.getString(R.string.list_separator)) { prayer ->
                    resources.getString(R.string.adjustment_summary, resources.getString(prayer.nameRes), signedMinutes(format.signedNumber(settings.adjustmentOf(prayer))))
                }
            },
            onClick = { adjusting = true },
        )
    }
    if (adjusting) {
        AlertDialog(
            onDismissRequest = { adjusting = false },
            title = { Text(stringResource(R.string.adjustments)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.adjustments_hint, format.number(PrayerSettings.ADJUSTMENT_RANGE.last)), style = MaterialTheme.typography.bodyMedium)
                    Prayer.entries.forEach { prayer ->
                        AdjustmentRow(prayer, settings.adjustmentOf(prayer)) { actions.setAdjustment(prayer, it) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { adjusting = false }) { Text(stringResource(R.string.done)) } },
        )
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

/** A signed minute count that reads left to right even in Arabic, so "+5" never shows as "5+". */
private fun signedMinutes(signed: String): String =
    BidiFormatter.getInstance().unicodeWrap(signed, TextDirectionHeuristicsCompat.LTR)

/** One Prayer's adjustment: its name, then −, the minutes and +, one minute per tap within the allowed range. */
@Composable
private fun AdjustmentRow(prayer: Prayer, minutes: Int, onChange: (Int) -> Unit) {
    val format = LocalDisplayFormatter.current
    val name = stringResource(prayer.nameRes)
    val range = PrayerSettings.ADJUSTMENT_RANGE
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        val earlier = stringResource(R.string.adjustment_earlier, name)
        val later = stringResource(R.string.adjustment_later, name)
        TextButton(
            onClick = { onChange(minutes - 1) },
            enabled = minutes > range.first,
            modifier = Modifier.semantics { contentDescription = earlier },
        ) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Text(
            stringResource(R.string.adjustment_minutes, signedMinutes(format.signedNumber(minutes))),
            style = MaterialTheme.typography.bodyLarge,
        )
        TextButton(
            onClick = { onChange(minutes + 1) },
            enabled = minutes < range.last,
            modifier = Modifier.semantics { contentDescription = later },
        ) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}

/** Advanced (#20): the high-latitude rule for Fajr and Isha. */
@Composable
private fun AdvancedGroup(settings: PrayerSettings, actions: SettingsActions) {
    val format = LocalDisplayFormatter.current
    SettingsGroup(R.string.settings_group_advanced) {
        Text(stringResource(R.string.high_latitude_rule), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.high_latitude_rule_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.selectableGroup()) {
            HighLatitudeRule.entries.forEach { rule ->
                ChoiceRow(
                    title = stringResource(rule.label),
                    description = when (rule) {
                        HighLatitudeRule.AUTOMATIC ->
                            stringResource(rule.description, format.number(HighLatitudeRule.AUTOMATIC_LATITUDE.toInt()))
                        HighLatitudeRule.TWILIGHT_ANGLE -> stringResource(rule.description, format.number(60))
                        else -> stringResource(rule.description)
                    },
                    selected = rule == settings.highLatitudeRule,
                    onSelect = { actions.setHighLatitudeRule(rule) },
                )
            }
        }
    }
}

@get:StringRes
internal val HighLatitudeRule.label: Int
    get() = when (this) {
        HighLatitudeRule.AUTOMATIC -> R.string.high_latitude_automatic
        HighLatitudeRule.MIDDLE_OF_THE_NIGHT -> R.string.high_latitude_middle
        HighLatitudeRule.SEVENTH_OF_THE_NIGHT -> R.string.high_latitude_seventh
        HighLatitudeRule.TWILIGHT_ANGLE -> R.string.high_latitude_twilight
    }

@get:StringRes
private val HighLatitudeRule.description: Int
    get() = when (this) {
        HighLatitudeRule.AUTOMATIC -> R.string.high_latitude_automatic_description
        HighLatitudeRule.MIDDLE_OF_THE_NIGHT -> R.string.high_latitude_middle_description
        HighLatitudeRule.SEVENTH_OF_THE_NIGHT -> R.string.high_latitude_seventh_description
        HighLatitudeRule.TWILIGHT_ANGLE -> R.string.high_latitude_twilight_description
    }

@Composable
private fun LanguageDisplayGroup(settings: PrayerSettings, digitStyle: DigitStyle, today: LocalDate, actions: SettingsActions) {
    val format = LocalDisplayFormatter.current
    val resources = LocalResources.current
    var choosingOffset by rememberSaveable { mutableStateOf(false) }
    fun offsetLabel(days: Int): String {
        val date = format.hijriDate(HijriDate.of(today, days))
        if (days == 0) return resources.getString(R.string.hijri_offset_none, date)
        // A signed number reads left to right even in Arabic, so "+1" never shows as "1+".
        val signed = BidiFormatter.getInstance().unicodeWrap(format.signedNumber(days), TextDirectionHeuristicsCompat.LTR)
        return resources.getString(R.string.hijri_offset_summary, signed, date)
    }
    SettingsGroup(R.string.settings_group_language_display) {
        Text(stringResource(R.string.language), style = MaterialTheme.typography.titleSmall)
        val arabic = LocalConfiguration.current.locales[0].language == "ar"
        Column(Modifier.selectableGroup()) {
            ChoiceRow(
                title = stringResource(R.string.language_arabic),
                description = stringResource(R.string.language_arabic_description),
                selected = arabic,
                onSelect = { actions.setLanguage("ar") },
            )
            ChoiceRow(
                title = stringResource(R.string.language_english),
                description = stringResource(R.string.language_english_description),
                selected = !arabic,
                onSelect = { actions.setLanguage("en") },
            )
        }
        Text(stringResource(R.string.digits), style = MaterialTheme.typography.titleSmall)
        Column(Modifier.selectableGroup()) {
            DigitStyle.entries.forEach { style ->
                ChoiceRow(
                    title = stringResource(style.label),
                    description = stringResource(style.description),
                    selected = style == digitStyle,
                    onSelect = { actions.setDigitStyle(style) },
                )
            }
        }
        SettingRow(
            title = stringResource(R.string.hijri_offset),
            summary = offsetLabel(settings.hijriOffsetDays),
            onClick = { choosingOffset = true },
        )
    }
    if (choosingOffset) {
        AlertDialog(
            onDismissRequest = { choosingOffset = false },
            title = { Text(stringResource(R.string.hijri_offset)) },
            text = {
                Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.hijri_offset_hint), style = MaterialTheme.typography.bodyMedium)
                    PrayerSettings.HIJRI_OFFSET_RANGE.forEach { days ->
                        ChoiceRow(
                            title = offsetLabel(days),
                            description = "",
                            selected = days == settings.hijriOffsetDays,
                            onSelect = {
                                actions.setHijriOffsetDays(days)
                                choosingOffset = false
                            },
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { choosingOffset = false }) { Text(stringResource(R.string.cancel)) }
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
            if (description.isNotEmpty()) Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@get:StringRes
internal val CalculationMethod.label: Int
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
private val DigitStyle.label: Int
    get() = when (this) {
        DigitStyle.AUTOMATIC -> R.string.digits_automatic
        DigitStyle.ARABIC_INDIC -> R.string.digits_arabic_indic
        DigitStyle.WESTERN -> R.string.digits_western
    }

@get:StringRes
private val DigitStyle.description: Int
    get() = when (this) {
        DigitStyle.AUTOMATIC -> R.string.digits_automatic_description
        DigitStyle.ARABIC_INDIC -> R.string.digits_arabic_indic_description
        DigitStyle.WESTERN -> R.string.digits_western_description
    }

@get:StringRes
internal val AsrConvention.label: Int
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
