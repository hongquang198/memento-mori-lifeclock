# Architecture diagrams

`widget-runtime.html` is a standalone, interactive map of the Android app-widget platform
together with this app's `:widget` probe code. Open it in a browser; no server or install is
needed. It exists because the wiring between the platform and our code is invisible in the
source: nothing in `:widget` shows you that the launcher is the process that actually draws
the widget, or that a boot-time `onUpdate` runs on a borrowed budget.

What it shows, in the order things actually run:

1. **Creation** — launcher -> `AppWidgetService` -> `APPWIDGET_UPDATE` -> our `onUpdate`.
2. **The broken arm** — Glance runs the whole composition inside the broadcast's `goAsync`
   window on a cold-starting process; it loses that race, the scope is cancelled, the
   `CancellationException` is swallowed, and the widget stays on `initialLayout` forever
   (`updatePeriodMillis=0` means nothing ever retries it).
3. **The working arm** — `BOOT_COMPLETED` (delivered after unlock) -> `BootRefreshReceiver`
   (enqueues only, never renders) -> `WorkManager`/`JobScheduler` -> `RefreshWidgetsWorker`
   -> `updateAll()` -> `provideGlance()`.
4. **Delivery** — `renderSpikeRing()` -> `RemoteViews` -> `AppWidgetService` -> the launcher,
   with the platform `Chronometer` ticking inside the host process.
5. **Authorisation** — who may call whom: only the platform triggers `onUpdate`, and our code
   reaches the launcher only through `AppWidgetService`.

`candidate.json` is the editable source of truth; the HTML is generated from it. Components
marked as probe code (`SpikeRingWidget*`, `SpikeRing.kt`, `SpikeRefreshReceiver`,
`SpikeProbeReceiver`, `BootRefreshReceiver`, `RefreshWidgetsWorker`) are the M0 spike, are
deleted before M2, and are **not** the target architecture.

Regenerate from the repo root (`review-4` is the run that produced the committed HTML; a rerun
needs a fresh `--out-dir`, because the delivered HTML owns the evidence taken from its bytes):

```powershell
node C:\Users\ADMIN\.agents\skills\archify\bin\archify.mjs finalize architecture `
  docs/architecture/candidate.json docs/architecture/widget-runtime.html `
  --repo-root . --quality showcase --out-dir docs/architecture/review-5 --json
```

Evidence: `docs/spikes/M0-findings.md` sections 6 (root cause) and 9 (the verified A/B),
`docs/design.md`, `docs/plan.md`, `settings.gradle.kts`. Every component cites its source.
Platform-only facts (that the launcher identity is `com.google.android.apps.nexuslauncher`,
that `JobScheduler` is what WorkManager requests) come from platform knowledge rather than
this repo, and are labelled as such in the diagram.
