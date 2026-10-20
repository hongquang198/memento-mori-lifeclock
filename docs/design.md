# Design — Memento Mori Life Clock

This document is the "how" behind `plan.md`. It defines the module map, contracts,
persistence schema, the ring-rendering pipeline, the scheduling state machine, the DI
graph, and the test strategy.

**Project coordinates:** `dev.mementomori.lifeclock` · **minSdk 26** (Android 8.0) ·
`java.time` used natively (no desugaring needed at API 26) · Kotlin + Jetpack Glance.

---

## 1. Layering & module map

Strict dependency direction: **inner layers never know about outer layers.** Android
frameworks only appear in the outer modules.

```
:app            (Compose app: widget catalog, editor, instance manager, Hilt, Manifest)
  ├─ :feature:config   (widget catalog + per-widget editor + "Your widgets" manager)
  ├─ :widget           (GlanceAppWidget, RemoteViews ring, Chronometer, receivers)
  ├─ :core:data        (DataStore, repositories)
  └─ :core:scheduler   (WorkManager workers, boundary alarms, boot/time receivers)
        └─ :core:mortality   (pure Kotlin life-stat engine)   ─┐
        └─ :core:time        (pure Kotlin countdown engine)   ─┼─ no Android deps
        └─ :core:model       (shared value types, Citation)   ─┘
```

| Module | Responsibility (SRP) | Depends on |
|---|---|---|
| `:core:model` | Value types, `Citation`, enums. No logic. | — |
| `:core:time` | Countdown decomposition (days/weeks/months/net-free-hours). | `:core:model` |
| `:core:mortality` | Life-table + ATUS math, citation-bound stats. | `:core:model` |
| `:core:data` | Persistence of widget configs, instance bindings & profile. | `:core:model` |
| `:core:scheduler` | When/how to refresh widgets. | `:core:data` |
| `:widget` | How a widget is drawn (ring bitmap + Chronometer); binding-picker activity. | `:core:*` |
| `:feature:config` | Widget **catalog** (create/name/edit reusable configs), editor with **live preview**, and the "Your widgets" instance manager. | `:core:*` |
| `:app` | Wiring, DI, manifest. | all |

Rationale: `:core:time` and `:core:mortality` are **pure JVM** — they can be unit-tested
with no Robolectric, no emulator, fast and deterministic (a `Clock` is injected).

---

## 2. Core contracts

### 2.1 Time abstraction (determinism everywhere)
```kotlin
fun interface Clock { fun now(): Instant }
```
Injected at every boundary. Production: `Clock.systemUTC()`. Tests: `FakeClock`.

### 2.2 Countdown engine (`:core:time`)
```kotlin
enum class TimeCategory { DAYS, WEEKS, MONTHS, NET_FREE_HOURS }

/** A single ring's data: how much is left and how full the ring should be. */
data class TimeSlice(
    val category: TimeCategory,
    val remaining: Long,          // in this category's own unit, rounded up
    val total: Long,              // the same count for the whole target, at `start`
    val fractionRemaining: Double // 0.0..1.0, clamped
)

data class CountdownTarget(val start: Instant, val end: Instant)

/** Sleep + maintenance removed before a countdown is expressed in "free" hours.
 *  The ATUS-derived default lives in `:core:mortality`; `:core:time` only does the math,
 *  so the dependency never points upward. */
data class FreeTimePolicy(
    val sleepHoursPerDay: Double,
    val maintenanceRatio: Double,
)

interface CountdownModel {
    fun slices(target: CountdownTarget, now: Instant, zone: ZoneId): List<TimeSlice>
}
```
- `DAYS` / `WEEKS` count **elapsed** units (24 h, and 7 × 24 h). `MONTHS` is a **calendar** count
  via `ChronoUnit.MONTHS`, not 30-day months.
- `zone` is a parameter, not a hidden default. A calendar month only exists inside a time zone:
  the same instant can be 28 Feb in UTC and 1 Mar in Tokyo, so `MONTHS` would silently change
  answer if the engine read the system default. A test pins exactly that pair.
- `remaining` / `total` are `Long` **counts in the category's unit**, not `Duration`. A month has
  no fixed length, and inventing `Duration.ofDays(30)` would be a lie that callers would start
  doing arithmetic on. The renderer formats `3` + `DAYS`; it never does date math.
