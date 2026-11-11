package app.mawaqeet.ui.settings

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.layout.ContentScale
import app.mawaqeet.about.CreditKind
import app.mawaqeet.about.Credits
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import app.mawaqeet.BuildConfig
import app.mawaqeet.ui.components.Card
import app.mawaqeet.ui.components.CardShape
import app.mawaqeet.ui.components.NavRow
import app.mawaqeet.ui.components.RowDivider
import app.mawaqeet.ui.components.RowText
import app.mawaqeet.ui.components.SectionTitle
import app.mawaqeet.ui.components.Segment
import app.mawaqeet.ui.components.SegmentedChoice
import app.mawaqeet.ui.components.SubPage
import app.mawaqeet.ui.components.SwitchRow
import app.mawaqeet.ui.components.TabPage
import app.mawaqeet.widget.WidgetStyle
import java.io.Serializable
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.mawaqeet.R
import app.mawaqeet.about.AboutContent
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
import app.mawaqeet.widget.WidgetAppearance
import app.mawaqeet.widget.WidgetColors
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalResources
import androidx.core.text.BidiFormatter
import androidx.core.text.TextDirectionHeuristicsCompat
import java.time.LocalDate
import kotlin.math.roundToInt

/** What the Settings screen can change. Later tickets add their own callbacks here. */
interface SettingsActions {
    fun setCalculationMethod(method: CalculationMethod)
    fun setAsrConvention(convention: AsrConvention)

    /** Switches the whole app to [languageTag] (`ar` or `en`) through the per-app language API. */
    fun setLanguage(languageTag: String)
    fun setDigitStyle(style: DigitStyle)

    /** Settings → Widgets (#32): every widget's background transparency, 0–100 in steps of 10. */
    fun setWidgetTransparency(percent: Int)

    /** Settings → Widgets (#32, Android 12+): wallpaper or app colours. */
    fun setWidgetColors(colors: WidgetColors)

    /** Settings → Widgets: every widget in the Next Prayer's deep look, or each in its own. */
    fun setWidgetNextPrayerLook(on: Boolean)
    fun setHijriOffsetDays(days: Int)

    /** Settings → Prayer Times (#20): moves [prayer]'s adjustment by [step] minutes, within [PrayerSettings.ADJUSTMENT_RANGE]. */
    fun stepAdjustment(prayer: Prayer, step: Int)

    /** Settings → Advanced (#20). */
    fun setHighLatitudeRule(rule: HighLatitudeRule)

    /** Opens the Location screen (city search or coordinates, #21). */
    fun openLocation()

    /** Settings → Alerts (#23); each change reschedules the alarms through the settings flow. */
    fun updatePrayerAlert(prayer: Prayer, change: (PrayerAlertSettings) -> PrayerAlertSettings)

    /** Settings → Alerts → All alerts: the master switch; off silences every Adhan Alert and Prayer Reminder. */
    fun setAlertsEnabled(on: Boolean)

    /** Settings → Alerts → «الصامت وقت الصلاة» (#61): stores the choice, independent of the master switch. */
    fun setPrayerSilenceEnabled(on: Boolean)

    /** «صامت» or «اهتزاز» (#68). */
    fun setPrayerSilenceMode(mode: app.mawaqeet.core.SilenceMode)

    /** Prayer Silence on or off for one Prayer (#68). */
    fun setPrayerSilencePrayer(prayer: Prayer, on: Boolean)

    /** The − and + buttons of the Silence Window's duration ([jumuah]: Jumu'ah's), one [app.mawaqeet.core.PrayerSilence.MINUTES_STEP] per step. */
    fun stepPrayerSilenceMinutes(jumuah: Boolean, step: Int)

    /** Whether Prayer Silence may change the ringer: Do Not Disturb (notification policy) access. */
    fun hasDoNotDisturbAccess(): Boolean

    /** The system page where the user grants Do Not Disturb access. */
    fun openDoNotDisturbAccess()
    fun setAlarmClockMode(on: Boolean)

