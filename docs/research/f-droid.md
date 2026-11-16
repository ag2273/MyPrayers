# Research: publishing Mawaqeet on F-Droid and IzzyOnDroid

Researched 2026-11-16. Terms follow [GLOSSARY.md](../../GLOSSARY.md).

**How each source was checked**

- **f-droid.org docs.** The proxy refuses `f-droid.org` and `www.f-droid.org` (CONNECT 403). The docs were read from their source in the website repo, [fdroid/fdroid-website](https://gitlab.com/fdroid/fdroid-website) @ `4a26ceed` (2026-11-16), under `_docs/`. That source is what `f-droid.org/docs/` renders. Each claim below cites the canonical f-droid.org URL; the source file is listed under [Sources](#sources).
- **fdroiddata, fdroidserver, gradlew-fdroid, gradle-transparency-log.** Read from gitlab.com: raw files, a shallow clone, and the public REST API (no login).
  - fdroiddata @ `948e9dfd` (2026-11-16)
  - fdroidserver @ `fc2c8647` (2026-11-12)
  - gradlew-fdroid @ `ac23ffb5` (2026-10-21)
- **IzzyOnDroid.** `apt.izzysoft.de`, `android.izzysoft.de` and `codeberg.org` are all refused by the proxy. The IzzyOnDroid project moved its issue tracker and repodata to Codeberg. Its older GitLab project, [IzzyOnDroid/repo](https://gitlab.com/IzzyOnDroid/repo) @ `deddda86` (2026-04-20), is "archived for now" but still "updated for a while". Its wiki (Inclusion Policy, FAQ, Fastlane, Reproducible Builds) and its APK-check scripts were read from there. **The GitLab wiki may lag the current policy on Codeberg.** Check the Codeberg copy before submitting.
- **Open-Meteo.** `open-meteo.com` is refused by the proxy. The project's own README and LICENSE were read from `raw.githubusercontent.com/open-meteo/open-meteo/main/`.
- **This repo and our own APK.** `app/build.gradle.kts`, `settings.gradle.kts`, `gradle/wrapper/gradle-wrapper.properties` and the app sources were read. The signing blocks of our own `mawaqeet-1.4.6-release.apk` (in project files) were parsed with a short Python script.
- **Review times.** Measured from the fdroiddata merge-request API (label `New App`), not taken from any docs page.

## Task

Find what Mawaqeet (`app.nileorbit.mawaqeet`, versionCode 16 / versionName 1.4.6) must satisfy to be listed:

- in **(a)** the official F-Droid repo, through an fdroiddata merge request
- in **(b)** the IzzyOnDroid repo

Then say which route is quicker. Use primary sources only.

## Short answer

| | F-Droid (fdroiddata) | IzzyOnDroid |
|---|---|---|
| **Hard blockers today** | No LICENSE file; no release tags; repo is 8 days old (3-month rule) | No LICENSE file; no GitHub release with an APK attached; the release APK carries the Google-encrypted dependency-info signing block |
| **Strongly expected** | fastlane metadata in the repo | fastlane metadata in the repo; APK ≤ 30 MB (ours is 15.7 MB) |
| **Copyrighted adhans** | Never built by F-Droid (they're injected with `-P`), so no conflict. But a silent adhan may fail the review's "functional" check (see §4) | IzzyOnDroid ships **our** APK, so the released APK must not contain the Alafasy or Makkah Haram recordings |
| **Open-Meteo** | Not NonFreeNet: the server is AGPLv3 and self-hostable. A Tracking flag is possible, see §2 | Not an issue |
| **AGP 9.5.1 / Gradle 9.9.0 / compileSdk 37** | No blocker found: Gradle 9.9.0 is in F-Droid's checksum log, JDK 21 is available, AGP comes from Google Maven | Not relevant (they don't build, except for optional RB checks) |
| **Time to listed** | ≥ 3 months (age rule, unless an exception applies), then the MR queue (median 42 days in our sample), then 1–2 days to publish | Days: no age rule; updates are picked up within 24 h of a tagged release |

**IzzyOnDroid is the quicker route by months.** The two routes are not exclusive. The work IzzyOnDroid needs (licence, tags, fastlane, `dependenciesInfo` off, an openly licensed or silent adhan in the released APK) is also F-Droid's prerequisite list. Do IzzyOnDroid first, then file the fdroiddata MR once the 3-month mark passes, ideally as a reproducible build signed with our key.

## Findings

### 1. Licence

- **F-Droid:** "All applications in the repository must be Free, Libre and Open Source Software (FLOSS)". The licence must be recognised by the DFSG, FSF, GNU or OSI lists. — [Inclusion Policy](https://f-droid.org/docs/Inclusion_Policy/)
- **Submitting checklist, item 1:** "The app should have a public source code repository and a FOSS license file." — [Quick Start Guide](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/)
- **What reviewers check:** they "look for copyright notices in license files, including _README_", and verify that the source files' licences "are consistent with corresponding license/README files". — same page, "Application Review Process"
- **Metadata `License` field:** "Values should correspond to short identifiers of the SPDX license list. There can only be one license listed here." — [Build Metadata Reference](https://f-droid.org/docs/Build_Metadata_Reference/#License)
- **IzzyOnDroid:** the app "must be free … and Open Source … This includes using a libre license, approved by OSI/FSF (see SPDX)". — [IzzyOnDroid Inclusion Policy (GitLab wiki)](https://gitlab.com/IzzyOnDroid/repo/-/wikis/Inclusion-Policy)

**Our state.** The repo has no `LICENSE`/`COPYING` file, and the GitHub API reports `license: null` for `ag2273/MyPrayers`. A public repo with no licence is "all rights reserved", which neither repo accepts. **This blocks both routes.**

**Notes for choosing a licence** (the choice is the maintainer's):

- **Compatibility of what we bundle.** Every bundled third-party part is compatible with both a copyleft and a permissive app licence:
  - adhan2 is MIT ([batoulapps/adhan-kotlin LICENSE](https://github.com/batoulapps/adhan-kotlin/blob/main/LICENSE))
  - the AndroidX libraries are Apache-2.0
  - the Noto fonts are OFL
  - the GeoNames data is CC BY 4.0 (`app/src/main/assets/licenses/cities.txt`)
  - the Natural Earth data is public domain

  Open-Meteo's AGPL covers their server, not API clients.
- **Copyleft vs permissive.** `GPL-3.0-or-later` stops closed-source rebrands; F-Droid's own client and fdroidserver are GPL/AGPL. `Apache-2.0` is permissive, with an explicit patent grant. Both are on every list F-Droid accepts. The prayer-app precedent in fdroiddata, Al-Azan, is `AGPL-3.0-only` ([metadata](https://gitlab.com/fdroid/fdroiddata/-/blob/master/metadata/com.github.meypod.al_azan.yml)).
- **Play is not a reason to avoid GPL.** We are the copyright holder, so we can ship our own GPL code on Play. GPL binds recipients, not the author.
- **Licence the assets separately.** Data and assets keep their own licences: GeoNames (CC BY 4.0) and any CC BY / CC BY-SA adhan. List them in the README or a NOTICE file, which the reviewers read.

### 2. Open-Meteo: NonFreeNet or not

- **Definition:** NonFreeNet is applied to apps that "promote or depend entirely on a proprietary network service". — [Anti-Features](https://f-droid.org/docs/Anti-Features/#NonFreeNet)
- **Open-Meteo's source:** "Source code available under AGPLv3" … "The free non-commercial API is hosted at api.open-meteo.com … **The API server source code is in this repository.**" … "You can self-host Open-Meteo with Docker or the prebuilt package for Ubuntu 22.04." — [open-meteo/open-meteo README](https://github.com/open-meteo/open-meteo/blob/main/README.md). The [LICENSE](https://github.com/open-meteo/open-meteo/blob/main/LICENSE) is the GNU AGPL v3.
- **Precedent.** fdroiddata ships Breezy Weather's `freenet` flavour with no AntiFeatures at all ([org.breezyweather.yml](https://gitlab.com/fdroid/fdroiddata/-/blob/master/metadata/org.breezyweather.yml)); its README says it "Fully works with Open-Meteo (FOSS source)" ([README](https://github.com/breezy-weather/breezy-weather/blob/main/README.md)). By contrast, an OpenWeatherMap app is flagged `NonFreeNet: Rely on OpenWeatherMap` ([org.woheller69.weather.yml](https://gitlab.com/fdroid/fdroiddata/-/blob/master/metadata/org.woheller69.weather.yml)).

**Verdict: Open-Meteo is not NonFreeNet.** TetheredNet ("depends entirely on a service which is impossible (or not easy) to replace", [Anti-Features](https://f-droid.org/docs/Anti-Features/#TetheredNet)) should not apply either. Weather is an extra on the Clock Widgets, and the app does not depend on it.

**Two things to fix or be ready for:**

- **Tracking (possible flag).** The two F-Droid pages define it differently:
  - The *Anti-Features* page defines Tracking as reporting activity "without your permission or by default".
  - The *Build Metadata Reference* goes further: "doing requests to a data collecting network service (regard- less if the service is based on free software, or not). For example, activity-based down-loading of weather data". — [Build Metadata Reference, AntiFeatures](https://f-droid.org/docs/Build_Metadata_Reference/#AntiFeatures)

  Our fetch is automatic once a Clock Widget is placed. `WeatherUpdater.updateIfDue` runs from the widget's minute tick, and the app has no setting to turn weather off. The request carries the location rounded to 0.1° (`core/.../Weather.kt`). Breezy Weather's precedent suggests reviewers don't flag weather apps for this. A cheap safeguard is a "Show weather" switch (or making weather opt-in), mentioned in the description.
- **Attribution and terms.** "API data is offered under the Attribution 4.0 International license (CC BY 4.0) … Include an attribution link next to any location where Open-Meteo data is displayed". The API is "free for open-source developers and non-commercial use". — [README, "Data License" / "Terms"](https://github.com/open-meteo/open-meteo/blob/main/README.md)

  The app's About → Credits (`about/Credits.kt`) has no Open-Meteo entry today. That is a CC BY gap whichever store we use. F-Droid also requires that "Applications must not infringe third party rights" ([Inclusion Policy](https://f-droid.org/docs/Inclusion_Policy/)).

### 3. The missing `dependenciesInfo` block

AGP embeds a "Dependency metadata" blob in the APK signing block when it signs. fdroidserver lists it among the forbidden extra blocks:

```python
# Dependencies metadata generated by Gradle and encrypted by Google Play.
0x504B4453: 'Dependency metadata',
```

`scan_binary()` counts each such block as a "Problem". — [fdroidserver/scanner.py L80–96, L690–703](https://gitlab.com/fdroid/fdroidserver/-/blob/master/fdroidserver/scanner.py#L80)

**Our APK has it.** `mawaqeet-1.4.6-release.apk` holds the signing-block IDs `0x7109871a` (v2 signature), **`0x504b4453` (dependency metadata, 7,534 bytes)**, and `0x42726577` (verity padding).

What this means per route:

- **F-Droid, F-Droid-signed (no `Binaries`): not a blocker.** F-Droid builds the unsigned release from source. Our build leaves the release unsigned when no keystore properties are given (`app/build.gradle.kts`, `signingConfigs`). fdroiddata CI then signs its test build with a throwaway key ([.gitlab-ci.yml L464–467](https://gitlab.com/fdroid/fdroiddata/-/blob/master/.gitlab-ci.yml#L464)). *Inference:* AGP only adds the blob when AGP itself signs, so F-Droid's own build won't carry it.
- **F-Droid, reproducible with our signature (`Binaries`): a blocker.** fdroiddata CI runs `fdroid scanner --exit-code` on the upstream binary when there is one. On `Dependency metadata` it prints `ERROR Found extra signing block 'Dependency metadata'` and fails the job (`EXITVALUE=1`). — [fdroiddata .gitlab-ci.yml L541–569](https://gitlab.com/fdroid/fdroiddata/-/blob/master/.gitlab-ci.yml#L541)
- **IzzyOnDroid: flagged.** Their updater runs `CheckSigningBlocks.py` on every APK and logs `! … contains signature block blobs` as an error ([ApkUpdater.class.inc L494–497](https://gitlab.com/IzzyOnDroid/repo/-/blob/master/lib/ApkUpdater.class.inc#L494)). The script lists `DEPENDENCY_INFO_BLOCK=0x504b4453` under `GOOGLE_BLOCKS`, separate from the `OK_BLOCKS` (v2, v3, v3.1, verity padding) ([CheckSigningBlocks.py L21–36](https://gitlab.com/IzzyOnDroid/repo/-/blob/master/lib/CheckSigningBlocks.py#L21)).

**Fix (two lines, harmless for Play):**

```kotlin
android {
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}
```

`includeInBundle = false` only stops Play Console's SDK-dependency insights. For Play, a cleaner option is to keep the bundle flag on and turn off only `includeInApk`, since GitHub/IzzyOnDroid get the APK and Play gets the AAB. This is a judgement call.

### 4. Silent placeholder, non-free audio, and the Non-Free Assets policy

- **Inclusion Policy:** "Non-functional assets including artwork and fonts can utilize less restrictive licenses … but must allow redistribution when using non-commercial licenses. All assets need to have valid legal licenses or be in the public domain while being free of copyright infringement." — [Inclusion Policy](https://f-droid.org/docs/Inclusion_Policy/)
- **NonFreeAssets** is for assets "under a license that restricts commercial usage or making derivative works (for example, any Creative Commons license with a 'Non-Commercial' (NC) or 'No Derivatives' (ND) restriction)". — [Anti-Features](https://f-droid.org/docs/Anti-Features/#NonFreeAssets)
- **Quality:** "The application should be functional and implement all the features described in the description." — [Inclusion Policy, Quality Control 2](https://f-droid.org/docs/Inclusion_Policy/)
- **Review:** reviewers "look at your non-source code files to identify Non-Free resources". — [Quick Start Guide](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/)

Applying this to our options:

| Build | F-Droid | IzzyOnDroid |
|---|---|---|
| **Alafasy / Makkah Haram recordings** (no licence, not ours) | **Not acceptable at all.** They have no licence, so NonFreeAssets can't cover them; it covers NC/ND-*licensed* assets. They are never in the repo, so F-Droid can't build them in anyway | **Not acceptable.** IzzyOnDroid redistributes our APK. Bundling recordings we have no right to redistribute exposes us and them |
| **Silent placeholder only** (the default when no `-P` is given) | Licence-clean (we generate it). Risk: the app is sold on its adhan, and a silent bundled adhan may be read as "not functional". Mitigation: the custom-adhan picker already exists (Settings → Alerts, `CustomAdhanFiles`); say so in the description | Licence-clean; the same description note applies |
| **Openly licensed adhans committed to the repo** (CC0 / CC BY / CC BY-SA shortlist in [adhan-recordings.md](adhan-recordings.md)) | **Best.** No anti-feature: CC BY and BY-SA aren't NC/ND. Attribution goes in the app's credits and the README | **Best** |

So the cleanest path is to commit the cleared recordings to the repo and drop the `-P` injection for store builds. The README rule "Never add adhan audio to git" was written for the *uncleared* clips, so a cleared recording can go in. If that isn't done yet, a silent-placeholder build is acceptable on licence grounds, provided the description is honest that the user picks their own adhan.

### 5. fdroiddata metadata fields

These fields come from the [Quick Start Guide](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/), the [Build Metadata Reference](https://f-droid.org/docs/Build_Metadata_Reference/) and the fdroiddata templates ([general.yml](https://gitlab.com/fdroid/fdroiddata/-/blob/master/templates/general.yml), [build-gradle.yml](https://gitlab.com/fdroid/fdroiddata/-/blob/master/templates/build-gradle.yml)):

| Field | Rule | Our value |
|---|---|---|
| `Categories` | A list, even of one, from [config/categories.yml](https://gitlab.com/fdroid/fdroiddata/-/blob/master/config/categories.yml) | `Religion` (exists; used by Al-Azan and Persian Calendar), plus `Clock` (exists) |
| `License` | One SPDX id | the chosen licence (§1) |
| `AuthorName` | "as published by upstream, e.g. in their copyright or authors file"; may be omitted | The public name or organisation in the LICENSE copyright line. Per CLAUDE.md, no personal real name unless the maintainer chooses to publish it |
| `WebSite` | "Don't use the source code url as website" | `https://mawaqeet.nileorbit.com` (the `WEBSITE_HOST` in SettingsScreen.kt) |
| `SourceCode`, `IssueTracker` | human-friendly URLs | the GitHub repo and `/issues` |
| `RepoType` / `Repo` | `git`; an `https://` URL that needs no auth | `https://github.com/ag2273/MyPrayers.git` |
| `Builds[].commit` | "Don't use branch name or tag name. The full commit hash should be used." | the 40-char hash of the release tag |
| `Builds[].subdir` | "the path where the `build` directory will be generated" | `app` |
| `Builds[].gradle` | `yes` = no flavour, runs `assembleRelease` | `yes` |
| `AutoUpdateMode` | `Version` (with `Tags`, no pattern) | `Version` |
| `UpdateCheckMode` | `Tags` checks every tagged revision for the highest versionCode; an optional regex filters the tags | `Tags ^v[\d.]+$` (Al-Azan uses the same) |
| `CurrentVersion` / `CurrentVersionCode` | Normally filled in by `fdroid checkupdates` | `1.4.6` / `16`, or the first tagged release |
| `Binaries` / `AllowedAPKSigningKeys` | Only for reproducible builds signed with our key (§7) | optional |

**Tags are a prerequisite:** "Each official release commit … should have a tag. For example, if its AndroidManifest contains `versionName: '1.0'`, the commit needs a `v1.0` tag." — [Quick Start Guide](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/). The repo has no tags today (GitHub API `/tags` returns `[]`). The version is a literal in `defaultConfig` (`versionCode = 16`, `versionName = "1.4.6"`), which is the "normal location", so no `UpdateCheckData` is needed: "The version info can't be composed or calculated dynamically since F-Droid only uses regex". — [Quick Start Guide, "Autoupdate configuration"](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/)

**First-timers file an RFP issue:** "If you are a 'first time' contributor, consider opening an issue at RFP (Request for Packaging)", or open the MR directly after the fork's CI pipeline passes. — [fdroiddata CONTRIBUTING.md](https://gitlab.com/fdroid/fdroiddata/-/blob/master/CONTRIBUTING.md)

**Draft `metadata/app.nileorbit.mawaqeet.yml`.** Placeholders are in `<…>`. Do not submit until the blockers are fixed.

```yaml
Categories:
  - Religion
  - Clock
License: <SPDX id, e.g. GPL-3.0-or-later>
AuthorName: <public name or organisation, as in LICENSE>
WebSite: https://mawaqeet.nileorbit.com
SourceCode: https://github.com/ag2273/MyPrayers
IssueTracker: https://github.com/ag2273/MyPrayers/issues
Changelog: https://github.com/ag2273/MyPrayers/releases

AutoName: مواقيت

RepoType: git
Repo: https://github.com/ag2273/MyPrayers.git
# Optional, for a reproducible build published with our signature (see section 7):
# Binaries: https://github.com/ag2273/MyPrayers/releases/download/v%v/mawaqeet-%v-release.apk

Builds:
  - versionName: 1.4.6
    versionCode: 16
    commit: <full 40-char commit hash that tag v1.4.6 points to>
    subdir: app
    gradle:
      - yes

# AllowedAPKSigningKeys: <lower-case SHA-256 of our release certificate, from apksigner verify --print-certs>

AutoUpdateMode: Version
UpdateCheckMode: Tags ^v[\d.]+$
CurrentVersion: 1.4.6
CurrentVersionCode: 16
```

Notes on the draft:

- **No `gradleprops`.** Without `-Pmawaqeet.adhanAudio` etc. the build bundles the silent clip, or the committed cleared recordings once §4 is done. Never pass the uncleared files.
- **No `AntiFeatures`, unless a reviewer applies `Tracking` (§2).** The current format is a map with reasons, e.g. `AntiFeatures: { NonFreeNet: { en-US: … } }` (see woheller69.weather above).
- **`Summary` and `Description` are left out on purpose.** "Don't use this. The localized metadata in the app's source code should be used instead." — [Build Metadata Reference, Name](https://f-droid.org/docs/Build_Metadata_Reference/#Name)

### 6. Fastlane metadata layout and locales

Place the metadata at `fastlane/metadata/android/<locale>/` from the repo root. Alternatives are `metadata/<locale>/` or `<subdir>/fastlane/metadata/android/<locale>/`. "Placing the structure anywhere else, like in the gradle module's root, **won't work**." — [All About Descriptions, Graphics, and Screenshots](https://f-droid.org/docs/All_About_Descriptions_Graphics_and_Screenshots/)

```
fastlane/metadata/android/
├── en-US/                       "en-US is the F-Droid fallback language" (IzzyOnDroid: "required")
│   ├── title.txt                app name, max 50 chars
│   ├── short_description.txt    max 80 chars, mandatory; "no trailing dot" (Quick Start)
│   ├── full_description.txt     max 4000 chars, mandatory; limited HTML allowed
│   ├── images/
│   │   ├── icon.png             usually 512×512
│   │   ├── featureGraphic.png   optional landscape banner
│   │   └── phoneScreenshots/1.png, 2.png, …
│   └── changelogs/16.txt        "must correspond to versionCode, literally, no padding"; max 500 chars
└── ar/
    └── (same files, Arabic)
```

- **Locale codes.**
  - **English:** `en-US` (the required fallback).
  - **Arabic:** `ar`. The F-Droid client's own metadata uses `ar` and `en-US` side by side ([fdroidclient/metadata](https://gitlab.com/fdroid/fdroidclient/-/tree/master/metadata)), and fdroidserver's `DEFAULT_LOCALE = 'en-US'` ([common.py L120](https://gitlab.com/fdroid/fdroidserver/-/blob/master/fdroidserver/common.py#L120)).
  - **Play:** if the same tree is also used with `fastlane supply` for Play, note that Play insists on regional codes. IzzyOnDroid notes that F-Droid merges multiple matching locations. — [IzzyOnDroid Fastlane wiki](https://gitlab.com/IzzyOnDroid/repo/-/wikis/Fastlane)
- **Only the latest release counts.** "F-Droid will only use metadata files from the latest release": the tree must be in the tagged commit. — [All About Descriptions](https://f-droid.org/docs/All_About_Descriptions_Graphics_and_Screenshots/)
- **The Latest tab** needs a name, icon, summary, description, licence, at least one What's New entry, at least one graphic, and at least one of these translated. — same page, "Latest tab criteria"
- **IzzyOnDroid specifics:**
  - It picks only `phoneScreenshots` and downsizes them to 350 px on the short side.
  - It keeps the height:width ratio at most 2:1.
  - It states the changelog limit as "max size: 500 bytes". **Arabic is 2 bytes per letter in UTF-8, so an Arabic changelog gets about 250 letters.**
  - "Please let me know whenever you add 'new elements' to your Fastlane structure". — [IzzyOnDroid Fastlane wiki](https://gitlab.com/IzzyOnDroid/repo/-/wikis/Fastlane)

The repo has no `fastlane/` directory today. Screenshots exist in project files (`screens-1.4.1/`, `play-store/`), and `site/icon-512.png` exists.

### 7. Reproducible builds and publishing with our own signing key

- **Optional.** "Reproducible builds are not a requirement for apps being on F-Droid. But we do consider their use best practice." But "one can't easily switch to them later because Android doesn't allow updates with a different signing key … So we mainly encourage their use for new apps." — [Quick Start Guide, "Setup Reproducible Build"](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/)
- **How it works.** Set `Binaries` (a versioned URL using `%v`/`%c`) and `AllowedAPKSigningKeys`. "If `fdroid publish` can verify that the downloaded APK matches the one built from the fdroiddata recipe, the downloaded APK will be published. Otherwise F-Droid will skip publishing this version." — [Reproducible Builds, "Exclusively publishing developer-signed APKs"](https://f-droid.org/docs/Reproducible_Builds/)
- **What `AllowedAPKSigningKeys` holds:** "a lower case hex value of the SHA-256 fingerprint of the signing certificate". — [Build Metadata Reference](https://f-droid.org/docs/Build_Metadata_Reference/#AllowedAPKSigningKeys)
- **Why it matters for us.** Play users and F-Droid users would get the same key, so updates cross over between stores. Without RB, an F-Droid install can't update from a GitHub, IzzyOnDroid or Play APK (IzzyOnDroid FAQ footnote 1, [FAQ](https://gitlab.com/IzzyOnDroid/repo/-/wikis/FAQ)).
  - **Caveat:** the Play key may be Google-held (Play App Signing). If so, the Play APK and our self-signed GitHub APK already differ, and the RB key is our *upload/self* key, not the Play key.
- **Known pitfalls that apply to us:**
  - **Signing block:** the dependency-info block must be off (§3).
  - **VCS info:** with AGP ≥ 8.3 the APK holds `META-INF/version-control-info.textproto` (ours does). So build "from a clean tree at the actual tagged commit". — [Reproducible Builds, "VCS Info"](https://f-droid.org/docs/Reproducible_Builds/); the same advice is in IzzyOnDroid's [RB Hints for Developers](https://gitlab.com/IzzyOnDroid/repo/-/wikis/Reproducible-Builds/RB-Hints-for-Developers)
  - **`apksigner` version:** "`apksigner` from build-tools >= 35.0.0-rc1 outputs unverifiable APKs … one needs to manually select the `apksigner` version from 34". AGP's built-in signing (what we use) is not apksigner. Check `apksigcopier` verification on the first release (fdroiddata#3299). — [Reproducible Builds](https://f-droid.org/docs/Reproducible_Builds/)
  - **Baseline profile:** `baseline.prof`/`.profm` can be non-deterministic with Compose. The doc's workaround disables the `ArtProfile` tasks. — same page
  - **JDK match:** our build uses `jvmToolchain(21)`, and the buildserver has JDK 21 from Debian Trixie. — same page, "Mismatched Toolchains"
- **IzzyOnDroid also runs RB verification** where it is set up ([Reproducible Builds wiki](https://gitlab.com/IzzyOnDroid/repo/-/wikis/Reproducible-Builds)). The preferred APK source, "attached to Github tagged `releases/`", is "preferred (as only then it is possible to establish reproducible builds)". — [IzzyOnDroid Inclusion Policy](https://gitlab.com/IzzyOnDroid/repo/-/wikis/Inclusion-Policy)

### 8. IzzyOnDroid requirements and timelines

The requirements come from [IzzyOnDroid Inclusion Policy (GitLab wiki)](https://gitlab.com/IzzyOnDroid/repo/-/wikis/Inclusion-Policy). Each row gives the rule and our status.

| Requirement | Our status |
|---|---|
| Libre OSI/FSF licence | **Missing** (§1) |
| Targeted at end users; code freely accessible (GitHub OK) with a proper description | OK |
| No proprietary components; trackers "bearable at best" (max two); **none at all** for apps that "process sensitive data … or targeted at children" | OK: no GMS, Firebase or trackers |
| No downloading of executable binaries without opt-in consent | OK |
| "`*.apk` files signed by a release key", not `debuggable`/`testOnly` | OK (release key, outside the repo) |
| APK "attached to Github tagged `releases/`" (preferred) | **Missing**: no releases and no tags |
| Own unique packageName | OK (`app.nileorbit.mawaqeet`) |
| Avoid `usesCleartextTraffic` | OK (HTTPS only) |
| "Up to 30 megabytes per app … at the same time the upper size limit for single `.apk` files"; up to 3 versions kept if they fit | OK: 1.4.6 is 15.7 MB. That includes about 2.4 MB of mp3s that must go anyway (§4), and the dex is unminified (about 31 MB uncompressed in 3 dex files) |
| No extra signing-block blobs (checked by `CheckSigningBlocks.py`) | **Fails today** (§3) |

- **Where to apply.** Inclusion requests go to [codeberg.org/IzzyOnDroid/repodata/issues](https://codeberg.org/IzzyOnDroid/repodata/issues). That host is refused by this proxy, so the current issue template is unverified. — [IzzyOnDroid/repo README](https://gitlab.com/IzzyOnDroid/repo/-/blob/master/README.md)
- **Updates.** "Our update checker runs daily … whenever developers tag a new version, create a release for it, and attach the APK to the new release, our update checker should pick it up within 24 hours". The FAQ table puts F-Droid updates at "usually within 3-5 days" and IzzyOnDroid at "usually within 24h". — [IzzyOnDroid FAQ](https://gitlab.com/IzzyOnDroid/repo/-/wikis/FAQ)
- **Metadata.** IzzyOnDroid syncs the fastlane tree with each release. The other fields (author, URLs) are set on their side. — same page
- **Initial review time.** No reachable IzzyOnDroid page states one, and Codeberg (where the queue lives) is blocked here. **Unverified.**

### 9. F-Droid timeline and the 3-month rule

- **Age rule.** "The application should have been actively developed for at least 3 months, unless: The application has been proven to have some users. The author has been maintaining other apps on F-Droid. The application is developed only for an event." — [Inclusion Policy, Quality Control 6](https://f-droid.org/docs/Inclusion_Policy/)
  - The GitHub repo was created 2026-11-08 (GitHub API `created_at`), so the earliest date without an exception is about **2027-02-08**.
  - A live Play listing with installs might count as "proven to have some users". That is the reviewer's call.
- **Review queue (measured).** We took the 100 most recently updated `New App` MRs merged in fdroiddata.
  - Time from MR creation to merge: median 42.5 days, quartiles 24–69 days, maximum 251 days.
  - 485 `New App` MRs were open on 2026-11-16 (`x-total` header).
  - Source: `GET /projects/fdroid%2Ffdroiddata/merge_requests?labels=New App`
- **After merge.** "Around 24 to 48 hours from fdroiddata merge until the application appears in the main repository" — [Quick Start Guide, "What to Expect"](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/). "Don't panic before 2 days have passed" — [F-Droid wiki FAQ](https://gitlab.com/fdroid/wiki/-/wikis/FAQ).

### 10. AGP 9.5.1, Gradle 9.9.0 and compileSdk 37 on the F-Droid buildserver

**No blocker was found.** The evidence:

- **Gradle.** The buildserver runs `gradlew-fdroid`, not our `gradlew`. The scanner removes `gradle-wrapper.jar`, `gradlew` and `gradlew.bat` ([scanner.py L987–993](https://gitlab.com/fdroid/fdroidserver/-/blob/master/fdroidserver/scanner.py#L987); [provision-gradle](https://gitlab.com/fdroid/fdroidserver/-/blob/master/buildserver/provision-gradle)).
  - `gradlew-fdroid` reads `distributionUrl` from the wrapper properties.
  - It verifies the download against its built-in list (up to 9.2.0) or against the signed [gradle-transparency-log checksums.json](https://gitlab.com/fdroid/gradle-transparency-log/-/raw/master/checksums.json) ([gradlew.py L24–28, README](https://gitlab.com/fdroid/gradlew-fdroid)).
  - `gradle-9.9.0-bin.zip` (our wrapper's version) is in that log (checked 2026-11-16).
- **AGP and libraries.** These come from Google Maven and Maven Central, which are listed as trusted repositories. "The Android SDK … ha[s] permission to use official prebuilt binaries". — [Inclusion Policy, "Build Transparency"](https://f-droid.org/docs/Inclusion_Policy/)
  - We use only `google()`, `mavenCentral()` and `gradlePluginPortal()` (plugins only), in `settings.gradle.kts`.
  - The ban on "some pre-release toolchains" means we should stay on stable AGP/Gradle releases for store builds.
- **JDK.** The buildserver disables Gradle's JVM auto-download (`org.gradle.java.installations.auto-download=false`, [provision-gradle](https://gitlab.com/fdroid/fdroidserver/-/blob/master/buildserver/provision-gradle)). It has JDK 25, 21 and 17 ([Reproducible Builds, "Mismatched Toolchains"](https://f-droid.org/docs/Reproducible_Builds/)). `jvmToolchain(21)` therefore resolves to the installed JDK 21.
- **SDK platform 37 and build-tools.** The base image preinstalls platforms only up to `android-33`. It does write the SDK licence hashes to `$ANDROID_HOME/licenses/android-sdk-license` ([provision-android-sdk](https://gitlab.com/fdroid/fdroidserver/-/blob/master/buildserver/provision-android-sdk)). *Inference:* with the licences accepted, AGP downloads the missing `platforms;android-37` and build-tools itself during the build, as it does for current apps such as Catima targeting Android 16 ([TWIF 2025-10-24](https://f-droid.org/2025/10/24/twif.html)). The fork's fdroiddata CI pipeline (`fdroid build`) proves this before the MR is filed.
- **Leftovers that don't matter.** `org.gradle.configuration-cache=true` and `org.gradle.caching=true` in `gradle.properties` are fine in a fresh container. gradlew-fdroid's `MIN_GRADLE_VERSION` table (AGP version → Gradle version) stops at AGP 9.4. That table is only a fallback when no wrapper version is found, and ours is found.

## Blockers vs done

**Done already**

- No GMS, Firebase, Play Core or trackers. Only Google Maven and Maven Central dependencies.
- Coarse location only, through LocationManager.
- Unique applicationId.
- A literal versionCode/versionName.
- A release that builds unsigned without keystore properties, which is what F-Droid needs.
- Secrets outside the repo.
- APK well under 30 MB.
- Toolchain available on the buildserver.

**Blockers (both routes)**

1. Add a `LICENSE` file, with an SPDX id in the README, and a NOTICE listing the data and asset licences.
2. Tag releases `vX.Y.Z` on the exact release commit.
3. Keep the uncleared adhans out of any published APK. Commit the cleared recordings from adhan-recordings.md, or ship the silent clip and say the user picks their own adhan.
4. Add the `fastlane/metadata/android/{en-US,ar}/` tree.
5. Credit Open-Meteo (CC BY 4.0) in About.

**IzzyOnDroid only**

6. Set `dependenciesInfo { includeInApk = false }` (also needed for F-Droid RB).
7. Publish a GitHub Release with the signed APK attached.

**F-Droid only**

8. Wait for the 3-month mark (about 2027-02-08), or argue for the "proven users" exception.
9. File the fdroiddata MR (or RFP) with green fork CI.
10. Optionally add a weather on/off switch to pre-empt a Tracking flag.

## Open questions for the maintainer

1. Which licence: GPL-3.0-or-later or Apache-2.0 (§1)?
2. Commit the cleared adhans now (which reverses the "never add adhan audio to git" rule for cleared files only), or ship silent first?
3. F-Droid-signed or reproducible with our key? This has to be decided **before** the first F-Droid release, because it can't be switched later without users reinstalling. If RB: which key (our self-signing key, not the Play App Signing key)?
4. Is Mawaqeet live on Play with users? That decides whether to ask F-Droid for the 3-month exception.
5. Before filing at IzzyOnDroid, check the current Codeberg copy of their Inclusion Policy and issue template. Codeberg is unreachable from this environment.

## Sources

Read in this session (2026-11-16):

- **F-Droid docs.** Canonical URLs, read from [fdroid-website `_docs/`](https://gitlab.com/fdroid/fdroid-website/-/tree/master/_docs) @ `4a26ceed`:
  - [Inclusion Policy](https://f-droid.org/docs/Inclusion_Policy/) (`_docs/Inclusion_Policy.md`)
  - [Anti-Features](https://f-droid.org/docs/Anti-Features/) (`_docs/Anti-Features.md`)
  - [Build Metadata Reference](https://f-droid.org/docs/Build_Metadata_Reference/) (`_docs/Build_Metadata_Reference.md`)
  - [Reproducible Builds](https://f-droid.org/docs/Reproducible_Builds/) (`_docs/Reproducible_Builds.md`)
  - [Submitting to F-Droid Quick Start Guide](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/) (`_docs/Submitting_to_F-Droid_Quick_Start_Guide.md`)
  - [All About Descriptions, Graphics, and Screenshots](https://f-droid.org/docs/All_About_Descriptions_Graphics_and_Screenshots/) (`_docs/All_About_Descriptions_Graphics_and_Screenshots.md`)
  - [TWIF 2025-10-24](https://f-droid.org/2025/10/24/twif.html) (`_posts/2025-10-24-twif.md`)
- **fdroiddata** @ `948e9dfd`:
  - [CONTRIBUTING.md](https://gitlab.com/fdroid/fdroiddata/-/blob/master/CONTRIBUTING.md)
  - [templates/general.yml](https://gitlab.com/fdroid/fdroiddata/-/blob/master/templates/general.yml)
  - [templates/build-gradle.yml](https://gitlab.com/fdroid/fdroiddata/-/blob/master/templates/build-gradle.yml)
  - [config/categories.yml](https://gitlab.com/fdroid/fdroiddata/-/blob/master/config/categories.yml)
  - [.gitlab-ci.yml](https://gitlab.com/fdroid/fdroiddata/-/blob/master/.gitlab-ci.yml) (L464–467, L541–569)
  - metadata: [com.github.meypod.al_azan](https://gitlab.com/fdroid/fdroiddata/-/blob/master/metadata/com.github.meypod.al_azan.yml), [org.breezyweather](https://gitlab.com/fdroid/fdroiddata/-/blob/master/metadata/org.breezyweather.yml), [org.woheller69.weather](https://gitlab.com/fdroid/fdroiddata/-/blob/master/metadata/org.woheller69.weather.yml), [com.byagowi.persiancalendar](https://gitlab.com/fdroid/fdroiddata/-/blob/master/metadata/com.byagowi.persiancalendar.yml)
  - merge-request API, label `New App` (merged and opened)
- **fdroidserver** @ `fc2c8647`:
  - [fdroidserver/scanner.py](https://gitlab.com/fdroid/fdroidserver/-/blob/master/fdroidserver/scanner.py) (L80–96, L667–717, L987–993)
  - [fdroidserver/common.py](https://gitlab.com/fdroid/fdroidserver/-/blob/master/fdroidserver/common.py) (L120)
  - [buildserver/provision-gradle](https://gitlab.com/fdroid/fdroidserver/-/blob/master/buildserver/provision-gradle)
  - [buildserver/provision-android-sdk](https://gitlab.com/fdroid/fdroidserver/-/blob/master/buildserver/provision-android-sdk)
- **gradlew-fdroid** @ `ac23ffb5`: [gradlew.py, README](https://gitlab.com/fdroid/gradlew-fdroid)
- **Gradle transparency log:** [checksums.json](https://gitlab.com/fdroid/gradle-transparency-log/-/raw/master/checksums.json)
- **F-Droid wiki:** [FAQ](https://gitlab.com/fdroid/wiki/-/wikis/FAQ)
- **F-Droid client:** [metadata/ locale directories](https://gitlab.com/fdroid/fdroidclient/-/tree/master/metadata)
- **IzzyOnDroid** ([gitlab.com/IzzyOnDroid/repo](https://gitlab.com/IzzyOnDroid/repo) @ `deddda86`):
  - [README](https://gitlab.com/IzzyOnDroid/repo/-/blob/master/README.md)
  - wiki: [Inclusion-Policy](https://gitlab.com/IzzyOnDroid/repo/-/wikis/Inclusion-Policy), [FAQ](https://gitlab.com/IzzyOnDroid/repo/-/wikis/FAQ), [Fastlane](https://gitlab.com/IzzyOnDroid/repo/-/wikis/Fastlane), [Reproducible-Builds](https://gitlab.com/IzzyOnDroid/repo/-/wikis/Reproducible-Builds), [RB-Hints-for-Developers](https://gitlab.com/IzzyOnDroid/repo/-/wikis/Reproducible-Builds/RB-Hints-for-Developers), [Developer-Best-Practices](https://gitlab.com/IzzyOnDroid/repo/-/wikis/Developer-Best-Practices)
  - [lib/CheckSigningBlocks.py](https://gitlab.com/IzzyOnDroid/repo/-/blob/master/lib/CheckSigningBlocks.py)
  - [lib/ApkUpdater.class.inc](https://gitlab.com/IzzyOnDroid/repo/-/blob/master/lib/ApkUpdater.class.inc) (L494–497)
- **Open-Meteo:** [README](https://github.com/open-meteo/open-meteo/blob/main/README.md), [LICENSE](https://github.com/open-meteo/open-meteo/blob/main/LICENSE) (AGPL-3.0)
- **Other upstreams:** [Breezy Weather README](https://github.com/breezy-weather/breezy-weather/blob/main/README.md), [adhan-kotlin LICENSE](https://github.com/batoulapps/adhan-kotlin/blob/main/LICENSE) (MIT)
- **GitHub API:** `repos/ag2273/MyPrayers` (`created_at`, `license`), plus its `/releases` and `/tags`
- **This repo:**
  - `app/build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradle/wrapper/gradle-wrapper.properties`, `gradle/libs.versions.toml`
  - `core/src/main/kotlin/app/mawaqeet/core/Weather.kt`, `app/src/main/kotlin/app/mawaqeet/weather/WeatherUpdater.kt`, `app/src/main/kotlin/app/mawaqeet/about/Credits.kt`
  - the signing blocks and entries of `mawaqeet-1.4.6-release.apk`

Unreachable from this environment (CONNECT 403 from the proxy):

- `f-droid.org`, `www.f-droid.org`: read through the website source repo instead
- `apt.izzysoft.de`, `android.izzysoft.de`: IzzyOnDroid repo info page and blog
- `codeberg.org`: IzzyOnDroid repodata (current issue tracker and inclusion template), apkscanner
- `open-meteo.com`: the terms and licence pages; the GitHub README was used instead
- `spdx.org`, `www.gnu.org`
