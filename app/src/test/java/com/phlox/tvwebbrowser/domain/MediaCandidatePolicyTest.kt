package com.phlox.tvwebbrowser.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [MediaCandidatePolicy]: the disguised-manifest rules and the CDN JSON wrapper
 * that must resolve to its sibling playlist instead of being listed as a stream.
 */
class MediaCandidatePolicyTest {
    private fun candidate(url: String) = MediaCandidatePolicy.candidateFor(url)

    private val jsonIndex =
        "https://edgestream3.pro/sh/access/TOKEN/cdn/hls/griogoehvn2.json"

    @Test
    fun opaqueJsonUnderHlsResolvesToSiblingPlaylist() {
        assertEquals(
            MediaCandidatePolicy.Candidate(
                "https://edgestream3.pro/sh/access/TOKEN/cdn/hls/griogoehvn2.m3u8",
                MediaCandidatePolicy.Kind.HLS,
            ),
            candidate(jsonIndex),
        )
    }

    @Test
    fun siblingPlaylistKeepsQueryAndFragment() {
        assertEquals(
            "https://cdn.example/hls/abc.m3u8?token=abc&x=1#frag",
            candidate("https://cdn.example/hls/abc.json?token=abc&x=1#frag")?.url,
        )
    }

    @Test
    fun jsonWhoseNameNamesAManifestStaysJson() {
        assertEquals(
            MediaCandidatePolicy.Candidate(
                "https://cdn.example/hls/master.json",
                MediaCandidatePolicy.Kind.HLS,
            ),
            candidate("https://cdn.example/hls/master.json"),
        )
    }

    @Test
    fun jsonOutsideManifestPathsIsNotACandidate() {
        assertNull(candidate("https://cdn.example/api/status/griogoehvn2.json"))
    }

    @Test
    fun disguisedTextManifestIsStillDetected() {
        assertEquals(
            MediaCandidatePolicy.Candidate(
                "https://cdn.example/hls/index.txt",
                MediaCandidatePolicy.Kind.HLS,
            ),
            candidate("https://cdn.example/hls/index.txt"),
        )
    }

    @Test
    fun gatewayQueryNamesManifest() {
        assertEquals(
            MediaCandidatePolicy.Candidate(
                "https://cdn.example/stream.php?path=ab12%2Findex.m3u8&sig=1",
                MediaCandidatePolicy.Kind.HLS,
            ),
            candidate("https://cdn.example/stream.php?path=ab12%2Findex.m3u8&sig=1"),
        )
    }

    @Test
    fun progressiveFilesAreDetected() {
        assertEquals(
            MediaCandidatePolicy.Candidate(
                "https://cdn.example/video.mp4",
                MediaCandidatePolicy.Kind.PROGRESSIVE,
            ),
            candidate("https://cdn.example/video.mp4"),
        )
    }
}
