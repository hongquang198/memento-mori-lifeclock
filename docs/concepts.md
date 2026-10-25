# Concepts — what I can actually explain

A milestone is not finished because the tests are green. It is finished when I can explain
**why** the code looks the way it does.

## How this works

- **6–8 questions per milestone**, asked in chat when the milestone's code is done.
- Each answer is rated: **✅ correct** · **🟡 partial** (a follow-up is asked on that point) ·
  **❌ incorrect**.
- **A milestone is only marked done when every question is ✅.** A 🟡 gets a follow-up until it
  is solid; a ❌ is recorded below with the correction, so the same gap can't survive into the
  next milestone.
- Saying **"skip"** is allowed and counts as ❌. It is better than a guess that hides a gap.
- Answers go in my own words. The recorded note is the *idea*, not the wording I gave.

## M0 — the Glance cold-start race and the boot rescue

| # | Concept | Verdict | What I had wrong / what to remember |
|---|---|---|---|
| 1 | `goAsync` budget: why a cold start loses the composition | ✅ | Correct. Add: the `RingCache` and the gate die with it too, so nothing remembers what was last drawn. |
| 2 | `runCatching` swallowing `CancellationException` | ✅ | Closed on the third pass. Two signals, not one: swallowing makes the caller see an ordinary `Result.failure()`, so it cannot tell "cancelled" from "failed" — and **cancellation is cooperative**, so nothing actually stops. In Glance's case `runCatching` turned "cut off mid-composition" into "returned fine". |
| 3 | `updatePeriodMillis = 0` + the designed `initialLayout` | ✅ | Correct. Sharpest form: with `0` the system will never ask again — only our own triggers (boot, time change, heartbeat) recover it. |
| 4 | "`provideGlance` ran" ≠ "the launcher drew a correct frame" | ✅ | Correct: the launcher paints, so our logs can't observe it. §9 accepted the fix with four reboots and screenshots, because visual evidence is the only evidence. |
| 5 | Rescue vs repair: why the boot path doesn't fix `onUpdate` | ✅ | Closed on design grounds, and the disagreement was useful. **The app works — because `refreshNow()` enqueues `CountdownRefreshWorker` → `updateAll()`, not because of `super`.** Discriminating test: drop `refreshNow`, keep `super` → §6's placeholder; keep `refreshNow`, drop `super` → same frame, one composition cheaper. `super` only buys Glance's `updateManager()` bookkeeping (which is why its KDoc insists), so keeping it is a **trade, not a mistake**. My "two writers race" framing was too strong — the real hazard is composition vs. process lifetime. |
| 6 | Receiver enqueues, worker renders — and why the split is mandatory | ✅ | Correct. Precision: the process is killed once the broadcast completes, and Glance 1.1.1 composes inside that window. |
| 7 | Inexact boundary alarms: why not `setExactAndAllowWhileIdle` | ✅ | Correct: permission (`SCHEDULE_EXACT_ALARM`, user-granted) + exact alarms are reserved for user-visible, time-critical things (alarms, calendar, timers). A widget flipping three minutes late is not a bug. |
| 8 | What the boot experiment measured, and what it did not | ✅ | Good. The sharpest limit: it demonstrates a *rescue* — `onUpdate` never succeeded in either arm. |

**Status: closed** — 8/8 (2026-10-23). The one sentence to carry forward: *a frame is only guaranteed if the system is tracking the work; a broadcast is not a place to compose.*

## M1 — countdown math (`:core:time`)

Asked when M0 is closed, since M1 was built before this rule existed.

| # | Concept | Verdict | What I had wrong / what to remember |
|---|---|---|---|
| 1 | `Long` counts, not `Duration` | ✅ | Counts are the **text**; the arc is the exact ratio. Rounding up keeps the text from reading "0" while time remains — the text is a bound, the arc is the truth. |
| 2 | `zone` is a parameter | ✅ | Sharpened: a test must be a function of its inputs, not of the machine it runs on — same assertion, two results, depending on the zone. |
| 3 | `end <= start` ⇒ empty list | ✅ | Closed after two corrections: `0.0 / 0.0` is **NaN**, not a crash; `coerceIn` returns NaN because every NaN comparison is false; `NaN <= 0` is false, so the guard passes and the arc is drawn with a NaN sweep. Empty is the better contract: representable, testable, and it cannot leak into geometry. |
| 4 | ceil for counts, fraction computed separately | ✅ | (a) counts are the text, and rounding up is the honest direction there; (b) the arc uses `fractionRemaining` — 1 s before the end the counts would say half full. |
| 5 | `ceilTo` uses integer arithmetic | ✅ | Mechanism named. My question was ill-posed: for day/week divisors no exact multiple misrounds (0 mismatches over 20k multiples per unit, verified), so no failing input exists. Integer maths is still the right call; the comment no longer claims the impossible. |
| 6 | calendar months, rounded up | ✅ | Truncation at month-end (31 Jan → 28 Feb is 0 whole months); `plusMonths(whole)` reveals the remainder. |
| 7 | float tolerance in `ceilHours` | ✅ | Caught as over-engineering, and that judgement was right: at ~10^6 hours a double's spacing is ~10^-10, below one `1e-9` epsilon, so the relative term covered a range a life clock never reaches. Removed — `ceilHours` now subtracts a single constant. |

