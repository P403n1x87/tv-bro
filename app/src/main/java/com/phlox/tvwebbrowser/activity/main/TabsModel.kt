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
import kotlinx.coroutines.runBlocking
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

    fun onCloseTab(tab: WebTabState) {
        tab.webEngine.onDetachFromWindow(completely = true, destroyTab = true)
        tabsStates.remove(tab)
        modelScope.launch(Dispatchers.Main) {
            tabsRepository.delete(tab)
            launch { tab.removeFiles() }
        }
    }

    fun onCloseAllTabs() = modelScope.launch(Dispatchers.Main) {
        val tabsClone = ArrayList(tabsStates)
        tabsStates.clear()
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