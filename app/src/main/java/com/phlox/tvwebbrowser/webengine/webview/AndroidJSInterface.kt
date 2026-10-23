package com.phlox.tvwebbrowser.webengine.webview

import android.content.Context
import android.net.http.SslError
import android.webkit.JavascriptInterface
import com.phlox.tvwebbrowser.Config
import com.phlox.tvwebbrowser.R
import com.phlox.tvwebbrowser.SearchEngines
import com.phlox.tvwebbrowser.domain.HomePagePolicy
import com.phlox.tvwebbrowser.model.Download
import com.phlox.tvwebbrowser.utils.DownloadUtils
import org.json.JSONArray

class AndroidJSInterface(
    private val webEngine: WebViewWebEngine,
    private val context: Context,
    internal val config: Config,
) {
    @JavascriptInterface
    fun currentUrl(): String {
        if (!webEngine.tab.url.startsWith(WebViewEx.INTERNAL_SCHEME)) return ""
        return webEngine.tab.url
    }

    @JavascriptInterface
    fun reloadWithSslTrust() {
        val callback = webEngine.callback ?: return
        if ((webEngine.getView() as WebViewEx).currentOriginalUrl?.scheme != "file") return
        callback.getActivity().runOnUiThread {
            val webview = webEngine.getView() as? WebViewEx ?: return@runOnUiThread
            webview.trustSsl = true
            webEngine.tab.url.apply { webEngine.loadUrl(this) }
        }
    }

    @JavascriptInterface
    fun getStringByName(name: String): String {
        val ctx = context
        when (name) {
            "connection_isnt_secure" -> return ctx.getString(R.string.connection_isnt_secure)
            "hostname" -> return ctx.getString(R.string.hostname)
            "err_desk" -> return ctx.getString(R.string.err_desk)
            "details" -> return ctx.getString(R.string.details)
            "back_to_safety" -> return ctx.getString(R.string.back_to_safety)
            "go_im_aware" -> return ctx.getString(R.string.go_im_aware)
            else -> return ""
        }
    }

    @JavascriptInterface
    fun startVoiceSearch() {
        if (!isHomePage()) return
        val callback = webEngine.callback ?: return
        callback.getActivity().runOnUiThread { callback.initiateVoiceSearch() }
    }

    @JavascriptInterface
    fun setSearchEngine(
        engine: String,
        customSearchEngineURL: String,
    ) {
        if (!isHomePage()) return
        config.searchEngineURL.value = customSearchEngineURL
    }

    @JavascriptInterface
    fun onEditBookmark(favoriteId: Long) {
        if (!isHomePage()) return
        val callback = webEngine.callback ?: return
        callback.getActivity().runOnUiThread { callback.onEditHomePageBookmarkSelected(favoriteId) }
    }

    @JavascriptInterface
    fun onHomePageLoaded() {
        val callback = webEngine.callback ?: return
        callback.getActivity().runOnUiThread {
            // Resolve the live WebView URL on the UI thread: WebView methods must not be called from
            // the JavascriptInterface thread, and on a cache-restored history load the page scripts
            // can run before tab.url has been updated to the new page.
            val webView = webEngine.getView() as? WebViewEx ?: return@runOnUiThread
            if (!HomePagePolicy.isHomePageUrl(webView.url, Config.localHomePageUrl)) return@runOnUiThread

            fun linksToJson(links: List<com.phlox.tvwebbrowser.model.HomePageLink>): String {
                val jsArr = JSONArray()
                for (item in links) {
                    jsArr.put(item.toJsonObj())
                }
                return jsArr.toString().replace("'", "\\'")
            }
            val bookmarks = linksToJson(callback.getHomePageLinks())
            val history = linksToJson(callback.getHomePageHistoryLinks())
            webEngine.evaluateJavascript("renderSections($bookmarks, $history)")
            webEngine.evaluateJavascript(
                "applySearchEngine(\"${SearchEngines.nameFor(config.searchEngineURL.value)}\", \"${config.searchEngineURL.value}\")",
            )
        }
    }

    @JavascriptInterface
    fun lastSSLError(getDetails: Boolean): String {
        val lastSSLError = (webEngine.getView() as? WebViewEx)?.lastSSLError ?: return "unknown"
        return if (getDetails) {
            lastSSLError.toString()
        } else {
            when (lastSSLError.primaryError) {
                SslError.SSL_EXPIRED -> context.getString(R.string.ssl_expired)
                SslError.SSL_IDMISMATCH -> context.getString(R.string.ssl_idmismatch)
                SslError.SSL_DATE_INVALID -> context.getString(R.string.ssl_date_invalid)
                SslError.SSL_INVALID -> context.getString(R.string.ssl_invalid)
                else -> "unknown"
            }
        }
    }

    @JavascriptInterface
    fun takeBlobDownloadData(
        base64BlobData: String,
        fileName: String?,
        url: String,
        mimetype: String,
    ) {
        val callback = webEngine.callback ?: return
        val finalFileName = fileName ?: DownloadUtils.guessFileName(url, null, mimetype)
        callback.onDownloadRequested(
            url,
            "",
            finalFileName,
            "Odysseus",
            mimetype,
            Download.OperationAfterDownload.NOP,
            base64BlobData,
        )
    }

    @JavascriptInterface
    fun markBookmarkRecommendationAsUseful(bookmarkOrder: Int) {
        if (!isHomePage()) return
        val callback = webEngine.callback ?: return
        callback.getActivity().runOnUiThread { callback.markBookmarkRecommendationAsUseful(bookmarkOrder) }
    }

    private fun isHomePage(): Boolean = HomePagePolicy.isHomePageUrl(webEngine.tab.url, Config.localHomePageUrl)
}