**Status: closed** — 7/7 (2026-10-23). The sentence to carry forward: *the number you read and the number you draw come from different computations — counts for text, exact ratio for geometry — and a degenerate case must be a representable value, not NaN.*

## M2 — widget, renderer, scheduling

### M2a — prerequisite gate (asked before T2.15)

Five questions on what the config Activity is about to build on. Passing this does **not** close M2.

| # | Concept | Verdict | What I had wrong / what to remember |
|---|---|---|---|
| 1 | config vs binding | ✅ | Edit propagates to every widget bound to that `configId` on its next render — no per-instance overrides. Delete is refused (`BlockedByBindings`) until the last binding goes. |
| 2 | one store for configs + bindings | ✅ | The failure to avoid is a **dangling binding**: a `bind()` landing between `delete()`'s check and its write would survive a config that no longer exists. |
| 3 | why the ring is a `Bitmap` | ✅ | `RemoteViews` is a closed set of views with **no `Canvas`**; and a widget frame is a still image, so it could not animate even with a perfect drawing API. |
| 4 | the ring cache key | ✅ | Label excluded (the bitmap is only the ring). 120 buckets = **3° steps**; 120 is the key alphabet, 32 is the capacity — not a multiplier. Unquantized, a `Double` key never repeats and a 32-slot LRU hits ~0 %. |
| 5 | `tick()` as one atomic step | ✅ | Split API is read-then-act: two workers both read `due` → same boundary drawn twice. On failure the state stays `REFRESHED`, so it retries — and because `tick` throws before `armBoundary`, that retry waits for the heartbeat (recorded as an open item). |

**Status: closed** — 5/5. The cache itself is still unproven; that stays an open question, separate from the bucket arithmetic.

### End-of-M2 gate

Asked after T2.15–T2.18, before the milestone is marked done.

| # | Concept | Verdict | What I had wrong / what to remember |
|---|---|---|---|
| 1 | the silent no-op: what "success" meant at each layer | ✅ | Closed on the third pass. `updateAll()` returns when its loop finishes; over zero ids that is zero work and zero errors. The registry is Glance's **cache**, written by the same `onUpdate` M0 measured losing — so the *rescue* (boot receiver) read the same stale cache and rescued nothing. The gate's `drew=true` reported our decision, never the launcher's receipt. |
| 2 | why the picker binds and the worker draws | ✅ | Picker owns *which config*; the worker owns *when to draw* and outlives the finishing Activity. |
| 3 | `configuration_optional` | ✅ | The flag makes placement without configuration legal; the launcher took it literally, no `bind()` was ever written, and `initialLayout` became permanent. Kept apart from the tap-intent decision — **placement ≠ reconfiguration**. OEM claim dropped: unmeasured. |
| 4 | why the ring cap lives in render | ✅ | One config, many cells. The cell is per-instance, so the cap cannot live in the config; rewriting the stored diameter destroys intent, and lowering the default only fixes one launcher. |
| 5 | `directBootAware` and what unlock guarantees | ✅ | Accepted cost, stated correctly: a real frame **within 15 minutes of unlock, from whichever trigger fires first** — measured: the heartbeat, not `BOOT_COMPLETED`. Locked boot would need a second config-free renderer for a 15-minute window. |
| 6 | why 81 green tests missed both device defects | ✅ | Closed after two corrections. The suite asserted on **our own output** — strings, fractions, the `RemoteViews` we composed. A widget appears when the **launcher** receives a `RemoteViews` for a placed `appWidgetId` and renders it; that receipt was never asserted. Same shape as `drew=true`: our side of the boundary reporting on itself. |

**Status: closed** — 6/6 (2026-10-25). The sentence to carry forward: *assert at the boundary that observes the outside world — a function returning is not an effect.*

## M3+ — not reached yet
