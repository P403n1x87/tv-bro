package com.phlox.tvwebbrowser.domain

import com.phlox.tvwebbrowser.model.HostConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [PopupPolicy], the per-host popup/new-window blocking rules used by
 * `MainActivity.WebEngineCallback.shouldBlockNewWindow`.
 */
class PopupPolicyTest {
    @Test
    fun noneLevelNeverBlocks() {
        for (dialog in listOf(false, true)) {
            for (gesture in listOf(false, true)) {
                assertFalse(
                    PopupPolicy.shouldBlockNewWindow(dialog, gesture, HostConfig.POPUP_BLOCK_NONE),
                )
            }
        }
    }

    @Test
    fun dialogsLevelBlocksDialogsOnly() {
        assertTrue(
            PopupPolicy.shouldBlockNewWindow(
                dialog = true,
                userGesture = true,
                popupBlockLevel = HostConfig.POPUP_BLOCK_DIALOGS,
            ),
        )
        assertFalse(
            PopupPolicy.shouldBlockNewWindow(
                dialog = false,
                userGesture = false,
                popupBlockLevel = HostConfig.POPUP_BLOCK_DIALOGS,
            ),
        )
    }

    @Test
    fun defaultLevelBlocksDialogsAndGesturelessTabs() {
        val level = HostConfig.POPUP_BLOCK_NEW_AUTO_OPENED_TABS
        // dialog: always blocked
        assertTrue(PopupPolicy.shouldBlockNewWindow(dialog = true, userGesture = true, level))
        assertTrue(PopupPolicy.shouldBlockNewWindow(dialog = true, userGesture = false, level))
        // plain new tab: only blocked without a user gesture
        assertFalse(PopupPolicy.shouldBlockNewWindow(dialog = false, userGesture = true, level))
        assertTrue(PopupPolicy.shouldBlockNewWindow(dialog = false, userGesture = false, level))
    }

    @Test
    fun strictestLevelBlocksEverything() {
        for (dialog in listOf(false, true)) {
            for (gesture in listOf(false, true)) {
                assertTrue(
                    PopupPolicy.shouldBlockNewWindow(
                        dialog,
                        gesture,
                        HostConfig.POPUP_BLOCK_NEW_TABS_EVEN_BY_USER_GESTURE,
                    ),
                )
            }
        }
    }

    @Test
    fun missingLevelFallsBackToDefault() {
        // null (no host config / unset level) must behave exactly like the default level
        val level = HostConfig.DEFAULT_BLOCK_POPUPS_VALUE
        for (dialog in listOf(false, true)) {
            for (gesture in listOf(false, true)) {
                assertEquals(
                    PopupPolicy.shouldBlockNewWindow(dialog, gesture, level),
                    PopupPolicy.shouldBlockNewWindow(dialog, gesture, null),
                )
            }
        }
        assertEquals(HostConfig.POPUP_BLOCK_NEW_AUTO_OPENED_TABS, level)
    }

    // --- cross-site popup guard -----------------------------------------------------------

    @Test
    fun crossSiteGuardDisabledNeverBlocks() {
        // Same site and cross-site alike: when the setting is off nothing is blocked.
        assertFalse(
            PopupPolicy.shouldBlockCrossSitePopup("https://example.com/page", "https://ads.network/x", enabled = false),
        )
        assertFalse(
            PopupPolicy.shouldBlockCrossSitePopup("https://example.com/page", "https://example.com/other", enabled = false),
        )
    }

    @Test
    fun crossSiteGuardBlocksCrossSitePopup() {
        assertTrue(
            PopupPolicy.shouldBlockCrossSitePopup("https://example.com/page", "https://ads.network/x", enabled = true),
        )
        assertTrue(
            PopupPolicy.shouldBlockCrossSitePopup("https://example.com/page", "http://other.org/y", enabled = true),
        )
        // Subdomains collapse to the same registrable domain -> not cross-site.
        assertFalse(
            PopupPolicy.shouldBlockCrossSitePopup("https://example.com/page", "https://www.example.com/other", enabled = true),
        )
    }

    @Test
    fun crossSiteGuardAllowsSameSite() {
        assertFalse(
            PopupPolicy.shouldBlockCrossSitePopup("https://example.com/page", "https://example.com/other", enabled = true),
        )
        assertFalse(
            PopupPolicy.shouldBlockCrossSitePopup("https://shop.example.co.uk/x", "https://www.example.co.uk/y", enabled = true),
        )
    }

    @Test
    fun crossSiteGuardIgnoresUnknownHosts() {
        // about:blank / data: / custom schemes have no registrable domain -> never cross-site.
        assertFalse(
            PopupPolicy.shouldBlockCrossSitePopup("about:blank", "https://ads.network/x", enabled = true),
        )
        assertFalse(
            PopupPolicy.shouldBlockCrossSitePopup("https://example.com/page", "data:text/html,<b>x</b>", enabled = true),
        )
        assertFalse(PopupPolicy.shouldBlockCrossSitePopup(null, "https://ads.network/x", enabled = true))
        assertFalse(PopupPolicy.shouldBlockCrossSitePopup("https://example.com/page", null, enabled = true))
    }
}