    /** Settings → Alerts → Try it: an Adhan Alert for the next Prayer in 10 seconds, in the current mode. False if it can't be scheduled. */
    fun tryAdhanAlert(): Boolean

    /** Whether alerts may show over the lock screen (full-screen intents; Android 14+ lets the user turn this off). */
    fun canShowOverLockScreen(): Boolean

    /** The system page that allows showing over the lock screen (Android 14+). */
    fun openLockScreenPermission()
    fun setPlayOnSilent(on: Boolean)
    fun setVibration(on: Boolean)

    /** Settings → Alerts → Adhan volume: the adhan follows the alarm volume or the media volume. */
    fun setAdhanVolume(volume: app.mawaqeet.core.AdhanVolume)

    /**
     * Custom Adhan (#28): [uri] is the file the user picked with the system file picker for [recording] only, or null
     * to go back to the bundled recording.
     */
    fun setCustomAdhan(recording: AdhanRecording, uri: Uri?)

    /** Opens the Fix alerts screen (#29): battery instructions for this phone's brand. */
    fun openFixAlerts()
    /** Settings → Prayer Log (#25): permanently deletes every Prayer Log entry. Called only after confirmation. */
    fun deletePrayerLog()

    /** Settings → your data (#74): permanently deletes the Adhkar Record. Called only after confirmation. */
    fun deleteAdhkarRecord()
}

/** The Settings sections, each its own page, plus the pages opened from them. */
enum class SettingsPage(@StringRes val title: Int) {
    PRAYER_TIMES(R.string.settings_group_prayer_times),
    CALCULATION_METHOD(R.string.calculation_method),
    ALERTS(R.string.settings_group_alerts),
    PRAYER_ALERT(R.string.alerts_adhan_alert),
    DISPLAY(R.string.settings_group_language_display),
    WIDGETS(R.string.settings_group_widgets),
    ABOUT(R.string.settings_group_about),
    CREDITS(R.string.about_sources_title),
}

/** Which Settings page is open; [prayer] is the Prayer whose alerts [SettingsPage.PRAYER_ALERT] shows. */
data class SettingsRoute(val page: SettingsPage, val prayer: Prayer? = null) : Serializable {
    /** Where Back goes: a picker or a Prayer's page returns to its section, a section to the Settings hub. */
    val parent: SettingsRoute?
        get() = when (page) {
            SettingsPage.CALCULATION_METHOD -> SettingsRoute(SettingsPage.PRAYER_TIMES)
            SettingsPage.PRAYER_ALERT -> SettingsRoute(SettingsPage.ALERTS)
            SettingsPage.CREDITS -> SettingsRoute(SettingsPage.ABOUT)
            else -> null
        }
}

/**
 * The Settings tab: whether alerts are ready, then the sections as rows with their current values. Each row opens the
 * section's page ([onOpen]); Fix alerts opens its own screen.
 */
