package com.phlox.tvwebbrowser.webengine.webview

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import com.phlox.tvwebbrowser.Config
import com.phlox.tvwebbrowser.domain.UserAgentPolicy
import com.phlox.tvwebbrowser.model.WebTabState
import com.phlox.tvwebbrowser.utils.Utils
import com.phlox.tvwebbrowser.webengine.MediaCandidate
import com.phlox.tvwebbrowser.webengine.WebEngine
import com.phlox.tvwebbrowser.webengine.WebEngineFactory
import com.phlox.tvwebbrowser.webengine.WebEngineProvider
import com.phlox.tvwebbrowser.webengine.WebEngineProviderCallback
import com.phlox.tvwebbrowser.webengine.WebEngineWindowProviderCallback
import com.phlox.tvwebbrowser.widgets.cursor.CursorDrawerDelegate
import com.phlox.tvwebbrowser.widgets.cursor.CursorLayout

class WebViewWebEngine(
    val tab: WebTabState,
    private val appContext: Context,
    private val config: Config,
) : WebEngine,
    CursorDrawerDelegate.Callback {
    private var webView: WebViewEx? = null
    internal var callback: WebEngineWindowProviderCallback? = null
    private var viewParent: CursorLayout? = null
    private var fullScreenView: View? = null
    private val permissionsRequests = HashMap<Int, Boolean>() // request code, isGeolocationPermissionRequest
    private val jsInterface = AndroidJSInterface(this, appContext, config)

    override fun getWebEngineName(): String = "WebView"

    override fun isSameSession(internalRepresentation: Any): Boolean = internalRepresentation == webView

    override val url: String?
        get() = webView?.url

    override var userAgentString: String? = null
        set(value) {
            field = value
            webView?.let { applyUserAgent(it, value) }
        }

    /**
     * Applies the requested User-Agent, or the WebView default with the `; wv` in-app marker
     * removed when the user kept "Default". Site players commonly reject the marker with a 403.
     *
     * [WebSettings.getDefaultUserAgent] is API 26 while the geckoExcluded flavor supports API 24+,
     * so below O there is no public default-UA accessor: a freshly created view — the only path
     * that reads a `null` field — still carries the stock default, so the marker is stripped from
     * whatever is currently set instead.
     */
    private fun applyUserAgent(
        view: WebView,
        userAgent: String?,
    ) {
        val base =
            userAgent
                ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    UserAgentPolicy.withoutInAppMarker(WebSettings.getDefaultUserAgent(appContext))
                } else {
                    UserAgentPolicy.withoutInAppMarker(view.settings.userAgentString)
                }
        view.settings.userAgentString = base
    }

    override fun saveState(): Any {
        val bundle = Bundle()
        webView?.saveState(bundle)
        return bundle
    }

    override fun restoreState(savedInstanceState: Any) {
        if (savedInstanceState is Bundle) {
            webView?.restoreState(savedInstanceState)
        } else {
            throw IllegalArgumentException("savedInstanceState must be Bundle")
        }
    }

    override fun stateFromBytes(bytes: ByteArray): Any? = Utils.bytesToBundle(bytes)

    override fun loadUrl(url: String) {
        webView?.loadUrl(url)
    }

    override fun canGoForward(): Boolean = webView?.canGoForward() ?: false

    override fun goForward() {
        webView?.goForward()
    }

    override fun canZoomIn(): Boolean = webView?.canZoomIn() ?: false

    override fun zoomIn() {
        webView?.zoomIn()
    }

    override fun canZoomOut(): Boolean = webView?.canZoomOut() ?: false

    override fun zoomOut() {
        webView?.zoomOut()
    }

    override fun zoomBy(zoomBy: Float) {
        webView?.zoomBy(zoomBy)
    }

    override fun evaluateJavascript(script: String) {
        webView?.evaluateJavascript(script, null)
    }

    override fun setNetworkAvailable(connected: Boolean) {
        webView?.setNetworkAvailable(connected)
    }

    override fun getView(): View? = webView

    @Throws(Exception::class)
    override fun getOrCreateView(activityContext: Context): View {
        if (webView == null) {
            val created = WebViewEx(activityContext, webViewCallback, jsInterface)
            applyUserAgent(created, userAgentString)
            webView = created
        }
        return webView!!
    }

    override fun canGoBack(): Boolean = webView?.canGoBack() ?: false

    override fun goBack() {
        webView?.goBack()
    }

    override fun reload() {
        webView?.reload()
    }

    override fun onFilePicked(
        resultCode: Int,
        data: Intent?,
    ) {
        if (resultCode != Activity.RESULT_OK || data == null) {
            return
        }
        webView?.onFilePicked(data)
    }

    override fun onResume() {
        webView?.onResume()
    }

    override fun onPause() {
        webView?.onPause()
    }

    override fun onUpdateAdblockSetting(newState: Boolean) {
        webView?.onUpdateAdblockSetting(newState)
    }

    override fun hideFullscreenView() {
        webView?.hideCustomView()
    }

    override fun togglePlayback() {
        webView?.evaluateJavascript("tvBroTogglePlayback()", null)
    }

    override fun stopPlayback() {
        webView?.evaluateJavascript("tvBroStopPlayback()", null)
    }

    override fun rewind() {
        webView?.evaluateJavascript("tvBroRewind()", null)
    }

    override fun fastForward() {
        webView?.evaluateJavascript("tvBroFastForward()", null)
    }

    override fun mediaCandidates(): List<MediaCandidate> = webView?.mediaCandidates() ?: emptyList()

    override fun playMediaCandidate(candidate: MediaCandidate) {
        webView?.playMediaCandidate(candidate.url)
    }

    override fun prefetchMediaMetadata() {
        webView?.prefetchMediaMetadata()
    }

    override suspend fun renderThumbnail(bitmap: Bitmap?): Bitmap? = webView?.renderThumbnail(bitmap)

    override fun onAttachToWindow(
        callback: WebEngineWindowProviderCallback,
        parent: ViewGroup,
    ) {
        this.callback = callback
        if (webView == null) {
            throw IllegalStateException("WebView is null")
        }
        this.viewParent = parent as CursorLayout
        parent.removeAllViews()
        parent.addView(webView)
        viewParent?.cursorDrawerDelegate?.callback = this
        // The WebView is constructed before its callback is attached, so the ad-block state read
        // at construction time is always false and the overlay zapper never gets registered.
        // Re-apply now that the callback (and its per-tab adblock setting) is available.
        webView?.onUpdateAdblockSetting(callback.isAdBlockingEnabled())
        onResume()
    }

    override fun onDetachFromWindow(
        completely: Boolean,
        destroyTab: Boolean,
    ) {
        onPause()
        (webView?.parent as? ViewGroup)?.removeView(webView)
        callback = null
        if (completely) {
            webView = null
        }
    }

    override fun trimMemory() {
        val webView = webView
        if (webView != null && !webView.isAttachedToWindow) {
            this.webView = null
        }
    }

    override fun onPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ): Boolean {
        val isGeolocationPermissionRequest = permissionsRequests[requestCode] ?: return false
        permissionsRequests.remove(requestCode)
        if (grantResults.isEmpty()) return true
        webView?.onPermissionsResult(permissions, grantResults, isGeolocationPermissionRequest)
        return true
    }

    override fun onLongPress(
        x: Int,
        y: Int,
    ) {
        webView?.let {
            it.evaluateJavascript(Scripts.LONG_PRESS_SCRIPT) { href ->
                val linkUrl = if (href == "null") null else href
                webViewCallback.onContextMenu(
                    it.currentOriginalUrl.toString(),
                    linkUrl,
                    x,
                    y,
                )
            }
        }
    }

    override fun isVirtualCursorMode(): Boolean = viewParent?.cursorEnabled ?: true

    override fun setVirtualCursorMode(enabled: Boolean) {
        viewParent?.cursorEnabled = enabled
        if (enabled) {
            viewParent?.cursorDrawerDelegate?.animateAppearing()
        }
        webView?.setVirtualCursorMode(enabled)
    }

    override fun getCursorDrawerDelegate(): CursorDrawerDelegate? = viewParent?.cursorDrawerDelegate

    private val webViewCallback =
        object : WebViewEx.Callback {
            override fun getActivity(): Activity? = callback?.getActivity()

            override fun onOpenInNewTabRequested(url: String) {
                callback?.onOpenInNewTabRequested(url, true)
            }

            override fun onDownloadRequested(url: String) {
                callback?.onDownloadRequested(url)
            }

            override fun onThumbnailError() {
                // nop for now
            }

            override fun onShowCustomView(view: View) {
                callback?.onPrepareForFullscreen()
                webView?.visibility = View.GONE
                viewParent?.apply {
                    addView(view)
                }
                fullScreenView = view
            }

            override fun onHideCustomView() {
                if (fullScreenView != null) {
                    viewParent?.removeView(fullScreenView)
                    fullScreenView = null
                }
                webView?.visibility = View.VISIBLE
                callback?.onExitFullscreen()
            }

            override fun onProgressChanged(newProgress: Int) {
                callback?.onProgressChanged(newProgress)
            }

            override fun onReceivedTitle(title: String) {
                callback?.onReceivedTitle(title)
            }

            override fun requestPermissions(
                array: Array<String>,
                geo: Boolean,
            ) {
                val requestCode = callback?.requestPermissions(array) ?: return
                permissionsRequests[requestCode] = geo
            }

            override fun onShowFileChooser(intent: Intent): Boolean = callback?.onShowFileChooser(intent) ?: false

            override fun onReceivedIcon(icon: Bitmap) {
                callback?.onReceivedIcon(icon)
            }

            override fun shouldOverrideUrlLoading(
                url: String,
                isAutomaticRedirect: Boolean,
            ): Boolean = callback?.shouldOverrideUrlLoading(url, isAutomaticRedirect) ?: false

            override fun onPageStarted(url: String?) {
                callback?.onPageStarted(url)
            }

            override fun onPageFinished(url: String?) {
                callback?.onPageFinished(url)
            }

            override fun onPageCertificateError(url: String?) {
                callback?.onPageCertificateError(url)
            }

            override fun isAd(
                request: WebResourceRequest,
                baseUri: Uri,
            ): Boolean = callback?.isAd(request.url, request.requestHeaders["Accept"], baseUri) ?: false

            override fun isAdBlockingEnabled(): Boolean = callback?.isAdBlockingEnabled() ?: false

            override fun isDialogsBlockingEnabled(): Boolean = callback?.isDialogsBlockingEnabled() ?: false

            override fun onBlockedAd(url: Uri) {
                callback?.onBlockedAd(url.toString())
            }

            override fun onBlockedDialog(newTab: Boolean) {
                callback?.onBlockedDialog(newTab)
            }

            override fun onCreateWindow(
                dialog: Boolean,
                userGesture: Boolean,
            ): WebViewEx? = callback?.onCreateWindow(dialog, userGesture) as? WebViewEx

            override fun closeWindow(window: WebView) {
                callback?.closeWindow(window)
            }

            override fun onDownloadStart(
                url: String,
                userAgent: String,
                contentDisposition: String,
                mimetype: String?,
                contentLength: Long,
            ) {
                callback?.onDownloadRequested(url, userAgent, contentDisposition, mimetype, contentLength)
            }

            override fun onScaleChanged(
                oldScale: Float,
                newScale: Float,
            ) {
                callback?.onScaleChanged(oldScale, newScale)
            }

            override fun onCopyTextToClipboardRequested(url: String) {
                callback?.onCopyTextToClipboardRequested(url)
            }

            override fun onShareUrlRequested(url: String) {
                callback?.onShareUrlRequested(url)
            }

            override fun onOpenInExternalAppRequested(url: String) {
                callback?.onOpenInExternalAppRequested(url)
            }

            override fun onVisited(url: String) {
                callback?.onVisited(url)
            }

            override fun onContextMenu(
                baseUrl: String?,
                href: String?,
                x: Int,
                y: Int,
            ) {
                callback?.onContextMenu(
                    viewParent!!.cursorDrawerDelegate,
                    baseUri = baseUrl,
                    linkUri = href,
                    srcUri = null,
                    title = null,
                    altText = null,
                    textContent = null,
                    x = x,
                    y = y,
                )
            }
        }

    companion object {
        init {
            WebEngineFactory.registerProvider(
                WebEngineProvider(
                    "WebView",
                    object : WebEngineProviderCallback {
                        private var appContext: Context? = null
                        private var config: Config? = null

                        override suspend fun initialize(
                            context: Context,
                            config: Config,
                            webViewContainer: CursorLayout,
                        ) {
                            appContext = context.applicationContext ?: context
                            this.config = config
                        }

                        override fun createWebEngine(tab: WebTabState): WebEngine = WebViewWebEngine(tab, appContext!!, config!!)

                        override suspend fun clearCache(ctx: Context) {
                            WebView(ctx).clearCache(true)
                        }

                        override fun onThemeSettingUpdated(value: Config.Theme) {
                            // nop
                        }

                        override fun getWebEngineVersionString(): String {
                            val webViewPackage = appContext?.let { WebViewCompat.getCurrentWebViewPackage(it) }
                            return (webViewPackage?.packageName ?: "unknown") + ":" + (webViewPackage?.versionName ?: "unknown")
                        }
                    },
                ),
            )
        }
    }
}
