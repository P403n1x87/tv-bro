package com.phlox.tvwebbrowser.domain

/**
 * Pure rule for turning free text a user typed into an absolute url.
 *
 * Used by the bookmark editor, which has to store something a browser can load: a host typed
 * without a scheme gets `https://`. This is deliberately *stricter* than
 * [AddressInputPolicy.resolveUrl] — there a schemeless string may still be a search query, so it is
 * only upgraded when it never contains a scheme at all.
 */
object UrlNormalizer {
    private val ABSOLUTE_URL = Regex("^[A-Za-z]+://.*$")

    /** [input] with an `https://` scheme in front, unless it already starts with one. */
    fun withAbsoluteScheme(input: String): String = if (ABSOLUTE_URL.matches(input)) input else "https://$input"
}
