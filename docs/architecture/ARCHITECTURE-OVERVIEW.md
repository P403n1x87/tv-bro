# Architecture overview — the macro-components in plain terms

_Date: Sat Oct 17 2026_

This document describes the **big moving parts** of the application and how they depend on each
other. It is written in deliberately technology-neutral language: it avoids language- and
framework-specific vocabulary wherever possible, so it can be read by someone who has never seen
this platform, this language, or this code base before.

Two conventions keep it readable:

- Generic words are used for the concepts (`screen`, `state holder`, `storage layer`, `seam`).
- Concrete names (components and files as they appear in the tree) are only used in the
  **translation table** at the end, and never assumed in the prose.

For the class-level inventory see `ARCHITECTURE-COMPONENTS.md`; for the current gaps and next steps
see `ARCHITECTURE-REVIEW.md`; for diagrams see `ARCHITECTURE.plantuml` / `ARCHITECTURE-LAYERED.plantuml`.

---

## 1. What the application is

A television web browser: a **screen** that embeds a third-party rendering engine (the "browser
core") and adds television-specific navigation — a remote-control cursor, a tabs/streams/bookmarks
overlay, an ad blocker, downloads and a built-in offline start page.

The application is split into **modules** (independently compiled bundles of code):

| Module | What it is | Why it is separate |
|---|---|---|
| Main application | Screens, state holders, storage layer, services, one browser-core adapter. | Everything that is specific to *this* application. |
| Shared library | Records, settings, the browser-core contract, shared widgets and small utilities. | Used by both browser-core adapters and by the main application; it declares no application start-up of its own. |
| Browser-core adapter (second engine) | A second implementation of the browser-core contract. | It pulls in a large third-party engine; keeping it separate lets builds exclude it. |
| Native player | An independently packaged media player screen. | It has its own lifecycle and tests. |

A browser-core adapter is only compiled into a build if the corresponding engine library is
available, so the same source tree can produce "with engine A", "with engine B" or "with neither".

---

## 2. The macro-components

### 2.1 Decisions (pure policy)

Small, self-contained pieces that answer one question each and return an answer. They depend on
nothing: no platform, no storage, no screen.

Examples: is this typed text a search or an address? May this popup open for this site? Is this link
on the same site? Where does a new tab go? Is this address the built-in start page? Which engine is
selected?

Why they are separate: these are the *product rules*. Keeping them isolated makes them testable
without a device and prevents the same rule from being re-implemented slightly differently in two
places. In this repository the rule is explicit: behaviour-carrying decisions belong here, not in
screens and not in state holders.

### 2.2 Records (data shapes)

Plain value records for the things the application stores and passes around: a tab, a download, a
bookmark, a history entry, a per-website configuration, a start-page link.

They carry fields, not decisions. Some of them double as the table definitions of the storage layer,
which is why they live in the shared library: both the browser-core adapters and the storage layer
need to describe a tab.

### 2.3 Storage layer

Spoken about loosely, the "data layer" is several things at once. It has three strata — two of them
code written here, one of them a library:

| Stratum | What it is | Vocabulary it speaks |
|---|---|---|
| Persistence framework | A library that does the mechanical work: it maps records to tables, generates the implementation of the query objects, runs queries off the interface thread, and applies transactions and schema migrations. Very little code is written here — it is driven by declarations. | the query language (SQL) |
| Database holder and query objects | The one database instance per process (it owns the table list and the migration history), plus one **query object** per table: a small contract listing the queries the application needs ("list a page of downloads", "find a bookmark by address", "delete history rows for this address"). | tables and query syntax |
| Repositories | One per storage area: the operations the application actually wants — create, read, update, delete, search, paginate. | the application's own vocabulary |

The boundary between the last two is the interesting one, and it is *not* arbitrary:

- A **query object** is shaped like its table. Query syntax shows through its parameters (a page
  size, a partial-match pattern, a sort order). It holds no logic and no product rules.
- A **repository** is shaped like a use case. It hides the query syntax, converts application input
  into it, and is where product rules live (what a search means, that a revisited page replaces its
  history row, how big a page is).
- A repository is also the **substitution seam**: swapping the storage technology, or passing an
  in-memory one in a test, is a change in the repository wiring (§3.2) and nowhere else. In the
  current tree each repository wraps exactly one query object; for the thinnest ones that is close to
  a pass-through, and it pays off in the ones that carry a rule and as the fake-able boundary for
  tests. If a use case ever needs to span two query objects atomically, that belongs in a repository,
  never in a query object or in a state holder.

Repository operations are **non-blocking** (§3.5): reading or writing storage must never freeze the
screen. A single wiring component constructs each repository **once** and hands the same instance to
everyone who needs it (§3.1, §3.2).

**Why the storage layer is not a state holder.** A state holder *owns* what the user sees; a
repository merely *stores*. Keep the split: state holders persist through repositories, and only the
background-service layer is allowed to talk to storage without a screen in between (it has no screen
to route through).

### 2.4 Screen state holders (the presentation models)

Each screen with real state is paired with a **state holder** that:

- owns the state the screen renders (the open tabs; the current download list; the settings; the
  update status; the history page; the bookmark list);
- publishes that state as **change notifications** (§3.4), so the screen re-renders when it changes;
- exposes *intents* — the actions a screen can ask for ("open a tab", "close a tab", "toggle the ad
  blocker", "change the theme") — and performs them;
- persists through the repositories, never by talking to the database.

State holders are **not** tied to the lifetime of a single screen: switching between screens keeps
the same holder, and therefore the same state, which avoids duplicated state and stale data. They are
released when the last screen using them goes away.

Two rules follow, and are enforced in this code base:

1. **A state holder never touches a screen, a dialog or a widget.** Anything visible must be
   requested through a *seam* (§2.5) or announced as a change notification the screen renders.
2. **A state holder declares its dependencies** (the repositories, the settings object, the storage
   directories, service handles) as initialisation parameters, with production defaults. That is what
   makes the same component constructible with fake storage in a test — see §3.1.

### 2.5 Seams (ports)

A **seam** is a narrow contract that names *what* one side needs without knowing *who* provides it.
They point in two directions:

- **Outward (state holder → screen).** When a state holder must cause something visible — show a
  dialog, ask for a permission, create a widget, repaint — it calls a contract that the screen
  implements. The state holder therefore compiles without referencing a screen type.
- **Around the engine (engine → application).** The browser-core adapter needs the application for
  anything platform-shaped: opening a new screen, resolving an external link, a permission prompt, a
  custom widget in full screen. It calls a contract the main screen implements, while everything that
  is pure policy goes to §2.1 instead.

A seam is deliberately *small*: it lists only what the other side genuinely needs. When a seam grows,
that is a signal that the component behind it has too many responsibilities.

### 2.6 Browser-core adapters

The application never talks to an embedded engine directly. It talks to a **contract** that describes
a browser core in application terms: load an address, go back/forward, save and restore state, render
a thumbnail, list the media streams found on the page, enable the cursor.

Two implementations exist: one over the platform's built-in browser widget, one over a third-party
engine. A registration point created at start-up picks the implementation named in the settings and
hands it the platform handle and the settings object; from then on every tab gets its adapter from
the same place.

Everything the engine must *report back* — page started/finished, title changed, a new window
requested, a download requested, a permission requested — travels over a **per-tab adapter**: one
small object per tab that owns the routing/policy half (which address is this, may this popup open,
where does a new tab go) and reaches the screen only through its seam.

### 2.7 Screens and view collaborators

A **screen** is the platform's unit of visible interaction. Two kinds of components sit around it:

- **View collaborators**: short-lived helpers that each own one visible concern — the overlay panel
  and its focus hand-off, the toolbar, the media-stream list dialog, the permission routing, the
  deep-link handling, the bookmark panel, the cursor menu, the thumbnail repaint, the download
  permission walk, the subscription of state changes into the widgets, and the session start-up
  (engine initialisation and state restore). They hold no domain state; they render and forward, and
  they receive everything they touch as initialisation parameters instead of reaching into the
  screen.
- **Widgets**: the reusable visible controls (toolbar, panes, cursor surface, focus helpers).

Screens and widgets read state from the state holders (§2.4) and must never open the storage layer
directly (§2.3).

---

## 3. Concepts used above, defined generically

These are the only ideas the prose depends on. None of them requires knowledge of a specific
language or framework.

### 3.1 Constructor injection (dependencies as parameters)

Instead of a component reaching for a global "everything is here" registry when it needs something,
each component **receives** what it needs at creation time. The same component can then be created a
second time with *different* inputs — fake storage, a fake settings object, a fake screen — which is
exactly what tests do. This repository uses that style throughout (called manual dependency
injection, because there is no framework generating the wiring).

### 3.2 A shared, single instance (and "created on first use")

Some things must exist exactly once per running process: the settings object, the database, the
repository wiring, the registry of state holders, the process-wide media/asset helpers. They are not
created repeatedly — the wiring component that owns them creates each one the first time it is asked
for and returns the same instance afterwards.

"Created on first use" (lazily) matters because a few of them cannot be built before start-up has
finished.

### 3.3 Lifetime-bound scopes

A running task (a background download, a page load, a settings reload) should not outlive what it
serves. State holders therefore own a **scope**: a boundary that groups the tasks started for them
and cancels them together when the holder is released. Without that boundary, a task can finish after
its screen is gone and write into stale state.

### 3.4 Change notifications (observables)

A screen renders state, but it must not *poll* for it. Instead, state is wrapped in a small
notification channel: a value that tells every interested party when it changes. The state holder
mutates the wrapped values; the screen subscribes and re-renders. The contract is one-directional and
simple — *pushes changes out, takes intents in* — which is what keeps state ownership in one place.
There is no third-party state framework here; these channels are small hand-written types.

### 3.5 Non-blocking (asynchronous) operations

Some operations are slow on purpose: reading a large history table, writing a downloaded file,
checking a remote version. If a screen waited for them, the interface would freeze — unacceptable on
a television remote.

Modern runtimes solve this by letting the operation **pause itself** instead of blocking the thread
that called it: the runtime resumes it later, possibly on a worker thread, while the caller writes the
sequencing as ordinary-looking code. In this code base, operations that can pause this way are
spelled with a distinctive keyword (**`suspend`**) — you will meet it throughout the repository layer.
They need no more exotic reading than "this takes a while; the runtime will come back to it".

The counterpart is a **blocking bridge** (**`runBlocking`** here): sometimes the platform demands an
answer *immediately*, inside a callback that cannot pause. There the code has no choice but to wait.
The architecture confines that to one documented fallback path (the synchronous popup decision)
instead of letting it spread; page persistence and the popup path were moved off the interface
thread for the same reason.

### 3.6 Lifecycle callbacks, briefly

Screens and processes are not plain functions: the platform creates them and calls named hooks on
them ("created", "started", "paused", "destroyed"). This architecture uses exactly two of those hooks
as composition points — the process start-up and the main screen's creation (§4) — and keeps domain
and storage logic out of them.

---

## 4. Start-up and wiring (the two composition points)

Nothing else wires the application. The two places are:

1. **Process start-up.** Installs the application-wide platform handle, reads the settings, starts the
   small built-in asset server that serves the offline start page, registers the browser-core
   adapters, starts the state-holder registry, creates the worker pool, and installs the process-wide
   lifecycle observer.
2. **Main screen creation.** The screen is the composition point for the *screen*: it takes the
   shared objects, asks the registry for its state holders, creates the view collaborators (§2.7),
   gives the widgets the values they need, and implements every seam the state holders and the engine
   expect. The body itself is an outline — compose the state holders, inflate the screen, compose the
   collaborators, bind the widgets — followed by the two collaborators that do the work: one subscribes
   the widgets to the state changes, the other brings the session up (engine initialisation, restore of
   the saved state, then the pending deep link or the start page).

The intent is that these two bodies stay wiring-only: no decisions and no storage logic live there.

---

## 5. Dependency direction

The arrows below mean "may use / call". The rule is one-directional: a component may use the layer
below, never the one above.

```
   decisions (no platform, no storage) ──────────────────────────┐
        ▲  used by                                              │
        │                                                       ▼
  state holders ──► repositories ──► storage (database + query objects)
        ▲                                                      
        │ change notifications / intents                        
        │                                                      
  screens, widgets, view collaborators ──► seams (ports) ──► platform operations
        │
        ├── browser-core contract ◄── adapters ◄── engine libraries
        └── background services (may touch storage; have no screen)
```

In words:

- **Decisions** depend on nothing; they never import platform or storage code.
- **State holders** depend on repositories, on records, on decisions, and on seams for anything
  visible. They never reference a screen or a widget type.
- **Screens and widgets** depend on state holders (change notifications and intents) and on the
  browser-core contract. They use repositories **not at all**.
- **Browser-core adapters** depend on the engine libraries, the decisions layer and the seams the
  screen implements; the screen depends only on the browser-core contract, never on a concrete engine.
- **Background services** are the one exception allowed to touch storage directly, because they have
  no screen to route through.

### 5.1 A page load, end to end

1. The engine reports "page started at this address" over its per-tab adapter.
2. The adapter applies the pure decisions (new tab? external application? blocked?) and updates the
   tab state holder.
3. The state holder updates its change-notified values; the screen re-renders the address box and the
   tab strip.
4. If the address is the built-in start page, the small asset server serves it from the bundled
   files, so it renders with no network at all.

---

## 6. Translation table: neutral term → this code base

Names are as of the tree at the time of writing (Oct 2026). Use them to navigate; reason about the
architecture with §2–§5.

| Neutral term | Concrete names here |
|---|---|
| Modules | `:app`, `:app:common`, `:app:gecko`, `:app:player` (plus the flavour sources under `app/generic`) |
| Records | `WebTabState`, `Download`, `FavoriteItem`, `HistoryItem`, `HostConfig`, `HomePageLink` (all in the shared library) |
| Persistence framework | Room (the database library the query objects are declared for) |
| Database holder | `AppDatabase` (owns the table list and the migration chain) |
| Query objects | `TabsDao`, `DownloadDao`, `FavoritesDao`, `HistoryDao`, `HostsDao` |
| Repositories | `TabsRepository`, `DownloadsRepository`, `FavoritesRepository`, `HistoryRepository`, `HostsRepository` |
| Repository wiring (single construction point) | `Repositories` |
| Decisions | `NavigationPolicy`, `PopupPolicy`, `SiteDomains`, `AddressInputPolicy`, `UrlNormalizer`, `LinkActionPolicy`, `NewTabPositionPolicy`, `HomePagePolicy`, `WebEnginePolicy` (package `domain`) |
| State holders | `MainActivityViewModel`, `TabsModel`, `SettingsModel`, `AdblockModel`, `AutoUpdateModel`, `HistoryModel`, `DownloadsHistoryModel`, `ActiveDownloadsModel` |
| State-holder base and registry | `ActiveModel`, `ActiveModelsRepository` (pinned by `ActiveModelsContractTest`) |
| Change notifications | `ObservableValue`, `ObservableList` (over `Subscribable`) |
| Screens | `MainActivity` / `IncognitoModeMainActivity`, `HistoryActivity`, `DownloadsActivity` |
| View collaborators | `PanelController`, `ToolbarController`, `MediaListPresenter`, `PermissionOrchestrator`, `IntentHandler`, `BookmarksController`, `CursorMenuController`, `TabThumbnailPresenter`, `DownloadCoordinator`, `SessionObservers` (the widget subscriptions) |
| Session start-up | `SessionStartup` (engine initialisation, state restore, the start page/deep link) |
| Seams | `WebEngineCallbackHost`, `TabViewHost`, `AutoUpdateHost`, `DownloadHost` (plus the small `Callback` contracts on the widgets) |
| Browser-core contract | `WebEngine` |
| Engine registration/dispatch | `WebEngineFactory`, `WebEngineProviderCallback`, `WebEngineWindowProviderCallback` |
| Browser-core adapters | `WebViewWebEngine` (+ `WebViewEx`, `AndroidJSInterface`); `GeckoWebEngine` (+ its delegates) in `:app:gecko` |
| Per-tab adapter | `WebEngineCallback` |
| Shared media/player plumbing (`:app:player`) | `MediaUrlIdentity`, `MediaRequestCatalog`, `MediaAutoReplay`, `PlaybackRetryPolicy`, `NativeVideoActivity` |
| Settings object | `Config` |
| Application/platform handle | `AppContext`, `TVBro` |
| Built-in asset server | `LocalAssetServer` |
| Background services | `DownloadService` and `DownloadTask` (`FileDownloadTask`, `BlobDownloadTask`, `StreamDownloadTask`) |
| Widgets | `ActionBar`, `TabsView`, `BookmarksPanelView`, `CursorLayout`, `CursorDrawerDelegate`, `DPADNavigationEventsAdapter`, `BackNavigationEventsAdapter` |

## 7. Where the platform still shows through

An honest list of the places where the generic description above deliberately stops:

- The process start-up hook and the screen creation hook are platform callbacks; they are the two
  composition points.
- The two browser-core adapters are written against two specific engine libraries. Everything on the
  application side of them is behind the contract.
- A few process-wide values remain globally reachable (the settings object, the database, the
  repository wiring, a favicon pool, a shortcut helper, an application handle) because they are, by
  nature, one per process. The ongoing work to reduce that list is tracked in
  `ARCHITECTURE-REVIEW.md` (item ②).
- Tests are usually run without a device against fake or in-memory storage; a handful of components
  (the engine adapters, the widgets, the services) are only exercised on a device.
