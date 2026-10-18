package com.phlox.tvwebbrowser.domain

/**
 * Pure decision logic for navigations requested by the web engine.
 *
 * Extracted from `MainActivity.WebEngineCallback` so the rules that carry real behaviour history —
 * the cross-site automatic-redirect blocker and the scheme routing that decides what the engine
 * loads itself versus what is handed to Android — live in one framework-free place and can be
 * unit-tested with plain JUnit. The callback keeps the Android plumbing (`Uri` parsing, intent
 * resolution, logging, user prompts) and asks this object what to do.
 */
object NavigationPolicy {
    /**
     * Non-http schemes the engine loads itself: handing them to an external app would be
     * meaningless, since `javascript:`/`data:`/`about:`/`blob:` are document-local URLs.
     */
    private val ENGINE_ONLY_SCHEMES = setOf("javascript", "data", "about", "blob")

    /** True when the scheme names an intent URI, which Android — not the engine — must handle. */
    fun isIntentScheme(scheme: String?): Boolean = scheme.equals("intent", ignoreCase = true)

    /**
     * Whether the engine should load the url itself instead of handing it off or blocking it.
     * A null/blank scheme is never loadable; the caller blocks it before asking.
     *
     * @param isNetworkUrl whether the url is a plain network url (`URLUtil.isNetworkUrl`), i.e.
     *   an http(s) address the engine knows how to fetch.
     */
    fun shouldLoadInEngine(
        scheme: String?,
        isNetworkUrl: Boolean,
    ): Boolean {
        if (scheme.isNullOrEmpty()) return false
        return isNetworkUrl || scheme.lowercase() in ENGINE_ONLY_SCHEMES
    }

    /**
     * Whether [requestedUrl] is a cross-site *automatic* redirect (a navigation with no user
     * gesture that jumps to a different registrable domain) that should be blocked while ad
     * blocking is on.
     *
     * The current page url is [currentUrl] when it is usable, falling back to
     * [lastLoadingUrl] — during a redirect chain the tab url may still be blank. The url-to-host
     * resolution is passed in as [hostOf] (`Uri.parse(url).host` in production) so this object
     * stays free of Android types.
     */
    fun shouldBlockAutomaticRedirect(
        requestedUrl: String,
        currentUrl: String?,
        lastLoadingUrl: String?,
        blockAutomaticRedirects: Boolean,
        adBlockEnabled: Boolean,
        hostOf: (String) -> String?,
    ): Boolean {
        if (!blockAutomaticRedirects || !adBlockEnabled) return false
        val current = currentUrl?.takeIf { it.isNotBlank() } ?: lastLoadingUrl ?: return false
        if (requestedUrl == current) return false
        return SiteDomains.isDifferentSite(hostOf(requestedUrl), hostOf(current))
    }
}
