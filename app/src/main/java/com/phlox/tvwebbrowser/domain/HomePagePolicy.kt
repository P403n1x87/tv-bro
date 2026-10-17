package com.phlox.tvwebbrowser.domain

import com.phlox.tvwebbrowser.Config

/**
 * Pure decision logic for the home page, lifted out of [Config] and its callers: whether a URL is
 * the built-in home page, which URL the settings store for a mode, what loading the home-page
 * alias does, and when the built-in (rather than a user-chosen) home page is the active one.
 *
 * The loopback URL is not a constant — the local asset server sets `Config.localHomePageUrl` — so
 * callers pass it in; `null` means the server is not running and only the remote URL and the
 * `about:home` alias count. `Config.currentHomePageUrl()` stays in `Config` because the Gecko module
 * (which cannot see this one) also needs it.
 */
object HomePagePolicy {
    /** True if [url] points at the built-in home page (remote, alias or loopback). */
    fun isHomePageUrl(url: String?, localHomePageUrl: String?): Boolean {
        if (url == null) return false
        if (url == Config.HOME_PAGE_URL || url == Config.HOME_URL_ALIAS) return true
        val local = localHomePageUrl ?: return false
        return url.startsWith(local)
    }

    /** True when the built-in (remote) home page — not a user-chosen one — is the active mode. */
    fun usesBuiltInHomePage(mode: Config.HomePageMode): Boolean =
        mode == Config.HomePageMode.HOME_PAGE

    /** What loading the `about:home` alias does under [mode]. */
    sealed interface HomeAliasTarget {
        /** Load nothing — a blank page. */
        object Blank : HomeAliasTarget

        /** Load [url]: the built-in home page, or the configured custom/search-engine URL. */
        data class Load(val url: String) : HomeAliasTarget
    }

    /**
     * The target of loading `about:home`: blank for [Config.HomePageMode.BLANK], the built-in page
     * for [Config.HomePageMode.HOME_PAGE], and the stored home page for the two modes that point at
     * a URL the settings wrote ([configuredHomePage] — custom URL or search engine origin).
     */
    fun homeAliasTarget(
        mode: Config.HomePageMode,
        configuredHomePage: String
    ): HomeAliasTarget = when (mode) {
        Config.HomePageMode.BLANK -> HomeAliasTarget.Blank
        Config.HomePageMode.HOME_PAGE -> HomeAliasTarget.Load(Config.HOME_PAGE_URL)
        Config.HomePageMode.CUSTOM, Config.HomePageMode.SEARCH_ENGINE ->
            HomeAliasTarget.Load(configuredHomePage)
    }

    /**
     * The URL the settings store as the home page for [mode]: the search engine's origin for
     * [Config.HomePageMode.SEARCH_ENGINE], the alias for the built-in and blank modes (both load
     * from the alias), and the custom URL — falling back to the alias — for
     * [Config.HomePageMode.CUSTOM].
     */
    fun homePageUrlFor(
        mode: Config.HomePageMode,
        customHomePageUrl: String?,
        searchEngineUrl: String
    ): String = when (mode) {
        Config.HomePageMode.SEARCH_ENGINE -> searchEngineHomePage(searchEngineUrl)
        Config.HomePageMode.HOME_PAGE, Config.HomePageMode.BLANK -> Config.HOME_URL_ALIAS
        Config.HomePageMode.CUSTOM -> customHomePageUrl ?: Config.HOME_URL_ALIAS
    }

    /**
     * The home page a search engine URL implies: its scheme+host origin (so the stored home page
     * follows the engine without its query path), or the alias when the URL has no http(s) origin.
     */
    fun searchEngineHomePage(searchEngineUrl: String): String =
        """^https?://[^#?/]+""".toRegex().find(searchEngineUrl)?.value ?: Config.HOME_URL_ALIAS
}