@Composable
fun SettingsHub(
    settings: PrayerSettings,
    locationName: String,
    digitStyle: DigitStyle,
    widgetAppearance: WidgetAppearance,
    /** On a phone known to delay alarms whose battery steps the user hasn't confirmed (#29). */
    alertsAtRisk: Boolean,
    onOpen: (SettingsRoute) -> Unit,
    onOpenFixAlerts: () -> Unit,
) {
    val format = LocalDisplayFormatter.current
    val resources = LocalResources.current
    TabPage(stringResource(R.string.settings)) {
        val alertsOn = settings.alerts.enabled
        AlertStatusCard(
            alertsOn = alertsOn,
            atRisk = alertsAtRisk,
            onClick = if (alertsOn && alertsAtRisk) onOpenFixAlerts else { { onOpen(SettingsRoute(SettingsPage.ALERTS)) } },
        )
        SectionTitle(stringResource(R.string.settings_section_prayer))
        Card {
            NavRow(
                stringResource(R.string.settings_group_prayer_times),
                resources.getString(R.string.settings_summary_pair, locationName, resources.getString(settings.method.label)),
                icon = R.drawable.ic_pin,
            ) { onOpen(SettingsRoute(SettingsPage.PRAYER_TIMES)) }
            RowDivider()
            val withAdhan = Prayer.entries.count { settings.alerts.of(it).adhanAlert }
            NavRow(
                stringResource(R.string.settings_group_alerts),
                if (alertsOn) {
                    resources.getString(R.string.settings_alerts_summary, format.number(withAdhan), format.number(Prayer.entries.size))
                } else {
                    resources.getString(R.string.settings_alerts_summary_off)
                },
                icon = R.drawable.ic_bell,
            ) { onOpen(SettingsRoute(SettingsPage.ALERTS)) }
        }
        SectionTitle(stringResource(R.string.settings_section_app))
        Card {
            val arabic = LocalConfiguration.current.locales[0].language == "ar"
            NavRow(
                stringResource(R.string.settings_group_language_display),
                resources.getString(
                    R.string.settings_summary_pair,
                    resources.getString(if (arabic) R.string.language_arabic else R.string.language_english),
                    resources.getString(digitStyle.label),
                ),
                icon = R.drawable.ic_globe,
            ) { onOpen(SettingsRoute(SettingsPage.DISPLAY)) }
            RowDivider()
            NavRow(
                stringResource(R.string.settings_group_widgets),
                resources.getString(R.string.widget_transparency_value, format.number(widgetAppearance.transparency)).let {
                    resources.getString(R.string.settings_widgets_summary, it)
                },
                icon = R.drawable.ic_widgets,
            ) { onOpen(SettingsRoute(SettingsPage.WIDGETS)) }
        }
        SectionTitle(stringResource(R.string.settings_section_help))
        Card {
            NavRow(stringResource(R.string.fix_alerts), stringResource(R.string.fix_alerts_summary), icon = R.drawable.ic_shield, onClick = onOpenFixAlerts)
            RowDivider()
            NavRow(
                stringResource(R.string.settings_group_about),
                stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                icon = R.drawable.ic_info,
            ) { onOpen(SettingsRoute(SettingsPage.ABOUT)) }
        }
    }
}

/**
 * Alerts are ready (the Grant screen comes first otherwise), this phone may delay them until Fix alerts is done, or
 * the user turned every alert off (Settings → Alerts → All alerts).
 */
@Composable
private fun AlertStatusCard(alertsOn: Boolean, atRisk: Boolean, onClick: () -> Unit) {
    if (!alertsOn) {
        StatusCard(
            icon = R.drawable.ic_bell_off,
            title = stringResource(R.string.settings_alerts_off),
            summary = stringResource(R.string.settings_alerts_off_summary),
            background = MaterialTheme.colorScheme.surfaceVariant,
            content = MaterialTheme.colorScheme.onSurfaceVariant,
            iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = onClick,
        )
        return
    }
    val colors = MaterialTheme.colorScheme
    StatusCard(
        icon = if (atRisk) R.drawable.ic_warning else R.drawable.ic_check,
        title = stringResource(if (atRisk) R.string.settings_alerts_at_risk else R.string.settings_alerts_ready),
        summary = stringResource(if (atRisk) R.string.fix_alerts_tip else R.string.settings_alerts_ready_summary),
        background = if (atRisk) colors.secondaryContainer else colors.tertiaryContainer,
        content = if (atRisk) colors.onSecondaryContainer else colors.onTertiaryContainer,
        iconTint = if (atRisk) colors.onSecondaryContainer else colors.tertiary,
        onClick = onClick,
    )
}

@Composable
private fun StatusCard(
    @DrawableRes icon: Int,
    title: String,
    summary: String,
    background: Color,
    content: Color,
    iconTint: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(background)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = content)
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = content)
        }
    }
}

