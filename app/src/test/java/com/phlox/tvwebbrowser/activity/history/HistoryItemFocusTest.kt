package com.phlox.tvwebbrowser.activity.history

import android.app.Activity
import android.app.Application
import android.view.View
import com.phlox.tvwebbrowser.R
import com.phlox.tvwebbrowser.model.HistoryItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config as RobolectricConfig

/**
 * Screen tests for the history list's D-pad reachability over the real inflated layouts. The pure
 * routing rules live in [com.phlox.tvwebbrowser.domain.HistoryFocusPolicyTest]; this covers the half
 * that only the XML decides: that a visit row really exposes a focusable delete icon, that a date
 * header does not, and that the header's controls can be walked to and from the clear-browsing-data
 * label instead of being pointer-only.
 */
@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(sdk = [26], application = Application::class)
class HistoryItemFocusTest {
    private lateinit var activity: Activity

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    }

    private fun visitRow(): HistoryItemView {
        val row = HistoryItemView(activity, HistoryAdapter.VIEW_TYPE_HISTORY_ITEM)
        row.setHistoryItem(
            HistoryItem().apply {
                title = "Example"
                url = "http://example.com"
            },
            multiselectMode = false,
        )
        activity.setContentView(row)
        return row
    }

    @Test
    fun aVisitRowOffersAFocusableDeleteIcon() {
        val row = visitRow()

        assertTrue("a visit row should expose its delete icon to the D-pad", row.canFocusDelete())
        val icon = row.findViewById<View>(R.id.ibDelete)
        assertTrue(icon.isFocusable)
        assertTrue(row.requestDeleteFocus())
        assertTrue("focus should land on the delete icon", icon.hasFocus())
    }

    @Test
    fun aDateHeaderHasNoFocusableDeleteIcon() {
        val header = HistoryItemView(activity, HistoryAdapter.VIEW_TYPE_HEADER)
        header.setHistoryItem(HistoryItem.createDateHeaderInfo(0L), multiselectMode = false)
        activity.setContentView(header)

        assertFalse(header.canFocusDelete())
        assertFalse("the RIGHT key must not be swallowed on a date header", header.requestDeleteFocus())
    }

    @Test
    fun multiSelectHidesTheDeleteIconFromTheDpad() {
        // While multi-select is on the checkbox takes over, so the icon must not claim the RIGHT key.
        val row = HistoryItemView(activity, HistoryAdapter.VIEW_TYPE_HISTORY_ITEM)
        row.setHistoryItem(HistoryItem().apply { url = "http://example.com" }, multiselectMode = true)
        activity.setContentView(row)

        assertFalse(row.canFocusDelete())
    }

    @Test
    fun theHeaderControlsAreConnectedToTheList() {
        val binding =
            com.phlox.tvwebbrowser.databinding.ActivityHistoryBinding
                .inflate(activity.layoutInflater)
        activity.setContentView(binding.root)

        // From the list, UP reaches the clear-browsing-data label, whose own UP reaches the title
        // bar. Nothing here is left to the framework's guesswork.
        assertTrue(binding.btnClearBrowsingData.isFocusable)
        assertTrue(binding.btnClose.isFocusable)
        assertTrue(binding.btnDelete.isFocusable)
        assertTrue(binding.listView.nextFocusUpId == binding.btnClearBrowsingData.id)
        assertTrue(binding.btnClearBrowsingData.nextFocusUpId == binding.btnClose.id)
    }
}
