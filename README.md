<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/branding/readme-banner-dark.png">
    <img src="docs/branding/readme-banner.png" alt="Odysseus" width="760">
  </picture>
</p>

<h3 align="center">A simple web browser built for the TV remote</h3>

<p align="center">
  <a href="https://github.com/P403n1x87/tv-bro/releases"><img alt="Latest release" src="https://img.shields.io/github/v/release/P403n1x87/tv-bro?sort=semver"></a>
  <a href="https://github.com/P403n1x87/tv-bro/releases"><img alt="Downloads" src="https://img.shields.io/github/downloads/P403n1x87/tv-bro/total"></a>
  <img alt="Android 7.0+" src="https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white">
  <a href="LICENSE.md"><img alt="License" src="https://img.shields.io/badge/license-BSD--3--Clause-blue"></a>
</p>

Odysseus is a lean web browser designed from the ground up to be driven with a TV remote. It runs
on Android TV, on phones and on tablets, and leans on the system's own web rendering engine rather
than shipping one of its own — so it stays light, starts fast and keeps up with WebView updates.

## Features

| | |
|---|---|
| **Remote-first navigation** | A focus ring and D-pad handling that follow what a remote can actually do, including a cursor mode for pages that need one. |
| **Tabs and bookmarks** | Manage many pages at once, with a library of saved bookmarks and shortcuts on the home page. |
| **Voice search** | Speak the query instead of typing it with a remote. |
| **User-agent switching** | Present the browser as a desktop, phone or TV client per site, so pages serve the right layout. |
| **System web engine** | Rendering runs on Android's built-in WebView (Blink/WebKit), with an optional GeckoView flavor. |
| **Download manager** | Downloads are handled in-app, with their own history and storage overview. |
| **Browsing history** | Revisit pages from a history that keeps the latest visit per address. |
| **Ad blocking** | Block ad and tracker requests without leaving the browser. |
| **Built-in media player** | Open video streams in a native player with TV-friendly transport controls. |

## Install

Grab the latest APK from the [releases page](https://github.com/P403n1x87/tv-bro/releases) and
sideload it onto your device. This fork publishes self-updating builds, so installed copies can
update themselves from later releases.

## Development

Requirements: JDK 21 (with JDK 17 available for the toolchain), the Android SDK, and a device or
emulator for the on-device flows.

Build a debug APK:

```
./gradlew :app:assembleP403n1x87GeckoIncludedDebug -PenableAbiSplits
```

Launch a build straight at a URL with `adb` (handy for testing). The package id depends on the
`appstore` flavor — `p403n1x87` → `com.phlox.tvwebbrowser.p403n1x87`, `generic`/`google` →
`com.phlox.tvwebbrowser`:

```
adb shell am start -a android.intent.action.VIEW -d "http://example.com" -p com.phlox.tvwebbrowser.p403n1x87
```

Every pull request is built with the same pipeline as a release, so a broken build or a bad version
cannot reach a tag. Releases are cut by pushing a `vX.Y.Z` tag — the tag becomes the app version and
drives the GitHub release.

See [AGENTS.md](AGENTS.md) for the full build, test and debug instructions, and
[docs/architecture/ARCHITECTURE-REVIEW.md](docs/architecture/ARCHITECTURE-REVIEW.md) for how the
codebase is put together.

## License and credits

Released under the BSD 3-Clause license — see [LICENSE.md](LICENSE.md).

Odysseus is a fork of [TV Bro](https://github.com/truefedex/tv-bro) by Fedir Tsapana. The branding
and name are this fork's own; the underlying browser is built on that original work, and the About
screen in the app points back to it.
