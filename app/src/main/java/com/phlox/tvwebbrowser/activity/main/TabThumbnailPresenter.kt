package com.phlox.tvwebbrowser.activity.main

import android.os.Handler
import android.view.View
import com.phlox.tvwebbrowser.databinding.ActivityMainBinding
import com.phlox.tvwebbrowser.model.WebTabState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the tab-thumbnail preview in the top-right corner: the debounced repaint the tabs panel
 * triggers while titles change, and the actual bitmap/placeholder rendering.
 *
 * The activity supplies the "keep the toolbar open" and "is this still the current tab" predicates,
 * so the presenter never reaches back into the composition root.
 */
class TabThumbnailPresenter(
    private val vb: ActivityMainBinding,
    private val uiHandler: Handler,
    private val scope: CoroutineScope,
    private val keepToolbarOpen: () -> Boolean,
    private val isCurrentTab: (WebTabState) -> Boolean,
) {
    companion object {
        private const val REPAINT_DELAY_MS = 200L
    }

    private val runnable = object : Runnable {
        var tabState: WebTabState? = null
        override fun run() {
            tabState?.let {
                scope.launch(Dispatchers.Main) {
                    display(it)
                }
            }
        }
    }

    /** Schedules a (debounced) repaint of [tab]'s thumbnail. */
    fun schedule(tab: WebTabState?) {
        uiHandler.removeCallbacks(runnable)
        runnable.tabState = tab
        uiHandler.postDelayed(runnable, REPAINT_DELAY_MS)
    }

    suspend fun display(currentTab: WebTabState?) {
        if (keepToolbarOpen()) return
        if (currentTab != null) {
            if (!isCurrentTab(currentTab)) return
            vb.llMiniaturePlaceholder.visibility = View.INVISIBLE
            vb.ivMiniatures.visibility = View.VISIBLE
            if (currentTab.thumbnail != null) {
                vb.ivMiniatures.setImageBitmap(currentTab.thumbnail)
            } else if (currentTab.thumbnailHash != null) {
                withContext(Dispatchers.IO) {
                    val thumbnail = currentTab.loadThumbnail()
                    withContext(Dispatchers.Main) {
                        if (thumbnail != null) {
                            vb.ivMiniatures.setImageBitmap(currentTab.thumbnail)
                        } else {
                            vb.ivMiniatures.setImageResource(0)
                        }
                    }
                }
            } else {
                vb.ivMiniatures.setImageResource(0)
            }
        } else {
            vb.llMiniaturePlaceholder.visibility = View.VISIBLE
            vb.ivMiniatures.setImageResource(0)
            vb.ivMiniatures.visibility = View.INVISIBLE
        }
    }
}
