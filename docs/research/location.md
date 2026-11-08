# Research: location for Prayer Times (ticket #5)

Researched 2026-11-08. Terms follow [GLOSSARY.md](../../GLOSSARY.md).

> **Source access caveat.** This session's network blocked `support.google.com`, `geonames.org` / `download.geonames.org`, `naturalearthdata.com` and `android-developers.googleblog.com`. Claims about Google Play policy and GeoNames therefore rest on search-engine extracts of those official pages, not a direct read. They are marked **[unverified-direct]** and should be checked before we rely on exact wording. Everything from `developer.android.com`, AOSP source and the Natural Earth GitHub repository was read directly. The Natural Earth sizes were measured locally.

## Question

1. Should we get approximate location (`ACCESS_COARSE_LOCATION`) from the Fused Location Provider, which needs Google Play services, or from the platform `LocationManager`, which also works on phones without Google Play services such as Huawei? How accurate is approximate location, and is that accurate enough for Prayer Times? Roughly how much Prayer Time error does each km of position error cause?
2. When the user travels, how can we notice a meaningful move without background location permission?
3. For the manual fallback, which offline city datasets exist? What are their licences and attribution rules, do they have Arabic names, and how much would a reasonable subset add to the app's size?
4. What do the Play Store data-safety rules require when location is collected but never leaves the device? What do the prominent-disclosure rules for location require?

## Short answer / recommendation

- **Use the platform `LocationManager`, through `androidx.core`'s `LocationManagerCompat`, and request only `ACCESS_COARSE_LOCATION`.** We don't need the Google Play services dependency. Approximate location is accurate enough.
  - Use `FUSED_PROVIDER` when it exists (API 31+). Otherwise use `NETWORK_PROVIDER`, then `GPS_PROVIDER`. Try `getLastKnownLocation` first as a quick cached answer.
  - Approximate location on stock Android snaps to a **2 km grid**. Each km of error moves Prayer Times by only **about 2–3.5 s**. A 2 km error is therefore a few seconds, well under the one-minute resolution we display.
- **Travel: check location only while the app is in the foreground.**
  - Each time the user opens the app, get a fresh coarse location. If it is more than a few km from the stored location, recompute the Prayer Times.
  - The docs count location reads from a widget update, an `AlarmManager` alarm or `WorkManager` as **background** access, so we must not read location there.
  - Register for `ACTION_TIMEZONE_CHANGED`, a broadcast still allowed in the manifest, as a cheap travel signal. It cannot read location. It should only mark the stored location as possibly stale, for example with a notification or widget hint asking the user to open the app.
- **Manual fallback: Natural Earth populated places is the simplest good fit.**
  - Public domain, no attribution required.
  - 7,342 places, every one with an Arabic name.
  - Each place has an IANA time zone, a GeoNames ID and a Wikidata ID.
  - About **155 KB gzipped** with name, Arabic name, country and coordinates.
  - **GeoNames `cities15000`** (CC BY 4.0, attribution required) is denser and so better for small towns. Its Arabic names need a join with the `alternateNames` table. Rough estimate: 0.5–1 MB compressed for that subset.
- **Play data safety: if location never leaves the device and no SDK sends it anywhere, Google's guidance says we need not declare it as "collected"** [unverified-direct].
  - We should still request location in context, explain why before asking, and keep a privacy policy.
  - Prominent disclosure is mainly for uses the user would not expect, such as background location. A foreground, in-context, approximate-only request for Prayer Times is the expected case. A short rationale screen is cheap insurance.

## Findings

### 1. Fused Location Provider vs platform LocationManager

