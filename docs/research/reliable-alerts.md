# Reliable Adhan Alerts and Prayer Reminders on modern Android

Research for ticket #3. Researched 2026-11-08 against Android 17 (API 37), the newest released platform. Terms follow [`GLOSSARY.md`](../../GLOSSARY.md): **Prayer**, **Prayer Time**, **Adhan Alert**, **Prayer Reminder**.

## Question

What does it take for Adhan Alerts and Prayer Reminders to fire on time on modern Android? The question covers:

- exact-alarm permissions and Google Play policy
- choice of alarm API
- Doze and App Standby
- OEM battery savers
- rescheduling after reboot or a time change
- how far ahead to schedule
- playing a full-length adhan (2–4 min): foreground service rules, Do Not Disturb (DND) and silent mode, audio stream
- POST_NOTIFICATIONS
- changes in Android 14–17

## Short answer / recommendation

1. **Schedule with `AlarmManager` exact alarms, not WorkManager.** Jobs (and so WorkManager) don't run in Doze ([Doze guide][doze]).
   - **Adhan Alerts: `setAlarmClock()`.** The system "never adjusts their delivery time" and "leaves low-power modes if necessary" ([alarms guide][alarms]). The framework treats it as wake-from-idle and exempt from App Standby bucket limits ([AOSP AlarmManagerService][ams-exempt]).
   - **Prayer Reminders: `setExactAndAllowWhileIdle()`.** It fires in Doze and is also exempt from bucket limits ([AOSP][ams-exempt]).
   - Trade-off: a `setAlarmClock()` alarm is shown to the user as an upcoming alarm ([AlarmManager reference][am-ref]). Both open-source prayer apps we read let the user switch between the two APIs (see §4).
2. **Permission: declare `SCHEDULE_EXACT_ALARM` and build the user grant flow:**
   - check `canScheduleExactAlarms()`
   - send the user to `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`
   - listen for `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`
   - fall back to `setWindow()` when the permission is missing

   On Android 14+ this permission is denied by default for new installs ([A14 change][a14-alarm]). `USE_EXACT_ALARM` is granted at install, but Play limits it to alarm-clock, timer and calendar apps. Whether a prayer-times app qualifies is a **policy call for the human** (see Open questions). The policy text names neither prayer apps nor religious apps.
3. **Play the full adhan from a `mediaPlayback` foreground service (FGS), started from the alarm, with `AudioAttributes.USAGE_ALARM`.** This is not optional on Android 17:
   - Background audio from an app targeting API 37 is silently muted unless one of two things holds ([A17 background audio hardening][a17-bgaudio]):
     - the FGS has "while-in-use" capability, which an alarm-started FGS doesn't get, or
     - the app holds the exact-alarm permission **and** plays a `USAGE_ALARM` stream.
   - An exact alarm is one of the allowed ways to start an FGS from the background ([FGS restrictions][fgs-bg]).
   - Play requires a foreground-service declaration with a demo video for each FGS type ([FGS types][fgs-types]).
   - Short sounds (Prayer Reminders, or a short adhan clip) can just be a notification-channel sound, which the system plays itself (see §7).
4. **`USAGE_ALARM` gives the behaviour users expect from an adhan:**
   - It plays when the ringer is on silent or vibrate. AOSP doesn't mute the alarm stream with the ringer unless a global setting is on (default off).
   - It is muted only by DND "Total silence", or by Priority DND with alarms turned off ([AOSP AudioService][aosp-audio]).
   - Product decision for the human: should the adhan respect silent mode? Offer a setting either way.
5. **Re-plan the schedule from one idempotent function, called on:**
   - `BOOT_COMPLETED`
   - `LOCKED_BOOT_COMPLETED` (receiver marked `directBootAware`, schedule stored in device-protected storage)
   - `TIME_SET` and `TIMEZONE_CHANGED`
   - `MY_PACKAGE_REPLACED`
   - exact-alarm permission granted
   - each alarm firing
   - app open, and a change of location or settings

   The time, time-zone, boot and locale broadcasts are on the list of manifest receivers still allowed ([broadcast exceptions][bcast-ex]). A Glance widget doesn't need those broadcasts to refresh.
6. **How far ahead:** Android gives no official number. The framework caps an app at 500 concurrent alarms ([AOSP][ams-consts]). Recommendation:
   - Keep a rolling window of the next ~48 h of Adhan Alerts and Prayer Reminders, each with its own stable request code.
   - Rebuild the window on every trigger above.
   - This is more robust than the "schedule only the next one" chain both reference apps use, because one missed delivery can't break the chain.
