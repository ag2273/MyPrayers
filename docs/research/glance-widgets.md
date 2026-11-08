# Research: Jetpack Glance constraints for Widget Variants

Ticket: #4. Researched 2026-11-08 against developer.android.com, AndroidX release notes and AndroidX/AOSP source. Terms follow [`GLOSSARY.md`](../../GLOSSARY.md).

## Question

What platform and library constraints shape the 4–5 **Widget Variants** we plan? In particular:

- how to show a live countdown to the next **Prayer Time** under widget update limits
- interactive actions, e.g. marking a **Prayer** as prayed in the **Prayer Log** from the widget
- size classes and responsive layouts
- RTL and Arabic font rendering
- theming (Material You, dark mode)
- the widget picker preview
- battery cost and update quotas
- minimum SDK per feature, and the current stable Glance version

## Short answer / recommendation

1. **Use Glance 1.2.0** (stable since 2026-08-26). Its default `minSdk` is 23. Treat **API 31 (Android 12)** as the real baseline for good widgets: responsive sizes, native compound buttons, dynamic colour, `targetCellWidth/Height`, `previewLayout` and system corner radius all start there. A `minSdk` of 26 or 31 is a product decision (see open questions).
2. **For the live countdown, use a platform `Chronometer` in count-down mode, embedded through `AndroidRemoteViews`.** Do not push an update every minute. The launcher process ticks the `Chronometer`, so the app wakes only at Prayer Time boundaries: up to six times a day (five Prayer Times plus Sunrise if a variant shows it), plus time, timezone and locale changes. The cost is that `Chronometer` always shows seconds (`MM:SS` / `H:MM:SS`), and once it passes its base it counts up with a minus sign. For a minutes-only look ("in 1 h 20 min"), re-render text through scheduled updates and accept that it can lag; a ≥15-minute WorkManager period is far too coarse for a minute counter.
3. **Wake up at each Prayer Time with exact alarms that are already needed for Adhan Alerts.** One alarm schedule then drives both the alert and the widget refresh. Set `updatePeriodMillis` to `0`, or to a long safety-net value (it cannot be under 30 minutes).
4. **Mark a Prayer as prayed with a Glance `CheckBox`, or with a `clickable` + `actionRunCallback`, that writes to the app's Prayer Log store and then calls `update`/`updateAll`.** The Prayer Log should live in the app's own database, not in Glance per-widget state, so every Widget Variant and the app stay consistent.
5. **Use `SizeMode.Responsive` with 2–4 breakpoints per variant.** Each Widget Variant should be its own `GlanceAppWidget` + receiver + `appwidget-provider` XML, because the picker lists each one separately.
6. **Fonts: only system font families work.** Glance and `RemoteViews` cannot load an app-bundled font. Arabic text will render in the device's system Arabic font. A custom Arabic typeface would mean drawing text into bitmaps, which hurts accessibility, RTL, dynamic colour and the Android 17 bitmap-memory budget. Recommendation: accept system fonts.
7. **Theming: wrap content in `GlanceTheme`**, which gives wallpaper-based dynamic colour on API 31+ and a fallback scheme otherwise, with day/night support built in.
8. **Picker preview:** ship `previewImage` (all versions) + `previewLayout` (API 31–34) + a generated preview via `providePreview`/`setWidgetPreviews` (API 35+). The latter is rate-limited to about 2 calls per hour.

## Findings

### 1. Glance version and minimum SDK

