package com.phlox.tvwebbrowser.player

import java.net.URI
import java.net.URLDecoder
import java.util.Locale

/**
 * Canonical identity for a media URL, so repeated observations of the same resource collapse
 * into one entry. Signed/tokenised gateways name the same stream every time while their
 * credentials rotate (e.g. ".../secure.php?path=ab12/index.m3u8&e=...&sig=..."), so parameters
 * are only dropped when at least one of them names a resource (contains a path separator or a
 * manifest extension).
 *
 * When no parameter names a resource but the path itself names the media (e.g.
 * ".../live/allzroed5s.m3u8?_=..."), the short-lived credential/cache-buster parameters are
 * dropped while anything else is kept, so the same stream still collapses across token
 * rotations without merging genuinely distinct variants.
 */
object MediaUrlIdentity {
    private val MEDIA_EXTENSIONS =
        listOf(".m3u8", ".mpd", ".mp4", ".m4v", ".m4s", ".ts", ".webm", ".mkv", ".mov", ".flv")

    // Parameters that carry short-lived credentials, signatures or cache-busters rather than
    // selecting a resource. Only dropped when the path itself already names the media.
    private val EPHEMERAL_PARAMS =
        setOf(
            "_",
            "_t",
            "t",
            "ts",
            "n",
            "r",
            "k",
            "s",
            "e",
            "token",
            "tok",
            "sign",
            "sig",
            "signature",
            "hash",
            "nonce",
            "nocache",
            "cache",
            "rand",
            "expire",
            "expires",
            "exp",
            "ttl",
            "key",
            "time",
        )

    fun key(url: String): String {
        val uri = runCatching { URI(url) }.getOrNull() ?: return url.substringBefore('#')
        val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
        val path = uri.path.orEmpty()
        val base = "$host$path"
        val query = uri.rawQuery ?: return base
        val params =
            query.split('&').mapNotNull { part ->
                val eq = part.indexOf('=')
                if (eq <= 0) return@mapNotNull null
                part.substring(0, eq) to part.substring(eq + 1)
            }
        val resourceParams =
            params
                .filter { namesResource(it.second) }
                .map { "${it.first}=${uriDecode(it.second).lowercase(Locale.ROOT)}" }
                .sorted()
        if (resourceParams.isNotEmpty()) return "$base?${resourceParams.joinToString("&")}"
        if (!pathNamesMedia(uri.path.orEmpty())) return "$base?${uri.rawQuery}"
        val kept =
            params
                .filterNot { it.first.lowercase(Locale.ROOT) in EPHEMERAL_PARAMS }
                .map { "${it.first}=${it.second}" }
                .sorted()
        return if (kept.isEmpty()) base else "$base?${kept.joinToString("&")}"
    }

    private fun pathNamesMedia(path: String): Boolean {
        val lower = path.lowercase(Locale.ROOT)
        return MEDIA_EXTENSIONS.any { lower.endsWith(it) }
    }

    private fun namesResource(value: String): Boolean {
        val v = uriDecode(value).lowercase(Locale.ROOT)
        return v.contains('/') || v.contains(".m3u8") || v.contains(".mpd")
    }

    private fun uriDecode(value: String): String = runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
}
