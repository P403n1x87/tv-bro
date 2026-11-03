package com.phlox.tvwebbrowser.domain

import java.net.URI
import java.util.Locale

/**
 * Pure URL classification for the WebView engine's stream picker.
 *
 * WebView only exposes request URLs, never response bodies. A CDN can therefore serve a JSON
 * segment index next to the playlist under the same name (`.../hls/abc.json` ->
 * `.../hls/abc.m3u8`): the JSON looks like a disguised manifest but is not playable, while the
 * sibling playlist is. This policy prefers that sibling when the JSON file name does not itself
 * name a manifest, and keeps the disguised-manifest rules for the cases that look deliberate.
 */
object MediaCandidatePolicy {
    enum class Kind { HLS, DASH, PROGRESSIVE }

    data class Candidate(
        val url: String,
        val kind: Kind,
    )

    private val MANIFEST_PATH_HINTS =
        listOf("/hls/", "master", "playlist", "manifest", "sublist", "chunklist")
    private val MANIFEST_NAME_HINTS =
        listOf("master", "playlist", "manifest", "sublist", "chunklist")

    /** The stream to list for [url], or null when it is not a media candidate. */
    fun candidateFor(url: String): Candidate? {
        if (url.isBlank()) return null
        val path = url.substringBefore('?').lowercase(Locale.ROOT)
        jsonPlaylistSibling(url, path)?.let { return Candidate(it, Kind.HLS) }
        if (!isCandidate(url, path)) return null
        return Candidate(url, kindFor(url, path))
    }

    private fun isCandidate(
        url: String,
        path: String,
    ): Boolean =
        path.endsWith(".m3u8") || path.endsWith(".mpd") ||
            isProgressiveFile(path) || isMaskedManifest(path) || isUrlManifest(path) ||
            url.contains("action=redirect") || path.contains("/api/er/get") ||
            queryNamesManifest(url)

    /**
     * Some players fetch the manifest from an extensionless gateway with the real file name in
     * the query, e.g. ".../stream.php?path=ab12/index.m3u8&sig=...".
     */
    private fun queryNamesManifest(url: String): Boolean {
        if (!url.contains('?')) return false
        val query = decode(url.substringAfter('?'))
        return query.contains(".m3u8") || query.contains(".mpd")
    }

    /**
     * Some sites disguise the HLS manifest behind a harmless extension. A `.txt` is treated like
     * a manifest whenever the path looks HLS-ish, but a `.json` only when its own file name names
     * a manifest: otherwise it is usually an API response (see [jsonPlaylistSibling]).
     */
    private fun isMaskedManifest(path: String): Boolean =
        when {
            path.endsWith(".txt") -> MANIFEST_PATH_HINTS.any(path::contains)
            path.endsWith(".json") -> MANIFEST_NAME_HINTS.any(manifestFileName(path)::contains)
            else -> false
        }

    /**
     * A CDN index like `.../hls/<id>.json` is metadata for the page's player; the playable
     * playlist is the same path with a `.m3u8` extension. Only derive it when the JSON name does
     * not name a manifest itself, so a genuinely disguised `master.json` keeps being listed.
     */
    private fun jsonPlaylistSibling(
        url: String,
        path: String,
    ): String? {
        if (!path.endsWith(".json") || !MANIFEST_PATH_HINTS.any(path::contains)) return null
        if (MANIFEST_NAME_HINTS.any(manifestFileName(path)::contains)) return null
        return siblingPlaylist(url)
    }

    /** Replaces the path's `.json` extension while preserving the query and fragment verbatim. */
    private fun siblingPlaylist(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val rawPath = uri.rawPath ?: return null
        if (rawPath.isEmpty() || !rawPath.lowercase(Locale.ROOT).endsWith(".json")) return null
        val pathStart = url.indexOf(rawPath)
        if (pathStart < 0) return null
        return url.substring(0, pathStart) +
            rawPath.dropLast(".json".length) + ".m3u8" +
            url.substring(pathStart + rawPath.length)
    }

    /** Some player services serve the manifest with no extension at all, behind .../q/<n>. */
    private fun isUrlManifest(path: String): Boolean {
        val qIdx = path.lastIndexOf("/q/")
        return qIdx >= 0 && path.substring(qIdx + 3).toIntOrNull() != null
    }

    private fun isProgressiveFile(path: String): Boolean =
        path.endsWith(".mp4") || path.endsWith(".m4v") || path.endsWith(".webm") ||
            path.endsWith(".mkv") || path.endsWith(".mov")

    private fun kindFor(
        url: String,
        path: String,
    ): Kind =
        when {
            path.endsWith(".mpd") -> Kind.DASH
            path.endsWith(".m3u8") || isMaskedManifest(path) || isUrlManifest(path) -> Kind.HLS
            queryNamesManifest(url) -> if (decode(url.substringAfter('?')).contains(".mpd")) Kind.DASH else Kind.HLS
            else -> Kind.PROGRESSIVE
        }

    private fun manifestFileName(path: String): String = path.substringAfterLast('/')

    private fun decode(value: String): String =
        runCatching { java.net.URLDecoder.decode(value, "UTF-8") }.getOrDefault(value).lowercase(Locale.ROOT)
}
