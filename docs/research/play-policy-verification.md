# Research: verifying the Google Play policy claims (exact alarms, FGS, location)

Researched 2026-11-08. Terms follow [GLOSSARY.md](../../GLOSSARY.md).

This note checks the Play policy claims marked unverified in [reliable-alerts.md](reliable-alerts.md) and [location.md](location.md). Every `support.google.com` and `developer.android.com` page cited below was fetched directly in this session (English, `?hl=en`). Quotes are copied from the fetched pages. Those two files are not edited here; where they are wrong or out of date, this note says so.

## Question

1. May MyPrayers declare `USE_EXACT_ALARM`, or must it use `SCHEDULE_EXACT_ALARM` with a user grant? Which app types qualify, and what counts as an "alarm clock" app? Is there a Play Console declaration?
2. What does Play require for a `mediaPlayback` foreground service (FGS), including the demo video? Does a 2–4 minute adhan for the Adhan Alert, started from an exact alarm, fit the allowed use cases?
3. If location never leaves the device, is "not collected" correct on the Data safety form? Does Android Auto Backup to the user's Google Drive change that? Does the prominent-disclosure rule apply to foreground-only approximate location?
4. Are there Play policies specific to religious apps or to apps with alarms that we should know about?

## Short answer

1. **Use `SCHEDULE_EXACT_ALARM` with a user grant flow. Do not ship `USE_EXACT_ALARM` unless the human accepts a real rejection risk.** Play reserves `USE_EXACT_ALARM` for apps whose core function is an alarm, timer or calendar. Prayer and religious apps are not named. No Play page defines "alarm clock app". Declaring `USE_EXACT_ALARM` triggers a Play Console declaration and review, and an app that fails "will be disallowed from publishing".
2. **Each FGS type needs a Play Console declaration: a description, the user impact if the task is deferred or interrupted, a demo video, and a chosen use case.** The adhan fits the binding policy criteria: it is "user perceptible" (audio), stoppable, and cannot be deferred. But the help page's `mediaPlayback` video row says "Playback must be user-initiated". An adhan started by an alarm is a grey area that a reviewer could reject. The video should show the user turning on the Adhan Alert and then the alert playing.
3. **"Not collected" is correct for location that is processed only on the device.** Play's definition is: "'Collect' means transmitting data from your app off a user's device", and "on-device access/processing … does not need to be disclosed". Auto Backup is not addressed by name. The closest Play FAQ says a user's own Google Drive backup need not be declared if "your app never collects or accesses the data". Auto Backup is also end-to-end encrypted on Android 9+ with a screen lock. Lowest-risk choice: exclude the stored location from cloud backup. Prominent disclosure applies only where use "may not be within the reasonable expectation of the user". Foreground coarse location for Prayer Times is likely expected, but a short in-app explanation before the permission prompt is cheap insurance.
4. **No religion-specific Play policy was found.** The relevant policies are: the full-screen intent limit (auto-granted only to alarm and calling apps), the battery-optimisation exemption rule, the Android 17 location-button and fine-location declaration (effective 2027-01-27, does not apply if we stay coarse-only), and the Data safety type "Political or religious beliefs". The Prayer Log could count as that type if it ever left the device.

## Findings

### 1. `USE_EXACT_ALARM` vs `SCHEDULE_EXACT_ALARM`

