package com.phlox.tvwebbrowser.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [LinkActionPolicy], the rule behind the cursor's link menu.
 */
class LinkActionPolicyTest {
    @Test
    fun theLinkIsPreferredOverTheElementSource() {
        assertEquals(
            "https://link.example/",
            LinkActionPolicy.targetUrl("https://link.example/", "https://image.example/pic.png"),
        )
    }

    @Test
    fun theElementSourceIsTheFallback() {
        assertEquals(
            "https://image.example/pic.png",
            LinkActionPolicy.targetUrl(null, "https://image.example/pic.png"),
        )
    }

    @Test
    fun nothingToActOn() {
        assertNull(LinkActionPolicy.targetUrl(null, null))
    }

    @Test
    fun surroundingQuotesAreStripped() {
        assertEquals("https://link.example/", LinkActionPolicy.targetUrl("\"https://link.example/\"", null))
        assertEquals("https://image.example/", LinkActionPolicy.targetUrl(null, "\"https://image.example/\""))
    }

    @Test
    fun aLoneQuoteIsLeftAlone() {
        assertEquals("\"", LinkActionPolicy.targetUrl("\"", null))
    }

    @Test
    fun httpUrlsAreActionable() {
        assertTrue(LinkActionPolicy.isHttpUrl("https://example.com/"))
        assertTrue(LinkActionPolicy.isHttpUrl("http://example.com/"))
    }

    @Test
    fun otherSchemesAndNothingAreNot() {
        assertFalse(LinkActionPolicy.isHttpUrl(null))
        assertFalse(LinkActionPolicy.isHttpUrl(""))
        assertFalse(LinkActionPolicy.isHttpUrl("blob:https://example.com/1234"))
        assertFalse(LinkActionPolicy.isHttpUrl("data:image/png;base64,AAAA"))
        assertFalse(LinkActionPolicy.isHttpUrl("about:blank"))
        assertFalse(LinkActionPolicy.isHttpUrl("HTTPS://example.com/"))
    }
}