- The Fused Location Provider (FLP) is "one of the location APIs in Google Play services", and the project "must include Google Play services" to use it. FLP's `getLastLocation()` can return null after "Google Play services on the device has restarted". Source: https://developer.android.com/develop/sensors-and-location/location/retrieve-current
- The platform `LocationManager` offers the same building blocks without Play services. Source: https://developer.android.com/reference/android/location/LocationManager
  - `FUSED_PROVIDER` (API 31) "may combine inputs from several other location providers".
  - `NETWORK_PROVIDER` "determines location based on nearby of cell tower and WiFi access points".
  - `PASSIVE_PROVIDER` returns only locations other apps requested, and is "always present".
  - `getCurrentLocation(...)` (API 30/31) "may activate sensors". It "will never return older locations (for example, several minutes old or older)".
  - `getLastKnownLocation` "will only ever return a cached location", which "may be quite old … the age of the location should always be checked".
  - With only the coarse permission, "providers will still return location results, but the exact location will be obfuscated to a coarse level of accuracy".
- `androidx.core.location.LocationManagerCompat` backports `getCurrentLocation` (non-deprecated overload since core 1.13.0). It also adds `hasProvider` and `isLocationEnabled`. It warns that background callers "may notice that the method fails to determine a valid location fix more often". Source: https://developer.android.com/reference/androidx/core/location/LocationManagerCompat
- **Implication:** the Huawei case is the deciding factor. A Prayer Times app needs a single fix now and then, not continuous tracking, so FLP's power savings matter little here. Huawei devices without Google Play services would get no location at all from FLP. On those devices, which providers exist (for example whether `NETWORK_PROVIDER` is present) varies by vendor, so the code must use `hasProvider` and fall back. See the open questions.

### 2. Accuracy of approximate location

- **Official figure.** An approximate estimate from `LocationManagerService` or `FusedLocationProvider` "is accurate to within about 3 square kilometers (about 1.2 square miles)". Google also recommends that apps "should still work when the user grants only approximate location access". Source: https://developer.android.com/develop/sensors-and-location/location/permissions#accuracy
- **Requesting coarse only is recommended.** The docs say: "it's recommended that you only request `ACCESS_COARSE_LOCATION`". On Android 12+ the user can downgrade any app to approximate in the permission dialog or in Settings. Source: https://developer.android.com/develop/sensors-and-location/location/permissions/runtime
- **How AOSP coarsens location.** In `LocationFudger.java`:
  - It adds a slowly drifting random offset with σ = accuracy/4 (500 m by default). The offset changes 3% per hour.
  - It then snaps to a grid whose size equals the coarse accuracy.
  - The minimum accuracy is 200 m.
  - `SystemSettingsHelper.java` sets `DEFAULT_COARSE_LOCATION_ACCURACY_M = 2000.0f`.
  - Sources: https://github.com/aosp-mirror/platform_frameworks_base/blob/main/services/core/java/com/android/server/location/fudger/LocationFudger.java and https://github.com/aosp-mirror/platform_frameworks_base/blob/main/services/core/java/com/android/server/location/injector/SystemSettingsHelper.java
  - So the worst case on the old algorithm is half the grid diagonal (about 1.4 km) plus the offset, typically **≤ 2–3 km**.
- **Android 17 change.** Coarse location becomes density-based. The old "static 2 km-wide grid" is replaced by "a dynamically-sized area based on local population density", larger where few people live. Rural users may see larger errors. No sizes are published. Source: https://developer.android.com/blog/posts/redefining-location-privacy-new-tools-and-improvements-for-android-17. AOSP's `LocationFudger` has the matching S2-cell code path behind `Flags.densityBasedCoarseLocations()`.

### 3. Prayer Time error per km of position error

The calculation is our own, not from a source. We computed solar Prayer Times with a standard low-precision solar position algorithm: Fajr at 18°, Isha at 17°, Asr Convention Standard, Maghrib at −0.833°. We then moved the position 1 km north and 1 km east.

- **East–west moves shift every Prayer Time by the same amount.** The Earth turns 1° of longitude in 4 minutes, so the shift is ≈ 240 s ÷ (111.32 km × cos latitude) per km:

  | City | Latitude | E–W shift per km |
  |---|---|---|
  | Mecca | 21.4° | ~2.3 s |
  | Cairo | 30.0° | ~2.5 s |
  | London | 51.5° | ~3.5 s |

