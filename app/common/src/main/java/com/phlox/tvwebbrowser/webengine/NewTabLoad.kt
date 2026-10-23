package com.phlox.tvwebbrowser.webengine

/**
 * What the new tab does with the url that asked for it.
 *
 * [Immediate] is a user action (a link in the cursor menu, the WebView `target=_blank` path): the
 * host creates the tab and loads the url itself. [Deferred] is the engine's popup path
 * (`GeckoSession.NavigationDelegate.onNewSession`): the engine loads the url into the session it
 * gets back, so the host must only create the tab.
 */
enum class NewTabLoad {
    Immediate,
    Deferred,
}
