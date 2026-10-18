package com.phlox.tvwebbrowser.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [NavigationPolicy]: scheme routing and the cross-site automatic-redirect blocker
 * that `MainActivity.WebEngineCallback.shouldOverrideUrlLoading` consults.
 */
class NavigationPolicyTest {
    /** Stand-in for `Uri.parse(url).host`: host between "://" and the next "/" or ":". */
    private fun hostOf(url: String): String? =
        url
            .substringAfter("://", "")
            .substringBefore('/')
            .substringBefore(':')
            .ifEmpty { null }

    private fun shouldBlock(
        requestedUrl: String,
        currentUrl: String? = "https://www.example.com/page",
        lastLoadingUrl: String? = null,
        blockAutomaticRedirects: Boolean = true,
        adBlockEnabled: Boolean = true,
    ) = NavigationPolicy.shouldBlockAutomaticRedirect(
        requestedUrl,
        currentUrl,
        lastLoadingUrl,
        blockAutomaticRedirects,
        adBlockEnabled,
    ) { u -> hostOf(u) }

    // -- shouldLoadInEngine / isIntentScheme -----------------------------------------------

    @Test
    fun networkUrlsLoadInEngine() {
        assertTrue(NavigationPolicy.shouldLoadInEngine("http", isNetworkUrl = true))
        assertTrue(NavigationPolicy.shouldLoadInEngine("https", isNetworkUrl = true))
    }

    @Test
    fun documentLocalSchemesLoadInEngine() {
        assertTrue(NavigationPolicy.shouldLoadInEngine("javascript", isNetworkUrl = false))
        assertTrue(NavigationPolicy.shouldLoadInEngine("data", isNetworkUrl = false))
        assertTrue(NavigationPolicy.shouldLoadInEngine("about", isNetworkUrl = false))
        assertTrue(NavigationPolicy.shouldLoadInEngine("blob", isNetworkUrl = false))
        // scheme matching is case-insensitive, as the inline checks were
        assertTrue(NavigationPolicy.shouldLoadInEngine("BLOB", isNetworkUrl = false))
    }

    @Test
    fun unknownSchemesDoNotLoadInEngine() {
        assertFalse(NavigationPolicy.shouldLoadInEngine("ftp", isNetworkUrl = false))
        assertFalse(NavigationPolicy.shouldLoadInEngine("intent", isNetworkUrl = false))
        assertFalse(NavigationPolicy.shouldLoadInEngine(null, isNetworkUrl = true))
        assertFalse(NavigationPolicy.shouldLoadInEngine("", isNetworkUrl = false))
    }

    @Test
    fun intentSchemesAreDetectedCaseInsensitively() {
        assertTrue(NavigationPolicy.isIntentScheme("intent"))
        assertTrue(NavigationPolicy.isIntentScheme("INTENT"))
        assertFalse(NavigationPolicy.isIntentScheme("http"))
        assertFalse(NavigationPolicy.isIntentScheme(null))
    }

    // -- shouldBlockAutomaticRedirect -------------------------------------------------------

    @Test
    fun crossSiteAutomaticRedirectIsBlocked() {
        assertTrue(shouldBlock("https://tracker.other.net/landing"))
    }

    @Test
    fun sameSiteRedirectIsAllowed() {
        // different subdomain, same registrable domain: not a cross-site jump
        assertFalse(shouldBlock("https://cdn.example.com/next"))
    }

    @Test
    fun identicalUrlIsNeverBlocked() {
        assertFalse(shouldBlock("https://www.example.com/page"))
    }

    @Test
    fun blockingRequiresTheRedirectSetting() {
        assertFalse(
            shouldBlock(
                "https://tracker.other.net/landing",
                blockAutomaticRedirects = false,
            ),
        )
    }

    @Test
    fun blockingRequiresAdBlock() {
        assertFalse(shouldBlock("https://tracker.other.net/landing", adBlockEnabled = false))
    }

    @Test
    fun blankTabUrlFallsBackToLastLoadingUrl() {
        // during a redirect chain the tab url may still be blank: the last loading url is current
        assertTrue(
            shouldBlock(
                requestedUrl = "https://tracker.other.net/landing",
                currentUrl = "",
                lastLoadingUrl = "https://www.example.com/page",
            ),
        )
        // ... and without any known current url there is nothing to compare against
        assertFalse(
            shouldBlock(
                requestedUrl = "https://tracker.other.net/landing",
                currentUrl = "",
                lastLoadingUrl = null,
            ),
        )
    }

    @Test
    fun unknownHostsAreNotBlocked() {
        // no resolvable host on either side means no established cross-site jump
        assertFalse(shouldBlock("relative-path/landing", currentUrl = "also/no-host"))
        assertFalse(shouldBlock("https://tracker.other.net/landing", currentUrl = ""))
    }
}