- **North–south moves leave Dhuhr unchanged.** They mostly shift Fajr, Sunrise, Maghrib, Isha and Asr, most at the solstices. Typical values are 0–1.6 s/km in Mecca, up to ~2.3 s/km in Cairo in June, and up to ~3 s/km in London.
- **Worst case for coarse location (≤ ~3 km): roughly ≤ 10 s.** Even a 10 km error is ~25–35 s. A useful rule of thumb is **about 1 minute of error per 20–30 km of east–west error at mid-latitudes**.
- Prayer Times are shown to the minute, and the gap between Calculation Methods is often several minutes. Approximate location is therefore more than accurate enough. A nearest-city fallback is also fine when the city is within ~20 km.

### 4. Detecting travel without background location

- **What counts as background.** The system treats location as foreground only when "an activity that belongs to your app is visible" or a `location` foreground service is running. Anything else counts as background, and on Android 10+ needs `ACCESS_BACKGROUND_LOCATION`. Source: https://developer.android.com/develop/sensors-and-location/location/permissions
- **Widgets, alarms and WorkManager count as background.** The background-access checklist names `WorkManager` / `JobScheduler` tasks, `AlarmManager` operations and "Pending intents that are invoked from an app widget" as places where location access happens in the background. Source: https://developer.android.com/develop/sensors-and-location/location/background
  - **Refreshing location on widget or alarm refresh therefore needs background permission.** Play restricts that permission to core features (see §6). We should not do it.
  - Even with background permission, apps "can receive location updates only a few times each hour" (same page).
- **Passive provider.** `PASSIVE_PROVIDER` / `getLastKnownLocation` reuse fixes other apps triggered, which saves battery. They are still location API calls, though, so calling them from a receiver, worker or widget is background access too. Use them only as a quick first answer while the app is open.
- **Time-zone broadcast.** `ACTION_TIMEZONE_CHANGED` and `TIME_SET` are on the implicit-broadcast exemption list, so a manifest receiver still gets them on API 26+. They are exempt because "clock apps might need to receive these broadcasts to update alarms when the time, timezone, or alarms change". Source: https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions
  - We need this receiver anyway, to reschedule Adhan Alerts and Prayer Reminders.
  - It can also mark the stored location as possibly stale and post a notification or widget hint asking the user to open the app.
  - It misses travel that stays within one time zone, for example within Saudi Arabia or Egypt.
- **Suggested design:**
  1. On each app open (activity visible), get the cached fix, then `getCurrentLocation` with a short timeout.
  2. If the new fix is more than a threshold (say 5–10 km, ≈ 15–30 s of error) from the stored location, recompute and reschedule.
  3. Show the stored location's name and age on the main screen and in the widget, so a stale location is visible.
  4. Use the time-zone broadcast as a "please open the app" prompt.

### 5. Offline city datasets for the manual fallback

| Dataset | Licence / attribution | Arabic names | Size (our subset) |
|---|---|---|---|
| **Natural Earth 10m populated places** | Public domain. The licence says "No permission is needed to use Natural Earth. Crediting the authors is unnecessary." Suggested credit: "Made with Natural Earth". Source: https://github.com/nvkelso/natural-earth-vector/blob/master/LICENSE.md (official site https://www.naturalearthdata.com/about/terms-of-use/ was not reachable here) | **Yes.** All 7,342 features have `NAME_AR`, e.g. Cairo → القاهرة, Makkah → مكة المكرمة. They also have `TIMEZONE` (6,159 of 7,342), `GEONAMESID` and `WIKIDATAID` | Measured: name + Arabic name + ISO3 country + lat/lon to 4 decimals is 338 KB raw, **~155 KB gzip -9**. Source file: https://github.com/nvkelso/natural-earth-vector/blob/master/geojson/ne_10m_populated_places.geojson (master, read 2026-11-08). Note: the `_simple` variant has **no** Arabic field |
| **GeoNames `cities15000` / `cities5000` / `cities1000`** [unverified-direct] | CC BY 4.0. The readme says the work "is licensed under a Creative Commons Attribution 4.0 License" with no warranty. GeoNames asks users to "give credit to GeoNames … with a link or another reference". Sources: https://download.geonames.org/export/dump/readme.txt, https://www.geonames.org/export/ | The main table's `alternatenames` field has no language tags. Reliable Arabic names need `alternateNamesV2.zip` (or the per-country files under `export/dump/alternatenames/`) filtered on `isolanguage = ar`. The main table has an IANA `timezone` column | Counts from the readme: cities15000 "ca 25.000", cities5000 "ca 50.000", cities1000 "ca 130.000" (cities500 ≈ 185,000). **Estimate**, extrapolated from the measured Natural Earth bytes per row (~21 B gzip): ~0.5–0.7 MB for cities15000, ~1–1.3 MB for cities5000, ~3 MB for cities1000. A mirror lists cities15000.zip itself at ~2.35 MB with all columns. Not measured: the download host was blocked |
| **Wikidata** [unverified-direct] | Structured data is CC0. Attribution is not required, but Wikidata asks users to mention it as the source. Sources: https://www.wikidata.org/wiki/Wikidata:Licensing, https://www.wikidata.org/wiki/Wikidata:Data_access | Arabic labels for most cities | No ready-made cities file; we would need a SPARQL query or a dump extract. Useful for filling gaps, keyed by the `WIKIDATAID` Natural Earth already has |

