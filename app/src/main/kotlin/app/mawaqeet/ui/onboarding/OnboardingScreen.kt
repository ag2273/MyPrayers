package app.mawaqeet.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import app.mawaqeet.ui.components.Card
import app.mawaqeet.ui.components.Segment
import app.mawaqeet.ui.components.SegmentedChoice
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.mawaqeet.R
import app.mawaqeet.alerts.AlertPermissions
import app.mawaqeet.core.AlarmKillingBrand
import app.mawaqeet.core.AsrConvention
import app.mawaqeet.core.CalculationMethod
import app.mawaqeet.core.CityDirectory
import app.mawaqeet.core.CountryDefaults
import app.mawaqeet.core.LocationSource
import app.mawaqeet.core.PrayerSettings
import app.mawaqeet.core.SavedLocation
import app.mawaqeet.location.DeviceLocation
import app.mawaqeet.ui.fixalerts.FixAlertsContent
import app.mawaqeet.ui.location.LocationScreen
import app.mawaqeet.ui.location.locationName
import app.mawaqeet.ui.settings.ChoiceRow
import app.mawaqeet.ui.settings.description
import app.mawaqeet.ui.settings.label
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What onboarding changes. Every choice is stored at once, so leaving half-way keeps what was chosen. */
interface OnboardingActions {
    /** Switches the whole app to `ar` or `en` (the activity is recreated; the step is kept). */
    fun setLanguage(languageTag: String)
    fun chooseLocation(saved: SavedLocation)
    fun setCalculationMethod(method: CalculationMethod)
    fun setAsrConvention(convention: AsrConvention)

    /** The system dialog the first time, the app's notification settings after "Don't allow". */
    fun grantNotifications()

    /** The system "Alarms & reminders" page (Android 12+). */
    fun grantExactAlarms()

    /** The device's approximate position, or null; called only once the permission is granted. */
    suspend fun readDevicePosition(): android.location.Location?

    /** Ends onboarding; [batteryDone] when the user tapped Done (not Skip) on the battery step. */
    fun finish(batteryDone: Boolean)
}

/** The steps, in order. A step that has nothing to ask (permissions already granted, a brand that keeps alarms) is skipped. */
enum class OnboardingStep { WELCOME, LOCATION, CITY, METHOD, PERMISSIONS, BATTERY }

/**
 * First run (#29), as on the redesign's Onboarding board: language, location (approximate, else city search or
 * coordinates), the suggested Calculation Method and Asr Convention with the reason (the full lists one tap away),
 * then the notification and (14+) exact-alarm permissions together on one screen, each explained. It cannot finish
 * until both are granted. On alarm-killing brands a skippable battery step follows. Each step keeps its actions at the
 * bottom, under the step dots; the welcome screen carries the app's only geometric pattern.
 */
