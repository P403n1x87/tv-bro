package com.phlox.tvwebbrowser.domain

/**
 * Decides what the main screen should do with a BACK press.
 *
 * The order is the priority: modal surfaces are dismissed first, then the side panels, and only
 * then does the key act on the page. When the page has the focus, BACK always hands the remote
 * back to the top bar - whether that bar is pinned or hidden while browsing - so a remote-only
 * user can never be stranded in the WebView.
 */
object BackNavigationPolicy {
    enum class Action {
        /** Leave direct-navigation mode and bring the virtual cursor back. */
        ENABLE_CURSOR,

        /** Close the long-press cursor menu. */
        CLOSE_CURSOR_MENU,

        /** Let the cursor drawer consume BACK (e.g. leave grab mode). */
        HANDLE_CURSOR_DRAWER,

        /** Leave the page's fullscreen video. */
        EXIT_FULLSCREEN,

        /** Close the bookmarks panel. */
        HIDE_BOOKMARKS,

        /** Close the tabs panel. */
        HIDE_TABS,

        /** Show the top bar if needed and move the focus into it. */
        FOCUS_TOP_BAR,

        /** Toggle the toolbar menu. */
        TOGGLE_TOOLBAR,
    }

    fun decide(
        cursorModeDisabled: Boolean,
        cursorMenuVisible: Boolean,
        cursorDrawerHandlesBack: Boolean,
        fullscreen: Boolean,
        bookmarksPanelVisible: Boolean,
        tabsPanelVisible: Boolean,
        pageFocused: Boolean,
    ): Action =
        when {
            cursorModeDisabled -> Action.ENABLE_CURSOR
            cursorMenuVisible -> Action.CLOSE_CURSOR_MENU
            cursorDrawerHandlesBack -> Action.HANDLE_CURSOR_DRAWER
            fullscreen -> Action.EXIT_FULLSCREEN
            bookmarksPanelVisible -> Action.HIDE_BOOKMARKS
            tabsPanelVisible -> Action.HIDE_TABS
            pageFocused -> Action.FOCUS_TOP_BAR
            else -> Action.TOGGLE_TOOLBAR
        }
}
