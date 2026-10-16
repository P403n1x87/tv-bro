package com.phlox.tvwebbrowser.domain

import com.phlox.tvwebbrowser.Config
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [HomePagePolicy], the built-in-home-page check used across the views.
 */
class HomePagePolicyTest {

    private val loopback = "http://127.0.0.1:12345/appcontent/home/"

    @Test
    fun theRemoteUrlAndAliasAreHome() {
        assertTrue(HomePagePolicy.isHomePageUrl(Config.HOME_PAGE_URL, null))
        assertTrue(HomePagePolicy.isHomePageUrl(Config.HOME_URL_ALIAS, null))
    }

    @Test
    fun theLoopbackUrlIsHomeOnlyWhenTheServerIsRunning() {
        assertTrue(HomePagePolicy.isHomePageUrl(loopback, loopback))
        assertTrue(HomePagePolicy.isHomePageUrl("$loopback#section", loopback))
        assertFalse(HomePagePolicy.isHomePageUrl(loopback, null))
    }

    @Test
    fun unrelatedUrlsAreNotHome() {
        assertFalse(HomePagePolicy.isHomePageUrl("https://example.com/", loopback))
        assertFalse(HomePagePolicy.isHomePageUrl("https://tvbro.phlox.dev/other/", null))
        assertFalse(HomePagePolicy.isHomePageUrl(null, loopback))
        assertFalse(HomePagePolicy.isHomePageUrl("", loopback))
    }
}