@Composable
fun OnboardingScreen(
    settings: PrayerSettings,
    savedLocation: SavedLocation?,
    loadCities: suspend () -> CityDirectory,
    permissions: AlertPermissions,
    brand: AlarmKillingBrand?,
    actions: OnboardingActions,
) {
    var step by rememberSaveable { mutableStateOf(OnboardingStep.WELCOME) }
    // After the location: each later step is skipped when it has nothing to ask.
    fun after(current: OnboardingStep): OnboardingStep? = when (current) {
        OnboardingStep.WELCOME -> OnboardingStep.LOCATION
        OnboardingStep.LOCATION, OnboardingStep.CITY -> OnboardingStep.METHOD
        OnboardingStep.METHOD -> if (permissions.allGranted) after(OnboardingStep.PERMISSIONS) else OnboardingStep.PERMISSIONS
        OnboardingStep.PERMISSIONS -> if (brand != null) OnboardingStep.BATTERY else null
        OnboardingStep.BATTERY -> null
    }
    fun next() {
        val following = after(step)
        if (following != null) step = following else actions.finish(batteryDone = false)
    }
    val previous = when (step) {
        OnboardingStep.WELCOME -> null
        OnboardingStep.LOCATION -> OnboardingStep.WELCOME
        OnboardingStep.CITY -> OnboardingStep.LOCATION
        OnboardingStep.METHOD -> OnboardingStep.LOCATION
        OnboardingStep.PERMISSIONS -> OnboardingStep.METHOD
        OnboardingStep.BATTERY -> OnboardingStep.PERMISSIONS
    }
    BackHandler(enabled = previous != null) { previous?.let { step = it } }
    val cities by produceState<CityDirectory?>(null) { value = withContext(Dispatchers.IO) { loadCities() } }

    if (step == OnboardingStep.CITY) {
        // Refused or unavailable approximate location: the city search and coordinates of Settings → Location.
        LocationScreen(
            cities = cities,
            settings = settings,
            onChoose = { actions.chooseLocation(it); step = OnboardingStep.METHOD },
            onAcceptSuggestion = {},
            onBack = { step = OnboardingStep.LOCATION },
            readDevicePosition = actions::readDevicePosition,
        )
        return
    }
    val dots: @Composable () -> Unit = { StepDots(step, brand != null) }
    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (step) {
                OnboardingStep.WELCOME -> Welcome(actions, dots, onStart = ::next)
                OnboardingStep.LOCATION -> LocationStep(
                    cities = cities,
                    actions = actions,
                    dots = dots,
                    onLocated = { step = OnboardingStep.METHOD },
                    onSearch = { step = OnboardingStep.CITY },
                )
                OnboardingStep.METHOD -> MethodStep(settings, savedLocation, actions, dots, onContinue = ::next)
                OnboardingStep.PERMISSIONS -> PermissionsStep(permissions, brand != null, actions, dots, onContinue = ::next)
                OnboardingStep.BATTERY -> StepLayout(
                    dots = dots,
                    bottom = {
                        // Only reachable once both permissions are granted (their step can't be passed otherwise).
                        PrimaryButton(stringResource(R.string.done)) { actions.finish(batteryDone = true) }
                        TextButton(onClick = { actions.finish(batteryDone = false) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.onboarding_skip))
                        }
                    },
                ) {
                    Heading(R.string.onboarding_battery_title)
                    FixAlertsContent(brand)
                }
                OnboardingStep.CITY -> Unit
            }
        }
    }
}

/**
 * One step's page: its content scrolls at the top, and the step dots and its actions stay at the bottom, where the
 * thumb is, as on the Onboarding board.
 */
@Composable
private fun StepLayout(
    dots: @Composable () -> Unit,
    bottom: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp)) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            dots()
            bottom()
        }
    }
}

@Composable
private fun Heading(title: Int) {
    Text(stringResource(title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
}

@Composable
private fun Explanation(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Where the person is in onboarding: one dot per step, the current one longer. Decorative for TalkBack. */
@Composable
private fun StepDots(step: OnboardingStep, battery: Boolean) {
    val steps = listOfNotNull(
        OnboardingStep.WELCOME, OnboardingStep.LOCATION, OnboardingStep.METHOD,
        OnboardingStep.PERMISSIONS, OnboardingStep.BATTERY.takeIf { battery },
    )
    val current = steps.indexOf(step)
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        steps.forEachIndexed { i, _ ->
            Box(
                Modifier
                    .size(width = if (i == current) 24.dp else 8.dp, height = 8.dp)
                    .background(if (i == current) colors.primary else colors.outlineVariant, CircleShape),
            )
        }
    }
}

/** The big primary action at the bottom of a step. */
@Composable
private fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) { Text(text) }
}

