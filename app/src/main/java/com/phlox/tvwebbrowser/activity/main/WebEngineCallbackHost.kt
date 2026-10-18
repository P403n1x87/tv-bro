package com.phlox.tvwebbrowser.activity.main

import android.app.Activity
import android.content.Intent
import android.view.View
import com.phlox.tvwebbrowser.model.Download
import com.phlox.tvwebbrowser.model.WebTabState
import com.phlox.tvwebbrowser.webengine.WebEngine
import com.phlox.tvwebbrowser.webengine.WebEngineWindowProviderCallback
import com.phlox.tvwebbrowser.widgets.cursor.CursorDrawerDelegate
import java.io.InputStream

/**
 * The view side of the per-tab engine adapter [WebEngineCallback].
 *
 * [WebEngineCallback] owns the engine-facing policy (url routing, popup/redirect blocking, the
 * new-tab position) and talks to the models it was handed; everything that touches the UI — a
 * repaint, the window flags, launching another activity — goes through this interface, so the
 * adapter is a plain class instead of an inner class of the activity and can be unit-tested against
 * a fake host. [MainActivity] is the only implementation.
 */
interface WebEngineCallbackHost {
    val activity: Activity

    /** Opens a tab at [index] (see [com.phlox.tvwebbrowser.domain.NewTabPositionPolicy]); the callback picks the index. */
    fun openInNewTabAt(
        url: String,
        index: Int,
        navigateImmediately: Boolean,
    ): WebEngine?

    // -- page lifecycle repainting -----------------------------------------------------------

    fun showLoadingProgress(progress: Int)

    fun onWebViewUpdated(tab: WebTabState)

    fun updateAddressBoxIfCurrentTab(tab: WebTabState)

    fun onTabTitleUpdated(tab: WebTabState)

    fun onTabIconUpdated(tab: WebTabState)

    fun renderAndShowThumbnail(tab: WebTabState)

    fun onCertificateError()

    // -- platform operations -----------------------------------------------------------------

    fun requestPermissionsOnHost(permissions: Array<String>): Int

    fun showFileChooser(intent: Intent): Boolean

    fun downloadRequested(request: DownloadRequest)

    fun openInExternalAppRequested(url: String)

    fun askUserAndOpenInExternalApp(
        url: String,
        intent: Intent,
    )

    fun copyToClipboard(url: String)

    fun shareUrl(url: String)

    fun initiateVoiceSearch()

    fun showBlockedDialogNotification(newTab: Boolean)

    fun onEditHomePageBookmarkSelected(favoriteId: Long)

    // -- tabs and window ---------------------------------------------------------------------

    fun createWebView(tab: WebTabState): View?

    fun changeTab(newTab: WebTabState)

    fun closeTab(tab: WebTabState?)

    fun onPrepareForFullscreen()

    fun onExitFullscreen()

    // -- cursor menu -------------------------------------------------------------------------

    fun showCursorMenu(
        tab: WebTabState,
        callback: WebEngineWindowProviderCallback,
        cursorDrawer: CursorDrawerDelegate,
        baseUri: String?,
        linkUri: String?,
        srcUri: String?,
        title: String?,
        altText: String?,
        textContent: String?,
        x: Int,
        y: Int,
    )

    fun suggestActionsForLink(
        tab: WebTabState,
        callback: WebEngineWindowProviderCallback,
        baseUri: String?,
        linkUri: String?,
        srcUri: String?,
        title: String?,
        altText: String?,
        textContent: String?,
        x: Int,
        y: Int,
    )
}

/**
 * The parameters of a download the engine asked for, bundled so [WebEngineCallbackHost] has one
 * method to implement instead of a nine-argument one. Mirrors
 * [DownloadCoordinator.requestDownload].
 */
data class DownloadRequest(
    val url: String,
    val referer: String,
    val fileName: String,
    val userAgent: String?,
    val mimeType: String?,
    val operationAfterDownload: Download.OperationAfterDownload,
    val base64BlobData: String?,
    val stream: InputStream?,
    val size: Long,
)