- **How dense each dataset is.** Natural Earth has ~7k places worldwide (e.g. 42 in Egypt, 31 in Saudi Arabia). The nearest listed place can be 50–150 km away in rural areas, which is ~2–5 minutes of error by §3. That is acceptable for a fallback but not ideal. GeoNames cities15000 or cities5000 usually gets within ~20 km in populated regions.
- **A practical hybrid.** Ship Natural Earth for its Arabic names and public-domain licence. Add GeoNames cities15000 for density, take Arabic names from GeoNames `alternateNames` where Natural Earth has none, and credit GeoNames on the About screen. Whatever we ship, the city list should include the IANA time zone.

### 6. Play Store data safety and prominent disclosure

- **Data types.** "Approximate location" means a location to an area "greater than or equal to 3 square kilometers … or location provided by Android's `ACCESS_COARSE_LOCATION` permission". "Precise location" means less than 3 km² / `ACCESS_FINE_LOCATION`. Declaring `ACCESS_COARSE_LOCATION` is listed as an indicator of location access. If an SDK collects data, the app must declare it. Source: https://developer.android.com/privacy-and-security/declare-data-use
- **Data processed only on the device** [unverified-direct]. Per the Play Console Help "Data safety" article, data that the app accesses only on the device and never sends off it does not have to be declared as "collected". The page's example is an app that uses location only for on-device features. The "ephemeral processing" exception is separate and applies to data that *is* sent off the device. Sharing data with another app on the same device can still count as "sharing". Sources: https://support.google.com/googleplay/android-developer/answer/10787469 and https://support.google.com/googleplay/android-developer/answer/10144311.
  - **Implication:** with no backend, no analytics or crash SDK sending location, and no cloud backup of the stored location, we can likely declare location as *not collected*.
  - Android Auto Backup could upload the stored coordinates to the user's Google Drive. Whether that counts as "off device" needs checking, or we exclude the location store from backup. Android 14+ shows the data-safety info inside the permission dialog. Source: https://developer.android.com/privacy-and-security/declare-data-use
- **Prominent disclosure** [unverified-direct]. The User Data policy requires an in-app disclosure where "users may not reasonably expect" their personal and sensitive data to be used. It must come *before* the runtime permission request, describe the data and how it is used, and be followed by a distinct affirmative user action. It "is not a substitute" for the privacy policy or the Data safety section. Google's examples of violations centre on undisclosed background location. Source: https://support.google.com/googleplay/android-developer/answer/10144311 (User Data policy) and https://support.google.com/googleplay/android-developer/answer/11150561 (prominent disclosure best practices).
- **Location permissions policy** [unverified-direct]. Background location is limited to core functionality and needs a declaration form and review. Location may not be requested solely for ads or analytics. Source: https://support.google.com/googleplay/android-developer/answer/9799150. Android docs repeat that Play restricts background access to "apps that need it for their core features". Source: https://developer.android.com/develop/sensors-and-location/location/permissions
- **Android best practice.** Request location "in context", only after the user interacts with the feature. Source: https://developer.android.com/develop/sensors-and-location/location/permissions/runtime
- **Android 17 location button.** "If your app targets Android 17 (API level 37) or later and only contains features that require session-based location access to function, Google Play policy requires you to use the location button." The button is for session-scoped **precise** location. Source: https://developer.android.com/guide/topics/permissions/private-alternatives/location-button. Policy page: https://support.google.com/googleplay/android-developer/answer/16909972 [unverified-direct]. MyPrayers uses approximate location every time the app opens, so it does not appear to be in scope, but this needs confirming.

