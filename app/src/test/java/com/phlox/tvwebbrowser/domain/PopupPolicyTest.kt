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
                    PopupPolicy.shouldBlockNewWindow(dialog, gesture, HostConfig.POPUP_BLOCK_NONE)
                )
            }
        }
    }

    @Test
    fun dialogsLevelBlocksDialogsOnly() {
        assertTrue(
            PopupPolicy.shouldBlockNewWindow(
                dialog = true, userGesture = true,
                popupBlockLevel = HostConfig.POPUP_BLOCK_DIALOGS
            )
        )
        assertFalse(
            PopupPolicy.shouldBlockNewWindow(
                dialog = false, userGesture = false,
                popupBlockLevel = HostConfig.POPUP_BLOCK_DIALOGS
            )
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
                        dialog, gesture,
                        HostConfig.POPUP_BLOCK_NEW_TABS_EVEN_BY_USER_GESTURE
                    )
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
                    PopupPolicy.shouldBlockNewWindow(dialog, gesture, null)
                )
            }
        }
        assertEquals(HostConfig.POPUP_BLOCK_NEW_AUTO_OPENED_TABS, level)
    }
}