7. **OEM battery killers (Samsung, Xiaomi, Huawei, Oppo):** these vendors offer no developer API to opt out ([dontkillmyapp source][dkma]). Do what the reference apps do:
   - Add a "Fix alerts" screen showing the exact-alarm, notification, battery-optimisation and "Pause app activity if unused" states.
   - Deep-link to `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`. Avoid `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, which Play allows only when Doze breaks core function ([Doze guide][doze]).
   - Add per-OEM instructions.
8. **POST_NOTIFICATIONS:** request it in context, for example when the user turns alerts on. Without it the Adhan notification is hidden, though an FGS can still run ([notification permission][notif-perm]).

## Findings

### 1. `SCHEDULE_EXACT_ALARM` vs `USE_EXACT_ALARM`, and Play policy

**What each permission is**

| | `SCHEDULE_EXACT_ALARM` | `USE_EXACT_ALARM` |
|---|---|---|
| Granted | "Granted by the user" | "Granted automatically" |
| Revocable | Yes: "Both the user and the system can revoke" it; "Apps should confirm that the permission has not been revoked" | "Cannot be revoked by the user" |
| Use cases | "Broader set of use cases" | "Limited use cases"; "Subject to an upcoming Google Play policy" |
| Source | [alarms guide][alarms] | [alarms guide][alarms] |

**Behaviour**

- **Android 14 default:** `SCHEDULE_EXACT_ALARM` "is **no longer being pre-granted to most newly installed apps targeting Android 13 and higher** (will be set to denied by default)". It stays denied after a backup-and-restore onto Android 14 ([A14 change][a14-alarm]).
- **Revocation:** "When the `SCHEDULE_EXACT_ALARM` permission is revoked for your app, your app stops, and all future exact alarms are canceled" ([alarms guide][alarms]).
- **Grant broadcast:** when the permission is granted, the system sends `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`. The app should then reschedule "similar to what your app does when it receives the `ACTION_BOOT_COMPLETED` broadcast" ([alarms guide][alarms]).
- **APIs covered:** without the permission, `setExact()`, `setExactAndAllowWhileIdle()` and `setAlarmClock()` all throw `SecurityException` ([A14 change][a14-alarm]).
- **Who qualifies for `USE_EXACT_ALARM`:** "Calendar or alarm clock apps need to send calendar reminders, wake-up alarms, or alerts when the app is no longer running. These apps can request the `USE_EXACT_ALARM` normal permission". Also: "Apps will not be able to publish a version of their app with this permission in the manifest unless they qualify based on the policy language" ([A14 change][a14-alarm]).
- **Battery-optimisation exemption is not a substitute.** An app on the power allowlist may call `setExact()` and `setExactAndAllowWhileIdle()` without either permission ([A14 change][a14-alarm]). But in AOSP, allowlisted apps get allow-while-idle alarms "without FGS capability". Such an alarm could not start the adhan foreground service ([AOSP AlarmManagerService `setImpl`][ams-setimpl]).
- **Side benefit of `USE_EXACT_ALARM`:** holding it exempts the app from the *restricted* standby bucket. `SCHEDULE_EXACT_ALARM` gives no such exemption ([App Standby][standby]).

**Play policy text.** The Play Console help pages (support.google.com) were not reachable from this session, so the wording below comes from search-result extracts of [Play Console Help: Permissions and APIs that Access Sensitive Information][play-perm], not a direct read. Verify it before relying on it.

- Use of `USE_EXACT_ALARM` is restricted to apps whose core, user-facing function needs precisely timed actions. The examples given are "an alarm or timer app" and "a calendar app that shows event notifications".
- Apps that don't qualify should use `SCHEDULE_EXACT_ALARM`.
- Requests that "do not meet the acceptable use case criteria will be disallowed from publishing on Google Play".
- "Core functionality" means the app is "broken" or unusable without the feature.

The policy says nothing about prayer-times apps either way.

**Evidence from an established prayer app.** Metin Kale's *Prayer Times* (open source, Play-published, `targetSdk 37`) declares **both** `USE_EXACT_ALARM` and `SCHEDULE_EXACT_ALARM` in its main manifest. Its Play flavour doesn't remove either one ([manifest][pt-manifest]). This suggests Play has accepted the declaration for at least one prayer app. It is not proof that it would accept ours.

### 2. Alarm APIs and Doze exemptions

**What the docs say**

- Doze "defers standard AlarmManager alarms, including `setExact()` and `setWindow()`, to the next maintenance window." `setAndAllowWhileIdle()` and `setExactAndAllowWhileIdle()` fire in Doze. "Alarms set with `setAlarmClock()` continue to fire normally. The system exits Doze shortly before those alarms fire" ([Doze guide][doze]).
- Jobs don't run in Doze, so WorkManager tasks don't run either ([Doze guide][doze]). WorkManager is unsuitable for Prayer Times.
- `setExactAndAllowWhileIdle()`: "a reasonable example would be for a calendar notification that should make a sound". When it fires, the app joins the temporary power allowlist "for approximately 10 seconds" ([AlarmManager reference][am-ref]).
- `setAlarmClock()`: "The expectation is that when this alarm triggers, the application will further wake up the device to tell the user about the alarm -- turning on the screen, playing a sound, vibrating". "The system will typically also use the information supplied here to tell the user about this upcoming alarm". "Alarms scheduled via this API will be allowed to start a foreground service even if the app is in the background" ([AlarmManager reference][am-ref]).
- Exact alarms "aren't affected by foreground service launch restrictions" ([alarms guide][alarms]).
- Inexact `set()` and `setWindow()` can be delayed by up to an hour, and windows under 10 minutes are clipped to 10 minutes ([alarms guide][alarms]). Too loose for Prayer Times; use them only as the fallback when the permission is denied.

**Frequency limits: the docs disagree**

The [Doze guide][doze] says "Neither `setAndAllowWhileIdle()` nor `setExactAndAllowWhileIdle()` can fire alarms more than once per nine minutes, per app". The [power-limits page][power] says "While-idle alarms: Limited to 7 per hour".

Current AOSP is more detailed ([AOSP constants][ams-consts], [setImpl][ams-setimpl], [idle policy][ams-idle]):

| Alarm kind | Quota while the device is idle |
|---|---|
| Exact allow-while-idle, app holds an exact-alarm permission | 72 per hour (`DEFAULT_ALLOW_WHILE_IDLE_QUOTA = 72`) |
| Inexact allow-while-idle, or allowlisted without an exact-alarm permission | 7 per hour, i.e. the old "once per 9 min" (`DEFAULT_ALLOW_WHILE_IDLE_COMPAT_QUOTA = 7`) |
| `setAlarmClock()` (`FLAG_WAKE_FROM_IDLE`) | Unrestricted |

The two closest alerts are a Prayer Reminder and its Adhan Alert, a few minutes apart. Under the 72/h quota they are fine. Using `setAlarmClock()` for the Adhan Alert removes the question entirely.

### 3. App Standby buckets

- **Bucket limits on alarms** ([power limits][power]):

  | Bucket | Alarms |
  |---|---|
  | Active | no execution limits |
  | Working set | 10 per hour |
  | Frequent | 2 per hour |
  | Rare | 1 per hour |
  | Restricted | "One alarm per day, either an exact alarm or an inexact alarm" |

- **Exemptions in AOSP:** `isExemptFromAppStandby()` returns true for `setAlarmClock()` alarms and for any `*AllowWhileIdle` alarm. Those alarm kinds skip the per-bucket wakeup quotas ([AOSP][ams-exempt]). The restricted bucket's one-per-day limit is still described in the docs, so avoiding that bucket matters.
- **When an app lands in the restricted bucket:**
  - On Android 13+, after 8 days without interaction, or for excessive broadcasts or bindings.
  - Exemptions include "Apps with active widgets", apps the user set to "unrestricted", and apps granted `USE_EXACT_ALARM` ([App Standby][standby]).
  - Our home-screen widget therefore protects users who have it placed.
- **Hibernation (separate from buckets):** after "a few months" without interaction, an app targeting 12+ "can't run jobs or alerts from the background" and loses runtime permissions ([hibernation][hibernation]).
  - Scheduling alarms does **not** count as usage. Tapping a notification or using the widget does ([hibernation][hibernation]).
  - A user who only hears the adhan and never opens the app could have it hibernated.
  - Offer `IntentCompat.createManageUnusedAppRestrictionsIntent()` ("Pause app activity if unused") on the fix-alerts screen ([hibernation][hibernation]).
- **Force stop:** on Android 15, force-stop "cancels all pending intents", which means our alarms. `BOOT_COMPLETED` is delivered when the user next launches the app ([A15 all apps][a15-all]). The `BOOT_COMPLETED` path therefore also covers force-stop.

### 4. OEM battery savers, and what open-source prayer apps do

**Vendors.** The primary first-party documentation for vendors is thin. The best-maintained public record is the open-source [dontkillmyapp][dkma] project (vendor content in its repo):

- **Samsung:** "After 3 days any unused app will not be able to start from background (e.g. alarms will not work anymore)". The project advises turning off *Adaptive battery* and *Put apps to sleep*. For developers: "No known solution on dev end" ([samsung][dkma-samsung]).
  - The project quotes Samsung's 2023 statement on the Android Developers Blog: "Since One UI 6.0, foreground services of apps targeting Android 14 will be guaranteed to work as intended so long as they are developed according to Android's new foreground service API policy." The original blog host was not reachable from this session.
- **Xiaomi (MIUI/HyperOS):** "There are no APIs and no documentation for those extensions. In default settings, background processing simply does not work right". The project points to a third-party library that detects the *Autostart* state ([xiaomi][dkma-xiaomi]).
- **Huawei (EMUI):** a bundled killer (HwPFWService / PowerGenie) "kills everything not whitelisted" on some versions ([huawei][dkma-huawei]).
- **Oppo:** limited data, "No known solution on the dev end" ([oppo][dkma-oppo]).

**Metin Kale – *Prayer Times*** ([repo @93b9e5b, 2026-11-08][pt-repo]; `targetSdk`/`compileSdk 37`):

- Scheduling ([`MyAlarmManager.kt`][pt-alarmmgr]):
  - uses `setAlarmClock()` if the user turned on the "use alarm" preference
  - otherwise `setExactAndAllowWhileIdle()` when `canScheduleExactAlarms()` is true
  - otherwise falls back to `setAndAllowWhileIdle()`
- The alarm's `PendingIntent` is `PendingIntent.getForegroundService(...)` pointing at a `mediaPlayback` FGS. **Only the next alarm is scheduled** ([`AlarmService.kt`][pt-alarmsvc]; [manifest][pt-manifest]).
- The user picks the audio stream: alarm, ring, notification or media ([`MyPlayer.kt`][pt-player]).
- Reschedules on `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED` and `DATE_CHANGED` ([manifest][pt-manifest]). Two cautions if we copy this:
  - It also lists `"android.intent.action.ACTION_LOCKED_BOOT_COMPLETED"`, which is not the real action string. The real one is `android.intent.action.LOCKED_BOOT_COMPLETED` ([Direct Boot][directboot]).
  - `DATE_CHANGED` is not on the manifest-receiver exception list ([broadcast exceptions][bcast-ex]).
- Battery: opens `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` and does not request a direct exemption ([`BatteryOptimizations.kt`][pt-battery]).
- Uses `specialUse` FGSs, with a written justification, only for its once-a-minute countdown widget and notification ([manifest][pt-manifest]).

**meypod – *Al-Azan*** (React Native with Notifee; [repo @4e7af09, 2026-06-25][aa-repo]; `targetSdk 34`):

- **Defaults to `SET_ALARM_CLOCK`** for the adhan and for reminders; `SET_EXACT_AND_ALLOW_WHILE_IDLE` is a user option ([`set_next_adhan.ts`][aa-next]).
- Posts a `CATEGORY_ALARM`, `IMPORTANCE_HIGH` notification as a foreground service. Uses a full-screen intent into an `AlarmActivity` ([`set_alarm.ts`][aa-setalarm]).
- Plays with `USAGE_ALARM`, or `USAGE_MEDIA` when an external device is preferred. Requests audio focus on `STREAM_ALARM` ([`MediaPlayerService.java`][aa-player]).
- Has an optional bypass-DND channel and declares `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `USE_FULL_SCREEN_INTENT` and `SCHEDULE_EXACT_ALARM`. Its FGS is `mediaPlayback` ([manifest][aa-manifest]).
- A "Fix common problems" screen shows the battery-optimisation status, opens the OEM power manager, gives Samsung "Device Care → Apps that won't be put to sleep" steps, and links to dontkillmyapp.com ([screen][aa-fix]).

