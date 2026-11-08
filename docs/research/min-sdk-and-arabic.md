# minSdk for v1, and Arabic-first (RTL, digits, Hijri, fonts) on Android

Research for ticket #6. Researched 2026-11-08. Terms follow [`GLOSSARY.md`](../../GLOSSARY.md).

## Question

1. What `minSdk` should v1 target? Weigh Android version distribution (global, plus Egypt, Saudi Arabia, UAE, Morocco, Algeria and Iraq) against the API levels the app needs: exact alarms for **Adhan Alerts** and **Prayer Reminders**, the notification permission, Glance for **Widget Variants**, and per-app language. Also check the current Compose and Glance minimum SDK, and the Play Store `targetSdk` rule for late 2026.
2. Compose RTL: layout mirroring, `LocalLayoutDirection`, which icons mirror, and bidi text that mixes Arabic, Latin and numbers.
3. Digits: Eastern Arabic (٠١٢٣) vs Western (0123). Defaults for `ar-EG`, `ar-SA`, `ar-MA` and similar locales, the `-u-nu-` extension, and a user setting.
4. Hijri dates with `java.time` `HijrahChronology` (Umm al-Qura) and `android.icu` `IslamicCalendar`. API levels and desugaring.
5. Per-app language on Android 13+ and the AppCompat backport.
6. Arabic fonts we can bundle: licences, sizes, and whether variable fonts work in Compose and in Glance/RemoteViews.

## Short answer / recommendation

- **Set `minSdk = 26` (Android 8.0).** Set `targetSdk = 36` and `compileSdk = 37`.
  - Compose and Glance now require at least API 23, so 23 is the floor in any case.
  - API 26 adds three things we use directly: native `java.time`, including `HijrahChronology` (Umm al-Qura); variable fonts in Compose; and notification channels on every supported device.
  - Google's December 2025 data puts Android 8.0 and newer at about 97.6% of devices.
  - Going from 26 down to 24 gains about 0.8% (Android 7.x), and down to 23 only a little more. That is not worth the fallback code it would need.
- **Play rule:** since 31 Aug 2026, new apps and updates must target API 36 (Android 16). Compose 1.12 requires `compileSdk` 37 and AGP 9.2.0 or newer.
- **Alerts:** declare `USE_EXACT_ALARM`, but only if Play accepts a prayer-time app under its "alarm clock or calendar" policy (open question 1). Otherwise use `SCHEDULE_EXACT_ALARM`, check `canScheduleExactAlarms()`, and fall back to `setWindow()` when it is not granted. Request `POST_NOTIFICATIONS` at runtime in context, for example when the user turns on an Adhan Alert.
- **RTL:** set `android:supportsRtl="true"`. Use only start/end APIs in Compose. Use `Icons.AutoMirrored.*` for directional icons, and leave non-directional ones (clock faces, the Qibla compass, check marks, logos, numerals) unmirrored. Wrap every runtime-inserted string in `BidiFormatter.unicodeWrap()` or Unicode isolates (FSI…PDI).
- **Digits:** follow the locale by default, using `DecimalStyle.of(locale)` with `java.time`. Offer a setting, "Digits: Automatic / Arabic-Indic ٠١٢ / Western 012", that adds `-u-nu-arab` or `-u-nu-latn` to the formatting locale. Never use this formatting locale for storage, logs or SQL.
- **Hijri:** with minSdk 26, use `HijrahChronology.INSTANCE` (Umm al-Qura) and `HijrahDate` from `java.time` directly, without desugaring. Add a user offset of ±1 or 2 days, because local moon-sighting can differ from Umm al-Qura (open question 3).
- **Language:** turn on `generateLocaleConfig` with `unqualifiedResLocale=ar` in `resources.properties`. Use `AppCompatDelegate.setApplicationLocales()` with `autoStoreLocales`, and host the app in an `AppCompatActivity` so the in-app picker works on API 26–32.
- **Fonts:** bundle one OFL Arabic UI family in the app. **Noto Naskh Arabic** (variable, 300 KB) and **Noto Kufi Arabic** (variable, 424 KB) are the safest picks: OFL, no Reserved Font Name, and wide glyph coverage. **Glance cannot use bundled fonts** (`FontFamily(String)` only resolves *system* families), so widget text falls back to the system Arabic font unless we render text to a bitmap (open question 5).

