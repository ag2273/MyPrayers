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
