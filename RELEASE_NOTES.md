Changes since v2.1.6: a built-in video player for detected streams, a rebuilt top
bar and settings dialog, and a large D-pad/remote usability pass.

### Video playback
* Streams listed under "Streams on this page" can now play in a built-in player
  (Media3/ExoPlayer), shared by both the WebView and the GeckoView engine.
* New Playback > Video player setting chooses between the built-in player, the
  system player and an external app. There is no longer an automatic fallback
  chain: a stream that fails only fails, and the user picks another one.
* The built-in player retries transient interruptions (connectivity, timeouts,
  HTTP errors, falling behind a live edge) with growing backoff and resumes at
  the position it reached; codec, parsing and DRM errors fail immediately.
* BACK takes two presses in the built-in player: the first only reveals the
  controls, so a remote can no longer leave the player by accident.
* The automatic "open fullscreen videos in the native player" handoff and the
  "play current video in a player" shortcut are gone. The app has no reliable
  way of telling which video on a page is "the" video, so both mechanisms
  guessed; streams are now only ever played from the explicit stream list.

### Stream detection
* The stream list shows one row per stream with a leading icon, a short label
  and, for HLS/DASH manifests, resolution, bandwidth, languages and duration
  read in the background while the list is already usable.
* Manifests hidden behind extensionless gateways (e.g. a `.m3u8` in a signed
  query string) are now detected on the WebView engine too.
* Rotating signatures on the same stream no longer add a new row each time.
* The current page URL is no longer listed as if it were a detected stream.

### Top bar and panels
* The URL box behaves like a button: focusing it no longer starts text editing,
  so LEFT/RIGHT cross it in a single press instead of stepping over every
  character. Editing starts on OK/ENTER (with the whole URL selected), a click,
  or by typing; leaving it returns the box to its button state.
* The URL box is a rounded pill, and the toolbar is exactly one button tall.
* Toolbar buttons appear only when they are useful: the tab button hides when
  there is a single tab, the stream button hides when nothing was detected, and
  the count badges only show when the count is greater than one.
* Settings moved out of the overflow menu into the toolbar; the overflow menu
  gained a "Toggle theme" entry, and there are new shortcuts for the tab panel,
  the stream list, the bookmarks panel and closing a tab.
* Bookmarks are a right-side panel behind the star button: it adds or removes
  the current page, lists all bookmarks, and edits/deletes them on long press.
* The tab panel is a left-side panel, the toolbar is a pinned top bar, and the
  toolbar can be kept open over the live page instead of being swapped for a
  thumbnail (Appearance > Keep toolbar open).
* Light/dark/system theme switching applies immediately, without a restart, and
  the toolbar icons follow the theme instead of staying black.
* The tab counter is the number of open tabs, and single-tap tab switching works
  again on touch devices.

### Remote (D-pad) fixes
* Disabled Back/Forward and hidden buttons no longer trap the focus: LEFT/RIGHT
  simply skip them.
* The tab panel and the bookmarks panel take the focus reliably when opened,
  including right after a tap, so they can always be navigated.
* The close "X" of a tab row is reachable with the D-pad (RIGHT opens it, LEFT
  returns to the row).
* Pressing BACK while the page has the focus now returns to the toolbar instead
  of doing nothing.

### Settings
* Rebuilt as a native TV two-pane dialog with 7 categories: General, Ads &
  privacy, Appearance, Playback, Controls, Advanced and About.
* All shortcut bindings are merged into Controls, and every shortcut row lets
  the key be rebound.
* About shows the app, build flavor and engine version, links to the repository,
  the original project, support, license and privacy policy, plus the updates
  section below.

### Updates
* The app checks this fork's GitHub Releases, picks the APK matching the build
  flavor and device ABI, and can download and install it from within the app.
* A badge on the settings gear and a dot on the About category appear while an
  update is available.
* Downloaded update files are cleaned up so a cancelled or failed download no
  longer leaves a stale APK behind.

### Ad blocking and privacy
* New cosmetic overlay zapper: transparent click traps, hidden off-screen
  popunder links and ad boxes in shadow roots are removed at runtime, on top of
  the network blocker.
* Cross-site navigations without a user gesture (script redirects) are blocked
  when ad blocking is enabled, while real link taps always go through.

### Other
* History keeps a single row per URL with its latest visit time instead of one
  row per visit.
* Bounded favicon reads and removal of leftover content-script scaffolding.
* The version is derived from git tags and release signing is configured in CI;
  only the P403n1x87 flavor is released from this repository.