- **Two different numbers, deliberately.** `remaining` is rounded **up** to whole units;
  `fractionRemaining` comes from the **exact** instants and is clamped to `[0,1]`, so the arc
  moves continuously while the text ticks once a day.
  Rounding up is what makes the two agree: at 2.9 days left on a 10-day countdown the text says
  "3 days" and the arc reads 29% — 3/10. Truncating would print "2 days" beside a 29%-full arc,
  and would also read "0 days" during the last day.
- `fractionRemaining` is **identical for every category**: it describes elapsed time, not how
  that time is displayed. A property test pins this, so four arcs in a `CONCENTRIC` widget cannot
  drift apart.
- `NET_FREE_HOURS`: `(elapsed hours − sleep) × (1 − maintenance)`. `CountdownModel` receives a
  `FreeTimePolicy` **at construction**, so `:core:time` never references `:core:mortality` or its
  constants. Without a policy the slice is simply absent.
- Clamp negatives to zero; `end <= start` ⇒ `slices()` returns an **empty list** (there is no
  meaningful fraction, and a `0/0` would be `NaN`).

### 2.3 Mortality engine (`:core:mortality`)
```kotlin
enum class BiologicalSex { FEMALE, MALE, AVERAGE }

data class PersonProfile(
    val dateOfBirth: LocalDate,
    val sex: BiologicalSex,
    val countryCode: String,      // ISO-3166 alpha-2
    val sleepHoursPerDay: Double,
    val screenHoursPerDay: Double
)

data class LifeStat(
    val id: String,               // "days_left", "heartbeats_left", ...
    val label: String,
    val value: Long,
    val unit: String,
    val citation: Citation
)

interface LifeTableSource {        // backed by bundled assets, pluggable
    fun lifeExpectancy(sex: BiologicalSex, countryCode: String, age: Int): Double
    val citation: Citation
}

interface LifeStatsEngine {
    fun compute(profile: PersonProfile, now: Instant): List<LifeStat>
}
```
- `LifeTableSource` reads bundled SSA/WHO tables (assets), `Citation` carries source +
  table edition + retrieval note. Values are **looked up, never hardcoded in code**.
- Derived stats (heartbeats, sleep tax, books, seasons) compute from base life expectancy
  via documented formulas in `Methodology.md` (shipped next to the docs).

### 2.4 The `Citation` contract (`:core:model`)
```kotlin
data class Citation(
    val sourceName: String,   // "SSA Period Life Table 2021"
    val url: String,
    val detail: String        // edition / table / accessed note
)
```
Every `LifeStat` must carry one — enforced by the type, not convention.

---

## 3. Persistence schema (`:core:data`)

**Preferences DataStore + `kotlinx.serialization` JSON** (KISS vs Proto build tooling), one
file `lifeclock.preferences_pb`, three keys: `profile`, `configs`, `bindings`.

```kotlin
@Serializable
data class ProfileRecord(
    val dateOfBirthEpochDay: Long,
    val sex: String,
    val countryCode: String,
    val sleepHoursPerDay: Double,
    val screenHoursPerDay: Double
)

@Serializable
data class RingConfigRecord(          // one per rendered ring
    val category: TimeCategory,
    val style: RingStyleRecord
)

@Serializable
data class WidgetConfigRecord(        // named, reusable config authored IN THE APP
    val configId: String,             // stable identity (independent of placement)
    val name: String,                 // "My life clock", "Project X deadline"
    val type: WidgetType,             // COUNTDOWN | LIFE_CLOCK
    val startEpochMillis: Long,
    val endEpochMillis: Long?,        // COUNTDOWN only
    val profileRef: String?,          // LIFE_CLOCK -> profile key
    val template: RingTemplate,       // preset seed (see 4.1); CUSTOM = no lock
    val layout: WidgetLayout,         // SINGLE | STACK | GRID | CONCENTRIC
    val rings: List<RingConfigRecord>,// category + user appearance, in display order
    val granularity: RefreshIntent    // HOURLY | SIX_HOURLY | DAILY
)

@Serializable
data class WidgetInstanceBinding(     // a placed widget -> the config it renders
    val glanceId: String,             // stable per placed widget instance
    val configId: String              // no per-instance overrides (shared only)
)

@Serializable
data class RingStyleRecord(           // user-configurable appearance (feature C)
    val diameterDp: Int,              // ring size
    val strokeRatio: Double,          // border width as fraction of radius, 0.05..0.5
    val color: String,                // "#RRGGBB" | "theme:primary" etc.
    val trackColor: String,
    val label: String,                // name shown with the ring
    val labelPlacement: LabelPlacement, // INSIDE | UNDER | NONE
    val startAngleDeg: Float = -90f
)
```
Repositories:
```kotlin
interface WidgetConfigRepository {     // the catalog
    fun observe(configId: String): Flow<WidgetConfigRecord?>
    fun observeAll(): Flow<List<WidgetConfigRecord>>
    suspend fun save(config: WidgetConfigRecord)
    suspend fun delete(configId: String)      // blocked/guarded if any binding references it
}
interface WidgetBindingRepository {    // placement -> config
    fun observeBinding(glanceId: String): Flow<WidgetInstanceBinding?>
    fun observeAllBindings(): Flow<List<WidgetInstanceBinding>>
    suspend fun bind(glanceId: String, configId: String)
    suspend fun unbind(glanceId: String)
}
interface ProfileRepository {
    fun observe(): Flow<ProfileRecord?>
    suspend fun save(profile: ProfileRecord)
}
```
Widget state is never held in memory as truth — always recomposed from these flows.

