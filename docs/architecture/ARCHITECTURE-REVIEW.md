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
| Doc/diagram drift | Diagrams missed the new collaborators/seams | **Done**: PlantUML sources updated and png/svg/pdf re-rendered; docs list the new pieces. | Done |
| `Config` grab-bag | Mixes preference storage with pure decision logic | **Done**: `isHomePageUrl`/`isWebEngineGecko` moved to `domain/HomePagePolicy`/`WebEnginePolicy`; the unused `isWebEngineNotSet` dropped. | Done |

## Known Deferred
- None. All behavior-affecting items landed with their tests.

## Recommendation
The refactor, the follow-up polish pass and the deferred improvements are complete: the main screen
is decomposed into constructor-injected collaborators, models own their state without touching
Activity/dialog classes, the host seams (`TabViewHost`, `AutoUpdateHost`, `DownloadHost`,
`WebViewDataDirManager`) are the only UI escape hatches, and the pure decisions live in `domain/`.
Keep new UI side effects behind such a seam rather than in a model.

