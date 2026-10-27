# Memento Mori Life Clock

An Android app plus home-screen widget that draws your remaining life as a determinate ring, and
countdowns to dates you care about. Everything the widget shows is a named config you edit in the
app; a placed widget owns no settings of its own.

## What it is, in one paragraph

A widget is a bitmap plus a platform `Chronometer`, redrawn by a worker. The app is a Compose shell
over the same repositories the widget reads, so saving a config in the editor is the same write the
next draw sees. Numbers about remaining life come from the UN World Population Prospects 2024 life
tables, shipped as a 10k-row CSV and interpolated at the person's age - see
[`docs/Methodology.md`](docs/Methodology.md) for sources, vintages and the formulas.

## Modules

| Module | Kind | Owns |
| --- | --- | --- |
| `:core:model` | JVM | `PersonProfile`, `WidgetConfigRecord`, ring styles, presets, and the editor drafts |
| `:core:time` | JVM | `TimeSlice`, calendar categories, remaining-fraction math |
| `:core:scheduler` | JVM | refresh cadence policy - no Android types |
| `:core:mortality` | JVM | life-table parsing, `LifeStatsEngine`, `ProfileValidator`, `ProfileDraft` |
| `:core:data` | Android lib | DataStore repositories + delete guards |
| `:widget` | Android lib | Glance widget, ring rendering, picker, workers, refresh gate |
| `:app` | Android app | composition root (Hilt graph, wiring) + the Compose editor |

The dependency arrow only points inward: `:app` -> `:widget` -> `:core:*`. `:core:time` and
`:core:mortality` contain no Android imports (there is a guard test for it).

## Build, install, test

```bash
# Build the debug APK (writes app/build/outputs/apk/debug/app-debug.apk)
./gradlew :app:assembleDebug

# Build and install on the connected device
./gradlew :app:installDebug

# Unit tests, module by module (all JVM, no device needed)
./gradlew :core:model:test :core:time:test :core:scheduler:test :core:mortality:test
./gradlew :core:data:test :widget:testDebugUnitTest

# Everything at once
./gradlew test

# Start the app on the device
adb shell am start -n dev.mementomori.lifeclock/.MainActivity

# Watch the widget's own render log (one line per ring per draw)
adb logcat -s LifeClockWidget:*
```

On Windows use `gradlew.bat` (or `.\gradlew`) instead of `./gradlew`.

If `:widget:bundleLibCompileToJarDebug` fails with "The process cannot access the file", a Kotlin
daemon or language server is holding the jar: kill `java.exe` processes whose command line matches
`kotlin-daemon|kotlinLanguageServer`, delete `widget/build/intermediates/compile_library_classes_jar`,
and rebuild.

## Using the app

1. **People** - add a person (birth date, sex, country, sleep/screen hours). The editor previews the
   computed "days left" with its citation before you save.
2. **Widgets** - create a countdown (pick a deadline) or a life clock (pick a person). Then choose a
   template, a layout, and per-ring diameter, stroke, colour, label and placement. The preview at the
   top is drawn by the same rasterizer the widget uses.
3. **Home screen** - long-press the launcher, choose Widgets, place *Memento Mori Countdown*, then
   pick a config. Tapping a placed widget re-opens the picker; *Your widgets* in the app lists what is
   placed and lets you re-point it.

A config that is still bound to a placed widget cannot be deleted, and neither can a person a config
refers to. The app tells you what is blocking it instead of silently unbinding.

## Where to read next

- [`docs/design.md`](docs/design.md) - architecture and the decisions behind it
- [`docs/Methodology.md`](docs/Methodology.md) - every number's source, vintage and formula
- [`docs/concepts.md`](docs/concepts.md) - the Android/Glance concepts, explained from a Flutter
  developer's starting point
- [`docs/tasks.md`](docs/tasks.md) - milestone tasks with the evidence each one was closed with
- `docs/architecture/` - diagrams