## Findings

### 1. minSdk

#### 1.1 Library floors

| Library | Current stable (as of 2026-11-08) | minSdk | Source |
|---|---|---|---|
| Compose UI | 1.12.1 (2026-09-09); 1.13.0-rc01 (2026-11-04) | **23** since 1.10.0-alpha01 (2025-08-13): "Moving the default minSdk from API 21 to API 23" | [compose-ui releases](https://developer.android.com/jetpack/androidx/releases/compose-ui) |
| Compose runtime / foundation | same release train | **23** (same change, Ibdfca) | [compose-runtime](https://developer.android.com/jetpack/androidx/releases/compose-runtime), [compose-foundation](https://developer.android.com/jetpack/androidx/releases/compose-foundation) |
| Glance | 1.2.0 (2026-08-26); 1.3.0-alpha02 | **23** since 1.2.0-beta01 (2025-08-27): "Moving the default `minSdk` from API 21 to API 23" | [glance releases](https://developer.android.com/jetpack/androidx/releases/glance) |
| AppCompat | 1.8.0 (2026-08-12) | **23** since 1.8.0-alpha01 | [appcompat releases](https://developer.android.com/jetpack/androidx/releases/appcompat) |

Compose 1.12.0 also states: "Updated Compose `compileSdk` to API 37. This means that a minimum AGP version of 9.2.0 is required when using Compose." In 1.13.0-alpha03 it moves to compileSdk 37.1 ([compose-ui releases](https://developer.android.com/jetpack/androidx/releases/compose-ui)).

#### 1.2 Play Store target API (late 2026)

"Starting August 31 2026: New apps and app updates must target Android 16 (API level 36) or higher… Existing apps must target Android 15 (API level 35) or higher to remain available to new users." An extension to 1 Nov 2026 was available. ([target-sdk](https://developer.android.com/google/play/requirements/target-sdk))

#### 1.3 Platform features we need, by API level

| Feature | API | Effect on minSdk | Source |
|---|---|---|---|
| Doze; exact alarms via `setExactAndAllowWhileIdle` / `setAlarmClock` | 23 | at or below the floor | [alarms](https://developer.android.com/develop/background-work/services/alarms) |
| `android.icu` (`IslamicCalendar`, ICU4J subset) | 24 | below 26 | [i18n](https://developer.android.com/guide/topics/resources/internationalization), [IslamicCalendar](https://developer.android.com/reference/android/icu/util/IslamicCalendar) |
| `java.time` incl. `HijrahChronology` / `HijrahDate` | 26 ("Added in API level 26") | **reason to pick 26** | [HijrahChronology](https://developer.android.com/reference/java/time/chrono/HijrahChronology) |
| Variable fonts in Compose | 26 ("Variable fonts are only supported on Android O and above") | **reason to pick 26** | [Compose fonts](https://developer.android.com/develop/ui/compose/text/fonts) |
| Notification channels (required on 26+) | 26 | simplifies code | [target-sdk §Android 8](https://developer.android.com/google/play/requirements/target-sdk) |
| `SCHEDULE_EXACT_ALARM` special access | 31 | runtime check | [alarms](https://developer.android.com/develop/background-work/services/alarms) |
| `POST_NOTIFICATIONS` runtime permission; `USE_EXACT_ALARM`; `LocaleManager` per-app language | 33 | runtime check / backport | [notification permission](https://developer.android.com/develop/ui/views/notifications/notification-permission), [app languages](https://developer.android.com/guide/topics/resources/app-languages) |
| `SCHEDULE_EXACT_ALARM` denied by default for new installs; Regional preferences (numbering system) | 34 | runtime check | [Android 14 exact alarms](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms), [Android 14 features](https://developer.android.com/about/versions/14/features) |

**Exact alarms.**
- On Android 14+, `SCHEDULE_EXACT_ALARM` "is no longer being pre-granted to most newly installed apps targeting Android 13 and higher (will be set to denied by default)". It is required by `setExact()`, `setExactAndAllowWhileIdle()` and `setAlarmClock()`.
- "Calendar or alarm clock apps… can request the `USE_EXACT_ALARM` normal permission… granted on install". However, "Apps will not be able to publish a version of their app with this permission in the manifest unless they qualify based on the policy language."
- Without the permission, the documented fallback is `setWindow()` (a 10-minute minimum window) or `setAndAllowWhileIdle()`.
- Sources: [Android 14 exact alarms](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms), [alarms](https://developer.android.com/develop/background-work/services/alarms).

**Notifications.**
- On Android 13+, a new install has "notifications are off by default".
- If the app targets 33+, "your app has complete control over when the permission dialog is displayed". Check `areNotificationsEnabled()` before posting.
- Source: [notification permission](https://developer.android.com/develop/ui/views/notifications/notification-permission).

#### 1.4 Distribution data

> Caveat: `gs.statcounter.com` and `dl.google.com` (the Android Studio distribution JSON) were **blocked by this session's network policy**. The numbers below come from search-engine snippets of those pages and from news reports that reproduce Google's data, not from fetching the primary pages. Re-verify them once those hosts are allowed (open question 6).

**Global (Google / Android Studio data, 1 Dec 2025, published around 30 Jan 2026):**
- Android 16 is at 7.5% and Android 15 at 19.3% (26.8% cumulative for 35+).
- Android 8.0/8.1 (API 26–27) and newer is at **97.6% cumulative**.
- Android 8.0 is 0.8%, 8.1 is 2.3%, and 7.0 and 7.1 are 0.4% each.
- Sources: [heise, 2026-01](https://www.heise.de/en/news/New-numbers-on-version-distribution-Android-16-with-7-5-percent-market-share-11162154.html), [9to5Google, 2026-01-30](https://9to5google.com/2026/01/30/android-16-is-on-7-5-of-devices-in-latest-distribution-numbers-update/).
- heise reports that Android Studio now shows only cumulative values, with Android 16 at 25.4% in October 2026 ([heise, 2026-10](https://www.heise.de/en/news/Android-version-distribution-in-October-2026-Android-16-leads-11504842.html)). I could not open that article directly.

**StatCounter, key markets (2026). Top versions shown only; Android 9 and 10 did not appear in the top six for any of these markets:**

| Market | Month | Leading versions | Source |
|---|---|---|---|
| Egypt (all) | Jun 2026 | 15: 15.7%, 13: 15.1%, 11: 13.9%, 16: 13.7% (16 leads by Jul: about 18%) | [StatCounter EG](https://gs.statcounter.com/android-version-market-share/all/egypt) |
| Saudi Arabia (all) | Aug 2026 | 16: 28.6%, 15: 20.5%, 13: 15.7%, 14: 13.7%, 12: 8.5%, 11: 6.5% | [StatCounter SA](https://gs.statcounter.com/android-version-market-share/mobile-tablet/saudi-arabia) |
| Morocco (all) | Aug 2026 | 13: 18.2%, 14: 15.2%, 16: 15.2%, 11: 10.6%, 12: 10.5% | [StatCounter MA](https://gs.statcounter.com/os-version-market-share/android/mobile-tablet/morocco) |
| Algeria (mobile+tablet) | Jun 2026 | 13: 16.3%, 14: 14.4%, 15: 13.7%, 11: 12.7% | [StatCounter DZ](https://gs.statcounter.com/android-version-market-share/mobile-tablet/algeria) |
| UAE, Iraq | — | no data retrieved | — |

**Reading:**
- In every market we have data for, Android 11+ dominates.
- North Africa (Morocco, Algeria, and Egypt in part) has a larger long tail of Android 11–13 than the Gulf. Even so, nothing surfaced suggests a meaningful share below Android 8.
- StatCounter measures web traffic, not installs, so it skews toward newer, more-used devices.

**Why not 23 or 24?**
- 23 is the absolute floor, set by Compose, Glance and AppCompat.
- Going below 26 would force core library desugaring for `java.time` (which supports `HijrahChronology`; see §4) and static-font fallbacks.
- It would gain roughly 1% of devices globally: Android 7.x at 0.8%, plus a sliver of 6.0.

### 2. Compose RTL

- **Manifest:** `android:supportsRtl="true"`. "Home-screen widgets support the RTL text direction as long as their corresponding manifest files include the attribute assignment `android:supportsRtl="true"`." ([languages guide](https://developer.android.com/training/basics/supporting-devices/languages))
- **Layout:**
  - Compose resolves `Arrangement.Start/End`, `Alignment.Start/End`, `Modifier.padding(start=, end=)` and `Row` order against `LocalLayoutDirection`, which comes from the configuration.
  - Use `absolutePadding` / `Arrangement.Absolute*` only where something must not mirror.
  - To force LTR for one subtree (for example a clock dial or a numeric time row), wrap it in `CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr)`.
  - The Compose layouts page does not document this pattern ([layouts basics](https://developer.android.com/develop/ui/compose/layouts/basics)). It is the standard `CompositionLocal` mechanism; verify it in a prototype.
- **Icons:**
  - `Icons.AutoMirrored` "are a subset of Icons that will automatically mirror themselves when displayed in an RTL layout" (added in material-icons-core 1.6.0). Example: `Icons.AutoMirrored.Outlined.ArrowForward` ([Icons.AutoMirrored](https://developer.android.com/reference/kotlin/androidx/compose/material/icons/Icons.AutoMirrored)).
  - For our own vectors, use `ImageVector.Builder(autoMirror = true)` ([ImageVector.Builder](https://developer.android.com/reference/kotlin/androidx/compose/ui/graphics/vector/ImageVector.Builder)).
  - For XML drawables, use `android:autoMirrored="true"`, which "only works for simple drawables whose bidirectional mirroring is simply a graphical mirroring of the entire drawable" ([languages guide](https://developer.android.com/training/basics/supporting-devices/languages)).
- **What not to mirror:**
  - The Android guide defers to Material's Bidirectionality page for the list ([m2.material.io bidirectionality](https://m2.material.io/design/usability/bidirectionality.html)).
  - The Material icon guidance ([Material icons: which icons should be mirrored](https://developers.google.com/fonts/docs/material_icons#which_icons_should_be_mirrored_for_rtl)) is the other reference.
  - *Both hosts were blocked in this session, so the list below is unverified working guidance.* Mirror back/forward arrows, "send", list/indent icons and progress direction. Do **not** mirror clocks or circular-progress direction, media play/skip, check marks, the Qibla compass and needle, logos, or numerals.
- **Bidi text:**
  - The Android guide says to use `BidiFormatter.unicodeWrap()` "on every piece of text that you insert into a localized message", except machine-readable strings ([languages guide](https://developer.android.com/training/basics/supporting-devices/languages)).
  - `unicodeWrap` "If isolate, directionally isolates the string so that it does not garble its surroundings" ([androidx BidiFormatter](https://developer.android.com/reference/androidx/core/text/BidiFormatter)). The framework `android.text.BidiFormatter` is API 18+ ([BidiFormatter](https://developer.android.com/reference/android/text/BidiFormatter)).
  - Relevant cases for us: a city or **Calculation Method** name in Latin script inside an Arabic sentence; "Fajr 05:07" labels; "+3 min" offsets; Latin-script place names.
- **Unicode controls:**
  - Isolates: LRI U+2066, RLI U+2067, FSI U+2068, PDI U+2069.
  - Marks: LRM U+200E, RLM U+200F, ALM U+061C.
  - These code points and bidi classes were checked against the Unicode Character Database 15.1 via Python `unicodedata`. The spec is [UAX #9](https://www.unicode.org/reports/tr9/), which was not fetched here because the host was blocked.
  - Arabic-Indic digits U+0660–0669 have bidi class **AN**. Extended Arabic-Indic digits U+06F0–06F9 (Persian/Urdu) are **EN**. So `٠١٢` and `۰۱۲` reorder differently next to punctuation. Use U+0660 for Arabic.

### 3. Digits

**CLDR defaults** (`defaultNumberingSystem`, CLDR 49.0.0, `cldr-json` commit `7d2b9cb`, files `cldr-numbers-full/main/ar-*/numbers.json`, [unicode-org/cldr-json](https://github.com/unicode-org/cldr-json)):

| Default `latn` (0123) | Default `arab` (٠١٢٣) |
|---|---|
| `ar` (root), `ar-AE`, `ar-DZ`, `ar-EH`, `ar-LY`, `ar-MA`, `ar-TN` | `ar-EG`, `ar-SA`, `ar-IQ`, `ar-JO`, `ar-KW`, `ar-LB`, `ar-OM`, `ar-QA`, `ar-BH`, `ar-SY`, `ar-YE`, `ar-SD`, `ar-PS`, and others |

The `native` numbering system is `arab` for every `ar-*` locale.

**Defaults differ by CLDR version, so they depend on the Android release.**
- Android bundles a fixed ICU/CLDR per release: API 26 has CLDR 30.0.3, API 33 has CLDR 40, API 34 has CLDR 42 and API 35 has CLDR 45 ([i18n table](https://developer.android.com/guide/topics/resources/internationalization)).
- Example: on JDK 21 (CLDR 42-era data), bare `ar` produced ٠١٢ while CLDR 49 says `latn` for bare `ar`. I tested this locally on OpenJDK 21.0.12. With regional locales, `ar-EG`, `ar-SA` and `ar-IQ` gave `١٤٤٧` and `ar-AE`, `ar-MA` and `ar-DZ` gave `1447`.
- **Do not rely on bare `ar` behaving the same across devices.**

**Unicode extension:**
- `ar-EG-u-nu-latn` forces Western digits and `ar-MA-u-nu-arab` forces Arabic-Indic digits. Both were verified on JDK 21: `ar-EG-u-nu-latn` gave `1447`, `05:07`.
- `Locale.forLanguageTag` accepts the extension. `java.text.NumberFormat` and `String.format(locale, …)` honour it.

**Pitfall: `java.time` ignores locale digits by default.**
- `DateTimeFormatter.ofPattern("HH:mm", ar-EG)` gave `05:07`. Adding `.withDecimalStyle(DecimalStyle.of(locale))` gave `٠٥:٠٧` (tested locally).
- So each **Prayer Time** formatter must set `DecimalStyle` explicitly.

**Machine strings:** the Android guide warns that `String.format` with an Arabic locale breaks SQL. Use `Locale.US` (or `Locale.ROOT`) for machine strings ([languages guide](https://developer.android.com/training/basics/supporting-devices/languages)).

**User setting:**
- Android 14 Regional preferences let users choose "temperature units, the first day of the week, and numbering systems" at the system level. They are surfaced as locale extensions, and `androidx.core.text.util.LocalePreferences` exposes some of them ([Android 14 features](https://developer.android.com/about/versions/14/features)).
- Recommended in-app setting: **Automatic** (respect the locale and its `-u-nu-` extension, including the Android 14 setting), **Arabic-Indic**, or **Western**.
- Implement it by building a formatting `Locale` with `Locale.Builder().setLocale(appLocale).setUnicodeLocaleKeyword("nu", "arab"|"latn")`, and use it only for display.
- This affects Compose screens, widgets and notification text alike, so it belongs in one shared formatter module.

### 4. Hijri dates

**`java.time`:**
- `HijrahChronology` and `HijrahDate` were added in API 26. `INSTANCE` is the "Singleton instance of the Islamic Umm Al-Qura calendar of Saudi Arabia" (ID `Hijrah-umalqura`, calendar type `islamic-umalqura`, locale extension `ca-islamic-umalqura`) ([HijrahChronology](https://developer.android.com/reference/java/time/chrono/HijrahChronology)).
- It is sighting-based: "For sighting based calendars only historical data from past sightings is available" (same page).
- On JDK 21 the supported year range is **1300–1600 AH**, which I checked locally with `range(YEAR)`. Verify it on device.

**Local check (JDK 21):**
- 2026-11-08 maps to `Hijrah-umalqura AH 1448-05-28`.
- In `ar-EG` with `DecimalStyle` applied, it formats as `٢٨ جمادى الأولى ١٤٤٨ هـ`.
- Without `DecimalStyle` it gives `28 جمادى الأولى 1448 هـ`.

**Desugaring:**
- The desugaring table lists `java.time.chrono.HijrahChronology` and `HijrahDate` as "Fully implemented class" ([Java 8+ desugaring table](https://developer.android.com/studio/write/java8-support-table)).
- That would work at minSdk 23–25, via `coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.3")` and AGP 7.4+ ([Java 8 support](https://developer.android.com/studio/write/java8-support)).
- With minSdk 26, desugaring is not needed for `java.time`.

**`android.icu.util.IslamicCalendar` (API 24):**
- It supports civil, religious and Umm al-Qura variants. "IslamicCalendar usually should be instantiated using Calendar.getInstance(ULocale) passing in a ULocale with the tag "@calendar=islamic" or "@calendar=islamic-civil" or "@calendar=islamic-umalqura"" ([IslamicCalendar](https://developer.android.com/reference/android/icu/util/IslamicCalendar)).
- Use it if we need ICU skeleton formatting (`DateFormat.getInstanceForSkeleton`) or variants other than Umm al-Qura.
- Its data comes from the device ICU version (table in §3), so results can differ between Android versions.

**Recommendation:** store dates as ISO `LocalDate`. Convert to Hijri only for display, using `HijrahChronology.INSTANCE` plus a user day-offset.

### 5. Per-app language

**Android 13+:**
- The system Settings > Apps > (app) > Language entry comes from `LocaleConfig`.
- `generateLocaleConfig = true` in `androidResources {}` works with AGP 8.1+ and compileSdk 33+. It needs `res/resources.properties` with `unqualifiedResLocale=…`, which for us is `unqualifiedResLocale=ar`, because Arabic is in `values/`.
- "the build fails if you do manually create a LocaleConfig file" when generation is on.
- Source: [app languages](https://developer.android.com/guide/topics/resources/app-languages).

**Backport to API 26–32:**
- Use `AppCompatDelegate.setApplicationLocales(LocaleListCompat)`, from AppCompat 1.6.0+.
- "If you're using Compose with `setApplicationLocales`, you must extend your activity from `AppCompatActivity`. Otherwise, setting the app locale won't work."
- For API ≤32 storage, declare `AppLocalesMetadataHolderService` with `autoStoreLocales=true`. Note: "causes a blocking read on the main thread".
- On API 33+ it delegates to the platform `LocaleManager`.
- Sources: [app languages](https://developer.android.com/guide/topics/resources/app-languages), [appcompat releases](https://developer.android.com/jetpack/androidx/releases/appcompat).

**Android 14+:** `LocaleManager.setOverrideLocaleConfig()` can change the supported-language list at runtime ([Android 14 features](https://developer.android.com/about/versions/14/features)).

**Widgets:** Glance renders with the app's resources and configuration. Check in a prototype that a per-app locale change updates **Widget Variants**, which may need a `GlanceAppWidget.updateAll()` on `ACTION_LOCALE_CHANGED`.

### 6. Arabic fonts

All six candidates are **SIL Open Font License 1.1**. I checked the `OFL.txt` in `google/fonts` at commit `5bb2873` ([google/fonts](https://github.com/google/fonts)). Sizes are the TTFs as shipped there. I also checked digit glyph coverage with fontTools.

| Family | Style | Files (size) | Variable axes | ٠–٩ / ۰–۹ | Upstream | Licence notes |
|---|---|---|---|---|---|---|
| Noto Naskh Arabic | Naskh (text) | `NotoNaskhArabic[wght].ttf` 307,592 B | wght 400–700 | yes / yes | [notofonts/arabic](https://github.com/notofonts/arabic) | OFL, no RFN |
| Noto Kufi Arabic | Kufi (UI/display) | `NotoKufiArabic[wght].ttf` 434,204 B | wght 100–900 | yes / yes | [notofonts/arabic](https://github.com/notofonts/arabic) | OFL, no RFN |
| Noto Sans Arabic | Sans UI | `NotoSansArabic[wdth,wght].ttf` 844,676 B | wdth 62.5–100, wght 100–900 | yes / yes | [notofonts/arabic](https://github.com/notofonts/arabic) | OFL, no RFN |
| IBM Plex Sans Arabic | Sans UI | 7 static TTFs, about 236–247 KB each (≈1.69 MB all) | none | yes / yes | [googlefonts/plex](https://github.com/googlefonts/plex) | OFL **with Reserved Font Name "Plex"**: a modified or subset build cannot be called "Plex" |
| Cairo | Sans UI | `Cairo[slnt,wght].ttf` 599,548 B | slnt −11…11, wght 200–1000 | yes / yes | [Gue3bara/Cairo](https://github.com/Gue3bara/Cairo) | OFL |
| Tajawal | Sans UI | 7 static TTFs, about 56–62 KB each (≈420 KB all) | none | yes / **no** | [googlefonts/tajawal](https://github.com/googlefonts/tajawal) | OFL; only 465 glyphs |
| Amiri | Classical Naskh (Qur'anic/book) | 4 static TTFs, about 410–431 KB each | none | yes / yes | [aliftype/amiri](https://github.com/aliftype/amiri) | OFL |

**OFL obligations:** ship `OFL.txt` with the app, for example on an in-app licences screen. Do not sell the font by itself. Reserved Font Names, where present (Plex), restrict renaming of modified builds. This follows the `OFL.txt` text in each folder.

**Compose:**
- Put the variable TTF in `res/font/` and use `Font(R.font.x, variationSettings = FontVariation.Settings(FontVariation.weight(…)))`.
- "Variable fonts are only supported on Android O and above" ([Compose fonts](https://developer.android.com/develop/ui/compose/text/fonts)). minSdk 26 removes the need for a static fallback.
- The same page says variable fonts are "not currently supported via downloadable fonts". Compose 1.12.0 release notes, however, add downloadable variable fonts in `ui-text-google-fonts` ([compose-ui releases](https://developer.android.com/jetpack/androidx/releases/compose-ui)). We work offline, so we bundle the fonts anyway.

**Glance / RemoteViews:**
- `androidx.glance.text.FontFamily` has `Serif`, `SansSerif`, `Monospace` and `Cursive`, plus `FontFamily(family: String)`: "If this is found on the system it will be used, otherwise it will fallback to a system default" ([Glance FontFamily](https://developer.android.com/reference/kotlin/androidx/glance/text/FontFamily)).
- So a font bundled in the app **cannot be selected by name in a Glance widget**. Widget text will render in the device's system Arabic font.
- Workarounds, none verified here:
  - (a) Render key strings (the next **Prayer Time** countdown) to a `Bitmap` and show it with `Image`.
  - (b) Use `AndroidRemoteViews` with an XML `TextView` that sets `android:fontFamily="@font/…"`.

## Open questions for the human

1. **USE_EXACT_ALARM eligibility.** Does Play accept a prayer-time app as an "alarm clock" app? The policy text is at [support.google.com](https://support.google.com/googleplay/android-developer/answer/12253906#exact_alarm_preview), which was not fetched. If not, we rely on `SCHEDULE_EXACT_ALARM` with the user-grant flow, and accept that Adhan Alerts may arrive within a window when it is denied.
2. **Confirm minSdk 26.** Is roughly 1% of devices (Android 6–7) worth giving up for simpler code (native `java.time`, variable fonts)? I recommend yes.
3. **Hijri accuracy.** Is Umm al-Qura plus a manual ±day offset enough for v1? Or do users in Egypt and the Maghreb need a country-default offset or a different calculation (`islamic-civil`)?
4. **Digits default.** Should "Automatic" follow the device locale (so `ar-MA` shows 0123 and `ar-EG` shows ٠١٢٣), or should Arabic always default to ٠١٢٣? Note that bare `ar` behaves differently across CLDR versions.
5. **Widget typography.** Is the system Arabic font acceptable in widgets, or should we prototype bitmap-rendered text for the main Widget Variant?
6. **Network access for re-verification.** The following hosts were blocked in this session: `gs.statcounter.com`, `dl.google.com`, `m2.material.io`, `developers.google.com` and `www.unicode.org`. Allow them to re-verify the distribution figures and the icon-mirroring list from primary pages. There is also no UAE or Iraq data yet.
7. **Font choice.** Pick Naskh (Noto Naskh Arabic) or Kufi/Sans (Noto Kufi / Cairo) as the UI face. Amiri only for any Qur'anic or ornamental text?

## Sources

- Compose UI releases: https://developer.android.com/jetpack/androidx/releases/compose-ui
- Compose runtime releases: https://developer.android.com/jetpack/androidx/releases/compose-runtime
- Compose foundation releases: https://developer.android.com/jetpack/androidx/releases/compose-foundation
- Glance releases: https://developer.android.com/jetpack/androidx/releases/glance
- AppCompat releases: https://developer.android.com/jetpack/androidx/releases/appcompat
- Play target API requirement: https://developer.android.com/google/play/requirements/target-sdk
- Schedule alarms: https://developer.android.com/develop/background-work/services/alarms
- Android 14 exact-alarm change: https://developer.android.com/about/versions/14/changes/schedule-exact-alarms
- Android 14 features (regional preferences, per-app language): https://developer.android.com/about/versions/14/features
- Notification runtime permission: https://developer.android.com/develop/ui/views/notifications/notification-permission
- Per-app language preferences: https://developer.android.com/guide/topics/resources/app-languages
- Support different languages and cultures (RTL, BidiFormatter, number formatting): https://developer.android.com/training/basics/supporting-devices/languages
- Unicode and internationalization (ICU/CLDR per API level): https://developer.android.com/guide/topics/resources/internationalization
- HijrahChronology: https://developer.android.com/reference/java/time/chrono/HijrahChronology
- HijrahDate: https://developer.android.com/reference/java/time/chrono/HijrahDate
- IslamicCalendar: https://developer.android.com/reference/android/icu/util/IslamicCalendar
- Java 8+ API desugaring: https://developer.android.com/studio/write/java8-support
- Desugaring API table: https://developer.android.com/studio/write/java8-support-table
- BidiFormatter (framework): https://developer.android.com/reference/android/text/BidiFormatter
- BidiFormatter (androidx): https://developer.android.com/reference/androidx/core/text/BidiFormatter
- Icons.AutoMirrored: https://developer.android.com/reference/kotlin/androidx/compose/material/icons/Icons.AutoMirrored
- ImageVector.Builder: https://developer.android.com/reference/kotlin/androidx/compose/ui/graphics/vector/ImageVector.Builder
- Compose layouts basics: https://developer.android.com/develop/ui/compose/layouts/basics
- Compose fonts (variable fonts): https://developer.android.com/develop/ui/compose/text/fonts
- Glance FontFamily: https://developer.android.com/reference/kotlin/androidx/glance/text/FontFamily
- CLDR JSON (v49.0.0): https://github.com/unicode-org/cldr-json
- Google Fonts repository (OFL texts, METADATA, font files): https://github.com/google/fonts
- Noto Arabic upstream: https://github.com/notofonts/arabic
- IBM Plex upstream: https://github.com/googlefonts/plex
- Cairo upstream: https://github.com/Gue3bara/Cairo
- Tajawal upstream: https://github.com/googlefonts/tajawal
- Amiri upstream: https://github.com/aliftype/amiri
- Secondary reports of Google distribution data: https://www.heise.de/en/news/New-numbers-on-version-distribution-Android-16-with-7-5-percent-market-share-11162154.html , https://9to5google.com/2026/01/30/android-16-is-on-7-5-of-devices-in-latest-distribution-numbers-update/ , https://www.heise.de/en/news/Android-version-distribution-in-October-2026-Android-16-leads-11504842.html
- StatCounter (snippets only, host blocked): https://gs.statcounter.com/android-version-market-share/all/egypt , https://gs.statcounter.com/android-version-market-share/mobile-tablet/saudi-arabia , https://gs.statcounter.com/os-version-market-share/android/mobile-tablet/morocco , https://gs.statcounter.com/android-version-market-share/mobile-tablet/algeria
- Not reachable from this session (cited as the owning source): https://m2.material.io/design/usability/bidirectionality.html , https://developers.google.com/fonts/docs/material_icons#which_icons_should_be_mirrored_for_rtl , https://www.unicode.org/reports/tr9/ , https://support.google.com/googleplay/android-developer/answer/12253906#exact_alarm_preview