### 3.1 Widget catalog & binding (feature D)

All settings live **in the app**. A widget is a dumb, stateless renderer of the
`WidgetConfigRecord` its binding points at.

- **Authoring (primary path):** in-app catalog — create / name / edit reusable configs with
  the appearance editor and live preview. Nothing is configured on the widget itself.
- **Add to home screen:** `:widget` declares a configuration Activity (`android:configure`)
  which the launcher launches with `ACTION_APPWIDGET_CONFIGURE` + `EXTRA_APPWIDGET_ID`. It
  presents a **picker** (list of saved configs + "New") and returns a result carrying the
  `appWidgetId`, then `bind(glanceId, configId)`. Because the config Activity is launched by
  the host, it **must** set a result with `EXTRA_APPWIDGET_ID` or the placement is cancelled.
- **Version/OEM robustness:** declare
  `android:widgetFeatures="reconfigurable|configuration_optional"` so API 31+ users can skip
  initial setup and later reconfigure. This flag is **not honored by every OEM** (e.g. Samsung
  still launches the picker) — so the picker is the real path; skipping is an enhancement.
- **Reconfigure:** a tap action on the widget reopens the picker for **that** `glanceId`
  (pre-selected on the bound config). The in-app "Your widgets" screen lists placed instances
  and can rebind / duplicate / delete them.
- **Shared semantics (chosen):** one config may back many placed widgets. Editing a
  `WidgetConfigRecord` re-renders **every** bound `glanceId` (call `GlanceAppWidget.update()`
  per binding). **No per-instance overrides** — a widget shows exactly its config.
- **Deletion guard:** deleting a config that still has bindings either blocks the delete or
  is confirmed and unbinds all of them (surfaced in the manager UI).

---

## 4. Ring-rendering pipeline (`:widget`)

Because Glance's `CircularProgressIndicator` is **indeterminate-only**, we rasterize the
determinate ring ourselves.

> **Vocabulary — ring vs arc.** *Ring* is the whole circular element: track + drawn segment +
> stroke, i.e. the thing the user configures and the bitmap contains. *Arc* is a drawn segment
> of that circle — the filled portion, or the 360° track (`Canvas.drawArc`). So: **ring** for the
> contract and the style types (`RingStyle`, `RingRenderer`), **arc** whenever we mean the
> geometry. One word per concept; they are not synonyms.

### 4.1 Customization spectrum, layouts & presets (feature C)

The ring is **parameterizable, not free-form**: widgets are `RemoteViews` (Glance compiles
down to `RemoteViews`), which have **no absolute positioning and no custom drawing** beyond
a bitmap we render ourselves. We therefore expose a bounded set of knobs plus **preset
templates**, rather than promising a canvas editor.

**Per-ring knobs (free-form):** diameter, border width (`strokeRatio`), fill color, track
color, label text, label placement, start angle.

**Layout arrangement (`WidgetLayout`):** `SINGLE`, `STACK` (column), `GRID`, `CONCENTRIC`
(multiple arcs nested in one bitmap).