/** A Settings page: its title and back arrow, then the section's settings. [onNavigate] opens another page or, with null, the hub. */
@Composable
fun SettingsPageScreen(
    route: SettingsRoute,
    settings: PrayerSettings,
    locationName: String,
    digitStyle: DigitStyle,
    today: LocalDate,
    actions: SettingsActions,
    widgetAppearance: WidgetAppearance,
    onNavigate: (SettingsRoute?) -> Unit,
) {
    val prayer = route.prayer ?: Prayer.FAJR
    val title = if (route.page == SettingsPage.PRAYER_ALERT) {
        stringResource(R.string.alerts_of_prayer, stringResource(prayer.nameRes))
    } else {
        stringResource(route.page.title)
    }
    SubPage(title, onBack = { onNavigate(route.parent) }) {
        when (route.page) {
            SettingsPage.PRAYER_TIMES -> PrayerTimesPage(settings, locationName, actions) { onNavigate(SettingsRoute(SettingsPage.CALCULATION_METHOD)) }
            SettingsPage.CALCULATION_METHOD -> MethodPage(settings) { method ->
                actions.setCalculationMethod(method)
                onNavigate(route.parent)
            }
            SettingsPage.ALERTS -> AlertsPage(settings.alerts, actions) { onNavigate(SettingsRoute(SettingsPage.PRAYER_ALERT, it)) }
            SettingsPage.PRAYER_ALERT -> PrayerAlertPage(prayer, settings.alerts.of(prayer), settings.alerts.customAdhans.keys) { change ->
                actions.updatePrayerAlert(prayer, change)
            }
            SettingsPage.DISPLAY -> DisplayPage(settings, digitStyle, today, actions)
            SettingsPage.WIDGETS -> WidgetsPage(widgetAppearance, actions)
            SettingsPage.ABOUT -> AboutPage(actions) { onNavigate(SettingsRoute(SettingsPage.CREDITS)) }
            SettingsPage.CREDITS -> CreditsPage()
        }
    }
}

