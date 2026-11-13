# New Design Proposal — "A Day That Breathes"

A complete, documented design proposal for the MyPrayers / Mawaqeet app.
The concept: **the app follows the day.** The sky is the protagonist — it shifts
through four phases (dawn, day, dusk, night) on the real clock, and the whole
surface (background gradient, glows, sun, accent colour) breathes along with it.

## What's in here

| Path | What it is |
|---|---|
| [`design-system.md`](design-system.md) | The full design system: concept, principles, colour, typography, spacing/radii/elevation, motion, components, widgets, iconography, accessibility. **Start here.** |
| [`tokens.css`](tokens.css) | Every design token as copyable CSS custom properties (light, dark, and the four sky phases). |
| [`palette.svg`](palette.svg) | A static palette chart (light + dark + sky phases + type specimens). |
| `artifacts/a-day-that-breathes/index.html` | The complete interactive prototype, covering every feature of Mawaqeet 1.3.0: 4 tabs (Today with Qibla, calendar, mosques and Ramadan mode; Log; Adhkar with its record; Settings with every page), onboarding, and all 14 widgets in today's layouts with this design's colours. Preview controls under the phone switch size, moment of day and Ramadan. |
| `artifacts/brand-board/index.html` | The original brand board (icon directions, palette, fonts, icon-generation prompts). |
| `artifacts/widget-variants/index.html` | The earlier widget-variants explorer (system-font widget study). |
| `artifacts/screenshots/` | Rendered screens (2×): Today in five phone sizes (`today-size-matrix.png`), after Isha, Ramadan and suhoor; Qibla; calendar; log; adhkar and its record; settings, alerts, per-prayer alert, backup, widgets settings and gallery; onboarding; and four widget boards (`widgets-board-*.png`: light, dark in Ramadan, Next Prayer look, wallpaper colours). |

## How to view the prototype

- `artifacts/a-day-that-breathes/index.html` is a single self-contained file
  (inline CSS + JS, Google Fonts only). Double-click it, or serve it:
  `python3 -m http.server` then open `http://localhost:8000/artifacts/a-day-that-breathes/`.
- It is **Arabic-first / RTL** by default. Tap the `EN` button to flip to
  English; the moon button toggles light/dark; digits follow the language
  (Arabic-Indic `٠١٢` in Arabic, `123` in English).

## Version 1.1 (2026-11-13)

Same visual design; it now covers the whole shipping app (v1–v3). The changes are
listed at the top of `design-system.md` and in its §12 coverage table.

## Scope note

This folder is intentionally additive. It documents the design and bundles its
artifacts without modifying any other file in the repository.
