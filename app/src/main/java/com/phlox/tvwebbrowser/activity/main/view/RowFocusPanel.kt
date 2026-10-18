package com.phlox.tvwebbrowser.activity.main.view

import android.view.View
import android.view.ViewParent
import android.view.ViewTreeObserver

/** A side panel that can hand the focus from its (focusable) container down to one of its rows. */
interface RowFocusPanel {
    /**
     * Asks the panel to move the focus to the row a remote user should land on. The panel's content
     * has to be settled by then (for the bookmarks that means the list has been read from the
     * database), so that only the layout pass that creates the rows is still outstanding.
     */
    fun requestRowFocus()
}

/**
 * Moves the focus from a side panel's container to the row the user should actually land on.
 *
 * The panels are focusable as a whole so that a D-pad press can never escape straight back to the
 * top bar, but what the remote has to be on is a row inside them. A row of a [android.widget.ListView]-like
 * list is an ordinary child view though, so it only exists once the panel - just made VISIBLE - has
 * been laid out, and there is no synchronous way to focus a view that does not exist yet. The
 * standoff therefore only lasts the one layout pass that creates the rows: [requestRowFocus] tries
 * at once and, if the row is not there yet, tries exactly once more from an
 * [ViewTreeObserver.OnPreDrawListener] - the callback that runs after measure/layout, immediately
 * before the frame is drawn. After that it gives up and the container keeps the focus, which is the
 * intended fallback for a panel with nothing to focus.
 *
 * The hand-off is bounded like that (rather than retrying on every layout until it works) so the
 * callers have to settle the content first; a panel that will never get a row (an empty bookmarks
 * list) must not be able to keep a listener - and itself - spinning. Do not call it before the data
 * is in: the one retry would then run before there is anything to focus.
 */
class PanelRowFocusDelegate(
    private val panel: View,
    private val rowProvider: () -> View?,
) {
    private var pending = false
    private var waitingForLayout = false

    private val preDrawListener =
        ViewTreeObserver.OnPreDrawListener {
            waitingForLayout = false
            tryFocusRow(retry = false)
            true
        }

    fun requestRowFocus() {
        pending = true
        tryFocusRow(retry = true)
    }

    /** Stops the hand-off, e.g. because the focus has left the panel. */
    fun cancel() {
        pending = false
        stopWaitingForLayout()
    }

    private fun tryFocusRow(retry: Boolean) {
        if (!pending) return
        if (rowProvider()?.requestFocus() == true) {
            cancel()
            return
        }
        // Nothing focusable (yet). Give up if the focus already moved outside the panel - the user
        // navigated away and the hand-off must not fight that. Otherwise wait for the layout pass
        // that is about to create the rows and try once more from there.
        val focused = panel.rootView.findFocus()
        if (focused != null && !isInsidePanel(focused)) {
            cancel()
            return
        }
        if (retry) startWaitingForLayout()
    }

    private fun startWaitingForLayout() {
        if (waitingForLayout) return
        waitingForLayout = true
        panel.viewTreeObserver.addOnPreDrawListener(preDrawListener)
    }

    private fun stopWaitingForLayout() {
        if (!waitingForLayout) return
        waitingForLayout = false
        runCatching { panel.viewTreeObserver.removeOnPreDrawListener(preDrawListener) }
    }

    private fun isInsidePanel(view: View): Boolean {
        if (view === panel) return true
        var ancestor: ViewParent? = view.parent
        while (ancestor != null) {
            if (ancestor === panel) return true
            ancestor = ancestor.parent
        }
        return false
    }
}
