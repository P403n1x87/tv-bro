package com.phlox.tvwebbrowser.activity.main

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.view.View
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import com.phlox.tvwebbrowser.Config
import com.phlox.tvwebbrowser.R
import com.phlox.tvwebbrowser.TVBro
import com.phlox.tvwebbrowser.domain.NavigationPolicy
import com.phlox.tvwebbrowser.domain.NewTabPositionPolicy
import com.phlox.tvwebbrowser.domain.PopupPolicy
import com.phlox.tvwebbrowser.model.Download
import com.phlox.tvwebbrowser.model.HomePageLink
import com.phlox.tvwebbrowser.model.WebTabState
import com.phlox.tvwebbrowser.utils.DownloadUtils
import com.phlox.tvwebbrowser.utils.Utils
import com.phlox.tvwebbrowser.webengine.WebEngine
import com.phlox.tvwebbrowser.webengine.WebEngineWindowProviderCallback
import com.phlox.tvwebbrowser.widgets.cursor.CursorDrawerDelegate
import kotlinx.coroutines.runBlocking
import java.io.InputStream

/**
 * The per-tab engine adapter: the browser engine reports its page events here and the adapter
 * decides what they mean for the app.
 *
 * It is a plain class now, not an inner class of [MainActivity]: the models it needs are injected,
 * and the view work goes through [WebEngineCallbackHost]. What is left here is the engine-facing
 * policy — url routing and the automatic-redirect/popup decisions (delegated to `domain/` where it
 * is pure), the new-tab position, the download filename — plus the page-lifecycle bookkeeping the
 * model owns. It never touches `MainActivity`'s view fields directly.
 */