/** A step's picture: a rounded block with an icon or the pattern in the middle. Decorative. */
@Composable
private fun Hero(color: Color, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(200.dp).background(color, RoundedCornerShape(28.dp)).clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun Welcome(actions: OnboardingActions, dots: @Composable () -> Unit, onStart: () -> Unit) {
    StepLayout(dots, bottom = { PrimaryButton(stringResource(R.string.onboarding_start), onClick = onStart) }) {
        Hero(MaterialTheme.colorScheme.tertiaryContainer) {
            GeometricStar(MaterialTheme.colorScheme.tertiary)
        }
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        Explanation(stringResource(R.string.onboarding_welcome))
        val arabic = LocalConfiguration.current.locales[0].language == "ar"
        SegmentedChoice(
            listOf(
                Segment(stringResource(R.string.language_arabic), arabic) { actions.setLanguage("ar") },
                Segment(stringResource(R.string.language_english), !arabic) { actions.setLanguage("en") },
            ),
        )
    }
}

/** The explanation, then the system request for approximate location only; a refusal leads to city search. */
@Composable
private fun LocationStep(
    cities: CityDirectory?,
    actions: OnboardingActions,
    dots: @Composable () -> Unit,
    onLocated: () -> Unit,
    onSearch: () -> Unit,
) {
    var finding by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun locate() {
        val directory = cities ?: return
        finding = true
        scope.launch {
            val position = actions.readDevicePosition()
            finding = false
            if (position == null) {
                onSearch()
            } else {
                val nearest = withContext(Dispatchers.Default) { directory.nearest(position.latitude, position.longitude) }
                actions.chooseLocation(nearest.toSavedLocation(LocationSource.AUTOMATIC))
                onLocated()
            }
        }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) locate() else onSearch()
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    StepLayout(
        dots,
        bottom = {
            PrimaryButton(
                stringResource(if (finding) R.string.onboarding_finding_location else R.string.onboarding_allow_location),
                enabled = cities != null && !finding,
            ) { if (DeviceLocation.isGranted(context)) locate() else launcher.launch(DeviceLocation.PERMISSION) }
            OutlinedButton(onClick = onSearch, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
                Text(stringResource(R.string.onboarding_search_city))
            }
        },
    ) {
        Hero(MaterialTheme.colorScheme.primaryContainer) {
            Icon(painterResource(R.drawable.ic_pin), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(88.dp))
        }
        Heading(R.string.location_permission_title)
        Explanation(stringResource(R.string.location_permission_explanation))
    }
}

/**
 * The country's suggestion, applied once, in a card with its reason; every method and convention is one tap away
 * ("Choose another method") rather than a long list in the way of Continue.
 */
@Composable
private fun MethodStep(
    settings: PrayerSettings,
    savedLocation: SavedLocation?,
    actions: OnboardingActions,
    dots: @Composable () -> Unit,
    onContinue: () -> Unit,
) {
    val country = savedLocation?.city?.countryCode
    val suggestion = country?.let(CountryDefaults::suggestionFor)
    var appliedFor by rememberSaveable { mutableStateOf<String?>(null) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(suggestion) {
        if (suggestion != null && appliedFor != suggestion.countryCode) {
            appliedFor = suggestion.countryCode
            actions.setCalculationMethod(suggestion.method)
            actions.setAsrConvention(suggestion.asrConvention)
        }
    }
    val locale = LocalConfiguration.current.locales[0]
    val countryName = suggestion?.let {
        java.util.Locale.Builder().setRegion(it.countryCode).build().getDisplayCountry(locale).ifEmpty { it.countryCode }
    }
    val isSuggested = suggestion != null && settings.method == suggestion.method && settings.asrConvention == suggestion.asrConvention
    StepLayout(dots, bottom = { PrimaryButton(stringResource(R.string.onboarding_continue), onClick = onContinue) }) {
        Heading(R.string.onboarding_method_title)
        Explanation(stringResource(R.string.onboarding_method_change))
        val colors = MaterialTheme.colorScheme
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.surfaceContainerLowest, RoundedCornerShape(22.dp))
                .border(2.dp, if (isSuggested) colors.primary else colors.outlineVariant, RoundedCornerShape(22.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (isSuggested && countryName != null) {
                Text(
                    stringResource(R.string.onboarding_suggested_for, countryName),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onPrimaryContainer,
                    modifier = Modifier.background(colors.primaryContainer, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 2.dp),
                )
            }
            Text(stringResource(settings.method.label), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.onboarding_asr_line, stringResource(settings.asrConvention.label)),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(painterResource(R.drawable.ic_pin), contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
                Text(locationName(savedLocation), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            if (suggestion != null) {
                Text(
                    stringResource(
                        R.string.suggestion_reason,
                        stringResource(suggestion.method.label),
                        stringResource(suggestion.asrConvention.label),
                        countryName.orEmpty(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        // With no suggestion for the country (or none yet), the lists are the step.
        if (suggestion != null) {
            TextButton(onClick = { showAll = !showAll }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(if (showAll) R.string.onboarding_hide_methods else R.string.onboarding_other_method))
            }
        }
        if (showAll || suggestion == null) {
            Text(stringResource(R.string.asr_convention), style = MaterialTheme.typography.titleSmall)
            Card {
                Column(Modifier.selectableGroup().padding(vertical = 4.dp)) {
                    AsrConvention.entries.forEach { convention ->
                        ChoiceRow(
                            stringResource(convention.label), stringResource(convention.description),
                            selected = convention == settings.asrConvention,
                            onSelect = { actions.setAsrConvention(convention) },
                        )
                    }
                }
            }
            Text(stringResource(R.string.calculation_method), style = MaterialTheme.typography.titleSmall)
            Card {
                Column(Modifier.selectableGroup().padding(vertical = 4.dp)) {
                    CalculationMethod.entries.forEach { method ->
                        ChoiceRow(
                            stringResource(method.label), stringResource(method.description),
                            selected = method == settings.method,
                            onSelect = { actions.setCalculationMethod(method) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Both alert permissions on one screen, each explained, with its own Allow (or ✓ once granted). Continue only once
 * both are granted, so onboarding can't finish without them. On alarm-killing brands a note says what comes next.
 */
@Composable
private fun PermissionsStep(
    permissions: AlertPermissions,
    battery: Boolean,
    actions: OnboardingActions,
    dots: @Composable () -> Unit,
    onContinue: () -> Unit,
) {
    StepLayout(
        dots,
        bottom = { PrimaryButton(stringResource(R.string.onboarding_continue), enabled = permissions.allGranted, onClick = onContinue) },
    ) {
        Heading(R.string.onboarding_permissions_title)
        Explanation(stringResource(R.string.onboarding_permissions_intro))
        PermissionCard(
            R.string.grant_notifications_title, R.string.grant_notifications_body,
            granted = permissions.notifications, onGrant = actions::grantNotifications,
        )
        PermissionCard(
            R.string.grant_exact_alarms_title, R.string.grant_exact_alarms_body,
            granted = permissions.exactAlarms, onGrant = actions::grantExactAlarms,
        )
        if (battery) {
            val colors = MaterialTheme.colorScheme
            Column(
                Modifier.fillMaxWidth().background(colors.tertiaryContainer, RoundedCornerShape(20.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(stringResource(R.string.onboarding_battery_row), style = MaterialTheme.typography.titleSmall, color = colors.onTertiaryContainer)
                Text(stringResource(R.string.onboarding_battery_hint), style = MaterialTheme.typography.bodySmall, color = colors.onTertiaryContainer)
            }
        }
    }
}

/** One permission: what it is for, then Allow, or ✓ once granted; after a refusal, why setup needs it. */
@Composable
private fun PermissionCard(title: Int, body: Int, granted: Boolean, onGrant: () -> Unit) {
    var asked by rememberSaveable { mutableStateOf(false) }
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (granted) {
                    Text(stringResource(R.string.onboarding_granted_short), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                } else {
                    Button(onClick = { asked = true; onGrant() }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(if (asked) R.string.grant_button else R.string.onboarding_allow))
                    }
                }
            }
            if (asked && !granted) Text(stringResource(R.string.onboarding_permission_needed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

/**
 * The app's single geometric pattern, as on the welcome board: an eight-pointed star (two overlapping squares) with
 * a smaller one inside and a dot at its centre.
 */
@Composable
private fun GeometricStar(color: Color) {
    Canvas(Modifier.size(150.dp)) {
        val centre = Offset(size.width / 2, size.height / 2)
        val stroke = Stroke(width = 1.6.dp.toPx())
        fun star(side: Float) {
            val square = Path().apply {
                moveTo(centre.x - side / 2, centre.y - side / 2)
                lineTo(centre.x + side / 2, centre.y - side / 2)
                lineTo(centre.x + side / 2, centre.y + side / 2)
                lineTo(centre.x - side / 2, centre.y + side / 2)
                close()
            }
            drawPath(square, color, style = stroke)
            rotate(45f, centre) { drawPath(square, color, style = stroke) }
        }
        star(size.width * 0.62f)
        star(size.width * 0.36f)
        drawCircle(color, radius = size.width * 0.06f, center = centre)
    }
}
