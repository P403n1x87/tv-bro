package com.phlox.tvwebbrowser.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [UrlNormalizer], the rule the bookmark editor applies to the url field.
 */
class UrlNormalizerTest {
    @Test
    fun schemelessInputGetsHttps() {
        assertEquals("https://example.com", UrlNormalizer.withAbsoluteScheme("example.com"))
        assertEquals("https://example.com/page?q=1", UrlNormalizer.withAbsoluteScheme("example.com/page?q=1"))
    }

    @Test
    fun absoluteInputIsLeftAlone() {
        assertEquals("https://example.com", UrlNormalizer.withAbsoluteScheme("https://example.com"))
        assertEquals("http://example.com", UrlNormalizer.withAbsoluteScheme("http://example.com"))
        assertEquals("ftp://example.com/file", UrlNormalizer.withAbsoluteScheme("ftp://example.com/file"))
        // Scheme casing does not matter.
        assertEquals("HTTPS://Example.com", UrlNormalizer.withAbsoluteScheme("HTTPS://Example.com"))
    }

    @Test
    fun aSchemeThatIsNotAtTheStartDoesNotCount() {
        // The editor only accepts an absolute url, so junk in front is prefixed rather than kept.
        assertEquals("https://a b://c", UrlNormalizer.withAbsoluteScheme("a b://c"))
        assertEquals("https:///local/path", UrlNormalizer.withAbsoluteScheme("/local/path"))
    }
}
