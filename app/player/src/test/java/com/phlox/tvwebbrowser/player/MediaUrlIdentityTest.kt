package com.phlox.tvwebbrowser.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MediaUrlIdentityTest {
    @Test fun collapsesRotatedSignaturesAndExpiry() {
        val a = "https://hls.example/secure.php?path=ab12%2Findex.m3u8&e=1&sig=AAA"
        val b = "https://hls.example/secure.php?path=ab12%2Findex.m3u8&e=2&sig=BBB&v=9-1"
        val c = "https://hls.example/secure.php?path=ab12%2Findex.m3u8&e=3&sig=CCC"
        assertEquals(MediaUrlIdentity.key(a), MediaUrlIdentity.key(b))
        assertEquals(MediaUrlIdentity.key(b), MediaUrlIdentity.key(c))
    }

    @Test fun keepsDistinctResourcesApart() {
        val a = "https://hls.example/secure.php?path=ab12%2Findex.m3u8&sig=AAA"
        val b = "https://hls.example/secure.php?path=cd34%2Findex.m3u8&sig=AAA"
        assertNotEquals(MediaUrlIdentity.key(a), MediaUrlIdentity.key(b))
    }

    @Test fun plainManifestWithoutQuery() {
        assertEquals("cdn.example/v/master.m3u8", MediaUrlIdentity.key("https://cdn.example/v/master.m3u8"))
    }

    @Test fun dropsCacheBusterOnNamedManifest() {
        val a = "https://cdn1.obstreamx.click/live/allzroed5s.m3u8?_=1792260359926"
        val b = "https://cdn1.obstreamx.click/live/allzroed5s.m3u8?_=1792260473000"
        assertEquals(MediaUrlIdentity.key(a), MediaUrlIdentity.key(b))
        assertEquals("cdn1.obstreamx.click/live/allzroed5s.m3u8", MediaUrlIdentity.key(a))
    }

    @Test fun dropsRotatingCredentialsOnNamedManifest() {
        val a = "https://cdn.example/v/master.m3u8?token=abc&t=1"
        val b = "https://cdn.example/v/master.m3u8?token=def&t=2"
        assertEquals(MediaUrlIdentity.key(a), MediaUrlIdentity.key(b))
    }

    @Test fun keepsMeaningfulQueryOnNamedManifest() {
        val low = "https://cdn.example/v/master.m3u8?quality=low"
        val high = "https://cdn.example/v/master.m3u8?quality=high"
        assertNotEquals(MediaUrlIdentity.key(low), MediaUrlIdentity.key(high))
        assertEquals(MediaUrlIdentity.key(low), MediaUrlIdentity.key("https://cdn.example/v/master.m3u8?quality=low"))
    }

    @Test fun keepsDistinctNamedManifestsApart() {
        assertNotEquals(
            MediaUrlIdentity.key("https://cdn.example/live/a.m3u8?_=1"),
            MediaUrlIdentity.key("https://cdn.example/live/b.m3u8?_=1"),
        )
    }

    @Test fun queryWithoutResourceParamKeptVerbatim() {
        val a = "https://cdn.example/v/stream?token=abc"
        val b = "https://cdn.example/v/stream?token=def"
        assertNotEquals(MediaUrlIdentity.key(a), MediaUrlIdentity.key(b))
        assertEquals(MediaUrlIdentity.key(a), MediaUrlIdentity.key("https://cdn.example/v/stream?token=abc"))
    }

    @Test fun keepsProgressiveQueryVariantsApart() {
        val low = "https://cdn.example/v.mp4?quality=low"
        val high = "https://cdn.example/v.mp4?quality=high"
        assertNotEquals(MediaUrlIdentity.key(low), MediaUrlIdentity.key(high))
    }
}
