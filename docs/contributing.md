# Code conventions

How the code is laid out and tested. Vocabulary follows `GLOSSARY.md`; decisions follow `docs/adr/` and the spec (#14).

## Modules

| Module | What | Package |
|---|---|---|
| `:core` | The prayer core: pure Kotlin/JVM, **no Android dependencies**. Owns every rule. | `app.mawaqeet.core` |
| `:app` | Android app: thin adapters (Compose screens, widgets, alarms, storage) around the core. | `app.mawaqeet` (`.ui.<screen>`, `.ui.theme`, later `.widget`, `.alerts`, `.data`) |

- adhan-kotlin is an `implementation` dependency of `:core` and is used only in `core/.../Calculation.kt`. Its types never appear in the core's public interface; the core has its own `Prayer`, `CalculationMethod`, `AsrConvention`.
- Library versions live in `gradle/libs.versions.toml`. AGP 9 has built-in Kotlin: never apply `org.jetbrains.kotlin.android` in `:app`.

## The core's interface

- `PrayerCore(settings: PrayerSettings, clock: java.time.Clock, log: PrayerLogRepository)`. It is cheap to build; create a new one when settings change. The current instant always comes from the clock, never from `Instant.now()`.
- Queries are methods returning immutable data classes (`displayDay(): DisplayDay`). New queries from the spec (Prayer Day lookup, Prayer Log commands, weekly summary, week grid, alert plan, location change check, country defaults) are added as further methods or as collaborators passed to the constructor (e.g. a Prayer Log repository interface the app implements).
- Times are `java.time.ZonedDateTime` in the location's zone; calendar days are `LocalDate` at the location, never the device's zone.
- Display rules (Jumu'ah, Sunrise never being the next Prayer) are answered by the core (`PrayerTime.isJumuah`, `DisplayDay.next`), so the app and widgets never re-derive them.

## The Prayer Log

- A **Prayer Day is identified by a `LocalDate`**: the local calendar date of its Fajr at the place the user was that day. Its identity never depends on the Calculation Method or any other setting; only its times do. `PrayerCore.prayerDayAt(instant)` / `currentPrayerDay()` / `prayerDay(date)` return a `PrayerDay` with its times, each Prayer's `LogState` (`PRAYED`, `NOT_MARKED`, `NOT_YET_DUE`) as of the clock, and `isEditable`.
- Writes go only through `PrayerCore.mark` / `unmark(date, prayer)`, which return a `LogCommandResult` (`DONE`, `NOT_YET_DUE`, `OUTSIDE_EDIT_WINDOW`). The edit window is the current Prayer Day plus the 6 previous ones.
- `PrayerLogRepository` (in `:core`) is plain storage keyed by (Prayer Day date, `Prayer`) plus the first-use date; every rule stays in the core. Its methods are synchronous and may block, so the app calls the core's log queries and commands on `Dispatchers.IO`. Tests use `InMemoryPrayerLog` in `core/src/test`.
- Android: `app.mawaqeet.data.RoomPrayerLogRepository` over `PrayerLogDatabase` (Room, file `prayer_log.db`, schemas exported to `app/schemas/` and committed). Prayer Days are stored as ISO dates (`2026-11-08`), Prayers as enum names; only prayed entries have rows. A schema change needs a Room migration.
- There is one repository per process, `MawaqeetApplication.prayerLog`. Its `changes: StateFlow<Int>` moves after every write: screens re-query the core when it changes, and widgets must refresh on it too.

## Settings

- **Storage:** `app.mawaqeet.data.SettingsRepository` over one Preferences DataStore (file `datastore/settings.preferences_pb`, created once per process in `MawaqeetApplication`; DataStore allows only one instance per file). `settings: Flow<PrayerSettings>` emits the core's settings; add a key and a setter per new setting. Enums are stored by `name`, so enum entries may be reordered or added but never renamed; a missing or unknown name reads as `PrayerSettings.Default`'s value. App-only preferences (not core input) may live in the same store, exposed as their own flow.
- **Core input:** `MainActivity` collects `settings` and builds a new `PrayerCore` for each value (`remember(settings)`), so every screen follows a change immediately. Nothing is shown until the first value is read, so default times never flash.
- **Settings screen:** `ui/settings/SettingsScreen.kt` lists the six groups in the spec's order (Prayer Times, Alerts, Prayer Log, Language & display, Widgets, About), each a `SettingsGroup(titleRes) { ... }`. A ticket that adds settings replaces its group's `ComingSoon()` with its own private `XxxGroup` composable, adds callbacks to `SettingsActions` (implemented in `MainActivity` with the repository), and reuses `SettingRow` (opens a chooser) and `ChoiceRow` (radio option with a one-line description).
- **Calculation Methods:** `CalculationMethod` order is the order Settings lists them; their Arabic and English names and one-line descriptions are `method_<name>` / `method_<name>_description` strings.
- **Spot-checks:** `core/src/test/.../CalculationSpotCheckTest.kt` compares the core with official published timetables within ±1 minute. Each table cites its source; add new ones only from a real published source.

## Location

- **Stored location:** `SettingsRepository.setLocation(SavedLocation)` / `savedLocation: Flow<SavedLocation?>` (null until the user sets one; `settings` then uses `PrayerSettings.Default.location`) / `clearLocation()`. `SavedLocation(location, source, city)`: `source` is `LocationSource.MANUAL` (city search or typed coordinates) or `AUTOMATIC` (#24); a manual location never changes by itself. `city: CityRef?` (id, English and Arabic name, country, `isNear`) names it without loading the city list: Home and Settings show it via `ui/location/locationName(saved)` ("near <city>" when `isNear`). Keys: `location_latitude`, `location_longitude`, `location_zone`, `location_source`, `location_city_id`, `location_city_name`, `location_city_name_ar`, `location_country`, `location_near_city`.
- **City list:** asset `app/src/main/assets/cities.tsv` (~34k GeoNames cities15000 places, ~1.0 MB compressed in the APK), regenerated only by `python3 -I tools/cities/generate_cities.py` (downloads at generation time; the build never needs the network). Read it through the core: `CityDirectory.read(stream)`, held once per process as `MawaqeetApplication.cities` (lazy; first touch off the main thread). `search(query, limit)` (Arabic or English, folds case, diacritics, hamza forms, ة/ه, ى/ي, optional «ال»; whole name, then prefix, then word start; larger cities first), `byId(id)`, `nearest(lat, lon): NearestCity` (`city`, `distanceKm`, `location` = the point with the city's zone; `toSavedLocation()`), `City.location` / `City.ref()` / `displayName(arabic)`.
- **Time zone of a point:** there is no zone-boundary data; typed coordinates (and any other point) take the IANA zone of the nearest bundled city. The Location screen says so. Near a zone or country border this can pick the neighbour's.
- **Country defaults:** `CountryDefaults.suggestionFor(countryCode): MethodSuggestion(countryCode, method, asrConvention)`; the country is the nearest bundled city's. The table is ADR 0003; change both together. Country names come from the platform (`Locale.getDisplayCountry`), not the dataset.
- **Prayer Days are fixed by place:** the location of past Prayer Days is kept as `PastPlace(until, location)` rows in `PrayerLogRepository` (`pastPlaces()` / `addPastPlace`; Room table `past_place`, DB version 2). A Prayer Day before an entry's `until` (and on or after the previous one's) is computed at that entry's location; the Prayer Day in progress at a change and every later one use the current settings' location. Only the location is frozen: a Calculation Method or Asr Convention change still recalculates every day. The Fajr boundary (`prayerDayAt`) uses each day's own place. A Prayer marked before travel stays `PRAYED` even if its new time is later.
- **Changing the location:** always through `MawaqeetApplication.locationUpdater` (`LocationUpdater.change(saved)`), never `SettingsRepository.setLocation` directly: it calls `PrayerCore(oldSettings, …).recordLocationChange()` first, then saves the location and clears the possibly-stale flag.
- **Automatic location and travel:** `app.mawaqeet.location.DeviceLocation` reads `ACCESS_COARSE_LOCATION` only, through `LocationManagerCompat` (fused if present, then network, then GPS; no Google Play services), only while the app is open. On every resume `MainActivity` passes the fix to `LocationUpdater.onDevicePosition(lat, lon)`, which applies the core rule `LocationChangeCheck.check(stored, lat, lon)` (moved more than 10 km: automatic → `Updated`, a short note on Home; manual → `Ask`, a dialog whose "Update" saves it as automatic; a declined move is not asked again in that process). The new place is `cities.nearest(...).toSavedLocation(LocationSource.AUTOMATIC)`. `ACTION_TIMEZONE_CHANGED` (in `RescheduleReceiver`) only sets `SettingsRepository.locationMaybeStale` (key `location_maybe_stale`); Home shows a hint until the next check or change.
- **Permission flow:** `ui/location/rememberLocationPermissionRequest(onResult)` returns a `LocationPermissionRequest`; `start()` shows the explanation (used only for Prayer Times, never leaves the phone), then the system request. Denial or "Not now" gives `false` and the caller falls back to city search. Onboarding reuses it.
- **Credits (About, #33):** GeoNames (CC BY 4.0, https://www.geonames.org/) and Natural Earth (public domain); text in `app/src/main/assets/licenses/cities.txt`.

## Dates, digits and language

- **One formatter:** every time, countdown, date and number the user sees goes through `app.mawaqeet.core.DisplayFormatter(locale, digitStyle)` (`time`, `countdown`, `gregorianDate`, `hijriDate`, `number`, `signedNumber`). Screens, notifications and widgets never build their own `DateTimeFormatter` or `String.format` for display; add a method to `DisplayFormatter` (with core tests in Arabic and English) when a new shape is needed. Machine strings use `Locale.ROOT`, never the formatter.
- **Hijri:** `HijriDate.of(date, offsetDays)` (Umm al-Qura via `java.time.chrono.HijrahDate`) gives plain numbers; `DisplayDay.hijriDate` already has the user's offset (`PrayerSettings.hijriOffsetDays`, `PrayerSettings.HIJRI_OFFSET_RANGE` = -2..2). Month names live only in `DisplayFormatter`; never use a platform Islamic-calendar formatter.
- **Digits:** `DigitStyle` (`AUTOMATIC` / `ARABIC_INDIC` / `WESTERN`) is an app-only preference (`SettingsRepository.digitStyle`), not core input. `DisplayFormatter` applies it as a `-u-nu-` extension and passes the resulting `DecimalStyle` to `java.time`.
- **In Compose:** `MainActivity` wraps its content in `ProvideDisplayFormatter(digits)` (`app/.../ui/DisplayFormatting.kt`); read `LocalDisplayFormatter.current`. Outside Compose screens (notifications, receivers, widgets) use `displayFormatter(context, digits)`.
- **Language:** `AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ar"|"en"))` from Settings, Language & display; the activity is recreated. The locale config is generated (`generateLocaleConfig`). Wrap a signed or Latin-script value inside Arabic text with `BidiFormatter.unicodeWrap` so `+1` never shows as `1+`.

## Alerts

- **Plan (core):** `PrayerCore.alertPlan(): AlertPlan` = `alerts: List<PlannedAlert>` (time order, due strictly after now and within `AlertPlan.WINDOW` = 48 h) + `nextLocalMidnight`. A `PlannedAlert` has `instant`, `prayer`, `kind` (`AlertKind.ADHAN_ALERT` / `PRAYER_REMINDER`), `sound` (`AlertSound.ShortAdhan` / `FullAdhan` / `FajrFullAdhan` / `CustomAdhan(reference)` / `Silent`), `deliveryMode` (`DeliveryMode.EXACT` / `ALARM_CLOCK`), `prayerDay` and `isJumuah`. Per-Prayer alert settings, reminders and alarm-clock mode are decided in the core from `PrayerSettings`; the adapter only follows the plan.
- **Scheduling (app, `app.mawaqeet.alerts`):** call `AlertRescheduler.reschedule(context)` (suspend, serialised) for any reason to re-plan; receivers use `rescheduleAsync(this, context)`. It is already called on boot, locked boot, time set, time-zone change, `MY_PACKAGE_REPLACED`, exact-alarm grant (`RescheduleReceiver`), every alarm (`AlertReceiver`), every settings value (`MawaqeetApplication.onCreate` collects `settings.settings`, so a new setting needs no extra wiring) and when the Grant screen is passed. `AlertScheduler.apply(plan)` reconciles and stores; never call `AlarmManager` for alerts elsewhere.
- **Locked boot:** before the first unlock DataStore (credential-encrypted) is unreadable, so `rearmStoredPlan(now)` re-arms the copy in device-protected storage: `createDeviceProtectedStorageContext().filesDir/alert_plan.txt` (`AlertPlanStore`, versioned text format; bump the version line on change). Backup rules (#25): `domain="device_file" path="alert_plan.txt"`, exclude it (it is regenerated).
- **Alarms:** request code = stable function of (Prayer Day, Prayer, kind) (`PlannedAlert.requestCode`, also the notification id); the local-midnight alarm uses request code 0 and action `AlertReceiver.ACTION_MIDNIGHT`. `AlertReceiver` (direct-boot aware) handles `ACTION_ALERT` (posts the notification on channel `adhan_alerts`, created silent: the adhan audio comes from the player service) and `ACTION_MIDNIGHT`; it is where the Adhan player (#22) starts and where widgets refresh at each Prayer Time and at midnight (#27).
- **Permission gate:** `AlertPermissions.of(context)` (notifications enabled; exact alarms via `canScheduleExactAlarms()` on 12+, always true before). `MainActivity` re-checks on every resume and shows `ui/permissions/GrantScreen` instead of everything else while either is missing. Robolectric denies exact alarms by default: activity tests need `AlertPermissionsRule` (rule `order = 0`, compose rule `order = 1`).

## Tests

- Nearly all tests are plain JVM tests in `core/src/test`, through the core's public interface only, with a fixed `Clock` (`Clock.fixed(...)`). No tests of internal classes or adhan-kotlin.
- Test names are backtick sentences stating the decision: `` `after local midnight the display day is the new date and the next Prayer is its Fajr` ``.
- Expected values come from an independent source (a published timetable, a worked example), never recomputed the way the code does.
- `:app` has only thin Robolectric tests (`@Config(sdk = [36])`). Robolectric defaults to `en-rUS`, so pin `@Config(qualifiers = "ar")` or `"fr"` (to prove Arabic is the fallback).

## Resources and look

- Arabic strings in `res/values/` (the unqualified default, `res/resources.properties`), English in `res/values-en/`. Write Arabic first.
- Colours: `ui/theme/Theme.kt` holds the brand palette as light and dark `ColorScheme`s; use `MaterialTheme.colorScheme`, not hex values, in screens.
- Fonts: `MaterialTheme.typography` display/headline/title styles are Noto Kufi Arabic (headings, large times); body/label styles are Noto Naskh Arabic (running text). Widgets use the system font.
- Font licences (OFL) are in `app/src/main/assets/licenses/` for the About screen.

## Commands

```bash
./gradlew :core:test                                        # fast loop
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug
```

Needs `local.properties` with `sdk.dir=...` (untracked). CI runs `:core:test` on every push.
