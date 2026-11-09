package app.mawaqeet.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
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

/** The steps, in order. A step that has nothing to ask (a permission already granted, a brand that keeps alarms) is skipped. */
enum class OnboardingStep { WELCOME, LOCATION, CITY, METHOD, NOTIFICATIONS, EXACT_ALARMS, BATTERY }

/**
 * First run (#29): language, location (approximate, else city search or coordinates), the suggested Calculation
 * Method and Asr Convention with the reason, then notification and (14+) exact-alarm permissions, each explained
 * first. It cannot finish until both alert permissions are granted. On alarm-killing brands a skippable battery
 * step follows. The welcome screen carries the app's only geometric pattern.
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
        OnboardingStep.METHOD -> if (permissions.notifications) after(OnboardingStep.NOTIFICATIONS) else OnboardingStep.NOTIFICATIONS
        OnboardingStep.NOTIFICATIONS -> if (permissions.exactAlarms) after(OnboardingStep.EXACT_ALARMS) else OnboardingStep.EXACT_ALARMS
        OnboardingStep.EXACT_ALARMS -> if (brand != null) OnboardingStep.BATTERY else null
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
        OnboardingStep.NOTIFICATIONS -> OnboardingStep.METHOD
        OnboardingStep.EXACT_ALARMS -> OnboardingStep.NOTIFICATIONS
        OnboardingStep.BATTERY -> OnboardingStep.EXACT_ALARMS
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
    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (step) {
                OnboardingStep.WELCOME -> Welcome(actions, onStart = ::next)
                OnboardingStep.LOCATION -> LocationStep(
                    cities = cities,
                    actions = actions,
                    onLocated = { step = OnboardingStep.METHOD },
                    onSearch = { step = OnboardingStep.CITY },
                )
                OnboardingStep.METHOD -> MethodStep(settings, savedLocation, actions, onContinue = ::next)
                OnboardingStep.NOTIFICATIONS -> PermissionStep(
                    title = R.string.onboarding_notifications_title,
                    body = R.string.grant_notifications_body,
                    granted = permissions.notifications,
                    onGrant = actions::grantNotifications,
                    onContinue = ::next,
                )
                OnboardingStep.EXACT_ALARMS -> PermissionStep(
                    title = R.string.onboarding_exact_alarms_title,
                    body = R.string.grant_exact_alarms_body,
                    granted = permissions.exactAlarms,
                    onGrant = actions::grantExactAlarms,
                    onContinue = ::next,
                )
                OnboardingStep.BATTERY -> {
                    Heading(R.string.onboarding_battery_title)
                    FixAlertsContent(brand)
                    // Only reachable once both permissions are granted (their steps can't be passed otherwise).
                    Button(onClick = { actions.finish(batteryDone = true) }, Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.done))
                    }
                    TextButton(onClick = { actions.finish(batteryDone = false) }, Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.onboarding_skip))
                    }
                }
                OnboardingStep.CITY -> Unit
            }
        }
    }
}

@Composable
private fun Heading(title: Int) {
    Text(stringResource(title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
}

@Composable
private fun ColumnScope.Welcome(actions: OnboardingActions, onStart: () -> Unit) {
    GeometricPattern()
    Text(
        stringResource(R.string.app_name),
        style = MaterialTheme.typography.displaySmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { heading() },
    )
    Text(stringResource(R.string.onboarding_welcome), style = MaterialTheme.typography.bodyLarge)
    Text(stringResource(R.string.language), style = MaterialTheme.typography.titleSmall)
    val arabic = LocalConfiguration.current.locales[0].language == "ar"
    Column(Modifier.selectableGroup()) {
        ChoiceRow(stringResource(R.string.language_arabic), "", selected = arabic, onSelect = { actions.setLanguage("ar") })
        ChoiceRow(stringResource(R.string.language_english), "", selected = !arabic, onSelect = { actions.setLanguage("en") })
    }
    Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.onboarding_start)) }
}

/** The explanation, then the system request for approximate location only; a refusal leads to city search. */
@Composable
private fun ColumnScope.LocationStep(
    cities: CityDirectory?,
    actions: OnboardingActions,
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
    Heading(R.string.location_permission_title)
    Text(stringResource(R.string.location_permission_explanation), style = MaterialTheme.typography.bodyLarge)
    Button(
        enabled = cities != null && !finding,
        onClick = { if (DeviceLocation.isGranted(context)) locate() else launcher.launch(DeviceLocation.PERMISSION) },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(if (finding) R.string.onboarding_finding_location else R.string.onboarding_allow_location)) }
    OutlinedButton(onClick = onSearch, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.onboarding_search_city))
    }
}