**Preset templates (`RingTemplate`)** seed a full `WidgetConfigRecord`; user may then tweak
any knob (choosing a template sets `template = CUSTOM` once edited):

| Template | Layout | Look |
|---|---|---|
| `CLASSIC` | SINGLE | one ring, theme primary, label `UNDER`, track shown |
| `COMPACT` | SINGLE | ring with value+label `INSIDE`, thin stroke |
| `BOLD` | SINGLE | thick stroke, no track |
| `MINIMAL` | SINGLE | ring only, no label, no track |
| `CONCENTRIC` | CONCENTRIC | nested rings, one per category, shared legend |
| `GALLERY` | GRID | grid of rings, label `UNDER` each |
| `CUSTOM` | any | no preset lock; all knobs exposed |

**Platform ceiling (documented, out of scope):** arbitrary x/y placement, rotated/arc text,
live animated sweep, and per-ring independent dragging are **not** supported by
`RemoteViews`/Glance. Gradients, dashed arcs, round caps and tick marks are *not* user
primitives — they would have to be baked into the bitmap as renderer variants.

### 4.2 Appearance model (feature C)
```kotlin
enum class LabelPlacement { INSIDE, UNDER, NONE }

/** Resolved, concrete appearance for one ring (theme colors already materialized). */
data class RingStyle(
    val diameterPx: Int,           // user diameter (dp -> px at compose time)
    val strokeRatio: Double,       // border width / radius  (0.05..0.5)
    val color: Int,                // concrete ARGB (bitmap needs a color)
    val trackColor: Int,
    val label: String,
    val labelPlacement: LabelPlacement,
    val startAngleDeg: Float = -90f
)

sealed interface RingColor {       // config stores this; resolved before rasterizing
    data class Fixed(val argb: Int) : RingColor
    data class Theme(val role: String) : RingColor // e.g. "primary", "secondary"
}
```
- Colors may be fixed OR theme-role. Because a `Bitmap` needs a concrete color, the
  `RingColor` is **resolved against `ColorProvider`/dynamic color at compose time**, then
  passed to the renderer as `Int`.

### 4.3 Renderer
```kotlin
interface RingRenderer {
    fun render(style: RingStyle, fraction: Double): ImageBitmap   // Canvas.drawArc
}
```
- `CanvasRingRenderer`: allocates a `Bitmap` of `diameterPx`, `drawArc` for track (full
  360°) and progress (`sweep = 360 * fraction`), anti-aliased, round stroke caps.
- **Caching:** LRU keyed by `(fractionBucket, diameterPx, strokeRatio, colors)` where
  `fractionBucket` uses the widget's declared granularity, so identical frames are not redrawn.
- **Glance path:** `ImageProvider(bitmap)`.
- **Fallback path (raw `RemoteViews`):** `setImageViewBitmap` when Glance can't express a
  layout detail.

### 4.4 Labels — rendered as text, not baked into the bitmap
The ring name is **never rasterized into the bitmap**; it is a Glance `Text`, so it stays
crisp, themeable, and accessible:
- `INSIDE` → a Glance `Box`: ring image + centered `Text`.
- `UNDER` → a Glance `Column`: ring image, then `Text` beneath it.
- `NONE` → ring only.

### 4.5 Live numeric readout
`RemoteViews` `Chronometer` with `setBase(end)` + `setCountDown(true)` — the platform ticks
it with **no wakeups from us**.

Widget composition (Glance) resolves: the bound `WidgetConfigRecord` from the repository + `slices` from
`CountdownModel` → per-ring `RingStyle` → `RingColor` resolved to concrete color →
`RingRenderer` → `ImageProvider`, arranged with a `Box`/`Column` per `LabelPlacement`, plus
a `Chronometer` for the live countdown.

---

## 5. Scheduling state machine (`:core:scheduler`)

States per widget instance: **`IDLE → SCHEDULED → REFRESHED`**.

