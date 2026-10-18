package com.phlox.tvwebbrowser.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SiteDomains], the registrable-domain logic behind the automatic-redirect blocker.
 */
class SiteDomainsTest {
    @Test
    fun blankHostIsNull() {
        assertNull(SiteDomains.siteDomain(null))
        assertNull(SiteDomains.siteDomain(""))
        assertNull(SiteDomains.siteDomain("   "))
    }

    @Test
    fun wwwPrefixIsStripped() {
        assertEquals("example.com", SiteDomains.siteDomain("www.example.com"))
        assertEquals("example.com", SiteDomains.siteDomain("example.com"))
    }

    @Test
    fun hostIsLowerCased() {
        assertEquals("example.com", SiteDomains.siteDomain("WWW.Example.COM"))
    }

    @Test
    fun twoLabelHostKeepsBoth() {
        assertEquals("example.com", SiteDomains.siteDomain("example.com"))
        assertEquals("cdn.io", SiteDomains.siteDomain("cdn.io"))
    }

    @Test
    fun threeLabelHostCollapsesToSite() {
        assertEquals("example.com", SiteDomains.siteDomain("sub.example.com"))
        assertEquals("example.com", SiteDomains.siteDomain("a.b.example.com"))
    }

    @Test
    fun secondLevelSuffixNeedsThreeLabels() {
        // co.uk is a second-level suffix, so bbc.co.uk is the site, not co.uk
        assertEquals("bbc.co.uk", SiteDomains.siteDomain("www.bbc.co.uk"))
        assertEquals("example.com.au", SiteDomains.siteDomain("news.example.com.au"))
    }

    @Test
    fun differentSitesOnDifferentDomains() {
        assertTrue(SiteDomains.isDifferentSite("ads.example.com", "tracker.other.net"))
    }

    @Test
    fun sameSiteEvenOnDifferentSubdomains() {
        // www and cdn are the same registrable domain: not a cross-site redirect
        assertFalse(SiteDomains.isDifferentSite("www.example.com", "cdn.example.com"))
        assertFalse(SiteDomains.isDifferentSite("m.example.com", "example.com"))
    }

    @Test
    fun hostIsExtractedFromAUrl() {
        assertEquals("example.com", SiteDomains.hostFromUrl("https://example.com/file.zip"))
        assertEquals("cdn.example.com", SiteDomains.hostFromUrl("http://cdn.example.com/a?b=c#d"))
        assertEquals("example.com", SiteDomains.hostFromUrl("https://user:pw@example.com:8080/x"))
    }

    @Test
    fun urlsWithoutAHostHaveNoDomain() {
        assertNull(SiteDomains.domainFromUrl(null))
        assertNull(SiteDomains.domainFromUrl(""))
        assertNull(SiteDomains.domainFromUrl("not a url"))
    }

    @Test
    fun urlDomainCollapsesToTheRegistrableDomain() {
        assertEquals("example.com", SiteDomains.domainFromUrl("https://www.example.com/file.zip"))
        assertEquals("bbc.co.uk", SiteDomains.domainFromUrl("https://www.bbc.co.uk/news"))
    }

    @Test
    fun unknownHostIsNeverDifferent() {
        // an unparseable host must not be flagged as a cross-site jump
        assertFalse(SiteDomains.isDifferentSite(null, "example.com"))
        assertFalse(SiteDomains.isDifferentSite("example.com", null))
        assertFalse(SiteDomains.isDifferentSite("", ""))
    }
}
