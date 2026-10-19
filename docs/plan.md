# Plan — Memento Mori Life Clock (Android home-screen widgets)

## 1. Problem & purpose
Abstract mortality statistics ("you have ~21,900 days left") are inert. This app turns
them into always-visible, glanceable circular time pressure on the phone's home screen,
to counteract procrastination. It renders **configurable countdown widgets** and a
**personalized life-clock widget**, each decomposing remaining time into visual rings.

## 2. Product principles
- **Glanceable/ambient** — readable in under a second, no interaction required.
- **Battery-honest** — update cadence is a declared, tested budget, not an accident.
- **Stateless & passive** — the widget holds no in-memory truth; it is recomposed from
  persisted state on demand (Glance instantiates it anytime, state can be destroyed).
- **Emotionally honest** — the numbers are meant to sting; no gamification, no soothing.
- **Citation-bound** — every statistic traces to a primary source and a documented method.

## 3. Scope
**In:** Android phones (target **API 26+/8.0**, exact floor confirmed against Glance in
`design.md`); 1..N widget instances; in-app configuration; four feature domains.
**Out:** iOS/Web/Desktop; accounts/cloud sync; per-second animated rings; servers/telemetry.

## 4. Feature domains
- **A. Arbitrary countdown** — user sets a target datetime (deadline / task / goal).
- **B. Memento-mori life-clock** — user sets **DOB + sex + country + sleep hrs/day +
  screen hrs/day**; app computes life-remaining statistics.
- **C. Per-ring appearance (both domains)** — for every ring/category the user configures
  **diameter, color, border width (stroke thickness), and a name label**; the label is shown
  **inside the ring or underneath it**. Layout arrangement (single / stack / grid /
  concentric) is selectable. Because `RemoteViews` has no free-form layout, we offer
  **preset templates** (Classic, Compact, Bold, Minimal, Concentric, Gallery, Custom) that
  seed all knobs, which the user can then tweak. The config UI shows a live preview.
- **D. Widget catalog (settings live in the app)** — all configuration is authored in-app as
  named, reusable `WidgetConfigRecord`s. Widgets are stateless renderers bound to a config.
  Adding a widget shows a **picker** of saved configs (Android's config Activity), and a
  placed widget can be rebound/reconfigured from the app. No per-instance overrides.

## 5. The time model (shared by both domains)
For each category we compute a **value** and a **fraction of the reference total** (for the
ring):
| Category | Definition | Notes |
|---|---|---|
| Days left | `remaining_seconds / 86400` | floor |
| Weeks left | whole weeks remaining | |
| Calendar months left | months between now and end | calendar-based, NOT 30-day |
| Net awake-free hours | remaining wake hours − sleep − maintenance | ATUS-derived proportions |
Optionally: weekends, seasons/winters. Reference total = category value at the *start*
moment so the ring shows meaningful progress.

## 6. Hard platform constraints (de-risked in M0, detailed in design.md)
- Glance `CircularProgressIndicator` is **indeterminate only** → determinate ring must be a
  self-rasterized `Bitmap` (`Canvas.drawArc`) pushed as an image. The ring is a static frame.
- The only self-ticking view is the platform `RemoteViews` **`Chronometer`** (text). Live
  numeric countdown is text, not the ring.
- Update floors: `updatePeriodMillis` ≥ 30 min; `WorkManager` ≥ 15 min; **Doze defers all**
  alarms/jobs while screen off. Exact alarms are avoided (API 31+ permission + battery cost).
- Multi-instance, launcher variance, resize, and `ACTION_DATE_CHANGED` /
  `TIMEZONE_CHANGED` / `BOOT_COMPLETED` / DST / manual clock change are **requirements**.

## 7. Architecture (high level → refined in design.md)
Pure Kotlin **time/stats engine** (no Android deps, TDD) → **persistence** (Jetpack
DataStore: profile + reusable widget **configs** + per-placement **bindings**) → **widget
layer** (Glance + raw RemoteViews ring bitmap & Chronometer; binding-picker config Activity)
→ **scheduling** (WorkManager + inexact boundary alarms + system broadcast receivers) →
**app UI** (Compose: catalog, editor, "Your widgets" manager). Hilt for DI. All settings
live in the app; the widget only renders its bound config.

## 8. Data sources & citations
SSA Period Life Table (`e_x` by age/sex); WHO Global Health Observatory life tables (country
adjustment); BLS **American Time Use Survey** (sleep / work / commute / grooming / screen
proportions for net free hours); sleep-duration meta-analyses and resting-HR normative data.
Methodology documented alongside the constants; values are **computed, not hardcoded**.

## 9. Milestones
- **M0 — Platform de-risk spike.** Prove determinately-filled ring bitmap renders in Glance
  on a real launcher; prove `Chronometer` countdown; measure update cadence under Doze.
  *DoD:* recorded findings + screenshots; no unknown blockers remain.
- **M1 — Time engine (TDD).** Countdown + category model, fractions, month/calendar math.
  *DoD:* unit tests pass (DST, leap years, timezones, month edges).
- **M2 — Countdown widget v1.** Single ring + Chronometer, DataStore, config activity,
  multi-instance, boundary scheduling.
  *DoD:* survives reboot/timezone/clock change on a physical device.
- **M3 — Memento-mori engine + life-clock widget.** Source-bound stats with citations;
  ring-per-category life-clock.
  *DoD:* computed remaining-days reconciles to SSA `e_x` within tolerance; free-hours
  reproduces published ATUS averages.
- **M4 — Appearance & UX polish.** Per-ring appearance editor (diameter, color, stroke,
  label/placement), layout arrangements (single/stack/grid/concentric), preset templates
  (Classic/Compact/Bold/Minimal/Concentric/Gallery/Custom) with live preview; dynamic color,
  a11y, launcher matrix, widget preview.
  *DoD:* every template renders correctly on Pixel Launcher, Samsung One UI, Nova, Lawnchair.
- **M5 — Battery & resilience hardening.** Battery Historian, `dumpsys appwidget`,
  Doze/App Standby tests.
  *DoD:* wakeups within declared budget; no ANR; documented budget met.

## 10. Success criteria / definition of done
Widget stays correct after: reboot, DST, timezone change, manual clock change, process death,
resize. Composed statistics reconcile to primary sources. Battery budget honored on device.

## 11. Risks & open questions
- Per-instance ring bitmap memory/perf as widget count grows.
- `Chronometer` behavior differences across launchers.
- Month-definition ambiguity (calendar vs 30-day) — surfaced to user.
- SSA/ATUS data licensing, update cadence, and country granularity.
- Play Store widget-quality/preview requirements.
- Update cadence vs user expectation of a "sweeping" ring — managed via UX framing.