### 5. Rescheduling: reboot, time change, time zone, travel

- **Alarms don't survive shutdown.** "By default, all alarms are canceled when a device shuts down". Use `RECEIVE_BOOT_COMPLETED`, which "only works if the app has already been launched by the user at least once" ([alarms guide][alarms]).
- **Manifest receivers still allowed for apps targeting 26+** ([broadcast exceptions][bcast-ex]):
  - `LOCKED_BOOT_COMPLETED` and `BOOT_COMPLETED` ("to schedule jobs and alarms")
  - `TIME_SET`, `TIMEZONE_CHANGED` and `NEXT_ALARM_CLOCK_CHANGED` ("Clock apps might need to receive these broadcasts to update alarms when the time, timezone, or alarms change")
  - `LOCALE_CHANGED`
  - `ACTION_PACKAGE_REPLACED` is *not* exempt. The app's own `ACTION_MY_PACKAGE_REPLACED` is targeted at the app and appears among the FGS-start exemptions ([FGS restrictions][fgs-bg]).
- **Direct Boot.** After a reboot, before the first unlock, apps don't run unless their components are `directBootAware`. "Apps that have scheduled notifications, such as alarm clock apps" are the named use case ([Direct Boot][directboot]).
  - To schedule from `LOCKED_BOOT_COMPLETED`, the Prayer Times inputs (location, Calculation Method, Asr Convention, alert settings) must be readable from **device-protected storage**.
  - Otherwise a phone rebooted overnight and not unlocked misses Fajr.
