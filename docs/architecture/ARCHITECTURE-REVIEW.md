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

## Core Principles Assessment
| Principle | Rating | Notes |
|---|---|---|
| SoC | Very good | Policy vs plumbing cleanly separated (adapter vs host). |
| SRP | Good | Most cohesive; MainActivity (1413 LOC) is the main outlier (host + wiring). |
| DIP | Very good | UI depends on models/interfaces, not data. Engine via callbacks. |
| ISP | Good | Host is coherent; could split if grows. |
| Coupling/Cohesion | Very good | Low coupling, high cohesion in domain/models. |
| Law of Demeter | Good | Call chains respect boundaries. |
| Open/Closed | Good | Declarative SettingsItem/SettingsRowFactory. |
| Immutability/State Ownership | Excellent | Models own state; observables notify. |
| Testability | Excellent | Domain/repo/model/adapter testable. |

## Concrete Improvements (actionable)
| Area | Issue | Suggestion | Priority |
|---|---|---|---|
| MainActivity size | 1413 lines (host + lifecycle + panels + permissions + intents) | Extract PanelController, ToolbarController/ActionBarHostBridge, IntentHandler, PermissionOrchestrator, MediaListPresenter in small reviewable commits | Medium |
| AdblockModel | Toast on failure uses TVBro.instance | Emit ObservableValue/ErrorEvent from model, let host show UI | Low |
| AutoUpdateModel | Shows dialogs/toasts and takes MainActivity (platform side effects) | Expose state+events (sealed UpdateAction/DialogEvent), host renders/executs | Medium |
| TabsModel | Takes ViewGroup + lambda for view provider | Consider ViewHost interface (view-agnostic) | Low |
| MainActivityViewModel | WebView file-dir incognito logic | Extract WebViewDataDirManager behind injected interface | Low |
| History search | LIKE with no wildcards (deferred behavior, pinned by test) | When changing: dao.search("%$q%","%$q%") + update test (explicit behavior change) | Deferred |
| History dedup + frequentlyUsedUrls | counts always 1 due to uniqueness | Either drop redundant grouping or add visits counter | Low |

## Known Deferred
- History search wildcard wrapping (old Java "%$q%" lost) — behavior-preserving rule; pinned by `HistoryModelTest`.

## Recommendation
Proceed as-is. Tackle MainActivity extraction in small chunks if SRP is priority. Other items are polish.

