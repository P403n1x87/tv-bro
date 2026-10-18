package com.phlox.tvwebbrowser.activity.main

import android.app.Activity
import android.app.AlertDialog
import android.widget.Toast
import com.phlox.tvwebbrowser.R
import com.phlox.tvwebbrowser.activity.main.dialogs.media.MediaListAdapter

/**
 * Presents the "streams on this page" list for the current tab and kicks off the background
 * metadata prefetch. Kept out of the activity so the media UI has one owner.
 */
class MediaListPresenter(
    private val activity: Activity,
    private val tabsModel: TabsModel,
) {
    companion object {
        private const val MAX_MEDIA_LIST_ITEMS = 20
    }

    fun showMediaListDialog() {
        val tab = tabsModel.currentTab.value
        val candidates = tab?.webEngine?.mediaCandidates().orEmpty()
        if (candidates.isEmpty()) {
            Toast.makeText(activity, R.string.media_list_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val listed = candidates.take(MAX_MEDIA_LIST_ITEMS)
        val adapter = MediaListAdapter(activity, listed)
        val dialog =
            AlertDialog
                .Builder(activity)
                .setTitle(R.string.media_list_title)
                .setAdapter(adapter) { _, which ->
                    if (tab != null) tab.webEngine.playMediaCandidate(listed[which])
                }.setNegativeButton(android.R.string.cancel, null)
                .create()
        dialog.setOnDismissListener { adapter.release() }
        dialog.show()
        tab?.webEngine?.prefetchMediaMetadata()
    }
}
