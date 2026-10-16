package com.phlox.tvwebbrowser.activity.main

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Process
import android.util.Log
import android.util.Patterns
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.FrameLayout
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.phlox.tvwebbrowser.AppContext
import com.phlox.tvwebbrowser.Config
import com.phlox.tvwebbrowser.R
import com.phlox.tvwebbrowser.TVBro
import com.phlox.tvwebbrowser.activity.IncognitoModeMainActivity
import com.phlox.tvwebbrowser.activity.downloads.DownloadsActivity
import com.phlox.tvwebbrowser.activity.history.HistoryActivity
import com.phlox.tvwebbrowser.activity.main.dialogs.favorites.FavoriteEditorDialog
import com.phlox.tvwebbrowser.activity.main.dialogs.settings.SettingsDialog
import com.phlox.tvwebbrowser.activity.main.dialogs.settings.SettingsDeps
import com.phlox.tvwebbrowser.activity.main.view.ActionBar
import com.phlox.tvwebbrowser.activity.main.view.CursorMenuView
import com.phlox.tvwebbrowser.activity.main.view.bookmarks.BookmarksPanelView
import com.phlox.tvwebbrowser.activity.main.view.tabs.TabsAdapter.Listener
import com.phlox.tvwebbrowser.databinding.ActivityMainBinding
import com.phlox.tvwebbrowser.domain.AddressInputPolicy
import com.phlox.tvwebbrowser.domain.LinkActionPolicy
import com.phlox.tvwebbrowser.model.FavoriteItem
import com.phlox.tvwebbrowser.model.HostConfig
import com.phlox.tvwebbrowser.model.WebTabState
import com.phlox.tvwebbrowser.service.downloads.DownloadService
import com.phlox.tvwebbrowser.singleton.shortcuts.ShortcutMgr
import com.phlox.tvwebbrowser.utils.BackNavigationEventsAdapter
import com.phlox.tvwebbrowser.utils.BaseAnimationListener
import com.phlox.tvwebbrowser.utils.InstallPermissionHelper
import com.phlox.tvwebbrowser.utils.Utils
import com.phlox.tvwebbrowser.utils.UpdateChecker
import com.phlox.tvwebbrowser.utils.VoiceSearchHelper
import com.phlox.tvwebbrowser.utils.activemodel.ActiveModelsRepository
import com.phlox.tvwebbrowser.utils.childs
import com.phlox.tvwebbrowser.utils.sameDay
import com.phlox.tvwebbrowser.player.MediaDiscoveryNotifier
import com.phlox.tvwebbrowser.webengine.WebEngine
import com.phlox.tvwebbrowser.webengine.WebEngineFactory
import com.phlox.tvwebbrowser.webengine.WebEngineWindowProviderCallback
import com.phlox.tvwebbrowser.widgets.NotificationView
import com.phlox.tvwebbrowser.widgets.cursor.CursorDrawerDelegate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.util.Calendar
import kotlin.system.exitProcess


open class MainActivity : AppCompatActivity(), ActionBar.Callback, WebEngineCallbackHost, TabViewHost, AutoUpdateHost, DownloadHost {
    companion object {
        private val TAG = MainActivity::class.java.simpleName
        const val VOICE_SEARCH_REQUEST_CODE = 10001
        const val PICK_FILE_REQUEST_CODE = 10005
        const val KEY_PROCESS_ID_TO_KILL = "proc_id_to_kill"
        private const val MY_PERMISSIONS_REQUEST_VOICE_SEARCH_PERMISSIONS = 10008
    }

    /** [WebEngineCallbackHost]: the activity is the host's view. */
    override val activity: Activity get() = this

