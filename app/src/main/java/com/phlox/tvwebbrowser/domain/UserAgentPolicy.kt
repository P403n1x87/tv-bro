package com.phlox.tvwebbrowser.domain

/**
 * Pure User-Agent rewrites, kept out of the engine so they can be tested on the JVM.
 *
 * The Android WebView default UA carries the `; wv` in-app marker. Many site players and
 * anti-bot gates reject embedded WebView clients (HTTP 403) as soon as they see it, while an
 * otherwise-identical UA without the marker is accepted. Stripping it lets the engine present
 * as an ordinary browser; every other token (device, Chrome version, `Version/4.0`) is kept.
 */
object UserAgentPolicy {
    private const val IN_APP_MARKER = "; wv"

    fun withoutInAppMarker(userAgent: String): String = userAgent.replace(IN_APP_MARKER, "")
}
