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

A module's **Gradle path names its layer**, so the structure answers "which layer is this?"
without a table to memorise. `settings.gradle.kts` groups them the same way.

| Module | Kind | Layer | Owns |
| --- | --- | --- | --- |
| `:domain:model` | JVM | domain | `PersonProfile`, `WidgetConfigRecord`, ring styles, presets, editor drafts |
| `:domain:time` | JVM | domain | `TimeSlice`, calendar categories, remaining-fraction math |
| `:domain:mortality` | JVM | domain | life-table parsing, `LifeStatsEngine`, `ProfileValidator`, `ProfileDraft` |
| `:domain:scheduler` | JVM | domain | refresh cadence policy - no Android types |
| `:data` | Android lib | data | DataStore repositories + delete guards |
| `:ui:widget` | Android lib | UI | Glance widget, ring rendering, picker, workers, refresh gate |
| `:app` | Android app | UI + composition root | Hilt graph, wiring, the Compose screens |

The dependency arrow only points inward: `:app` -> `:ui:widget` -> `:data` -> `:domain:model`, and
`:app`/`:ui:widget` -> `:domain:*`. `:domain:*` contains no Android imports — enforced by
`ArchitectureGuardTest`, not by discipline. See `docs/Architecture.md` for the layer map and the
Flutter-to-Kotlin naming table.

## Build, install, test

```bash
# Build the debug APK (writes app/build/outputs/apk/debug/app-debug.apk)
./gradlew :app:assembleDebug

# Build and install on the connected device
./gradlew :app:installDebug

# Unit tests, module by module (all JVM, no device needed)
./gradlew :domain:model:test :domain:time:test :domain:scheduler:test :domain:mortality:test
./gradlew :data:test :ui:widget:testDebugUnitTest

# Everything at once
./gradlew test

# Start the app on the device
adb shell am start -n dev.mementomori.lifeclock/.MainActivity

# Watch the widget's own render log (one line per ring per draw)
adb logcat -s LifeClockWidget:*
```

On Windows use `gradlew.bat` (or `.\gradlew`) instead of `./gradlew`.

### Signing a build you can share

Android only installs **signed** APKs. `assembleRelease` without a `signingConfig` writes
`app-release-unsigned.apk`, and installing that fails with `INSTALL_FAILED_NO_CERTIFICATES` - the
file looks fine, it just cannot be installed. (Flutter hides this: its template signs release builds
with the debug key, so `flutter build apk --release` installs anywhere. AGP has no such default.)

One-time setup, then every release build is signed:

```bash
# 1. Create the keystore (10,000 days, RSA 2048). Keep it out of git - it IS your app's identity.
keytool -genkeypair -v -keystore keystore/memento-release.jks -alias memento \
  -keyalg RSA -keysize 2048 -validity 10000

# 2. Point the build at it (this file is gitignored; storeFile is relative to the repo root)
#    storeFile=keystore/memento-release.jks
#    storePassword=...
#    keyAlias=memento
#    keyPassword=...

# 3. Build -> app/build/outputs/apk/release/app-release.apk
./gradlew :app:assembleRelease

# 4. Prove it is signed before sending it to anyone
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

Without `keystore.properties` the build still succeeds, but it is unsigned again - so the file name
(`app-release.apk` vs `app-release-unsigned.apk`) is the quick check.

Rules that save a support conversation later:

- **Never lose the keystore.** The same key is what lets a new APK install *over* the old one; a new
  key means the friend must uninstall first (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`).
- **Uninstall any earlier build first** when the key changed - including your own debug APK, which is
  signed with the machine's debug key.
- `minSdk = 26` means Android 8.0+; older phones fail with "app not compatible".
- Sideloading also needs "install unknown apps" for the app doing the install, and Play Protect may
  ask you to confirm ("Install anyway"). Prefer a file link (Drive) over a chat app, which may strip
  or block `.apk` attachments.

If `:ui:widget:bundleLibCompileToJarDebug` fails with "The process cannot access the file", a Kotlin
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