    private lateinit var vb: ActivityMainBinding
    private lateinit var viewModel: MainActivityViewModel
    private lateinit var tabsModel: TabsModel
    /** Composition root for the settings: also read by [com.phlox.tvwebbrowser.activity.IncognitoModeMainActivity]. */
    protected lateinit var settingsModel: SettingsModel
    private lateinit var adblockModel: AdblockModel
    private lateinit var autoUpdateModel: AutoUpdateModel
    private lateinit var uiHandler: Handler
    private lateinit var panelController: PanelController
    private lateinit var toolbarController: ToolbarController
    private lateinit var mediaListPresenter: MediaListPresenter
    private lateinit var permissionOrchestrator: PermissionOrchestrator
    private lateinit var intentHandler: IntentHandler
    private val mediaDiscoveryListener: () -> Unit = { uiHandler.post { updateMediaCounter() } }
    private lateinit var prefs: SharedPreferences
    protected val config = AppContext.provideConfig()
    private val voiceSearchHelper = VoiceSearchHelper(this, VOICE_SEARCH_REQUEST_CODE,
        MY_PERMISSIONS_REQUEST_VOICE_SEARCH_PERMISSIONS)
    private val downloadCoordinator by lazy {
        DownloadCoordinator(this) { onDownloadStarted(it) }
    }
    var openUrlInExternalAppDialog: AlertDialog? = null
    private var linkActionsMenu: PopupMenu? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Read straight from Config: this runs before the models are composed (and, in the
        // process-switch case below, before this activity really starts).
        val incognitoMode = config.incognitoMode
        Log.d(TAG, "onCreate incognitoMode: $incognitoMode")
        if (incognitoMode xor (this is IncognitoModeMainActivity)) {
            switchProcess(incognitoMode, intent?.extras)
            finish()
            return
        }
        val pidToKill = intent?.getIntExtra(KEY_PROCESS_ID_TO_KILL, -1) ?: -1
        if (pidToKill != -1) {
            Process.killProcess(pidToKill)
        }

        viewModel = ActiveModelsRepository.get(MainActivityViewModel::class, this)
        if (incognitoMode) {
            viewModel.prepareSwitchToIncognito()
        }
        settingsModel = ActiveModelsRepository.get(SettingsModel::class, this)
        adblockModel = ActiveModelsRepository.get(AdblockModel::class, this)
        tabsModel = ActiveModelsRepository.get(TabsModel::class, this)
        autoUpdateModel = ActiveModelsRepository.get(AutoUpdateModel::class, this)
        uiHandler = Handler()
        MediaDiscoveryNotifier.addListener(mediaDiscoveryListener)
        prefs = getSharedPreferences(TVBro.MAIN_PREFS_NAME, Context.MODE_PRIVATE)
        vb = ActivityMainBinding.inflate(layoutInflater)
        setContentView(vb.root)

        // The view-layer collaborators: each owns one slice of the activity's UI work and reaches it
        // only through the binding and the models/callbacks handed to it here.
        panelController = PanelController(vb) {
            tabsModel.currentTab.value?.webEngine?.getView()?.requestFocus()
        }
        toolbarController = ToolbarController(
            this, vb,
            keepScreenOn = { settingsModel.keepScreenOn.value },
            focusEngine = { tabsModel.currentTab.value?.webEngine?.getView()?.requestFocus() },
        )
        mediaListPresenter = MediaListPresenter(this, tabsModel)
        permissionOrchestrator = PermissionOrchestrator(
            downloadCoordinator,
            voiceSearchPermissionsResult = voiceSearchHelper::processPermissionsResult,
            voiceSearchActivityResult = voiceSearchHelper::processActivityResult,
        ) { requestCode, permissions, grantResults ->
            tabsModel.currentTab.value?.webEngine?.onPermissionsResult(requestCode, permissions, grantResults) == true
        }
        intentHandler = IntentHandler(
            tabsModel = tabsModel,
            openInNewTab = { url, index, navigateImmediately ->
                openInNewTab(url, index, needToHideMenuOverlay = true, navigateImmediately = navigateImmediately)
            },
            navigate = { navigate(it) },
            changeTab = { changeTab(it) },
            autoHideMenuOverlay = { toolbarController.autoHideMenuOverlay() },
        )

        vb.ivMiniatures.visibility = View.INVISIBLE
        vb.rlActionBar.visibility = View.VISIBLE
        vb.progressBar.visibility = View.GONE

        vb.vTabs.listener = tabsListener
        vb.vBookmarks.listener = bookmarksListener
        // The composition root hands the models to the views; the views never locate them.
        vb.vTabs.bind(tabsModel)

        vb.vActionBar.callback = this

        // The cursor menu asks the host about (and reports back) the "direct navigation" hint
        // instead of reading global config itself.
        vb.vCursorMenu.shouldSuppressDirectNavigationHint = { settingsModel.directNavigationModeHintSuppress }
        vb.vCursorMenu.onDirectNavigationHintSuppressed = { settingsModel.directNavigationModeHintSuppress = true }

        settingsModel.userAgentString.subscribe(this.lifecycle, false) {
            for (tab in tabsModel.tabsStates) {
                tab.webEngine.userAgentString = it
            }
        }

        settingsModel.theme.subscribe(this.lifecycle, false) {
            val mode = when (it) {
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
            if (!isFinishing && !isDestroyed) recreate()
        }

        settingsModel.keepScreenOn.subscribe(this.lifecycle) {
            if (it) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        // The toolbar overlay style follows the setting live, so the settings page never touches the
        // activity to restyle it.
        settingsModel.keepToolbarOpen.subscribe(this.lifecycle) {
            toolbarController.applyOverlayStyle()
        }

        // The ad-blocker list load flags its own failure; the host only renders (and acknowledges) it.
        adblockModel.loadError.subscribe(this.lifecycle) { failed ->
            if (failed) {
                Utils.showToast(this, R.string.adblock_load_error)
                adblockModel.loadError.value = false
            }
        }

        viewModel.homePageLinks.subscribe(this) {
            Log.i(TAG, "homePageLinks updated")
            val currentUrl = tabsModel.currentTab.value?.url ?: return@subscribe
            if (Config.isHomePageUrl(currentUrl)) {
                tabsModel.currentTab.value?.webEngine?.reload()
            }
        }

        // Bookmark state is owned by the view model; the view only renders it.
        viewModel.bookmarks.subscribe(this, false) { vb.vBookmarks.setBookmarks(it) }

        viewModel.currentPageBookmarked.subscribe(this, false) { bookmarked ->
            vb.vBookmarks.setCurrentPage(tabsModel.currentTab.value?.url, bookmarked)
            vb.vActionBar.setBookmarked(bookmarked)
        }

        tabsModel.currentTab.subscribe(this) {
            vb.vActionBar.setAddressBoxText(it?.url ?: "")
            it?.let {
                onWebViewUpdated(it)
            }
        }

        tabsModel.tabsStates.subscribe(this, false) {
            vb.vActionBar.setTabsCounter(it.size)
            if (it.isEmpty()) {
                if (!config.isWebEngineGecko()) {
                    vb.flWebViewContainer.removeAllViews()
                }
            }
        }

        onBackPressedDispatcher.addCallback(onBackPressedCallback)

        loadState()
    }

    private var progressBarHideRunnable: Runnable = Runnable {
        val anim = AnimationUtils.loadAnimation(this@MainActivity, android.R.anim.fade_out)
        anim.setAnimationListener(object : BaseAnimationListener() {
            override fun onAnimationEnd(animation: Animation) {
                vb.progressBar.visibility = View.GONE
            }
        })
        vb.progressBar.startAnimation(anim)
    }

    private val mConnectivityChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = cm.activeNetworkInfo
            val isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting
            val tab = tabsModel.currentTab.value ?: return
            tab.webEngine.setNetworkAvailable(isConnected)
        }
    }

    private val displayThumbnailRunnable = object : Runnable {
        var tabState: WebTabState? = null
        override fun run() {
            tabState?.let {
                lifecycleScope.launch(Dispatchers.Main) {
                    displayThumbnail(it)
                }
            }
        }
    }

