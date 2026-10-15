package com.phlox.tvwebbrowser.activity.main

import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.phlox.tvwebbrowser.AppContext
import com.phlox.tvwebbrowser.TVBro
import com.phlox.tvwebbrowser.model.HostConfig
import com.phlox.tvwebbrowser.model.WebTabState
import com.phlox.tvwebbrowser.repository.Repositories
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

class TabsModel : ActiveModel() {
    companion object {
        var TAG: String = TabsModel::class.java.simpleName
    }

    var loaded = false
    val currentTab = ObservableValue<WebTabState?>(null)
    val tabsStates = ObservableList<WebTabState>()
    private val config = AppContext.provideConfig()
    private var incognitoMode = config.incognitoMode

    // Data-layer gateways (see the repository package): the model must not open Room itself.
    private val tabsRepository = Repositories.tabs
    private val hostsRepository = Repositories.hosts

    init {
        tabsStates.subscribe({
            //auto-update positions on any list change
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

    fun loadState() = modelScope.launch(Dispatchers.Main) {
        if (loaded) {
            //check is incognito mode changed
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

    fun discardSavedState() = modelScope.launch(Dispatchers.Main) {
        loaded = true
        tabsRepository.deleteAll(config.incognitoMode)
        withContext(Dispatchers.IO) {
            tabWvStatesDir()?.deleteRecursively()
        }
    }

    private fun tabWvStatesDir() =
        AppContext.get().filesDir?.let { File(it, WebTabState.TAB_WVSTATES_DIR) }

    suspend fun saveTab(tab: WebTabState) {
        if (tab.selected) {
            tabsRepository.unselectAll(config.incognitoMode)
        }
        withContext(Dispatchers.IO) {
            tab.saveWebViewStateToFile()
        }
        if (tab.id != 0L) {
            tabsRepository.update(tab)
        } else {
            tab.id = tabsRepository.insert(tab)
        }
    }

    /**
     * The "close tab" intent (the tab panel's X, the Close tab menu entry and the close-tab
     * shortcut all forward here): [tab] goes away and the selection moves on with it — to its
     * left neighbour, or to a fresh tab (opened through [openNewTab]) when it was the only one.
     * Materialising an engine view for the tab that becomes current is view business, hence the
     * two callbacks.
     */
    fun onCloseTab(tab: WebTabState, switchTo: (WebTabState) -> Unit, openNewTab: () -> Unit) {
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
            launch { tab.removeFiles() }
        }
    }

    /**
     * Drops every tab of the current browsing mode: the list is cleared (and the selection with
     * it — nothing is selected when no tab exists), the rows are deleted from the repository and
     * the tab files go away in the background.
     */
    fun onCloseAllTabs() = modelScope.launch(Dispatchers.Main) {
        val tabsClone = ArrayList(tabsStates)
        tabsStates.clear()
        currentTab.value = null
        tabsRepository.deleteAll(config.incognitoMode)
        withContext(Dispatchers.IO) {
            tabsClone.forEach { it.removeFiles() }
        }
    }

    fun onDetachActivity() {
        for (tab in tabsStates) {
            tab.webEngine.onDetachFromWindow(completely = true, destroyTab = false)
        }
    }

    fun changeTab(
        newTab: WebTabState,
        webViewProvider: (tab: WebTabState) -> View?,
        webViewParent: ViewGroup,
        webEngineWindowProviderCallback: WebEngineWindowProviderCallback
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
        var wv = newTab.webEngine.getView()
        var needReloadUrl = false
        if (wv == null) {
            wv = webViewProvider(newTab)
            if (wv == null) {
                return
            }
            needReloadUrl = !newTab.restoreWebView()
        }
        newTab.webEngine.onAttachToWindow(webEngineWindowProviderCallback, webViewParent)
        if (needReloadUrl) {
            newTab.webEngine.loadUrl(newTab.url)
        }
        newTab.webEngine.setNetworkAvailable(Utils.isNetworkConnected(TVBro.instance))
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
    fun onNavigationRequested(tab: WebTabState, url: String) {
        tab.lastLoadingUrl = url
    }

    /**
     * Page lifecycle events reported by the per-tab engine callback. The engine knows the final url
     * after redirects, so its value wins and the url the callback reported is only a fallback; the
     * blocked-ad/popup counters restart with every new document.
     */
    fun onPageStarted(tab: WebTabState, engineUrl: String?, callbackUrl: String?) {
        onPageUrlUpdated(tab, engineUrl, callbackUrl)
        tab.blockedAds = 0
        tab.blockedPopups = 0
    }

    fun onPageFinished(tab: WebTabState, engineUrl: String?, callbackUrl: String?) {
        onPageUrlUpdated(tab, engineUrl, callbackUrl)
        // A settled page invalidates the thumbnails of the other tabs (the one that just finished
        // keeps its own: it is about to be re-rendered right away).
        tabsStates.onEach { if (it != tab) it.thumbnail = null }
    }

    /** The engine knows the document title; the tabs panel repaints the row from the new value. */
    fun onPageTitleUpdated(tab: WebTabState, title: String) {
        tab.title = title
    }

    fun onAdBlocked(tab: WebTabState) {
        tab.blockedAds++
    }

    fun onDialogBlocked(tab: WebTabState) {
        tab.blockedPopups++
    }

    /** The engine reports a page-scale change; kept on the tab so it survives switching. */
    fun onScaleChanged(tab: WebTabState, newScale: Float) {
        tab.scale = newScale
    }

    private fun onPageUrlUpdated(tab: WebTabState, engineUrl: String?, callbackUrl: String?) {
        val url = engineUrl ?: callbackUrl
        if (url != null) {
            tab.url = url
        }
    }

    suspend fun findHostConfig(tab: WebTabState, createIfNotFound: Boolean): HostConfig? {
        Log.d(WebTabState.TAG, "findOrCreateHostConfig")
        val currentHostName = try {
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

    suspend fun changePopupBlockingLevel(newLevel: Int, tab: WebTabState) {
        val hostConfig = findHostConfig(tab,true) ?: return
        hostConfig.popupBlockLevel = newLevel
        hostsRepository.update(hostConfig)
    }

    /**
     * Moves the tab at [from] to [to]. The tab panel asks for this instead of swapping the list
     * itself, so the list mutation (and the position persistence it triggers) stays in the model.
     */
    fun moveTab(from: Int, to: Int) {
        if (from !in tabsStates.indices || to !in tabsStates.indices || from == to) return
        tabsStates.swap(from, to)
    }
}