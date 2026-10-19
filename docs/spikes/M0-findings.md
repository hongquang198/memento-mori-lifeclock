# M0 — Platform de-risk spike: findings

**Status:** complete, with two residual items (see [Residual blockers](#residual-blockers)).
**Device:** Pixel 8a (`akita`), Android 16 / SDK 36, app `targetSdk 35`, debug (debuggable) build.
**Libraries:** Jetpack Glance 1.1.1, Kotlin 2.0.21.
**Method:** every claim below comes from a measurement on the physical device, not from documentation.

The point of M0 was to prove the three platform facts the whole architecture rests on *before*
committing to it. Two were confirmed as designed. One (scheduling) turned out to be stricter than
the design assumed, and one new failure mode was discovered (boot rendering). Both are recorded
with the design changes they force.

---

## 1. What the spike actually is

| File | Purpose |
| --- | --- |
| `widget/.../SpikeRing.kt` | `renderSpikeRing()` — rasterizes a **determinate** arc with `Canvas.drawArc` into a `Bitmap`. |
| `widget/.../SpikeRingWidget.kt` | Glance widget: ring `Image` + embedded `RemoteViews` `Chronometer`; logs every render to logcat **and** to `files/m0_render_log.txt`. |
| `widget/.../SpikeRefreshReceiver.kt` | Drives the production refresh path (`SpikeRingWidget().updateAll(context)`) from the shell. |
| `app/.../spike/SpikeProbeReceiver.kt` | Arms `setAndAllowWhileIdle` + `setExactAndAllowWhileIdle` and logs when each fires. |

No tests: this is a measurement harness, not production logic. The behaviours it measures
(Doze, boot, widget-host rendering) cannot be expressed as unit tests; they are only observable
on a device. Every source file above is throwaway and must be deleted before M2.

### Screenshots (`docs/spikes/`)

| File | Shows |
| --- | --- |
| `m0_spike_activity.png` | First proof: determinate ring + `Chronometer` inside the app. |
| `m0_expand.png`, `m0_drop.png` | Widget picker → drag onto the home screen. |
| `m0_placed_t1.png`, `m0_placed_t2.png` | Widget rendering **in the real widget host**, 9 s apart. |
| `m0_reboot.png` | Widget survived reboot #1, fully rendered. |
| `m0_reboot2.png` | Reboot #2: widget stuck on Glance's **`initialLayout` placeholder**. |
| `m0_recovered.png` | Same widget after our own refresh call: rendering again. |
| `m0_userboot.png` | Your reboot (repro #3): placeholder again; instance had been re-added as id 13. |
| `m0_boot_probe.png` | Repro #4 with the `onUpdate` probe installed: the trigger arrives with the correct ids, `provideGlance` never runs. |

---

## 2. T0.2 — a determinate ring is possible (confirmed)

Glance's `CircularProgressIndicator` is indeterminate-only, so the ring is rasterized by hand:
`Canvas.drawArc(bounds, -90f, 360f * fraction, false, paint)` → `Bitmap` → `ImageProvider(bitmap)`
→ Glance `Image`.

Confirmed on device, in the **home-screen widget host** (not just in a test activity):
`docs/spikes/m0_placed_t1.png` shows a ~73 % arc in the launcher.

Corroboration from the widget service itself:

```
dumpsys appwidget → views=android.widget.RemoteViews@f3449ce
                    views_bitmap_memory=230400
```

`230400 = 240 × 240 × 4` — exactly the ARGB_8888 bitmap produced by `renderSpikeRing()`. The
live `RemoteViews` really does carry our rasterized bitmap.

**Consequence for the design:** the ring is a **static image**. A smoothly animating ring is
impossible; the ring can only change when the widget is re-rendered. The design's "coarse ring"
is therefore not a preference, it is the only option.

## 3. T0.3 — the text ticks by itself (confirmed)

The countdown is a platform `Chronometer` embedded as raw `RemoteViews` through
`AndroidRemoteViews`, with:

```kotlin
remoteViews.setChronometerCountDown(R.id.spike_chronometer, true)
remoteViews.setChronometer(R.id.spike_chronometer, base, null, true) // base = elapsedRealtime() + remaining
```

Two screenshots nine seconds apart, **with no code of ours running** (the widget was idle and no
update was sent):

```
m0_placed_t1.png  →  167:59:38
m0_placed_t2.png  →  167:59:29
```

`Chronometer` is a real `View` in the host process. It re-ticks itself once per second with zero
participation from our app, zero alarms, zero wakeups. This is the only sub-minute time display
available to a widget.

**Frame choice matters (and is a trap):** `setChronometer` expects an **`elapsedRealtime()`**
base, not wall-clock. `elapsedRealtime()` *includes* deep sleep but *resets to zero on reboot*.
So a deadline must be stored as wall-clock and converted to an elapsed base **on every render**.
A base persisted across a reboot becomes meaningless — which is exactly why the boot problem in
§6 matters rather than being cosmetic.

## 4. T0.4a — no refresh happens unless we ask for it (confirmed)

`appwidget-provider` sets `updatePeriodMillis="0"`, and `dumpsys appwidget` confirms
`updatePeriodMillis=0`. Across the whole session, `provideGlance` ran only on:

1. the widget being bound / the host requesting its first update, and
2. an explicit `updateAll()` from our own code.

No periodic render ever occurred on its own. Every render observed is in
`files/m0_render_log.txt` (durable) and, when logcat cooperated, in logcat.

**Consequence:** cadence is entirely our responsibility — via `WorkManager` (≥15 min) and/or
inexact alarms. The ring cannot be trusted to be fresh unless we schedule it.

## 5. T0.4b — exact alarms are unavailable; inexact ones are late (design changed)

Measured on the device:

```
SCHEDULE exactAlarmsAllowed=false sdk=36
ARMED exact=false delayMs=120000
ARM-FAILED exact=true reason=SecurityException: Caller dev.mementomori.lifeclock needs to hold
    android.permission.SCHEDULE_EXACT_ALARM or android.permission.USE_EXACT_ALARM to set exact alarms.
```

So:

* `AlarmManager.canScheduleExactAlarms()` is **false** for this app on Android 16.
* `setExactAndAllowWhileIdle()` throws `SecurityException`.
* `USE_EXACT_ALARM` (the permission that works without user consent) is reserved by Play for
  alarm-clock/calendar apps, which a countdown widget is not. `SCHEDULE_EXACT_ALARM` is a special
  access the *user* must grant, and is not granted by default on modern Android.

The inexact alternative does work, but it is late. Two runs, both with the screen off:

| Run | Condition | Expected | Actual | Lateness |
| --- | --- | --- | --- | --- |
| 1 | `QUICK_DOZE_DELAY` / light idle | +120 s | +210 s | **+90 s** |
| 2 | device idle-ish, alarm not fired in 45 s past trigger | +120 s | delivered only after the device left idle | **deferred, then delivered** |

Run 2 is the important one: `dumpsys alarm` afterwards showed the alarm had fired
(`2 wakes 2 alarms`), i.e. it was **deferred, not lost** — it was released when the device exited
idle.

Attempting to reach true deep `IDLE` with `dumpsys deviceidle force-idle` did not stick on this
device (`mForceIdle` reset itself; the machine parked at `INACTIVE` / `QUICK_DOZE_DELAY`). This is
a limitation of testing on an actively-used phone, not a platform result — see
[Residual blockers](#residual-blockers).

**Consequence for the design:** the ring cadence must be described as *best-effort and
potentially hours stale*, never as "on time". Only the `Chronometer` text is exact. The design
sentence "refreshed on a sane cadence" is now quantitative: **≥15 min via `WorkManager`, plus an
inexact `setAndAllowWhileIdle` boundary alarm, with no promise tighter than "coarse".**

## 6. T0.4c — the boot problem: **root-caused** (forces a design change)

**Symptom.** After a reboot the widget comes back as Glance's `initialLayout` placeholder
(white card + spinner) instead of the ring, and can stay that way **indefinitely**.
Reproduced on 3 of 3 boots after the mechanism was suspected (`m0_reboot2.png`, `m0_userboot.png`,
`m0_boot_probe.png`); one earlier boot rendered fine (`m0_reboot.png`), which is exactly what a
*race* looks like.

**What the system does.** After a boot the widget is re-bound and the provider really is invoked:

```
ActivityManager: Start proc 5249:dev.mementomori.lifeclock ... for broadcast {...
    SpikeRingWidgetReceiver}
```

**What the app sees.** With an `onUpdate` override recording the id array, the last boot produced:

```
onUpdate ids=[13] wallMs=1792381528752 elapsedMs=30947     <-- 31 s after boot
                          ... and then nothing ...
```

`provideGlance` was never entered. There was no exception, no ANR, no broadcast timeout, and no
freeze; the process was simply reaped as cached ~25 s later.

**Why.** Read from the pinned library itself (`glance-appwidget-1.1.1.aar`, verified with `javap`):

```kotlin
// GlanceAppWidgetReceiver 1.1.1
override fun onUpdate(context, appWidgetManager, appWidgetIds) {
    goAsync(coroutineContext) { doUpdate(context, appWidgetIds) }   // coroutineContext = Dispatchers.Default
}

internal suspend fun CoroutineScope.doUpdate(context: Context, appWidgetIds: IntArray) {
    updateManager(context)                                          // touches the appwidget service
    appWidgetIds.map { async { glanceAppWidget.update(context, it) } }.awaitAll()
}

private inline fun runAndLogExceptions(block: () -> Unit) {
    try { block() }
    catch (ex: CancellationException) { /* Nothing to do */ }        // <-- swallowed silently
    catch (throwable: Throwable) { logException(throwable) }
}
```

So in 1.1.1 the whole composition runs **inside the broadcast's `goAsync` window** on a
**cold-starting process** (dex + Hilt `@HiltAndroidApp` init + Compose runtime + Glance session).
Warm, that path takes a measurable **1.6 s** (`onUpdate` → `provideGlance`); on the heaviest start
in the app's life it exceeds the budget, the scope is cancelled, and `CancellationException` is
caught and discarded with a comment — which is why the failure is completely silent.

**Why it never heals.** The spike sets `updatePeriodMillis=0`, so the framework has no reason to
retry. Nothing re-triggers a render, so the placeholder persists until an unrelated event renders
the widget: our own `updateAll()` (recovered immediately — see below) or the user removing and
re-adding the widget (which is exactly what created id 13 after id 8 was deleted).

Recovery via the production path, for the record:

```
broadcast → SpikeRefreshReceiver → SpikeRingWidget().updateAll(context)
logcat:   REFRESH ok wallMs=1792247785518
durable:  provideGlance id=AppWidgetId(appWidgetId=8) wallMs=1792247782260 elapsedMs=657549
screenshot: m0_recovered.png  (ring + 167:59:52)
```

### Why the obvious fix would not have worked

The tempting fix is "add a `BOOT_COMPLETED` receiver that calls `updateAll()`". That receiver is
**also a broadcast**, so it inherits the same `goAsync` budget and races the same cold start. It
would look right and fail intermittently — the worst kind of fix.

Glance *does* ship the correct escape hatch: `shouldLaunchAsyncRequestWorker()`, which runs the
update in a `WorkManager` worker instead of `goAsync` ("*useful if your broadcast receiver
frequently times out*"), and `maybeLaunchAsyncRequestWorker` already forces that path on vivo
devices because "goAsync is broken on certain OEMs". **But `javap` on the pinned 1.1.1 AAR shows
1.1.1 has neither `shouldLaunchAsyncRequestWorker` nor `AsyncRequestWorker`** — that landed later.
On 1.1.1 the escape hatch has to be ours.

**Consequence for the design (new requirement).** The boot refresh must run somewhere with a real
execution budget, not in a broadcast:

1. a `BOOT_COMPLETED` receiver whose *only* job is to **enqueue a `WorkManager` job**, and
2. that job calls `updateAll()` (a `suspend` function — trivially callable from a worker), and
3. a periodic `WorkManager` refresh that **self-heals** any widget that missed its first render,
   and
4. a designed `initialLayout`/placeholder, because a real user can genuinely see it.

Do not implement (1) as "receiver calls `updateAll()` directly". Either upgrade Glance to a
version with `shouldLaunchAsyncRequestWorker()`, or use the `WorkManager` indirection above.


## 7. Broadcast semantics discovered along the way

* **Implicit custom-action broadcasts do not reach manifest receivers** (API 26+ background
  limits). `am broadcast -a <custom.action>` produced no log; adding an explicit component
  (`-n pkg/.Receiver`) delivered it. All internal widget signalling must use **explicit
  intents**.
* **`android.appwidget.action.APPWIDGET_UPDATE` is a protected broadcast** — neither adb nor a
  normal app may send it (`SecurityException: not allowed to send broadcast ... from unknown
  caller`). Test harnesses must go through `GlanceAppWidget.updateAll()` instead.

## 8. Meta-finding: logcat is not a reliable evidence channel here

After reboot #1 the widget demonstrably re-rendered, yet our `Log.i` line appeared in **no**
logcat buffer (main, all, or filtered by pid), while system logs from the same process were
present. That single missing line was what made the boot behaviour look "inconsistent" instead of
"a race we happened to win"; §6 later root-caused the behaviour from the durable file. The exact
logcat reason was never identified, and chasing it further is not worth it.

The mitigation is the real lesson: **record measurements to app-private storage**, not logcat.
Switching to `files/m0_render_log.txt` is what made §6 solvable — the decisive evidence
(`onUpdate ids=[13]` with no following `provideGlance`) exists only because it was written to a
file. Any future on-device measurement in this project should use the same durable channel.

Two practical notes on that channel, learned the hard way:

* `run-as` fails with *"couldn't stat /data/user/0/<pkg>"* while the device is still at the lock
  screen, because the directory is credential-encrypted. Wait for `dumpsys user` to report
  `RUNNING_UNLOCKED` before reading.
* Bundling a `-RedirectStandardOutput`-based `adb logcat` capture and killing it later is
  unreliable (buffered writes); prefer the file inside the app.

---

## Residual blockers

| # | Item | Why it is still open | Suggested resolution |
| --- | --- | --- | --- |
| 1 | True deep-`IDLE` cadence never measured | `force-idle` would not hold on an actively-used phone; it parked at `QUICK_DOZE_DELAY` | Repeat on a spare/secondary device, or with the screen off and the phone left alone for hours |
| 2 | The boot fix is **specified but not yet verified** | Root cause is established (§6) and the fix is designed, but nothing implementing it has been reboot-tested | Implement the `WorkManager`-based boot refresh (M2: T2.12/T2.13) and re-run the reboot test until the placeholder can no longer persist |
| 3 | No Battery Historian session captured | Needs the on-device bugreport + Battery Historian import | Capture one during M2 scheduling verification, where the numbers actually matter |

Item 2 is the only one that can still invalidate a design decision, so it is the first thing M2
must close. Items 1 and 3 can only make the cadence guarantee more precise.

## What M0 changes in the plan

1. **Ring = static bitmap; only the text is live.** No animation is possible. (Confirmed, as designed.)
2. **No exact alarms, ever.** Cadence is best-effort: `WorkManager` (≥15 min) + inexact
   `setAndAllowWhileIdle`. No promise on time. (Design tightened.)
3. **The `Chronometer` base must be recomputed on every render** from a wall-clock deadline.
   Never persist an elapsed base.
4. **The framework's boot-time `onUpdate` must not be trusted.** Root cause: Glance 1.1.1 runs the
   composition inside the broadcast's `goAsync` budget on a cold-starting process; when it loses
   that race the work is cancelled and the `CancellationException` is swallowed *silently*.
   The boot refresh must run in `WorkManager`, a periodic refresh must self-heal a widget that
   missed its first render, and a real `initialLayout`/placeholder must be designed.
   (New requirement, root-caused in §6 — and on 1.1.1 Glance's own
   `shouldLaunchAsyncRequestWorker()` escape hatch does not exist yet.)
5. **All internal widget broadcasts are explicit intents.** (New constraint.)
6. **On-device measurements are recorded to a durable file, not logcat.** (New practice.)

## Commands (all of them, for reproducibility)

All commands are run from the repo root with the device connected. `$adb` is
`C:\Users\ADMIN\AppData\Local\Android\Sdk\platform-tools\adb.exe`.

```powershell
# Build + install the spike
.\gradlew.bat :app:assembleDebug --console=plain
& $adb install -r "app\build\outputs\apk\debug\app-debug.apk"

# Read the durable render log (survives reboots, process freezes, logcat quirks)
& $adb shell run-as dev.mementomori.lifeclock cat files/m0_render_log.txt

# Drive the production refresh path (cannot use the protected APPWIDGET_UPDATE action)
& $adb shell am broadcast -n dev.mementomori.lifeclock/.widget.SpikeRefreshReceiver `
        -a dev.mementomori.lifeclock.spike.REFRESH

# Glance's own debug trigger. Unlike APPWIDGET_UPDATE this is a normal action, and Glance
# enumerates the widget ids itself before calling onUpdate. This is what produced the decisive
# evidence in §6 (`onUpdate ids=[13]`, then no provideGlance).
& $adb shell am broadcast -a androidx.glance.appwidget.action.DEBUG_UPDATE `
        -n dev.mementomori.lifeclock/.widget.SpikeRingWidgetReceiver

# Arm the two alarms (inexact vs exact), 120 s out
& $adb shell am broadcast -n dev.mementomori.lifeclock/.spike.SpikeProbeReceiver `
        -a dev.mementomori.lifeclock.spike.SCHEDULE

# Force the device idle, then read the outcome
& $adb shell dumpsys battery unplug            # a charging device never dozes
& $adb shell dumpsys deviceidle force-idle
& $adb shell dumpsys deviceidle | Select-String "mState|mForceIdle|mCharging|mScreenOn"
& $adb shell dumpsys alarm | Select-String "mementomori" -Context 0,3
& $adb shell dumpsys battery reset
& $adb shell dumpsys deviceidle unforce

# Place the widget (no scriptable bind exists; the picker is driven via UI automation)
& $adb shell input draganddrop <x1> <y1> <x2> <y2> 2000
# NOTE: placement had to be done through the launcher UI. `cmd appwidget` has no bind command
# ("No shell command implementation"), and the search field is unusable via `input text`
# because the Gboard Vietnamese IME autocorrects it.
```
