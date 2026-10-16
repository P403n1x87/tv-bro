package com.phlox.tvwebbrowser.activity.main

import android.view.View
import androidx.core.view.isVisible
import com.phlox.tvwebbrowser.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Owns the two side panels of the main screen: the tabs panel on the left and the bookmarks panel
 * on the right. [MainActivity] only forwards the toolbar/back-key intents that toggle them.
 *
 * The focus hand-off is the delicate part. A panel that has just been made VISIBLE is not laid out
 * yet, so its rows do not exist and a plain `requestFocus()` on the container is not enough: a view
 * that is not focusable in touch mode cannot take focus at all when the device is in touch mode (a
 * tap anywhere puts it there). `requestFocusFromTouch()` leaves touch mode first, so the container
 * takes the focus synchronously and a D-pad press can never escape back to the top bar. The focus
 * then moves down to a real row via `RowFocusPanel.requestRowFocus()` — but only once the panel's
 * content is settled, which for the bookmarks panel means the list has been read from the database.
 */
class PanelController(
    private val vb: ActivityMainBinding,
    /** Puts the focus back on the page, called when the tabs panel is dismissed. */
    private val focusEngine: () -> Unit,
) {
    val isTabsPanelVisible: Boolean get() = vb.llTabPanel.isVisible
    val isBookmarksPanelVisible: Boolean get() = vb.rlBookmarksPanel.isVisible

    /** Toggles the tabs panel; the tabs are already in the adapter, so one layout pass is enough. */
    fun toggleTabsPanel() {
        if (vb.llTabPanel.isVisible) {
            vb.llTabPanel.visibility = View.GONE
            focusEngine()
        } else {
            vb.rlBookmarksPanel.visibility = View.GONE
            vb.llTabPanel.visibility = View.VISIBLE
            focusPanel(vb.vTabs)
            vb.vTabs.requestRowFocus()
        }
    }

    fun showTabsPanel() {
        vb.rlBookmarksPanel.visibility = View.GONE
        vb.llTabPanel.visibility = View.VISIBLE
        focusPanel(vb.vTabs)
        vb.vTabs.requestRowFocus()
    }

    fun hideTabsPanel() {
        vb.llTabPanel.visibility = View.GONE
    }

    /**
     * Shows the bookmarks panel and claims its focus straight away. The row hand-off is deferred to
     * [onContentReady], which reads the list first: the rows, and the "Add this page" entry (hidden
     * again for the home page), are final by then, so the hand-off needs at most the one layout pass
     * that creates them.
     */
    fun showBookmarksPanel(scope: CoroutineScope, onContentReady: suspend () -> Unit) {
        vb.llTabPanel.visibility = View.GONE
        vb.rlBookmarksPanel.visibility = View.VISIBLE
        focusPanel(vb.vBookmarks)
        scope.launch(Dispatchers.Main) {
            onContentReady()
            vb.vBookmarks.requestRowFocus()
        }
    }

    fun hideBookmarksPanel() {
        vb.rlBookmarksPanel.visibility = View.GONE
    }

    /** Closes both panels, e.g. when the extended address bar takes over the screen. */
    fun hideAllPanels() {
        vb.llTabPanel.visibility = View.GONE
        vb.rlBookmarksPanel.visibility = View.GONE
    }

    private fun focusPanel(panel: View) {
        panel.requestFocusFromTouch()
    }
}