- **RTC semantics.** `RTC_WAKEUP` (and `setAlarmClock()`, which "implies `RTC_WAKEUP`") is UTC wall-clock time ([AlarmManager reference][am-ref]).
  - On a kernel time change AOSP re-evaluates RTC alarms and broadcasts `ACTION_TIME_CHANGED` ([AOSP][ams-timechange]).
  - A time-zone change doesn't move an alarm's UTC instant. A Prayer Time is a UTC instant for a location, so a time-zone change alone needs no re-plan; re-plan anyway to refresh the display.
- **Travel.** **Travel is a location change, not a time-zone change.** Android sends no broadcast for "the user moved city". Re-plan when the app opens and when the user's saved location changes. `TIMEZONE_CHANGED` is a useful hint that the user may have travelled (an open product question, since we work offline with no backend).
- **Exact-alarm grant.** Re-plan on `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` ([alarms guide][alarms]).

### 6. How many alarms ahead

- There is no official guidance. Each alarm needs a distinct `PendingIntent` (request code), because "When you set a second alarm that uses the same pending intent, it replaces the original alarm" ([alarms guide][alarms]).
- Hard cap: `DEFAULT_MAX_ALARMS_PER_UID = 500` ([AOSP][ams-consts]). 48 h of five Adhan Alerts plus five Prayer Reminders is about 20 alarms.
- Both reference apps chain "next one only" ([Prayer Times][pt-alarmsvc], [Al-Azan][aa-next]). A rolling window that is rebuilt idempotently is more fault-tolerant.

### 7. Playing a full-length adhan

**Option A: notification-channel sound (system plays it)**

- AOSP's `NotificationAttentionHelper.playSound()` hands the URI to the system ringtone player with the channel's `AudioAttributes`. It loops only if `FLAG_INSISTENT` is set, and we found **no length cap** in that code. It doesn't play if the stream volume is 0, if another app holds exclusive audio focus, or during a call ([AOSP NAH][aosp-nah-play], [call check][aosp-nah-call]).
- The sound stops when the notification is cleared ([AOSP][aosp-nah-clear]).
- Channel behaviour, sound included, is fixed once created: "After you create a notification channel, you can't change the notification behaviors. The user has complete control at that point" ([channels][channels]).
  - `setSound()` is "Only modifiable before the channel is submitted". Avoid `android.resource://` URIs, "as resource ids can change on app upgrade" ([NotificationChannel][nc-ref]).
  - A user-selectable adhan therefore means one channel per adhan sound, or deleting and recreating channels. Deleted channels are counted in Settings ([channels][channels]).
- Android 15+ "polite notifications" cooldown lowers the volume of bursts from one app, but exempts `CATEGORY_ALARM` ([AOSP][aosp-nah-polite]).
- Verdict: fine for short Prayer Reminder sounds. Poor for a 2–4 min adhan: there is no Stop button or progress, and the sound is tied to the channel.

**Option B: foreground service plus our own player (recommended for the Adhan Alert)**

- **Type and permission.** Android 14+ requires a type for each FGS. `mediaPlayback` ("Continue audio or video playback from the background") needs `FOREGROUND_SERVICE_MEDIA_PLAYBACK` and has no runtime prerequisites ([FGS types][fgs-types]).
- **Starting from the alarm is allowed.** Exemptions include "Your app invokes an exact alarm to complete an action that the user requests", along with time, time-zone and boot broadcasts ([FGS restrictions][fgs-bg]).
- **Android 15+: not from boot.** Apps targeting 15+ "are not allowed to launch a media playback foreground service from a `BOOT_COMPLETED` broadcast receiver" ([A15][a15-fgs]). Never play a missed adhan at boot; just re-plan.
- **Android 15+: audio focus.** The app "must be the top app or running a foreground service in order to request audio focus" ([A15][a15-fgs]).
- **Android 17: background audio hardening (the big one)** ([A17 bg audio][a17-bgaudio]):
  - All apps on Android 17 need "a visible activity or ... a foreground service that is not of type `SHORT_SERVICE`" to play audio, request focus or change volume.
  - Apps targeting 37 also need an FGS with while-in-use (WIU) capability, "However, the requirement for WIU capabilities is waived if the app has been granted the exact alarm permission, and it is making changes to audio streams that have the `USAGE_ALARM` attribute."
  - Failures are silent: "the audio playback and volume change APIs fail silently".
  - FGSs started from the background mostly don't get WIU ([A17 bg audio][a17-bgaudio]). So `USAGE_ALARM` plus an exact-alarm permission is **mandatory** for an alarm-started adhan once we target API 37.
  - Test with `adb shell cmd audio set-enable-hardening throw` ([A17 bg audio][a17-bgaudio]).
