# Architecture - Layered View with Component Descriptions

This document gives a more layered visualisation with brief descriptions of each main component.
For a technology-neutral, concept-first description of the same architecture (no language/framework
vocabulary), see `ARCHITECTURE-OVERVIEW.md`.

## Layer 1 - UI (Presentation Layer)
| Component | Purpose | Key Notes |
|---|---|---|
| **MainActivity** | Root activity; implements `WebEngineCallbackHost`, `TabViewHost`, `AutoUpdateHost`. Composition root for the screen: `onCreate` composes the models, the view collaborators and the widget bindings (`composeModels`/`inflateViews`/`composeViews`/`bindViews`), then starts the session. | Reads config only for pre-model engine checks (deliberate). Delegates its UI work — including rendering model events and the session start-up — to the collaborators below. |
| **SessionObservers** | The model→widget subscriptions: user agent, night mode, keep-screen-on, overlay style, adblock `loadError`, home-page links, bookmarks, current tab and the tab strip. | Keeps the notification flags and the order the composition root used to have; the host performs the three side effects (recreate on a night-mode change, the toast, the page-level repaint). |
| **SessionStartup** | Bringing a session up: engine-factory initialisation, the progress bar, the view-model/tab persistence load, then the saved tab / deep link / home page and the start-up update check. | Drives the start-up sequence through host callbacks (launch intent, open/change tab, post-load work). |
| **PanelController** | Tabs/bookmarks panel visibility and the focus hand-off to a row. | Claims the container focus with `requestFocusFromTouch()`, then `RowFocusPanel.requestRowFocus()` once the content is settled. |
| **ToolbarController** | Toolbar overlay show/hide/style, the menu toggle, and the in-page fullscreen window flags. | Owns the `isFullscreen` state; `autoHideMenuOverlay()` is a no-op under the keep-toolbar-open behaviour. |
| **MediaListPresenter** | The "streams on this page" list dialog and its background metadata prefetch. | Wraps `MediaListAdapter`; releases it on dismiss. |
| **PermissionOrchestrator** | Routes permission/activity-result callbacks (voice search → engine → downloads) and owns the shared request-code counter. | Consults the handlers in the same order as before. |
| **IntentHandler** | Deep links, the "open in same tab" extra and the History activity result. | Talks to the activity through navigate/open-tab/change-tab/auto-hide callbacks. |
| **IntentHandler** | Deep links, the "open in same tab" extra and the History activity result. | Talks to the activity through navigate/open-tab/change-tab/auto-hide callbacks. |
| **BookmarksController** | Bookmark panel toggle, bookmark CRUD (add/remove/edit) and favorite editing. | Renders into `vb.vBookmarks`; reads/writes through `MainActivityViewModel`; delegates the panel to `PanelController`. |
| **CursorMenuController** | Fullscreen enter/exit, the cursor menu, the link-actions popup and the "site wants to open an unknown URL" dialog. | Owns the two popup lifetimes; takes the `BackNavigationEventsAdapter` and `ToolbarController`. |
| **TabThumbnailPresenter** | The debounced thumbnail repaint and its bitmap/placeholder rendering. | Given keep-toolbar-open and "is current tab" predicates; owns the repaint `Runnable`. |
| **DownloadCoordinator** | The download permission walk and the download-service binding. | Talks to `DownloadHost` (bind/unbind + `hasPermission`/`requestPermission`), implemented by `MainActivity`. |
| **InstallPermissionHelper** | Launches the system APK installer for a downloaded file. | Shared by `DownloadsActivity` and `DownloadService`; MediaStore-aware Uri (content Uri from API 30, path below). |
| **HistoryActivity** | History list with search (voice), multi-select delete, infinite scroll. | Binds to `HistoryModel`; forwards selection to caller via result. |
| **DownloadsActivity** | Active + completed downloads, open/install APK, open folder, cancel/delete. | Binds to `ActiveDownloadsModel` + `DownloadsHistoryModel`. |
| **ActionBar/View Layer** (ActionBar, TabsView, BookmarksPanelView) | Toolbar, tabs panel (RecyclerView), bookmarks panel (RecyclerView + "Add this page"). | Observes models; uses `PanelRowFocusDelegate`/`RowFocusPanel` for deterministic D-pad row focus. Never touches repositories. |
| **SettingsDialog/Pages** | 7-category settings rendered declaratively via `SettingsItem` + `SettingsRowFactory`. | View-only; writes via model properties or model methods. Rebuilds keeping focused row. |

## Layer 2 - Presentation Models (ActiveModel)
| Component | Purpose | Key Notes |
|---|---|---|
| **TabsModel** | Manages open tabs, current selection, persistence, lifecycle (pause/save/detach). | Exposes `tabsStates` (ObservableList), `currentTab` (ObservableValue). Provides intents: `onOpenTab`, `onCloseTab`, `onToggleAdBlock`, `moveTab`, `changeTab`. `changeTab` materialises/attaches through an injected `TabViewHost` (no `ViewGroup` in the model). |
| **MainActivityViewModel** | Home page links/bookmarks, history logging (debounced), incognito mode cleanup/switch orchestration. | Owns `bookmarks` and `currentPageBookmarked`. WebView data-dir isolation is delegated to the injected `WebViewDataDirManager`. |
| **SettingsModel** | App settings as observables backed by `Config`. | Exposes observable prefs (theme, keepToolbarOpen, userAgentString). View assigns = intent. |
| **AdblockModel** | Brave adblock client lifecycle, loading/filter matching. | Loads/updates EasyList; maps request types to filter options via the pure `AdblockRequestType` (unit-tested without the native engine). Emits state via observables (incl. `loadError`); the host renders it. |
| **AutoUpdateModel** | Update checking + state; the dialog/action flow runs through `AutoUpdateHost`. | Checks GitHub Releases (semver-aware), manages auto-check cadence; exposes `isUpdateAvailable()`. |
| **HistoryModel** | Paginated history list/search. | `loadItems(offset)` or search; owns `lastLoadedItems`. Repository injected with default. |
| **DownloadsHistoryModel** | Paginated completed downloads. | Tracks all loaded items + last page. |
| **ActiveDownloadsModel** | Active download tasks + listener registry. | Coordinates cancel/update/error/all-complete notifications. |

