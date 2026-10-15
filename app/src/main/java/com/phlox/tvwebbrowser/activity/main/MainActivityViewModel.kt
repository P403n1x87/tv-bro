package com.phlox.tvwebbrowser.activity.main

import android.os.Build
import android.util.Log
import android.webkit.WebView
import com.phlox.tvwebbrowser.AppContext
import com.phlox.tvwebbrowser.BuildConfig
import com.phlox.tvwebbrowser.Config
import com.phlox.tvwebbrowser.TVBro
import com.phlox.tvwebbrowser.model.FavoriteItem
import com.phlox.tvwebbrowser.model.HistoryItem
import com.phlox.tvwebbrowser.model.HomePageLink
import com.phlox.tvwebbrowser.model.WebTabState
import com.phlox.tvwebbrowser.repository.FavoritesRepository
import com.phlox.tvwebbrowser.repository.HistoryRepository
import com.phlox.tvwebbrowser.repository.Repositories
import com.phlox.tvwebbrowser.utils.UpdateChecker
import com.phlox.tvwebbrowser.utils.activemodel.ActiveModel
import com.phlox.tvwebbrowser.utils.deleteDirectory
import com.phlox.tvwebbrowser.utils.observable.ObservableList
import com.phlox.tvwebbrowser.utils.observable.ObservableValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.util.Calendar
import java.util.Locale

/**
 * Presentation model of the main screen.
 *
 * The data-layer gateways are constructor parameters with production defaults, so a test can hand
 * it repositories over an in-memory database; [com.phlox.tvwebbrowser.utils.activemodel.ActiveModelsRepository]
 * builds the no-argument constructor it synthesises.
 */