- **Other FGS types we ruled out:**
  - `shortService` is "about 3 minutes" and, per the hardening rule above, can't play background audio on Android 17 ([FGS types][fgs-types]).
  - `systemExempted` is "Reserved for system applications", yet lists "Apps holding `SCHEDULE_EXACT_ALARM` or `USE_EXACT_ALARM`" as eligible ([FGS types][fgs-types]). Not recommended without Play guidance.
  - `specialUse` needs a free-form justification reviewed by Play ([FGS types][fgs-types]).
- **Play declaration.** "If your app targets Android 14 or higher, you'll need to declare your app's foreground service types in the Play Console's app content page (Policy > App content)" ([FGS types][fgs-types]). From search extracts of [Play: FGS requirements][play-fgs] (not fetched directly):
  - each declared type needs a description, the user impact if it is deferred or interrupted, and a demo video
  - for media playback, playback must be user-initiated
  - Verify that a user-configured Adhan Alert counts as "user-initiated".
- **Stop control.** `POST_NOTIFICATIONS` isn't needed to *start* an FGS, but the FGS must show a notification ([notification permission][notif-perm]). Put "Stop" on it.

**Do Not Disturb and silent mode (AOSP `AudioService`)** ([AOSP AudioService][aosp-audio])

- **Ringer silent/vibrate:**
  - By default it affects RING, NOTIFICATION, SYSTEM and SYSTEM_ENFORCED.
  - It affects ALARM only if `MUTE_ALARM_STREAM_WITH_RINGER_MODE` is set, which defaults to 0.
  - So `USAGE_ALARM` plays in silent mode, while `USAGE_NOTIFICATION` doesn't.
- **DND:**

  | DND mode | Streams muted |
  |---|---|
  | Total silence | system, notification, ring, **alarm** and music |
  | Alarms only | system, notification and ring |
  | Priority | alarm muted only if `PRIORITY_CATEGORY_ALARMS` is off |

- **Notification bypass:** `NotificationChannel.setBypassDnd()` only works for apps with DND policy access, and only "if the channel hasn't been updated by the user" ([NotificationChannel][nc-ref]).
- **We can't turn DND off.** Apps targeting 15+ "can no longer change the global state or policy of Do Not Disturb" ([A15][a15-fgs]).

**Full-screen intent.** On Android 14+, `USE_FULL_SCREEN_INTENT` is limited to apps "that provide calling and alarms only". Play revokes the default grant for others. Check `canUseFullScreenIntent()` ([A14][a14]). A heads-up `CATEGORY_ALARM` notification works without it.

### 8. POST_NOTIFICATIONS

- It is a runtime permission on Android 13+. "notifications are off by default" for new installs. Apps targeting 13+ choose when to ask, and should ask "in context" ([notification permission][notif-perm]).
- If the user denies it, FGS notices appear only "in the Task Manager" and not in the drawer ([notification permission][notif-perm]). Media-session notifications are exempt.
- Check `areNotificationsEnabled()` before relying on alerts ([notification permission][notif-perm]).

### 9. What changed recently

| Version | Change relevant to alerts | Source |
|---|---|---|
| 12 (API 31) | Exact alarms need "Alarms & reminders" access. Background FGS starts are banned except for exemptions such as exact alarms. Restricted standby bucket added. Hibernation. | [alarms][alarms], [target SDK][target-sdk], [standby][standby] |
| 13 (33) | `POST_NOTIFICATIONS`; `USE_EXACT_ALARM` added. Restricted bucket after 8 idle days. | [notif-perm][notif-perm], [alarms][alarms], [standby][standby] |
| 14 (34) | `SCHEDULE_EXACT_ALARM` denied by default. FGS types required, with Play declaration. Full-screen intent limited to calling/alarm apps. | [a14-alarm][a14-alarm], [a14][a14] |
| 15 (35) | No `mediaPlayback` FGS from `BOOT_COMPLETED`. Audio focus needs top app or FGS. Force-stop cancels pending intents. Apps can't change global DND. | [a15-fgs][a15-fgs], [a15-all][a15-all] |
| 16 (36) | Job quotas apply even to active-bucket apps and FGS-concurrent jobs (WorkManager is affected; alarms are not). Ordered-broadcast priority no longer global. **Play requires target 36 for new apps and updates from 2026-08-31.** | [a16-all][a16-all], [target SDK][target-sdk] |
| 17 (37) | **Background audio hardening:** an alarm-started adhan needs an exact-alarm permission plus `USAGE_ALARM`. Strict `RemoteViews` bitmap memory limit (affects our widget). | [a17-all][a17-all], [a17][a17], [a17-bgaudio][a17-bgaudio] |

## Open questions for the human

1. **`USE_EXACT_ALARM` or `SCHEDULE_EXACT_ALARM`?**
   - `USE_EXACT_ALARM` means no permission screen and no restricted bucket, but it risks Play rejection: the policy names alarm/timer and calendar apps, not prayer apps.
   - `SCHEDULE_EXACT_ALARM` is safe but requires the user to grant access.
   - The common pattern is to declare both: `SCHEDULE_EXACT_ALARM` with `android:maxSdkVersion="32"`, and `USE_EXACT_ALARM` for 33+. Do we file the Play declaration and argue that the Adhan Alert *is* an alarm?
