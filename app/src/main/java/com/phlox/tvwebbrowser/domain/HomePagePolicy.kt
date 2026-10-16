package com.phlox.tvwebbrowser.domain

import com.phlox.tvwebbrowser.Config

/**
 * Pure decision logic for the built-in home page, lifted out of [Config].
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
}