- Current stable is **Glance 1.2.0, released August 26, 2026**. The latest alpha is 1.3.0-alpha02 (July 01, 2026). [Glance release notes](https://developer.android.com/jetpack/androidx/releases/glance)
- 1.2.0-beta01 moved "the default `minSdk` from API 21 to API 23". [Glance release notes, 1.2.0-beta01](https://developer.android.com/jetpack/androidx/releases/glance#1.2.0-beta01)
- 1.2.0-alpha01 added generated previews (`GlanceAppWidget.providePreview`, `GlanceAppWidgetManager.setWidgetPreview`), took lambda actions out of experimental, and added `MultiProcessGlanceAppWidget`. [Glance release notes, 1.2.0-alpha01](https://developer.android.com/jetpack/androidx/releases/glance#1.2.0-alpha01)
- 1.3.0-alpha01 "Updated Compose `compileSdk` to API 37. This means that a minimum AGP version of 9.2.0 is required". Snap scrolling requires 1.3.0-alpha02 and Android 17. Neither is needed for our use. [Glance release notes, 1.3.0-alpha01](https://developer.android.com/jetpack/androidx/releases/glance#1.3.0-alpha01), [Build UI with Glance](https://developer.android.com/develop/ui/compose/glance/build-ui)
- Since 1.0.0-beta01, Glance runs `provideGlance` inside a WorkManager-based session, so the docs present it as "a good place to load resources, database or network items". [Glance release notes, 1.0.0-beta01](https://developer.android.com/jetpack/androidx/releases/glance#1.0.0-beta01)

### 2. Live countdown and update limits

**Platform limits on periodic updates**

- "`updatePeriodMillis` doesn't support values of less than 30 minutes. However, if you want to disable periodic updates, you can specify 0." [Create an advanced widget](https://developer.android.com/develop/ui/views/appwidgets/advanced)
- The Glance guide recommends updating "as infrequently as possible—no more than once an hour". [Create an app widget with Glance](https://developer.android.com/develop/ui/compose/glance/create-app-widget)
- Glance lists three periodic options: `updatePeriodMillis` "up to once every 30 minutes", WorkManager "more frequent updates, such as every 15 minutes", or a broadcast. It also warns: "Avoid updating your widget every minute when the app isn't awake, as frequent updates drain your users' battery." [Manage and update GlanceAppWidget](https://developer.android.com/develop/ui/compose/glance/glance-app-widget)
- WorkManager: "The minimum repeat interval that can be defined is 15 minutes", and exact timing "depends on the constraints … and on the optimizations performed by the system". [Define work requests](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work)

**Glance update APIs**

- `GlanceAppWidget.update(context, glanceId)`, `updateAll(context)` and `updateIf<State>(context) { … }` are suspend functions that can be called from anywhere in the app, for example from a `CoroutineWorker`. [Manage and update GlanceAppWidget](https://developer.android.com/develop/ui/compose/glance/glance-app-widget)
- `updateAppWidgetState` writes to the backing DataStore "but it doesn't automatically recompose your widget". Always call `update`/`updateAll` afterwards. [Create an app widget with Glance](https://developer.android.com/develop/ui/compose/glance/create-app-widget)
- Glance session timeouts in source: `initialTimeout = 45.seconds`, `additionalTime = 5.seconds`, `idleTimeout = 5.seconds` (in device idle / low-power standby). So a live Glance composition does not stay running; a `LaunchedEffect` ticker in the composition cannot drive a countdown. [AndroidX source: `SessionWorker.kt`](https://github.com/androidx/androidx/blob/androidx-main/glance/glance/src/main/java/androidx/glance/session/SessionWorker.kt)

**Chronometer / TextClock (host-side ticking)**

- `RemoteViews` supports these widgets: "AnalogClock, Button, Chronometer, ImageButton, ImageView, ProgressBar, TextClock, TextView". As of API 31 it also supports CheckBox, RadioButton, RadioGroup and Switch. "Descendants of these classes are not supported." [RemoteViews reference](https://developer.android.com/reference/android/widget/RemoteViews)
- `RemoteViews.setChronometer(viewId, base, format, started)` exists from API 1. `setChronometerCountDown(viewId, isCountDown)` was added in **API 24**. [RemoteViews reference](https://developer.android.com/reference/android/widget/RemoteViews)
- `Chronometer.setBase` uses the `SystemClock.elapsedRealtime()` time base. The display is "MM:SS" or "H:MM:SS", optionally wrapped by a `%s` format string. [Chronometer reference](https://developer.android.com/reference/android/widget/Chronometer)
- In AOSP source, `Chronometer` ticks only while `mVisible && mStarted && isShown()`. It formats with `DateUtils.formatElapsedTime`, and once the countdown passes its base it shows a negative duration (`R.string.negative_duration`). [AOSP `Chronometer.java` (GitHub mirror)](https://github.com/aosp-mirror/platform_frameworks_base/blob/main/core/java/android/widget/Chronometer.java)
- I found no Chronometer or TextClock composable in Glance (a code search of the AndroidX `glance` sources found no `Chronometer`). Glance's way to use such views is `AndroidRemoteViews(remoteViews)`, or `AndroidRemoteViews(remoteViews, containerViewId) { … }`, which hosts Glance content inside a `RemoteViews` `ViewGroup`. [Glance interoperability](https://developer.android.com/develop/ui/compose/glance/interoperability)
- `TextClock` shows wall-clock time, not a countdown (it is listed as a supported widget above). It suits a "current time" element; Prayer Times themselves are static text.

**Scheduling refreshes at Prayer Time boundaries**

- Exact alarms: apps targeting 12+ must hold `SCHEDULE_EXACT_ALARM`, which the user grants, can be revoked, and is not pre-granted on fresh installs targeting 13+. The alternative is `USE_EXACT_ALARM`, which is auto-granted but limited to "limited use cases" such as alarm clocks and calendars and is subject to Play policy. `setExactAndAllowWhileIdle()` fires even under battery-saving measures. [Schedule alarms](https://developer.android.com/develop/background-work/services/alarms)
- Inexact alarms on API 31+ are delivered "within one hour of the supplied trigger time". `setWindow` windows under 10 minutes are typically clipped to 10 minutes. Both are unusable for an on-time Prayer boundary. [Schedule alarms](https://developer.android.com/develop/background-work/services/alarms)
- `TIME_SET`, `ACTION_TIMEZONE_CHANGED`, `ACTION_LOCALE_CHANGED` and `BOOT_COMPLETED` are exempt from manifest-receiver limits. `DATE_CHANGED` is not, so the midnight / next-day rollover needs its own alarm. [Implicit broadcast exceptions](https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions)
- Broadcast receivers have about 10 s before ANR. Glance's receiver already runs work asynchronously and "must never call `goAsync`" yourself. [Create an app widget with Glance](https://developer.android.com/develop/ui/compose/glance/create-app-widget), [Create an advanced widget](https://developer.android.com/develop/ui/views/appwidgets/advanced)

### 3. Interactive actions (marking a Prayer as prayed)

- Actions: `actionStartActivity`, `actionStartService`, `actionSendBroadcast`, lambda actions, and `actionRunCallback<T : ActionCallback>()`, whose `suspend onAction(context, glanceId, parameters)` runs on click. Typed parameters go in `ActionParameters`. "To update the widget after the action is performed … call `update`". [Handle user interaction](https://developer.android.com/develop/ui/compose/glance/user-interaction)
- Lambda callbacks "run in the context of a `WorkManager` worker that is run in a `Service`". `ActionCallback` runs through "a custom async `BroadcastReceiver`", and long tasks should go to a Worker. Activities must be started with `actionStartActivity`, not from lambdas, because of Android 12 trampoline rules. [Handle user interaction](https://developer.android.com/develop/ui/compose/glance/user-interaction)
- Compound buttons: Glance offers `CheckBox`, `Switch` and `RadioButton` with "backwards compatibility". Native compound buttons arrived in Android 12. The widget itself stays stateless: "Your app must store the state and register for state change events." [Build UI with Glance](https://developer.android.com/develop/ui/compose/glance/build-ui), [Create a basic widget (stateful behavior)](https://developer.android.com/develop/ui/views/appwidgets)
- `RemoteViews.setCompoundButtonChecked` / `setOnCheckedChangeResponse` are **API 31**. [RemoteViews reference](https://developer.android.com/reference/android/widget/RemoteViews)
- Glance state: per-widget state defaults to `PreferencesGlanceStateDefinition` (DataStore). The docs separate "Application state" (owned by the app's data layer) from "Glance state". [Manage and update GlanceAppWidget](https://developer.android.com/develop/ui/compose/glance/glance-app-widget), [AndroidX source: `GlanceAppWidget.kt`](https://github.com/androidx/androidx/blob/androidx-main/glance/glance-appwidget/src/main/java/androidx/glance/appwidget/GlanceAppWidget.kt)
- Gestures on widgets are limited to touch and vertical swipe. [App widgets overview](https://developer.android.com/develop/ui/views/appwidgets/overview)

### 4. Sizes and responsive layouts

- `SizeMode.Single` (default) uses one layout at the minimum size. `SizeMode.Responsive(setOf(DpSize…))` composes once per listed size, and the system picks the best fit ("If none fits, the smallest one is used"); the docs say this mode "performs better and allows smoother transitions". `SizeMode.Exact` recomposes on every size change, at a cost in performance and UI jumps, and depends on the launcher reporting sizes. [Build UI with Glance](https://developer.android.com/develop/ui/compose/glance/build-ui)
- Before Android 12, Responsive behaves like Exact (composed per size). [Build UI with Glance](https://developer.android.com/develop/ui/compose/glance/build-ui)
- Sizing attributes: `targetCellWidth/Height` (Android 12+, in grid cells; overrides `minWidth/Height`); `minWidth/Height` (dp, all versions); `minResizeWidth/Height`; `maxResizeWidth/Height` (Android 12+); `resizeMode`. The docs recommend specifying both target cells and min dp. [Create an app widget with Glance](https://developer.android.com/develop/ui/compose/glance/create-app-widget)
- Cell-to-dp estimate (Pixel 4, 5×4 grid): portrait `(73n − 16) × (118m − 16)` dp, landscape `(142n − 15) × (66m − 15)` dp. For example, 2×1 is 130×102 dp in portrait and 269×51 dp in landscape. Use portrait for widths and landscape for heights. [Provide flexible widget layouts](https://developer.android.com/develop/ui/views/appwidgets/layouts)
- Glance layouts map to `RemoteViews` layouts (`Box` → `RelativeLayout`, `Row`/`Column` → `LinearLayout`). [Build UI with Glance](https://developer.android.com/develop/ui/compose/glance/build-ui)
- **A container has at most 10 children.** Glance logs "container cannot have more than 10 elements" and truncates. Five Prayers + Sunrise + header fits, but only just, in a single `Column`. [AndroidX source: `LayoutSelection.kt`](https://github.com/androidx/androidx/blob/androidx-main/glance/glance-appwidget/src/main/java/androidx/glance/appwidget/LayoutSelection.kt)
- Corner radius: `GlanceModifier.cornerRadius(android.R.dimen.system_app_widget_background_radius)` on Android 12+ ("never larger than 28 dp"). [Create an app widget with Glance](https://developer.android.com/develop/ui/compose/glance/create-app-widget)

### 5. RTL and Arabic text

- Glance computes `isRtl` from `resources.configuration.layoutDirection`, and maps `TextAlign.Left/Right` to opposite/normal alignment under RTL. Row and Column layouts become platform `LinearLayout`s, which mirror under an RTL configuration. [AndroidX source: `RemoteViewsTranslator.kt`](https://github.com/androidx/androidx/blob/androidx-main/glance/glance-appwidget/src/main/java/androidx/glance/appwidget/RemoteViewsTranslator.kt), [`TextTranslator.kt`](https://github.com/androidx/androidx/blob/androidx-main/glance/glance-appwidget/src/main/java/androidx/glance/appwidget/translators/TextTranslator.kt)
- **Custom fonts are not supported:** "`fontFamily` supports all system fonts … but custom fonts in apps aren't supported." [Build UI with Glance (Handle text)](https://developer.android.com/develop/ui/compose/glance/build-ui)
- `FontFamily(family: String)` names a family: "If this is found on the system it will be used, otherwise it will fallback to a system default". Constants are `Serif`, `SansSerif`, `Monospace` and `Cursive`. [Glance `FontFamily` reference](https://developer.android.com/reference/kotlin/androidx/glance/text/FontFamily). The translator applies it as a `TypefaceSpan(family.family)`, i.e. a system family name only. [`TextTranslator.kt`](https://github.com/androidx/androidx/blob/androidx-main/glance/glance-appwidget/src/main/java/androidx/glance/appwidget/translators/TextTranslator.kt)
- `FontWeight` and custom weights in `FontStyle` are available since Glance 1.1.0. [Glance release notes, 1.1.0-rc01](https://developer.android.com/jetpack/androidx/releases/glance#1.1.0-rc01)
- Consequence: Arabic glyphs come from the system font fallback chain, which varies by OEM. The only route to a bundled Arabic typeface is drawing text to a `Bitmap`. That counts against the Android 17 bitmap budget (§8) and loses text semantics.

### 6. Theming

- `GlanceTheme { … }`: "On devices that support dynamic colors, this theme is derived from the user-specific platform colors. On other devices, this falls back to the Material baseline theme." `glance-material3`'s `ColorProviders(light, dark)` maps an app's M3 schemes. You can pass the app's scheme only below API 31 (S) to keep dynamic colour on 12+. [Theme in Glance](https://developer.android.com/develop/ui/compose/glance/theme)
- Dynamic colours on widgets are Android 12+ only. [Enhance your widget](https://developer.android.com/develop/ui/views/appwidgets/enhance)
- Day/night: `ColorProvider(day = …, night = …)` and resource qualifiers such as `values-night`. A `widgetBackground` colour role was added in 1.1.0-alpha01. [Build UI with Glance](https://developer.android.com/develop/ui/compose/glance/build-ui), [Theme in Glance](https://developer.android.com/develop/ui/compose/glance/theme), [Glance release notes, 1.1.0-alpha01](https://developer.android.com/jetpack/androidx/releases/glance#1.1.0-alpha01)

### 7. Widget picker preview

- Three tiers: generated previews on **Android 15+**, scalable `previewLayout` on **Android 12–14**, and `previewImage` for earlier versions and as a fallback. [Add previews to your widget picker](https://developer.android.com/develop/ui/views/appwidgets/previews)
- Generated previews need `compileSdk` ≥ 35. In Glance you override `providePreview` ("a single composition with no recomposition or effects") and call `GlanceAppWidgetManager.setWidgetPreviews`. "`setWidgetPreview` is a rate-limited API. The default limit is approximately two calls per hour." The system gives no callback, so the app decides when to publish. [Glance generated previews](https://developer.android.com/develop/ui/compose/glance/generated-previews)
- The preview renders at `SizeMode.Single` by default. To show all elements, override `previewSizeMode` with `SizeMode.Responsive`. [Glance generated previews](https://developer.android.com/develop/ui/compose/glance/generated-previews)
- `setWidgetPreviews` is annotated `@RequiresApi(VANILLA_ICE_CREAM)` (API 35). [AndroidX source: `GlanceAppWidgetManager.kt`](https://github.com/androidx/androidx/blob/androidx-main/glance/glance-appwidget/src/main/java/androidx/glance/appwidget/GlanceAppWidgetManager.kt)
- Glance has no `initialLayout` of its own. Use `@layout/glance_default_loading_layout`. `previewLayout` is a plain XML layout, so for Glance it is a hand-made static XML mock. [Create an app widget with Glance](https://developer.android.com/develop/ui/compose/glance/create-app-widget)

### 8. Battery cost, quotas and memory

- "Updating widget content can be computationally expensive. To save battery consumption, optimize the update type, frequency, and timing." [Create an advanced widget](https://developer.android.com/develop/ui/views/appwidgets/advanced)
- Exact alarms "consume a great deal of resources … the system cannot easily batch these requests". [Schedule alarms](https://developer.android.com/develop/background-work/services/alarms)
- App Standby Buckets (API 28+) limit jobs and alarms by bucket while on battery. **"Apps with active widgets"** and apps granted `USE_EXACT_ALARM` are exempt from the *restricted* bucket. That bucket allows one alarm and one batched job session per day. [App Standby Buckets](https://developer.android.com/topic/performance/appstandby)
- Android 17 (apps targeting API 37): `RemoteViews` image memory across all size buckets is capped at `6 × width × height` bytes of the display, and an overrun throws `IllegalArgumentException`. With `SizeMode.Responsive`, a bitmap decoded inside `provideContent` is duplicated for every bucket, so decode it once in `provideGlance`. [Android 17 behavior changes](https://developer.android.com/about/versions/17/behavior-changes-17), [Create an app widget with Glance](https://developer.android.com/develop/ui/compose/glance/create-app-widget)

### Minimum-SDK summary

| Feature | Min API | Source |
|---|---|---|
| Glance 1.2 library | 23 | [Release notes](https://developer.android.com/jetpack/androidx/releases/glance#1.2.0-beta01) |
| `Chronometer` count-down via `setChronometerCountDown` | 24 | [RemoteViews](https://developer.android.com/reference/android/widget/RemoteViews) |
| App Standby Buckets apply | 28 | [App Standby](https://developer.android.com/topic/performance/appstandby) |
| Responsive layouts (true), `targetCell*`, `maxResize*`, `description`, `previewLayout`, native compound buttons, dynamic colour, system corner radius, exact-alarm permission | 31 | [Layouts](https://developer.android.com/develop/ui/views/appwidgets/layouts), [Enhance](https://developer.android.com/develop/ui/views/appwidgets/enhance), [Alarms](https://developer.android.com/develop/background-work/services/alarms) |
| `USE_EXACT_ALARM` option | 33 | [Alarms](https://developer.android.com/develop/background-work/services/alarms) |
| Generated picker previews | 35 | [Generated previews](https://developer.android.com/develop/ui/compose/glance/generated-previews) |
| Hard bitmap-memory cap (when targeting) | 37 | [Android 17 changes](https://developer.android.com/about/versions/17/behavior-changes-17) |

## Implications for Widget Variant design

- **One `GlanceAppWidget` class per Widget Variant**, each with its own receiver, provider XML, `targetCell*`/`min*` sizes, description and previews. They share composables and one data source (Prayer Times calculator + Prayer Log repository).
- **Next-Prayer countdown variants:** render the countdown through `AndroidRemoteViews` with a `Chronometer` (count-down, base = `elapsedRealtime() + (nextPrayerTime − now)`). Re-render at every Prayer Time, at midnight, and on `TIME_SET` / `TIMEZONE_CHANGED` / `LOCALE_CHANGED` / boot. Wherever possible, reuse the alarms that the Adhan Alert / Prayer Reminder features schedule. Design must accept the seconds display, or choose a "next Prayer at HH:MM" design that needs no ticking at all.
- **Prayer Log variant (checklist):** five `CheckBox` rows (or tappable rows with icons) with `ActionParameters` carrying the Prayer and date. The write goes to the app's Prayer Log store, followed by `updateAll` for every variant. Sunrise is shown but never gets a checkbox (per glossary). Keep each container to 10 children or fewer.
- **Full-day Prayer Times variant:** a static list of five Prayer Times + Sunrise. It changes only at day rollover or when location/Calculation Method changes, which is the cheapest variant. Highlight the next Prayer with a boundary-time refresh.
- **Breakpoints:** start from 2×1, 2×2, 4×1, 4×2 (see the cell formula above) with `SizeMode.Responsive`. Drop secondary content (Hijri date, Sunrise, labels) at small sizes.
- **Arabic-first:** rely on system RTL mirroring. Use `TextAlign.Start/End`, never Left/Right. Use system fonts only. Test digit shaping (Arabic-Indic vs Latin) in both the `Chronometer` and our own formatted times.
- **Theming:** `GlanceTheme` with dynamic colour on 12+ and the app's M3 light/dark scheme below that. Avoid bitmaps (memory cap, no dynamic tint). Prefer vector drawables tinted via `ColorFilter.tint(GlanceTheme.colors…)`.
- **Previews:** three tiers per variant. Republish generated previews only after meaningful changes, such as location or method changes or the first launch, to stay under about 2 calls per hour.

## Open questions for the human

1. **minSdk:** 26 (wide reach, degraded widgets on 8–11) or 31 (all widget features native, no fallback layouts)? This decision matters most for widget complexity.
2. **Countdown format:** is a seconds-ticking `Chronometer` (`01:23:45`) acceptable, or do we want "in 1 h 23 min" (minute granularity, needing ~per-minute wakeups we should avoid) or simply "Asr at 15:42"?
3. **Exact-alarm permission:** do Adhan Alerts justify `USE_EXACT_ALARM` under Play policy (alarm-clock-like use), or do we ask the user for `SCHEDULE_EXACT_ALARM`? The widget's boundary refresh should share this choice.
4. **Custom Arabic typeface:** is the system Arabic font acceptable on widgets? The only alternative is bitmap-rendered text with real costs.
5. **Digits:** Arabic-Indic (٠١٢٣) or Latin digits in the widget? `Chronometer` uses `DateUtils.formatElapsedTime`, whose digit choice follows the locale. This needs on-device verification.
6. **Retroactive logging:** should the checklist variant allow marking earlier Prayers of today only, or also yesterday's Isha after midnight?

## Sources

- Glance release notes: https://developer.android.com/jetpack/androidx/releases/glance
- Create an app widget with Glance: https://developer.android.com/develop/ui/compose/glance/create-app-widget
- Manage and update GlanceAppWidget: https://developer.android.com/develop/ui/compose/glance/glance-app-widget
- Build UI with Glance: https://developer.android.com/develop/ui/compose/glance/build-ui
- Handle user interaction (Glance): https://developer.android.com/develop/ui/compose/glance/user-interaction
- Theme in Glance: https://developer.android.com/develop/ui/compose/glance/theme
- Glance interoperability: https://developer.android.com/develop/ui/compose/glance/interoperability
- Glance generated previews: https://developer.android.com/develop/ui/compose/glance/generated-previews
- Glance `FontFamily` reference: https://developer.android.com/reference/kotlin/androidx/glance/text/FontFamily
- Create a basic widget: https://developer.android.com/develop/ui/views/appwidgets
- App widgets overview: https://developer.android.com/develop/ui/views/appwidgets/overview
- Provide flexible widget layouts: https://developer.android.com/develop/ui/views/appwidgets/layouts
- Enhance your widget: https://developer.android.com/develop/ui/views/appwidgets/enhance
- Create an advanced widget: https://developer.android.com/develop/ui/views/appwidgets/advanced
- Add previews to your widget picker: https://developer.android.com/develop/ui/views/appwidgets/previews
- RemoteViews reference: https://developer.android.com/reference/android/widget/RemoteViews
- Chronometer reference: https://developer.android.com/reference/android/widget/Chronometer
- Schedule alarms: https://developer.android.com/develop/background-work/services/alarms
- Define work requests (WorkManager): https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work
- App Standby Buckets: https://developer.android.com/topic/performance/appstandby
- Implicit broadcast exceptions: https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions
- Android 17 behavior changes (apps targeting 17): https://developer.android.com/about/versions/17/behavior-changes-17
- AndroidX source, `SessionWorker.kt`: https://github.com/androidx/androidx/blob/androidx-main/glance/glance/src/main/java/androidx/glance/session/SessionWorker.kt
- AndroidX source, `GlanceAppWidget.kt`: https://github.com/androidx/androidx/blob/androidx-main/glance/glance-appwidget/src/main/java/androidx/glance/appwidget/GlanceAppWidget.kt
- AndroidX source, `GlanceAppWidgetManager.kt`: https://github.com/androidx/androidx/blob/androidx-main/glance/glance-appwidget/src/main/java/androidx/glance/appwidget/GlanceAppWidgetManager.kt
- AndroidX source, `LayoutSelection.kt`: https://github.com/androidx/androidx/blob/androidx-main/glance/glance-appwidget/src/main/java/androidx/glance/appwidget/LayoutSelection.kt
- AndroidX source, `RemoteViewsTranslator.kt`: https://github.com/androidx/androidx/blob/androidx-main/glance/glance-appwidget/src/main/java/androidx/glance/appwidget/RemoteViewsTranslator.kt
- AndroidX source, `TextTranslator.kt`: https://github.com/androidx/androidx/blob/androidx-main/glance/glance-appwidget/src/main/java/androidx/glance/appwidget/translators/TextTranslator.kt
- AOSP source, `Chronometer.java` (GitHub mirror): https://github.com/aosp-mirror/platform_frameworks_base/blob/main/core/java/android/widget/Chronometer.java
