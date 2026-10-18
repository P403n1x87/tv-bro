package com.phlox.tvwebbrowser.domain

/**
 * Pure decision logic for the actions the cursor menu suggests for a link or a media element.
 *
 * Extracted from `MainActivity.WebEngineCallback.suggestActionsForLink`, which keeps the Android
 * plumbing (the popup menu, the anchor view, the action dispatch) and asks this object what to show.
 */
object LinkActionPolicy {
    /**
     * The url the menu acts on: the link when there is one, the element source (image, video)
     * otherwise. Some engines hand the url over wrapped in quotes, which are stripped — a lone
     * quote is left alone rather than cutting the string past its end.
     */
    fun targetUrl(
        linkUri: String?,
        srcUri: String?,
    ): String? {
        val url = linkUri ?: srcUri ?: return null
        return if (url.length >= 2 && url.startsWith("\"") && url.endsWith("\"")) {
            url.substring(1, url.length - 1)
        } else {
            url
        }
    }

    /**
     * Whether [url] is a web address the "open in a new tab", "open in an external app" and
     * "download" entries can act on. Case-sensitive, like the engine urls it is fed.
     */
    fun isHttpUrl(url: String?): Boolean = url != null && (url.startsWith("http://") || url.startsWith("https://"))
}
