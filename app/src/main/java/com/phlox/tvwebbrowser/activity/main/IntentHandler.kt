package com.phlox.tvwebbrowser.activity.main

import android.app.Activity
import android.content.Intent
import android.util.Log
import com.phlox.tvwebbrowser.activity.history.HistoryActivity
import com.phlox.tvwebbrowser.model.WebTabState

/**
 * Handles the intents the main screen receives: the deep links that opened (or re-opened) it, with
 * the "open in the same tab" extra, and the result of the History activity. The view-side effects
 * (open a tab, navigate, switch tab, restore the overlay) are passed in as callbacks so the handler
 * does not need to know about the activity's private helpers.
 */
class IntentHandler(
    private val tabsModel: TabsModel,
    private val openInNewTab: (url: String?, index: Int, navigateImmediately: Boolean) -> Unit,
    private val navigate: (String) -> Unit,
    private val changeTab: (WebTabState) -> Unit,
    private val autoHideMenuOverlay: () -> Unit,
) {
    companion object {
        private val TAG = IntentHandler::class.java.simpleName
        const val REQUEST_CODE_HISTORY_ACTIVITY = 10006
    }

    fun handleIntent(intent: Intent) {
        Log.d(TAG, "handleIntent: " + intent.data)
        if (intent.getBooleanExtra("com.phlox.tvwebbrowser.EXTRA_OPEN_IN_SAME_TAB", false) &&
            tabsModel.tabsStates.isNotEmpty()
        ) {
            if (tabsModel.currentTab.value == null) {
                changeTab(tabsModel.tabsStates[0])
            }
            navigate(intent.data.toString())
            return
        }

        openInNewTab(
            intent.data.toString(), tabsModel.tabsStates.size, true
        )
    }

    /** Handles the History activity result. Called only for [REQUEST_CODE_HISTORY_ACTIVITY]. */
    fun handleHistoryResult(resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            val url = data?.getStringExtra(HistoryActivity.KEY_URL)
            if (url != null) {
                navigate(url)
            }
            autoHideMenuOverlay()
        }
    }
}