class MainActivityViewModel(
    private val favorites: FavoritesRepository = Repositories.favorites,
    private val history: HistoryRepository = Repositories.history,
): ActiveModel() {
    companion object {
        var TAG: String = MainActivityViewModel::class.java.simpleName
        const val WEB_VIEW_DATA_FOLDER = "app_webview"
        const val WEB_VIEW_CACHE_FOLDER = "WebView"
        const val WEB_VIEW_DATA_BACKUP_DIRECTORY_SUFFIX = "_backup"
        const val INCOGNITO_DATA_DIRECTORY_SUFFIX = "incognito"
    }

    var loaded = false
    var lastHistoryItem: HistoryItem? = null
    private var lastHistoryItemSaveJob: Job? = null
    val homePageLinks = ObservableList<HomePageLink>()
    val homePageHistoryLinks = ObservableList<HomePageLink>()
    val config = AppContext.provideConfig()

    /** Stored bookmarks, owned here so the view only renders them. */
    val bookmarks = ObservableList<FavoriteItem>()
    /** Whether the current page is bookmarked; drives the toolbar star and the panel's first row. */
    val currentPageBookmarked = ObservableValue(false)

    fun loadState() = modelScope.launch(Dispatchers.Main) {
        Log.d(TAG, "loadState")
        if (loaded) return@launch
        // Purge any update APK left in the cache by a previous session (after a download, the file
        // has to linger until the installer has read it, so we clean up on the next launch).
        withContext(Dispatchers.IO) {
            UpdateChecker.clearTempFilesIfAny(TVBro.instance)
        }
        checkVersionCodeAndRunMigrations()
        initHistory()
        loadHomePageLinks()
        loaded = true
    }

    private suspend fun checkVersionCodeAndRunMigrations() {
        Log.d(TAG, "checkVersionCodeAndRunMigrations")
        if (config.appVersionCodeMark != BuildConfig.VERSION_CODE) {
            Log.i(TAG, "App version code changed from ${config.appVersionCodeMark} to ${BuildConfig.VERSION_CODE}")
            config.appVersionCodeMark = BuildConfig.VERSION_CODE
            withContext(Dispatchers.IO) {
                UpdateChecker.clearTempFilesIfAny(TVBro.instance)
            }
        }
    }

    private suspend fun initHistory() {
        Log.d(TAG, "initHistory")
        val count = history.count()
        if (count > 5000) {
            val c = Calendar.getInstance()
            c.add(Calendar.MONTH, -3)
            history.deleteOlderThan(c.time.time)
        }
        try {
            val result = history.latest()
            if (result.isNotEmpty()) {
                lastHistoryItem = result[0]
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun loadHomePageLinks() {
        Log.d(TAG, "loadHomePageLinks")
        val config = AppContext.provideConfig()
        if (config.homePageMode == Config.HomePageMode.HOME_PAGE) {
            homePageLinks.replaceAll(
                favorites.allBookmarks()
                    .map { HomePageLink.fromBookmarkItem(it) })
            homePageHistoryLinks.replaceAll(
                history.latest(8)
                    .map { HomePageLink.fromHistoryItem(it) })
        }
    }

    fun logVisitedHistory(title: String?, url: String, faviconHash: String?) {
        Log.d(TAG, "logVisitedHistory: $url")
        if ((url == lastHistoryItem?.url) || Config.isHomePageUrl(url) || !url.startsWith("http", true)) {
            return
        }

        val now = System.currentTimeMillis()
        val minVisitedInterval = 5000L //5 seconds

        lastHistoryItem?.let {
            if ((!it.saved) && (it.time + minVisitedInterval) > now) {
                lastHistoryItemSaveJob?.cancel()
            }
        }

        val item = HistoryItem()
        item.url = url
        item.title = title ?: ""
        item.time = now
        item.favicon = faviconHash
        lastHistoryItem = item
        lastHistoryItemSaveJob = modelScope.launch(Dispatchers.Main) {
            delay(minVisitedInterval)
            // Replace any older entry for the same URL instead of appending a duplicate, so a site
            // shows up once in history with its latest visit time (and storage stays bounded by
            // distinct URLs rather than repeat visits). Done inside the debounced job so a cancelled
            // visit never deletes the previously saved row.
            history.deleteByUrl(item.url)
            item.id = history.add(item)
            item.saved = true
        }
    }

    fun onTabTitleUpdated(tab: WebTabState) {
        Log.d(TAG, "onTabTitleUpdated: ${tab.url} ${tab.title}")
        if (AppContext.provideConfig().incognitoMode) return
        val lastHistoryItem = lastHistoryItem ?: return
        if (tab.url == lastHistoryItem.url) {
            lastHistoryItem.title = tab.title
            if (lastHistoryItem.saved) {
                modelScope.launch(Dispatchers.Main) {
                    history.updateTitle(lastHistoryItem.id, lastHistoryItem.title)
                }
            }
        }
    }

    /**
     * Flips the incognito mode — the intent behind the toolbar/menu entry and the cleanup the
     * "close app" flow runs when it happens inside an incognito process.
     *
     * Leaving the mode wipes the previous session first, in a fixed order the model owns:
     * the engine's session data, then the open tabs (closed by the caller through
     * [clearOpenTabs], since the tab model is not a dependency of this one), then the isolated
     * WebView data directories. The engine-specific work is injected because only the view layer
     * knows which engine factory is in use and how to reach its windows.
     *
     * @return the mode the process has to be switched into (unchanged when no process switch
     * follows, e.g. the close-app flow, which only needs the cleanup).
     */
    suspend fun toggleIncognitoMode(
        clearEngineSession: suspend () -> Unit,
        clearOpenTabs: suspend () -> Unit,
    ): Boolean {
        val becomingIncognitoMode = !config.incognitoMode
        if (!becomingIncognitoMode) {
            if (!config.isWebEngineGecko()) {
                clearEngineSession()
            }
            clearOpenTabs()
            if (!config.isWebEngineGecko()) {
                clearIncognitoData().join()
            }
        }
        config.incognitoMode = becomingIncognitoMode
        return becomingIncognitoMode
    }

    fun prepareSwitchToIncognito() {
        Log.d(TAG, "prepareSwitchToIncognito")
        if (AppContext.provideConfig().isWebEngineGecko()) return
        //to isolate incognito mode data:
        //in api >= 28 we just use another directory for WebView data
        //on earlier apis we backup-ing existing WebView data directory
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val incognitoWebViewData = File(
                TVBro.instance.filesDir.parentFile!!.absolutePath +
                        "/" + WEB_VIEW_DATA_FOLDER + "_" + INCOGNITO_DATA_DIRECTORY_SUFFIX
            )
            if (incognitoWebViewData.exists()) {
                Log.i(TAG, "Looks like we already in incognito mode")
                return
            }
            WebView.setDataDirectorySuffix(INCOGNITO_DATA_DIRECTORY_SUFFIX)
        } else {
            val webViewData = File(
                TVBro.instance.filesDir.parentFile!!.absolutePath +
                        "/" + WEB_VIEW_DATA_FOLDER
            )
            val backupedWebViewData = File(
                TVBro.instance.filesDir.parentFile!!.absolutePath +
                        "/" + WEB_VIEW_DATA_FOLDER + WEB_VIEW_DATA_BACKUP_DIRECTORY_SUFFIX
            )
            if (backupedWebViewData.exists()) {
                Log.i(TAG, "Looks like we already in incognito mode")
                return
            }
            webViewData.renameTo(backupedWebViewData)
            val webViewCache =
                File(TVBro.instance.cacheDir.absolutePath + "/" + WEB_VIEW_CACHE_FOLDER)
            val backupedWebViewCache = File(
                TVBro.instance.cacheDir.absolutePath + "/" + WEB_VIEW_CACHE_FOLDER +
                        WEB_VIEW_DATA_BACKUP_DIRECTORY_SUFFIX
            )
            webViewCache.renameTo(backupedWebViewCache)
        }
    }

    fun clearIncognitoData() = modelScope.launch(Dispatchers.IO) {
        Log.d(TAG, "clearIncognitoData")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val webViewData = File(
                TVBro.instance.filesDir.parentFile!!.absolutePath +
                        "/" + WEB_VIEW_DATA_FOLDER + "_" + INCOGNITO_DATA_DIRECTORY_SUFFIX
            )
            deleteDirectory(webViewData)
            var webViewCache =
                File(
                    TVBro.instance.cacheDir.absolutePath + "/" + WEB_VIEW_CACHE_FOLDER +
                            "_" + INCOGNITO_DATA_DIRECTORY_SUFFIX
                )
            if (!webViewCache.exists()) {
                webViewCache = File(
                    TVBro.instance.cacheDir.absolutePath + "/" +
                            WEB_VIEW_CACHE_FOLDER.lowercase(Locale.getDefault()) +
                            "_" + INCOGNITO_DATA_DIRECTORY_SUFFIX
                )
            }
            deleteDirectory(webViewCache)
        } else {
            val webViewData = File(
                TVBro.instance.filesDir.parentFile!!.absolutePath +
                        "/" + WEB_VIEW_DATA_FOLDER
            )
            deleteDirectory(webViewData)
            val webViewCache =
                File(TVBro.instance.cacheDir.absolutePath + "/" + WEB_VIEW_CACHE_FOLDER)
            deleteDirectory(webViewCache)

            val backupedWebViewData = File(
                TVBro.instance.filesDir.parentFile!!.absolutePath +
                        "/" + WEB_VIEW_DATA_FOLDER + WEB_VIEW_DATA_BACKUP_DIRECTORY_SUFFIX
            )
            backupedWebViewData.renameTo(webViewData)
            val backupedWebViewCache = File(
                TVBro.instance.cacheDir.absolutePath + "/" + WEB_VIEW_CACHE_FOLDER +
                        WEB_VIEW_DATA_BACKUP_DIRECTORY_SUFFIX
            )
            backupedWebViewCache.renameTo(webViewCache)
        }
    }

    override fun onClear() {

    }

    fun removeHomePageLink(bookmark: HomePageLink) = modelScope.launch {
        homePageLinks.remove(bookmark)
        bookmark.favoriteId?.let {
            favorites.delete(it)
        }
    }

    fun deleteBookmark(favoriteId: Long) = modelScope.launch {
        favorites.delete(favoriteId)
        val index = homePageLinks.indexOfFirst { it.favoriteId == favoriteId }
        if (index != -1) {
            homePageLinks.removeAt(index)
        }
    }

    /**
     * Reads the stored bookmarks into [bookmarks]; the view reacts to the observable, so it never
     * queries the repository (or Room) itself.
     */
    suspend fun loadBookmarks() {
        bookmarks.replaceAll(favorites.allBookmarks())
    }

    /**
     * Recomputes [currentPageBookmarked] for [url]. Called on page load, tab switch and after any
     * bookmark mutation; the toolbar star and the panel's "Add this page" row follow the observable.
     */
    suspend fun refreshBookmarkState(url: String?) {
        currentPageBookmarked.value = !url.isNullOrBlank() && favorites.isBookmarked(url)
    }

    suspend fun getBookmarkByUrl(url: String): FavoriteItem? = favorites.byUrl(url)

    suspend fun getBookmarkById(id: Long): FavoriteItem? = favorites.byId(id)

    fun onHomePageLinkEdited(item: FavoriteItem) = modelScope.launch {
        if (item.id == 0L) {
            val lastInsertRowId = favorites.add(item)
            item.id = lastInsertRowId
            homePageLinks.add(HomePageLink.fromBookmarkItem(item))
        } else {
            favorites.update(item)
            val index = homePageLinks.indexOfFirst { it.favoriteId == item.id }
            if (index != -1) {
                homePageLinks[index] = HomePageLink.fromBookmarkItem(item)
            }
        }
    }

    fun markBookmarkRecommendationAsUseful(bookmarkOrder: Int) {
        val link = homePageLinks.find { it.order == bookmarkOrder } ?: return
        val favoriteId = link.favoriteId ?: return
        if (link.validUntil == null) return
        modelScope.launch {
            favorites.markAsUseful(favoriteId)
        }
    }
}