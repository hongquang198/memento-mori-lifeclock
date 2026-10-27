# Tasks — Memento Mori Life Clock

## How to read this file
This is a **learning-oriented** build queue, not just a requirements partition. Each
milestone is a self-contained unit you can run and review, and answers three questions:
**Goal** (what we're building), **Learn** (the stack concepts it exists to teach you), and
**Review focus** (what to actually look at when you review, given you're new to native
Android).

**TDD applies to every item:** the `Tests:` task is written and failing *before* its paired
`Impl:` task. `Tests:`/`Impl:` pairs are kept adjacent so you can read the intent first.

**Milestone gate:** a milestone is only marked done when its review questions in
`docs/concepts.md` are answered **correctly** (every question ✅). Green tests are necessary,
not sufficient.

**Review cadence (agreed):** I work autonomously *within* a milestone; we **stop at each
milestone boundary**. **M0 is a hard, personal review** (it teaches the platform's "why");
M1/M5 can be skimmed; M2/M4 deserve a close read. Each gate shows: what changed, the new
concepts, test output, and what to try on a device.

Legend: `[ ]` todo · `[~]` in progress · `[x]` done · `[!]` blocked.

---

## M0 — Platform de-risk spike
**Goal:** prove the three platform facts the whole architecture rests on, before committing
to it. Throwaway code is fine.
**Learn:** how Android widgets actually work — the `RemoteViews` ceiling, the app-process
vs widget-host-process split, why Glance's circular indicator is indeterminate-only, why a
"live animated ring" is impossible, and how Doze limits update cadence.
**Review focus:** read the spike code + `docs/spikes/M0-findings.md`. This is where every
"why does the design look like that?" answer lives.

- [x] **T0.1** Create Gradle skeleton: `:core:model`, `:core:time`, `:core:mortality`, `:core:data`, `:core:scheduler`, `:widget`, `:feature:config`, `:app`; minSdk 26, Kotlin, Hilt, Glance.
- [x] **T0.2** Spike: render a determinate ring via `Canvas.drawArc` → `Bitmap` → Glance `ImageProvider`; confirm it displays on a **physical device**.
- [x] **T0.3** Spike: `RemoteViews` `Chronometer` with `setCountDown(true)` + `setBase(end)`; confirm it ticks without our code running.
- [x] **T0.4** Measure: place widget, screen off, observe actual update cadence under **Doze/App Standby** (`adb shell dumpsys appwidget`, Battery Historian).
- [x] **T0.5** Record findings + screenshots in `docs/spikes/M0-findings.md`; list any residual blockers.
- **DoD:** ring + Chronometer proven on device; cadence measured; no unknown blockers remain. **Met**, with 3 residual items carried into M2 (`M0-findings.md` §Residual blockers). Design deltas: no exact alarms (best-effort cadence only); boot rendering needs `BOOT_COMPLETED` + `WorkManager` self-heal; all internal widget broadcasts must be explicit intents. **Closed 2026-10-23** — milestone gate passed 8/8 (`docs/concepts.md`), including the two questions that took several passes: `CancellationException` is swallowed into an ordinary result, and `super.onUpdate()` is the unreliable path while the enqueued worker is the load-bearing one.

---

## M1 — Time engine (`:core:time`, pure Kotlin, TDD)
**Goal:** the pure time math — turning an interval into days/weeks/calendar-months/net-free
hours with ring fractions — decoupled from Android entirely.
**Learn:** Kotlin data/sealed types; `java.time` (`Instant`, `Duration`, `ChronoUnit`); why
injecting a `Clock` makes time deterministic and testable; Kotlin property testing (Kotest);
why a *pure* module compiles and tests in milliseconds.
**Review focus:** contrast each construct with its Dart/Flutter equivalent; note that this
module has **zero** Android imports — the boundary is the lesson.

- [x] **T1.0** Define `Clock` and `FakeClock` (test fixture).
- [x] **T1.1** Tests: `DAYS` slice value + `fractionRemaining` for start/end/now cases.
- [x] **T1.2** Impl: `CountdownModel.slices()` — days, clamping, zero/negative handling.
- [x] **T1.3** Tests: `WEEKS` (whole weeks) incl. partial-week boundary.
- [x] **T1.4** Impl: weeks.
- [x] **T1.5** Tests: `MONTHS` = **calendar** months (`ChronoUnit.MONTHS`), month-end, leap year.
- [x] **T1.6** Impl: months.
- [x] **T1.7** Tests: DST transitions, timezone-sensitive `Instant` handling, far-future dates.
- [x] **T1.8** Impl: fix any tz/DST defects; `NET_FREE_HOURS` slice (sleep + maintenance subtracted).
- [x] **T1.9** Property tests (Kotest): `fractionRemaining ∈ [0,1]`, monotonic decrease over time.
- **DoD:** **Met** — 31 tests green in ~0.5 s; `:core:time` has no Android dependency and no
  `android.*` import. Two contract changes were forced by the work itself: `TimeSlice.remaining` /
  `total` are `Long` **counts**, not `Duration` (a month has no fixed length), and `slices()` takes
  a `ZoneId` (a calendar month does not exist without a zone). Both are recorded in `design.md` §2.2.
  **Closed 2026-10-23** — gate 7/7 (`docs/concepts.md`). The quiz produced one real defect: the
  `ceilHours` comment cited 70 h / 8 h / 25 % as a floating-point failure, but that is exactly
  `35.0` in IEEE, and the *relative* half of the tolerance only mattered past ~1000 years — removed,
  replaced by a single `1e-9` epsilon with a verified example (1000 min, sleep 0, maintenance 0.1).

---

## M2 — Countdown widget v1 (`:widget`, `:core:data`, `:core:scheduler`)
**Goal:** the first end-to-end, placeable countdown widget — catalog config → binding →
ring + live text on the home screen, refreshed on a sane cadence.
**Learn:** Gradle multi-module wiring; Jetpack DataStore + `kotlinx.serialization`; how Glance
(declarative widget UI) differs from Compose and compiles to `RemoteViews`; the bitmap bridge
for the ring; `WorkManager`/`AlarmManager`/Doze scheduling; broadcast receivers and the widget
lifecycle; the config-Activity contract (`EXTRA_APPWIDGET_ID`); Hilt dependency injection.
**Review focus:** the **config/binding split** (why a widget owns no settings), the
**scheduling state machine**, and the Glance→`RemoteViews` translation in the renderer.

### Persistence
- [x] **T2.1** Tests: `WidgetConfigRecord`/`WidgetInstanceBinding`/`RingStyleRecord` serialize round-trip (kotlinx).
- [x] **T2.2** Impl: DataStore + `WidgetConfigRepository` (catalog CRUD) + `WidgetBindingRepository` (bind/unbind) + in-memory fakes.
- [x] **T2.3** Tests: two bindings → one config; editing the config re-renders **both**; deleting a bound config is guarded; rebinding moves an instance.

### Ring rendering
- [x] **T2.4** Tests: `CanvasRingRenderer` — arc sweep ∝ fraction; stroke width; colors (golden/hash).
- [x] **T2.5** Impl: `RingRenderer` with LRU cache keyed by `(fractionBucket, diameter, strokeRatio, colors)`.
- [x] **T2.6** Tests: `RingColor.Theme` → concrete ARGB resolution; clamp `strokeRatio`.
- [x] **T2.7** Impl: color resolution + clamps.

### Widget composition
- [x] **T2.8** Tests (`runGlanceAppWidgetUnitTest`): widget composes ring + Chronometer for a config.
- [x] **T2.9** Impl: `CountdownWidget : GlanceAppWidget` + `GlanceAppWidgetReceiver`; `provideGlance` reads repo flow.
- [x] **T2.10** Impl: `CountdownWidget`'s static `previewImage` + `appwidget-provider` XML (`updatePeriodMillis` = 0).

### Scheduling
- [x] **T2.11** Tests: scheduling state machine transitions (IDLE→SCHEDULED→REFRESHED) with fake triggers. → `core/scheduler`: 15 tests (boundaries + cycle).
- [x] **T2.12** Impl: unique periodic `WorkManager` work (15 min) + inexact boundary alarm (`setAndAllowWhileIdle`); gate ring redraw by `RefreshIntent` granularity. → policy in `:core:scheduler` (pure), mechanism in `:widget` (`WidgetRefreshScheduler`, `RefreshGate`, `CountdownRefreshWorker`); 6 gate tests.
- [x] **T2.13** Impl: receivers for `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED`, `DATE_CHANGED` (+ `MY_PACKAGE_REPLACED`) → enqueue a forced refresh; the boot path also re-arms the heartbeat. Split into `WidgetRefreshReceiver` (never draws) and `CountdownRefreshWorker` (draws), productionising the mechanism proven in spike §9.
  - **Still open:** the framework's warm `onUpdate` race. Left as-is (super called) rather than deferring it to WorkManager un-measured; the deferral latency needs a device.
  - **Still open:** the **visual** check of the boot path (`§9` showed `provideGlance` running ≠ a complete frame). Needs the phone.
  - **Still open (found in the M2a gate):** a failed draw is **terminal**. `draw()` throws → `tick` never returns → `armBoundary` is skipped → the worker returns `Result.failure()`, so the only retry is the 15 min heartbeat. Either arm a near-term retry in the `catch`, or return `Result.retry()`.
- [x] **T2.14** Impl: `onDeleted` → `unbind(glanceId)` in `UnbindWidgetsWorker` (config persists). `onAppWidgetOptionsChanged` deliberately not overridden: the ring is rasterized at the configured diameter, so a resize does not change it; the 15 min heartbeat covers the rest.

### Config Activity & binding (feature D)
- [x] **T2.15** Tests: picker config Activity returns a result carrying `EXTRA_APPWIDGET_ID`; choosing a config calls `bind()`. (`ConfigPickerTest` 6, `ConfigActivityTest` 5 — Robolectric, incl. the tap-intent contract.)
- [x] **T2.16** Impl: `:widget` **picker** config Activity (`android:configure`) listing saved configs; `widgetFeatures="reconfigurable"`; request a refresh after binding.
  - **Deviation from the plan, both decided on the device (Pixel 8a, API 36):**
    - **`configuration_optional` dropped.** With it the launcher placed the widget *without* running the picker, leaving a placeholder that nothing could configure. The widget is useless unbound, so placement must configure.
    - **No "New" row.** Without an editor (feature C) a new config would be an indistinguishable copy; it returns with the editor.
  - Seeding: an empty catalog gets one placeholder countdown (`ConfigPicker.SEED_CONFIG_ID`, "First year") so the first placement is not a dead end.
- [x] **T2.17** Impl: widget tap action → reopen picker for that `glanceId` (pre-selected, marked `✓`).
  - The system affordance is not enough: long-pressing an unbound widget offered only "Remove", and tapping did nothing. The widget now carries `actionStartActivity(configureIntent(...))` itself — the same intent the launcher uses at placement.
- [x] **T2.18** Instrumented: **survives reboot, timezone change, manual clock change** on a physical device.
  - **Done on device (Pixel 8a, Android 16):** picker → bind → draw (`refresh(configure): drew=true widgets=1 next=…T17:00:00Z` = local midnight); tap → picker with `✓`; timezone change → `refresh(TIMEZONE_CHANGED): drew=true widgets=1`; manual clock +2 h → `refresh(TIME_SET): drew=true widgets=1` and `fraction` moved 0.99991 → 0.99968; clock restored.
  - **Reboot:** the frame was correct after boot + unlock (`render(16)` at 12:47:31), but the draw was the **periodic heartbeat**, not the boot receiver — `refresh(BOOT_COMPLETED)` was never logged. `BOOT_COMPLETED` is withheld until first unlock (the receiver is deliberately not `directBootAware`) and the phone stayed locked through the whole heartbeat window, so the boot branch never got a clean shot. **Closed by test instead of another reboot:** `WidgetRefreshReceiverTest` fires every handled action at the receiver and asserts exactly one immediate refresh (plus the heartbeat for boot/reinstall). What stays unproven is Android's *delivery* of `BOOT_COMPLETED` — platform contract, not our code — and the guarantee holds either way, because the heartbeat covers the same window within 15 minutes.
  - **Found and fixed here — the heartbeat was a silent no-op.** `GlanceAppWidget.updateAll` reads Glance's own id registry, which is written by the receiver's `onUpdate` (the callback M0 proved unreliable). On this device the registry was empty: `updateAll` updated zero widgets while the gate logged `drew=true`, and the widget sat on its placeholder. `updatePlacedWidgets` now enumerates `AppWidgetManager.getAppWidgetIds(provider)` and logs the count (`widgets=N`), so "drew nothing" can never look like "drew fine" again. **Guard:** `UpdatePlacedWidgetsTest` asserts one draw per id the platform reports, and it was mutation-checked by restoring `updateAll` — the test fails with `expected:<[1, 2]> but was:<[]>`. Robolectric cannot observe Glance's apply (`getViewFor` stays on the initial layout, because composition is handed to a session scope), so the seam is the assertion and the launcher receipt stays a device observation.
  - **Found and fixed here — the ring overflowed its cell.** A 2-column cell reports 140 dp; the default ring is 160 dp, so the arc was clipped on both sides. `fitDiameterDp` caps the draw to `cell - 16 dp` (never grows a small ring, never rewrites the config). Log now carries `cell=140dp` and the fitted bitmap size.
  - **Found and fixed here — a package move silently killed the receiver.** Reorganising `:widget` into `config/ content/ render/ refresh/` rewrote every `package` line but not `android:name` in the manifest. The build stayed green; the first broadcast after install died with `ClassNotFoundException: ...widget.WidgetRefreshReceiver` and a `FATAL EXCEPTION` in the app process. `ManifestComponentsTest` now resolves every declared activity and receiver with `Class.forName`, so the compiler's blind spot has a test.
- **Carried into T2.15:** a config may hold N rings, but every ring shares one target and
  `TimeSlice.fractionRemaining` is identical across categories, so N rings currently draw N
  *identical* arcs. Decide what a second ring means (extra targets / a different ratio / pure
  decoration) before templates seed multi-ring configs. `WidgetLayout` is also still unread:
  `CONCENTRIC` needs one `Canvas` pass drawing N arcs, not N bitmaps.
- **DoD:** a countdown widget stays correct across the three device events in T2.18; adding shows the picker and binds.

---

## M3 — Memento-mori engine + life-clock (`:core:mortality`)
**Goal:** the credibility engine — compute life-remaining statistics from primary sources and
render them as a multi-ring life-clock.
**Learn:** data-driven engines with bundled assets; source-bound constants with a `Citation`
type; **golden-file** testing; **reconciliation** testing (your output vs a published table);
how to make a claim independently verifiable rather than hardcoded.
**Review focus:** the derivation and reconciliation of the numbers — this is the product's
entire credibility. Check `Methodology.md` against the tests.

- [x] **T3.0** Bundle `e_x` tables + `Methodology.md`. **Deviation:** `ssa.gov` returns 403 to every
  programmatic client, so the source is **UN WPP 2024** (period 2023, medium variant) - the upstream
  of WHO's estimates and one table for all 237 countries. `tools/build_life_tables.py` regenerates the
  183 KB subset from the 138 MB download; the citation, filters and vintage are in `docs/Methodology.md`.
- [x] **T3.1** Golden tests: `LifeTablesTest` pins US `e_40` for both sexes, linear interpolation
  inside a five-year group, null for unknown country / age past the table, monotonicity.
- [x] **T3.2** Impl: `CsvLifeTableSource` + `Citation` (source, vintage, table, url, retrieved, licence).
- [x] **T3.3** Tests: `days/weeks/months` are one ratio with three divisors; pinned integers
  (14,367 days / 2,053 weeks / 473 months for the reference profile). Cross-source reconciliation
  (UN `e_0` vs WHO `e_0`) still to add - needs the WHO series pinned to a vintage.
- [x] **T3.4** Impl: `LifeStatsEngine.compute()` - zone-aware age, `e_x x 365.2425`, round up.
- [x] **T3.5** Tests: heartbeats (`days x 1440 x 72`, flagged `MODEL` against the AHA 60-100 range)
  and seasons (`days / 91.31`, `SOURCE`). Sleep tax and books are **not** implemented: books have no
  source that survives scrutiny, sleep tax is the ATUS split below.
- [x] **T3.6** Impl: `DerivedStat` carries `StatKind.SOURCE` vs `MODEL`, so the UI can label a choice
  differently from a fact.
- [ ] **T3.7** Tests: `NET_FREE_HOURS` reproduces published **BLS ATUS** averages within tolerance.
- [ ] **T3.8** Impl: ATUS-derived maintenance ratio; `NET_FREE_HOURS` stays absent from the engine
  until then, and the resolver drops a ring that asks for it rather than drawing an empty arc.
- [x] **T3.9** Tests: `ProfileValidatorTest` - future birth, age > 120, sleep/screen range, and
  sleep + screen exceeding the day.
- [x] **T3.10** Impl: `ProfileRepository` (DataStore + in-memory) with the delete guard
  (`BlockedByConfigs`). The *editing* UI is feature C; M3 ships a named sample profile.
- [x] **T3.11** Impl: `LIFE_CLOCK` branch in `WidgetSpecResolver` + `WidgetStateLoader`; the ring's
  label becomes its own count ("14,068 days"), no Chronometer. Device: `render(16): fraction=0.5112
  bitmap=326x326 cell=140dp label="14,068 days"`.
- **DoD:** computed stats reconcile to the table (golden + conversion tests); life-clock widget
  renders on device. ATUS reconciliation (T3.7/T3.8) is open and tracked.

---

## M4 — Appearance, presets & UX polish (`:widget`, `:feature:config`)
**Goal:** make it configurable and good-looking — the appearance editor, preset templates,
layout arrangements, and the in-app catalog/instance manager.
**Learn:** Compose UI state + live preview; sealed types driving preset/layout behavior;
the config-vs-instance UX separation; design tokens & dynamic color; accessibility; how the
same widget renders differently across launchers.
**Review focus:** the catalog/binding UX, and the claim "editor preview matches the placed
widget" (T4.16) — a common place for widgets to drift from their preview.

### Appearance editor (feature C)
- [x] **T4.1** Tests: `RingPresetsTest` pins every preset (diameter, stroke, placement, layout, rings).
- [x] **T4.2** Impl: `RingPresets` in `:core:model` (Classic, Compact, Bold, Minimal, Concentric,
  Gallery) + `apply()` that keeps identity/target and replaces appearance; `CUSTOM` is an outcome,
  so seeding it throws instead of inventing a look.
- [x] **T4.3** Tests: `LayoutPlanTest` - SINGLE draws the first ring only, STACK/GRID draw all,
  CONCENTRIC nests with a 0.22 step and never below 0.3. **Deviation:** CONCENTRIC composes N
  bitmaps in a `Box` rather than drawing N arcs in one `Canvas` pass - identical pixels, no new
  renderer, and each ring keeps its own style/colour.
- [x] **T4.4** Impl: `LayoutPlan` (pure) + the four arrangements in `WidgetContent`; `WidgetState.Ready`
  carries the config's layout.
- [ ] **T4.5** Tests: label placement `INSIDE` (`Box` overlay) vs `UNDER` (`Column`); `NONE` renders no text.
- [ ] **T4.6** Impl: label widget tree per placement (text **not** baked into bitmap).
- [ ] **T4.7** Tests (Compose UI): editing diameter/color/stroke/label updates the **live preview** and persists.
- [ ] **T4.8** Impl: editor controls (diameter slider, color picker, stroke slider, label field, placement, layout, template picker) + live preview.
- [ ] **T4.9** Tests: `strokeRatio` bounds (0.05..0.5) and diameter bounds clamp in UI and engine.

### Widget catalog & instance manager (feature D)
- [ ] **T4.10** Tests (Compose UI): catalog screen creates/renames/duplicates/deletes configs.
- [ ] **T4.11** Impl: catalog screen (list of `WidgetConfigRecord`) + "New config" entry point.
- [ ] **T4.12** Tests: "Your widgets" lists placed instances; rebind changes the rendered config; delete guards a bound config.
- [ ] **T4.13** Impl: "Your widgets" manager screen (list/rebind/duplicate/unbind) calling `GlanceAppWidget.update()` per affected `glanceId`.

### Polish
- [ ] **T4.14** Impl: dynamic color / `ColorProvider` theming; verify `theme:` color roles render.
- [ ] **T4.15** Impl: a11y — content descriptions, large-font survival.
- [ ] **T4.16** Impl: per-size widget previews (Android 12+ `previewLayout` + static fallback); verify preview matches placed widget.
- [ ] **T4.17** Manual: launcher matrix (Pixel Launcher, Samsung One UI, Nova, Lawnchair) — screenshot each template.
- **DoD:** every template renders correctly on all four launchers; editor preview matches the placed widget.

---

## M5 — Battery & resilience hardening
**Goal:** prove the app is a good Android citizen and survives the messy real world.
**Learn:** Battery Historian & `dumpsys`; Doze/App Standby behavior; process-death recovery
(stateless design pays off here); ANR hygiene (`goAsync`/WorkManager); memory bounding.
**Review focus:** the recorded **evidence** that the declared update budget is met — numbers,
not assurances.

- [ ] **T5.1** Battery Historian run: wakeups within the declared cadence budget (no per-minute wakeups).
- [ ] **T5.2** `adb shell dumpsys appwidget` review: update counts match design.
- [ ] **T5.3** Doze/App Standby test: widget correct after long idle.
- [ ] **T5.4** Process-death test: kill app; widget recomposes from DataStore correctly.
- [ ] **T5.5** ANR audit: receivers use `goAsync`/WorkManager; no main-thread IO.
- [ ] **T5.6** Memory: verify ring LRU cache bounds with many widget instances.
- [ ] **T5.7** Record the budget + results in `docs/BatteryBudget.md`.
- **DoD:** wakeups within budget, no ANR, all resilience tests pass; budget documented.

---

## Cross-cutting (run on every PR)
- [ ] `./gradlew test` — pure-JVM unit tests.
- [ ] `./gradlew :widget:verifyRoborazziDebug` (or Paparazzi) — screenshot goldens.
- [ ] `./gradlew lint` — Android lint clean.
- [ ] Confirm `:core:time` and `:core:mortality` still have **no Android imports** (architecture guard test).
