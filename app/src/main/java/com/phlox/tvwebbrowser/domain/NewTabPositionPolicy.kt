package com.phlox.tvwebbrowser.domain

/**
 * Where a tab that a page asks for goes in the tab list.
 *
 * Extracted from `MainActivity.WebEngineCallback` (`onOpenInNewTabRequested`, `onCreateWindow`),
 * which keeps the Android plumbing (building the engine view, switching to the tab) and asks this
 * object for the position. The paths that already know where the tab belongs (the tab panel's "+",
 * the overflow menu, an external intent) pass an explicit index instead.
 */
object NewTabPositionPolicy {
    /**
     * The insertion index for a tab requested by the tab at [requestingTabIndex]: right after it, so
     * a link's "open in a new tab" lands next to the page it came from. With no selection
     * ([requestingTabIndex] == -1) the tab is appended.
     */
    fun insertIndexAfter(
        requestingTabIndex: Int,
        tabCount: Int,
    ): Int = if (requestingTabIndex == -1) tabCount else requestingTabIndex + 1
}