## Layer 3 - Domain (pure, framework-free)
| Component | Purpose | Key Notes |
|---|---|---|
| **NavigationPolicy** | Scheme routing (engine-only vs external intent vs network) + automatic-redirect blocking. | Pure decisions; host does Android plumbing (Uri/intent resolution). |
| **PopupPolicy** | Popup/dialog blocking decision by host config level. | Pure. |
| **NewTabPositionPolicy** | Computes new tab insertion index (after requesting tab, else append). | Pure; used by adapter and host paths. |
| **AddressInputPolicy** | Resolves typed input to URL (search or direct). | Pure. |
| **UrlNormalizer** | Normalizes URLs. | Pure. |
| **LinkActionPolicy** | Determines link target/action eligibility (HTTP URL checks). | Pure. |
| **LinkActionPolicy** | Determines link target/action eligibility (HTTP URL checks). | Pure. |
| **SiteDomains** | Same-site/domain comparison for cross-site checks. | Pure. |
| **HomePagePolicy** | Built-in-home-page check (remote URL, `about:home` alias or the loopback URL). | Pure; the loopback URL is passed in because the asset server sets it at runtime. |
| **WebEnginePolicy** | Web-engine identity check (Gecko or not). | Pure; the engine string comes from `Config`. |

## Layer 4 - Repository (suspend-only)
| Component | Purpose | Key Notes |
|---|---|---|
| **Repositories** | Manual DI container (lazy singletons). Wires DAOs → Repos. | Single construction point; models use ctor defaults. |
| **HistoryRepository** | History CRUD + search + pagination + deleteByUrl (dedup). | All suspend. `search(q)` wraps `%q%` for a partial match on title/url. |
| **FavoritesRepository** | Bookmarks CRUD + lookup by URL/id + useful marker. | Indexed by URL (favorites_url_idx). |
| **TabsRepository** | Tabs CRUD, positions, unselect-all, bulk delete. | Persists tab state metadata. |
| **DownloadsRepository** | Downloads CRUD + pagination. | Persists download entries. |
| **HostsRepository** | Host configs (per-host settings, favicons). | Upsert/find by hostname (unique). |

## Layer 5 - Data (Room/DAOs)
| Component | Purpose | Key Notes |
|---|---|---|
| **AppDatabase** | Room DB (entities: HistoryItem, FavoriteItem, WebTabState, Download, HostConfig). | Migrations include favorites URL index. |
| **HistoryDao** | Queries: count, last, allByLimitOffset, search (LIKE), getAll, insert/update/delete variants. | deleteByUrl used for history uniqueness (latest visit wins). |
| **FavoritesDao** | Bookmarks queries (all, byId/byUrl, insert/update/delete, markAsUseful). | has index on URL. |
| **TabsDao** | Tabs persistence (all by incognito, update positions, delete/unselect). | |
| **DownloadDao** | Downloads (all by limit/offset, insert/update/delete). | |
| **HostsDao** | Host lookup/upsert/update. | Unique host_name. |

## Layer 6 - Engine
| Component | Purpose | Key Notes |
|---|---|---|
| **WebEngine** | Interface abstracting WebView/GeckoView. | View lifecycle, navigation, JS, media, virtual cursor, thumbnails. |
| **WebViewWebEngine** | WebView implementation. | Document-start overlay zapper (when supported), media candidate detection (incl. query-param manifests), prefetch metadata. |
| **GeckoWebEngine** | GeckoView implementation. | Delegates-based (Content/Navigation/Media/etc.), media candidate extraction, prefetch metadata. |
| **WebEngineCallback** | Per-tab adapter: policy + model updates. | Plain class; injected models; talks to UI via host. Delegates to domain where pure. Uses runBlocking only for synchronous popup decision (documented). |
| **WebEngineWindowProviderCallback** | Engine→app callback interface. | Defines host-facing operations engine needs. |

## Layer 7 - Service/Application
| Component | Purpose | Key Notes |
|---|---|---|
| **TVBro** | Application class. Wires Repositories, initializes ActiveModelsRepository, sets theme, registers lifecycle callbacks. | Initializes engine classes, LocalAssetServer, thread pool. |
| **ActiveModelsRepository** | Lifecycle-scoped holder of ActiveModel instances (by class). | Reflection-based construction with declared no-arg ctor (defaults). Clears models when last user gone (Activity finishing). |
| **DownloadService** | Foreground service managing downloads. | Uses Repositories/DownloadTask. |
| **DownloadTask** | Background download task implementation(s). | Reports progress/state to listeners; updates Download entity. |