/** Location & calculation: the place, the Calculation Method and Asr Convention, the adjustments, and (advanced) the high-latitude rule. */
@Composable
private fun ColumnScope.PrayerTimesPage(settings: PrayerSettings, locationName: String, actions: SettingsActions, onChooseMethod: () -> Unit) {
    val format = LocalDisplayFormatter.current
    Card {
        NavRow(stringResource(R.string.location), locationName, icon = R.drawable.ic_pin, onClick = actions::openLocation)
    }
    SectionTitle(stringResource(R.string.settings_section_calculation))
    Card {
        NavRow(stringResource(R.string.calculation_method), stringResource(settings.method.label), onClick = onChooseMethod)
        RowDivider()
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.asr_convention), style = MaterialTheme.typography.titleMedium)
            SegmentedChoice(
                AsrConvention.entries.map { convention ->
                    Segment(stringResource(convention.label), convention == settings.asrConvention) { actions.setAsrConvention(convention) }
                },
            )
            Text(
                stringResource(settings.asrConvention.description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    SectionTitle(stringResource(R.string.adjustments))
    Card {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                stringResource(R.string.adjustments_hint, format.number(PrayerSettings.ADJUSTMENT_RANGE.last)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Prayer.entries.forEach { prayer ->
                AdjustmentRow(prayer, settings.adjustmentOf(prayer)) { actions.stepAdjustment(prayer, it) }
            }
        }
    }
    var advanced by rememberSaveable { mutableStateOf(settings.highLatitudeRule != HighLatitudeRule.AUTOMATIC) }
    Card {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.settings_group_advanced)) { advanced = !advanced }
                .heightIn(min = 60.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RowText(stringResource(R.string.high_latitude_rule), stringResource(settings.highLatitudeRule.label))
            Icon(
                painterResource(R.drawable.ic_expand),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp).graphicsLayer { if (advanced) rotationZ = 180f },
            )
        }
        if (advanced) {
            Text(
                stringResource(R.string.high_latitude_rule_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Column(Modifier.selectableGroup().padding(bottom = 8.dp)) {
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
}

/** Every Calculation Method with the regions it's used in; choosing one returns to the section. */
@Composable
private fun MethodPage(settings: PrayerSettings, onChoose: (CalculationMethod) -> Unit) {
    Card {
        Column(Modifier.selectableGroup().padding(vertical = 4.dp)) {
            CalculationMethod.entries.forEach { method ->
                ChoiceRow(
                    title = stringResource(method.label),
                    description = stringResource(method.description),
                    selected = method == settings.method,
                    onSelect = { onChoose(method) },
                )
            }
        }
    }
}

/** A signed minute count that reads left to right even in Arabic, so "+5" never shows as "5+". */
private fun signedMinutes(signed: String): String =
    BidiFormatter.getInstance().unicodeWrap(signed, TextDirectionHeuristicsCompat.LTR)

/** One Prayer's adjustment: its name, then −, the minutes and +, one minute per tap within the allowed range. */
@Composable
private fun AdjustmentRow(prayer: Prayer, minutes: Int, onStep: (Int) -> Unit) {
    val format = LocalDisplayFormatter.current
    val name = stringResource(prayer.nameRes)
    val range = PrayerSettings.ADJUSTMENT_RANGE
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        val earlier = stringResource(R.string.adjustment_earlier, name)
        val later = stringResource(R.string.adjustment_later, name)
        StepButton(R.drawable.ic_minus, earlier, enabled = minutes > range.first) { onStep(-1) }
        Text(
            if (minutes == 0) stringResource(R.string.adjustments_none) else stringResource(R.string.adjustment_minutes, signedMinutes(format.signedNumber(minutes))),
            style = MaterialTheme.typography.bodyLarge,
            color = if (minutes == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 72.dp),
        )
        StepButton(R.drawable.ic_plus, later, enabled = minutes < range.last) { onStep(+1) }
    }
}

/** A round − or + button; [description] is what TalkBack reads (e.g. "Fajr one minute later"). An icon, not a "−"
 * glyph, so it keeps its size when the text is enlarged. */
@Composable
internal fun StepButton(@DrawableRes icon: Int, description: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
        Icon(painterResource(icon), contentDescription = description, modifier = Modifier.size(20.dp))
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

/** Widgets (#32): a live preview, the background transparency for every widget, and (Android 12+) their colours. */
@Composable
private fun WidgetsPage(appearance: WidgetAppearance, actions: SettingsActions) {
    val format = LocalDisplayFormatter.current
    val resources = LocalResources.current
    // Follows the finger while dragging, stored once released.
    var percent by remember(appearance.transparency) { mutableFloatStateOf(appearance.transparency.toFloat()) }
    WidgetPreview(appearance.copy(transparency = percent.roundToInt()))
    Card {
        SwitchRow(
            title = stringResource(R.string.widget_next_prayer_look),
            description = stringResource(R.string.widget_next_prayer_look_description),
            checked = appearance.nextPrayerLook,
            onChange = actions::setWidgetNextPrayerLook,
        )
    }
    Card {
        Column(Modifier.padding(16.dp)) {
            val label = resources.getString(R.string.widget_transparency_value, format.number(percent.roundToInt()))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.widget_transparency), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = percent,
                onValueChange = { percent = it },
                onValueChangeFinished = { actions.setWidgetTransparency(percent.roundToInt()) },
                valueRange = 0f..100f,
                steps = (WidgetAppearance.TRANSPARENCY_RANGE.last / WidgetAppearance.TRANSPARENCY_STEP) - 1,
                modifier = Modifier.semantics { stateDescription = label },
            )
            Text(
                stringResource(R.string.widget_transparency_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        SectionTitle(stringResource(R.string.widget_colors))
        Card {
            Column(Modifier.selectableGroup().padding(vertical = 4.dp)) {
                ChoiceRow(
                    title = stringResource(R.string.widget_colors_wallpaper),
                    description = stringResource(R.string.widget_colors_wallpaper_description),
                    selected = appearance.colors == WidgetColors.WALLPAPER,
                    onSelect = { actions.setWidgetColors(WidgetColors.WALLPAPER) },
                )
                ChoiceRow(
                    title = stringResource(R.string.widget_colors_app),
                    description = stringResource(R.string.widget_colors_app_description),
                    selected = appearance.colors == WidgetColors.APP,
                    onSelect = { actions.setWidgetColors(WidgetColors.APP) },
                )
            }
        }
    }
}

/**
 * How the Next Prayer widget looks with [appearance], on a stand-in wallpaper. Decorative: the settings below say the
 * same in words, so TalkBack skips it.
 */
@Composable
private fun WidgetPreview(appearance: WidgetAppearance) {
    val style = WidgetStyle.of(appearance, deep = appearance.nextPrayerLook)
    val text = colorResource(style.text)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Color(0xFF3E5B57))
            .padding(horizontal = 18.dp, vertical = 24.dp)
            .clearAndSetSemantics {},
    ) {
        val shadow = if (style.shadowedText) Shadow(Color.Black.copy(alpha = 0.55f), blurRadius = 4f) else null
        Column(
            Modifier
                .fillMaxWidth()
                .background(colorResource(style.background).copy(alpha = style.backgroundAlpha / 255f), RoundedCornerShape(22.dp))
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Text(stringResource(R.string.next_prayer), style = MaterialTheme.typography.labelMedium.copy(shadow = shadow), color = colorResource(style.muted))
            Text(stringResource(R.string.prayer_asr), style = MaterialTheme.typography.headlineSmall.copy(shadow = shadow), color = text)
            Text(stringResource(R.string.widget_countdown_hours, "1:12:34"), style = MaterialTheme.typography.titleSmall.copy(shadow = shadow), color = colorResource(style.accent))
        }
    }
}

/** Language & display: the app language, digits, and the Hijri offset with a preview of today's Hijri date. */
@Composable
private fun DisplayPage(settings: PrayerSettings, digitStyle: DigitStyle, today: LocalDate, actions: SettingsActions) {
    val format = LocalDisplayFormatter.current
    SectionTitle(stringResource(R.string.language))
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val arabic = LocalConfiguration.current.locales[0].language == "ar"
            SegmentedChoice(
                listOf(
                    Segment(stringResource(R.string.language_arabic), arabic) { actions.setLanguage("ar") },
                    Segment(stringResource(R.string.language_english), !arabic) { actions.setLanguage("en") },
                ),
            )
            Text(
                stringResource(R.string.language_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    SectionTitle(stringResource(R.string.digits))
    Card {
        Column(Modifier.selectableGroup().padding(vertical = 4.dp)) {
            DigitStyle.entries.forEach { style ->
                ChoiceRow(
                    title = stringResource(style.label),
                    description = stringResource(style.description),
                    selected = style == digitStyle,
                    onSelect = { actions.setDigitStyle(style) },
                )
            }
        }
    }
    SectionTitle(stringResource(R.string.hijri_offset))
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val days = settings.hijriOffsetDays
            val range = PrayerSettings.HIJRI_OFFSET_RANGE
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepButton(R.drawable.ic_minus, stringResource(R.string.hijri_offset_earlier), enabled = days > range.first) { actions.setHijriOffsetDays(days - 1) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(format.hijriDate(HijriDate.of(today, days)), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    Text(
                        if (days == 0) stringResource(R.string.adjustments_none) else stringResource(R.string.hijri_offset_days, signedMinutes(format.signedNumber(days))),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StepButton(R.drawable.ic_plus, stringResource(R.string.hijri_offset_later), enabled = days < range.last) { actions.setHijriOffsetDays(days + 1) }
            }
            Text(
                stringResource(R.string.hijri_offset_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * About & your data (#33), as on the redesign's board: the app's icon, name and version, the privacy promise, the
 * sources (each kind of credit with its names, opening the full list with licences), and Delete Prayer Log behind a
 * confirmation.
 */
@Composable
private fun ColumnScope.AboutPage(actions: SettingsActions, onOpenCredits: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val open = { url: String -> runCatching { uriHandler.openUri(url) }; Unit }
    // The website has an Arabic home at / and an English one at /en/, each with its privacy page.
    val english = LocalConfiguration.current.locales[0].language == "en"
    val sitePath = { if (english) "/en/" else "/" }
    val privacyPath = { if (english) "/en/privacy" else "/privacy" }
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AppIconTile()
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.about_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
    SectionTitle(stringResource(R.string.about_privacy_section))
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.privacy_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.privacy_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RowDivider()
        NavRow(stringResource(R.string.about_privacy_policy), WEBSITE_HOST + privacyPath(), onClick = { open(WEBSITE + privacyPath()) })
    }
    SectionTitle(stringResource(R.string.about_contact_section))
    Card {
        NavRow(stringResource(R.string.about_website), WEBSITE_HOST + sitePath(), onClick = { open(WEBSITE + sitePath()) })
        RowDivider()
        NavRow(stringResource(R.string.about_contact), CONTACT_EMAIL, onClick = { open("mailto:$CONTACT_EMAIL") })
    }
    SectionTitle(stringResource(R.string.about_sources_section))
    Card {
        CreditKind.entries.forEachIndexed { index, kind ->
            if (index > 0) RowDivider()
            val names = Credits.of(kind).joinToString(" · ") { it.name.substringBefore(" (").substringBefore(":") }
            NavRow(stringResource(kind.title), names.ifEmpty { stringResource(R.string.about_recordings_none_short) }, onClick = onOpenCredits)
        }
    }
    SectionTitle(stringResource(R.string.settings_section_your_data))
    Card {
        DeleteDataRow(
            R.string.delete_prayer_log, R.string.delete_prayer_log_summary, R.string.delete_prayer_log_done,
            R.string.delete_prayer_log_title, R.string.delete_prayer_log_body, actions::deletePrayerLog,
        )
        RowDivider()
        DeleteDataRow(
            R.string.delete_adhkar_record, R.string.delete_adhkar_record_summary, R.string.delete_adhkar_record_done,
            R.string.delete_adhkar_record_title, R.string.delete_adhkar_record_body, actions::deleteAdhkarRecord,
        )
    }
}

/** The launcher icon as a rounded tile: its background layer with the foreground drawn over it. Decorative. */
@Composable
private fun AppIconTile() {
    Box(
        Modifier.size(84.dp).clip(RoundedCornerShape(24.dp)).clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.ic_launcher_background), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        // An adaptive icon's foreground is 108dp with the visible 72dp in the middle: scale it so that part fills the tile.
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.requiredSize(126.dp))
    }
}

/** Every library, dataset, font and recording the app is built from, with its licence; each opens its website. */
@Composable
private fun ColumnScope.CreditsPage() {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { AboutContent() }
    }
}

/** Delete Prayer Log (#25) or Delete Adhkar Record (#74), behind an in-app confirmation. */
@Composable
private fun DeleteDataRow(
    @StringRes title: Int,
    @StringRes summary: Int,
    @StringRes done: Int,
    @StringRes dialogTitle: Int,
    @StringRes body: Int,
    onDelete: () -> Unit,
) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    var deleted by rememberSaveable { mutableStateOf(false) }
    val error = MaterialTheme.colorScheme.error
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { confirming = true }
            .heightIn(min = 60.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(painterResource(R.drawable.ic_trash), contentDescription = null, tint = error, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium, color = error)
            Text(
                stringResource(if (deleted) done else summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(dialogTitle)) },
            text = { Text(stringResource(body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    deleted = true
                    onDelete()
                }) {
                    Text(stringResource(R.string.delete_prayer_log_confirm), color = error)
                }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.cancel)) } },
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
            .padding(horizontal = 12.dp, vertical = 6.dp),
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
internal val CalculationMethod.description: Int
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
internal val DigitStyle.label: Int
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
internal val AsrConvention.description: Int
    get() = when (this) {
        AsrConvention.STANDARD -> R.string.asr_standard_description
        AsrConvention.HANAFI -> R.string.asr_hanafi_description
    }

/** The app's website and public contact address (About page, store listing and privacy policy use the same ones). */
private const val WEBSITE_HOST = "mawaqeet.nileorbit.com"
private const val WEBSITE = "https://$WEBSITE_HOST"
private const val CONTACT_EMAIL = "contact@nileorbit.com"
