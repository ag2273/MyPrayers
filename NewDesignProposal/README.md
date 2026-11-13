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
| `artifacts/a-day-that-breathes/index.html` | The complete interactive prototype — 5 tabs, all settings sub-pages, all 13 widgets, onboarding. Open in any browser. |
| `artifacts/brand-board/index.html` | The original brand board (icon directions, palette, fonts, icon-generation prompts). |
| `artifacts/widget-variants/index.html` | The earlier widget-variants explorer (system-font widget study). |
| `artifacts/screenshots/` | Rendered screens: home (light/dark/mobile), log, adhkar, settings, alerts, widgets, and three widget shots. |

## How to view the prototype

- `artifacts/a-day-that-breathes/index.html` is a single self-contained file
  (inline CSS + JS, Google Fonts only). Double-click it, or serve it:
  `python3 -m http.server` then open `http://localhost:8000/artifacts/a-day-that-breathes/`.
- It is **Arabic-first / RTL** by default. Tap the `EN` button to flip to
  English; the moon button toggles light/dark; digits follow the language
  (Arabic-Indic `٠١٢` in Arabic, `123` in English).

## Scope note

This folder is intentionally additive. It documents the design and bundles its
artifacts without modifying any other file in the repository.
