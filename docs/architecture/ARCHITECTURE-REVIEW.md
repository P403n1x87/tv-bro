# Architecture Review (post-MVVM refactor)
_Date: Fri Oct 16 2026_

This review analyzes the new architecture against core software design principles, identifies strengths and concrete improvements, and maps next steps.

## Strengths
- Clear layered architecture: domain (pure) → repository (suspend) → presentation models (ActiveModel + observables) → views/activities (render/forward). Engine behind interfaces with narrow seam.
- View purity: Activities/views do not touch AppDatabase/DAOs/Repositories; only service layer may (explicitly allowed). Direct `config` reads in MainActivity limited to engine-construction checks (deliberate).
- Clean engine/UI seam: `WebEngineCallback` is plain class (not inner Activity), injected with models, talks to UI only via `WebEngineCallbackHost`. `DownloadRequest` bundles params; `NewTabPositionPolicy` pure.
- Testable manual DI: Models take repositories with production defaults; `ActiveModelsRepository` uses `getDeclaredConstructor()`. Tests inject in-memory Room repos.
- State ownership: Models own state via observables; views observe and forward input.
- Domain pure + unit-tested: NavigationPolicy, PopupPolicy, SiteDomains, AddressInputPolicy, UrlNormalizer, LinkActionPolicy, NewTabPositionPolicy.
- Deterministic focus: `PanelRowFocusDelegate` + `RowFocusPanel` wait for layout/data before focusing rows.
- Main-screen decomposition: `MainActivity` is the host/composition root; panel, toolbar, media-list, permission and intent concerns live in small collaborators (`PanelController`, `ToolbarController`, `MediaListPresenter`, `PermissionOrchestrator`, `IntentHandler`) wired with explicit dependencies/callbacks.

## Core Principles Assessment
| Principle | Rating | Notes |
|---|---|---|
| SoC | Very good | Policy vs plumbing cleanly separated (adapter vs host). |
| SRP | Good | Most concerns cohesive; `MainActivity` is now the host/composition root after the controller split. |
| DIP | Very good | UI depends on models/interfaces, not data. Engine via callbacks. |
| ISP | Good | Host is coherent; could split if grows. |
| Coupling/Cohesion | Very good | Low coupling, high cohesion in domain/models. |
| Law of Demeter | Good | Call chains respect boundaries. |
| Open/Closed | Good | Declarative SettingsItem/SettingsRowFactory. |
| Immutability/State Ownership | Excellent | Models own state; observables notify. |
| Testability | Excellent | Domain/repo/model/adapter testable. |

## Concrete Improvements (actionable)
| Area | Issue | Suggestion | Status |
|---|---|---|---|
| MainActivity size | Was 1413 LOC (host + lifecycle + panels + permissions + intents) | **Done**: extracted `PanelController`, `ToolbarController`, `MediaListPresenter`, `PermissionOrchestrator`, `IntentHandler`; `MainActivity` keeps the host seam/composition-root role. | Done |
| AdblockModel | Toast on failure used `TVBro.instance` | **Done**: the model only sets `loadError`; `MainActivity` renders the toast and acknowledges the flag. | Done |
| AutoUpdateModel | Showed dialogs/took `MainActivity` (platform side effects) | **Done**: `AutoUpdateHost` (`canInstallPackages`/`showUpdateDialog`/`showUnknownSourcesDialog`/`openInstallPermissionSettings`/`downloadUpdate`); the model keeps only the decision. | Done |
| TabsModel | Took `ViewGroup` + a view-provider lambda | **Done**: `TabViewHost` (`createView`/`attachEngineToWindow`); `TabsModel` no longer sees a `ViewGroup`. | Done |
| MainActivityViewModel | WebView file-dir incognito logic inline | **Done**: extracted `DefaultWebViewDataDirManager` behind the `WebViewDataDirManager` interface. | Done |
| History search | `LIKE` without wildcards | **Done**: `HistoryRepository.search` wraps the query in `%…%`; the pinning tests were updated (explicit behavior change). | Done |
| History dedup + `frequentlyUsedUrls` | `frequentlyUsedUrls()` was redundant under URL-keyed history | **Done**: the method was dropped; history stays keyed by URL. | Done |
| APK install duplication | `DownloadsActivity` and `DownloadService` each had a `launchInstallAPKActivity` (the service copy was buggy: FileProvider on a `content://` Uri, missing `NEW_TASK`) | **Done**: both route through `InstallPermissionHelper.launchInstaller` (MediaStore-aware, `NEW_TASK` only off-activity). | Done |
| Permission/download/intent tests | The orchestrators had no tests; `PermissionOrchestrator`/`DownloadCoordinator` reached into the activity | **Done**: `DownloadHost` seam + voice-search lambdas; tests cover permission routing, deep links, the download walk, settings and adblock request mapping. | Done |
| `ActiveModelsRepository` contract | A model missing its no-arg ctor broke at runtime, not build time | **Done**: `ActiveModelsContractTest` reflects over the known models. | Done |
| `DefaultWebViewDataDirManager` | Read `AppContext.get()` at construction (before init in some paths) | **Done**: directories resolve through providers at use time; test covers the move/restore cycle. | Done |
| MainActivity clusters | Bookmark, cursor-menu/link and thumbnail concerns still sat in the activity | **Done**: `BookmarksController`, `CursorMenuController`, `TabThumbnailPresenter` (each ctor-injected). | Done |
| MainActivity composition body | `onCreate` was still the last big function: it built the models, the view collaborators, the widget bindings, the model→widget subscriptions and then kicked off the session load | **Done**: `onCreate` is now an outline (`composeModels`/`inflateViews`/`composeViews`/`bindViews`, then start); the subscriptions moved to `SessionObservers`, the engine init + session restore to `SessionStartup`, and the dead `prefs` field went away. | Done |
| Doc/diagram drift | Diagrams missed the new collaborators/seams | **Done**: PlantUML sources updated and png/svg/pdf re-rendered; docs list the new pieces. | Done |
| `Config` grab-bag | Mixes preference storage with pure decision logic | **Done**: `isHomePageUrl`/`isWebEngineGecko` moved to `domain/HomePagePolicy`/`WebEnginePolicy`; the unused `isWebEngineNotSet` dropped. | Done |

