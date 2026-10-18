package com.phlox.tvwebbrowser.activity.main

import android.util.Log
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.phlox.tvwebbrowser.Config
import com.phlox.tvwebbrowser.R
import com.phlox.tvwebbrowser.databinding.ActivityMainBinding
import com.phlox.tvwebbrowser.domain.HomePagePolicy
import com.phlox.tvwebbrowser.domain.WebEnginePolicy
import com.phlox.tvwebbrowser.model.WebTabState
import com.phlox.tvwebbrowser.utils.Utils
import com.phlox.tvwebbrowser.webengine.WebEngineFactory

/**
 * Renders model changes into the main screen: every `subscribe()` the composition root used to make
 * in `onCreate` lives here, so [MainActivity] only wires and never observes.
 *
 * The subscriptions keep the notification flags they had in `onCreate` (`false` for the ones whose
 * initial state the composition root sets by hand) and their relative order, because that order is
 * what the user sees at startup.
 *
 * The activity is the host for the three side effects the observers have to trigger: recreating
 * itself on a night-mode change, toasting an ad-blocker failure, and refreshing the page-level
 * chrome when the current tab changes.
 */
class SessionObservers(
    private val activity: AppCompatActivity,
    private val vb: ActivityMainBinding,
    private val config: Config,
    private val settingsModel: SettingsModel,
    private val adblockModel: AdblockModel,
    private val viewModel: MainActivityViewModel,
    private val tabsModel: TabsModel,
    private val toolbarController: ToolbarController,
    private val bookmarksController: BookmarksController,
    /** The current tab changed (it is never null here): repaint the address box, badge and bookmarks. */
    private val onCurrentTabChanged: (WebTabState) -> Unit,
) {
    companion object {
        private val TAG = SessionObservers::class.java.simpleName
    }

    fun observe() {
        settingsModel.userAgentString.subscribe(activity.lifecycle, false) {
            for (tab in tabsModel.tabsStates) {
                tab.webEngine.userAgentString = it
            }
        }

        settingsModel.theme.subscribe(activity.lifecycle, false) {
            val mode =
                when (it) {
                    Config.Theme.BLACK -> AppCompatDelegate.MODE_NIGHT_YES
                    Config.Theme.WHITE -> AppCompatDelegate.MODE_NIGHT_NO
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
            if (AppCompatDelegate.getDefaultNightMode() != mode) {
                AppCompatDelegate.setDefaultNightMode(mode)
            }
            WebEngineFactory.onThemeSettingUpdated(it)
            // MainActivity declares uiMode in android:configChanges, so AppCompat does not recreate
            // it on a night-mode change and the already-inflated views would keep their old colors.
            // Recreate explicitly so the top bar and dialogs repaint immediately without a restart.
            if (!activity.isFinishing && !activity.isDestroyed) activity.recreate()
        }

        settingsModel.keepScreenOn.subscribe(activity.lifecycle) {
            if (it) {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        // The toolbar overlay style follows the setting live, so the settings page never touches the
        // activity to restyle it.
        settingsModel.keepToolbarOpen.subscribe(activity.lifecycle) {
            toolbarController.applyOverlayStyle()
        }

        // The ad-blocker list load flags its own failure; the host only renders (and acknowledges) it.
        adblockModel.loadError.subscribe(activity.lifecycle) { failed ->
            if (failed) {
                Utils.showToast(activity, R.string.adblock_load_error)
                adblockModel.loadError.value = false
            }
        }

        viewModel.homePageLinks.subscribe(activity.lifecycle, true) {
            Log.i(TAG, "homePageLinks updated")
            val currentUrl = tabsModel.currentTab.value?.url ?: return@subscribe
            if (HomePagePolicy.isHomePageUrl(currentUrl, Config.localHomePageUrl)) {
                tabsModel.currentTab.value
                    ?.webEngine
                    ?.reload()
            }
        }

        // Bookmark state is owned by the view model; the view only renders it.
        viewModel.bookmarks.subscribe(activity.lifecycle, false) { bookmarksController.renderBookmarks(it) }

        viewModel.currentPageBookmarked.subscribe(activity.lifecycle, false) {
            bookmarksController.renderCurrentPageBookmarked(it)
        }

        tabsModel.currentTab.subscribe(activity.lifecycle, true) {
            vb.vActionBar.setAddressBoxText(it?.url ?: "")
            it?.let(onCurrentTabChanged)
        }

        tabsModel.tabsStates.subscribe(activity.lifecycle, false) {
            vb.vActionBar.setTabsCounter(it.size)
            if (it.isEmpty()) {
                if (!WebEnginePolicy.isGecko(config.webEngine)) {
                    vb.flWebViewContainer.removeAllViews()
                }
            }
        }
    }
}
