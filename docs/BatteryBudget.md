# Battery budget and resilience record

The claim this file has to defend: **the widget refreshes on a bounded, explainable schedule, and it
recovers from the ways Android kills processes.**

Everything below was measured on a Pixel 8a (Android 16, `Asia/Ho_Chi_Minh`). Commands are quoted so
the numbers can be reproduced, not trusted.

## 1. The declared budget

| Mechanism | Cadence | Why it exists |
| --- | --- | --- |
| `PeriodicWorkRequest` (`CountdownRefreshWorker`) | every 15 min (WorkManager's floor) | heartbeat: survives reboot and Doze, imprecise by design |
| `AlarmManager.setAndAllowWhileIdle` | one alarm at the next boundary (midnight for `DAYS`) | precision: WorkManager alone can be a period late |
| `updatePeriodMillis` | **0** | the system's own periodic update would fight the two above |
| Foreground service / exact alarms / network | none | not needed, and both are restricted |

Worst case per widget per day: 96 worker wakeups, of which **at most one draws** (the refresh gate
skips a tick whose boundary has not been crossed). One alarm, inexact, one hour of slack.

## 2. Measured: what is actually scheduled

`adb shell dumpsys appwidget`

```
cmp=ComponentInfo{dev.mementomori.lifeclock/dev.mementomori.lifeclock.widget.CountdownWidgetReceiver}
  min=(28161x28161) ... updatePeriodMillis=0 ... initialLayout=#7f0b0493
```

`min` is in 1/256 dp, so 28161 = 110 dp. `updatePeriodMillis=0` is the deliberate part: the system
must not also wake us.

`adb shell dumpsys jobscheduler` - our heartbeat:

```
JOB #u0a363/5: ... dev.mementomori.lifeclock/androidx.work.impl.background.systemjob.SystemJobService
  Requires: charging=false batteryNotLow=false deviceIdle=false
  Minimum latency: +2m27s100ms
```

The work database (`no_backup/androidx.work.workdb`) gives the spec itself:

```
CountdownRefreshWorker  interval_duration=900000  initial_delay=0  requires_charging=0  period_count=1
```

900000 ms = 15 min, no constraints, no initial delay. This is the budget, verified at the source
rather than inferred from a dump.

`adb shell dumpsys alarm`

```
RTC_WAKEUP #76: ... dev.mementomori.lifeclock
  tag=*walarm*:dev.mementomori.lifeclock.widget.BOUNDARY
  type=RTC_WAKEUP origWhen=2026-10-28 00:00:00.000 window=+1h0m0s0ms flags=0x20
```

`flags=0x20` is `FLAG_ALLOW_WHILE_IDLE`; the one-hour window is the price of not holding
`SCHEDULE_EXACT_ALARM`.

### A job that looks like a defect and is not

The same dump contains:

```
JOB #u0a363/1: ... Requires: charging=true ... Minimum latency: +3649d23h26m27s699ms
```

That is **not** this app's refresh. It is Glance's own `SessionWorker` placeholder
(`androidx.glance.session.SessionWorker`, `initial_delay=315360000000` = 3650 days,
`requires_charging=1`), which Glance enqueues as a dormant slot it replaces when a real session
starts. It never runs, so it costs nothing - but an audit that stops at `dumpsys` will call it a bug.
The work database is what settles the question; `docs/Methodology.md` has the same rule for numbers:
go to the source.

Related: `ensurePeriodic()` uses `ExistingPeriodicWorkPolicy.UPDATE`, not `KEEP`. `KEEP` keeps the
first registration forever, so an install that ever ran a build with a different interval or
constraint would keep that spec until the work is cancelled. `WidgetRefreshSchedulerTest` pins the
replacement (a stale 60-minute spec must become 15 minutes).

## 3. Measured: process death and cold start

This is the M0 failure mode: a widget drawn inside a broadcast's `goAsync` budget loses the race on a
cold start, and with `updatePeriodMillis=0` nothing retries. The fix is a rescue - the receiver only
enqueues work, and the work draws.

```
adb shell am kill dev.mementomori.lifeclock          -> pid: (none)
adb shell am broadcast -a androidx.glance.appwidget.action.DEBUG_UPDATE \
  -n dev.mementomori.lifeclock/.widget.CountdownWidgetReceiver
                                                     -> pid: 13315
adb logcat -s LifeClockWidget:*
  11:15:45.672 13315 13315 I LifeClockWidget: render(16): fraction=0.5112660700750072
                                                  bitmap=326x326 cell=140dp label="14,068 days"
```

A fresh process, no app UI, and the widget still knows what to draw: the binding and the config came
from DataStore, and the number was recomputed from the life table. Nothing was cached in the process.

## 4. Measured: the gate does skip work

After the reboot, the periodic worker ran and **no render line appeared**:

```
11:14:49.437 WM-WorkerWrapper: Worker result SUCCESS for Work [ id=b935952f-... CountdownRefreshWorker ]
```

The life clock's granularity is `DAYS` and the day had already been drawn, so `ProcessRefreshGate`
settled the tick without drawing. That is the difference between "a worker wakes up" and "the widget
redraws": 96 wakeups a day do not mean 96 bitmaps.

## 5. Measured: CPU while the screen was off

`adb shell dumpsys batterystats --charged`, uid `u0a363` (this app), since the last charge:

```
bg: 0.499 (9m 50s)   cached: 0.938 (23h 27m)
(on battery, screen off/doze) cpu=0.227 cpu:bg=0.0305 cpu:cached=0.197 wakelock=0.00603 (300ms)
```

Roughly a day of cached time cost ~300 ms of wakelock and a small fraction of a CPU-second. The
numbers are `batterystats` units, not percentages; the shape is what matters: no wakeup storm, no
wakelock held across a refresh.

**Deviation:** Battery Historian's GUI was not used. `batterystats` is the ledger Battery Historian
renders, so the evidence is the same; only the chart is missing.

## 6. Memory

`adb shell dumpsys meminfo dev.mementomori.lifeclock`:

```
TOTAL PSS: 140726 KB    TOTAL RSS: 260268 KB
```

That is the whole process (Compose editor + widget code). The part that scales with widgets is the
ring cache: `CachedRingRenderer` holds 32 entries (`maxEntries = 32`), each a 326x326 ARGB bitmap
(~425 KB), so the ceiling is ~13.6 MB regardless of how many instances are placed - entries are
keyed by `(style, fraction bucket)`, not by widget id, so ten widgets sharing one look share one
bitmap.

**Not verified:** more than one placed instance. The bound is proven by the cache's own test
(`RingCacheTest`) and the constant above, not by placing ten widgets.

## 7. What is still open

- **Doze on a real idle device.** `dumpsys deviceidle force-idle` refused (`stopped at INACTIVE`:
  the device was charging with the screen on). Both mechanisms are Doze-compatible by construction
  (`ALLOW_WHILE_IDLE` alarm, unconstrained job), but the honest state is "not measured". To measure:
  unplug, screen off, `force-idle`, wait past the boundary, `unforce`, compare the render log.
- **ANR audit** is a code property, not a device measurement: no `runBlocking`, no main-thread
  queries, receivers only enqueue work, every store read is a suspend call (checked across
  `widget/`, `app/`, `core/data/`).
- **Launcher matrix** (T4.17) is still Pixel Launcher only.
