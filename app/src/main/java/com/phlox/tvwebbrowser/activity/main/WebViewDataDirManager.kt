package com.phlox.tvwebbrowser.activity.main

import android.os.Build
import android.util.Log
import android.webkit.WebView
import com.phlox.tvwebbrowser.AppContext
import com.phlox.tvwebbrowser.utils.deleteDirectory
import java.io.File
import java.util.Locale

/**
 * The WebView data/cache directories an incognito session runs on.
 *
 * The engine keeps its store next to the app's private files, so an incognito session moves the live
 * directories aside before it starts and restores them once the user leaves the mode. Gecko has its
 * own store and needs none of this (the caller guards on it). Behind an interface so
 * [MainActivityViewModel] can be exercised without touching the real filesystem.
 */
interface WebViewDataDirManager {
    /** Moves the live data/cache directories aside so the session gets a clean, isolated store. */
    fun prepareIncognitoSession()

    /** Wipes the isolated directories and restores the ones [prepareIncognitoSession] moved aside. */
    fun clearIncognitoData()
}

/**
 * The default [WebViewDataDirManager]: the app's real private data/cache directories.
 *
 * The directory providers are lazy on purpose: the manager may be built (e.g. as a default
 * constructor argument) before `AppContext` is initialised, so the real directories are only read
 * when the session methods run.
 */
class DefaultWebViewDataDirManager(
    private val appFilesDir: () -> File = { AppContext.get().filesDir },
    private val appCacheDir: () -> File = { AppContext.get().cacheDir },
) : WebViewDataDirManager {
    companion object {
        private val TAG = DefaultWebViewDataDirManager::class.java.simpleName
        const val DATA_FOLDER = "app_webview"
        const val CACHE_FOLDER = "WebView"
        const val BACKUP_SUFFIX = "_backup"
        const val INCOGNITO_SUFFIX = "incognito"
    }

    private fun dataDir(name: String) = File(appFilesDir().parentFile, name)
    private fun cacheDir(name: String) = File(appCacheDir(), name)

    override fun prepareIncognitoSession() {
        Log.d(TAG, "prepareIncognitoSession")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val incognitoData = dataDir(DATA_FOLDER + "_" + INCOGNITO_SUFFIX)
            if (incognitoData.exists()) {
                // The suffix can only be set once per process, so this is already prepared.
                Log.i(TAG, "Looks like we already in incognito mode")
                return
            }
            WebView.setDataDirectorySuffix(INCOGNITO_SUFFIX)
        } else {
            val backup = dataDir(DATA_FOLDER + BACKUP_SUFFIX)
            if (backup.exists()) {
                Log.i(TAG, "Looks like we already in incognito mode")
                return
            }
            dataDir(DATA_FOLDER).renameTo(backup)
            cacheDir(CACHE_FOLDER).renameTo(cacheDir(CACHE_FOLDER + BACKUP_SUFFIX))
        }
    }

    override fun clearIncognitoData() {
        Log.d(TAG, "clearIncognitoData")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            deleteDirectory(dataDir(DATA_FOLDER + "_" + INCOGNITO_SUFFIX))
            var webViewCache = cacheDir(CACHE_FOLDER + "_" + INCOGNITO_SUFFIX)
            if (!webViewCache.exists()) {
                webViewCache = cacheDir(
                    CACHE_FOLDER.lowercase(Locale.getDefault()) + "_" + INCOGNITO_SUFFIX
                )
            }
            deleteDirectory(webViewCache)
        } else {
            deleteDirectory(dataDir(DATA_FOLDER))
            deleteDirectory(cacheDir(CACHE_FOLDER))
            dataDir(DATA_FOLDER + BACKUP_SUFFIX).renameTo(dataDir(DATA_FOLDER))
            cacheDir(CACHE_FOLDER + BACKUP_SUFFIX).renameTo(cacheDir(CACHE_FOLDER))
        }
    }
}