2. **Is `setAlarmClock()` acceptable UX for every Adhan Alert?** The system shows it as the user's next alarm (alarm icon, lock screen). Or should `setExactAndAllowWhileIdle()` be the default, with `setAlarmClock()` as an opt-in "maximum reliability" toggle, as *Prayer Times* does?
3. **Should the adhan play in silent/vibrate mode by default?** `USAGE_ALARM` does; `USAGE_NOTIFICATION` doesn't. Should Prayer Reminders follow the ringer?
4. **Play FGS declaration:** do we accept the `mediaPlayback` declaration plus demo video, and the risk that review reads an alarm-triggered adhan as not "user-initiated"?
5. **Full-screen alarm screen:** do we want an alarm-style screen for the Adhan Alert, given the Android 14 `USE_FULL_SCREEN_INTENT` limit?
6. **Travel:** without a backend or background location, how should the app detect that the user moved? Options: prompt when the app opens or when `TIMEZONE_CHANGED` arrives, or ask for location on open.
7. **Minimum SDK:** what is the support floor? It decides how much of the pre-Android 12 compat path we keep.

## Sources

Android Developers (fetched 2026-11-08):
- [alarms]: https://developer.android.com/develop/background-work/services/alarms/schedule
- [am-ref]: https://developer.android.com/reference/android/app/AlarmManager
- [a14-alarm]: https://developer.android.com/about/versions/14/changes/schedule-exact-alarms
- [a14]: https://developer.android.com/about/versions/14/behavior-changes-14
- [a15-fgs]: https://developer.android.com/about/versions/15/behavior-changes-15
- [a15-all]: https://developer.android.com/about/versions/15/behavior-changes-all
- [a16-all]: https://developer.android.com/about/versions/16/behavior-changes-all
- [a17-all]: https://developer.android.com/about/versions/17/behavior-changes-all
- [a17]: https://developer.android.com/about/versions/17/behavior-changes-17
- [a17-bgaudio]: https://developer.android.com/about/versions/17/changes/bg-audio
- [doze]: https://developer.android.com/training/monitoring-device-state/doze-standby
- [standby]: https://developer.android.com/topic/performance/appstandby
- [power]: https://developer.android.com/topic/performance/power/power-details
- [hibernation]: https://developer.android.com/topic/performance/app-hibernation
- [fgs-types]: https://developer.android.com/develop/background-work/services/fgs/service-types
- [fgs-bg]: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- [bcast-ex]: https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions
- [directboot]: https://developer.android.com/privacy-and-security/direct-boot
- [notif-perm]: https://developer.android.com/develop/ui/views/notifications/notification-permission
- [channels]: https://developer.android.com/develop/ui/views/notifications/channels
- [nc-ref]: https://developer.android.com/reference/android/app/NotificationChannel
- [target-sdk]: https://developer.android.com/google/play/requirements/target-sdk

Google Play policy. These hosts are blocked in this session; the wording came from search-result extracts only, so re-check it:
- [play-perm]: https://support.google.com/googleplay/android-developer/answer/16558241
- [play-exact-preview]: https://support.google.com/googleplay/android-developer/answer/12253906#exact_alarm_preview
- [play-fgs]: https://support.google.com/googleplay/android-developer/answer/13392821

AOSP, read from the `aosp-mirror/platform_frameworks_base` GitHub mirror at commit `1cdfff5`. The mirror may lag internal AOSP.
- [ams-consts]: https://github.com/aosp-mirror/platform_frameworks_base/blob/1cdfff555f4a21f71ccc978290e2e212e2f8b168/apex/jobscheduler/service/java/com/android/server/alarm/AlarmManagerService.java#L728-L757
- [ams-setimpl]: https://github.com/aosp-mirror/platform_frameworks_base/blob/1cdfff555f4a21f71ccc978290e2e212e2f8b168/apex/jobscheduler/service/java/com/android/server/alarm/AlarmManagerService.java#L2750-L2830
- [ams-idle]: https://github.com/aosp-mirror/platform_frameworks_base/blob/1cdfff555f4a21f71ccc978290e2e212e2f8b168/apex/jobscheduler/service/java/com/android/server/alarm/AlarmManagerService.java (`adjustDeliveryTimeBasedOnDeviceIdle`)
- [ams-exempt]: https://github.com/aosp-mirror/platform_frameworks_base/blob/1cdfff555f4a21f71ccc978290e2e212e2f8b168/apex/jobscheduler/service/java/com/android/server/alarm/AlarmManagerService.java#L4328-L4331
- [ams-timechange]: https://github.com/aosp-mirror/platform_frameworks_base/blob/1cdfff555f4a21f71ccc978290e2e212e2f8b168/apex/jobscheduler/service/java/com/android/server/alarm/AlarmManagerService.java#L4480-L4500
- [aosp-audio]: https://github.com/aosp-mirror/platform_frameworks_base/blob/1cdfff555f4a21f71ccc978290e2e212e2f8b168/services/core/java/com/android/server/audio/AudioService.java#L7473-L7580
- [aosp-nah-play]: https://github.com/aosp-mirror/platform_frameworks_base/blob/1cdfff555f4a21f71ccc978290e2e212e2f8b168/services/core/java/com/android/server/notification/NotificationAttentionHelper.java#L729-L766
- [aosp-nah-call]: https://github.com/aosp-mirror/platform_frameworks_base/blob/1cdfff555f4a21f71ccc978290e2e212e2f8b168/services/core/java/com/android/server/notification/NotificationAttentionHelper.java#L1052-L1054
- [aosp-nah-clear]: https://github.com/aosp-mirror/platform_frameworks_base/blob/1cdfff555f4a21f71ccc978290e2e212e2f8b168/services/core/java/com/android/server/notification/NotificationAttentionHelper.java#L936-L947
- [aosp-nah-polite]: https://github.com/aosp-mirror/platform_frameworks_base/blob/1cdfff555f4a21f71ccc978290e2e212e2f8b168/services/core/java/com/android/server/notification/NotificationAttentionHelper.java#L265-L311