| Trigger | Source | Action |
|---|---|---|
| `onUpdate` (≥30 min) | framework | recompute → refresh ring + reset Chronometer base |
| `onEnabled` / `onDisabled` | framework | start/stop unique `WorkManager` refresh |
| `onDeleted` | framework | `unbind(glanceId)` (the `WidgetConfigRecord` persists) |
| `onAppWidgetOptionsChanged` | framework (resize) | re-rasterize for new size |
| config edited (app) | `:feature:config` | `GlanceAppWidget.update()` for **every** bound `glanceId` |
| config deleted (app) | `:feature:config` | guard: block if bound, else unbind all + delete |
| Periodic (≥15 min) | `WorkManager` unique work | recompute; refresh only if ring bucket changed |
| Boundary alarm | `setAndAllowWhileIdle` (inexact) | refresh at next midnight/hour |
| `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED`, `DATE_CHANGED` | system | reschedule all + refresh all |

Rules:
- **No exact alarms** (avoids `SCHEDULE_EXACT_ALARM` on API 31+ and battery cost).
- Ring refreshes are gated by `RefreshIntent` granularity; Chronometer text is left to tick
  itself. This is the core battery-honesty mechanism.
- Every refresh is idempotent and derives from persisted state (survives process death).

---

## 6. DI graph (Hilt)

| Component | Provides |
|---|---|
| `CoreModule` | `Clock`, `CountdownModel`, `LifeStatsEngine`, `LifeTableSource` (asset-backed), `RingRenderer` |
| `DataModule` | `DataStore`, `WidgetConfigRepository`, `WidgetBindingRepository`, `ProfileRepository` |
| `WorkModule` | `HiltWorkerFactory` wiring for refresh workers |
| `AppModule` | app-scoped singletons; no widget truth in memory |

Workers obtain repositories via Hilt. `GlanceAppWidget` is instantiated by the system, so
any dependencies it needs must be resolved through `EntryPointAccessors` (Glance has no
constructor injection).

---

## 7. Threading & statelessness

- Engines are **pure and synchronous** (`:core:time`, `:core:mortality`).
- IO is `suspend`/`Flow` via DataStore; widget exposes state through `provideGlance`'s
  coroutine scope.
- No mutable global state; no long-lived services; recomposition from persisted truth.

---

## 8. Test strategy (TDD)

| Layer | Tooling | What it proves |
|---|---|---|
| `:core:time` | JUnit + Kotest property tests | DST, leap years, month ends, tz, zero/negative, far-future; fraction clamping |
| `:core:mortality` | JUnit + golden files | computed remaining-days matches SSA `e_x` rows within tolerance; net-free hours reproduces published ATUS averages |
| `:widget` renderer | Robolectric + pixel/hash golden | arc sweep corresponds to fraction; stroke width/diameter; track/progress colors |
| Appearance mapping | JUnit | dp→px diameter, `strokeRatio` clamp, `RingColor.Theme`→concrete resolution, label placement |
| Templates & layout | JUnit + Roborazzi | each `RingTemplate` seeds expected `WidgetConfigRecord`; `CONCENTRIC` draws N nested arcs; `GRID`/`STACK` arrangement |
| Widget composition | `runGlanceAppWidgetUnitTest` + Roborazzi screenshots | layout per size, Chronometer presence, ring count, label INSIDE vs UNDER |
| Catalog & binding | JUnit + in-memory repos | config CRUD; **two bindings to one config both re-render after an edit**; rebind moves an instance; delete guard blocks/clears bindings; picker returns a result carrying `EXTRA_APPWIDGET_ID` |
| Config UI | Compose UI tests (`:feature:config`) | editing diameter/color/stroke/label updates preview and persists; "Your widgets" lists/rebinds/duplicates/deletes |
| Resilience | Instrumented on device + adb | reboot, DST, timezone, manual clock change, Doze, process death |
| Budget | Battery Historian + `dumpsys appwidget` | wakeups within declared cadence; no ANR |

Fakes: `FakeClock`, asset-backed `LifeTableSource` fixture, in-memory repositories.
Golden assets (SSA/WHO/ATUS extracts) live under `core/mortality/src/test/resources/`.

---

## 9. Risks carried from the plan → mitigations

- **Ring bitmap memory × N instances** → LRU cache + granularity bucketing + size-keyed.
- **`Chronometer` launcher quirks** → M0 spike per launcher; text fallback if unsupported.
- **Month ambiguity** → surfaced in config UI copy ("calendar months").
- **Data licensing/updates** → `LifeTableSource` pluggable; edition recorded in `Citation`.
- **Play Store widget preview** → static `previewImage` + per-size previews in manifest.
