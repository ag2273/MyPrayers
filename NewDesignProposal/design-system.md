# Design System — "A Day That Breathes"

Version 1.1 · proposal, now covering every feature of Mawaqeet 1.3.0 · Arabic-first, RTL by default

> **1.1 (2026-11-13).** The look is unchanged: same tokens, type, radii, motion and
> components. What changed is coverage: every screen and setting of the shipping app
> (v1–v3) is now drawn with this system, the Widgets tab became a gallery inside
> Settings → Widgets (the app has four tabs), and the widgets keep today's component
> layouts in this design's colours. See §12 for the full list.

---

## 1. Concept

**"A Day That Breathes" (يوم يتنفّس).** Mawaqeet is an app about time, so time
itself is the visual hero. The background is a living sky that tracks the
prayer day: it is in **dawn** before sunrise, **day** through the afternoon,
**dusk** before Isha, and **night** after Isha. Every part of the surface —
background gradient, ambient glows, the sun, and the accent colour — shifts
slowly with the phase, so the app is never static: *it breathes*.

The countdown is the centrepiece: a horizon arc (the sun's path) with the next
prayer's name, a live `HH:MM:SS` countdown, and a sun-dot that travels the arc
between the previous prayer and the next.

### Design principles

1. **Arabic-first.** Arabic copy, RTL layout, and Kufi typography are designed
   first; English/LTR is the mirror, not an afterthought.
2. **Time is the hero.** Huge Kufi numerals, tabular figures everywhere, the
   countdown large enough to read across a room.
3. **The sky follows the day.** Four sky phases bound to the real clock; the
   UI is tinted by the moment, never a flat static background.
4. **Calm, slow motion.** All motion is gentle and ambient (18s–42s loops);
   nothing flashes. `prefers-reduced-motion` disables all of it.
5. **Tactile feedback.** Taps ripple and press-scale; the tasbeeh buzzes;
   controls feel physical rather than abstract.

### Out of scope for this proposal

No new architecture, no backend, no icon finals (the three icon directions are
candidates), no production code. This is the visual/UX language and its
reference implementation.

---

## 2. Colour

Palette source: the sky across the prayer day. Same palette powers light and
dark; only the roles change.

### Brand / core swatches

| Token | Light role | Dark role | Hex |
|---|---|---|---|
| `--teal` | Primary | — | `#1F6F66` |
| `--deep` | Primary (deep / gradient) | — | `#134A44` |
| `--amber` | Accent (next prayer, sun, focus) | — | `#E0A246` |
| `--amber-dark` | — | Accent | `#F2B65E` |
| `--rose` | Dusk glow only | — | `#C97A6B` |
| `--mist` | Background (light) | — | `#F2F6F4` |
| `--night` | — | Background (dark) | `#0E1917` |
| `--ink` | Text (light) | — | `#142421` |

### Semantic tokens — light (`html[data-theme="light"]`)

| Token | Value |
|---|---|
| `--bg` | `#F2F6F4` (mist) |
| `--surface` | `rgba(255,255,255,.72)` |
| `--surface-strong` | `rgba(255,255,255,.94)` |
| `--line` | `rgba(20,36,33,.12)` |
| `--text` | `#142421` (ink) |
| `--muted` | `rgba(20,36,33,.62)` |
| `--shadow` | `0 18px 48px rgba(19,74,68,.16)` |
| `--accent` | `#1F6F66` (teal) |
| `--accent-soft` | `rgba(31,111,102,.12)` |
| `--bar` | `#FBFDFC` |
| `--field` | `rgba(255,255,255,.9)` |
| `--track` | `rgba(20,36,33,.14)` |

### Semantic tokens — dark (`html[data-theme="dark"]`)

| Token | Value |
|---|---|
| `--seed` | `#2F7A6B` |
| `--bg` | `#0E1917` (night) |
| `--surface` | `rgba(24,44,41,.6)` |
| `--surface-strong` | `rgba(20,38,36,.92)` |
| `--line` | `rgba(255,255,255,.12)` |
| `--text` | `#EAF3EF` |
| `--muted` | `rgba(234,243,239,.62)` |
| `--shadow` | `0 18px 50px rgba(0,0,0,.5)` |
| `--accent` | `#8FD3C8` (teal glow) |
| `--accent-soft` | `rgba(143,211,200,.14)` |
| `--bar` | `#12201E` |
| `--field` | `rgba(255,255,255,.06)` |
| `--track` | `rgba(255,255,255,.16)` |

### Sky phases

The sky background is a three-stop gradient (`--sky-1` → `--sky-2` → `--sky-3`)
plus two blurred ambient glows (`--glow-a`, `--glow-b`) and a sun. The phase is
driven by the clock (`skyFor()`):

| Phase | Clock range | `--sky-1/2/3` (light) | `--sky-1/2/3` (dark) | Glows (`a`/`b`) |
|---|---|---|---|---|
| **dawn** | 05:05–06:24 | `#F6E7CE / #F2F6F4 / #CFE6E1` | `#123B36 / #0E1917 / #1B4B45` | amber `rgba(224,162,70,.55)` / rose `rgba(201,122,107,.35)` |
| **day** | 06:24–17:36 | same as dawn | same as dawn | mist `rgba(242,246,244,.85)` / teal `rgba(31,111,102,.22)` |
| **dusk** | 17:36–19:30 | same | same | teal `rgba(31,111,102,.45)` / amber `rgba(224,162,70,.30)` |
| **night** | < 05:05 or ≥ 19:30 | same | same | teal `rgba(31,111,102,.40)` / night `rgba(14,25,23,.85)` |

Note: the sky gradient stops (`--sky-1..3`) are constant per theme; only the
**glow colours** change per phase.

**1.1:** the phase now follows the real prayer day for the saved location, not fixed
clock hours: **night** from Isha + 45 min to Fajr, **dawn** from Fajr to Sunrise + 30 min,
**day** until Maghrib − 30 min, **dusk** until Isha + 45 min. Fixed hours put Cairo's
sky in "day" an hour after Maghrib in winter. The sun is amber by day and a pale moon by
night.

---

## 3. Typography

| Role | Family | Weights | Used for |
|---|---|---|---|
| `--font-display` | **Noto Kufi Arabic** | 400 / 600 / 700 | Prayer names, big times, countdown, section titles, widget names |
| `--font-body` | **Noto Naskh Arabic** | 400 / 600 | Dhikr text, running Arabic body |
| `--font-ui` | **Noto Sans** | 400 / 500 / 600 | Labels, settings rows, buttons, chips, captions |

Rules:

- All numerals use `font-variant-numeric: tabular-nums` (no layout jitter while
  the countdown ticks).
- **Digits follow the language**: Arabic-Indic `٠١٢٣٤٥٦٧٨٩` in Arabic, Latin
  `0123456789` in English (a "Digits" setting lets the user override: Auto /
  `٠١٢` / `123`).
- Display sizes are responsive: the countdown is
  `clamp(27px, 8.2vw, 34px)`, prayer-name hero `~34px`, widget 1×1 time `15px`.
- Kufi for the wordmark and every time value; Naskh for devotional text; Sans
  for chrome — this three-way split is what gives the app its calm, devotional
  tone.

Type specimen (Arabic):
> مواقيت · الفجر ٤:٤٨

---

## 4. Spacing, radii, elevation

### Spacing scale (informal, `px`)

`2 · 4 · 6 · 8 · 10 · 12 · 14 · 16 · 18 · 20 · 22 · 24`. Cards pad `14–16px`;
rows `12px 16px` with a `12px` gap; section gutters `14–16px`; the top bar pads
`safe-area-top` + `14px 18px`.

### Corner radii

| Surface | Radius |
|---|---|
| Phone frame (desktop) | `44px` |
| Onboarding card | `26px` |
| Card, dhikr card | `22px` |
| Tab bar, log row, stat | `20px` |
| Day cell (log week), icon btn | `16px` / `13px` |
| Chips, pills, switch, date chip | `999px` |
| Buttons | `14–16px` |
| Widget (Android 12+) | `22px` · widget (8–11) `10px` |

### Elevation

One soft shadow only: `--shadow` (`0 18px 48px rgba(19,74,68,.16)` light;
`0 18px 50px rgba(0,0,0,.5)` dark). Surfaces are translucent (`--surface`
is 60–94% opacity), so elevation is conveyed by translucency + shadow, never by
hard borders. The phone frame on desktop adds a deep outer shadow.

---

## 5. Motion

The signature is **slow ambient breathing**, not micro-interaction fireworks.
All keyframes honour `prefers-reduced-motion` (durations forced to ~0 and the
sky animations stopped).

| Animation | Element | Duration / easing |
|---|---|---|
| `breathe` | sky glow layer | `26s` alternate, scale 1→1.08 |
| `float1` / `float2` | two ambient glows | `34s` / `42s` alternate, drift |
| `sunrise` | sun | `18s` alternate, drift + scale |
| sky phase crossfade | `.sky` background | `1200ms var(--ease)` |
| `ripple` | tap ripple | `0.62s` ease-out, scale 0→2.6, fade |
| press scale | `.log-row`, `.tap-btn`, `.tasbeeh-btn` | `0.1–0.2s`, scale `.985/.95/.97` |
| progress fill | arc + adhkar ring + widget bar | `0.4s` / `0.9s` linear |

Standard easing: `--ease: cubic-bezier(.22,.61,.36,1)`.

---

## 6. Components

### 6.1 The sky (`<div class="sky">`)

Absolute, full-bleed, behind everything. Contains `.sky-glow.g1`, `.sky-glow.g2`,
`.sun`. The gradient is `linear-gradient(170deg, var(--sky-1), var(--sky-2) 52%,
var(--sky-3))`; the glows are blurred radial gradients with
`mix-blend-mode: screen`; the sun is a radial amber orb (pale moon at night).

### 6.2 Top bar

Brand wordmark `مواقيت` + `MAWAQEET` eyebrow, then a **location chip** (pin + city,
opens Settings → Location, as the app's Today does), a `EN` language toggle and a
sun/moon theme toggle (`38×38`, radius `13px`). The theme follows the phone by default
("حسب الهاتف" in Language and display).

### 6.3 Hero countdown arc (Today)

- SVG `viewBox 0 0 270 270`, radius `92`, centre `135,135`.
- A track circle + a progress fill (`pathLength=100`, `stroke-dashoffset`
  driven by the elapsed fraction between the previous and next prayer).
- A sun-dot on the arc edge (amber by day, pale by night).
- Centre: live countdown (Kufi), "حتى الأذان", and the next prayer's time.
- **After Isha** the time line becomes «الثلث الأخير ٠٠:٥٣» (start of the last third
  of the night, Maghrib → next Fajr), inside the arc so Today does not grow.
- **Ramadan** (automatic by Hijri date): the hero counts to iftar (Fajr → Maghrib,
  «الإفطار», «حتى الإفطار») or to the end of suhoor (Maghrib → Fajr, «السحور»).
  An amber-edged **Imsak chip** («الإمساك ٤:٣٨», Fajr − 10 min, editable 0–30) joins
  the date chips, and the next prayer's countdown sits under its name in the
  highlighted timeline row.
- Below: Gregorian chip + **Hijri chip with a calendar glyph** (tap → Calendar).
- A **fix-alerts tip** (amber soft strip, «قد يؤخر هاتفك تنبيهات الأذان» · إصلاح · تم)
  appears above the hero only while alerts are at risk.

### 6.4 Today: tools row and timeline

- **Tools row**: three equal `44px` pills under the dates — القبلة · التقويم · المساجد
  (teal glyph, `--surface`, radius `16px`).
- **Timeline**: six stops (Fajr, Sunrise, Dhuhr/Jumu'ah, Asr, Maghrib, Isha). Each
  prayer stop now carries, from the start side: dot · name · **alert bell** (full
  adhan = bell with waves, short = bell, silent = bell with a dash, off = struck bell)
  · time · **mark circle** (`30px` target): ring = due and unmarked, filled teal with
  a check = prayed, dashed = not yet due (not tappable). Sunrise has neither. Marking
  from Today ripples and buzzes, like the Log.
- A one-line hint («اضغط الدائرة بعد أداء الصلاة لتسجيلها. الجرس يبيّن نوع التنبيه.»)
  shows until the first mark.

**Size rule (no scroll, no empty space).** Today fills the screen: the arc is
`clamp(132px, min(24cqh, 60cqw), 244px)` of the phone container (`21cqh` under
`800px` tall, where the eyebrow hides), the countdown scales with it, and the six
stops share the leftover height (`min 44px`, `max 76px`). Checked at 360×740,
390×844, 393×852, 412×915 and 430×932 with no scroll; 360×640 scrolls a little
(the app's agreed exception). The fix-alerts tip may add a short scroll while shown.

### 6.4a Qibla

Full compass dial (`clamp(220px, min(44cqh, 82cqw), 320px)`) on `--surface-strong`:
5° ticks, ش ق ج غ, an amber Kaaba marker at the bearing, a fixed teal needle for the
phone, degrees in the centre. Under it «لفّ ٢٣° يمينًا» or, within 3°, «أنت في اتجاه
القبلة» in teal; chips for the bearing from true north and the km to Makkah. Cards for
the figure-8 calibration hint and the no-sensor fallback. From the saved location,
no new permission.

### 6.4b Calendar

Segmented control «القادم / الشهر». **Upcoming**: a card of rows with a Hijri date
block (`--accent-soft`, amber-soft for fasts), the occasion, the Gregorian date and
«بعد ١٢ يومًا», and an amber «صيام» chip on Sunnah fasts. **Month**: Hijri month header
with Gregorian span and prev/next, a 7-column grid starting Saturday (Hijri day big,
Gregorian small, amber dot = occasion, teal dot = Sunnah fast, today ringed amber),
a legend, and «في هذا الشهر». Umm al-Qura plus the user's Hijri offset.

### 6.4c Nearby mosques

A bottom sheet (onboarding card style) that hands off to the maps app searching
«مسجد»; no location leaves the app.

### 6.5 Prayer Log

- Week header with prev/next (back-btn style): «آخر سبعة أيام» and the date span;
  older weeks are view only («الأيام الأقدم من أسبوع للعرض فقط.»).
- 7 day cells (name, day number, 5 pips): teal = prayed, ring = missed, faint = not
  yet, empty outline = before first use. Tap a day to show its five rows.
- Summary stats: **المجموع ٢٦ من ٣٣** and the week's percentage. (1.0's "streak" is
  gone: the app does not count streaks.)
- Rows: tap to toggle (ripple + buzz); each row says its state («تمّت الصلاة»,
  «لم تُسجَّل», «لم يحن وقتها بعد», «قبل بدء استخدامك للتطبيق»).

### 6.6 Adhkar

- Set chips: أذكار الصباح · أذكار المساء · بعد الصلاة; after-prayer adds «بعد أيّ
  صلاة؟» chips for the five prayers.
- Set head: progress ring + «وصلت إلى الذكر ٢ من ٤ · وقتها من الفجر إلى الظهر».
- Outside the set's time an amber-soft note says it can be read but is not recorded.
- **Reader**: one dhikr card at a time (`min 180px`), «الذكر ١ من ٤», tap anywhere on
  the card to count, «بقي ٣», auto-advance when a dhikr completes, «الذكر السابق /
  التالي» ghost buttons. Complete → «اكتملت أذكار الصباح لهذا اليوم · تقبّل الله منا
  ومنك» + «ابدأ من جديد».
- **سجل الأذكار** row → Adhkar Record page (7 day cells with 3 pips: morning,
  evening, all five after-prayer sets; today's three states listed).
- **Tasbeeh**: unchanged `168px` button, «مجموع اليوم», long-press resets.

### 6.7 Settings

Root = the app's hub, in this system's cards:

- **Status card** (`--surface-strong` + shadow): التنبيهات جاهزة (shield) / قد تتأخر
  التنبيهات (amber warning → Fix alerts) / التنبيهات متوقفة (struck bell).
- **الصلاة**: الموقع وطريقة الحساب · التنبيهات والأذان.
- **التطبيق**: اللغة والعرض · أدوات الشاشة الرئيسية · النسخة الاحتياطية.
- **المساعدة**: إصلاح التنبيهات · حول مواقيت وبياناتك.

Rows gain an optional leading `34px` icon tile (`--accent-soft`, teal glyph) and a
radio primitive (`20px`, teal dot) for single choices with descriptions. Section
labels are `11px` muted captions above the cards.

Sub-pages (all built from card/row/switch/seg/stepper/slider/select):

| Page | Contents |
|---|---|
| Location and method | Location row → Location; method row → Method list (11 methods, descriptions, "suggested for you"); Asr seg with its definition; per-prayer steppers ±30 min; Advanced: high-latitude rule + explanation |
| Location | Offline city search, "use my current location", city list, manual coordinates, GeoNames credit |
| Alerts and adhan | Master switch (off hides all but Prayer Silence and Ramadan's Imsak); **Prayer silence** (switch, DND access row, silent/vibrate, prayer chips, duration stepper, Jumu'ah switch + duration, copy log); **Each prayer** (name → its page, reminder summary, seg قصير/كامل/صامت/بلا, reciter note); **Worship reminders** (salawat + frequency, morning/evening adhkar + minutes, Al-Kahf); **السنن** (Duha + minutes after Sunrise, Qiyam + notification/alarm); **صيام السنة** (5 switches); **رمضان** (suhoor reminder + minutes, Imsak alert, Imsak minutes); **How alerts behave** (alarm-clock mode, lock-screen permission row, try the alert, play on silent, vibration); **Adhan volume** (alarm/media, level slider, listen); **Your own adhan** (full non-Fajr, full Fajr) |
| Prayer alert (per prayer) | Adhan alert switch; sound radios with reciters (Fajr's full adhan has «الصلاة خير من النوم»); reminder switch + 5/10/15/20/30 min |
| Language and display | Language (with "changes app, widgets and notifications"), digits, theme, Hijri offset stepper ±2 days with live date |
| Widgets | Live Next Prayer preview, "every widget in the Next Prayer look", background transparency (light text from 60%), colours radio (app / wallpaper), → Widget gallery |
| Widget gallery | The 1.0 stage, now with today's 14 widget variants, times incl. Ramadan, looks (light, dark, wallpaper, Next Prayer look), Android 12+/8–11, transparency |
| Backup | What the file holds, privacy note, last export, Export / Restore (confirm sheet: settings replaced, logs merged) |
| Fix alerts | Brand chips (Samsung, Xiaomi, Huawei, Oppo, Vivo, other), numbered steps, two buttons |
| About and your data | App card (version 1.3.0), privacy, website and contact, Sources → Credits page, delete prayer log / adhkar record (red rows + confirm sheet), replay intro |

### 6.8 Tab bar

**4 tabs** — اليوم (Today) · السجل (Log) · الأذكار (Adhkar) · الإعدادات (Settings),
matching the app. The 1.0 fifth "Widgets" tab was a showcase, not something users
need daily; it lives on as the gallery under Settings → Widgets. Floating pill
`left/right 10px`, radius `22px`, 4 equal columns, selected tab gets `--accent-soft`
+ teal icon. Tapping the current tab returns it to its root page.

### 6.8a Sheets

Confirmations and hand-offs (mosques, restore, delete log/record) reuse the
onboarding card as a bottom sheet over a soft dark scrim. Destructive actions use a
muted red (`#B4473A`, `#F0A194` in dark).

### 6.9 Onboarding

The app's four steps in the bottom-sheet card: (1) «يوم يتنفّس» welcome with the
app's promise → (2) rough location, or search a city → (3) suggested calculation
method for the country, or choose another → (4) **one permissions screen**
(notifications + exact alarms with «السماح» buttons that turn into «✓ تم», battery
note); «ابدأ» enables only when both are granted. Replayable from About.

## 7. Home-screen widgets

**1.1 rule: today's widget layouts, this design's colours.** Every component, size
and piece of content stays as it ships in 1.3.0 (see `widgets-board-*.png`); only the
palette moves to this system.

| Look | Surface | Text / muted | Accent (prayed, links) | Next prayer / progress |
|---|---|---|---|---|
| App colours, light | dawn-sky gradient `#FBF1E2 → #F2F6F4 → #DDEEEA` (165°) | `#142421` / `#5A6A66` | teal `#1F6F66` | amber `#E0A246` (band `rgba(224,162,70,.20)`, bars, due ring) |
| App colours, dark | night gradient `#123B36 → #0E1917 → #1B4B45` | `#EAF3EF` / `#A9BAB5` | `#8FD3C8` | amber `#F2B65E` |
| Next Prayer (always) and "Next Prayer look" | deep gradient `#1F6F66 → #134A44 → #0F3B36` + hairline 8-point star | `#F2F6F4` / `#B9CFCB` | `#F2B65E` | `#F2B65E` |
| Gold clock (fixed) | `#000` | `#FFF` / `#C8B28A` | amber `#F2B65E` | amber |
| Wallpaper colours | the phone's Material You palette (unchanged); Next Prayer keeps the app colours | | | |
| Transparency ≥ 60% | background fades; text turns light with a soft shadow (unchanged rule) | | | |

Why amber for "next": in the app the next prayer is the amber dot on the arc and the
amber-ringed current stop, so the widgets now say "next" the same way; teal stays
"done/prayed". Amber is never used for small text on the light surface (contrast);
countdown text there is teal.

Variants (14): Next Prayer 4×2 / 2×2 / ring 1×1 (all deep), Today's times 4×2 (with
the day path) / 4×1, Today + log 4×2 / 4×1 (tap circles), Week log 4×3 (with dates,
row totals, legend) / 4×2, Date strip 4×1, Big clock 4×2 / 4×1 (weather beside the
clock: temperature, humidity, rain chance; 4×1 temperature only), Gold clock 4×2,
Clock & prayer 4×2. In Ramadan the Next Prayer widgets count to iftar / suhoor end
and the chip reads «تنبيه الإمساك»; the clock widgets keep their layout.

Widget radius: `22px` on Android 12+, `10px` on 8–11 (unchanged).

## 8. Iconography (candidates)

Three directions from the brand board, all drawn as flat vectors on a 108×108
adaptive canvas (artwork inside the central 66% safe zone):

- **B · Sun path** — the sun's arc over a horizon with five prayer points, the
  next one amber.
- **C · Mihrab** — a pointed mihrab arch outline with a small hanging lamp.
- **B+C · Mihrab with sun path** — the arch holding the five-point arc.

Colours for icons: background deep teal `#134A44→#1F6F66`, artwork mist white
`#F2F6F4`, one amber accent `#E0A246`. Themed (monochrome) versions in black on
transparent.

---

## 9. Accessibility

- **Reduced motion**: `prefers-reduced-motion` collapses all animation/transition
  durations to ~0 and stops the sky loops.
- **Focus**: visible focus ring `2px solid var(--amber)`, offset `3px`.
- **Contrast**: all probed text pairs ≥ 4.5:1 in light and dark (lowest measured
  4.86).
- **Touch targets**: min `44px` height on tap targets; `40px` on chips.
- **Semantics**: tabs use `role=tablist/tab/tabpanel` + `aria-selected`; switches
  use `role=switch` + `aria-checked`; segmented controls use `aria-pressed`.
- **Language/direction**: `lang`/`dir` flip together; back chevron and switch
  thumb mirror in RTL.
- **Vibration** is best-effort (`navigator.vibrate`) and never required.

---

## 10. State & persistence

Prototype state persists to `localStorage` (`mq.v13`, `mq.v13.log`,
`mq.v13.adhkar`); a seeded nine-day log and adhkar week make the Log, Record and
widgets meaningful on first open. Demo times (Cairo, Egyptian method): Fajr 04:48 ·
Sunrise 06:16 · Dhuhr 11:40 · Asr 14:43 · Maghrib 17:04 · Isha 18:23, shifted by city,
method, Asr convention and adjustments.

**Preview controls** sit under the phone and are not part of the app: phone size
(360×640 … 430×932, desktop only), a 115/130% scale stress test, moments of the day
(now, 14:20, Friday, after Isha, Ramadan fasting, Ramadan suhoor), alert state (ready
/ at risk) and "show onboarding".

## 11. Reference implementation

`artifacts/a-day-that-breathes/index.html` (single file, inline CSS + JS, Google Fonts
only). 1.1 was checked in headless Chromium at 360×640, 360×740, 390×844, 393×852,
412×915 and 430×932 (Today without scroll from 360×740 up, rows growing on tall
phones), Arabic and English, light and dark, every tab and sub-page, the onboarding,
all sheets, and all 14 widgets in four looks and five moments; no console errors and
no horizontal overflow.

## 12. Feature coverage (Mawaqeet 1.3.0)

| Feature | Where in the proposal |
|---|---|
| Prayer times, Jumu'ah naming, location chip | Today hero + timeline, top bar |
| Mark prayers from Today, alert bell per prayer | Today timeline |
| Last third of the night | Today arc after Isha |
| Ramadan mode (iftar / suhoor, Imsak) | Today hero, Alerts → رمضان, Next Prayer widgets |
| Qibla | Today → القبلة |
| Hijri calendar, occasions, Sunnah fasts | Today → التقويم / Hijri chip |
| Nearby mosques | Today → المساجد (sheet) |
| Prayer Log (weeks, read-only history, first use) | السجل |
| Adhkar reader, time windows, after-prayer sets, tasbeeh | الأذكار |
| Adhkar Record | الأذكار → سجل الأذكار |
| Location, method (11), Asr, adjustments, high latitude | Settings → الموقع وطريقة الحساب |
| Master switch, per-prayer adhan and reminder, Fajr adhan | Settings → التنبيهات والأذان (+ per-prayer page) |
| Prayer silence (DND, prayers, durations, Jumu'ah, log) | Alerts → الصامت وقت الصلاة |
| Worship reminders (salawat, adhkar, Al-Kahf) | Alerts → تذكيرات العبادة |
| Duha / Qiyam reminders | Alerts → السنن |
| Sunnah fast reminders | Alerts → صيام السنة |
| Alarm-clock mode, try alert, play on silent, vibration | Alerts → طريقة التنبيه |
| Adhan volume (alarm/media + level) | Alerts → صوت الأذان |
| Custom adhan (full, Fajr full) | Alerts → أذان من اختيارك |
| Language, digits, Hijri offset | Settings → اللغة والعرض |
| Widget settings and all widgets | Settings → أدوات الشاشة الرئيسية → معرض الأدوات |
| Weather on clock widgets | Big clock / Gold clock widgets |
| Backup / restore | Settings → النسخة الاحتياطية |
| Fix alerts by phone brand | Settings → إصلاح التنبيهات, status card, Today tip |
| Onboarding (location, method, one permissions screen) | First launch / About → replay |
| About, privacy, sources, delete data | Settings → حول مواقيت وبياناتك → المصادر |