## Open questions for the human

1. **How large a fallback dataset?** Option A is Natural Earth only: ~155 KB, public domain, sparse. Option B adds GeoNames cities15000: roughly +0.5–1 MB, CC BY 4.0 credit on the About screen, much denser. Is ~1 MB acceptable? Do we want an in-app attribution screen?
2. **Should the app offer a precise-location upgrade at all?** The numbers say it isn't needed. Leaving `ACCESS_FINE_LOCATION` out of the manifest entirely gives the simplest permission story.
3. **What threshold triggers a recompute?** Suggestion: 5–10 km. Also, should a time-zone change post a notification, or only change the widget?
4. **Auto Backup:** should the stored location be excluded from Android backup, so that "never leaves the device" stays literally true for the data-safety form?
5. **Device testing on a Huawei phone without Google Play services.** Which `LocationManager` providers exist there, and how good are they? Does `getLastKnownLocation` from a background context return null with foreground-only permission, as expected? We should test before finalising.
6. **Policy pages to re-read by hand**, because this session could not reach `support.google.com`: the Data safety article (10787469), the User Data policy (10144311), the Location permissions policy (9799150) and the location button policy (16909972). Confirm the on-device exemption wording and that the location button rule does not apply to us.
7. **Android 17 density-based coarse location** may give larger errors in rural areas, and no sizes are published. Is a ~5-minute worst case acceptable for rural users, or should rural users be nudged toward manual city selection?

## Sources

- https://developer.android.com/develop/sensors-and-location/location/permissions
- https://developer.android.com/develop/sensors-and-location/location/permissions/runtime
- https://developer.android.com/develop/sensors-and-location/location/retrieve-current
- https://developer.android.com/develop/sensors-and-location/location/background
- https://developer.android.com/reference/android/location/LocationManager
- https://developer.android.com/reference/androidx/core/location/LocationManagerCompat
- https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions
- https://developer.android.com/guide/topics/permissions/private-alternatives/location-button
- https://developer.android.com/blog/posts/redefining-location-privacy-new-tools-and-improvements-for-android-17
- https://developer.android.com/privacy-and-security/declare-data-use
- https://github.com/aosp-mirror/platform_frameworks_base/blob/main/services/core/java/com/android/server/location/fudger/LocationFudger.java
- https://github.com/aosp-mirror/platform_frameworks_base/blob/main/services/core/java/com/android/server/location/injector/SystemSettingsHelper.java
- https://github.com/nvkelso/natural-earth-vector/blob/master/LICENSE.md
- https://github.com/nvkelso/natural-earth-vector/blob/master/geojson/ne_10m_populated_places.geojson
- https://www.naturalearthdata.com/about/terms-of-use/ (not reachable from this session)
- https://download.geonames.org/export/dump/readme.txt [unverified-direct]
- https://www.geonames.org/export/ [unverified-direct]
- https://www.wikidata.org/wiki/Wikidata:Licensing [unverified-direct]
- https://www.wikidata.org/wiki/Wikidata:Data_access [unverified-direct]
- https://support.google.com/googleplay/android-developer/answer/10787469 [unverified-direct]
- https://support.google.com/googleplay/android-developer/answer/10144311 [unverified-direct]
- https://support.google.com/googleplay/android-developer/answer/11150561 [unverified-direct]
- https://support.google.com/googleplay/android-developer/answer/9799150 [unverified-direct]
- https://support.google.com/googleplay/android-developer/answer/16909972 [unverified-direct]
