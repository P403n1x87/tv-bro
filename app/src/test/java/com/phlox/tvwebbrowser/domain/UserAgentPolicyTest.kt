package com.phlox.tvwebbrowser.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Unit tests for [UserAgentPolicy], the User-Agent rewrite that drops the WebView in-app marker.
 */
class UserAgentPolicyTest {
    @Test
    fun theInAppMarkerIsRemovedWhileEveryOtherTokenSurvives() {
        val webViewUa =
            "Mozilla/5.0 (Linux; Android 10; BRAVIA 4K UR3 Build/QT; wv) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/155.0.8059.43 Mobile Safari/537.36"
        val rewritten = UserAgentPolicy.withoutInAppMarker(webViewUa)
        assertFalse(rewritten.contains("; wv"))
        assertEquals(
            "Mozilla/5.0 (Linux; Android 10; BRAVIA 4K UR3 Build/QT) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/155.0.8059.43 Mobile Safari/537.36",
            rewritten,
        )
    }

    @Test
    fun aUaWithoutTheMarkerIsLeftUntouched() {
        val plain = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36"
        assertEquals(plain, UserAgentPolicy.withoutInAppMarker(plain))
    }
}
