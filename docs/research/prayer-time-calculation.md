# Prayer Time calculation library

Research for ticket #2. Researched 2026-11-08. Terms follow [`GLOSSARY.md`](../../GLOSSARY.md).

## Question

Which library should v1 use to calculate Prayer Times, Sunrise and the Hijri date on the device (offline, no backend)? Compare `adhan` (Batoul Apps: adhan-kotlin / adhan-java) with credible alternatives, or writing the calculation ourselves. Cover accuracy, licence, maintenance, Calculation Methods, Asr Conventions, high-latitude rules and Hijri support. Also: how do established apps and libraries pick a default Calculation Method from the user's region, and is there a published mapping we can reuse?

## Short answer / recommendation

1. **Prayer Times + Sunrise: use `com.batoulapps.adhan:adhan2` (adhan-kotlin), currently 0.0.7.** It is MIT-licensed, has no external dependencies besides `kotlinx-datetime`, and is still maintained (commits in October 2026). It ships 11 named Calculation Methods, both Asr Conventions and three high-latitude rules. Its tests check results against published official timetables, within 0–2 minutes. Don't use the old pure-Java `adhan` 1.2.1. It is a branch that has had no release since 2021.
2. **Hijri date: use the platform, not a library.** Use `android.icu.util.IslamicCalendar` with `ISLAMIC_UMALQURA` (API 24+), or `java.time.chrono.HijrahDate` (API 26+). Both implement the Saudi Umm al-Qura calendar from a table that covers AH 1300–1600 (1882–2174 CE). Adhan has no Hijri support at all. Add a user "Hijri day offset" setting (±1–2 days), because sighting-based countries can differ from Umm al-Qura (see Findings §6).
3. **Don't write the astronomy ourselves.** Adhan already implements Meeus's *Astronomical Algorithms*, along with method-specific offsets and edge-case handling we would have to rediscover. Examples are the Moonsighting Committee seasonal rules and the polar-circle guards.
4. **Region → default Calculation Method: no reusable, machine-readable mapping was found in primary sources.** We'll have to write our own small country → method table (ADR-worthy). It can draw on the region notes in Adhan's `METHODS.md` and the PrayTimes.org methods table.

Things v1 must handle when using adhan-kotlin:
- `PrayerTimes(...)` **throws `IllegalStateException`** when it can't produce valid, ordered times (polar regions).
- Umm al-Qura does **not** add the +30 min Ramadan Isha adjustment automatically. The app has to add it.
- The Kotlin port lacks the **Tehran** method and the `PolarCircleResolution` option that adhan-js and adhan-swift have.

## Findings

### 1. adhan-kotlin (and adhan-java)

**Identity / packaging**
- The parent project says the astronomy comes "directly from the book 'Astronomical Algorithms' by Jean Meeus". It lists the official ports as Swift, JS and Kotlin, plus a shared `adhan-testdata` repo. — https://github.com/batoulapps/Adhan
- The `batoulapps/adhan-java` URL now serves the adhan-kotlin repository (cloning it gives identical content). Its README says "All primary development is for the Kotlin Multiplatform version. There is also a branch for a pure Java version." — https://github.com/batoulapps/adhan-kotlin (README), https://github.com/batoulapps/adhan-kotlin/tree/java
- The Gradle coordinate is `com.batoulapps.adhan:adhan2:0.0.7`. On Android, `kotlinx-datetime` "needs either a minimum api level of 26, or enabling of `coreLibraryDesugaring`". — https://github.com/batoulapps/adhan-kotlin#gradle
- Maven Central lists 0.0.1–0.0.7, with `lastUpdated` 2026-06-13. — https://repo1.maven.org/maven2/com/batoulapps/adhan/adhan2/maven-metadata.xml
- The only dependency is `kotlinx-datetime` (0.8.0 in the current build). — https://github.com/batoulapps/adhan-kotlin/blob/main/gradle/libs.versions.toml

