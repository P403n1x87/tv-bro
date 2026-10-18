# tv-bro — agent notes

Android TV browser. `master` already carries the MVVM refactor, the `MainActivity` decomposition and
the model/host seam polish. Work on a feature branch; **open a PR only when asked**.

## Layout / flavors
- Flavors: `p403n1x87` (this fork, self-updates from `P403n1x87/tv-bro`), `generic`/`google`
  (upstream `truefedex/tv-bro`).
- `domain/` is plain Kotlin + JUnit; `repository/` is one `suspend` class per DAO; the rest is
  Android. Behavior-carrying decisions belong in `domain/`, not in views.

## Architecture
- Custom MVVM: no Hilt, no androidx `ViewModel`/`StateFlow`. Activities/views get their models from
  `ActiveModelsRepository` (reflection → every primary-ctor param needs a default).
- Layers: views → presentation models (`MainActivityViewModel`, `TabsModel`, `SettingsModel`,
  `AdblockModel`, `AutoUpdateModel`, `HistoryModel`, `DownloadsHistoryModel`,
  `ActiveDownloadsModel`, `DownloadStorageModel`) → repositories → Room. Views never touch `AppDatabase`/`Repositories`
  (only the service layer may).
- `WebEngineCallback` is a plain per-tab engine adapter; all UI access goes through the
  `WebEngineCallbackHost` interface (`MainActivity` is the only host). Pure policy in `domain/`
  (`NavigationPolicy`, `PopupPolicy`, `SiteDomains`, `AddressInputPolicy`, `UrlNormalizer`,
  `LinkActionPolicy`, `NewTabPositionPolicy`, `HomePagePolicy`, `WebEnginePolicy`).
- Engines get the app `Context` and `Config` through `WebEngineFactory.initialize`, which the
  composition root drives; the provider hands them to each engine it creates, so
  `WebViewWebEngine`/`AndroidJSInterface` take both as ctor params instead of reading
  `AppContext`/`TVBro`.
- `MainActivity` is the composition root/host; `onCreate` is an outline (`composeModels`,
  `inflateViews`, `composeViews`, `bindViews`, then `sessionObservers.observe()` +
  `sessionStartup.load(intent)`).
  Its UI work is delegated to `PanelController`, `ToolbarController`, `MediaListPresenter`,
  `PermissionOrchestrator`, `IntentHandler`, `BookmarksController`, `CursorMenuController`,
  `TabThumbnailPresenter`, `DownloadCoordinator`, `SessionObservers` (every model→widget
  subscription) and `SessionStartup` (engine init + session restore). Collaborators must not read
  `MainActivity` privates — take deps/callbacks via ctor (e.g. `DownloadCoordinator` takes a
  `DownloadHost`).
- Models must not touch Activity/dialog classes: UI side effects go through a host seam or an
  observable the host renders — `TabViewHost` (`TabsModel.changeTab`), `AutoUpdateHost`
  (`AutoUpdateModel.showUpdateDialogIfNeeded`), `DownloadHost` (`DownloadCoordinator`),
  `AdblockModel.loadError` (host resets it). `MainActivity` implements all of them.
- Models own state; a view assigning a model property is the accepted "intent" pattern. In tests,
  pass repository fakes (existing tests build in-memory Room instances).
- Models take every dependency — repositories, `Config`, and Context/`filesDir`/connectivity
  accessors — as ctor params with production defaults. Models must not read `AppContext`/`TVBro`
  directly (the default lambdas may, but only at the composition edge).
- Deliberate exception to view purity: `MainActivity` reads `config` for pre-model engine checks
  (`incognitoMode`, `isWebEngineGecko()`).
- First architecture margin is closed, and so are its two follow-ups (the `MainActivity.onCreate` body
  now delegates to `SessionObservers`/`SessionStartup`, and the four flagged screen areas — toolbar
  URL box, panel focus, D-pad chain, back handling — have Robolectric screen tests); the next margin
  is the remaining structural items (wide `WebEngineCallbackHost`, `:app:common`
  packaging); `Config`'s cohesive slices already moved to `config/` (`AdblockConfig`, `UpdatesConfig`,
  `PlaybackConfig`, `EngineTuningConfig` next to `CursorConfig`). The global
  `TVBro.instance`/`AppContext.get()` coupling is now gone from models,
  engines, adapters, widgets and download tasks; what is left is process-level state (singletons,
  restart flags) plus a few inert mentions in comments. Details/evidence:
  `docs/architecture/ARCHITECTURE-REVIEW.md`.

## Build / test
- Linux: JDK 21 exported, plus JDK 17 at `/usr/lib/jvm/java-17-openjdk-amd64` (`buildSrc` pins
  `jvmToolchain(17)` → both required). macOS: `JAVA_HOME="$HOME/java/jdk-21.0.12.1+1/Contents/Home"`.
  Android SDK at `$HOME/.android/sdk`; AVD `tvbro_tv_api36`.