**Play policy, "Exact Alarm Permission"** (in *Permissions and APIs that Access Sensitive Information*, https://support.google.com/googleplay/android-developer/answer/16558241):

> "USE_EXACT_ALARM permission on Android 13+ is a highly restricted permission used only for apps whose core, user-facing functionality genuinely requires precise timing, like dedicated alarm, timer, or calendar applications with event notifications. If your app does not have this specific core need, consider using SCHEDULE_EXACT_ALARM permission instead. It provides the same functionality but access must be granted by the user."

> "USE_EXACT_ALARM is a restricted permission and apps must only declare this permission if their core functionality supports the need for an exact alarm. Apps that request this restricted permission are subject to review, and those that do not meet the acceptable use case criteria will be disallowed from publishing on Google Play."

> "Your app must use the USE_EXACT_ALARM functionality only when your app's core, user facing functionality requires precisely-timed actions, such as:
> - The app is an alarm or timer app.
> - The app is a calendar app that shows event notifications.
>
> If you have a use case for exact alarm functionality that's not covered above, you should evaluate if using SCHEDULE_EXACT_ALARM as an alternative is an option."

Key considerations on the same page:

> "Request the auto granted version of the permission, USE_EXACT_ALARM, only if your app's core functionality is of alarm or calendar."
> "Don't use this permission for non-critical features that do not directly contribute to the app's main purpose."
> "Use SCHEDULE_EXACT_ALARM instead if the above criteria is not met."
> "Complete Play Console declaration to indicate app functionality."

The policy preview (https://support.google.com/googleplay/android-developer/answer/16909972) has the same Exact Alarm text. No change to it is announced.

**Android docs** use the term "alarm clock":

- https://developer.android.com/about/versions/13/features#use-exact-alarm-permission: "it must satisfy at least one of the following criteria: Your app is an alarm clock app or a timer app. Your app is a calendar app that shows notifications for upcoming events." Also: "If your app sets exact alarms but doesn't satisfy either case shown in the previous list, continue to declare the SCHEDULE_EXACT_ALARM permission instead, and be prepared for the situation where the user denies access to your app."
- https://developer.android.com/about/versions/14/changes/schedule-exact-alarms: "Calendar or alarm clock apps need to send calendar reminders, wake-up alarms, or alerts when the app is no longer running." And: "Apps will not be able to publish a version of their app with this permission in the manifest unless they qualify based on the policy language."
- https://developer.android.com/develop/background-work/services/alarms: `USE_EXACT_ALARM` is "Granted automatically", "Cannot be revoked by the user", "Subject to an upcoming Google Play policy", "Limited use cases". `SCHEDULE_EXACT_ALARM` is "Granted by the user" and has a "Broader set of use cases".

**What counts as an "alarm clock" app?** Neither the Play policy nor the Android pages above define it. The Play wording is "an alarm or timer app" and "core functionality is of alarm or calendar". The "such as" list is non-exhaustive, but the policy sends any use case "not covered above" to `SCHEDULE_EXACT_ALARM`. Our core function is showing Prayer Times and logging Prayers. The Adhan Alert is alarm-like, but the app is not an alarm clock app in the plain sense. Prayer and religious apps are not named.

**Declaration form.** The policy says to complete a Play Console declaration. Its links point to the Permissions Declaration Form, described in https://support.google.com/googleplay/android-developer/answer/9214102. That form needs the app's core functionality chosen "from the list of supported use cases", review instructions and "a video demonstration". Review "may require up to several weeks", and the release stays pending until it is reviewed. (The policy's `goo.gle/play-permission-decl-form` short link was blocked in this session, so its exact target was not confirmed.)

**Recommendation and risk**

| Option | Benefit | Risk |
|---|---|---|
| `SCHEDULE_EXACT_ALARM` only (recommended) | Policy-safe. No restricted-permission review. | Denied by default on Android 14+ for new installs. Needs a grant flow, re-planning on `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`, and a degraded path if the user refuses or revokes the grant. |
| `USE_EXACT_ALARM` (plus `SCHEDULE_EXACT_ALARM` with `maxSdkVersion="32"`) | Granted at install, cannot be revoked. Also exempts the app from the restricted standby bucket (per reliable-alerts.md). | Release goes into extended review. A reviewer can reject it, which blocks publishing until the permission is removed. Even after approval, later enforcement is possible because the use case is not named in the policy. |

**Verdict on earlier claims**

- reliable-alerts.md §1, "Play limits it to alarm-clock, timer and calendar apps" and "The policy text names neither prayer apps nor religious apps": **holds**. Note that Play says "alarm or timer app"; "alarm clock" is the Android docs' wording.
- reliable-alerts.md §1 "Play policy text" extracts ("an alarm or timer app", "a calendar app that shows event notifications", non-qualifying apps "should use `SCHEDULE_EXACT_ALARM`"): **hold**, now verified word for word.
- reliable-alerts.md cites `answer/12253906#exact_alarm_preview`. **This link is out of date.** That page is now "Policy Deadlines" and no longer mentions exact alarms. Use `answer/16558241`.
- The Metin Kale prayer app shipping `USE_EXACT_ALARM` shows only that one app got through. It is not policy, as reliable-alerts.md already says.

### 2. `mediaPlayback` foreground service for the adhan

**Binding policy: "Permissions for Foreground Services (FGS)"** (in *Device and Network Abuse*, https://support.google.com/googleplay/android-developer/answer/13315670#foreground_service):

> "For apps targeting Android 14+ you must declare valid Foreground Service (FGS) types in the manifest and Play Console, providing descriptions, user impact, and a demo video justifying their use based on user-initiated, perceptible actions."

> "Apps are only allowed to declare a foreground service permission if the use:
> - provides a feature that is beneficial to the user and relevant to the core functionality of the app
> - is initiated by the user or is user perceptible (for example, audio from playing a song, cast media to another device, accurate and clear user notification, user request to upload a photo to the cloud)
> - can be terminated or stopped by the user
> - can't be interrupted or deferred by the system without causing a negative user experience or causing the user anticipated feature to not work as intended (for example, a phone call needs to start immediately and can't be deferred by the system)
> - runs only for as long as necessary to complete the task"

> "The following foreground service use cases are exempt from the above criteria:
> - foreground service types systemExempted or shortService;"

**Declaration details** (*Understanding foreground service and full-screen intent requirements*, https://support.google.com/googleplay/android-developer/answer/13392821):

> "When your apps target Android 14 and above, you'll need to declare any foreground service types that you use in a new declaration on the App content page (Monitor and improve > App content) in Play Console.
> For each foreground service type you declare, you'll need to do the following:
> - Provide a description of the app functionality that is using each foreground service type.
> - Describe the user impact if: the task is deferred by the system (does not start immediately); and/or the task is interrupted by the system (paused and/or restarted).
> - Include a link to a video demonstrating each foreground service feature. The video should demonstrate the steps the user needs to take in your app in order to trigger the feature.
> - Choose your specific use case for each foreground service type. A pre-set list of use cases will be provided for you to choose from. … if you do not see your use case listed, you can enter your use case manually."

The `TYPE_MEDIA_PLAYBACK` row:

> Use case "Media Playback": "Continue audio or video playback from the background, including streaming." Video must show: "Show a user starting video or audio playback. Playback must be user-initiated; any automatic soundtracks that play when you open an app do not qualify (for example, background game soundtracks)."

The same page's FAQ defines the phrase: "User perceptible means that the user should be aware that a foreground service task is running on their device. Users can be considered aware if they initiate the action themselves … Your app can also make users aware of an ongoing foreground service by presenting a clear and accurate notification". It also notes that "All foreground service types are subject to review."

The preview version (https://support.google.com/googleplay/android-developer/answer/16965181) only removes geofencing as an FGS use case from 2027-01-27. Media playback is unchanged.

**Android docs.** `mediaPlayback` needs `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, has "Runtime prerequisites: None", and the description "Continue audio or video playback from the background." Apps targeting 15+ may not start it from `BOOT_COMPLETED` (https://developer.android.com/develop/background-work/services/fgs/service-types). Starting an FGS from the background is allowed when "Your app invokes an exact alarm to complete an action that the user requests" (https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start).

**Does the Adhan Alert fit?**

- Against the binding policy criteria: **yes**. The adhan is core to the app, user perceptible ("audio from playing a song" is the policy's own example), stoppable from the notification, cannot be deferred (an adhan played late is wrong), and stops when the audio ends.
- Against the help-page video row: **grey area**. The user does not press play at the moment the adhan starts. They turn on the Adhan Alert in advance and the exact alarm starts playback. The "do not qualify" example is about soundtracks that play automatically on app open, which is a different case. Android's own exemption language ("an exact alarm to complete an action that the user requests") supports reading it as user-requested.
- Mitigations for review: choose or write the use case as "Adhan Alert: plays the user-selected call to prayer at a Prayer Time the user enabled". Describe the impact of deferral as "the adhan plays late, after the Prayer Time". In the video, show the user turning on the Adhan Alert for a Prayer, the alert firing at the scheduled time with a notification and a Stop action, and the user stopping it.
- `systemExempted` is exempt from the Play criteria, and Android lists "Apps holding SCHEDULE_EXACT_ALARM or USE_EXACT_ALARM permission" as eligible. But Android describes it as "Reserved for system applications and specific system integrations", so declaring it is still a review risk. It also has no stated media-playback purpose. This note does not recommend it.

**Verdict on earlier claims**

- reliable-alerts.md "each declared type needs a description, the user impact if it is deferred or interrupted, and a demo video": **holds**. Add: a use case is also chosen from a list, or written in.
- "for media playback, playback must be user-initiated": **holds** as the help page's video requirement. The binding policy is looser: "initiated by the user **or** is user perceptible".
- The Play Console menu path is now "Monitor and improve > App content" on the Play help page. The Android docs still say "Policy > App content". This is a label difference only.

### 3. Location, Data safety and prominent disclosure

**Definition of collection** (*Provide information for Google Play's Data safety section*, https://support.google.com/googleplay/android-developer/answer/10787469):

> "'Collect' means transmitting data from your app off a user's device."

> "Not in scope for data collection. The following use cases do not need to be disclosed as collected:
> - On-device access/processing: User data accessed by your app that is only processed locally on the user's device and not sent off device does not need to be disclosed.
> - End-to-end encryption: User data that is sent off device, but that is unreadable by you or anyone other than the sender and recipient as a result of end-to-end encryption does not need to be disclosed."

> "Do I need to declare data if my app includes a permission but does not actually collect or share the data? You do not need to declare collection or sharing unless data is actually collected and/or shared."

> "Even developers with apps that do not collect any user data must complete this form and provide a link to their privacy policy."

Sharing includes "On-device transfer to another app … even if your app does not transmit the data off the user's device." Ephemeral processing applies only to "User data transmitted off device", so it is not our case.

**Auto Backup to Google Drive.** No Play page names Android Auto Backup. The closest Play FAQ (same page):

> "My app enables users to upload their data directly to Google Drive or Dropbox for backup or storage. My app does not access any of this data. Should that still be disclosed as 'collection'? It depends on the particular implementation. If the user chooses to upload their data directly to their own external drive or cloud storage account (such as Google Drive, Dropbox, or similar services) and this upload is governed by the external drive or cloud storage provider's terms of service and privacy policy, and your app never collects or accesses the data in question, then your app does not need to declare the collection of this data."

Android docs (https://developer.android.com/identity/data/autobackup): "Android preserves app data by uploading it to the user's Google Drive, where it's protected by the user's Google Account credentials. The backup is end-to-end encrypted on devices running Android 9 or higher using the device's PIN, pattern, or password." Also: "Backup data is stored in a private folder in the user's Google Drive account" and "can't be read by the user or other apps on the device." The `disableIfNoEncryptionCapabilities` flag in `<cloud-backup>` makes sure "the backup happens only if it can be encrypted".

Reading: Auto Backup is done by the OS to the user's own account, and the developer never receives the data. With client-side encryption it also matches the end-to-end encryption exclusion. So it very likely does not make the location "collected". The fit is not exact: the FAQ says "the user chooses" and Auto Backup is on by default, and devices without a screen lock are not encrypted. The cleanest option is to exclude the stored location, and optionally the Prayer Log, from `<cloud-backup>` (keeping device-to-device transfer). The other option is `disableIfNoEncryptionCapabilities="true"`. Either removes the doubt.

**Prominent disclosure** (*User Data*, https://support.google.com/googleplay/android-developer/answer/10144311):

> "In cases where your app's access, collection, use, or sharing of personal and sensitive user data may not be within the reasonable expectation of the user of the product or feature in question (for example, if data collection occurs in the background when the user is not engaging with your app), you must meet the following requirements: …"

When it applies, the disclosure must be in-app, shown "in the normal usage of the app", describe the data and its use, and "Consent and runtime permissions: Requests for in-app user consent and runtime permission requests must be immediately preceded by an in-app disclosure that meets the requirement of this policy." Consent "Must require affirmative user action".

The same page lists "device location" as personal and sensitive user data. Its violation examples include: "An app collects device location but does not have a prominent disclosure explaining which feature uses this data and/or indicates the app's usage in the background." The best-practice page (https://support.google.com/googleplay/android-developer/answer/11150561) says: "The prominent disclosure requirement is not a substitute for an app's privacy policy or Data Safety section". It names "the Background Location Permission" as a case that needs a separate disclosure.

Reading: for foreground-only `ACCESS_COARSE_LOCATION` used to compute Prayer Times, the use is plausibly "within the reasonable expectation of the user". The strict requirement then does not clearly apply. But the violation example uses the word "collects" broadly, and the cost is one screen. Recommendation: before the system prompt, show a short screen, for example "MyPrayers uses your approximate location to calculate Prayer Times. It stays on this phone." Give it an explicit continue and a "choose city instead" option.

**Location permission policy** (https://support.google.com/googleplay/android-developer/answer/16558241, current): "Apps should request the minimum scope necessary (for example, coarse instead of fine, and foreground instead of background)". Background location "may only be used to provide features beneficial to the user and relevant to the core functionality of the app". Key consideration: "Complete the Console declaration for background location."

**Upcoming, effective 2027-01-27** (preview https://support.google.com/googleplay/android-developer/answer/16909972 and guide https://support.google.com/googleplay/android-developer/answer/17033915):

- Preview summary: "Background and foreground location require a Play console declaration."
- Guide: "All apps requesting precise location (ACCESS_FINE_LOCATION) must complete a declaration in the Play Console". The location button "is the required minimum scope method for obtaining precise location for 'transactional' (one-time) use cases."
- Guide, minimum scope: "Precision: Use coarse location (ACCESS_COARSE_LOCATION) if your feature does not strictly require fine-grained accuracy." "Persistence: For one-time or infrequent needs, apps must transition from 'persistent by default' to transactional access via the location button."

**Verdict on earlier claims (location.md)**

- "if location never leaves the device and no SDK sends it anywhere … we need not declare it as 'collected'": **holds**, verified word for word.
- "The page's example is an app that uses location only for on-device features": **wrong**. The Data safety page has no such example. The only location example is the ephemeral-processing weather app, which *does* send location off the device. The on-device exclusion is a one-line rule with no example.
- "Sharing data with another app on the same device can still count as 'sharing'": **holds**.
- Auto Backup "needs checking": now answered above. Probably not collection; excluding it from cloud backup removes the doubt.
- Prominent-disclosure description (before the runtime request, describes data and use, affirmative action, "not a substitute"): **holds**. The trigger is "may not be within the reasonable expectation of the user". location.md quoted "users may not reasonably expect", which is the best-practice page's wording.
- Background location needs a declaration form and review, and location is never for ads or analytics only: **holds**.
- Android 17 location button "does not appear to be in scope" for a coarse-only app: **holds on the full text**. The button and the new declaration both target `ACCESS_FINE_LOCATION`. Two caveats location.md did not have: (a) the preview summary says "Background and foreground location require a Play console declaration", which is broader than the guide's fine-location wording; (b) the "infrequent needs" line is stated generally. location.md cited `answer/16909972` as "the policy page". It is the policy *preview*; the detailed guide is `answer/17033915`.

### 4. Other policies to know (alarms, religious apps)

- **No religion-specific Play policy found.** Religion appears in the hate-speech rules (*Inappropriate Content*, https://support.google.com/googleplay/android-developer/answer/9878810): "We don't allow apps that promote violence, or incite hatred against individuals or groups based on … religion". This matters for any user-generated or quoted content, but not for core features.
- **Data safety type "Political or religious beliefs"**: "Information about a user's political or religious beliefs" (https://support.google.com/googleplay/android-developer/answer/10787469). The Prayer Log is arguably this type. It is out of scope while it stays on the device, but any future sync, backup to our server, analytics or crash SDK would make it a sensitive declared type. This is another reason to exclude it from cloud backup.
- **Impersonation** (https://support.google.com/googleplay/android-developer/answer/9888374): do not "falsely imply an affiliation or relationship with another company, developer, entity, or organization". This is relevant if we name a Calculation Method authority (for example Umm al-Qura) in the title, icon or listing.
- **Full-screen intent** (https://support.google.com/googleplay/android-developer/answer/16558241): "On Android 14+, the USE_FULL_SCREEN_INTENT permission is auto-granted only for apps whose core function is setting alarms or handling calls. For any other use case, you must obtain explicit user consent". There is a Play Console declaration for it. An alarm-style full-screen Adhan Alert screen raises the same "is this an alarm app?" question as `USE_EXACT_ALARM`.
- **Battery optimisation** (https://developer.android.com/training/monitoring-device-state/doze-standby): "Google Play policies prohibit apps from requesting direct exemption from Power Management features—Doze and App Standby—in Android 6.0 and above unless the core function of the app is adversely affected." This matches reliable-alerts.md's advice to deep-link to settings rather than request the exemption.
- **Privacy policy**: required even when nothing is collected: "Even developers with apps that do not collect any user data must complete this form and provide a link to their privacy policy" (answer/10787469). The User Data policy requires the link "within the app itself" as well (answer/10144311).

## Open questions for the human

1. **Exact alarms:** accept `SCHEDULE_EXACT_ALARM` with a grant flow (recommended), or apply for `USE_EXACT_ALARM` and accept weeks of review and possible rejection? If we try `USE_EXACT_ALARM`, should it be held back for a later release so that a rejection does not block launch?
2. **Adhan FGS review:** are we comfortable presenting the alarm-started adhan as "Media Playback", with a video that shows the user turning on the Adhan Alert? Do we want a fallback (a notification-sound-only Adhan Alert) ready in case review rejects it?
3. **Backup:** exclude the stored location (and the Prayer Log?) from Google Drive backup, at the cost of users losing them on a new phone except through device-to-device transfer? Or keep backup with `disableIfNoEncryptionCapabilities`?
4. **Pre-permission screen:** add a one-screen location explanation before the system prompt, even though it is arguably not required for foreground coarse location?
5. **Android 17 (2027-01-27):** confirm in the Play Console once the declaration appears that a coarse-only app has no foreground-location declaration to file. The preview summary's wording is broader than the guide's.
6. **Full-screen Adhan Alert screen:** wanted? It would need the user to grant `USE_FULL_SCREEN_INTENT`, because we are probably not an "alarm" app for the auto-grant.

## Sources

Google Play Console Help (fetched 2026-11-08):
- https://support.google.com/googleplay/android-developer/answer/16558241 (Permissions and APIs that Access Sensitive Information: Exact Alarm, Full-Screen Intent, Location)
- https://support.google.com/googleplay/android-developer/answer/16909972 (Preview of the same policy, effective 2027-01-27)
- https://support.google.com/googleplay/android-developer/answer/17033915 (Minimum Scope: Foreground Location Access and the Location Button)
- https://support.google.com/googleplay/android-developer/answer/9214102 (Declare permissions for your app: Permissions Declaration Form)
- https://support.google.com/googleplay/android-developer/answer/13315670#foreground_service (Device and Network Abuse: Permissions for Foreground Services)
- https://support.google.com/googleplay/android-developer/answer/13392821 (Understanding foreground service and full-screen intent requirements)
- https://support.google.com/googleplay/android-developer/answer/16965181 (Preview of the same)
- https://support.google.com/googleplay/android-developer/answer/10787469 (Data safety section)
- https://support.google.com/googleplay/android-developer/answer/10144311 (User Data policy)
- https://support.google.com/googleplay/android-developer/answer/11150561 (Best practices for prominent disclosure and consent)
- https://support.google.com/googleplay/android-developer/answer/9799150 (Understanding location in the background permissions)
- https://support.google.com/googleplay/android-developer/answer/12253906 (Policy Deadlines; no longer contains exact-alarm content)
- https://support.google.com/googleplay/android-developer/answer/9878810 (Inappropriate Content)
- https://support.google.com/googleplay/android-developer/answer/9888374 (Impersonation)

Android Developers (fetched 2026-11-08):
- https://developer.android.com/about/versions/13/features#use-exact-alarm-permission
- https://developer.android.com/about/versions/14/changes/schedule-exact-alarms
- https://developer.android.com/develop/background-work/services/alarms
- https://developer.android.com/develop/background-work/services/fgs/service-types
- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- https://developer.android.com/identity/data/autobackup
- https://developer.android.com/guide/topics/permissions/private-alternatives/location-button
- https://developer.android.com/training/monitoring-device-state/doze-standby
