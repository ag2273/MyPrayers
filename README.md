# مواقيت · Mawaqeet

An Arabic-first Android app (package `app.nileorbit.mawaqeet`) and set of home-screen widgets for daily prayer times. It sounds the adhan when each prayer is due, and keeps a private log of the prayers you mark as prayed. ("MyPrayers" is only the repository's name.)

## What it does

- **Prayer Times** for your location: automatic, a city search over about 34k cities, or typed coordinates. The Calculation Method and Asr Convention are suggested per country. You can adjust each Prayer by minutes, and there is a high-latitude rule. Friday shows Jumu'ah. The Hijri date follows your offset.
- **Adhan Alerts and Prayer Reminders** on exact alarms. For each Prayer you choose the Short adhan, the Full adhan or silent, and an optional reminder some minutes before. Fajr keeps its own adhan («الصلاة خير من النوم»). A Custom Adhan file can replace any recording, but a custom Full Adhan never changes Fajr's. Other options:
  - one switch that turns every app alert off and back on
  - alarm-clock mode, which shows the alert over the lock screen
  - play on silent
  - vibration
  - the adhan's volume, following either the alarm or the media volume
- **Prayer Log:** mark each Prayer as prayed for today and the 6 days before. There is a weekly view and summary, and you can delete the log. Everything stays on the phone.
- **Widgets:**
  - Next Prayer: 4×2 and 2×2 on a deep teal surface, with a gold countdown and progress line. Its 1×1 is a ring.
  - Today's Prayer Times: 4×1, and 4×2 with the day path.
  - Date strip: 4×1.
  - Today with Prayer Log: 4×1 and 4×2. Tap a due Prayer to mark it, or tap again to unmark it.
  - Week Prayer Log: 4×2 and 4×3.
  - Settings → Widgets sets transparency, and app or wallpaper colours. App colours are the default.
- Arabic and English, with Arabic-Indic or Latin digits, light and dark themes, and TalkBack support.

## Website

[mawaqeet.nileorbit.com](https://mawaqeet.nileorbit.com) lives in [`site/`](site): a static page in Arabic (`/`) and English (`/en/`), each with its privacy policy (`/privacy`, `/en/privacy`). No build step. Cloudflare Pages deploys it from `main`: framework preset **None**, build command empty, output directory `site`, custom domain `mawaqeet.nileorbit.com`. Contact: contact@nileorbit.com.

## Decisions

Product and architecture decisions are recorded in [`docs/adr/`](docs/adr):

- [0001](docs/adr/0001-native-android-only.md) Native Android only (Kotlin, Compose, Glance) for v1.
- [0002](docs/adr/0002-arabic-first.md) Arabic first.
- [0003](docs/adr/0003-country-defaults.md) Default calculation settings per country.

Domain vocabulary is in [`GLOSSARY.md`](GLOSSARY.md). How the code is laid out, tested and built is in [`docs/contributing.md`](docs/contributing.md).

Later decisions, recorded there:

- Widgets were redesigned per widget and per size. Next Prayer keeps its deep colours, whatever the colours choice.
- Today with Prayer Log may change the log, but only through the prayer core's rules.
- Release APKs are signed with a keystore kept outside the repo.
- The shipped adhan recording is supplied at build time and never committed.

## Build

Requirements: JDK 21 and the Android SDK (platform 37, build-tools 37.0.0).

```sh
./gradlew :core:test :app:testDebugUnitTest   # tests
./gradlew :app:assembleDebug                    # debug APK
```

A signed release needs your own keystore and, for an audible default adhan, your own recording. Neither is in this repository. Put the following in `~/.gradle/gradle.properties`, or pass each line with `-P`:

```properties
mawaqeet.signing.storeFile=/path/to/release.jks
mawaqeet.signing.storePassword=…
mawaqeet.signing.keyAlias=…
mawaqeet.signing.keyPassword=…
mawaqeet.adhanAudio=/path/to/short-adhan.mp3
mawaqeet.adhanFullAudio=/path/to/full-adhan.mp3
mawaqeet.adhanFajrAudio=/path/to/fajr-adhan.mp3
```

Then run:

```sh
./gradlew :app:assembleRelease
```

Without the signing properties, the release APK is left unsigned. Without `adhanAudio`, the bundled adhan is a silent placeholder until you pick a Custom Adhan; without `adhanFullAudio` or `adhanFajrAudio`, the Full or Fajr adhan reuses the short one.

## Repository rules

This repository is public. Never commit keys, keystores, passwords, `.env` files, real `google-services.json` files, adhan recordings or personal data. See [`CLAUDE.md`](CLAUDE.md).
