package com.phlox.tvwebbrowser.activity.main

import android.util.Log
import com.phlox.tvwebbrowser.AppContext
import com.phlox.tvwebbrowser.Config
import com.phlox.tvwebbrowser.TVBro
import com.phlox.tvwebbrowser.model.HostConfig
import com.phlox.tvwebbrowser.model.WebTabState
import com.phlox.tvwebbrowser.repository.HostsRepository
import com.phlox.tvwebbrowser.repository.Repositories
import com.phlox.tvwebbrowser.repository.TabsRepository
import com.phlox.tvwebbrowser.utils.Utils
import com.phlox.tvwebbrowser.utils.activemodel.ActiveModel
import com.phlox.tvwebbrowser.utils.observable.ObservableList
import com.phlox.tvwebbrowser.utils.observable.ObservableValue
import com.phlox.tvwebbrowser.webengine.WebEngineWindowProviderCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

/**
 * The view side of tab switching: materialises a tab's engine view and supplies the window it is
 * attached to, so [TabsModel] never handles a `ViewGroup` itself. Implemented by the main screen.
 */
interface TabViewHost {
    /** Materialises [tab]'s engine view; false when it cannot be created (the switch is aborted). */
    fun createView(tab: WebTabState): Boolean

    /** Attaches [tab]'s engine to this host's window. */
    fun attachEngineToWindow(
        tab: WebTabState,
        callback: WebEngineWindowProviderCallback,
    )
}

/**
 * The open tabs of the current browsing mode.
 *
 * Every dependency — the data-layer gateways, the settings and the application services it needs
 * (the private files directory, the cache directory, the connectivity check) — is a constructor
 * parameter with a production default, so a test can hand it in-memory repositories and its own
 * configuration;
 * [com.phlox.tvwebbrowser.utils.activemodel.ActiveModelsRepository] builds the no-argument
 * constructor Kotlin synthesises.
 */