    private val tabsListener = object : Listener {
        override fun onTitleChanged(index: Int) {
            Log.d(TAG, "onTitleChanged: $index")
            val tab = tabByTitleIndex(index)
            vb.vActionBar.setAddressBoxText(tab?.url ?: "")
            uiHandler.removeCallbacks(displayThumbnailRunnable)
            displayThumbnailRunnable.tabState = tab
            uiHandler.postDelayed(displayThumbnailRunnable, 200)
        }

        override fun onTitleSelected(index: Int) {
            val tab = tabByTitleIndex(index)
            if (tab == null) {
                syncTabWithTitles()
            } else if (!tab.selected) {
                changeTab(tab)
            }
            panelController.hideTabsPanel()
            toolbarController.autoHideMenuOverlay()
        }

        override fun onAddNewTabSelected() {
            onNewTabRequested(tabsModel.tabsStates.size)
        }

        override fun closeTab(tabState: WebTabState?) = this@MainActivity.closeTab(tabState)

        override fun onNewTabRequested(tabIndex: Int) {
            this@MainActivity.openInNewTab(settingsModel.homePage, tabIndex,
                needToHideMenuOverlay = false,
                navigateImmediately = true
            )
        }
    }

    private val bookmarksListener = object : BookmarksPanelView.Listener {
        override fun onToggleThisPageBookmark() {
            toggleCurrentPageBookmark()
        }

        override fun onBookmarkSelected(item: FavoriteItem) {
            item.url?.let { navigate(it) }
            hideBookmarksPanel()
        }

        override fun onEditBookmark(item: FavoriteItem) {
            FavoriteEditorDialog(
                this@MainActivity,
                initialTitle = item.title,
                initialUrl = item.url,
                newBookmark = item.id == 0L,
            ) { title, url ->
                lifecycleScope.launch(Dispatchers.Main) {
                    viewModel.onHomePageLinkEdited(item, title, url).join()
                    loadBookmarks()
                    updateBookmarkState()
                }
            }.show()
        }

        override fun onDeleteBookmark(item: FavoriteItem) {
            lifecycleScope.launch(Dispatchers.Main) {
                viewModel.deleteBookmark(item.id).join()
                loadBookmarks()
                updateBookmarkState()
            }
        }
    }

    override fun closeWindow() {
        Log.d(TAG, "closeWindow")
        lifecycleScope.launch {
            if (settingsModel.incognitoMode) {
                toggleIncognitoMode(andSwitchProcess = false)
            }
            finish()
        }
    }

    override fun showDownloads() {
        startActivity(Intent(this@MainActivity, DownloadsActivity::class.java))
    }

    override fun showHistory() {
        startActivityForResult(
                Intent(this@MainActivity, HistoryActivity::class.java),
                IntentHandler.REQUEST_CODE_HISTORY_ACTIVITY)
        toolbarController.autoHideMenuOverlay()
    }

    override fun showFavorites() {
        if (panelController.isBookmarksPanelVisible) {
            hideBookmarksPanel()
        } else {
            showBookmarksPanel()
        }
    }

    private fun showBookmarksPanel() {
        // Read the content first and hand the focus to a row only afterwards: the rows, and the
        // "Add this page" entry (which is hidden again for the home page), are final by then, so the
        // hand-off needs at most the one layout pass that creates them.
        panelController.showBookmarksPanel(lifecycleScope) {
            loadBookmarks()
            updateBookmarkState()
        }
    }

    private fun hideBookmarksPanel() {
        panelController.hideBookmarksPanel()
    }

    private suspend fun loadBookmarks() {
        viewModel.loadBookmarks()
    }

    /**
     * Asks the view model to recompute whether the current page is bookmarked. The toolbar star and
     * the panel's "Add this page" / "Remove from list" row follow [MainActivityViewModel.currentPageBookmarked].
     */
    private fun updateBookmarkState() {
        val url = tabsModel.currentTab.value?.url
        lifecycleScope.launch(Dispatchers.Main) {
            viewModel.refreshBookmarkState(url)
        }
    }

    private fun addCurrentPageAsBookmark() {
        val currentTab = tabsModel.currentTab.value ?: return
        val url = currentTab.url
        if (url.isNullOrBlank() || Config.isHomePageUrl(url)) return
        val newItem = FavoriteItem()
        FavoriteEditorDialog(
            this,
            initialTitle = currentTab.title ?: url,
            initialUrl = url,
            newBookmark = true,
        ) { title, editedUrl ->
            lifecycleScope.launch(Dispatchers.Main) {
                viewModel.onHomePageLinkEdited(newItem, title, editedUrl).join()
                loadBookmarks()
                updateBookmarkState()
            }
        }.show()
    }

    /**
     * The bookmarks panel's first row: adds the current page if it is not bookmarked, or removes
     * the existing bookmark when it already is.
     */
    private fun toggleCurrentPageBookmark() {
        val currentTab = tabsModel.currentTab.value ?: return
        val url = currentTab.url
        if (url.isNullOrBlank() || Config.isHomePageUrl(url)) return
        lifecycleScope.launch(Dispatchers.Main) {
            val existing = viewModel.getBookmarkByUrl(url)
            if (existing != null) {
                viewModel.deleteBookmark(existing.id).join()
                loadBookmarks()
                updateBookmarkState()
            } else {
                addCurrentPageAsBookmark()
            }
        }
    }

    private fun tabByTitleIndex(index: Int) =
            if (index >= 0 && index < tabsModel.tabsStates.size) tabsModel.tabsStates[index] else null

    override fun showSettings() {
        SettingsDialog(this, SettingsDeps(settingsModel, adblockModel, autoUpdateModel)).apply {
            // The ad-block toggle is global (adblockModel.adBlockEnabled) while the overlay zapper is a
            // document-start script registered per WebView; re-sync every open tab on dismiss so
            // the zapper matches the network blocker. Per-tab overrides are preserved.
            onDismissed = {
                tabsModel.tabsStates.forEach {
                    it.webEngine.onUpdateAdblockSetting(it.adblock ?: adblockModel.adBlockEnabled)
                }
                updateUpdateBadge()
            }
        }.show()
    }