## Known Deferred (the next margin)
The polish pass above is complete, but it is the *first* margin, not the end. A fresh pass over the
tree (numbers as of commit `1ce745b`, unless a row says otherwise) shows the remaining improvements
are a different kind of work — mostly large, invasive or test-shape changes rather than small
extractions. Ordered by value/effort (items 1 and 6 have since landed; the numbering is kept so the
cross-reference from `ARCHITECTURE-OVERVIEW.md` stays valid):

| # | Area | Evidence | Effort / value |
|---|---|---|---|
| 1 | ~~`MainActivity.onCreate` is the last big function~~ **Done** | Was 166 LOC, with the activity at 1163 LOC / 75 `override fun` / 16 `lateinit` and 5 host interfaces. `onCreate` is now 26 LOC (the activity is 1078 LOC): `composeModels`/`inflateViews`/`composeViews`/`bindViews`, then `sessionObservers.observe()` + `sessionStartup.load(intent)`. | Done — the model→widget subscriptions live in `SessionObservers`, the engine initialisation + session restore in `SessionStartup`; the dead `prefs` field and eight dead imports went with them. |
| 2 | Global service-locator coupling | 19 sites across 14 non-test files (`TVBro.instance`, `AppContext.get()`) — down from 38; only the process-level singletons and restart flags are left, and two of the 19 are inert mentions in comments (a third, a commented-out home-page block in `HomePageHelper`, was deleted with the `androidTest` smoke suite). No model, engine, adapter, widget or service task reads a global. | **In progress**: the presentation layer is fully constructor-injected (settings, files dir, connectivity, content resolver, application context) with production defaults. The last model that read a global, `WebTabState`, now takes the directories it writes to (thumbnail cache, tab-state files) from its callers. The WebView engine and its JS bridge take the application context and the app `Config` handed down through `WebEngineFactory.initialize` (which no longer reads a global itself), and `WebEngineCallback` uses its host's `PackageManager`. The Gecko engine mirrors it: `GeckoWebEngine` takes the application context and the app `Config` the provider stores from `initialize` (and `GeckoWebEngine.initialize` receives the `Config` instead of reading it), its delegates read both off the engine (`MyPermissionDelegate`, `MyNavigationDelegate`, `AppHomeContentScriptPortDelegate`), and the D-pad `GeckoViewWithVirtualCursor` takes the `Config` too. The adapter/widget/service layer is done as well: `WebViewEx` and the cursor widgets (`CursorDrawerDelegate`, `CursorLayout`) take the app `Config` from their holders (the JS bridge; the composition root, which hands it to the inflated `CursorLayout`; `GeckoViewWithVirtualCursor`'s ctor), and the download tasks (`FileDownloadTask`, `BlobDownloadTask`, `StreamDownloadTask`) take the `ContentResolver` from `DownloadService` instead of `TVBro.instance`. **Remaining:** the process-level singletons (`FaviconsPool`, `Config`, `ShortcutMgr`, `AppDatabase`, `Repositories`) and restart flags (`AdvancedSettingsView`, `IncognitoModeMainActivity`) — that group is arguably legitimate process state and is deferred as such. High / high — the deepest coupling. |
| 3 | `Config` grab-bag | Was 13.6 KB / ~60 members mixing storage with decisions. Now `Config.kt` is 8.5 KB with 15 app-wide properties (plus the shared keys, enums and defaults); the cohesive domains live in their own slices beside it — `CursorConfig`, `AdblockConfig`, `UpdatesConfig`, `PlaybackConfig`, `EngineTuningConfig` (11 properties), reached as `config.<slice>.…`, the `CursorConfig` shape applied to the rest. The dead engine-notification member and two commented-out blocks went with the split. | **Done** — the decision logic was already extracted (item 7); the two decision-looking bits left in storage are its own documented cases (legacy home-page migration, GeckoView recommendation kill-switch), and the remaining core is genuinely app-wide state. |
| 4 | `runBlocking` on the UI path | `MainActivity.onPause` blocked the main thread on Room + the tab-state file; `WebEngineCallback.shouldBlockNewWindow` is a synchronous engine callback. | **Mostly done**: `onPause` persists through `TabsModel.saveTabInBackground`; `TabsModel.onPageStarted` warms the tab's host config off the main thread so the popup bridge resolves from memory, keeping `runBlocking` as the documented fallback for a popup that beats the warm-up. Low–medium / medium. |
| 5 | `WebEngineCallbackHost` is wide | 335-LOC adapter with a many-member host interface (ISP smell) | Medium / low–medium |
| 6 | ~~Test shape, not count~~ **Done** | Was 164 tests, all model/repository units, with nothing pinned for panel focus, D-pad, toolbar URL box or back handling. Now 216: `ActionBarTest` (the address box state machine, the key handling, the focus flags behind the D-pad chain), `PanelRowFocusDelegateTest` (the row hand-off), `PanelControllerTest` (panel focus claim/mutual exclusion/return) and `BackNavigationEventsAdapterTest` (press/release, cross-channel suppression, controller long-press) — all Robolectric over the real inflated layouts (`app/src/test`, same task as the rest of the suite). | Done — a full `MainActivity` launch stays out of reach on the JVM (the ad-block model loads a native library; the download-service binding's shadow passes a null `ComponentName`), so that level got an instrumented `androidTest` setup: `MainScreenSmokeTest` (`app/src/androidTest`) drives the whole activity on a device — cold start, VIEW-intent page load, history back navigation, bookmarks panel — through `:app:connected<Flavor>DebugAndroidTest`. |
| 7 | Packaging | `:app:common` is 4092 LOC (Config + singletons + utils + cursor widgets); the engine factory/provider seam is the last global inside it. Gecko only sees `:app:common` (correct), but `common` is a grab-bag. | High / low–medium |

## Recommendation
The first margin is closed: the main screen is decomposed into constructor-injected collaborators,
models own their state without touching Activity/dialog classes, the host seams (`TabViewHost`,
`AutoUpdateHost`, `DownloadHost`, `WebViewDataDirManager`) are the only UI escape hatches, and the
pure decisions live in `domain/`. Keep new UI side effects behind such a seam rather than in a model.

The integration-test margin (item ⑥) is closed too: the toolbar URL box, the panel focus hand-off
and the back-channel handling are pinned by Robolectric screen tests over the real inflated layouts,
and `MainScreenSmokeTest` (instrumented, `app/src/androidTest`) launches the whole `MainActivity`
on a device/emulator — cold start, VIEW-intent page load, history back navigation, bookmarks panel.
The next margin is now the remaining structural items — the wide `WebEngineCallbackHost` (item 5)
and the `:app:common` packaging (item 7) — plus, if deeper
regression safety is wanted, extending that instrumented suite with scenario coverage (the JVM
cannot load the ad-block native library nor shadow the download binding).
The engine/adapter/widget/service layer no longer reads globals, and what remains
(`FaviconsPool`/`Config`/`ShortcutMgr`/`AppDatabase`/`Repositories` and the restart flags) is
process-level state.

