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