- Run Gradle tasks **serially** (concurrent builds corrupt the Kotlin incremental state).
- Formatting: Spotless + ktlint (`ktlint_official` style; config in root `build.gradle.kts`, rules in
  `.editorconfig`). `./gradlew spotlessApply` fixes; `spotlessCheck` gates CI's lint job and the
  pre-commit hook. Git hooks are opt-in per clone — `git config core.hooksPath .githooks`
  (`.githooks/pre-commit` = `spotlessCheck` when staged `.kt/.kts` change, seconds;
  `.githooks/pre-push` = single-flavor Android lint, ~1m warm; both skippable `--no-verify`).
  Gotcha: ktlint reports some `max-line-length`/`comment-wrapping` lints on *intermediate*
  formatting passes of an unformatted file — if `spotlessApply` exits 1 on a file you touched,
  `ktlint --format <file>` (ktlint 1.8 CLI) writes it anyway; afterwards the reports are stable
  and any hit is a real violation.
- Tests: `:app:player:testDebugUnitTest` (`:app:testDebugUnitTest`), `:app:testP403n1x87GeckoIncludedDebugUnitTest`
  (216: model/repository units + the Robolectric screen tests — `ActionBarTest`,
  `PanelControllerTest`, `PanelRowFocusDelegateTest`, `BackNavigationEventsAdapterTest`, over real
  inflated layouts, `sdk = 26`, plain `Application`). Screen-test gotchas: hand
  `binding.flWebViewContainer.config` its `Config` after inflate (as `MainActivity.onCreate` does,
  else `CursorLayout.onSizeChanged` throws); build `KeyEvent`s with real `downTime`/`eventTime`
  (`KeyEvent(action, code)` carries 0, so time-window logic never matches); the URL box enters edit
  mode on key **ACTION_UP**, not down. A full `MainActivity` launch is not possible on the JVM
  (ad-block native lib, null `ComponentName` from the binding shadow) → that level is covered by
  the instrumented smoke suite: `:app:connectedP403n1x87GeckoIncludedDebugAndroidTest`
  (`MainScreenSmokeTest`, UiAutomator, needs a booted device/emulator; ~25s of tests). Its gotchas:
  the instrumentation runs inside the app process, so a `pm clear` in `@Before` kills the run —
  write tests state-independent instead; a VIEW intent opens a **new tab** (single-entry history,
  back is a no-op) unless the intent carries `com.phlox.tvwebbrowser.EXTRA_OPEN_IN_SAME_TAB`; the
  bookmarks panel's "Add this page" row is hidden on the home page (assert `vBookmarks` instead).
- Debug APK: `./gradlew :app:assembleP403n1x87GeckoIncludedDebug -PenableAbiSplits`.
- Launch a URL: `am start -a android.intent.action.VIEW -d "http://example.com" -p <appId>`; click
  `input tap <x> <y>`. `p403n1x87` → `com.phlox.tvwebbrowser.p403n1x87`, else `…webbrowser`.

## Releasing
- Version comes from git tags (`vX.Y.Z`), never hardcoded; `./gradlew` derives it.
- CI `.github/workflows/release.yml`: `tests` (the three unit suites), `lint` (Android lint on
  both app flavors + player + common, zero-baseline; `MissingTranslation` is the one global
  exemption, disabled in buildSrc) and `instrumented` (the UiAutomator smoke suite on an API 26
  emulator, KVM-enable step included) run as separate jobs and `build` depends on all three
  (`needs: [tests, lint, instrumented]`), so neither an APK build nor a release happens on red
  tests, a new lint error or a broken device flow; runs on every PR that touches code or the build
  harness; a docs-only PR (`docs/**`, `*.md`, repo metadata) runs no CI at all; publish only from a
  tag.
- `gh release create vX.Y.Z --target <ref>` creates the tag → workflow attaches signed APKs and sets
  the body from `RELEASE_NOTES.md`. Keystore: copy of the debug keystore under `secrets`
  (fallback `signing.keystore`); **never regenerate** (it is what upgrades installs in place).

## Known issues / gotchas
- History is keyed by URL (`HistoryDao.deleteByUrl` before insert → visits collapse to one row,
  latest time wins).
- Home page: `pages/home/*` is served from bundled assets by **request URL** in
  `WebViewEx.shouldInterceptRequest`; never gate it on the current tab URL — on Back/Forward the
  document request lands before `onPageStarted` updates the tab, and the stale remote copy loads
  (its markup differs, so the injected `renderSections` call fails).
- Panels: claim focus with `requestFocusFromTouch()`, then hand to a row via
  `RowFocusPanel.requestRowFocus()` once content is settled (row lookup uses
  `findViewHolderForAdapterPosition`).
- D-pad: a disabled `View` still reports focusable — keep `isFocusable` in sync with `isEnabled` and
  check `isShown`; the toolbar URL box is a button until activated.
- Toolbar overlay keeps the live page (`showMenuOverlay`); the auto-hide seam is `autoHideMenuOverlay()`
  (no-op when configured off).
- Gecko auto-redirect: use `request.hasUserGesture`, not `!isDirectNavigation`.
- Stream list: `MediaUrlIdentity.key()` collapses signature rotations.
- Live streams: retries resume at the live edge; when they exhaust, the host reloads the page and
  replays the identity-matched stream via the shared `MediaAutoReplay` (used by both engine
  adapters; one automatic replay per user playback). Whether a failure is refresh-recoverable is
  `PlaybackRetryPolicy.canRecoverByRefresh` and is live-scoped, so a non-live stall never restarts
  from zero.
- Settings: declarative `SettingsItem` + `SettingsRowFactory`, pages rebuild with `post { }`.
- Native player BACK is opted out of predictive back (`enableOnBackInvokedCallback=false`).