    override fun onExtendedAddressBarMode() {
        panelController.hideAllPanels()
    }

    override fun onUrlInputDone() {
        toolbarController.autoHideMenuOverlay()
    }

    fun navigateBack(goHomeIfNoHistory: Boolean = false) {
        val currentTab = tabsModel.currentTab.value
        if (currentTab != null && currentTab.webEngine.canGoBack()) {
            currentTab.webEngine.goBack()
        } else if (goHomeIfNoHistory) {
            navigate(settingsModel.homePage)
        }
    }

    override fun onNavigateBack() = navigateBack()

    override fun onNavigateForward() {
        val tab = tabsModel.currentTab.value ?: return
        if (tab.webEngine.canGoForward()) {
            tab.webEngine.goForward()
        }
    }

    override fun onReload() = refresh()

    override fun onShowStreams() = mediaListPresenter.showMediaListDialog()

    override fun onToggleTabPanel() {
        panelController.toggleTabsPanel()
    }

    override fun onShowMenu() {
        val currentTab = tabsModel.currentTab.value
        val items = arrayListOf(
            getString(R.string.navigate_home),
            getString(R.string.new_tab),
            getString(R.string.add_bookmark),
            getString(R.string.toggle_ads_blocking),
            getString(R.string.block_popups),
            getString(R.string.history),
            getString(R.string.downloads),
            getString(R.string.incognito_mode),
            getString(R.string.voice_search),
            getString(R.string.toggle_theme),
            getString(R.string.close_tab),
            getString(R.string.close_application)
        ).toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.menu)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> navigate(settingsModel.homePage)
                    1 -> openInNewTab(settingsModel.homePage, tabsModel.tabsStates.size, needToHideMenuOverlay = false, navigateImmediately = true)
                    2 -> addCurrentPageAsBookmark()
                    3 -> toggleAdBlockForTab()
                    4 -> lifecycleScope.launch(Dispatchers.Main) { showPopupBlockOptions() }
                    5 -> showHistory()
                    6 -> showDownloads()
                    7 -> toggleIncognitoMode()
                    8 -> initiateVoiceSearch()
                    9 -> toggleTheme()
                    10 -> currentTab?.let { closeTab(it) }
                    11 -> closeWindow()
                }
            }
            .show()
    }

    /**
     * Cycles the UI theme SYSTEM → WHITE → BLACK → SYSTEM. The actual night-mode switch and the
     * activity repaint are handled by the `settingsModel.theme` subscriber in onCreate.
     */
    private fun toggleTheme() {
        val themes = Config.Theme.entries
        settingsModel.setTheme(themes[(settingsModel.theme.value.ordinal + 1) % themes.size])
    }

    fun refresh() {
        tabsModel.currentTab.value?.webEngine?.reload()
    }

    /** Public entry point for the Close tab shortcut (the overflow menu calls [closeTab] directly). */
    fun closeCurrentTab() {
        closeTab(tabsModel.currentTab.value)
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        //here properties can be uninitialized in case of wrong activity for incognito mode
        //detection and force activity restart in onCreate()
        if (::tabsModel.isInitialized) {
            tabsModel.onDetachActivity()
        }
        MediaDiscoveryNotifier.removeListener(mediaDiscoveryListener)
        super.onDestroy()
    }

    @SuppressLint("MissingSuperCall")
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.data != null) {
            handleIntent(intent)
        }
    }

    private fun loadState() = lifecycleScope.launch(Dispatchers.Main) {
        Log.d(TAG, "loadState")
        WebEngineFactory.initialize(this@MainActivity, vb.flWebViewContainer)

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

        // `tabsStates` is retained across activity re-creation (theme toggle), in which case the
        // observer above is not re-fired, so seed the tab counter here as well.
        vb.vActionBar.setTabsCounter(tabsModel.tabsStates.size)

        if (intent.data == null) {
            if (tabsModel.tabsStates.isEmpty()) {
                openInNewTab(settingsModel.homePage, 0,
                    needToHideMenuOverlay = true,
                    navigateImmediately = true
                )
            } else {
                var foundSelectedTab = false
                for (i in tabsModel.tabsStates.indices) {
                    val tab = tabsModel.tabsStates[i]
                    if (tab.selected) {
                        changeTab(tab)
                        foundSelectedTab = true
                        break
                    }
                }
                if (!foundSelectedTab) {//this may happen in some error states
                    changeTab(tabsModel.tabsStates[0])
                }
            }
        } else {
            handleIntent(intent)
        }

        val currentTab = tabsModel.currentTab.value
        if (currentTab == null || currentTab.url == settingsModel.homePage) {
            toolbarController.showMenuOverlay()
        }
        if (autoUpdateModel.needAutoCheckUpdates &&
            autoUpdateModel.updateChecker.versionCheckResult == null &&
                !autoUpdateModel.lastUpdateNotificationTime.sameDay(Calendar.getInstance())) {
            autoUpdateModel.checkUpdate(false){
                updateUpdateBadge()
                if (autoUpdateModel.updateChecker.hasUpdate()) {
                    autoUpdateModel.showUpdateDialogIfNeeded(this@MainActivity)
                }
            }
        }
        updateUpdateBadge()
    }

    /** Keeps the toolbar settings dot in sync with the last update check result. */
    private fun updateUpdateBadge() {
        vb.vActionBar.setUpdateAvailable(autoUpdateModel.isUpdateAvailable())
    }

    // ---- AutoUpdateHost: the view side of the auto-update flow ------------------------------

    override val isScreenFinishing: Boolean get() = isFinishing

    override fun canInstallPackages(): Boolean = InstallPermissionHelper.canInstallPackages(this)

    override fun showUpdateDialog(onDownload: () -> Unit, onSettings: () -> Unit) {
        autoUpdateModel.updateChecker.showUpdateDialog(this, object : UpdateChecker.DialogCallback {
            override fun download() = onDownload()
            override fun later() {}
            override fun settings() = onSettings()
        })
    }

    override fun showUnknownSourcesDialog(onConfirm: () -> Unit) {
        InstallPermissionHelper.showEnableUnknownSourcesDialog(this, onConfirm)
    }

    override fun openInstallPermissionSettings(): Boolean =
        InstallPermissionHelper.openUnknownSourcesSettings(this)

    override fun downloadUpdate() {
        autoUpdateModel.modelScope.launch(Dispatchers.Main) {
            autoUpdateModel.updateChecker.downloadUpdate(this@MainActivity, autoUpdateModel.modelScope)
        }
    }

    // ---- DownloadHost: the platform side of DownloadCoordinator ------------------------------

    override fun bindDownloadService(connection: ServiceConnection) {
        bindService(Intent(this, DownloadService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    override fun unbindDownloadService(connection: ServiceConnection) {
        unbindService(connection)
    }

    override fun hasPermission(permission: String): Boolean =
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    override fun requestPermission(permission: String, requestCode: Int) {
        requestPermissions(arrayOf(permission), requestCode)
    }

    private fun handleIntent(intent: Intent) {
        intentHandler.handleIntent(intent)
    }

    private fun openInNewTab(url: String?, index: Int = 0, needToHideMenuOverlay: Boolean = true, navigateImmediately: Boolean): WebEngine? {
        Log.d(TAG, "openInNewTab: url: $url, index: $index, needToHideMenuOverlay: $needToHideMenuOverlay, navigateImmediately: $navigateImmediately")
        if (url == null) {
            return null
        }
        val tab = WebTabState(url = url, incognito = settingsModel.incognitoMode)
        createWebView(tab) ?: return null
        tabsModel.onOpenTab(tab, index)
        changeTab(tab)
        if (navigateImmediately) {
            navigate(url)
        }
        if (needToHideMenuOverlay) {
            toolbarController.autoHideMenuOverlay(true)
        }
        return tab.webEngine
    }

    override fun closeTab(tab: WebTabState?) {
        if (tab == null) return
        tabsModel.onCloseTab(
            tab,
            switchTo = { changeTab(it) },
            openNewTab = { openInNewTab(settingsModel.homePage, 0, needToHideMenuOverlay = true, navigateImmediately = true) },
        )
        toolbarController.autoHideMenuOverlay(true)
    }

    override fun changeTab(newTab: WebTabState) {
        tabsModel.changeTab(
            newTab,
            this,
            WebEngineCallback(this, newTab, tabsModel, adblockModel, settingsModel, viewModel),
        )
    }

    // ---- TabViewHost: the view side of tab switching ----------------------------------------

    override fun createView(tab: WebTabState): Boolean = createWebView(tab) != null

    override fun attachEngineToWindow(tab: WebTabState, callback: WebEngineWindowProviderCallback) {
        tab.webEngine.onAttachToWindow(callback, vb.flWebViewContainer)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun createWebView(tab: WebTabState): View? {
        val webView: View
        try {
            webView = tab.webEngine.getOrCreateView(this) //WebViewEx(this, WebViewCallback(tab), AndroidJSInterface(this, viewModel, tabsModel, tab))
        } catch (e: Throwable) {
            e.printStackTrace()

            if (!config.isWebEngineGecko()) {
                val dialogBuilder = AlertDialog.Builder(this)
                    .setTitle(R.string.error)
                    .setCancelable(false)
                    .setMessage(R.string.err_webview_can_not_link)
                    .setNegativeButton(R.string.exit) { _, _ -> finish() }

                val appPackageName = "com.google.android.webview"
                val intent =
                    Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$appPackageName"))
                val activities = packageManager.queryIntentActivities(intent, 0)
                if (activities.size > 0) {
                    dialogBuilder.setPositiveButton(R.string.find_in_apps_store) { _, _ ->
                        try {
                            startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        finish()
                    }
                }
                dialogBuilder.show()
            }
            return null
        }

        var ua = settingsModel.userAgentString.value
        if (ua?.contains("TV Bro/1.0 ") == true) {//legacy ua string - now default one should be used
            settingsModel.userAgentString.value = null
            ua = null
        }
        if (ua != null) {
            tab.webEngine.userAgentString = ua
        }

        return webView
    }

    override fun onWebViewUpdated(tab: WebTabState) {
        vb.vActionBar.setBackEnabled(tab.webEngine.canGoBack() == true)
        vb.vActionBar.setForwardEnabled(tab.webEngine.canGoForward() == true)

        updateMediaCounter()
        updateBookmarkState()
    }

    private fun updateMediaCounter() {
        val count = tabsModel.currentTab.value?.webEngine?.mediaCandidates()?.size ?: 0
        vb.vActionBar.setMediaCounter(count)
    }

    override fun onTrimMemory(level: Int) {
        for (tab in tabsModel.tabsStates) {
            if (!tab.selected) {
                tab.trimMemory()
            }
        }
        super.onTrimMemory(level)
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        if (permissionOrchestrator.onRequestPermissionsResult(requestCode, permissions, grantResults)) {
            return
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (permissionOrchestrator.onActivityResult(requestCode, resultCode, data)) {
            return
        }
        when (requestCode) {
            PICK_FILE_REQUEST_CODE -> {
                tabsModel.currentTab.value?.webEngine?.onFilePicked(resultCode, data)
            }
            IntentHandler.REQUEST_CODE_HISTORY_ACTIVITY ->
                intentHandler.handleHistoryResult(resultCode, data)
            InstallPermissionHelper.REQUEST_CODE_UNKNOWN_APP_SOURCES -> if (autoUpdateModel.needToShowUpdateDlgAgain) {
                autoUpdateModel.showUpdateDialogIfNeeded(this)
            }

            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onStart() {
        super.onStart()
        downloadCoordinator.bind()
    }

    override fun onStop() {
        super.onStop()
        downloadCoordinator.unbind()
    }

    override fun onResume() {
        super.onResume()
        updateUpdateBadge()
        val intentFilter = IntentFilter("android.net.conn.CONNECTIVITY_CHANGE")
        registerReceiver(mConnectivityChangeReceiver, intentFilter)
        tabsModel.currentTab.value?.webEngine?.onResume()
    }

    override fun onPause() {
        unregisterReceiver(mConnectivityChangeReceiver)
        tabsModel.currentTab.value?.apply {
            webEngine.onPause()
            onPause()
            runBlocking { tabsModel.saveTab(this@apply) }
        }
        super.onPause()
    }

    private fun toggleAdBlockForTab() {
        val tab = tabsModel.currentTab.value ?: return
        val enabled = tabsModel.onToggleAdBlock(tab, adblockModel.adBlockEnabled)
        tab.webEngine.onUpdateAdblockSetting(enabled)
        onWebViewUpdated(tab)
        refresh()
    }

    private suspend fun showPopupBlockOptions() {
        val tab = tabsModel.currentTab.value ?: return
        val currentHostConfig = tabsModel.findHostConfig(tab,false)
        val currentBlockPopupsLevelValue = currentHostConfig?.popupBlockLevel ?: HostConfig.DEFAULT_BLOCK_POPUPS_VALUE
        val hostName = currentHostConfig?.hostName ?: try { URL(tab.url).host } catch (e: Exception) { "" }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.block_popups_s, hostName))
            .setSingleChoiceItems(R.array.popup_blocking_level, currentBlockPopupsLevelValue) {
                    dialog, itemId -> lifecycleScope.launch {
                        tabsModel.changePopupBlockingLevel(itemId, tab)
                        dialog.dismiss()
                    }
            }
            .show()
    }

    fun navigate(url: String) {
        Log.d(TAG, "navigate: $url")
        vb.vActionBar.setAddressBoxTextColor(ContextCompat.getColor(this@MainActivity, R.color.default_url_color))
        if (!tabsModel.navigateTo(url)) {
            openInNewTab(url, 0, needToHideMenuOverlay = true, navigateImmediately = true)
        }
    }

    override fun search(aText: String) {
        val url = AddressInputPolicy.resolveUrl(
            aText,
            settingsModel.searchEngineURL,
        ) { Patterns.WEB_URL.matcher(it).matches() }
        if (url == null) {
            Utils.showToast(this, R.string.error)
            return
        }
        navigate(url)
    }

    override fun toggleIncognitoMode() {
        lifecycleScope.launch(Dispatchers.Main) {
            toggleIncognitoMode(andSwitchProcess = true)
        }
    }

    /**
     * Runs the incognito flip the model decided on and shows the progress it takes: everything
     * about *what* has to be cleaned (and in which order) lives in
     * [MainActivityViewModel.toggleIncognitoMode]; the view only supplies the engine session
     * cleanup and the tab closing, and restarts the process afterwards.
     */
    private suspend fun toggleIncognitoMode(andSwitchProcess: Boolean) {
        Log.d(TAG, "toggleIncognitoMode andSwitchProcess: $andSwitchProcess")
        vb.progressBarGeneric.visibility = View.VISIBLE
        val newMode = viewModel.toggleIncognitoMode(
            clearEngineSession = {
                withContext(Dispatchers.IO) {
                    WebStorage.getInstance().deleteAllData()
                    CookieManager.getInstance().removeAllCookies(null)
                    CookieManager.getInstance().flush()
                }
                WebEngineFactory.clearCache(this@MainActivity)
            },
            clearOpenTabs = { tabsModel.onCloseAllTabs().join() },
        )
        vb.progressBarGeneric.visibility = View.GONE
        if (andSwitchProcess) {
            switchProcess(newMode)
        }
    }

    private fun switchProcess(incognitoMode: Boolean, intentDataToCopy: Bundle? = null) {
        Log.d(TAG, "switchProcess incognitoMode: $incognitoMode")
        val activityClass = if (incognitoMode) IncognitoModeMainActivity::class.java
        else MainActivity::class.java
        val intent = Intent(this@MainActivity, activityClass)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        intent.putExtra(KEY_PROCESS_ID_TO_KILL, Process.myPid())
        intentDataToCopy?.let {
            intent.putExtras(it)
        }
        startActivity(intent)
        exitProcess(0)
    }

    fun toggleMenu() {
        toolbarController.toggleMenu()
    }

    val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            backNavigationEventsAdapter.dispatchSystemBackNavigationEvent()
        }
    }

    private val backNavigationEventsAdapter = BackNavigationEventsAdapter(
        onEmulatedBackEvent = {
            if (!hideSoftwareKeyboardIfVisible()) {
                handleBackNavigation()
            }
        }
    )

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val localCallback = window.callback
        window.callback = object : Window.Callback by localCallback {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                //Log.d(TAG, "dispatchKeyEvent event: $event")
                backNavigationEventsAdapter.dispatchKeyEvent(event)

                val keyCode = if (event.keyCode != 0) event.keyCode else event.scanCode
                val keyCodeBackNavigation = keyCode == KeyEvent.KEYCODE_ESCAPE ||
                        keyCode == KeyEvent.KEYCODE_BUTTON_B || keyCode == KeyEvent.KEYCODE_BACK
                val shortcutMgr = ShortcutMgr.getInstance()
                val currentTab = tabsModel.currentTab.value
                if (!keyCodeBackNavigation &&
                    shortcutMgr.handle(event, this@MainActivity, currentTab)) {
                    return true
                }

                return localCallback.dispatchKeyEvent(event)
            }

            override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
                //Log.d(TAG, "dispatchGenericMotionEvent event: $event")
                if (backNavigationEventsAdapter.dispatchGenericMotionEvent(event)) {
                    return true
                }
                return localCallback.dispatchGenericMotionEvent(event)
            }
        }
    }

    /**
     * When the IME is visible, the first back press should only dismiss it; this mirrors standard
     * Android behavior and avoids navigating away while the user is typing.
     *
     * @return true if the IME was visible and a hide was requested.
     */
    private fun hideSoftwareKeyboardIfVisible(): Boolean {
        val root = window.decorView.rootView
        val insets = ViewCompat.getRootWindowInsets(root) ?: return false
        if (!insets.isVisible(WindowInsetsCompat.Type.ime())) {
            return false
        }
        val view = currentFocus ?: root
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
        return true
    }

    private fun handleBackNavigation() {
        Log.d(TAG, "handleBackNavigation")
        if (tabsModel.currentTab.value?.webEngine?.isVirtualCursorMode() == false) {
            tabsModel.currentTab.value?.webEngine?.setVirtualCursorMode(true)
            backNavigationEventsAdapter.gameControllersLongPressBForBackNavigation = false
            return
        }

        if (vb.vCursorMenu.isVisible) {
            vb.vCursorMenu.close(CursorMenuView.CloseAnimation.ROTATE_OUT)
        } else if (vb.flWebViewContainer.cursorDrawerDelegate.canHandleBackNavigation()) {
            vb.flWebViewContainer.cursorDrawerDelegate.handleBackNavigation()
        } else if (toolbarController.isFullscreen) {
            tabsModel.currentTab.value?.webEngine?.hideFullscreenView()
        } else if (panelController.isBookmarksPanelVisible) {
            hideBookmarksPanel()
        } else if (panelController.isTabsPanelVisible) {
            panelController.hideTabsPanel()
        } else if (vb.flWebViewContainer.hasFocus()) {
            //Focus is on the page: BACK must give the remote a way back to the UI,
            //otherwise the user is stuck in the WebView with no focused control.
            toolbarController.showMenuOverlay()
        } else {
            toolbarController.toggleMenu()
        }
    }

    private suspend fun displayThumbnail(currentTab: WebTabState?) {
        if (settingsModel.keepToolbarOpen.value) return
        if (currentTab != null) {
            if (tabByTitleIndex(vb.vTabs.current) != currentTab) return
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

    private fun syncTabWithTitles() {
        val tab = tabByTitleIndex(vb.vTabs.current)
        if (tab == null) {
            openInNewTab(settingsModel.homePage, if (vb.vTabs.current < 0) 0 else tabsModel.tabsStates.size,
                needToHideMenuOverlay = true,
                navigateImmediately = true
            )
        } else if (!tab.selected) {
            changeTab(tab)
        }
    }

    private fun onDownloadStarted(fileName: String) {
        Utils.showToast(this, getString(R.string.download_started,
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString() + File.separator + fileName))
        toolbarController.showMenuOverlay()
    }

    override fun initiateVoiceSearch() {
        toolbarController.autoHideMenuOverlay()
        voiceSearchHelper.initiateVoiceSearch(object : VoiceSearchHelper.Callback {
            override fun onResult(text: String?) {
                if (text == null) {
                    Utils.showToast(this@MainActivity, getString(R.string.can_not_recognize))
                    return
                }
                search(text)
                toolbarController.autoHideMenuOverlay()
            }
        })
    }

    private fun onEditHomePageBookmark(favoriteItem: FavoriteItem) {
        FavoriteEditorDialog(
            this,
            initialTitle = favoriteItem.title,
            initialUrl = favoriteItem.url,
            newBookmark = favoriteItem.id == 0L,
        ) { title, url ->
            viewModel.onHomePageLinkEdited(favoriteItem, title, url)
        }.show()
    }

    // ---- WebEngineCallbackHost: the view side of the per-tab engine callback -----------------

    override fun openInNewTabAt(url: String, index: Int, navigateImmediately: Boolean): WebEngine? =
        openInNewTab(url, index, needToHideMenuOverlay = true, navigateImmediately = navigateImmediately)

    override fun showLoadingProgress(progress: Int) {
        vb.progressBar.visibility = View.VISIBLE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            vb.progressBar.setProgress(progress, true)
        } else {
            vb.progressBar.progress = progress
        }
        uiHandler.removeCallbacks(progressBarHideRunnable)
        if (progress == 100) {
            uiHandler.postDelayed(progressBarHideRunnable, 1000)
        } else {
            uiHandler.postDelayed(progressBarHideRunnable, 5000)
        }
    }

    override fun updateAddressBoxIfCurrentTab(tab: WebTabState) {
        if (tabByTitleIndex(vb.vTabs.current) == tab) {
            vb.vActionBar.setAddressBoxText(tab.url)
        }
    }

    override fun onTabTitleUpdated(tab: WebTabState) {
        vb.vTabs.onTabTitleUpdated(tab)
    }

    override fun onTabIconUpdated(tab: WebTabState) {
        vb.vTabs.onFavIconUpdated(tab)
    }

    override fun renderAndShowThumbnail(tab: WebTabState) {
        lifecycleScope.launch {
            val newThumbnail = tab.webEngine.renderThumbnail(tab.thumbnail)
            if (newThumbnail != null) {
                tab.updateThumbnail(this@MainActivity, newThumbnail)
                if (vb.rlActionBar.visibility == View.VISIBLE && tab == tabsModel.currentTab.value) {
                    displayThumbnail(tab)
                }
            }
        }
    }

    override fun onCertificateError() {
        vb.vActionBar.setAddressBoxTextColor(Color.RED)
    }

    override fun requestPermissionsOnHost(permissions: Array<String>): Int {
        val requestCode = permissionOrchestrator.nextRequestCode()
        requestPermissions(permissions, requestCode)
        return requestCode
    }

    override fun showFileChooser(intent: Intent): Boolean {
        try {
            startActivityForResult(intent, PICK_FILE_REQUEST_CODE)
        } catch (e: ActivityNotFoundException) {
            try {
                //trying again with type */* (seems file pickers usually doesn't support specific types in intent filters but still can do the job)
                intent.type = "*/*"
                startActivityForResult(intent, PICK_FILE_REQUEST_CODE)
            } catch (e: ActivityNotFoundException) {
                Utils.showToast(applicationContext, getString(R.string.err_cant_open_file_chooser))
                return false
            }
        }
        return true
    }

    override fun downloadRequested(request: DownloadRequest) {
        downloadCoordinator.requestDownload(
            url = request.url, referer = request.referer,
            originalDownloadFileName = request.fileName,
            userAgent = request.userAgent, mimeType = request.mimeType,
            operationAfterDownload = request.operationAfterDownload,
            base64BlobData = request.base64BlobData, stream = request.stream, size = request.size,
        )
    }

    override fun openInExternalAppRequested(url: String) {
        val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        val activityComponent = intent.resolveActivity(packageManager)
        if (activityComponent != null && activityComponent.packageName == packageName) {
            Toast.makeText(this@MainActivity, R.string.external_app_open_error, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this@MainActivity, R.string.external_app_open_error, Toast.LENGTH_SHORT).show()
        }
    }

    override fun copyToClipboard(url: String) {
        val clipBoard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText("URL", url)
        clipBoard.setPrimaryClip(clipData)
        Toast.makeText(this@MainActivity, getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
    }

    override fun shareUrl(url: String) {
        val share = Intent(Intent.ACTION_SEND)
        share.type = "text/plain"
        share.putExtra(Intent.EXTRA_SUBJECT, R.string.share_url)
        share.putExtra(Intent.EXTRA_TEXT, url)
        try {
            startActivity(share)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this@MainActivity, R.string.external_app_open_error, Toast.LENGTH_SHORT).show()
        }
    }

    override fun showBlockedDialogNotification(newTab: Boolean) {
        runOnUiThread {
            val msg = getString(if (newTab) R.string.new_tab_blocked else R.string.popup_dialog_blocked)
            NotificationView.showBottomRight(vb.rlRoot, R.drawable.ic_block_popups, msg)
        }
    }

    override fun onEditHomePageBookmarkSelected(favoriteId: Long) {
        lifecycleScope.launch {
            val favoriteItem = viewModel.getBookmarkById(favoriteId) ?: return@launch
            AlertDialog.Builder(this@MainActivity)
                .setTitle(R.string.bookmarks)
                .setItems(arrayOf(getString(R.string.edit), getString(R.string.delete))) { _, which ->
                    when (which) {
                        0 -> onEditHomePageBookmark(favoriteItem)
                        1 -> {
                            val bookmark = viewModel.homePageLinks.firstOrNull { it.favoriteId == favoriteId }
                            if (bookmark != null) {
                                viewModel.removeHomePageLink(bookmark)
                            } else {
                                viewModel.deleteBookmark(favoriteId)
                            }
                        }
                    }
                }
                .show()
        }
    }

    override fun onPrepareForFullscreen() {
        toolbarController.enterFullscreen()
    }

    override fun onExitFullscreen() {
        toolbarController.exitFullscreen()
    }

    override fun showCursorMenu(
        tab: WebTabState, callback: WebEngineWindowProviderCallback, cursorDrawer: CursorDrawerDelegate,
        baseUri: String?, linkUri: String?, srcUri: String?, title: String?, altText: String?,
        textContent: String?, x: Int, y: Int
    ) {
        uiHandler.post {
            vb.vCursorMenu.show(
                tab, callback, cursorDrawer,
                baseUri, linkUri, srcUri,
                title, altText, textContent,
                x, y,
                backNavigationEventsAdapter
            )
        }
    }

    override fun suggestActionsForLink(
        tab: WebTabState, callback: WebEngineWindowProviderCallback, baseUri: String?, linkUri: String?,
        srcUri: String?, title: String?, altText: String?, textContent: String?, x: Int, y: Int
    ) {
        val url = LinkActionPolicy.targetUrl(linkUri, srcUri)
        val isHTTPUrl = LinkActionPolicy.isHttpUrl(url)
        val anchor = View(this@MainActivity)
        val lp = FrameLayout.LayoutParams(1, 1)
        lp.setMargins(x, y, 0, 0)
        vb.flWebViewContainer.addView(anchor, lp)
        linkActionsMenu = PopupMenu(this@MainActivity, anchor, Gravity.BOTTOM).also {
            it.inflate(R.menu.menu_link)
            it.menu.findItem(R.id.miOpenInNewTab).isVisible = isHTTPUrl
            it.menu.findItem(R.id.miOpenInExternalApp).isVisible = isHTTPUrl
            it.menu.findItem(R.id.miDownload).isVisible = isHTTPUrl
            it.menu.findItem(R.id.miCopyToClipboard).isVisible = url != null
            it.menu.findItem(R.id.miShare).isVisible = url != null
            it.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.miRefreshPage -> tab.webEngine.reload()
                    R.id.miOpenInNewTab -> callback.onOpenInNewTabRequested(url!!, true)
                    R.id.miOpenInExternalApp -> callback.onOpenInExternalAppRequested(url!!)
                    R.id.miDownload -> callback.onDownloadRequested(url!!)
                    R.id.miCopyToClipboard -> callback.onCopyTextToClipboardRequested(url!!)
                    R.id.miShare -> callback.onShareUrlRequested(url!!)
                }
                true
            }

            it.setOnDismissListener {
                vb.flWebViewContainer.removeView(anchor)
                linkActionsMenu = null
            }
            it.show()
        }
    }

    override fun askUserAndOpenInExternalApp(url: String, intent: Intent) {
        if (openUrlInExternalAppDialog != null) {
            return
        }
        openUrlInExternalAppDialog = AlertDialog.Builder(this)
            .setTitle(R.string.site_asks_to_open_unknown_url)
            .setMessage(getString(R.string.site_asks_to_open_unknown_url_message) + "\n\n" + url)
            .setPositiveButton(R.string.yes) { _, _ ->
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this@MainActivity, R.string.external_app_open_error, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.no, null)
            .setOnDismissListener {
                openUrlInExternalAppDialog = null
            }
            .show()
    }
}