Open-source apps and OEM data:
- [pt-repo]: https://github.com/metinkale38/prayer-times-android/tree/93b9e5bf51145ae4f3ce1fd91da89fbec495facf
- [pt-manifest]: https://github.com/metinkale38/prayer-times-android/blob/93b9e5bf51145ae4f3ce1fd91da89fbec495facf/app/src/main/AndroidManifest.xml
- [pt-alarmmgr]: https://github.com/metinkale38/prayer-times-android/blob/93b9e5bf51145ae4f3ce1fd91da89fbec495facf/app/src/main/java/com/metinkale/prayer/MyAlarmManager.kt
- [pt-alarmsvc]: https://github.com/metinkale38/prayer-times-android/blob/93b9e5bf51145ae4f3ce1fd91da89fbec495facf/app/src/main/java/com/metinkale/prayer/times/alarm/AlarmService.kt
- [pt-player]: https://github.com/metinkale38/prayer-times-android/blob/93b9e5bf51145ae4f3ce1fd91da89fbec495facf/app/src/main/java/com/metinkale/prayer/times/alarm/sounds/MyPlayer.kt
- [pt-battery]: https://github.com/metinkale38/prayer-times-android/blob/93b9e5bf51145ae4f3ce1fd91da89fbec495facf/app/src/main/java/com/metinkale/prayer/times/utils/BatteryOptimizations.kt
- [aa-repo]: https://github.com/meypod/al-azan/tree/4e7af099b9e1ec5cb67dc52938ea3e91998257bf
- [aa-manifest]: https://github.com/meypod/al-azan/blob/4e7af099b9e1ec5cb67dc52938ea3e91998257bf/android/app/src/main/AndroidManifest.xml
- [aa-next]: https://github.com/meypod/al-azan/blob/4e7af099b9e1ec5cb67dc52938ea3e91998257bf/src/tasks/set_next_adhan.ts
- [aa-setalarm]: https://github.com/meypod/al-azan/blob/4e7af099b9e1ec5cb67dc52938ea3e91998257bf/src/tasks/set_alarm.ts
- [aa-player]: https://github.com/meypod/al-azan/blob/4e7af099b9e1ec5cb67dc52938ea3e91998257bf/android/app/src/main/java/com/github/meypod/al_azan/modules/MediaPlayerService.java
- [aa-fix]: https://github.com/meypod/al-azan/blob/4e7af099b9e1ec5cb67dc52938ea3e91998257bf/src/screens/settings_fix_common_problems/index.tsx
- [dkma]: https://github.com/urbandroid-team/dont-kill-my-app/tree/3616bc8b3b6b3910ac204e107ff0dfd6064f877c
- [dkma-samsung]: https://github.com/urbandroid-team/dont-kill-my-app/tree/3616bc8b3b6b3910ac204e107ff0dfd6064f877c/_vendors-content/samsung
- [dkma-xiaomi]: https://github.com/urbandroid-team/dont-kill-my-app/tree/3616bc8b3b6b3910ac204e107ff0dfd6064f877c/_vendors-content/xiaomi
- [dkma-huawei]: https://github.com/urbandroid-team/dont-kill-my-app/tree/3616bc8b3b6b3910ac204e107ff0dfd6064f877c/_vendors-content/huawei
- [dkma-oppo]: https://github.com/urbandroid-team/dont-kill-my-app/tree/3616bc8b3b6b3910ac204e107ff0dfd6064f877c/_vendors-content/oppo

