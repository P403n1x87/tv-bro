package com.phlox.tvwebbrowser.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [BackNavigationPolicy], the priority list behind the main screen's BACK handling.
 * Each state is pinned in isolation and against the state "above" it, so the policy cannot silently
 * reorder the dismissals, and a focused page always routes to the top bar.
 */
class BackNavigationPolicyTest {
    private fun decide(
        cursorModeDisabled: Boolean = false,
        cursorMenuVisible: Boolean = false,
        cursorDrawerHandlesBack: Boolean = false,
        fullscreen: Boolean = false,
        bookmarksPanelVisible: Boolean = false,
        tabsPanelVisible: Boolean = false,
        pageFocused: Boolean = true,
    ) = BackNavigationPolicy.decide(
        cursorModeDisabled = cursorModeDisabled,
        cursorMenuVisible = cursorMenuVisible,
        cursorDrawerHandlesBack = cursorDrawerHandlesBack,
        fullscreen = fullscreen,
        bookmarksPanelVisible = bookmarksPanelVisible,
        tabsPanelVisible = tabsPanelVisible,
        pageFocused = pageFocused,
    )

    @Test
    fun aFocusedPageAlwaysHandsBackToTheTopBar() {
        // The regression: this used to depend on the "hide toolbar when browsing" setting, so with
        // the toolbar pinned BACK only toggled the overlay and the remote stayed in the WebView.
        assertEquals(BackNavigationPolicy.Action.FOCUS_TOP_BAR, decide(pageFocused = true))
    }

    @Test
    fun anUnfocusedPageWithoutPanelsTogglesTheToolbar() {
        assertEquals(BackNavigationPolicy.Action.TOGGLE_TOOLBAR, decide(pageFocused = false))
    }

    @Test
    fun directNavigationModeIsLeftFirst() {
        assertEquals(
            BackNavigationPolicy.Action.ENABLE_CURSOR,
            decide(cursorModeDisabled = true, cursorMenuVisible = true, fullscreen = true),
        )
    }

    @Test
    fun theCursorMenuIsClosedBeforeThePageOrFullscreen() {
        assertEquals(
            BackNavigationPolicy.Action.CLOSE_CURSOR_MENU,
            decide(cursorMenuVisible = true, fullscreen = true, pageFocused = true),
        )
    }

    @Test
    fun theCursorDrawerIsAskedBeforeFullscreenAndPanels() {
        assertEquals(
            BackNavigationPolicy.Action.HANDLE_CURSOR_DRAWER,
            decide(cursorDrawerHandlesBack = true, fullscreen = true, tabsPanelVisible = true),
        )
    }

    @Test
    fun fullscreenIsLeftBeforeThePanelsAndThePage() {
        assertEquals(
            BackNavigationPolicy.Action.EXIT_FULLSCREEN,
            decide(fullscreen = true, bookmarksPanelVisible = true, pageFocused = true),
        )
    }

    @Test
    fun theBookmarksPanelIsClosedBeforeTheTabsPanel() {
        assertEquals(
            BackNavigationPolicy.Action.HIDE_BOOKMARKS,
            decide(bookmarksPanelVisible = true, tabsPanelVisible = true, pageFocused = true),
        )
    }

    @Test
    fun theTabsPanelIsClosedBeforeThePageHandoff() {
        assertEquals(
            BackNavigationPolicy.Action.HIDE_TABS,
            decide(tabsPanelVisible = true, pageFocused = true),
        )
    }
}
