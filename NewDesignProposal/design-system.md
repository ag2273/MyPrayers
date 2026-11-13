# Design System — "A Day That Breathes"

Version 1.0 · proposal · Arabic-first, RTL by default

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
**glow colours** change per phase. The sun is amber by day and a pale moon by
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

Brand wordmark `مواقيت` + `MAWAQEET` eyebrow, then a `EN` language toggle and a
sun/moon theme toggle (`38×38`, radius `13px`).

### 6.3 Hero countdown arc (Home)

- SVG `viewBox 0 0 270 270`, radius `92`, centre `135,135`.
- A track circle + a progress fill (`pathLength=100`, `stroke-dashoffset`
  driven by the elapsed fraction between the previous and next prayer).
- A sun-dot on the arc edge (amber by day, pale by night).
- Centre: live `HH:MM:SS` countdown (Kufi, `clamp(27px,8.2vw,34px)`),
  "حتى الأذان" label, and the next prayer time.
- Below: Gregorian + Hijri date chips.

### 6.4 Timeline (Home)

Six stops (Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha) as a vertical list. States:
`current` (strong surface + amber dot with glow ring), `past` (60% opacity),
`done` (filled teal dot). Sunrise is a stop, never a logged prayer.

### 6.5 Prayer Log

- 7-day week strip of day cells with 5 pip-dots each (prayed = teal).
- Summary stats: streak (days) + weekly %.
- Today's rows: tap to toggle, checkmark fills teal, ripple + buzz.

### 6.6 Adhkar

- Set chips (Morning / Evening / After prayer).
- Progress ring (`64px`, dashoffset fill) + set meta.
- Dhikr cards (Arabic Naskh text, optional English, count pill, tap button).
- **Tasbeeh**: a `168px` circular button (teal gradient + amber ring), tap to
  count, **long-press (~650ms) to reset**, with vibration.

### 6.7 Settings

Cards of rows. Row primitives:

- **switch** — `46×28` pill, teal when on, RTL-aware thumb slide.
- **seg** — segmented control (language, digits, theme, reminders, adhan
  short/full/silent, etc.).
- **stepper** — `±` with a tabular readout (per-prayer ±30 min).
- **slider** — styled `input[type=range]`.
- **select** — RTL-aware dropdown.
- **tap row** — navigates to a sub-page (Location, Alerts, Widgets, About) or
  an action.

Sub-pages: **Appearance**, **Location** (city search + manual coords),
**Prayer times** (method, Asr convention, per-prayer adjustments, high-latitude
rule), **Alerts** (master switch, per-prayer adhan segments, reminder,
alarm-clock mode, play-on-silent, vibration, volume, worship reminders, prayer
silence), **Widgets** (transparency, colours, deep-look), **About** (version,
credits, privacy, contact, replay intro).

### 6.8 Tab bar

5 tabs — الرئيسية (Home) · السجل (Log) · الأذكار (Adhkar) · الإعدادات (Settings) ·
الأدوات (Widgets). Floating pill `left/right 10px`, `bottom safe-area`, radius
`22px`, 5 equal columns, selected tab gets `--accent-soft` fill + teal icon.

### 6.9 Onboarding

3 steps (day-that-breathes / times-that-fit-you / one-alert-is-enough), bottom
sheet card with icon, dots (active dot stretches), Skip / Next→Start. Shown
once, persisted, replayable from About.

---

## 7. Home-screen widgets

13 variants, one gallery tab with a fake home-screen stage. Tokens:

- **Android 12+** (`mode-12`): translucent `color-mix` tint from `--seed`,
  radius `22px`.
- **Android 8–11** (`mode-8`): solid `--surface-strong`, radius `10px`, drop
  shadow.
- Widgets use `--w-bg / --w-fg / --w-muted / --w-accent / --w-soft / --w-on`.

| # | Variant | Class | Size |
|---|---|---|---|
| 1 | Next prayer | `v1` | 4×2 |
| 2 | Next prayer | `v1` | 2×2 |
| 3 | Next prayer ring | `v1x1` | 1×1 |
| 4 | Today's times | `v2` | 4×2 |
| 5 | Today's times | `v2` | 4×1 |
| 6 | Today with log | `v3` | 4×2 |
| 7 | Today with log | `v3` | 4×1 |
| 8 | Week log | `v4` | 4×3 |
| 9 | Week log | `v4` | 4×2 |
| 10 | Date strip | `v5` | 4×1 |
| 11 | Big clock | `clk` | 4×2 |
| 12 | Gold clock | `clk clk-gold` | 4×2 |
| 13 | Clock & prayer | `clk` | 4×2 |

Gallery controls: variant, scenario (14:20 / 00:30 / Friday / 18:50), Android
12+ vs 8–11, digits, language, transparency (0–100%).

---

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

All prototype state persists to `localStorage`:

| Key | Contents |
|---|---|
| `mq.complete` | settings, language, theme, digits, tab, onboarding, widget prefs |
| `mq.log` | prayer log per day per prayer |
| `mq.adhkar` | adhkar counters |
| `mq.tasbeeh` | tasbeeh count |

Demo prayer times (Cairo): Fajr 04:48 · Sunrise 06:16 · Dhuhr 11:40 · Asr 14:43
· Maghrib 17:04 · Isha 18:23. Countdown uses the real clock with an after-Isha
rollover to tomorrow's Fajr.

---

## 11. Reference implementation

The complete tap-through prototype is at
`artifacts/a-day-that-breathes/index.html` (single self-contained file, inline
CSS+JS, Google Fonts only). It was verified in headless Chrome at 1440px and
390px: zero console errors, no horizontal overflow, onboarding shows once and
skips, all 5 tabs + 6 settings sub-pages open, all 13 widgets render and tick,
log/adhkar/tasbeeh persist, AR/EN flips direction, theme toggles.
