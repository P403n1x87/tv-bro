package com.phlox.tvwebbrowser.activity.main.view

import android.app.Activity
import android.app.Application
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config as RobolectricConfig

/**
 * Integration tests for [PanelRowFocusDelegate]: the panels are focusable as a whole so that a
 * D-pad press can never escape straight back to the top bar, but what the remote has to be on is a
 * row inside them. The rows only exist once the panel - just made VISIBLE - has been laid out, so
 * the hand-off has to survive a row that is not there yet, and it must never fight a user who has
 * already moved the focus elsewhere.
 */
@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(sdk = [26], application = Application::class)
class PanelRowFocusDelegateTest {

    private lateinit var activity: Activity
    private lateinit var root: FrameLayout
    private lateinit var panel: FrameLayout
    private var row: View? = null
    private lateinit var delegate: PanelRowFocusDelegate

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        root = FrameLayout(activity)
        panel = FrameLayout(activity).apply {
            isFocusable = true
            isFocusableInTouchMode = true
        }
        root.addView(panel, FrameLayout.LayoutParams(200, 400))
        activity.setContentView(root)
        shadowOf(Looper.getMainLooper()).idle()
        delegate = PanelRowFocusDelegate(panel) { row }
    }

    private fun addRow(): View =
        View(activity).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            layoutParams = FrameLayout.LayoutParams(200, 50)
            panel.addView(this)
        }

    /** Runs the layout pass that creates the rows and fires the pre-draw listeners. */
    private fun layoutPass() {
        panel.requestLayout()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun handsTheFocusOverToTheRowWhenItIsAlreadyThere() {
        panel.requestFocusFromTouch()
        assertTrue(panel.hasFocus())
        row = addRow()
        layoutPass()

        delegate.requestRowFocus()

        assertTrue(row!!.hasFocus())
    }

    @Test
    fun waitsForTheLayoutThatCreatesTheRow() {
        panel.requestFocusFromTouch()
        assertTrue(panel.hasFocus())

        delegate.requestRowFocus()
        assertTrue("the panel keeps the focus while the row does not exist", panel.hasFocus())

        row = addRow()
        layoutPass()

        assertTrue(row!!.hasFocus())
    }

    @Test
    fun givesUpWhenTheFocusHasLeftThePanel() {
        val other = View(activity).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            layoutParams = FrameLayout.LayoutParams(200, 200)
        }
        root.addView(other, 0)
        layoutPass()
        other.requestFocus()
        assertTrue(other.hasFocus())

        delegate.requestRowFocus()

        // A row shows up later, but the user has already moved the focus away: the hand-off must
        // not steal it back.
        row = addRow()
        layoutPass()

        assertFalse(row!!.hasFocus())
        assertTrue(other.hasFocus())
    }

    @Test
    fun cancelStopsThePendingHandOff() {
        panel.requestFocusFromTouch()
        delegate.requestRowFocus()

        delegate.cancel()
        row = addRow()
        layoutPass()

        assertFalse(row!!.hasFocus())
        assertTrue(panel.hasFocus())
    }
}