/** The country's suggestion, applied once with its reason, and every method and convention to change it. */
@Composable
private fun ColumnScope.MethodStep(
    settings: PrayerSettings,
    savedLocation: SavedLocation?,
    actions: OnboardingActions,
    onContinue: () -> Unit,
) {
    val country = savedLocation?.city?.countryCode
    val suggestion = country?.let(CountryDefaults::suggestionFor)
    var appliedFor by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(suggestion) {
        if (suggestion != null && appliedFor != suggestion.countryCode) {
            appliedFor = suggestion.countryCode
            actions.setCalculationMethod(suggestion.method)
            actions.setAsrConvention(suggestion.asrConvention)
        }
    }
    Heading(R.string.onboarding_method_title)
    Text(locationName(savedLocation), style = MaterialTheme.typography.titleMedium)
    if (suggestion != null) {
        Text(
            stringResource(
                R.string.suggestion_reason,
                stringResource(suggestion.method.label),
                stringResource(suggestion.asrConvention.label),
                java.util.Locale.Builder().setRegion(suggestion.countryCode).build()
                    .getDisplayCountry(LocalConfiguration.current.locales[0]).ifEmpty { suggestion.countryCode },
            ),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
    Text(stringResource(R.string.onboarding_method_change), style = MaterialTheme.typography.bodyMedium)
    Text(stringResource(R.string.asr_convention), style = MaterialTheme.typography.titleSmall)
    Column(Modifier.selectableGroup()) {
        AsrConvention.entries.forEach { convention ->
            ChoiceRow(
                stringResource(convention.label), stringResource(convention.description),
                selected = convention == settings.asrConvention,
                onSelect = { actions.setAsrConvention(convention) },
            )
        }
    }
    Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.onboarding_continue)) }
    Text(stringResource(R.string.calculation_method), style = MaterialTheme.typography.titleSmall)
    Column(Modifier.selectableGroup()) {
        CalculationMethod.entries.forEach { method ->
            ChoiceRow(
                stringResource(method.label), stringResource(method.description),
                selected = method == settings.method,
                onSelect = { actions.setCalculationMethod(method) },
            )
        }
    }
}

/** A permission explained first; Continue only once it is granted, so onboarding can't finish without it. */
@Composable
private fun ColumnScope.PermissionStep(title: Int, body: Int, granted: Boolean, onGrant: () -> Unit, onContinue: () -> Unit) {
    var asked by rememberSaveable { mutableStateOf(false) }
    Heading(title)
    Text(stringResource(R.string.grant_intro), style = MaterialTheme.typography.bodyLarge)
    Text(stringResource(body), style = MaterialTheme.typography.bodyLarge)
    if (granted) {
        Text(stringResource(R.string.onboarding_granted), color = MaterialTheme.colorScheme.primary)
    } else {
        Button(onClick = { asked = true; onGrant() }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (asked) R.string.grant_button else R.string.onboarding_allow))
        }
        if (asked) Text(stringResource(R.string.onboarding_permission_needed), color = MaterialTheme.colorScheme.error)
    }
    Button(onClick = onContinue, enabled = granted, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.onboarding_continue))
    }
}

/** The app's single geometric pattern: a faint row of eight-pointed stars (two overlapping squares). */
@Composable
private fun GeometricPattern() {
    val color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    Canvas(Modifier.fillMaxWidth().height(56.dp)) {
        val size = this.size.height * 0.7f
        val count = (this.size.width / (size * 1.3f)).toInt().coerceAtLeast(1)
        val gap = this.size.width / count
        repeat(count) { i ->
            val centre = Offset(gap * (i + 0.5f), this.size.height / 2)
            val square = Path().apply {
                moveTo(centre.x - size / 2, centre.y - size / 2)
                lineTo(centre.x + size / 2, centre.y - size / 2)
                lineTo(centre.x + size / 2, centre.y + size / 2)
                lineTo(centre.x - size / 2, centre.y + size / 2)
                close()
            }
            drawPath(square, color, style = Stroke(width = 2.dp.toPx()))
            rotate(45f, centre) { drawPath(square, color, style = Stroke(width = 2.dp.toPx())) }
        }
    }
}
