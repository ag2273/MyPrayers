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

- `PrayerCore(settings: PrayerSettings, clock: java.time.Clock)`. It is cheap to build; create a new one when settings change. The current instant always comes from the clock, never from `Instant.now()`.
- Queries are methods returning immutable data classes (`displayDay(): DisplayDay`). New queries from the spec (Prayer Day lookup, Prayer Log commands, weekly summary, week grid, alert plan, location change check, country defaults) are added as further methods or as collaborators passed to the constructor (e.g. a Prayer Log repository interface the app implements).
- Times are `java.time.ZonedDateTime` in the location's zone; calendar days are `LocalDate` at the location, never the device's zone.
- Display rules (Jumu'ah, Sunrise never being the next Prayer) are answered by the core (`PrayerTime.isJumuah`, `DisplayDay.next`), so the app and widgets never re-derive them.

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
