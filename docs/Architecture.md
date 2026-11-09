# Architecture

This is the map. It exists because the answer to "which layer is this?" should be readable from a
path, not remembered from a table.

## The three layers

Android's [guide to app architecture](https://developer.android.com/topic/architecture) names three
layers. A layer is a rule about **what a file is allowed to know**, not a folder.

| Layer | Rule | Lives in |
| --- | --- | --- |
| **UI** | knows Compose, `Context`, and user intent | `:app`, `:ui:widget` |
| **Domain** | knows Kotlin and `java.time`; never Android | `:domain:*` |
| **Data** | knows where bytes are stored (DataStore) | `:data` |

Dependencies point **downward**: UI → domain → data. The data layer implements the contracts the
layers above it use; it never knows who is asking. The one inversion is a *platform port*: the
domain declares the interface, `:ui:widget` implements it, and the ViewModel receives the
implementation through DI (see "The widget is a second UI surface").

### Why there are seven modules and only three layers

A module is a *dependency boundary*, not a layer. The domain is split into four small modules
(`model`, `time`, `mortality`, `scheduler`) so each is independently testable and none can
accidentally depend on another's internals. `:app` and `:ui:widget` are both the UI layer, because
app widgets are a second UI surface that Compose cannot draw.

| Module | Layer | What it may know |
| --- | --- | --- |
| `:domain:model` | domain | Kotlin, `kotlinx.serialization` |
| `:domain:time` | domain | Kotlin, `java.time` |
| `:domain:mortality` | domain | Kotlin, bundled CSV resources |
| `:domain:scheduler` | domain | Kotlin, `java.time` |
| `:domain:contracts` | domain | Kotlin, `kotlinx.coroutines` (a `Flow` is part of the contract) |
| `:domain:usecases` | domain | the contracts and the model - never a platform API |
| `:data` | data | DataStore, the domain contracts |
| `:ui:widget` | UI | Glance, `AppWidgetManager`, `AlarmManager`, WorkManager |
| `:app` | UI + composition root | Compose, Hilt, `Context` |

`:app` is the one conventional exception to the prefix rule: an Android application module is always
called `:app`.

## Flutter → Kotlin

Flutter's folders *are* the layers, so `features/home/presentation/home_screen.dart` says everything.
Kotlin carries the same information in **suffixes** and in the **Gradle path**.

| Flutter | Here | Lives in |
| --- | --- | --- |
| `home_screen.dart` / `home_page.dart` | `HomeScreen.kt` (`@Composable`) | `:app/ui/home/` |
| `home_bloc.dart` | `HomeViewModel.kt` | `:app/ui/home/` |
| `home_state.dart` | `HomeUiState.kt` (one immutable data class) | `:app/ui/home/` |
| `home_event.dart` | UI callbacks, or a sealed `HomeEvent` if it grows | `:app/ui/home/` |
| `home_repository.dart` | `WidgetConfigRepository` | `:data/` |
| `home_repository_impl.dart` | `DataStoreLifeClockStore` | `:data/` |
| `home_usecase.dart` | `SaveConfigUseCase.kt` | `:domain:usecases/` |
| `models/` | `WidgetConfigRecord.kt` | `:domain:model/` |
| a platform `service/` | `WidgetRefresher` (interface) + `AndroidWidgetRefresher` (adapter) | domain / `:ui:widget` |

Kotlin has no `_bloc` culture; the idiomatic suffix is `ViewModel`. One public type per file, named
after the file.

### The one-question test

> What does this file know?

- `@Composable` → **UI**
- a `Repository` **and** another `Repository`, or "save then refresh" → **use case**
- DataStore → **data**
- `AlarmManager` / `AppWidgetManager` → **platform adapter**, and the ViewModel may not import it
- only `java.time` → **domain**

If a file answers two of those, it is in the wrong place.

## Unidirectional data flow

```
Compose ──events──► ViewModel ──► use case ──► repository ──► DataStore
   ▲                    │
   └──── UiState ◄──────┘        (StateFlow, one immutable state per screen)
```

- **State flows down**: one `XxxUiState` data class per screen, exposed as `StateFlow`, collected
  with `collectAsStateWithLifecycle()`.
- **Events flow up**: lambdas passed into the screen. The screen never mutates state directly.
- **The ViewModel holds no `Context`** and no Android platform type. Anything that needs the
  platform (refresh a widget, enumerate placed widgets) is a **port**: an interface in the domain,
  implemented in `:ui:widget` and injected inward. That inversion is what keeps the UI layer from
  knowing how widgets work.

## The widget is a second UI surface

Glance cannot host a `ViewModel`. Its state holder is the resolver plus the refresh gate
(`:ui:widget`), fed by the same repositories as the app. Two surfaces, one source of truth: a config
saved in the app is the config the next widget draw reads.

## Navigation and the back stack

There is no navigation library. Four destinations, no deep links, no second level — a dependency
would buy nothing. What Compose does *not* give you for free is the back bridge that Flutter installs
with `WidgetsApp`:

| | Flutter | Compose |
| --- | --- | --- |
| Who owns the stack | `Navigator` | you, or a library |
| Back gesture | `handlePopRoute` → `Navigator.maybePop` | `OnBackPressedDispatcher` → last registered callback, else `finish()` |
| Intercept | `PopScope` | `BackHandler` |

So the current screen is state, and back is a callback that pops it:

```kotlin
var screen by rememberSaveable(stateSaver = ScreenSaver) { mutableStateOf<Screen>(Screen.Home) }
BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }
```

If a second navigation level appears (or a widget tap must deep-link into the editor), the same idea
becomes a `List<Screen>` with `removeAt(lastIndex)`, or Navigation Compose — which is exactly that
list and that callback, owned by a library.

## Where to add a new file

1. Is it a screen, a ViewModel, or a UiState? → `:app/ui/<feature>/`
2. Does it combine two repositories, or "do X then Y"? → `:domain:usecases/`
3. Does it parse, compute, or decide something with no I/O? → the matching `:domain:*`
4. Does it read or write storage? → `:data/`
5. Does it declare what the UI needs from the platform? → `:domain:contracts/` (a port); implement it in `:app/di/`
6. Does it need `Context`, Glance, alarms, or WorkManager? → `:ui:widget/` (or `:app` for Compose)

If it fits none of those, the design is wrong — not the naming.

## What enforces this

`ArchitectureGuardTest` (`:app/src/test`) does two things:

- every `.kt` under `domain/*/src/main` must not import `android.*` or `androidx.*`;
- every module in `settings.gradle.kts` must be named for a layer (`:domain:*`, `:data`, `:ui:*`,
  `:app`).

A rule the build checks does not need a wiki.