class WebEngineCallback(
    private val host: WebEngineCallbackHost,
    val tab: WebTabState,
    private val tabsModel: TabsModel,
    private val adblockModel: AdblockModel,
    private val settingsModel: SettingsModel,
    private val viewModel: MainActivityViewModel,
) : WebEngineWindowProviderCallback {

    companion object {
        private val TAG = WebEngineCallback::class.java.simpleName
    }

    override fun getActivity(): Activity = host.activity

    override fun onOpenInNewTabRequested(url: String, navigateImmediately: Boolean): WebEngine? {
        val index = NewTabPositionPolicy.insertIndexAfter(
            tabsModel.tabsStates.indexOf(tabsModel.currentTab.value), tabsModel.tabsStates.size)
        return host.openInNewTabAt(url, index, navigateImmediately)
    }

    override fun onDownloadRequested(url: String) {
        Log.i(TAG, "onDownloadRequested url: $url")
        val fileName = Uri.parse(url).lastPathSegment
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(url))
        onDownloadRequested(url, tab.url,
            fileName, tab.webEngine.userAgentString, mimeType)
    }

    override fun onDownloadRequested(url: String, referer: String,
                                     originalDownloadFileName: String?, userAgent: String?, mimeType: String?,
                                     operationAfterDownload: Download.OperationAfterDownload,
                                     base64BlobData: String?, stream: InputStream?, size: Long, contentDisposition: String?) {
        val fileName = DownloadUtils.guessFileName(url, contentDisposition, mimeType)

        host.downloadRequested(DownloadRequest(
            url = url, referer = referer, fileName = fileName,
            userAgent = userAgent, mimeType = mimeType,
            operationAfterDownload = operationAfterDownload,
            base64BlobData = base64BlobData, stream = stream, size = size,
        ))
    }

    override fun onDownloadRequested(url: String, userAgent: String?, contentDisposition: String,
                                     mimetype: String?, contentLength: Long ) {
        Log.i(TAG, "DownloadListener.onDownloadStart url: $url")
        onDownloadRequested(url= url, referer = tab.url, originalDownloadFileName = null,
            userAgent = userAgent, mimeType = mimetype, size = contentLength, contentDisposition = contentDisposition)
    }

    override fun onProgressChanged(newProgress: Int) {
        host.showLoadingProgress(newProgress)
    }

    override fun onReceivedTitle(title: String) {
        tabsModel.onPageTitleUpdated(tab, title)
        host.onTabTitleUpdated(tab)
        viewModel.onTabTitleUpdated(tab)
    }

    override fun requestPermissions(array: Array<String>): Int {
        return host.requestPermissionsOnHost(array)
    }

    override fun onShowFileChooser(intent: Intent): Boolean {
        return host.showFileChooser(intent)
    }

    override fun onReceivedIcon(icon: Bitmap) {
        host.onTabIconUpdated(tab)
    }

    override fun shouldOverrideUrlLoading(url: String, isAutomaticRedirect: Boolean): Boolean {
        tabsModel.onNavigationRequested(tab, url)

        val uri = try {
            Uri.parse(url)
        } catch (e: Exception) {
            Log.e(TAG, "shouldOverrideUrlLoading: ", e)
            return true
        }

        if (uri.scheme == null) {
            Log.d(TAG, "shouldOverrideUrlLoading: no scheme: $url")
            return true
        }

        if (NavigationPolicy.shouldLoadInEngine(uri.scheme, URLUtil.isNetworkUrl(url))) {
            if (isAutomaticRedirect && shouldBlockAutomaticRedirect(url)) {
                return true
            }
            Log.d(TAG, "shouldOverrideUrlLoading: network url: $url")
            return false
        }

        if (NavigationPolicy.isIntentScheme(uri.scheme)) {
            Log.d(TAG, "shouldOverrideUrlLoading: intent url: $url")
            host.openInExternalAppRequested(url)
            return true
        }

        //try to handle intent for non-network urls by external apps
        //TODO: ask user if he wants to open this url in external app
        return try {
            Log.d(TAG, "shouldOverrideUrlLoading: non-network url: $url")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (intent.resolveActivity(TVBro.instance.packageManager) != null) {
                host.activity.runOnUiThread {
                    host.askUserAndOpenInExternalApp(url, intent)
                }
                true
            } else {
                Log.d(TAG, "shouldOverrideUrlLoading: no activity to handle intent")
                host.activity.runOnUiThread {
                    Utils.showToast(host.activity, host.activity.getString(R.string.err_no_app_to_handle_url))
                }
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "shouldOverrideUrlLoading: ", e)
            true
        }
    }

    private fun shouldBlockAutomaticRedirect(url: String): Boolean {
        val blocked = NavigationPolicy.shouldBlockAutomaticRedirect(
            requestedUrl = url,
            currentUrl = tab.url,
            lastLoadingUrl = tab.lastLoadingUrl,
            blockAutomaticRedirects = adblockModel.blockAutomaticRedirects,
            adBlockEnabled = adblockModel.adBlockEnabled,
            hostOf = { u -> Uri.parse(u).host }
        )
        if (blocked) {
            Log.i(TAG, "blockAutomaticRedirect: $url -> blocked")
            onBlockedAd(url)
        }
        return blocked
    }

    override fun onPageStarted(url: String?) {
        host.onWebViewUpdated(tab)
        tabsModel.onPageStarted(tab, tab.webEngine.url, url)
        host.updateAddressBoxIfCurrentTab(tab)
    }

    override fun onPageFinished(url: String?) {
        if (tabsModel.currentTab.value == null) {
            return
        }
        host.onWebViewUpdated(tab)
        tabsModel.onPageFinished(tab, tab.webEngine.url, url)
        host.updateAddressBoxIfCurrentTab(tab)
        host.renderAndShowThumbnail(tab)
    }

    override fun onPageCertificateError(url: String?) {
        host.onCertificateError()
    }

    override fun isAd(url: Uri, acceptHeader: String?, baseUri: Uri): Boolean? {
        return adblockModel.isAd(url, acceptHeader, baseUri)
    }

    override fun isAdBlockingEnabled(): Boolean {
        // Use this callback's own tab, not whichever tab happens to be current: the WebView
        // callback is per-tab and is queried while a tab may not be selected yet.
        return tab.adblock ?: adblockModel.adBlockEnabled
    }

    override fun isDialogsBlockingEnabled(): Boolean {
        if (Config.isHomePageUrl(tab.url)) return false
        return shouldBlockNewWindow(dialog = true, userGesture = false)
    }

    override fun shouldBlockNewWindow(dialog: Boolean, userGesture: Boolean): Boolean {
        // Synchronous callback: the popup decision has to be returned now, so the (normally
        // suspending) host lookup is bridged with a plain runBlocking. That form runs the
        // coroutine on an event loop owned by *this* thread, which joinBlocking() pumps itself
        // while it waits. An explicit dispatcher must not be used here: the lookup resumes from
        // Room's query executor, and a dispatcher like Dispatchers.Main.immediate would post the
        // resumption to a queue that this blocked thread never drains again (deadlock/ANR).
        val hostConfig = runBlocking { tabsModel.findHostConfig(tab, false) }
        return PopupPolicy.shouldBlockNewWindow(dialog, userGesture, hostConfig?.popupBlockLevel)
    }

    override fun onBlockedAd(uri: String) {
        Log.i(TAG, "onBlockedAd: $uri")
        if (!adblockModel.adBlockEnabled) return
        tabsModel.onAdBlocked(tab)
    }

    override fun onBlockedDialog(newTab: Boolean) {
        tabsModel.onDialogBlocked(tab)
        host.showBlockedDialogNotification(newTab)
    }

    override fun onCreateWindow(dialog: Boolean, userGesture: Boolean): View? {
        if (shouldBlockNewWindow(dialog, userGesture)) {
            onBlockedDialog(!dialog)
            return null
        }
        val tab = WebTabState(incognito = settingsModel.incognitoMode)
        val webView = host.createWebView(tab) ?: return null
        val currentTab = tabsModel.currentTab.value ?: return null
        val index = NewTabPositionPolicy.insertIndexAfter(
            tabsModel.tabsStates.indexOf(currentTab), tabsModel.tabsStates.size)
        tabsModel.onOpenTab(tab, index)
        host.changeTab(tab)
        return webView
    }

    override fun closeWindow(internalRepresentation: Any) {
        for (tab in tabsModel.tabsStates) {
            if (tab.webEngine.isSameSession(internalRepresentation)) {
                host.closeTab(tab)
                break
            }
        }
    }

    override fun onScaleChanged(oldScale: Float, newScale: Float) {
        Log.d(TAG, "onScaleChanged: oldScale: $oldScale newScale: $newScale")
        tabsModel.onScaleChanged(tab, newScale)
    }

    override fun onCopyTextToClipboardRequested(url: String) {
        host.copyToClipboard(url)
    }

    override fun onShareUrlRequested(url: String) {
        host.shareUrl(url)
    }

    override fun onOpenInExternalAppRequested(url: String) {
        host.openInExternalAppRequested(url)
    }

    override fun initiateVoiceSearch() {
        host.initiateVoiceSearch()
    }

    override fun onEditHomePageBookmarkSelected(favoriteId: Long) {
        host.onEditHomePageBookmarkSelected(favoriteId)
    }

    override fun getHomePageLinks(): List<HomePageLink> {
        return viewModel.homePageLinks
    }

    override fun getHomePageHistoryLinks(): List<HomePageLink> {
        return viewModel.homePageHistoryLinks
    }

    override fun onPrepareForFullscreen() {
        host.onPrepareForFullscreen()
    }

    override fun onExitFullscreen() {
        host.onExitFullscreen()
    }

    override fun onVisited(url: String) {
        val tab = tabsModel.currentTab.value ?: return

        if (!settingsModel.incognitoMode) {
            viewModel.logVisitedHistory(tab.title, url, tab.faviconHash)
        }
    }

    override fun onContextMenu(
        cursorDrawer: CursorDrawerDelegate,
        baseUri: String?,
        linkUri: String?,
        srcUri: String?,
        title: String?,
        altText: String?,
        textContent: String?,
        x: Int,
        y: Int
    ) {
        host.showCursorMenu(tab, this, cursorDrawer, baseUri, linkUri, srcUri,
            title, altText, textContent, x, y)
    }

    override fun suggestActionsForLink(baseUri: String?, linkUri: String?, srcUri: String?,
                                       title: String?, altText: String?, textContent: String?,
                                       x: Int, y: Int) {
        host.suggestActionsForLink(tab, this, baseUri, linkUri, srcUri,
            title, altText, textContent, x, y)
    }

    override fun markBookmarkRecommendationAsUseful(bookmarkOrder: Int) {
        viewModel.markBookmarkRecommendationAsUseful(bookmarkOrder)
    }
}
