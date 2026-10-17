package com.phlox.tvwebbrowser.domain

import com.phlox.tvwebbrowser.Config
import org.junit.Assert.assertEquals
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

    @Test
    fun theHomePageIsUsedForTheBuiltInMode() {
        assertTrue(HomePagePolicy.usesBuiltInHomePage(Config.HomePageMode.HOME_PAGE))
        assertFalse(HomePagePolicy.usesBuiltInHomePage(Config.HomePageMode.SEARCH_ENGINE))
        assertFalse(HomePagePolicy.usesBuiltInHomePage(Config.HomePageMode.CUSTOM))
        assertFalse(HomePagePolicy.usesBuiltInHomePage(Config.HomePageMode.BLANK))
    }

    @Test
    fun homePageUrlForComputesTheStoredUrlForTheMode() {
        val custom = "https://example.com/"
        val engine = "https://www.google.com/search?q=[query]"
        assertEquals(Config.HOME_URL_ALIAS, HomePagePolicy.homePageUrlFor(Config.HomePageMode.HOME_PAGE, custom, engine))
        assertEquals(Config.HOME_URL_ALIAS, HomePagePolicy.homePageUrlFor(Config.HomePageMode.BLANK, custom, engine))
        val origin = HomePagePolicy.searchEngineHomePage(engine)
        assertEquals(origin, HomePagePolicy.homePageUrlFor(Config.HomePageMode.SEARCH_ENGINE, custom, engine))
        assertEquals(custom, HomePagePolicy.homePageUrlFor(Config.HomePageMode.CUSTOM, custom, engine))
        assertEquals(Config.HOME_URL_ALIAS, HomePagePolicy.homePageUrlFor(Config.HomePageMode.CUSTOM, null, engine))
    }

    @Test
    fun searchEngineHomePageExtractsTheOriginOrFallsBackToTheAlias() {
        assertEquals("https://www.google.com", HomePagePolicy.searchEngineHomePage("https://www.google.com/search?q=test"))
        assertEquals("https://duckduckgo.com", HomePagePolicy.searchEngineHomePage("https://duckduckgo.com/?q=a#hash"))
        assertEquals("http://example.com", HomePagePolicy.searchEngineHomePage("http://example.com/path?a=b"))
        assertEquals(Config.HOME_URL_ALIAS, HomePagePolicy.searchEngineHomePage("about:blank"))
        assertEquals(Config.HOME_URL_ALIAS, HomePagePolicy.searchEngineHomePage("ftp://example.com"))
    }

    @Test
    fun homeAliasTargetIsBlankOrLoadsTheConfiguredOrBuiltInUrl() {
        assertEquals(HomePagePolicy.HomeAliasTarget.Blank, HomePagePolicy.homeAliasTarget(Config.HomePageMode.BLANK, "x"))
        assertEquals(HomePagePolicy.HomeAliasTarget.Load(Config.HOME_PAGE_URL), HomePagePolicy.homeAliasTarget(Config.HomePageMode.HOME_PAGE, "x"))
        val configured = "https://example.com/"
        assertEquals(HomePagePolicy.HomeAliasTarget.Load(configured), HomePagePolicy.homeAliasTarget(Config.HomePageMode.CUSTOM, configured))
        assertEquals(HomePagePolicy.HomeAliasTarget.Load(configured), HomePagePolicy.homeAliasTarget(Config.HomePageMode.SEARCH_ENGINE, configured))
    }
}
