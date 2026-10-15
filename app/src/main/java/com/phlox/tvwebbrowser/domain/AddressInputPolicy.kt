package com.phlox.tvwebbrowser.domain

import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.util.Locale

/**
 * Pure decision logic for a submitted address-box string: is it a url to load, or a query for
 * the search engine?
 *
 * Extracted from `MainActivity.search` so the rule — which carries real behaviour history, e.g.
 * the schemeless-host `https://` upgrade — is unit-testable. The platform's `Patterns.WEB_URL`
 * match is passed in as [looksLikeWebUrl] so this object stays free of Android types.
 */
object AddressInputPolicy {

    /**
     * The url to load for [input]:
     *
     * - the input itself when it looks like an address — either [looksLikeWebUrl] accepts it (the
     *   platform's web-url pattern in production) or, ignoring case and surrounding whitespace,
     *   it already starts with `http://`/`https://`. An input without a scheme anywhere gets
     *   `https://` in front;
     * - otherwise the search-engine url with its `[query]` placeholder replaced by the
     *   url-encoded input.
     *
     * Returns null only when the input cannot be url-encoded (unreachable for "utf-8" on a real
     * JVM), so the caller can report an error instead of navigating.
     */
    fun resolveUrl(
        input: String,
        searchEngineUrlTemplate: String,
        looksLikeWebUrl: (String) -> Boolean
    ): String? {
        val trimmedLowercased = input.trim { it <= ' ' }.lowercase(Locale.ROOT)
        val isAddress = looksLikeWebUrl(input) ||
                trimmedLowercased.startsWith("http://") ||
                trimmedLowercased.startsWith("https://")
        if (isAddress) {
            return if (!input.lowercase(Locale.ROOT).contains("://")) {
                "https://$input"
            } else {
                input
            }
        }
        val query = try {
            URLEncoder.encode(input, "utf-8")
        } catch (e: UnsupportedEncodingException) {
            e.printStackTrace()
            return null
        }
        return searchEngineUrlTemplate.replace("[query]", query)
    }
}
