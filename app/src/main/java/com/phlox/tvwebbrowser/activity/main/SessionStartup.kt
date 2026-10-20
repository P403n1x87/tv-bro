package com.phlox.tvwebbrowser.activity.main

import android.content.Intent
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.phlox.tvwebbrowser.Config
import com.phlox.tvwebbrowser.databinding.ActivityMainBinding
import com.phlox.tvwebbrowser.model.WebTabState
import com.phlox.tvwebbrowser.webengine.WebEngineFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Brings a [MainActivity] session up: initialises the engine factory, loads the persisted state of
 * the view model and of the tabs, and then either restores the tab that was open, opens the deep
 * link that started the activity, or lands on the home page with the toolbar overlay showing.
 *
 * The activity is the host: everything that has to change the screen from here (open/switch a tab,
 * handle the launch intent, the post-load update check) is a callback, so this class needs to know
 * nothing about the composition root. The suspension points used to live at the bottom of
 * `onCreate`, which is exactly why it was hard to read.
 */
class SessionStartup(
    private val activity: AppCompatActivity,
    private val vb: ActivityMainBinding,
    private val config: Config,
    private val viewModel: MainActivityViewModel,
    private val settingsModel: SettingsModel,
    private val tabsModel: TabsModel,
    private val toolbarController: ToolbarController,
    private val handleIntent: (Intent) -> Unit,
    /** (url, index, needToHideMenuOverlay, navigateImmediately), the activity's own tab opener. */
    private val openInNewTab: (url: String?, index: Int, needToHideMenuOverlay: Boolean, navigateImmediately: Boolean) -> Unit,
    private val changeTab: (WebTabState) -> Unit,
    /**
     * Runs the host's own post-load work (the update badge and the startup update check) once the
     * session state is in place, so a check that wants to show a dialog runs after the tabs exist.
     */
    private val onLoaded: () -> Unit,
) {
    companion object {
        private val TAG = SessionStartup::class.java.simpleName
    }

    fun load(intent: Intent) =
        activity.lifecycleScope.launch(Dispatchers.Main) {
            Log.d(TAG, "load")
            WebEngineFactory.initialize(activity, config, vb.flWebViewContainer)

            vb.progressBarGeneric.visibility = View.VISIBLE
            vb.progressBarGeneric.requestFocus()
            viewModel.loadState().join()
            if (settingsModel.restoreTabsOnStartup) {
                tabsModel.loadState().join()
            } else {
                tabsModel.discardSavedState().join()
            }

            if (!isActive) {
                return@launch
            }

            vb.progressBarGeneric.visibility = View.GONE

            if (intent.data == null) {
                restoreSavedTabOrOpenHome()
            } else {
                handleIntent(intent)
            }

            val currentTab = tabsModel.currentTab.value
            if (currentTab == null || currentTab.url == settingsModel.homePage) {
                toolbarController.showMenuOverlay()
            }
            onLoaded()
        }

    /**
     * The launch without a deep link: switch to the tab that was selected when the app went away,
     * or open the home page when there is nothing to restore.
     */
    private fun restoreSavedTabOrOpenHome() {
        if (tabsModel.tabsStates.isEmpty()) {
            // (url, index, needToHideMenuOverlay, navigateImmediately)
            openInNewTab(settingsModel.homePage, 0, true, true)
            return
        }
        for (i in tabsModel.tabsStates.indices) {
            val tab = tabsModel.tabsStates[i]
            if (tab.selected) {
                changeTab(tab)
                return
            }
        }
        // this may happen in some error states
        changeTab(tabsModel.tabsStates[0])
    }
}