[alarms]: https://developer.android.com/develop/background-work/services/alarms/schedule
[am-ref]: https://developer.android.com/reference/android/app/AlarmManager
[a14-alarm]: https://developer.android.com/about/versions/14/changes/schedule-exact-alarms
[a14]: https://developer.android.com/about/versions/14/behavior-changes-14
[a15-fgs]: https://developer.android.com/about/versions/15/behavior-changes-15
[a15-all]: https://developer.android.com/about/versions/15/behavior-changes-all
[a16-all]: https://developer.android.com/about/versions/16/behavior-changes-all
[a17-all]: https://developer.android.com/about/versions/17/behavior-changes-all
[a17]: https://developer.android.com/about/versions/17/behavior-changes-17
[a17-bgaudio]: https://developer.android.com/about/versions/17/changes/bg-audio
[doze]: https://developer.android.com/training/monitoring-device-state/doze-standby
[standby]: https://developer.android.com/topic/performance/appstandby
[power]: https://developer.android.com/topic/performance/power/power-details
[hibernation]: https://developer.android.com/topic/performance/app-hibernation
[fgs-types]: https://developer.android.com/develop/background-work/services/fgs/service-types
[fgs-bg]: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
[bcast-ex]: https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions
[directboot]: https://developer.android.com/privacy-and-security/direct-boot
[notif-perm]: https://developer.android.com/develop/ui/views/notifications/notification-permission
[channels]: https://developer.android.com/develop/ui/views/notifications/channels
[nc-ref]: https://developer.android.com/reference/android/app/NotificationChannel
[target-sdk]: https://developer.android.com/google/play/requirements/target-sdk
[play-perm]: https://support.google.com/googleplay/android-developer/answer/16558241
[play-fgs]: https://support.google.com/googleplay/android-developer/answer/13392821
[ams-consts]: https://github.com/aosp-mirror/platform_frameworks_base/blob/1cdfff555f4a21f71ccc978290e2e212e2f8b168/apex/jobscheduler/service/java/com/android/server/alarm/AlarmManagerService.java#L728-L757
[ams-setimpl]: https://github.com/aosp-mirror/platform_frameworks_base/blob/1cdfff555f4a21f71ccc978290e2e212e2f8b168/apex/jobscheduler/service/java/com/android/server/alarm/AlarmManagerService.java#L2750-L2830
[ams-idle]: https://github.com/aosp-mirror/platform_frameworks_base/blob/1cdfff555f4a21f71ccc978290e2e212e2f8b168/apex/jobscheduler/service/java/com/android/server/alarm/AlarmManagerService.java
[ams-exempt]: https://github.com/aosp-mirror/platform_frameworks_base/blob/1cdfff555f4a21f71ccc978290e2e212e2f8b168/apex/jobscheduler/service/java/com/android/server/alarm/AlarmManagerService.java#L4328-L4331
[ams-timechange]: https://github.com/aosp-mirror/platform_frameworks_base/blob/1cdfff555f4a21f71ccc978290e2e212e2f8b168/apex/jobscheduler/service/java/com/android/server/alarm/AlarmManagerService.java#L4480-L4500
[aosp-audio]: https://github.com/aosp-mirror/platform_frameworks_base/blob/1cdfff555f4a21f71ccc978290e2e212e2f8b168/services/core/java/com/android/server/audio/AudioService.java#L7473-L7580
[aosp-nah-play]: https://github.com/aosp-mirror/platform_frameworks_base/blob/1cdfff555f4a21f71ccc978290e2e212e2f8b168/services/core/java/com/android/server/notification/NotificationAttentionHelper.java#L729-L766
[aosp-nah-call]: https://github.com/aosp-mirror/platform_frameworks_base/blob/1cdfff555f4a21f71ccc978290e2e212e2f8b168/services/core/java/com/android/server/notification/NotificationAttentionHelper.java#L1052-L1054
[aosp-nah-clear]: https://github.com/aosp-mirror/platform_frameworks_base/blob/1cdfff555f4a21f71ccc978290e2e212e2f8b168/services/core/java/com/android/server/notification/NotificationAttentionHelper.java#L936-L947
[aosp-nah-polite]: https://github.com/aosp-mirror/platform_frameworks_base/blob/1cdfff555f4a21f71ccc978290e2e212e2f8b168/services/core/java/com/android/server/notification/NotificationAttentionHelper.java#L265-L311
[pt-repo]: https://github.com/metinkale38/prayer-times-android/tree/93b9e5bf51145ae4f3ce1fd91da89fbec495facf
[pt-manifest]: https://github.com/metinkale38/prayer-times-android/blob/93b9e5bf51145ae4f3ce1fd91da89fbec495facf/app/src/main/AndroidManifest.xml
[pt-alarmmgr]: https://github.com/metinkale38/prayer-times-android/blob/93b9e5bf51145ae4f3ce1fd91da89fbec495facf/app/src/main/java/com/metinkale/prayer/MyAlarmManager.kt
[pt-alarmsvc]: https://github.com/metinkale38/prayer-times-android/blob/93b9e5bf51145ae4f3ce1fd91da89fbec495facf/app/src/main/java/com/metinkale/prayer/times/alarm/AlarmService.kt
[pt-player]: https://github.com/metinkale38/prayer-times-android/blob/93b9e5bf51145ae4f3ce1fd91da89fbec495facf/app/src/main/java/com/metinkale/prayer/times/alarm/sounds/MyPlayer.kt
[pt-battery]: https://github.com/metinkale38/prayer-times-android/blob/93b9e5bf51145ae4f3ce1fd91da89fbec495facf/app/src/main/java/com/metinkale/prayer/times/utils/BatteryOptimizations.kt
[aa-repo]: https://github.com/meypod/al-azan/tree/4e7af099b9e1ec5cb67dc52938ea3e91998257bf
[aa-manifest]: https://github.com/meypod/al-azan/blob/4e7af099b9e1ec5cb67dc52938ea3e91998257bf/android/app/src/main/AndroidManifest.xml
[aa-next]: https://github.com/meypod/al-azan/blob/4e7af099b9e1ec5cb67dc52938ea3e91998257bf/src/tasks/set_next_adhan.ts
[aa-setalarm]: https://github.com/meypod/al-azan/blob/4e7af099b9e1ec5cb67dc52938ea3e91998257bf/src/tasks/set_alarm.ts
[aa-player]: https://github.com/meypod/al-azan/blob/4e7af099b9e1ec5cb67dc52938ea3e91998257bf/android/app/src/main/java/com/github/meypod/al_azan/modules/MediaPlayerService.java
[aa-fix]: https://github.com/meypod/al-azan/blob/4e7af099b9e1ec5cb67dc52938ea3e91998257bf/src/screens/settings_fix_common_problems/index.tsx
[dkma]: https://github.com/urbandroid-team/dont-kill-my-app/tree/3616bc8b3b6b3910ac204e107ff0dfd6064f877c
[dkma-samsung]: https://github.com/urbandroid-team/dont-kill-my-app/tree/3616bc8b3b6b3910ac204e107ff0dfd6064f877c/_vendors-content/samsung
[dkma-xiaomi]: https://github.com/urbandroid-team/dont-kill-my-app/tree/3616bc8b3b6b3910ac204e107ff0dfd6064f877c/_vendors-content/xiaomi
[dkma-huawei]: https://github.com/urbandroid-team/dont-kill-my-app/tree/3616bc8b3b6b3910ac204e107ff0dfd6064f877c/_vendors-content/huawei
[dkma-oppo]: https://github.com/urbandroid-team/dont-kill-my-app/tree/3616bc8b3b6b3910ac204e107ff0dfd6064f877c/_vendors-content/oppo
