package com.phlox.tvwebbrowser.domain

import com.phlox.tvwebbrowser.Config
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [WebEnginePolicy], the engine-identity check used by the views.
 */
class WebEnginePolicyTest {
    @Test
    fun theGeckoConstantMapsToGecko() {
        assertTrue(WebEnginePolicy.isGecko(Config.ENGINE_GECKO_VIEW))
    }

    @Test
    fun webViewAndAnythingElseAreNotGecko() {
        assertFalse(WebEnginePolicy.isGecko(Config.ENGINE_WEB_VIEW))
        assertFalse(WebEnginePolicy.isGecko(""))
        assertFalse(WebEnginePolicy.isGecko("geckoview"))
    }
}
