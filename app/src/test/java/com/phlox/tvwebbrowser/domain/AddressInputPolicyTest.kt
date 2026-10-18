package com.phlox.tvwebbrowser.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [AddressInputPolicy], the rule behind `MainActivity.search`.
 *
 * The platform's `Patterns.WEB_URL` match is faked with a small predicate so the tests are about
 * the policy and not about the SDK's web-url regex.
 */
class AddressInputPolicyTest {
    private val searchUrl = "https://duckduckgo.com/?q=[query]"

    /** Stand-in for the platform web-url pattern: `host.tld` plus anything after it. */
    private val looksLikeWebUrl: (String) -> Boolean = { it.matches(Regex("[\\w-]+(\\.[\\w-]+)+.*")) }

    private fun resolve(
        input: String,
        looksLike: (String) -> Boolean = looksLikeWebUrl,
    ) = AddressInputPolicy.resolveUrl(input, searchUrl, looksLike)

    @Test
    fun schemelessHostGetsHttps() {
        assertEquals("https://example.com", resolve("example.com"))
        assertEquals("https://example.com/page?q=1", resolve("example.com/page?q=1"))
    }

    @Test
    fun explicitSchemeIsKept() {
        assertEquals("http://example.com", resolve("http://example.com"))
        assertEquals("https://example.com", resolve("https://example.com"))
        // Scheme casing is not rewritten, and neither is the rest of the input.
        assertEquals("HTTP://Example.com/Path", resolve("HTTP://Example.com/Path"))
    }

    @Test
    fun httpSchemeIsHonouredEvenWhenTheWebUrlPatternSaysNo() {
        // The pattern needs a dot; the starts-with check still saves the input, verbatim (the
        // leading/trailing whitespace included) because it already carries a scheme.
        assertEquals(" http://localhost:8000", resolve(" http://localhost:8000", looksLike = { false }))
        assertEquals(" HTTPS://intranet", resolve(" HTTPS://intranet", looksLike = { false }))
    }

    @Test
    fun whitespaceOnlyInputIsSearchedFor() {
        assertEquals("https://duckduckgo.com/?q=+++", resolve("   "))
    }

    @Test
    fun plainTextBecomesAQuery() {
        assertEquals("https://duckduckgo.com/?q=hello+world", resolve("hello world"))
        assertEquals("https://duckduckgo.com/?q=tv+bro", resolve("tv bro"))
    }

    @Test
    fun queryIsUrlEncoded() {
        assertEquals("https://duckduckgo.com/?q=a%26b%3Dc", resolve("a&b=c"))
        assertEquals("https://duckduckgo.com/?q=caf%C3%A9", resolve("café"))
    }

    @Test
    fun patternInputIsAskedAboutVerbatim() {
        var asked: String? = null
        assertEquals(
            "https://asked.example",
            resolve("asked.example") {
                asked = it
                true
            },
        )
        assertEquals("asked.example", asked)
    }
}