class TabsModel(
    private val tabsRepository: TabsRepository = Repositories.tabs,
    private val hostsRepository: HostsRepository = Repositories.hosts,
    private val config: Config = AppContext.provideConfig(),
    private val filesDir: () -> File? = { AppContext.get().filesDir },
    private val cacheDir: () -> File? = { AppContext.get().cacheDir },
    private val networkAvailable: () -> Boolean = { Utils.isNetworkConnected(TVBro.instance) },
) : ActiveModel() {
    companion object {
        val TAG: String = TabsModel::class.java.simpleName
    }

    var loaded = false
    val currentTab = ObservableValue<WebTabState?>(null)
    val tabsStates = ObservableList<WebTabState>()
    private var incognitoMode = config.incognitoMode

    init {
        tabsStates.subscribe({
            // auto-update positions on any list change
            var positionsChanged = false
            tabsStates.forEachIndexed { index, webTabState ->
                if (webTabState.position != index) {
                    webTabState.position = index
                    positionsChanged = true
                }
            }
            if (positionsChanged) {
                val tabsListClone = listOf(*tabsStates.toTypedArray())
                modelScope.launch(Dispatchers.Main) {
                    tabsRepository.updatePositions(tabsListClone)
                }
            }
        }, false)
    }

    fun loadState() =
        modelScope.launch(Dispatchers.Main) {
            if (loaded) {
                // check is incognito mode changed
                if (incognitoMode != config.incognitoMode) {
                    incognitoMode = config.incognitoMode
                    loaded = false
                } else {
                    return@launch
                }
            }
            tabsStates.replaceAll(tabsRepository.all(config.incognitoMode))
            loaded = true
        }

    fun discardSavedState() =
        modelScope.launch(Dispatchers.Main) {
            loaded = true
            tabsRepository.deleteAll(config.incognitoMode)
            withContext(Dispatchers.IO) {
                tabWvStatesDir()?.deleteRecursively()
            }
        }

    private fun tabWvStatesDir() = filesDir()?.let { File(it, WebTabState.TAB_WVSTATES_DIR) }

    suspend fun saveTab(tab: WebTabState) {
        if (tab.selected) {
            tabsRepository.unselectAll(config.incognitoMode)
        }
        withContext(Dispatchers.IO) {
            tab.saveWebViewStateToFile(filesDir())
        }
        if (tab.id != 0L) {
            tabsRepository.update(tab)
        } else {
            tab.id = tabsRepository.insert(tab)
        }
    }

    /**
     * Persists [tab] without blocking the caller. The write is durable once the coroutine completes,
     * so the screen pause path uses this instead of blocking the main thread on Room and the tab
     * state file.
     */
    fun saveTabInBackground(tab: WebTabState) {
        modelScope.launch { saveTab(tab) }
    }

    /**
     * The "close tab" intent (the tab panel's X, the Close tab menu entry and the close-tab
     * shortcut all forward here): [tab] goes away and the selection moves on with it — to its
     * left neighbour, or to a fresh tab (opened through [openNewTab]) when it was the only one.
     * Materialising an engine view for the tab that becomes current is view business, hence the
     * two callbacks.
     */
    fun onCloseTab(
        tab: WebTabState,
        switchTo: (WebTabState) -> Unit,
        openNewTab: () -> Unit,
    ) {
        val position = tabsStates.indexOf(tab)
        if (currentTab.value == tab) {
            currentTab.value = null
        }
        when {
            tabsStates.size == 1 -> openNewTab()
            position > 0 -> switchTo(tabsStates[position - 1])
            else -> switchTo(tabsStates[position + 1])
        }
        removeTab(tab)
    }

    private fun removeTab(tab: WebTabState) {
        tab.webEngine.onDetachFromWindow(completely = true, destroyTab = true)
        tabsStates.remove(tab)
        modelScope.launch(Dispatchers.Main) {
            tabsRepository.delete(tab)
            launch { tab.removeFiles(filesDir(), cacheDir()) }
        }
    }

    /**
     * The "toggle ad blocking for this tab" intent: the tab's own override becomes the opposite of
     * what is currently effective for it — the override when it has one, the browser-wide default
     * ([defaultEnabled]) otherwise. The new value is returned so the caller can push it into the
     * tab's engine and repaint.
     */
    fun onToggleAdBlock(
        tab: WebTabState,
        defaultEnabled: Boolean,
    ): Boolean {
        val newState = !(tab.adblock ?: defaultEnabled)
        tab.adblock = newState
        return newState
    }

    /**
     * Drops every tab of the current browsing mode: the list is cleared (and the selection with
     * it — nothing is selected when no tab exists), the rows are deleted from the repository and
     * the tab files go away in the background.
     *
     * Dispatched on [Dispatchers.Main.immediate]: the tab panel's "Close all" asks for a fresh tab
     * right after this call without joining the returned job, so the clear has to be applied before
     * that request runs — a plain [Dispatchers.Main] launch would execute afterwards and drop the
     * new tab as well. The durable work (repository, files) stays in the returned job.
     */
    fun onCloseAllTabs() =
        modelScope.launch(Dispatchers.Main.immediate) {
            val tabsClone = ArrayList(tabsStates)
            tabsStates.clear()
            currentTab.value = null
            tabsRepository.deleteAll(config.incognitoMode)
            withContext(Dispatchers.IO) {
                tabsClone.forEach { it.removeFiles(filesDir(), cacheDir()) }
            }
        }

    fun onDetachActivity() {
        for (tab in tabsStates) {
            tab.webEngine.onDetachFromWindow(completely = true, destroyTab = false)
        }
    }

    /**
     * The "open a tab" intent (a link's new-tab request, a popup the user allowed): [tab] is
     * inserted at [index] and the caller then materialises its engine view and switches to it. The
     * list observer updates the stored positions.
     */
    fun onOpenTab(
        tab: WebTabState,
        index: Int,
    ) {
        tabsStates.add(index.coerceIn(0, tabsStates.size), tab)
    }

    fun changeTab(
        newTab: WebTabState,
        viewHost: TabViewHost,
        webEngineWindowProviderCallback: WebEngineWindowProviderCallback,
    ) {
        if (currentTab.value == newTab && newTab.webEngine.getView() != null) return
        if (currentTab.value != newTab) {
            tabsStates.forEach {
                it.selected = false
            }
            currentTab.value?.apply {
                webEngine.onDetachFromWindow(completely = false, destroyTab = false)
                onPause()
                modelScope.launch { saveTab(this@apply) }
            }

            newTab.selected = true
            currentTab.value = newTab
        }
        var needReloadUrl = false
        if (newTab.webEngine.getView() == null) {
            if (!viewHost.createView(newTab)) {
                return
            }
            needReloadUrl = !newTab.restoreWebView(filesDir())
        }
        viewHost.attachEngineToWindow(newTab, webEngineWindowProviderCallback)
        if (needReloadUrl) {
            newTab.webEngine.loadUrl(newTab.url)
        }
        newTab.webEngine.setNetworkAvailable(networkAvailable())
    }

    /**
     * Loads [url] in the current tab — the model intent behind the address box and the external
     * "open in browser" entries. Returns false when no tab is selected, so the caller can fall back
     * to opening one.
     */
    fun navigateTo(url: String): Boolean {
        val tab = currentTab.value ?: return false
        onPageUrlUpdated(tab, url, null)
        tab.webEngine.loadUrl(url)
        return true
    }

    /** The engine is about to load [url] in [tab]; remembered as the redirect-blocker's fallback. */
    fun onNavigationRequested(
        tab: WebTabState,
        url: String,
    ) {
        tab.lastLoadingUrl = url
    }

    /**
     * Page lifecycle events reported by the per-tab engine callback. The engine knows the final url
     * after redirects, so its value wins and the url the callback reported is only a fallback; the
     * blocked-ad/popup counters restart with every new document.
     */
    fun onPageStarted(
        tab: WebTabState,
        engineUrl: String?,
        callbackUrl: String?,
    ) {
        onPageUrlUpdated(tab, engineUrl, callbackUrl)
        tab.blockedAds = 0
        tab.blockedPopups = 0
        // Warm the per-tab host config off the main thread, so the synchronous popup callback
        // (WebEngineCallback.shouldBlockNewWindow) resolves it from memory instead of blocking the
        // UI thread on a database read. findHostConfig only reads.
        modelScope.launch { findHostConfig(tab) }
    }

    fun onPageFinished(
        tab: WebTabState,
        engineUrl: String?,
        callbackUrl: String?,
    ) {
        onPageUrlUpdated(tab, engineUrl, callbackUrl)
        // A settled page invalidates the thumbnails of the other tabs (the one that just finished
        // keeps its own: it is about to be re-rendered right away).
        tabsStates.onEach { if (it != tab) it.thumbnail = null }
    }

    /** The engine knows the document title; the tabs panel repaints the row from the new value. */
    fun onPageTitleUpdated(
        tab: WebTabState,
        title: String,
    ) {
        tab.title = title
    }

    fun onAdBlocked(tab: WebTabState) {
        tab.blockedAds++
    }

    fun onDialogBlocked(tab: WebTabState) {
        tab.blockedPopups++
    }

    /** The engine reports a page-scale change; kept on the tab so it survives switching. */
    fun onScaleChanged(
        tab: WebTabState,
        newScale: Float,
    ) {
        tab.scale = newScale
    }

    private fun onPageUrlUpdated(
        tab: WebTabState,
        engineUrl: String?,
        callbackUrl: String?,
    ) {
        val url = engineUrl ?: callbackUrl
        if (url != null) {
            tab.url = url
        }
    }

    /** The stored per-host config of [tab]'s current url, or null while the host has none. */
    suspend fun findHostConfig(tab: WebTabState): HostConfig? = resolveHostConfig(tab, createIfNotFound = false)

    /** Like [findHostConfig], but stores a fresh config when the host does not have one yet. */
    suspend fun getOrCreateHostConfig(tab: WebTabState): HostConfig? = resolveHostConfig(tab, createIfNotFound = true)

    private suspend fun resolveHostConfig(
        tab: WebTabState,
        createIfNotFound: Boolean,
    ): HostConfig? {
        Log.d(WebTabState.TAG, "findOrCreateHostConfig")
        val currentHostName =
            try {
                URL(tab.url).host
            } catch (e: Exception) {
                Log.w(WebTabState.TAG, "Can not parse current url host: $e")
                return null
            }
        var hostConfig = tab.cachedHostConfig
        if (hostConfig == null || hostConfig.hostName != currentHostName) {
            hostConfig = hostsRepository.findByHostName(currentHostName)
            if (hostConfig == null && createIfNotFound) {
                hostConfig = HostConfig(currentHostName)
                hostConfig.id = hostsRepository.upsert(hostConfig)
            }
            tab.cachedHostConfig = hostConfig
        }
        return hostConfig
    }

    suspend fun changePopupBlockingLevel(
        newLevel: Int,
        tab: WebTabState,
    ) {
        val hostConfig = getOrCreateHostConfig(tab) ?: return
        hostConfig.popupBlockLevel = newLevel
        hostsRepository.update(hostConfig)
    }

    /**
     * Moves the tab at [from] to [to]. The tab panel asks for this instead of swapping the list
     * itself, so the list mutation (and the position persistence it triggers) stays in the model.
     */
    fun moveTab(
        from: Int,
        to: Int,
    ) {
        if (from !in tabsStates.indices || to !in tabsStates.indices || from == to) return
        tabsStates.swap(from, to)
    }
}
