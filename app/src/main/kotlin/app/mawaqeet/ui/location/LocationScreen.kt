package app.mawaqeet.ui.location

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.mawaqeet.R
import app.mawaqeet.core.City
import app.mawaqeet.core.CityDirectory
import app.mawaqeet.core.CountryDefaults
import app.mawaqeet.core.LocationSource
import app.mawaqeet.core.MethodSuggestion
import app.mawaqeet.core.PrayerSettings
import app.mawaqeet.core.SavedLocation
import app.mawaqeet.ui.settings.label
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Settings → Prayer Times → Location: search the bundled cities by Arabic or English name, or type coordinates;
 * either is stored as a [LocationSource.MANUAL] location. Afterwards the country's suggested Calculation Method
 * and Asr Convention are offered with the reason. [cities] is null while the list loads.
 */
@Composable
fun LocationScreen(
    cities: CityDirectory?,
    settings: PrayerSettings,
    onChoose: (SavedLocation) -> Unit,
    onAcceptSuggestion: (MethodSuggestion) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val arabic = isArabicUi()
    var query by rememberSaveable { mutableStateOf("") }
    var latitude by rememberSaveable { mutableStateOf("") }
    var longitude by rememberSaveable { mutableStateOf("") }
    var invalidCoordinates by rememberSaveable { mutableStateOf(false) }
    // After a choice: the country it suggests for, and for typed coordinates the city whose zone is used.
    var suggestionCountry by rememberSaveable { mutableStateOf<String?>(null) }
    var zoneNote by rememberSaveable { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf(emptyList<City>()) }
    LaunchedEffect(cities, query) {
        results = cities?.let { withContext(Dispatchers.Default) { it.search(query) } } ?: emptyList()
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                Text(
                    stringResource(R.string.location),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
            }

            val country = suggestionCountry
            if (country != null) {
                SuggestionCard(
                    suggestion = CountryDefaults.suggestionFor(country),
                    settings = settings,
                    zoneNote = zoneNote,
                    onAccept = { onAcceptSuggestion(it); onBack() },
                    onDone = onBack,
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.location_search_label)) },
                supportingText = { Text(stringResource(R.string.location_search_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            when {
                cities == null -> Text(stringResource(R.string.location_loading))
                query.isNotBlank() && results.isEmpty() -> Text(stringResource(R.string.location_no_results))
            }
            Column {
                results.forEach { city ->
                    CityRow(city, arabic) {
                        onChoose(SavedLocation(city.location, LocationSource.MANUAL, city.ref()))
                        suggestionCountry = city.countryCode
                        zoneNote = null
                        query = ""
                    }
                }
            }

            HorizontalDivider()
            Text(
                stringResource(R.string.location_coordinates),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            OutlinedTextField(
                value = latitude,
                onValueChange = { latitude = it; invalidCoordinates = false },
                label = { Text(stringResource(R.string.location_latitude)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = longitude,
                onValueChange = { longitude = it; invalidCoordinates = false },
                label = { Text(stringResource(R.string.location_longitude)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            if (invalidCoordinates) {
                Text(stringResource(R.string.location_invalid_coordinates), color = MaterialTheme.colorScheme.error)
            }
            val zoneNoteFormat = stringResource(R.string.location_zone_note)
            Button(
                enabled = cities != null,
                onClick = {
                    val lat = parseCoordinate(latitude)
                    val lon = parseCoordinate(longitude)
                    if (cities == null || lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
                        invalidCoordinates = true
                    } else {
                        // Typed coordinates take the time zone (and country) of the nearest bundled city.
                        val nearest = cities.nearest(lat, lon)
                        onChoose(nearest.toSavedLocation())
                        suggestionCountry = nearest.city.countryCode
                        zoneNote = String.format(zoneNoteFormat, nearest.city.displayName(arabic), nearest.city.zone.id)
                    }
                },
            ) { Text(stringResource(R.string.location_use_coordinates)) }

            Text(
                stringResource(R.string.location_credit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CityRow(city: City, arabic: Boolean, onClick: () -> Unit) {
    val countryName = countryName(city.countryCode)
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(city.displayName(arabic), style = MaterialTheme.typography.bodyLarge)
        val other = if (arabic) city.name else city.arabicName
        Text(
            listOfNotNull(other?.takeIf { it != city.displayName(arabic) }, countryName).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SuggestionCard(
    suggestion: MethodSuggestion,
    settings: PrayerSettings,
    zoneNote: String?,
    onAccept: (MethodSuggestion) -> Unit,
    onDone: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (zoneNote != null) Text(zoneNote, style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(
                    R.string.suggestion_reason,
                    stringResource(suggestion.method.label),
                    stringResource(suggestion.asrConvention.label),
                    countryName(suggestion.countryCode),
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
            val alreadyUsed = settings.method == suggestion.method && settings.asrConvention == suggestion.asrConvention
            if (alreadyUsed) {
                Text(stringResource(R.string.suggestion_already_used), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onDone) { Text(stringResource(R.string.done)) }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onAccept(suggestion) }) { Text(stringResource(R.string.suggestion_accept)) }
                    OutlinedButton(onClick = onDone) { Text(stringResource(R.string.suggestion_change)) }
                }
            }
        }
    }
}

/** True when the screen is showing Arabic: city names are then shown in Arabic where known. */
@Composable
fun isArabicUi(): Boolean = LocalConfiguration.current.locales[0].language == "ar"

/** The country's name in the UI language, from the platform's locale data. */
@Composable
private fun countryName(countryCode: String): String {
    val locale = LocalConfiguration.current.locales[0]
    return Locale.Builder().setRegion(countryCode).build().getDisplayCountry(locale).ifEmpty { countryCode }
}

/** How Home and Settings name the location: the city, "near <city>" for typed coordinates, or the default. */
@Composable
fun locationName(saved: SavedLocation?): String {
    val city = saved?.city
    return when {
        saved == null -> stringResource(R.string.default_location_name)
        city == null -> "%.4f, %.4f".format(Locale.ROOT, saved.location.latitude, saved.location.longitude)
        city.isNear -> stringResource(R.string.location_near_city, city.displayName(isArabicUi()))
        else -> city.displayName(isArabicUi())
    }
}

/** Accepts Western or Arabic-Indic digits, and «٫» or «,» as the decimal separator. */
internal fun parseCoordinate(text: String): Double? {
    val western = text.trim().map { c ->
        when (c) {
            in '٠'..'٩' -> '0' + (c - '٠')
            in '۰'..'۹' -> '0' + (c - '۰')
            '٫', ',' -> '.'
            '−' -> '-'
            else -> c
        }
    }.joinToString("")
    return western.toDoubleOrNull()?.takeIf { it.isFinite() }
}