**Licence**
- The licence is MIT, "Copyright (c) 2016 Batoul Apps", the same for adhan-js and adhan-swift. — https://github.com/batoulapps/adhan-kotlin/blob/main/LICENSE, https://github.com/batoulapps/adhan-js/blob/master/LICENSE
- MIT works for both a public GitHub repo and a Play Store app. The only condition is keeping the copyright and licence notice, for example on an open-source-licences screen.

**Maintenance**
- Releases (git tags):
  - `adhan2-v0.0.7`: 2026-06-13
  - `adhan2-v0.0.6`: 2025-10-13
  - `adhan2-v0.0.5`: 2024-03-04 — https://github.com/batoulapps/adhan-kotlin/tags
- Latest commit on `main` is 2026-10-29 ("fix(sunnah): honor the rounding setting in sunnah times", PR #96). Polar-circle validation was merged 2026-07-04 (PR #94). — https://github.com/batoulapps/adhan-kotlin/commits/main
- The repo had about 20 commits in the 12 months to 2026-11-08, mainly from two maintainers.
- The 0.0.7 changelog covers: a date-line edge-case fix, Kotlin 2.4.0, and a note that "next version will likely remove macOSX64 and watchOSX64 targets". The 0.0.5 changelog added Turkey (Diyanet), Shafaq and rounding. — https://github.com/batoulapps/adhan-kotlin/blob/main/CHANGELOG.md
- The pure-Java branch's last release is `v1.2.1` (2021-03-06), and its last commit is 2023-04-16. Treat it as unmaintained. — https://github.com/batoulapps/adhan-kotlin/tree/java
- The sibling ports are also active: adhan-js had a commit on 2026-11-01 and adhan-swift on 2026-10-31. — https://github.com/batoulapps/adhan-js, https://github.com/batoulapps/adhan-swift

**Calculation Methods** (`CalculationMethod.kt`) — https://github.com/batoulapps/adhan-kotlin/blob/main/adhan/src/commonMain/kotlin/com/batoulapps/adhan2/CalculationMethod.kt

| Enum | Fajr | Isha | Built-in minute offsets |
|---|---|---|---|
| `MUSLIM_WORLD_LEAGUE` | 18° | 17° | Dhuhr +1 |
| `EGYPTIAN` | 19.5° | 17.5° | Dhuhr +1 |
| `KARACHI` | 18° | 18° | Dhuhr +1 |
| `UMM_AL_QURA` | 18.5° | 90 min after Maghrib | — (Ramadan +30 min Isha is "you should add", not automatic) |
| `DUBAI` | 18.2° | 18.2° | Sunrise −3, Dhuhr/Asr/Maghrib +3 |
| `MOON_SIGHTING_COMMITTEE` | 18° | 18° + seasonal rules | Dhuhr +5, Maghrib +3 |
| `NORTH_AMERICA` (ISNA) | 15° | 15° | Dhuhr +1 ("included for completeness but is not recommended") |
| `KUWAIT` | 18° | 17.5° | — |
| `QATAR` | 18° | 90 min | — |
| `SINGAPORE` | 20° | 18° | Dhuhr +1, rounding UP |
| `TURKEY` (Diyanet approximation) | 18° | 17° | Sunrise −7, Dhuhr +5, Asr +4, Maghrib +7 |
| `OTHER` | 0° | 0° | for custom parameters |

- Any parameter can be overridden with `CalculationParameters.copy(...)`, including the angles, `ishaInterval`, per-Prayer `prayerAdjustments`, `rounding` and `shafaq`. — https://github.com/batoulapps/adhan-kotlin/blob/main/adhan/src/commonMain/kotlin/com/batoulapps/adhan2/CalculationParameters.kt
- The JS and Swift ports also have **Tehran** (Fajr 17.7°, Isha 14°, Maghrib 4.5°). The Kotlin port doesn't, and its `CalculationParameters` has no `maghribAngle` to build it with. — https://github.com/batoulapps/adhan-js/blob/master/src/CalculationMethod.ts, https://github.com/batoulapps/adhan-swift/blob/main/Sources/Models/CalculationMethod.swift

**Asr Conventions**
- `Madhab.SHAFI` (shadow length 1, the default) and `Madhab.HANAFI` (shadow length 2). These map one-to-one to our Standard / Hanafi Asr Convention. — https://github.com/batoulapps/adhan-kotlin/blob/main/adhan/src/commonMain/kotlin/com/batoulapps/adhan2/Madhab.kt

**High-latitude rules**
- `HighLatitudeRule` has three values:
  - `MIDDLE_OF_THE_NIGHT`
  - `SEVENTH_OF_THE_NIGHT`
  - `TWILIGHT_ANGLE` (night portion = angle/60)

  If none is set, `recommendedFor(coordinates)` picks `SEVENTH_OF_THE_NIGHT` above 48° latitude and `MIDDLE_OF_THE_NIGHT` otherwise. — https://github.com/batoulapps/adhan-kotlin/blob/main/adhan/src/commonMain/kotlin/com/batoulapps/adhan2/HighLatitudeRule.kt, https://github.com/batoulapps/adhan-kotlin/blob/main/adhan/src/commonMain/kotlin/com/batoulapps/adhan2/CalculationParameters.kt
- Moonsighting Committee has a built-in special case at or above 55° latitude, plus seasonal Fajr/Isha formulas that depend on `Shafaq` (General / Ahmer / Abyad). — https://github.com/batoulapps/adhan-kotlin/blob/main/adhan/src/commonMain/kotlin/com/batoulapps/adhan2/PrayerTimes.kt, https://github.com/batoulapps/adhan-kotlin/blob/main/adhan/src/commonMain/kotlin/com/batoulapps/adhan2/model/Shafaq.kt
- **Polar behaviour:** if any Prayer Time is missing, or any consecutive pair is out of order or closer than 5 minutes, the constructor `throw IllegalStateException()` ("most likely due to polar circle issues ... all-or-nothing result"). — https://github.com/batoulapps/adhan-kotlin/blob/main/adhan/src/commonMain/kotlin/com/batoulapps/adhan2/PrayerTimes.kt
- By contrast, adhan-js has a `PolarCircleResolution` (`AqrabBalad` default, `AqrabYaum`, `Unresolved`), plus an `elevationFromHorizon` parameter. Neither exists in Kotlin. — https://github.com/batoulapps/adhan-js/blob/master/METHODS.md

**Accuracy against published timetables**
- The test suite (`TimingTest.kt`) loads JSON fixtures of official times and asserts each Prayer Time and Sunrise is within the file's `variance` in minutes (default 0). — https://github.com/batoulapps/adhan-kotlin/blob/main/adhan/src/commonTest/kotlin/com/batoulapps/adhan2/TimingTest.kt, https://github.com/batoulapps/adhan-kotlin/blob/main/adhan/src/commonTest/kotlin/com/batoulapps/adhan2/data/TimingFile.kt
- Fixtures in `Shared/Times`, from `batoulapps/adhan-testdata` @ `d418fb3` (2021-06-15). — https://github.com/batoulapps/adhan-kotlin/tree/main/Shared/Times, https://github.com/batoulapps/adhan-testdata/tree/main/Times

  | Fixture | Source cited in file | Tolerance |
  |---|---|---|
  | Makkah, Umm al-Qura (2016) | ummulqura.org.sa ("adjusted +/- 1 minute") | 0 |
  | Doha, Qatar (2016) | islam.gov.qa | 0 |
  | London, Moonsighting Committee (2016) | moonsighting.com | 0 |
  | Dubai (2016) | awqaf.gov.ae | 1 min |
  | Kuwait City (2016) | Kuwait prayer-times app | 2 min |
  | Ankara, Turkey (2019) | yenisafak.com | 2 min |
  | Singapore (2020, every day) | — | 2 min |

- **Gap:** there are **no fixtures for Egyptian, Karachi, Muslim World League or North America**. For an Arabic-first app, Egypt matters, so we should add our own spot-check tests against an official Egyptian timetable. The method angles themselves match the published conventions (see §2).

### 2. PrayTimes.org and its ports (alternative)

- PrayTimes.org (Hamid Zarrabi-Zadeh) is the other common reference implementation. Its methods table lists regions per method. For example, MWL covers "Europe, Far East, parts of US", and Egyptian covers "Africa, Syria, Lebanon, Malaysia". — https://praytimes.org/docs/methods, https://praytimes.org/wiki/Calculation_Methods (seen only as search-result excerpts. praytimes.org is outside this session's network allowlist, so the page wasn't fetched directly. Verify before relying on it.)
- **Licence is the problem:** the maintained ports are LGPL-3.0.
  - Metin Kale's `open-prayer-times` says "`calctimes` [a Kotlin port of PrayTimes.org] is licensed under LGPL-3.0". It supports MWL, ISNA, Egypt, Makkah, Karachi, UOIF, Russia, Malaysia, Singapore, Tehran and Jafari. — https://github.com/metinkale38/open-prayer-times (README)
  - `islamic-network/prayer-times`, the PHP engine behind the AlAdhan API, is a PHP edition of PrayTimes v2.3, licensed LGPL-3.0. Its canonical repo is gone, and Packagist marks it frozen. — https://packagist.org/packages/islamic-network/prayer-times
- LGPL inside an APK is possible, but it brings relinking and notice obligations that MIT doesn't. So we have no reason to prefer it over Adhan, which covers the same methods except Tehran, Jafari, UOIF and Russia.

### 3. Writing it ourselves

- The core sun-position maths (Meeus) is well documented. However, Adhan's value is everything around it:
  - per-method minute offsets (table above)
  - Moonsighting Committee seasonal formulas
  - high-latitude night portions
  - polar-circle and date-line fixes (released in 0.0.7 and PR #94, 2026)
  - a fixture suite against official timetables

  — https://github.com/batoulapps/adhan-kotlin/blob/main/CHANGELOG.md, https://github.com/batoulapps/adhan-kotlin/blob/main/adhan/src/commonMain/kotlin/com/batoulapps/adhan2/PrayerTimes.kt
- Rebuilding all of that adds risk and gives no benefit for v1. If we ever need something Adhan lacks, such as Tehran or polar resolution, we can set custom parameters on `OTHER`, contribute upstream, or port the small piece from adhan-js (MIT).

### 4. Hijri date options

- **Adhan:** none of adhan-kotlin, adhan-js or adhan-swift contains any Hijri code (source search for "hijri"/"hijrah" returns nothing). — https://github.com/batoulapps/adhan-kotlin/tree/main/adhan/src/commonMain
- **`android.icu.util.IslamicCalendar` (API 24+)** offers four calculation types:
  - `ISLAMIC`: "astronomical simulation"
  - `ISLAMIC_CIVIL`: tabular, Friday epoch
  - `ISLAMIC_TBLA`: tabular, Thursday epoch
  - `ISLAMIC_UMALQURA`

  — https://developer.android.com/reference/android/icu/util/IslamicCalendar.CalculationType
  - ICU's own docs say the `ISLAMIC` simulation is "fairly simplistic". They say Umm al-Qura "is also based on the sighting method of the crescent moon but is standardized by Saudi Arabia". — https://github.com/unicode-org/icu/blob/main/icu4j/main/core/src/main/java/com/ibm/icu/util/IslamicCalendar.java
  - ICU's Umm al-Qura data covers `UMALQURA_YEAR_START = 1300` to `UMALQURA_YEAR_END = 1600`. Outside that range it falls back to the civil (arithmetic) algorithm. — same file
- **`java.time.chrono.HijrahChronology` / `HijrahDate` (API 26+)**: `HijrahChronology.INSTANCE` is the "Islamic Umm Al-Qura calendar of Saudi Arabia" (ID `Hijrah-umalqura`, type `islamic-umalqura`). — https://developer.android.com/reference/java/time/chrono/HijrahChronology
  - In OpenJDK, the data file `hijrah-config-Hijrah-umalqura_islamic-umalqura.properties` defines month lengths for AH 1300–1600, with `iso-start=1882-11-12`. — https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/time/chrono/hijrah-config-Hijrah-umalqura_islamic-umalqura.properties
  - Android's libcore is derived from OpenJDK, but we couldn't check its copy of the file from this session.
- **Arithmetic vs Umm al-Qura:** the tabular (civil) calendar is predictable but can differ from the religious/observed calendar "by up to three days" (ICU docs, same file as above). Umm al-Qura is the official Saudi calendar.
- **Is the platform enough?** For v1, yes. `IslamicCalendar(ISLAMIC_UMALQURA)` is available from API 24 with no extra dependency and covers 2026 (about AH 1448) well inside its table. Other Kotlin/Java libraries exist but add nothing for our use:
  - `msarhan/ummalqura-calendar`: MIT, last commit 2021-05-01. — https://github.com/msarhan/ummalqura-calendar
  - `open-prayer-times/hijri`: uses its own observed-date table from AH 1433 and falls back to the platform before that. — https://github.com/metinkale38/open-prayer-times/blob/main/hijri/src/main/kotlin/dev/metinkale/openprayertimes/hijri/HijriDate.kt

### 5. Region → default Calculation Method

- **Adhan** publishes region *hints* in prose but no mapping or API. — https://github.com/batoulapps/adhan-js/blob/master/METHODS.md, https://github.com/batoulapps/adhan-swift/blob/main/METHODS.md
  - Dubai is "used in the UAE".
  - Singapore is "used in Singapore, Malaysia, and Indonesia".
  - Moonsighting Committee is "recommended for North America and the UK".
  - Turkey is "less accurate outside the region of Turkey".
  - North America is "can be used for North America, but the moonsightingCommittee method is preferable".
  - Swift labels its presets "Preset calculation parameters for different regions".
- **The only location-based helper in Adhan is `HighLatitudeRule.recommendedFor(coordinates)`** (latitude > 48°). Nothing picks a Calculation Method. — https://github.com/batoulapps/adhan-kotlin/blob/main/adhan/src/commonMain/kotlin/com/batoulapps/adhan2/HighLatitudeRule.kt
- **PrayTimes.org** has a per-method region column (see §2), which is a human-readable table only.
- **AlAdhan**'s calculation-methods page advises choosing "the method associated with the issuing authority closest to where you are" (seen only as a search excerpt; aladhan.com and its source host 1x.ax are outside this session's network allowlist, and the old GitHub template now returns 404). — https://aladhan.com/calculation-methods
- **Metin Kale's prayer-times-android** (Apache-2.0) mostly sidesteps the question. It offers official per-country sources (Diyanet, IGMG and others) alongside "Calculated (offline) Methods". — https://github.com/metinkale38/prayer-times-android (README)
- **Conclusion:** we found no reusable, licensed, machine-readable country → method table in primary sources. We should write our own small table, keyed by ISO country code. Each row should cite its basis (Adhan's hints, PrayTimes' region column, or the national authority). For example:
  - SA → Umm al-Qura
  - AE → Dubai
  - KW → Kuwait
  - QA → Qatar
  - EG and much of Africa/Levant → Egyptian
  - PK/IN/BD/AF → Karachi
  - TR → Turkey
  - SG/MY/ID → Singapore
  - US/CA/GB → Moonsighting Committee
  - anything else → Muslim World League

  Record the table as an ADR, and let the user override the choice.

### 6. Other points that affect v1

- Adhan returns `Instant`s in UTC. The app converts them to the location's zone. — https://github.com/batoulapps/adhan-kotlin#prayer-times
- The library also gives Qibla direction and `SunnahTimes` (middle and last third of the night) for free. — https://github.com/batoulapps/adhan-kotlin#qibla
- Hijri day boundaries and local sighting: national calendars (e.g. Turkey's Diyanet, Egypt, Morocco) may start months on a different day from Umm al-Qura. The ICU docs explain why observation-based calendars can't be computed in advance. That is the reason for the manual Hijri offset setting.

## Open questions for the human

1. **Minimum SDK:** use API 26, or enable `coreLibraryDesugaring`? `kotlinx-datetime`, which Adhan depends on, needs one of the two. API 26 also unlocks `java.time.HijrahDate`. We didn't verify whether desugaring includes `HijrahChronology`. ICU's `IslamicCalendar` works from API 24 either way.
2. **Default Calculation Method:** where should the country come from, offline? The options are device locale/region, a bundled reverse-geocoding boundary file, or a choice at onboarding. And do you approve writing our own country → method table as an ADR?
3. **Asr Convention default:** Standard everywhere, or Hanafi by default for regions such as South Asia and Turkey?
4. **Hijri:** use Umm al-Qura as the default with a ±N day user offset, or also offer the tabular/civil calendar?
5. **Polar and high latitude:** what should the UI show when Adhan throws (latitudes beyond about 66°)? Should we port adhan-js's `PolarCircleResolution` (`AqrabBalad` / `AqrabYaum`)?
6. **Methods missing from adhan-kotlin** (Tehran, Jafari, UOIF/France, Russia): needed for v1, or out of scope?
7. **Ramadan Isha +30 min for Umm al-Qura:** apply it automatically while the Hijri month is Ramadan?

## Sources

- Adhan parent repo: https://github.com/batoulapps/Adhan
- adhan-kotlin (README, CHANGELOG, LICENSE, source, tests, tags): https://github.com/batoulapps/adhan-kotlin
- adhan-kotlin `java` branch (adhan-java): https://github.com/batoulapps/adhan-kotlin/tree/java (also https://github.com/batoulapps/adhan-java)
- Maven Central `adhan2` metadata: https://repo1.maven.org/maven2/com/batoulapps/adhan/adhan2/maven-metadata.xml
- adhan-testdata: https://github.com/batoulapps/adhan-testdata
- adhan-js (METHODS.md, README, source): https://github.com/batoulapps/adhan-js
- adhan-swift (METHODS.md, source): https://github.com/batoulapps/adhan-swift
- Android `IslamicCalendar.CalculationType`: https://developer.android.com/reference/android/icu/util/IslamicCalendar.CalculationType
- Android `HijrahChronology`: https://developer.android.com/reference/java/time/chrono/HijrahChronology
- ICU4J `IslamicCalendar.java`: https://github.com/unicode-org/icu/blob/main/icu4j/main/core/src/main/java/com/ibm/icu/util/IslamicCalendar.java
- OpenJDK Umm al-Qura data: https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/time/chrono/hijrah-config-Hijrah-umalqura_islamic-umalqura.properties
- open-prayer-times (PrayTimes Kotlin port, LGPL-3.0): https://github.com/metinkale38/open-prayer-times
- prayer-times-android: https://github.com/metinkale38/prayer-times-android
- islamic-network/prayer-times on Packagist: https://packagist.org/packages/islamic-network/prayer-times
- msarhan/ummalqura-calendar: https://github.com/msarhan/ummalqura-calendar
- PrayTimes.org methods (not fetched directly): https://praytimes.org/docs/methods
- AlAdhan calculation methods (not fetched directly): https://aladhan.com/calculation-methods
